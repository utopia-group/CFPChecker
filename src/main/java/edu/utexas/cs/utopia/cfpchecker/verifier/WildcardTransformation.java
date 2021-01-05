package edu.utexas.cs.utopia.cfpchecker.verifier;

import edu.utexas.cs.utopia.cfpchecker.speclang.APISpecCall;
import edu.utexas.cs.utopia.cfpchecker.speclang.Specification;
import edu.utexas.cs.utopia.cfpchecker.speclang.Wildcard;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.typing.ClassHierarchy;

import java.util.*;

import static edu.utexas.cs.utopia.cfpchecker.verifier.InstrumentationUtils.addOrGetLocal;
import static edu.utexas.cs.utopia.cfpchecker.verifier.InstrumentationUtils.getTempBoolLocal;
import static edu.utexas.cs.utopia.cfpchecker.verifier.VerifierUtil.reachableFromMain;

/**
 * Created by kferles on 7/2/18.
 */
public class WildcardTransformation extends SceneTransformer
{
    private Specification spec;

    private Map<Unit, APISpecCall> invokeStmtToSpecCalls = new HashMap<>();

    private Map<Type, Set<SootField>> wildcardFieldsPerType = new HashMap<>();

    private Map<SootField, Set<SootField>> distinctFieldsPerWildcard = new HashMap<>();

    private String getDistinctStaticFieldName(Specification spec, Wildcard w, SootMethod m) {
        assert w != Wildcard.DONT_CARE_VALUE : "Don't care valeus can't be distinct";

        return "Distinct_" + spec.hashCode() + "_" + w.hashCode() + "_" + m.getName();
    }

    private String getWildcardStaticFieldName(Specification spec, Wildcard w)
    {
        assert w != Wildcard.DONT_CARE_VALUE : "Don't care values don't have static fields";
        // WARNING: this assumes that each method in the spec has a distinct name.
        return "Spec_" + spec.hashCode() + "_" + w;
    }

    private Value generateEqualityConstraints(Stmt stmt, APISpecCall specCall, Body b)
    {
        Value rv = null;
        Wildcard[] params = specCall.getParams();
        SootMethod currMethod = b.getMethod();

        Jimple jimpleFactory = Jimple.v();

        PatchingChain<Unit> units = b.getUnits();

        for (int i = 0, e = params.length; i != e; i++)
        {
            Wildcard w = params[i];
            if (w != Wildcard.DONT_CARE_VALUE)
            {
                String wildcardStaticFieldName = getWildcardStaticFieldName(spec, w);

                SootClass mainClass = Scene.v().getMainClass();
                SootField wildcardField = mainClass.getFieldByName(wildcardStaticFieldName);
                Local wildCardLocal = addOrGetLocal(b, currMethod.getName() + "_wildcard_local_" + wildcardStaticFieldName, wildcardField.getType());
                InvokeExpr invokeExpr = stmt.getInvokeExpr();

                Value actualArg;
                if (invokeExpr instanceof InstanceInvokeExpr)
                {
                    InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) invokeExpr;
                    if (i == 0)
                        actualArg = instanceInvokeExpr.getBase();
                    else
                        actualArg = instanceInvokeExpr.getArg(i - 1);
                }
                else
                {
                    actualArg = invokeExpr.getArg(i);
                }

                Local tempBool = getTempBoolLocal(currMethod);
                units.insertBefore(jimpleFactory.newAssignStmt(wildCardLocal, jimpleFactory.newStaticFieldRef(wildcardField.makeRef())), stmt);
                EqExpr currComp = jimpleFactory.newEqExpr(wildCardLocal, actualArg);
                units.insertBefore(jimpleFactory.newAssignStmt(tempBool, currComp), stmt);

                if(w.isDistinct()) {
                    //Local distinctLocal = getTempBoolLocal(currMethod);
                    String distinctName = getDistinctStaticFieldName(spec, w, specCall.getMethod());
                    Local distinctLocal = addOrGetLocal(b, currMethod.getName() + "_distinct_local_" + distinctName, BooleanType.v());
                    SootField distinctField = mainClass.getFieldByName(distinctName);

                    units.insertBefore(jimpleFactory.newAssignStmt(distinctLocal, jimpleFactory.newStaticFieldRef(distinctField.makeRef())), stmt);
                    Local distinctNegation = getTempBoolLocal(currMethod);
                    units.insertBefore(jimpleFactory.newAssignStmt(distinctNegation, jimpleFactory.newNegExpr(distinctLocal)), stmt);

                    Local distinctCheck = getTempBoolLocal(currMethod);
                    units.insertBefore(jimpleFactory.newAssignStmt(distinctCheck, jimpleFactory.newAndExpr(tempBool, distinctNegation)), stmt);
                    tempBool = distinctCheck;
                }

                if (rv != null)
                {
                    Local conjTempBool = getTempBoolLocal(currMethod);
                    units.insertBefore(jimpleFactory.newAssignStmt(conjTempBool, jimpleFactory.newAndExpr(rv, tempBool)), stmt);
                    rv = conjTempBool;
                }
                else
                {
                    rv = tempBool;
                }
            }
        }

        assert rv != null : "Something went terribly wrong";
        return rv;
    }

    private void performSplits(Body b, Stmt apiCall, Set<APISpecCall> callInstances)
    {
        Jimple jimpleFactory = Jimple.v();
        PatchingChain<Unit> units = b.getUnits();

        CallGraph cg = Scene.v().getCallGraph();
        SootMethod tgMeth = apiCall.getInvokeExpr().getMethod();

        for (APISpecCall specCall : callInstances)
        {
            Value wildcardCheck = generateEqualityConstraints(apiCall, specCall, b);
            Unit currSucc = units.getSuccOf(apiCall);
            GotoStmt notEqualBB = jimpleFactory.newGotoStmt(currSucc);

            // Duplicate API call to capture potential side-effects.
            InvokeStmt dupApiCall = jimpleFactory.newInvokeStmt(apiCall.getInvokeExpr());
            units.insertBefore(dupApiCall, apiCall);
            units.insertBefore(notEqualBB, apiCall);

            if (tgMeth.hasActiveBody())
            {
                cg.addEdge(new Edge(b.getMethod(), dupApiCall, tgMeth));
            }

            IfStmt nonDetCheck = jimpleFactory.newIfStmt(jimpleFactory.newEqExpr(wildcardCheck, IntConstant.v(1)), apiCall);
            units.insertBefore(nonDetCheck, dupApiCall);

            invokeStmtToSpecCalls.put(apiCall, specCall);

            SootClass mainClass = Scene.v().getMainClass();

            for(Wildcard w : specCall.getParams()) {
                if(w.isDistinct()) {
                    String distinctName = getDistinctStaticFieldName(spec, w, specCall.getMethod());
                    SootField distinctField = mainClass.getFieldByName(distinctName);

                    AssignStmt updateDistinctStmt = jimpleFactory.newAssignStmt(jimpleFactory.newStaticFieldRef(distinctField.makeRef()), IntConstant.v(1));
                    units.insertAfter(updateDistinctStmt, apiCall);
                }
            }

            // Duplicate call statement and repeat!
            if (callInstances.size() > 1)
            {
                apiCall = jimpleFactory.newInvokeStmt(apiCall.getInvokeExpr());
                units.insertBefore(apiCall, currSucc);
                // TODO: Update  call graph
            }
        }
    }

    private void internalTransform(Body body)
    {
        Map<SootMethod, Set<APISpecCall>> specCallInstances = spec.getAPICallInstances();
        Set<Stmt> apiCallsWorkList = new HashSet<>();

        for (Unit u : body.getUnits())
        {
            u.apply(new AbstractStmtSwitch()
            {
                @Override
                public void caseInvokeStmt(InvokeStmt stmt)
                {
                    if (specCallInstances.containsKey(stmt.getInvokeExpr().getMethod()))
                    {
                        apiCallsWorkList.add(stmt);
                    }
                }

                @Override
                public void caseAssignStmt(AssignStmt stmt)
                {
                    Value rightOp = stmt.getRightOp();
                    if (rightOp instanceof InvokeExpr)
                    {
                        if (specCallInstances.containsKey(stmt.getInvokeExpr().getMethod()))
                        {
                            apiCallsWorkList.add(stmt);
                        }
                    }
                }
            });
        }

        for (Stmt inv : apiCallsWorkList)
        {
            performSplits(body, inv, specCallInstances.get(inv.getInvokeExpr().getMethod()));
        }
    }

    @Override
    protected void internalTransform(String s, Map<String, String> map)
    {
        SootClass mainClass = Scene.v().getMainClass();

        // Find immediate callers of APIs.
        for (Set<APISpecCall> specCalls : spec.getAPICallInstances().values())
        {
            for (APISpecCall specCall : specCalls)
            {
                SootMethod sootMethod = specCall.getMethod();
                boolean isStaticMeth = sootMethod.isStatic();

                List<Type> actualParamTypes = sootMethod.getParameterTypes();
                Wildcard[] wildcardParams = specCall.getParams();

                for (int i = 0, e = wildcardParams.length; i != e; ++i)
                {
                    Wildcard w = wildcardParams[i];
                    if (w != Wildcard.DONT_CARE_VALUE)
                    {
                        Type t;
                        if (!isStaticMeth)
                        {
                            if (i == 0)
                                t = sootMethod.getDeclaringClass().getType();
                            else
                                t = actualParamTypes.get(i - 1);
                        }
                        else
                            t = actualParamTypes.get(i);

                        String wildcardFieldName = getWildcardStaticFieldName(spec, w);

                        SootField wildcardFld = null;

                        if (mainClass.getFieldByNameUnsafe(wildcardFieldName) == null)
                        {
                            wildcardFld = new SootField(wildcardFieldName, t, Modifier.STATIC);
                            mainClass.addField(wildcardFld);

                            if (!wildcardFieldsPerType.containsKey(t))
                                wildcardFieldsPerType.put(t, new HashSet<>());

                            wildcardFieldsPerType.get(t).add(wildcardFld);
                        }
                        else {
                            wildcardFld = mainClass.getFieldByName(wildcardFieldName);
                            // Not the best place for this!
                            assert t.equals(wildcardFld.getType()) : "Specification is not well typed";
                        }

                        if(w.isDistinct()) {
                            String distinctName = getDistinctStaticFieldName(spec, w, sootMethod);

                            assert (mainClass.getFieldByNameUnsafe(distinctName) == null) : "Conflicting names for distinct flags";

                            SootField distinctField = new SootField(distinctName, BooleanType.v(), Modifier.STATIC);
                            mainClass.addField(distinctField);

                            if(!distinctFieldsPerWildcard.containsKey(wildcardFld)) {
                                distinctFieldsPerWildcard.put(wildcardFld, new HashSet<>());
                            }

                            distinctFieldsPerWildcard.get(wildcardFld).add(distinctField);
                        }
                    }
                }
            }
        }

        Set<SootMethod> workSet = new HashSet<>();
        for (SootClass cl : Scene.v().getClasses())
        {
            for (SootMethod m : cl.getMethods())
                if (VerifierUtil.mustAnalyze(spec.getApiClasses(), m) && reachableFromMain(m))
                    workSet.add(m);
        }

        for (SootMethod m : new HashSet<>(workSet))
            internalTransform(m.getActiveBody());
    }

    public WildcardTransformation(Specification spec)
    {
        this.spec = spec;
    }

    public Map<Unit, APISpecCall> getInvokeStmtToSpecCalls()
    {
        return invokeStmtToSpecCalls;
    }
}
