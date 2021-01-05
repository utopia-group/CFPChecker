package edu.utexas.cs.utopia.cfpchecker.verifier;

import edu.utexas.cs.utopia.cfpchecker.speclang.APISpecCall;
import edu.utexas.cs.utopia.cfpchecker.speclang.Specification;
import soot.*;
import soot.jimple.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static edu.utexas.cs.utopia.cfpchecker.verifier.VerifierUtil.reachableFromMain;

/**
 * Created by kferles on 7/30/18.
 */
public class BreakBlocksOnCalls extends SceneTransformer
{
    private Map<Unit, APISpecCall> invokeStmtToSpecCalls;

    private Specification spec;

    private Map<GotoStmt, Unit> returnLocations = new HashMap<>();

    private void internalTransform(Body b)
    {
        Map<Unit, SootMethod> stmtsToBreak = new HashMap<>();

        for (Unit u : b.getUnits())
        {
            u.apply(new AbstractStmtSwitch()
            {
                // TODO: This needs more work. Currently it makes tons of assumptions about the input program.
                @Override
                public void caseInvokeStmt(InvokeStmt stmt)
                {
                    SootMethod calledMeth = stmt.getInvokeExpr().getMethod();
                    if (VerifierUtil.mustAnalyze(spec.getApiClasses(), calledMeth))
                    {
                        stmtsToBreak.put(stmt, calledMeth);
                    }
                }

                @Override
                public void caseAssignStmt(AssignStmt stmt)
                {
                    // TODO: also check for API calls
                    Value rightOp = stmt.getRightOp();
                    if (rightOp instanceof InvokeExpr)
                    {
                        InvokeExpr invExpr = (InvokeExpr) rightOp;

                        SootMethod calledMeth = invExpr.getMethod();
                        if (VerifierUtil.mustAnalyze(spec.getApiClasses(), calledMeth))
                            stmtsToBreak.put(stmt, calledMeth);
                    }
                }
            });
        }

        Jimple jimpleFactory = Jimple.v();

        for (Unit u : stmtsToBreak.keySet())
        {
            UnitPatchingChain currUnits = b.getUnits();
            GotoStmt returnLocation = jimpleFactory.newGotoStmt(currUnits.getSuccOf(u));
            GotoStmt callLocation = jimpleFactory.newGotoStmt(u);

            returnLocations.put(returnLocation, u);
            currUnits.insertAfter(returnLocation, u);
            currUnits.insertOnEdge(callLocation, currUnits.getPredOf(u), u);
        }
    }

    @Override
    protected void internalTransform(String s, Map<String, String> map)
    {
        Set<SootMethod> workSet = new HashSet<>();
        for (SootClass cl : Scene.v().getClasses())
        {
            for (SootMethod m : cl.getMethods())
                if (VerifierUtil.mustAnalyze(spec.getApiClasses(), m) && reachableFromMain(m))
                    workSet.add(m);
        }

        for (SootMethod m : workSet)
            internalTransform(m.getActiveBody());
    }

    public BreakBlocksOnCalls(Map<Unit, APISpecCall> invokeStmtToSpecCalls, Specification spec)
    {
        this.invokeStmtToSpecCalls = invokeStmtToSpecCalls;
        this.spec = spec;
    }

    public Map<GotoStmt, Unit> getReturnLocations()
    {
        return returnLocations;
    }
}
