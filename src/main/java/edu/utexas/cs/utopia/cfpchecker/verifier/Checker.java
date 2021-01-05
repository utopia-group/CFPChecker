package edu.utexas.cs.utopia.cfpchecker.verifier;

import edu.utexas.cs.utopia.cfpchecker.CmdLine;
import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.grammarcomp.GrammarCompProxy;
import edu.utexas.cs.utopia.cfpchecker.speclang.Specification;
import grammarcomp.grammar.CFGrammar;
import grammarcomp.parsing.ParseTree;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static edu.utexas.cs.utopia.cfpchecker.verifier.VerifierUtil.targetMethodsOf;
import static edu.utexas.cs.utopia.cfpchecker.verifier.VerifierUtil.mustAnalyze;

/**
 * Created by kferles on 7/2/18.
 */
public class Checker
{
    private static GrammarCompProxy G_COMP = GrammarCompProxy.getInstance();

    private Specification spec;

    private AbstractionLifter abs;

    private InterpolantGenerator generator;

    private HashSet<List<Unit>> seenCexPaths;

    private int refinementCnt;

    public Checker(Specification spec, AbstractionLifter abs, InterpolantGenerator generator)
    {
        this.spec = spec;
        this.abs = abs;
        this.generator = generator;
        this.seenCexPaths = new HashSet<>();
        this.refinementCnt = 0;
    }

    public void check()
    {
        Set<ParseTree<String>> inclusionCheckResult;
        refinementCnt = 0;
        outer:
        do
        {
            CFGrammar.Grammar<String> currAbstraction = abs.getCurrAbstraction();

            Instant start = null;

            inclusionCheckResult = G_COMP.isSubsetOf(currAbstraction, spec.getSpecGrammar().cfGrammar());

            if (!inclusionCheckResult.isEmpty())
            {
                Map<Unit, Set<Expr>> accumInterpolants = new HashMap<>();
                Set<ParseTree<String>> spuriousCexs = new HashSet<>();
                Set<List<Unit>> freshCexPaths = new HashSet<>();
                Map<Unit, Map<Expr, Map<Unit, Expr>>> interpImpl = new HashMap<>();
                for (ParseTree<String> cex : inclusionCheckResult)
                {
                    List<Unit> cexPath = abs.cexToJimplePath(cex);

                    if (freshCexPaths.contains(cexPath))
                        continue;

                    freshCexPaths.add(cexPath);

                    if (CmdLine.STATS_MODE)
                    {
                        start = Instant.now();
                    }

                    List<Expr> traceFormula = abs.isCexFeasible(cexPath);

                    if (CmdLine.STATS_MODE)
                    {
                        Instant end = Instant.now();
                        PerfStats.getInstance().addCexSAT(Duration.between(start, end));
                    }

                    if (traceFormula != null)
                    {
                        if (CmdLine.STATS_MODE)
                        {
                            start = Instant.now();
                        }

                        Map<Unit, Set<Expr>> interpolants = generator.getInterpolants(cexPath, traceFormula, abs.collectBlocksFromCex(cex, new HashSet<>()), interpImpl);

                        if (CmdLine.STATS_MODE)
                        {
                            Instant end = Instant.now();
                            PerfStats.getInstance().addInterpolation(Duration.between(start, end));
                        }

                        if(CmdLine.DEBUG_MODE)
                        {
                            System.out.println("Spurious Counter-example:");
                            cexPrettyPrint(cexPath, interpolants);
                        }

                        interpolants.forEach((u, inters) ->
                                             {
                                                 if (!accumInterpolants.containsKey(u))
                                                     accumInterpolants.put(u, new HashSet<>());

                                                 accumInterpolants.get(u).addAll(inters);
                                             });

                        if (seenCexPaths.contains(cexPath))
                        {
                            throw new IllegalStateException("Found repetitive counterexample");
                        }

                        seenCexPaths.add(cexPath);

                        spuriousCexs.add(cex);
                    }
                    else
                    {
                        // Counter-Example Found
                        System.out.println("UNSAT, counter-example found:");
                        cexPrettyPrint(cexPath, null);
                        break outer;
                    }
                }

                if (CmdLine.STATS_MODE)
                    start = Instant.now();

                abs.refineAbstraction(spuriousCexs, accumInterpolants, interpImpl);
                refinementCnt++;

                if (CmdLine.STATS_MODE)
                {
                    Instant end = Instant.now();
                    PerfStats.getInstance().addRefinement(Duration.between(start, end));
                }
            }

        } while (!inclusionCheckResult.isEmpty());
    }

    private void cexPrettyPrint(List<Unit> cexPath,  Map<Unit, Set<Expr>> interpolants)
    {
        StringBuilder indent = new StringBuilder("    ");

        for (Unit u : cexPath)
        {
            System.out.println(indent + u.toString());

            if (interpolants != null && interpolants.containsKey(u))
            {
                System.out.println(indent + interpolants.get(u).toString());
            }

            u.apply(new AbstractStmtSwitch()
            {
                @Override
                public void caseInvokeStmt(InvokeStmt stmt)
                {
                    SootMethod calledMeth = stmt.getInvokeExpr().getMethod();
                    if (mustAnalyze(spec.getApiClasses(), calledMeth) && targetMethodsOf(stmt).contains(calledMeth))
                        indent.append("    ");
                }

                @Override
                public void caseAssignStmt(AssignStmt stmt)
                {
                    if (stmt.getRightOp() instanceof InvokeExpr)
                    {
                        SootMethod calledMeth = ((InvokeExpr) stmt.getRightOp()).getMethod();
                        if (mustAnalyze(spec.getApiClasses(), calledMeth) && targetMethodsOf(stmt).contains(calledMeth))
                            indent.append("    ");
                    }
                }

                @Override
                public void caseReturnStmt(ReturnStmt stmt)
                {
                    if (indent.length() > 0)
                        indent.delete(indent.length() - 4, indent.length());
                }

                @Override
                public void caseReturnVoidStmt(ReturnVoidStmt stmt)
                {
                    if (indent.length() > 0)
                        indent.delete(indent.length() - 4, indent.length());
                }

                @Override
                public void caseThrowStmt(ThrowStmt stmt)
                {
                    if (indent.length() > 0)
                        indent.delete(indent.length() - 4, indent.length());
                }
            });
        }
    }

    public void printExecutionStats() {
        System.out.println(PerfStats.getInstance());
        System.out.println("# of Refinements: " + refinementCnt);

        //since interpolants are stored at tail of BB, can use methods that calculate per unit
        System.out.println("# of Unique Tracked Predicates: " + abs.getTotalPredicates());
        System.out.println("BBs with Tracked Predicates: " + abs.getNumUnitsWithPredicates() + " / " + abs.getPCFASize());
        int[] predicateStates = abs.getPredicateStats();
        System.out.println("Min Predicates Tracked by BB: " + predicateStates[0]);
        System.out.println("Avg Predicates Tracked by BB (excluding those with 0): " + predicateStates[1]);
        System.out.println("Max Predicates Tracked by BB: " + predicateStates[2]);

        System.out.println("PCFA Size: " + abs.getPCFASize());
        System.out.println("Avg Method PCFA Size: " + abs.getPCFASize() / abs.getNumMethods());

        int totalCexSize = 0;
        for(List<Unit> path : seenCexPaths) {
            totalCexSize += path.size();
        }

        if(seenCexPaths.isEmpty()) {
            System.out.println("Average Counterexample Path Length (# Units): N/A");
        }
        else {
            System.out.println("Average Counterexample Path Length (# Units): " + totalCexSize / seenCexPaths.size());
        }
    }
}
