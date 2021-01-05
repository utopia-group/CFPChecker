package edu.utexas.cs.utopia.cfpchecker.verifier;

import edu.utexas.cs.utopia.cfpchecker.speclang.Specification;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.Chain;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static edu.utexas.cs.utopia.cfpchecker.verifier.VerifierUtil.*;

public class Devirtualize extends SceneTransformer
{
    private Specification spec;

    public Devirtualize(Specification spec)
    {
        this.spec = spec;
    }

    @Override
    protected void internalTransform(String s, Map<String, String> map)
    {
        for (SootClass cl : Scene.v().getClasses())
        {
            for (SootMethod m : cl.getMethods())
                if (VerifierUtil.mustAnalyze(spec.getApiClasses(), m) && reachableFromMain(m))
                    devirtualize(m.getActiveBody());
        }
    }

    private void devirtualize(Unit u, InvokeExpr expr, Value leftOp, Body b)
    {
        CallGraph cg = Scene.v().getCallGraph();
        Set<Edge> outEdges = edgedOutOf(u);

        if (outEdges.isEmpty())
            return;

        UnitPatchingChain units = b.getUnits();
        Chain<Local> locals = b.getLocals();
        Unit callSucc = units.getSuccOf(u);

        units.remove(u);
        cg.removeAllEdgesOutOf(u);

        Jimple jimpleGen = Jimple.v();
        Set<Stmt> newCallStmts = new HashSet<>();
        for (Edge e : outEdges)
        {
            SootMethod trgMeth = e.getTgt().method();
            InvokeExpr newExpr;
            if (expr instanceof VirtualInvokeExpr)
            {
                VirtualInvokeExpr virtualInvokeExpr = (VirtualInvokeExpr) expr;
                newExpr = jimpleGen.newVirtualInvokeExpr((Local)virtualInvokeExpr.getBase(), trgMeth.makeRef(), virtualInvokeExpr.getArgs());
            }
            else
            {
                assert expr instanceof InterfaceInvokeExpr;
                InterfaceInvokeExpr interfaceInvokeExpr = (InterfaceInvokeExpr) expr;
                newExpr = jimpleGen.newVirtualInvokeExpr((Local)interfaceInvokeExpr.getBase(), trgMeth.makeRef(), interfaceInvokeExpr.getArgs());
            }

            Stmt newStmt = leftOp == null ? jimpleGen.newInvokeStmt(newExpr) : jimpleGen.newAssignStmt(leftOp, newExpr);
            newCallStmts.add(newStmt);

            units.insertBefore(newStmt, callSucc);

            cg.addEdge(new Edge(b.getMethod(), newStmt, trgMeth));
        }

        if (outEdges.size() > 1)
        {
            int localId = 0;
            for (Stmt s : newCallStmts)
            {
                Unit currStmtSucc = units.getSuccOf(s);
                units.insertAfter(jimpleGen.newGotoStmt(callSucc), s);

                Local nonDetBool = jimpleGen.newLocal("devirt$" + u.hashCode() + "$" + (localId++), BooleanType.v());
                locals.add(nonDetBool);
                units.insertBefore(jimpleGen.newIfStmt(jimpleGen.newEqExpr(nonDetBool, IntConstant.v(0)), currStmtSucc), s);
            }
        }
    }

    private void devirtualize(Body mBody)
    {
        UnitPatchingChain units = mBody.getUnits();
        Set<InvokeStmt> invStmtWorkSet = new HashSet<>();
        Set<AssignStmt> assignStmtWorkSet = new HashSet<>();
        for (Unit u : units)
        {
            u.apply(new AbstractStmtSwitch()
            {
                @Override
                public void caseInvokeStmt(InvokeStmt stmt)
                {
                    InvokeExpr invokeExpr = stmt.getInvokeExpr();
                    if ((invokeExpr instanceof VirtualInvokeExpr || invokeExpr instanceof InterfaceInvokeExpr))
                    {
                        invStmtWorkSet.add(stmt);
                    }
                }

                @Override
                public void caseAssignStmt(AssignStmt stmt)
                {
                    Value rightOp = stmt.getRightOp();

                    if (rightOp instanceof VirtualInvokeExpr || rightOp instanceof InterfaceInvokeExpr)
                    {
                        assignStmtWorkSet.add(stmt);
                    }
                }
            });
        }

        invStmtWorkSet.forEach(s -> devirtualize(s, s.getInvokeExpr(), null, mBody));
        assignStmtWorkSet.forEach(s -> devirtualize(s, s.getInvokeExpr(), s.getLeftOp(), mBody));
        mBody.validate();
    }
}
