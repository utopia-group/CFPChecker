package edu.utexas.cs.utopia.cfpchecker.verifier;

import edu.utexas.cs.utopia.cfpchecker.speclang.Specification;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.Chain;

import java.util.*;
import java.util.stream.Collectors;

import static edu.utexas.cs.utopia.cfpchecker.verifier.VerifierUtil.reachableFromMain;

public class APICallRewriter extends SceneTransformer
{
    private Specification spec;

    private SootMethod tryLockWrapper = null;

    public APICallRewriter(Specification spec)
    {
        this.spec = spec;
    }

    @Override
    protected void internalTransform(String s, Map<String, String> map)
    {
        Set<SootClass> apiClasses = spec.getApiClasses();
        Set<SootClass> classesForRewrite = apiClasses.stream()
                                                     .filter(c -> c.getName().equals("java.util.concurrent.locks.ReentrantLock"))
                                                     .collect(Collectors.toSet());
        if (classesForRewrite.isEmpty()) return;

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

    private void internalTransform(Body mBody)
    {
        Map<Unit, Unit> unitsToReplace = new HashMap<>();
        Map<Unit, SootMethod> unitToWrapper = new HashMap<>();
        UnitPatchingChain mUnits = mBody.getUnits();
        for (Unit u : mUnits)
        {
            u.apply(new AbstractStmtSwitch()
            {
                @Override
                public void caseInvokeStmt(InvokeStmt stmt)
                {
                    InvokeExpr iExpr = stmt.getInvokeExpr();
                    Unit replacementUnit = getReplacementUnit(iExpr, null);
                    if (replacementUnit != null)
                    {
                        unitsToReplace.put(stmt, replacementUnit);
                        unitToWrapper.put(stmt, ((InvokeStmt)replacementUnit).getInvokeExpr().getMethod());
                    }
                }

                @Override
                public void caseAssignStmt(AssignStmt stmt)
                {
                    if (stmt.containsInvokeExpr())
                    {
                        InvokeExpr iExpr = stmt.getInvokeExpr();
                        Unit replacementUnit = getReplacementUnit(iExpr, stmt.getLeftOp());
                        if (replacementUnit != null)
                        {
                            unitsToReplace.put(stmt, replacementUnit);
                            unitToWrapper.put(stmt, ((AssignStmt)replacementUnit).getInvokeExpr().getMethod());
                        }
                    }
                }
            });
        }

        CallGraph cg = Scene.v().getCallGraph();
        for (Unit u : unitsToReplace.keySet())
        {
            Unit callToWrapper = unitsToReplace.get(u);
            mUnits.swapWith(u, callToWrapper);
            cg.addEdge(new Edge(mBody.getMethod(), callToWrapper, unitToWrapper.get(u), Kind.STATIC));
        }
    }

    private Unit getReplacementUnit(InvokeExpr invokeExpr, Value lhs)
    {
        Unit rv = null;

        SootMethod calledMethod = invokeExpr.getMethod();
        SootClass declaringClass = calledMethod.getDeclaringClass();

        // Not the best way to perform this check, but it will do the trick for now.
        if (calledMethod.getName().equals("tryLock") && declaringClass.getName().equals("java.util.concurrent.locks.ReentrantLock"))
        {
            Jimple jimpleGen = Jimple.v();

            if (tryLockWrapper == null)
            {

                // Create tryLockWrapper
                RefType receiverType = declaringClass.getType();
                tryLockWrapper = new SootMethod("tryLockWrapper", Collections.singletonList(receiverType), BooleanType.v(), Modifier.STATIC);
                Scene.v().getMainClass().addMethod(tryLockWrapper);

                Body mBody = new JimpleBody(tryLockWrapper);
                Chain<Local> locals = mBody.getLocals();
                UnitPatchingChain units = mBody.getUnits();

                Local receiver = jimpleGen.newLocal("receiver", receiverType);
                locals.add(receiver);

                Local ndChoice = jimpleGen.newLocal("ndChoice", BooleanType.v());
                locals.add(ndChoice);

                units.add(jimpleGen.newIdentityStmt(receiver, jimpleGen.newParameterRef(receiverType, 0)));

                Unit callToLock = jimpleGen.newInvokeStmt(jimpleGen.newVirtualInvokeExpr(receiver, declaringClass.getMethodByName("lock").makeRef()));
                units.add(callToLock);
                units.add(jimpleGen.newReturnStmt(IntConstant.v(1)));

                IfStmt noDetChoice = jimpleGen.newIfStmt(jimpleGen.newEqExpr(ndChoice, IntConstant.v(1)), callToLock);
                units.insertOnEdge(noDetChoice, units.getPredOf(callToLock), callToLock);

                units.insertAfter(jimpleGen.newReturnStmt(IntConstant.v(0)), noDetChoice);

                tryLockWrapper.setActiveBody(mBody);

                VerifierUtil.addToReachableMethods(tryLockWrapper);
            }

            if (lhs == null)
            {
                rv = jimpleGen.newInvokeStmt(jimpleGen.newStaticInvokeExpr(tryLockWrapper.makeRef(), ((VirtualInvokeExpr)invokeExpr).getBase()));
            }
            else
            {
                rv = jimpleGen.newAssignStmt(lhs, jimpleGen.newStaticInvokeExpr(tryLockWrapper.makeRef(), ((VirtualInvokeExpr)invokeExpr).getBase()));
            }
        }

        return rv;
    }
}
