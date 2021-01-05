package edu.utexas.cs.utopia.cfpchecker.verifier;

import edu.utexas.cs.utopia.cfpchecker.speclang.Specification;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JimpleLocal;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.Chain;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

import static edu.utexas.cs.utopia.cfpchecker.verifier.VerifierUtil.mustAnalyze;
import static edu.utexas.cs.utopia.cfpchecker.verifier.VerifierUtil.reachableFromMain;

// TODO: This needs a major re-write. Currently, it does not raverse the call graph properly.
// It only does a bottom-up traversal, this has some issues for virtual calls with multiple outgoing edges.
public class StaticLocalizeTransformation extends SceneTransformer
{
    private class MethodRevisions {
        public ArrayList<SootField> addedParams;
        public HashMap<SootField, Local> substitutions;

        public MethodRevisions(ArrayList<SootField> addedParams, HashMap<SootField, Local> substitutions) {
            this.addedParams = addedParams;
            this.substitutions = substitutions;
        }
    }

    private Specification spec;

    private static Set<SootClass> newContainerClasses = new HashSet<>();

    public static Set<SootClass> getNewContainerClasses()
    {
        return newContainerClasses;
    }

    public StaticLocalizeTransformation(Specification spec)
    {
        this.spec = spec;
    }

    private SootClass createNewContainer(Type containerType) {
        Scene.v().loadClassAndSupport("java.lang.Object");
        String className = containerType.toString();
        String name = className.substring(className.lastIndexOf('.') + 1);
        SootClass container = new SootClass(name + "Container", Modifier.PUBLIC);
        container.setSuperclass(Scene.v().getSootClass("java.lang.Object"));
        container.addField(new SootField("ref", containerType, Modifier.PUBLIC));
        Scene.v().addClass(container);

        newContainerClasses.add(container);

        /*PrintWriter out = new PrintWriter(System.out);
        System.out.println(container.getName());
        for(SootField f : container.getFields()) {
            System.out.println(f.getSignature());
        }
        soot.Printer.v().printTo(container, out);*/
        return container;
    }

    private SootClass getContainer(HashMap<Type, SootClass> containers, Type t) {
        if(containers.containsKey(t)) {
            return containers.get(t);
        }
        SootClass container = createNewContainer(t);
        containers.put(t, container);
        return container;
    }

    private HashSet<SootField> collectDefFields(SootMethod m) {
        HashSet<SootField> definedFields = new HashSet<>();
        for(ValueBox box : m.getActiveBody().getDefBoxes()) {
            box.getValue().apply(new AbstractJimpleValueSwitch() {
                @Override
                public void caseStaticFieldRef(StaticFieldRef field) {
                    definedFields.add(field.getField());
                }
            });
        }

        return definedFields;
    }

    private HashSet<SootField> collectUsedFields(SootMethod m, HashSet<SootField> defs)
    {
        HashSet<SootField> usedFields = new HashSet<>();
        for (ValueBox box : m.getActiveBody().getUseAndDefBoxes())
        {
            box.getValue().apply(new AbstractJimpleValueSwitch() {
                @Override
                public void caseStaticFieldRef(StaticFieldRef field) {
                    SootField f = field.getField();
                    if(defs == null || !defs.contains(f)) {
                        usedFields.add(f);
                    }
                }
            });
        }

        return usedFields;
    }

    private void collectMethodCallees(SootMethod caller, HashMap<SootMethod, HashSet<SootMethod>> calleeCollection)
    {
        CallGraph cg = Scene.v().getCallGraph();

        Iterator<Edge> edges = cg.edgesOutOf(caller);

        while (edges.hasNext())
        {
            Edge e = edges.next();

            SootMethod callee = e.tgt();

            if (calleeCollection.containsKey(callee))
            {
                calleeCollection.get(callee).add(caller);
            }
            else
            {
                HashSet<SootMethod> callerSet = new HashSet<>();
                callerSet.add(caller);
                calleeCollection.put(callee, callerSet);
            }
        }
    }

    private void joinRequiredFields(HashMap<SootMethod, HashSet<SootField>> methodUsedFields, HashMap<SootMethod, HashSet<SootField>> methodDefinedFields, HashMap<SootMethod, HashSet<SootMethod>> callees)
    {
        HashSet<SootMethod> inList = new HashSet<>(methodUsedFields.keySet());
        inList.addAll(methodDefinedFields.keySet());
        Stack<SootMethod> worklist = new Stack<>();
        worklist.addAll(inList);

        while (!worklist.isEmpty())
        {
            SootMethod cur = worklist.pop();
            HashSet<SootField> calleeUsedFields = methodUsedFields.get(cur);
            HashSet<SootField> calleeDefinedFields = methodDefinedFields.get(cur);

            if (callees.containsKey(cur))
            {
                for (SootMethod caller : callees.get(cur))
                {
                    HashSet<SootField> callerUsedFields = methodUsedFields.get(caller);
                    HashSet<SootField> callerDefinedFields = methodDefinedFields.get(caller);
                    boolean fieldsModified = callerUsedFields.removeAll(calleeDefinedFields);
                    fieldsModified = callerUsedFields.addAll(calleeUsedFields) || fieldsModified;
                    fieldsModified = callerDefinedFields.addAll(calleeDefinedFields) || fieldsModified;

                    if(fieldsModified) {
                        if (!inList.contains(caller))
                        {
                            worklist.push(caller);
                            inList.add(caller);
                        }
                    }
                }
            }

            inList.remove(cur);
        }
    }

    private HashSet<SootField> getDefsOnlyUsedInCalls(SootMethod m, HashMap<SootMethod, HashSet<SootField>> methodDefinedFields) {
        HashSet<SootField> callerDefinedFields = methodDefinedFields.get(m);

        CallGraph cg = Scene.v().getCallGraph();

        Iterator<Edge> edges = cg.edgesOutOf(m);
        HashSet<SootField> calleeDefinedFields = new HashSet<>();

        while(edges.hasNext()) {
            SootMethod callee = edges.next().tgt();

            if(methodDefinedFields.containsKey(callee)) {
                calleeDefinedFields.addAll(methodDefinedFields.get(callee));
            }
        }

        HashSet<SootField> defsUsedInCalls = new HashSet<>();

        for(SootField f: callerDefinedFields) {
            if(!calleeDefinedFields.contains(f)) {
                defsUsedInCalls.add(f);
            }
        }

        return defsUsedInCalls;
    }

    private Local createNewLocal(Chain<Local> locals, String basename, Type t)
    {
        String name = null;
        int id = 0;

        while (name == null)
        {
            name = basename + id;
            for (Local l : locals)
            {
                if (l.getName().equals(name))
                {
                    name = null;
                    id++;
                    break;
                }
            }
        }

        return new JimpleLocal(name, t);
    }

    private Unit getIdentitySucc(PatchingChain<Unit> units)
    {
        Unit cur = units.getFirst();
        while (cur instanceof IdentityStmt)
        {
            cur = units.getSuccOf(cur);
        }

        return cur;
    }

    private HashMap<SootField, Local> declareStaticLocals(HashSet<SootField> usedFields, HashSet<SootField> definedFields, HashMap<Type, SootClass> containers)
    {
//        assert(definedFields.isEmpty() || !usedFields.containsAll(definedFields));
        SootMethod mainMethod = Scene.v().getMainMethod();
        Body body = mainMethod.getActiveBody();
        Chain<Local> locals = body.getLocals();

        HashMap<SootField, Local> substitutions = new HashMap<>();

        for (SootField field : usedFields)
        {
            Type localType = field.getType();
            Local fieldLocal = createNewLocal(locals, field.getName(), localType);
            locals.add(fieldLocal);
            substitutions.put(field, fieldLocal);
        }

        for(SootField field : definedFields) {
            Type localType = getContainer(containers, field.getType()).getType();
            Local fieldLocal = createNewLocal(locals, field.getName(), localType);
            locals.add(fieldLocal);
            substitutions.put(field, fieldLocal);
        }

        return substitutions;
    }

    private void initializeLocalStatics(HashMap<SootField, Local> substitutions, HashMap<Type, SootClass> containers)
    {
        Jimple factory = Jimple.v();
        SootMethod mainMethod = Scene.v().getMainMethod();
        Body body = mainMethod.getActiveBody();
        PatchingChain<Unit> units = body.getUnits();
        Chain<Local> locals = body.getLocals();

        Unit initLoc = getIdentitySucc(units);

        for (Map.Entry<SootField, Local> sub : substitutions.entrySet())
        {
            SootField field = sub.getKey();
            Local fieldLocal = sub.getValue();

            if(field.getType() == fieldLocal.getType()) {
                units.insertBefore(factory.newAssignStmt(sub.getValue(), factory.newStaticFieldRef(sub.getKey().makeRef())), initLoc);
            }
            else {
                SootClass containerClass = getContainer(containers, field.getType());
                units.insertBefore(factory.newAssignStmt(fieldLocal, factory.newNewExpr(RefType.v(containerClass))), initLoc);
                Local tmp = createNewLocal(locals, field.getName(), field.getType());
                units.insertBefore(factory.newAssignStmt(tmp, factory.newStaticFieldRef(field.makeRef())), initLoc);
                InstanceFieldRef fieldRef = factory.newInstanceFieldRef(fieldLocal, containerClass.getField("ref", field.getType()).makeRef());
                units.insertBefore(factory.newAssignStmt(fieldRef, tmp), initLoc);
            }
        }
    }

    private Local addParam(PatchingChain<Unit> units, Unit paramLoc, Chain<Local> locals, ArrayList<Type> paramTypes,  SootField field, Type localType) {
        Jimple factory = Jimple.v();
        Local fieldLocal = createNewLocal(locals, field.getName(), localType);
        locals.add(fieldLocal);
        IdentityStmt newIdentity = factory.newIdentityStmt(fieldLocal, factory.newParameterRef(localType, paramTypes.size()));
        paramTypes.add(localType);
        units.insertBefore(newIdentity, paramLoc);
        return fieldLocal;
    }

    private Map<SootClass, Integer> localizedCnt = new HashMap<>();
    private MethodRevisions addLocalStatics(SootMethod m, HashSet<SootField> usedFields, HashSet<SootField> definedFields, HashMap<Type, SootClass> containers)
    {
//        assert(definedFields.isEmpty() || !usedFields.containsAll(definedFields));

        Body body = m.getActiveBody();
        PatchingChain<Unit> units = body.getUnits();
        Unit paramLoc = getIdentitySucc(units);

        Chain<Local> locals = body.getLocals();
        ArrayList<Type> paramTypes = new ArrayList<>(m.getParameterTypes());
        ArrayList<SootField> addedParams = new ArrayList<>();
        HashMap<SootField, Local> substitutions = new HashMap<>();

        for(SootField field : usedFields) {
            Local fieldLocal = addParam(units, paramLoc, locals, paramTypes, field, field.getType());
            substitutions.put(field, fieldLocal);
            addedParams.add(field);
        }

        for(SootField field : definedFields) {
            Type localType = getContainer(containers, field.getType()).getType();
            Local fieldLocal = addParam(units, paramLoc, locals, paramTypes, field, localType);
            substitutions.put(field, fieldLocal);
            addedParams.add(field);
        }

        //update signature
        if (addedParams.size() > 0)
        {
            SootClass cl = m.getDeclaringClass();
            Integer lcCnt = localizedCnt.getOrDefault(cl, 0);
            localizedCnt.put(cl, lcCnt + 1);
            m.setName(m.getName() + "$localized$" + lcCnt);
            m.setParameterTypes(paramTypes);
        }

        return new MethodRevisions(addedParams, substitutions);
    }


    private void modifyInvocation(PatchingChain<Unit> units, Edge invocation, ArrayList<SootField> addedParams, HashMap<SootField, Local> srcSubstitutions, HashMap<SootField, Local> tgtSubstitutions, HashMap<Type, SootClass> containers)
    {
        Jimple factory = Jimple.v();
        Stmt invokeStmt = invocation.srcStmt();
        InvokeExpr oldExpr = invokeStmt.getInvokeExpr();
        SootMethod srcMethod = invocation.src();
        SootMethod tgtMethod = invocation.tgt();

        Chain<Local> locals = srcMethod.getActiveBody().getLocals();
        Unit callLoc = invocation.srcUnit();

        ArrayList<Value> args = new ArrayList<>(oldExpr.getArgs());
        for (SootField f : addedParams)
        {
            Local srcLocal = srcSubstitutions.get(f);
            Local tgtLocal = tgtSubstitutions.get(f);

            if(srcLocal.getType() == tgtLocal.getType()) {
                args.add(srcLocal);
            }
            else {
                //because we propagated defs down
                assert f.getType() == tgtLocal.getType();
                Local callLocal = createNewLocal(locals, f.getName(), f.getType());
                locals.add(callLocal);

                SootClass containerClass = getContainer(containers, f.getType());
                InstanceFieldRef fieldRef = factory.newInstanceFieldRef(srcLocal, containerClass.getField("ref", f.getType()).makeRef());
                units.insertBefore(factory.newAssignStmt(callLocal, fieldRef), callLoc);
                args.add(callLocal);
            }
        }

        AbstractExprSwitch exprSwitch = new AbstractExprSwitch() {
            Expr newExpr;

            @Override
            public void caseInterfaceInvokeExpr(InterfaceInvokeExpr v) {
                newExpr = factory.newVirtualInvokeExpr((Local) ((InterfaceInvokeExpr) oldExpr).getBase(), tgtMethod.makeRef(), args);
            }

            @Override
            public void caseSpecialInvokeExpr(SpecialInvokeExpr v) {
                newExpr = factory.newSpecialInvokeExpr((Local) ((SpecialInvokeExpr) oldExpr).getBase(), tgtMethod.makeRef(), args);
            }

            @Override
            public void caseStaticInvokeExpr(StaticInvokeExpr v) {
                newExpr = factory.newStaticInvokeExpr(tgtMethod.makeRef(), args);
            }

            @Override
            public void caseVirtualInvokeExpr(VirtualInvokeExpr v) {
                newExpr = factory.newVirtualInvokeExpr((Local) ((VirtualInvokeExpr) oldExpr).getBase(), tgtMethod.makeRef(), args);
            }

            @Override
            public void caseDynamicInvokeExpr(DynamicInvokeExpr v) {
                throw new UnsupportedOperationException("DynamicInvokeExpr is not supported");
            }

            @Override
            public void defaultCase(Object obj) {
                throw new UnsupportedOperationException("expected InvokeExpr");
            }

            @Override
            public Object getResult() {
                return newExpr;
            }
        };

        oldExpr.apply(exprSwitch);
        Expr newExpr = (Expr) exprSwitch.getResult();

        invokeStmt.apply(new AbstractStmtSwitch() {
            @Override
            public void caseInvokeStmt(InvokeStmt stmt) {
                stmt.setInvokeExpr(newExpr);
            }

            @Override
            public void caseAssignStmt(AssignStmt stmt) {
                stmt.setRightOp(newExpr);
            }

            @Override
            public void defaultCase(Object obj) {
                throw new UnsupportedOperationException("Unknown Method invocation");
            }
        });
    }

    private void propagateReplacements(HashMap<SootMethod, ArrayList<SootField>> addedParams, HashMap<SootMethod, HashMap<SootField, Local>> methodSubstitutions, HashMap<Type, SootClass> containers)
    {
        CallGraph cg = Scene.v().getCallGraph();
        Jimple factory = Jimple.v();

        for (SootMethod m : methodSubstitutions.keySet())
        {
            PatchingChain<Unit> units = m.getActiveBody().getUnits();
            HashMap<SootField, Local> substitutions = methodSubstitutions.get(m);

            Iterator<Edge> edges = cg.edgesOutOf(m);

            HashSet<Stmt> seenStmts = new HashSet<>();

            while (edges.hasNext())
            {
                Edge e = edges.next();

                SootMethod callee = e.tgt();

                Stmt srcStmt = e.srcStmt();

                if(!seenStmts.contains(srcStmt) && addedParams.containsKey(callee)) {
                    seenStmts.add(srcStmt);
                    modifyInvocation(units, e, addedParams.get(callee), substitutions, methodSubstitutions.get(callee), containers);
                }
            }

            for (Unit u : units)
            {
                for (ValueBox box : u.getUseAndDefBoxes())
                {
                    box.getValue().apply(new AbstractJimpleValueSwitch() {
                        @Override
                        public void caseStaticFieldRef(StaticFieldRef staticField) {
                            SootField field = staticField.getField();

                            assert substitutions.containsKey(field);

                            Local fieldLocal = substitutions.get(field);

                            if(fieldLocal.getType() == field.getType()) {
                                box.setValue(fieldLocal);
                            }
                            else {
                                SootClass containerClass = getContainer(containers, field.getType());
                                InstanceFieldRef fieldRef = factory.newInstanceFieldRef(fieldLocal, containerClass.getField("ref", field.getType()).makeRef());
                                box.setValue(fieldRef);
                            }
                        }
                    });
                }
            }
        }
    }

    private void localizeStatics(HashMap<SootMethod, HashSet<SootField>> methodUsedRefs, HashMap<SootMethod, HashSet<SootField>> methodDefinedRefs)
    {
        HashMap<SootMethod, ArrayList<SootField>> addedParams = new HashMap<>();
        HashMap<SootMethod, HashMap<SootField, Local>> substitutions = new HashMap<>();
        HashMap<Type, SootClass> containers = new HashMap<>();
        SootMethod mainMethod = Scene.v().getMainMethod();
        HashSet<SootMethod> methods = new HashSet<>(methodUsedRefs.keySet());
        methods.addAll(methodDefinedRefs.keySet());

        for (SootMethod m : methods)
        {
            HashSet<SootField> usedFields = methodUsedRefs.get(m);
            HashSet<SootField> definedFields = methodDefinedRefs.get(m);

            if (m.equals(mainMethod))
            {
                substitutions.put(m, declareStaticLocals(usedFields, definedFields, containers));
            }
            else
            {
                MethodRevisions revisions = addLocalStatics(m, usedFields, definedFields, containers);
                addedParams.put(m, revisions.addedParams);
                substitutions.put(m, revisions.substitutions);
            }
        }

        propagateReplacements(addedParams, substitutions, containers);
        if (substitutions.containsKey(mainMethod)) initializeLocalStatics(substitutions.get(mainMethod), containers);
    }

    @Override
    protected void internalTransform(String s, Map<String, String> options)
    {
        Chain<SootClass> classes = Scene.v().getClasses();
        HashMap<SootMethod, HashSet<SootField>> methodDefinedRefs = new HashMap<>();
        HashMap<SootMethod, HashSet<SootField>> methodUsedRefs = new HashMap<>();
        HashMap<SootMethod, HashSet<SootMethod>> calleeCollection = new HashMap<>();
        //HashSet<SootClass> changedClasses = new HashSet<SootClass>();

        for (SootClass c : classes)
        {
            for (SootMethod m : c.getMethods())
            {
                if (mustAnalyze(spec.getApiClasses(), m) && reachableFromMain(m))
                {
                    //changedClasses.add(c);
                    HashSet<SootField> definedRefs = collectDefFields(m);
                    methodDefinedRefs.put(m, definedRefs);
                    methodUsedRefs.put(m, collectUsedFields(m, definedRefs));
                    collectMethodCallees(m, calleeCollection);
                }
            }
        }

        joinRequiredFields(methodUsedRefs, methodDefinedRefs, calleeCollection);

        //static fields defined in main, but no where else can be treated like a use (don't need a container)
        SootMethod mainMethod = Scene.v().getMainMethod();
        if (methodDefinedRefs.containsKey(mainMethod))
        {
            HashSet<SootField> defsUsedInCalls = getDefsOnlyUsedInCalls(mainMethod, methodDefinedRefs);
            methodUsedRefs.get(mainMethod).addAll(defsUsedInCalls);
            methodDefinedRefs.get(mainMethod).removeAll(defsUsedInCalls);
        }

        localizeStatics(methodUsedRefs, methodDefinedRefs);

        /*PrintWriter out = new PrintWriter(System.out);
        for(SootClass c : changedClasses) {
            soot.Printer.v().printTo(c, out);
        }
        out.flush();*/
    }
}
