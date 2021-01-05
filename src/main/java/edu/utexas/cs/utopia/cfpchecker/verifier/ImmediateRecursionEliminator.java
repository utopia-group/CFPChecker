package edu.utexas.cs.utopia.cfpchecker.verifier;

import edu.utexas.cs.utopia.cfpchecker.speclang.Specification;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.Chain;

import java.util.*;

import static edu.utexas.cs.utopia.cfpchecker.verifier.VerifierUtil.*;

/**
 * Created by kferles on 1/16/19.
 */
public class ImmediateRecursionEliminator extends SceneTransformer
{
    private Specification spec;

    public ImmediateRecursionEliminator(Specification spec)
    {
        this.spec = spec;
    }

    private enum InvokeKind
    {
        IFACE,
        SPECIAL,
        VIRTUAL,
        STATIC
    }

    private InvokeKind getInvokationKind(InvokeExpr invokeExpr)
    {
        AbstractExprSwitch exprSwitch = new AbstractExprSwitch()
        {
            InvokeKind result;

            @Override
            public void caseInterfaceInvokeExpr(InterfaceInvokeExpr v)
            {
                result = InvokeKind.IFACE;
            }

            @Override
            public void caseSpecialInvokeExpr(SpecialInvokeExpr v)
            {
                result = InvokeKind.SPECIAL;
            }

            @Override
            public void caseStaticInvokeExpr(StaticInvokeExpr v)
            {
                result = InvokeKind.STATIC;
            }

            @Override
            public void caseVirtualInvokeExpr(VirtualInvokeExpr v)
            {
                result = InvokeKind.VIRTUAL;
            }

            @Override
            public Object getResult()
            {
                return result;
            }
        };

        invokeExpr.apply(exprSwitch);

        return (InvokeKind) exprSwitch.getResult();
    }

    private SootClass getCommonCallingClass(Set<Unit> callsToReplace) {
        Iterator<Unit> it = callsToReplace.iterator();
        Unit cur = it.next();
        LinkedList<SootClass> commonClasses = new LinkedList<>();

        SootClass c = ((Stmt) cur).getInvokeExpr().getMethod().getDeclaringClass();
        commonClasses.addFirst(c);

        while(c.hasSuperclass()) {
            c = c.getSuperclass();
            commonClasses.add(c);
        }

        while(it.hasNext()) {
            cur = it.next();
            c = ((Stmt) cur).getInvokeExpr().getMethod().getDeclaringClass();

            int loc = commonClasses.indexOf(c);

            if(loc != -1) {
                commonClasses.subList(0, loc).clear();
            }
            else {
                while(c.hasSuperclass()) {
                    c = c.getSuperclass();
                    loc = commonClasses.indexOf(c);
                    if(commonClasses.contains(c)) {
                        commonClasses.subList(0, loc).clear();
                        break;
                    }
                    else {
                        commonClasses.removeFirst();
                    }
                }
            }

        }

        return commonClasses.getFirst();
    }

    private void internalTransfrom(SootMethod method) {
        Set<Unit> callsToReplace = new HashSet<>();

        CallGraph cg = Scene.v().getCallGraph();
        Iterator<Edge> edges = cg.edgesOutOf(method);

        InvokeKind invokeKind = InvokeKind.STATIC;
        Body methodBody = method.getActiveBody();
        PatchingChain<Unit> methodUnits = methodBody.getUnits();

        Set<Edge> invalidatedEdges = new HashSet<>();
        while(edges.hasNext()) {
            Edge e = edges.next();

            if(e.tgt().equals(method)) {
                callsToReplace.add(e.srcUnit());
                Stmt srcStmt = e.srcStmt();
                invalidatedEdges.addAll(edgedOutOf(srcStmt));
                invokeKind = getInvokationKind(srcStmt.getInvokeExpr());
            }
        }

        if(!callsToReplace.isEmpty()) {
            invalidatedEdges.forEach(cg::removeEdge);

            Jimple factory = Jimple.v();

            Type returnType = method.getReturnType();
            List<Type> newParameterTypes = new ArrayList<>(method.getParameterTypes());

            SootMethod calledMethod = method;
            if(!method.isStatic()) {
                /*Get common superclass*/
                SootClass commonClass = callsToReplace.size() > 1 ? getCommonCallingClass(callsToReplace) : calledMethod.getDeclaringClass();

                calledMethod = commonClass.getMethod(method.getSubSignature());
                newParameterTypes.add(0, commonClass.getType());
            }

            SootMethod fwdRecMeth = new SootMethod(method.getName() + "$rec_callsite", newParameterTypes, returnType,
                    Modifier.STATIC, method.getExceptions());

            method.getDeclaringClass().addMethod(fwdRecMeth);

            Body b = new JimpleBody(fwdRecMeth);
            fwdRecMeth.setActiveBody(b);

            VerifierUtil.addToReachableMethods(fwdRecMeth);

            PatchingChain<Unit> units = b.getUnits();

            List<Type> parameterTypes = fwdRecMeth.getParameterTypes();
            List<Local> parameters = new ArrayList<>();

            for (int i = 0, e = parameterTypes.size(); i < e; ++i) {
                Type t = parameterTypes.get(i);

                Local local = InstrumentationUtils.addOrGetLocal(b, "p" + i, t);
                parameters.add(local);

                units.add(factory.newIdentityStmt(local, factory.newParameterRef(t, i)));
            }

            InvokeExpr iExpr = null;

            switch (invokeKind) {
                case IFACE:
                    iExpr = factory.newInterfaceInvokeExpr(parameters.get(0), calledMethod.makeRef(), parameters.subList(1, parameters.size()));
                    break;
                case SPECIAL:
                    iExpr = factory.newSpecialInvokeExpr(parameters.get(0), calledMethod.makeRef(), parameters.subList(1, parameters.size()));
                    break;
                case VIRTUAL:
                    iExpr = factory.newVirtualInvokeExpr(parameters.get(0), calledMethod.makeRef(), parameters.subList(1, parameters.size()));
                    break;
                case STATIC:
                    iExpr = factory.newStaticInvokeExpr(calledMethod.makeRef(), parameters);
                    break;
                default:
                    assert false;
                    break;
            }

            Unit newRecCallUnit;
            if (!returnType.equals(VoidType.v()))
            {
                Local retVar = InstrumentationUtils.addOrGetLocal(b, "retVar", returnType);
                newRecCallUnit = factory.newAssignStmt(retVar, iExpr);
                units.add(newRecCallUnit);
                units.add(factory.newReturnStmt(retVar));
            }
            else
            {
                newRecCallUnit = factory.newInvokeStmt(iExpr);
                units.add(newRecCallUnit);
                units.add(factory.newReturnVoidStmt());
            }

            // Add new call-graph edges
            for (Edge e : invalidatedEdges)
            {
                cg.addEdge(new Edge(fwdRecMeth, newRecCallUnit, e.tgt(), Edge.ieToKind(iExpr)));
            }

            for (Unit u : callsToReplace)
            {
                u.apply(new AbstractStmtSwitch()
                {
                    @Override
                    public void caseInvokeStmt(InvokeStmt stmt)
                    {
                        InvokeExpr invokeExpr = stmt.getInvokeExpr();
                        List<Value> args = invokeExpr.getArgs();
                        if (invokeExpr instanceof InstanceInvokeExpr)
                        {
                            args.add(0, ((InstanceInvokeExpr) invokeExpr).getBase());
                        }

                        InvokeStmt newInvStmt = factory.newInvokeStmt(factory.newStaticInvokeExpr(fwdRecMeth.makeRef(), args));
                        methodUnits.swapWith(u, newInvStmt);
                        cg.addEdge(new Edge(method, newInvStmt, fwdRecMeth, Kind.STATIC));
                    }

                    @Override
                    public void caseAssignStmt(AssignStmt stmt)
                    {
                        InvokeExpr invokeExpr = stmt.getInvokeExpr();
                        Value retVar = stmt.getLeftOp();

                        List<Value> args = invokeExpr.getArgs();
                        if (invokeExpr instanceof InstanceInvokeExpr)
                        {
                            args.add(0, ((InstanceInvokeExpr) invokeExpr).getBase());
                        }

                        AssignStmt newStmt = factory.newAssignStmt(retVar, factory.newStaticInvokeExpr(fwdRecMeth.makeRef(), args));
                        methodUnits.swapWith(u, newStmt);
                        cg.addEdge(new Edge(method, newStmt, fwdRecMeth, Kind.STATIC));
                    }
                });
            }
        }
    }

    /*private void internalTransfrom(SootMethod method)
    {
        Set<Unit> callsToReplace = new HashSet<>();

        InvokeKind[] invokeKind = new InvokeKind[1];

        Body methodBody = method.getActiveBody();
        PatchingChain<Unit> methodUnits = methodBody.getUnits();
        for (Unit u : methodUnits)
        {
            u.apply(new AbstractStmtSwitch()
            {
                @Override
                public void caseInvokeStmt(InvokeStmt stmt)
                {
                    InvokeExpr invokeExpr = stmt.getInvokeExpr();
                    handleInvokeExpr(invokeExpr);
                }

                @Override
                public void caseAssignStmt(AssignStmt stmt)
                {
                    Value rightOp = stmt.getRightOp();

                    if (rightOp instanceof InvokeExpr)
                    {
                        handleInvokeExpr((InvokeExpr) rightOp);
                    }
                }

                private void handleInvokeExpr(InvokeExpr invExpr)
                {
                    if (invExpr.getMethod().equals(method))
                    {
                        callsToReplace.add(u);

                        switch (getInvokationKind(invExpr))
                        {

                            case IFACE:
                                invokeKind[0] = InvokeKind.IFACE;
                                break;
                            case SPECIAL:
                                invokeKind[0] = InvokeKind.SPECIAL;
                                break;
                            case VIRTUAL:
                                invokeKind[0] = InvokeKind.VIRTUAL;
                                break;
                            case STATIC:
                                invokeKind[0] = InvokeKind.STATIC;
                                break;
                            default:
                                assert false;
                                break;
                        }
                    }
                }
            });
        }

        Jimple jimpleFactory = Jimple.v();

        if (!callsToReplace.isEmpty())
        {
            Type returnType = method.getReturnType();
            List<Type> newParameterTypes = new ArrayList<>(method.getParameterTypes());

            if (!method.isStatic())
            {
                newParameterTypes.add(method.getDeclaringClass().getType());
            }

            SootMethod fwdRecMeth = new SootMethod(method.getName() + "$rec_callsite", newParameterTypes, returnType,
                                                      Modifier.STATIC, method.getExceptions());

            method.getDeclaringClass().addMethod(fwdRecMeth);

            Body b = new JimpleBody(fwdRecMeth);
            fwdRecMeth.setActiveBody(b);

            PatchingChain<Unit> units = b.getUnits();

            List<Type> parameterTypes = fwdRecMeth.getParameterTypes();
            List<Local> parameters = new ArrayList<>();

            for (int i = 0, e = parameterTypes.size(); i < e; ++i)
            {
                Type t = parameterTypes.get(i);

                Local local = InstrumentationUtils.addOrGetLocal(b, "p" + i, t);
                parameters.add(local);

                units.add(jimpleFactory.newIdentityStmt(local, jimpleFactory.newParameterRef(t, i)));
            }

            InvokeExpr iExpr = null;

            switch (invokeKind[0])
            {
                case IFACE:
                    iExpr = jimpleFactory.newInterfaceInvokeExpr(parameters.get(0), method.makeRef(), parameters.subList(1, parameters.size()));
                    break;
                case SPECIAL:
                    iExpr = jimpleFactory.newSpecialInvokeExpr(parameters.get(0), method.makeRef(), parameters.subList(1, parameters.size()));
                    break;
                case VIRTUAL:
                    iExpr = jimpleFactory.newVirtualInvokeExpr(parameters.get(0), method.makeRef(), parameters.subList(1, parameters.size()));
                    break;
                case STATIC:
                    iExpr = jimpleFactory.newStaticInvokeExpr(method.makeRef(), parameters);
                    break;
                default:
                    assert false;
                    break;
            }

            if (!returnType.equals(VoidType.v()))
            {
                Local retVar = InstrumentationUtils.addOrGetLocal(b, "retVar", returnType);
                units.add(jimpleFactory.newAssignStmt(retVar, iExpr));
                units.add(jimpleFactory.newReturnStmt(retVar));
            }
            else
            {
                units.add(jimpleFactory.newInvokeStmt(iExpr));
                units.add(jimpleFactory.newReturnVoidStmt());
            }

            for (Unit u : callsToReplace)
            {
                u.apply(new AbstractStmtSwitch()
                {
                    @Override
                    public void caseInvokeStmt(InvokeStmt stmt)
                    {
                        InvokeExpr invokeExpr = stmt.getInvokeExpr();
                        List<Value> args = invokeExpr.getArgs();
                        if (invokeExpr instanceof InstanceInvokeExpr)
                        {
                            args.add(0, ((InstanceInvokeExpr) invokeExpr).getBase());
                        }

                        methodUnits.swapWith(u, jimpleFactory.newInvokeStmt(jimpleFactory.newStaticInvokeExpr(fwdRecMeth.makeRef(), args)));
                    }

                    @Override
                    public void caseAssignStmt(AssignStmt stmt)
                    {
                        InvokeExpr invokeExpr = stmt.getInvokeExpr();
                        Value retVar = stmt.getLeftOp();

                        List<Value> args = invokeExpr.getArgs();
                        if (invokeExpr instanceof InstanceInvokeExpr)
                        {
                            args.add(0, ((InstanceInvokeExpr) invokeExpr).getBase());
                        }

                        methodUnits.swapWith(u, jimpleFactory.newAssignStmt(retVar, jimpleFactory.newStaticInvokeExpr(fwdRecMeth.makeRef(), args)));
                    }
                });
            }
        }
    }*/

    @Override
    protected void internalTransform(String s, Map<String, String> options)
    {
        Chain<SootClass> classes = Scene.v().getClasses();
        Set<SootMethod> workSet = new HashSet<>();

        for (SootClass c : classes)
        {
            for (SootMethod m : c.getMethods())
            {
                if (mustAnalyze(spec.getApiClasses(), m) && reachableFromMain(m))
                    workSet.add(m);
            }
        }

        workSet.forEach(this::internalTransfrom);
    }

}
