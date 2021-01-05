package edu.utexas.cs.utopia.cfpchecker.verifier;

import com.microsoft.z3.Context;
import edu.utexas.cs.utopia.cfpchecker.CmdLine;
import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.ExprUtils;
import edu.utexas.cs.utopia.cfpchecker.expression.factory.ExprFactory;
import edu.utexas.cs.utopia.cfpchecker.speclang.Specification;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.*;
import soot.toolkits.graph.Block;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static edu.utexas.cs.utopia.cfpchecker.verifier.VerifierUtil.targetMethodsOf;
import static edu.utexas.cs.utopia.cfpchecker.verifier.VerifierUtil.mustAnalyze;

/**
 * Created by kferles on 7/6/18.
 */
public class InterpolantGenerator
{
    private Specification spec;

    private ExprFactory factory;

    private Context ctx;

    private Map<GotoStmt, Unit> returnLocations;

    public InterpolantGenerator(ExprFactory fact, Specification spec, com.microsoft.z3.Context context, Map<GotoStmt, Unit> returnLocations)
    {
        this.ctx = context;
        this.factory = fact;
        this.spec = spec;
        this.returnLocations = returnLocations;
    }

    public Map<Unit, Set<Expr>> getInterpolants(final List<Unit> cex, List<Expr> traceFormula, Collection<Block> blocksInCex, Map<Unit, Map<Expr, Map<Unit, Expr>>> interpImpls)
    {
        Instant start = null;

        if (CmdLine.PROFILING_MODE)
            start = Instant.now();

        Set<Unit> bbHeads = blocksInCex.stream()
                                       .map(Block::getHead)
                                       .collect(Collectors.toSet());

        assert cex.size() > 1;
        Map<Unit, Set<Expr>> interpMap = new HashMap<>();

        Map<Integer, Integer> upBoundMap = new HashMap<>();

        int currCallIndex = 0;
        Stack<Integer> callIndexStack = new Stack<>();
        List<Expr> bbEncs = new ArrayList<>();
        Expr currBBEnc = factory.mkTRUE();
        Map<Integer, Unit> bbToTail = new HashMap<>();

        for (int i = 0, e = cex.size(); i < e; ++i)
        {
            Unit u = cex.get(i);
            if (bbHeads.contains(u))
            {
                // Finish curr BB Enc
                if (i != 0)
                {
                    bbToTail.put(bbEncs.size(), cex.get(i - 1));
                    bbEncs.add(currBBEnc);
                }

                // Start a new one
                upBoundMap.put(bbEncs.size(), currCallIndex);
                currBBEnc = factory.mkTRUE();
            }
            else if (u instanceof GotoStmt && returnLocations.containsKey(u))
            {
                // Finish curr BB Enc
                bbToTail.put(bbEncs.size(), cex.get(i - 1));
                bbEncs.add(currBBEnc);

                // Start a new one
                upBoundMap.put(bbEncs.size(), currCallIndex);
                currBBEnc = factory.mkTRUE();
            }

            currBBEnc = factory.mkAND(currBBEnc, traceFormula.get(i));

            if (u instanceof InvokeStmt)
            {
                InvokeStmt invStm = (InvokeStmt) u;
                SootMethod meth = invStm.getInvokeExpr().getMethod();
                if (mustAnalyze(spec.getApiClasses(), meth) && targetMethodsOf(u).contains(meth))
                {
                    callIndexStack.push(currCallIndex);
                    currCallIndex = bbEncs.size() + 1;
                }
            }
            else if (u instanceof AssignStmt)
            {
                if (((AssignStmt) u).getRightOp() instanceof InvokeExpr)
                {
                    InvokeExpr inv = (InvokeExpr) ((AssignStmt) u).getRightOp();
                    SootMethod calledMeth = inv.getMethod();
                    if (mustAnalyze(spec.getApiClasses(), calledMeth) && targetMethodsOf(u).contains(calledMeth))
                    {
                        callIndexStack.push(currCallIndex);
                        currCallIndex = bbEncs.size() + 1;
                    }
                }
            }
            else if (u instanceof ReturnStmt || u instanceof ReturnVoidStmt || u instanceof ThrowStmt)
            {
                if (currCallIndex != 0)
                    currCallIndex = callIndexStack.pop();
            }
        }

        bbEncs.add(currBBEnc);

        Expr subTrace = factory.mkTRUE();
        int unsatSuffixIdx = bbEncs.size() - 1;
        for (int i = bbEncs.size() - 1; i >= 0; i--)
        {
            subTrace = factory.mkAND(subTrace, bbEncs.get(i));

            if (ExprUtils.checkUNSATWithZ3(ctx, subTrace))
            {
                unsatSuffixIdx = i;
                break;
            }
        }

        List<Expr> interpolants = getInterpolants(upBoundMap, bbEncs, unsatSuffixIdx);
        if (!getUnsupportedInterpolants(interpolants).isEmpty())
        {
            // A bit of a hack, try to find a different unsat core
            interpolants = getInterpolants(upBoundMap, bbEncs, 0);
            unsatSuffixIdx = 0;
            if (!getUnsupportedInterpolants(interpolants).isEmpty())
                throw new RuntimeException("Unable to interpolate");
        }


        for (int i = unsatSuffixIdx; i < bbEncs.size() - 1; ++i)
        {
            Unit stmt = bbToTail.get(i);
            if (!interpMap.containsKey(stmt))
                interpMap.put(stmt, new HashSet<>());

            Expr itp = interpolants.get(i - unsatSuffixIdx);

            if (CmdLine.DEBUG_MODE)
            {
                System.out.println("Interpolant for " + stmt + " -> " + itp.toString());
            }

            itp = AnalysisUtils.cleanExpr(itp, factory);
            interpMap.get(stmt).add(itp);

//            if (!(stmt instanceof ReturnStmt))
//            {
//                if (i < bbEncs.size() - 2)
//                {
//                    if (!interpImpls.containsKey(stmt))
//                        interpImpls.put(stmt, new HashMap<>());
//
//                    Map<Expr, Map<Unit, Expr>> implMap = interpImpls.get(stmt);
//
//                    if (!implMap.containsKey(itp))
//                        implMap.put(itp, new HashMap<>());
//
//                    implMap.get(itp).put(bbToTail.get(i+1), AnalysisUtils.cleanExpr(interpolants.get(i + 1 - unsatSuffixIdx), factory));
//                }
//            }
        }

        if (CmdLine.PROFILING_MODE)
        {
            Instant end = Instant.now();
            System.out.println("getInterpolants: " + Duration.between(start, end));
        }

        return interpMap;
    }

    private Set<Expr> getUnsupportedInterpolants(List<Expr> interpolants)
    {
        return interpolants.stream()
                           .filter(e -> e.toString().contains("@diff"))// || e.toString().contains("devirt$") || e.toString().contains("instanceOf"))
                           .collect(Collectors.toSet());
    }

    private List<Expr> getInterpolants(Map<Integer, Integer> upBoundMap, List<Expr> bbEncs, int unsatSuffixIdx)
    {
        int[] treeIndices = new int[bbEncs.size() - unsatSuffixIdx];

        for (int i = unsatSuffixIdx, e = bbEncs.size(); i < e; i++)
        {
            treeIndices[i - unsatSuffixIdx] = Math.max(upBoundMap.get(i), unsatSuffixIdx) - unsatSuffixIdx;
        }

        return ExprUtils.computeTreeInterp(bbEncs.subList(unsatSuffixIdx, bbEncs.size()), treeIndices, factory);
    }
}
