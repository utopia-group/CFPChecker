package edu.utexas.cs.utopia.cfpchecker.verifier;

import edu.utexas.cs.utopia.cfpchecker.expression.AbstractExprTransformer;
import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.ExprUtils;
import edu.utexas.cs.utopia.cfpchecker.expression.factory.ExprFactory;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncApp;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncDecl;
import edu.utexas.cs.utopia.cfpchecker.expression.terminal.ConstBool;
import edu.utexas.cs.utopia.cfpchecker.expression.terminal.ConstInteger;
import edu.utexas.cs.utopia.cfpchecker.expression.type.*;
import edu.utexas.cs.utopia.cfpchecker.expression.type.BooleanType;
import edu.utexas.cs.utopia.cfpchecker.expression.type.IntegerType;
import edu.utexas.cs.utopia.cfpchecker.speclang.Specification;
import org.apache.commons.lang.Validate;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JArrayRef;

import java.math.BigInteger;
import java.util.*;

import static edu.utexas.cs.utopia.cfpchecker.expression.ExprUtils.isVar;
import static edu.utexas.cs.utopia.cfpchecker.verifier.AnalysisUtils.*;
import static edu.utexas.cs.utopia.cfpchecker.verifier.VerifierUtil.targetMethodsOf;
import static edu.utexas.cs.utopia.cfpchecker.verifier.VerifierUtil.mustAnalyze;

/**
 * Created by yufeng on 7/10/18.
 */
public class StrongestPostconditionCalculator
{
    private SootMethod currMethod = Scene.v().getMainMethod();

    private ExprFactory exprFactory;

    private Specification spec;

    private Stack<SootMethod> callStack = new Stack<>();

    private List<Expr> traceFormula;

    private Map<Expr, Expr> currFreshVarMap = new HashMap<>();

    private Stack<Map<Expr, Expr>> freshVarMapStack = new Stack<>();

    private Stack<Map<Expr, Expr>> oldParamValsStack = new Stack<>();

    private Map<Expr, Integer> freshExprCount = new HashMap<>();

    private Map<GotoStmt, Unit> returnLocations;

    private void addToTraceFormula(Expr expr)
    {
        traceFormula.add(expr);
    }

    private Expr getFormalToActualExpr(InvokeExpr invokeExpr, ExprGeneratorSwitch callerExpGen, ExprGeneratorSwitch calleeExpGen)
    {
        SootMethod calledMethod = invokeExpr.getMethod();
        Body methodBody = calledMethod.getActiveBody();
        List<Value> parameterRefs = methodBody.getParameterRefs();
        Expr argsExpr = exprFactory.mkTRUE();

        if (!calledMethod.isStatic())
        {
            Value base = ((InstanceInvokeExpr) invokeExpr).getBase();
            Value thisSym = ((IdentityStmt)methodBody.getUnits().getFirst()).getRightOp();

            thisSym.apply(calleeExpGen);
            base.apply(callerExpGen);

            Expr baseExp = skolemize(callerExpGen.getResult());
            argsExpr = exprFactory.mkAND(argsExpr, exprFactory.mkEQ(baseExp, skolemize(calleeExpGen.getResult())),
                                         // Assume base is not null.
                                         exprFactory.mkNEG(exprFactory.mkEQ(baseExp, exprFactory.mkINT(BigInteger.valueOf(0)))));

            // TODO: the following is a bit problematic with some benchmarks.
            // We need a more precise modeling for dynamic type information.
//            if (invokeExpr instanceof VirtualInvokeExpr)
//            {
//                Type baseTy = calledMethod.getDeclaringClass().getType();
//                FuncApp dTypeAr = AnalysisUtils.getDTypeArray(exprFactory);
//                argsExpr = exprFactory.mkAND(argsExpr,
//                                             exprFactory.mkEQ(exprFactory.mkSelectExpr(dTypeAr, baseExp), exprFactory.mkINT(BigInteger.valueOf(baseTy.hashCode())))
//                                             );
//            }
        }

        for (int i = 0, e = invokeExpr.getArgCount(); i < e; ++i)
        {
            Value formalParam = parameterRefs.get(i);
            Value actualParam = invokeExpr.getArg(i);
            formalParam.apply(calleeExpGen);
            actualParam.apply(callerExpGen);
            Expr actualParamExp = skolemize(callerExpGen.getResult());
            Expr formalParamExp = skolemize(calleeExpGen.getResult());

            actualParamExp = fixRHSConstants(formalParamExp.getType(), actualParamExp);

            argsExpr = exprFactory.mkAND(argsExpr, exprFactory.mkEQ(formalParamExp, actualParamExp));
        }

        for (SootField fld : getRelevantFieldsForMethod(calledMethod))
        {
            Expr symVar = skolemize(getSymVarForField(exprFactory, fld, calledMethod));
            Expr callerSymStore = skolemize(getArrayVar(exprFactory, fld, currMethod));

            argsExpr = exprFactory.mkAND(argsExpr, exprFactory.mkEQ(symVar, callerSymStore));
        }

        Expr symAllocVar = skolemize(getSymAllocArray(exprFactory, calledMethod));
        Expr callerAllocVar = skolemize(getAllocArrayVar(exprFactory, currMethod));

        argsExpr = exprFactory.mkAND(argsExpr, exprFactory.mkEQ(symAllocVar, callerAllocVar));

        if (traceFormula != null)
        {
            addToTraceFormula(argsExpr);
        }

        return argsExpr;
    }

    private Expr createNewFreshVar(FuncApp e)
    {
        assert isVar(e) : e;

        int varCount = freshExprCount.getOrDefault(e, 0);
        freshExprCount.put(e, varCount + 1);

        FuncDecl funDecl = e.getDecl();
        String currDeclName = funDecl.getName();
        int freshIdx = currDeclName.indexOf(FRESH_VAR_SUFFIX);

        String newDeclName = currDeclName.substring(0, freshIdx != -1 ? freshIdx : currDeclName.length()) + FRESH_VAR_SUFFIX + varCount;
        FuncApp newExpr = exprFactory.mkFAPP(exprFactory.mkFDECL(newDeclName, funDecl.getType()));

        currFreshVarMap.put(e, newExpr);

        return newExpr;
    }

    Expr skolemize(Expr e)
    {
        return e.accept(new AbstractExprTransformer(exprFactory)
        {
            @Override
            public Expr visit(FuncApp e)
            {
                if (!isVar(e))
                {
                    List<Expr> newArgs = new ArrayList<>();
                    for (Expr arg : e)
                    {
                        newArgs.add(arg.accept(this));
                    }

                    return exprFactory.mkFAPP(e.getDecl(), newArgs);
                }

                if (!currFreshVarMap.containsKey(e))
                    createNewFreshVar(e);

                return currFreshVarMap.get(e);
            }
        });
    }

    private void putSymVarsInScope(SootMethod method)
    {
        ExprGeneratorSwitch exprGen = new ExprGeneratorSwitch(exprFactory, method);
        Body methodBody = method.getActiveBody();
        if (!method.isStatic())
        {
            Value thisSym = ((IdentityStmt)methodBody.getUnits().getFirst()).getRightOp();

            thisSym.apply(exprGen);
            createNewFreshVar((FuncApp) exprGen.getResult());
        }

        for (Value formalParam : methodBody.getParameterRefs())
        {
            formalParam.apply(exprGen);
            createNewFreshVar((FuncApp) exprGen.getResult());
        }

        Set<SootField> fieldWriteSet = TransitiveReadWriteSetGenerator.v().getFieldWriteSet(method);
        for (SootField fld : getRelevantFieldsForMethod(method))
        {
            createNewFreshVar(getSymVarForField(exprFactory, fld, method));

            if (fieldWriteSet.contains(fld))
                createNewFreshVar(getSymRetForField(exprFactory, fld, method));
        }

        createNewFreshVar(getSymAllocArray(exprFactory, method));
        createNewFreshVar(getSymRetForAllocArray(exprFactory, method));

        if (!method.getReturnType().equals(VoidType.v()))
        {
            createNewFreshVar(getSymRetVarForMethod(exprFactory, method));
        }
    }

    private void putLocalVarsInScope(SootMethod method)
    {
        // Create a copy of all local state.
        ExprGeneratorSwitch exprGen = new ExprGeneratorSwitch(exprFactory, method);
        Body methodBody = method.getActiveBody();

        for (Local local : methodBody.getLocals())
        {
            local.apply(exprGen);
            createNewFreshVar((FuncApp) exprGen.getResult());
        }

        for (SootField fld : getRelevantFieldsForMethod(method))
        {
            createNewFreshVar(getArrayVar(exprFactory, fld, method));
        }

        createNewFreshVar(getAllocArrayVar(exprFactory, method));
    }

    private Expr fixRHSConstants(ExprType leftExprTy, Expr rightExpr)
    {
        if (leftExprTy == IntegerType.getInstance() && rightExpr instanceof ConstBool)
        {
            return boolToInt(exprFactory, (ConstBool) rightExpr);
        }

        return rightExpr;
    }

    StrongestPostconditionCalculator(ExprFactory exprFactory, Specification spec, SootMethod currMethod, Map<GotoStmt, Unit> returnLocations)
    {
        this.exprFactory = exprFactory;
        this.currMethod = currMethod;
        this.spec = spec;
        this.returnLocations = returnLocations;

        putLocalVarsInScope(currMethod);
        putSymVarsInScope(currMethod);
    }

    StrongestPostconditionCalculator(ExprFactory exprFactory, Specification spec, List<Expr> traceFormula, Map<GotoStmt, Unit> returnLocations)
    {
        Validate.notNull(traceFormula);

        this.exprFactory = exprFactory;
        this.traceFormula = traceFormula;
        this.spec = spec;
        this.returnLocations = returnLocations;
    }

    Expr sp(final List<Unit> cex)
    {
        return sp(cex, exprFactory.mkTRUE());
    }

    Expr sp(final List<Unit> cex, Expr pre)
    {
        Expr sp = pre;
        for (Unit currStmt : cex)
        {
            StmtPostcondGenSwitch postCondGen = new StmtPostcondGenSwitch(exprFactory, sp);
            currStmt.apply(postCondGen);
            sp = postCondGen.getPostCond();
        }

        return ExprUtils.booleanSimplification(exprFactory, sp);
    }

    Expr sp(Unit u, Expr pre)
    {
        Expr sp = pre;

        StmtPostcondGenSwitch postCondGen = new StmtPostcondGenSwitch(exprFactory, sp);
        u.apply(postCondGen);
        sp = postCondGen.getPostCond();

        if (!callStack.isEmpty())
        {
            currMethod = callStack.pop();
            popContext();
        }

        return ExprUtils.booleanSimplification(exprFactory, sp);
    }

    void createNewContext(SootMethod method)
    {
        Map<Expr, Expr> oldParamVals = new HashMap<>();

        Body methodBody = method.getActiveBody();
        List<Value> parameterRefs = methodBody.getParameterRefs();

        ExprGeneratorSwitch exprGen = new ExprGeneratorSwitch(exprFactory, method);

        if (!method.isStatic())
        {
            Value thisSym = ((IdentityStmt)methodBody.getUnits().getFirst()).getRightOp();

            thisSym.apply(exprGen);

            Expr calleeFormalArg = exprGen.getResult();

            if (currFreshVarMap.containsKey(calleeFormalArg))
                oldParamVals.put(calleeFormalArg, currFreshVarMap.get(calleeFormalArg));
        }

        for (int i = 0, e = parameterRefs.size(); i < e; ++i)
        {
            Value formalParam = parameterRefs.get(i);
            formalParam.apply(exprGen);
            Expr calleeFormalArg = exprGen.getResult();

            if (currFreshVarMap.containsKey(calleeFormalArg))
                oldParamVals.put(calleeFormalArg, currFreshVarMap.get(calleeFormalArg));
        }

        for (SootField fld : getRelevantFieldsForMethod(method))
        {
            Expr symStoreVar = getSymVarForField(exprFactory, fld, method);

            if (currFreshVarMap.containsKey(symStoreVar))
                oldParamVals.put(symStoreVar, currFreshVarMap.get(symStoreVar));
        }

        putSymVarsInScope(method);

        callStack.push(currMethod);

        oldParamValsStack.push(oldParamVals);
    }

    void restoreFormalArguments()
    {
        oldParamValsStack.pop().forEach((key, value) -> currFreshVarMap.put(key, value));
    }

    private void popContext()
    {
        if (!freshVarMapStack.isEmpty())
            currFreshVarMap = freshVarMapStack.pop();

        if (!oldParamValsStack.isEmpty())
            restoreFormalArguments();
    }

    class StmtPostcondGenSwitch extends AbstractStmtSwitch
    {
        private ExprFactory exprFactory;

        private Expr postCond;

        private Expr preCond;

        StmtPostcondGenSwitch(ExprFactory exprFactory, Expr preCond)
        {
            this.exprFactory = exprFactory;
            this.preCond = preCond;
        }

        Expr getPostCond()
        {
            return postCond;
        }

        SootMethod getCurrMethod()
        {
            return currMethod;
        }

        @Override
        public void caseAssignStmt(AssignStmt stmt)
        {
            ExprGeneratorSwitch exprGen = new ExprGeneratorSwitch(exprFactory, getCurrMethod());
            Value leftOp = stmt.getLeftOp();
            Value rightOp = stmt.getRightOp();

            if (isInstanceFieldRef(leftOp))
            {
                InstanceFieldRef fieldRef = (InstanceFieldRef) leftOp;
                // Store operation
                FuncApp arrayVar = getArrayVar(exprFactory, fieldRef.getField(), currMethod);
                Expr base = getFuncAppExpr(exprFactory, currMethod, fieldRef.getBase());
                rightOp.apply(exprGen);

                Expr rightExpr = fixRHSConstants(((FunctionType)arrayVar.getType()).getCoDomain(), skolemize(exprGen.getResult()));

                Expr storeExpr = exprFactory.mkStoreExpr((FuncApp) skolemize(arrayVar), skolemize(base), rightExpr);
                Expr newMemStore = createNewFreshVar(arrayVar);
                Expr notNull = exprFactory.mkNEG(exprFactory.mkEQ(base, exprFactory.mkINT(BigInteger.valueOf(0))));

                Expr currConstraint = exprFactory.mkAND(exprFactory.mkEQ(newMemStore, storeExpr), notNull);
                postCond = exprFactory.mkAND(preCond, currConstraint);

                if (traceFormula != null)
                {
                    traceFormula.add(currConstraint);
                }
            }
            else if (isArrayRef(leftOp))
            {
                // TODO: Make this more precise.
                // Currently we just havoc everything at loads.
                postCond = preCond;

                if (traceFormula != null)
                {
                    traceFormula.add(exprFactory.mkTRUE());
                }
            }
            else
            {

                leftOp.apply(exprGen);
                Expr leftExpr = exprGen.getResult();


                if (isAllocationExpr(rightOp))
                {
                    // Create new symbolic object
                    rightOp.apply(exprGen);

                    Expr allocArray = getAllocArrayVar(exprFactory, currMethod);

                    Expr newFreshVar = createNewFreshVar((FuncApp) leftExpr);
                    FuncApp oldAllocArray = (FuncApp) skolemize(allocArray);
                    FuncApp dTypeAr = AnalysisUtils.getDTypeArray(exprFactory);
                    Type newTy = rightOp.getType();
                    Expr currConstraint = exprFactory.mkAND(exprFactory.mkNEG(exprFactory.mkSelectExpr(oldAllocArray, newFreshVar)),
                                                            exprFactory.mkNEG(exprFactory.mkEQ(newFreshVar, exprFactory.mkINT(new BigInteger("0")))),
                                                            exprFactory.mkEQ(exprFactory.mkSelectExpr(dTypeAr, newFreshVar), exprFactory.mkINT(BigInteger.valueOf(newTy.hashCode()))),
                                                            exprFactory.mkEQ(createNewFreshVar((FuncApp) allocArray), exprFactory.mkStoreExpr(oldAllocArray, newFreshVar, exprFactory.mkTRUE())));

                    postCond = exprFactory.mkAND(preCond, currConstraint);

                    if (traceFormula != null)
                    {
                        traceFormula.add(currConstraint);
                    }

                    return;
                }

                postCond = preCond;

                SootMethod newMeth = null;
                if (isInvokeExpr(rightOp))
                {
                    InvokeExpr invExpr = (InvokeExpr) rightOp;
                    SootMethod calledMeth = invExpr.getMethod();

                    if (mustAnalyze(spec.getApiClasses(), calledMeth) && targetMethodsOf(stmt).contains(calledMeth))
                    {
                        ExprGeneratorSwitch calleeExpGen = new ExprGeneratorSwitch(exprFactory, calledMeth);

                        newMeth = calledMeth;
                        createNewContext(newMeth);

                        postCond = exprFactory.mkAND(getFormalToActualExpr(invExpr, exprGen, calleeExpGen), postCond);
                    }
                    else
                    {
                        if (traceFormula != null)
                        {
                            addToTraceFormula(exprFactory.mkTRUE());
                        }
                    }

                    createNewFreshVar((FuncApp) leftExpr);
                }
                else
                {

                    Type expTy = leftOp.getType();
                    Expr currConstraint;
                    if (expTy instanceof FloatType || expTy instanceof DoubleType)
                    {
                        // Just havoc target
                        createNewFreshVar((FuncApp) leftExpr);
                        currConstraint = exprFactory.mkTRUE();
                    }
                    else
                    {
                        rightOp.apply(exprGen);
                        Expr rightExpr = skolemize(exprGen.getResult());

                        rightExpr = fixRHSConstants(leftExpr.getType(), rightExpr);

                        currConstraint = exprFactory.mkEQ(createNewFreshVar((FuncApp) leftExpr), rightExpr);
                        postCond = exprFactory.mkAND(currConstraint, postCond);
                    }

                    if (traceFormula != null)
                    {
                        addToTraceFormula(currConstraint);
                    }
                }

                if (newMeth != null)
                {
                    freshVarMapStack.push(currFreshVarMap);
                    currFreshVarMap = new HashMap<>(currFreshVarMap);

                    currMethod = newMeth;
                }
            }
        }

        @Override
        public void caseGotoStmt(GotoStmt stmt)
        {
            postCond = preCond;

            Expr currConstraint = exprFactory.mkTRUE();

            if (returnLocations.containsKey(stmt))
            {
                // TODO: probably we need to do something about virtual calls.
                Unit callStmt = returnLocations.get(stmt);

                SootMethod calledMeth;
                Value retVar = null;

                if (callStmt instanceof InvokeStmt)
                {
                    calledMeth = ((InvokeStmt) callStmt).getInvokeExpr().getMethod();
                }
                else
                {
                    assert callStmt instanceof AssignStmt;
                    AssignStmt assignStmt = (AssignStmt) callStmt;
                    calledMeth = assignStmt.getInvokeExpr().getMethod();
                    retVar = assignStmt.getLeftOp();
                }

                for (SootField fld : TransitiveReadWriteSetGenerator.v().getFieldWriteSet(calledMeth))
                {
                    Expr newCallerStore = createNewFreshVar(getArrayVar(exprFactory, fld, currMethod));
                    currConstraint = exprFactory.mkAND(currConstraint, exprFactory.mkEQ(newCallerStore, skolemize(getSymRetForField(exprFactory, fld, calledMeth))));
                }

                Expr newCallerAllocArray = createNewFreshVar(getAllocArrayVar(exprFactory, currMethod));
                currConstraint = exprFactory.mkAND(currConstraint, exprFactory.mkEQ(newCallerAllocArray, skolemize(getSymRetForAllocArray(exprFactory, calledMeth))));

                if (retVar != null)
                {
                    ExprGeneratorSwitch exprSw = new ExprGeneratorSwitch(exprFactory, currMethod);
                    retVar.apply(exprSw);

                    currConstraint = exprFactory.mkAND(currConstraint, exprFactory.mkEQ(skolemize(exprSw.getResult()), skolemize(getSymRetVarForMethod(exprFactory, calledMeth))));
                }

                postCond = exprFactory.mkAND(postCond, currConstraint);
            }

            if (traceFormula != null)
            {
                addToTraceFormula(currConstraint);
            }
        }

        // Stupid Soot.
        private int getIdentityStmtIndex(IdentityStmt stmt)
        {
            String rightOp = stmt.getRightOp().toString();
            String parameter = "parameter", thisStr = "@this";
            if (rightOp.contains(parameter))
            {
                int paramIdx = rightOp.indexOf(parameter);
                int colonIdx = rightOp.indexOf(":", paramIdx);
                int rv = Integer.parseInt(rightOp.substring(paramIdx + parameter.length(), colonIdx));
                return currMethod.isStatic() ? rv : rv + 1;
            }
            else
            {
                assert rightOp.contains(thisStr);
                return 0;
            }
        }

        @Override
        public void caseIdentityStmt(IdentityStmt stmt)
        {
            ExprGeneratorSwitch exprGen = new ExprGeneratorSwitch(exprFactory, getCurrMethod());

            stmt.getRightOp().apply(exprGen);
            Expr rightExpr = skolemize(exprGen.getResult());

            stmt.getLeftOp().apply(exprGen);
            Expr leftExpr = createNewFreshVar((FuncApp) exprGen.getResult());

            Expr currConstraint = exprFactory.mkEQ(leftExpr, rightExpr);

            if (getIdentityStmtIndex(stmt) == 0)
            {
                for (SootField fld : getRelevantFieldsForMethod(currMethod))
                {
                    Expr storeVar = createNewFreshVar(getArrayVar(exprFactory, fld, currMethod));
                    Expr storeSymVar = skolemize(getSymVarForField(exprFactory, fld, currMethod));
                    currConstraint = exprFactory.mkAND(currConstraint, exprFactory.mkEQ(storeVar, storeSymVar));
                }

                Expr allocVar = createNewFreshVar(getAllocArrayVar(exprFactory, currMethod));
                Expr allocSymVar = skolemize(getSymAllocArray(exprFactory, currMethod));
                currConstraint = exprFactory.mkAND(currConstraint, exprFactory.mkEQ(allocVar, allocSymVar));
            }

            postCond = exprFactory.mkAND(preCond, currConstraint);

            if (traceFormula != null)
            {
                addToTraceFormula(currConstraint);
            }
        }

        @Override
        public void caseIfStmt(IfStmt stmt)
        {
            //Treat it as no-op cuz we have assume statements
            postCond = preCond;

            if (traceFormula != null)
            {
                addToTraceFormula(exprFactory.mkTRUE());
            }
        }

        private Expr handleReturnStmt(Value returnVal)
        {
            Expr currConstraint = exprFactory.mkTRUE();

            if (returnVal != null)
            {
                ExprGeneratorSwitch exprSw = new ExprGeneratorSwitch(exprFactory, currMethod);
                returnVal.apply(exprSw);

                Expr symRet = skolemize(getSymRetVarForMethod(exprFactory, currMethod));
                Expr retVal = fixRHSConstants(symRet.getType(), skolemize(exprSw.getResult()));

                currConstraint = exprFactory.mkAND(currConstraint, exprFactory.mkEQ(symRet, retVal));
            }

            for (SootField fld : TransitiveReadWriteSetGenerator.v().getFieldWriteSet(currMethod))
            {
                currConstraint = exprFactory.mkAND(currConstraint, exprFactory.mkEQ(skolemize(getSymRetForField(exprFactory, fld, currMethod)),
                                                                                    skolemize(getArrayVar(exprFactory, fld, currMethod))));
            }

            currConstraint = exprFactory.mkAND(currConstraint, exprFactory.mkEQ(skolemize(getSymRetForAllocArray(exprFactory, currMethod)),
                                                                                skolemize(getAllocArrayVar(exprFactory, currMethod))));

            postCond = exprFactory.mkAND(preCond, currConstraint);

            if (!callStack.isEmpty())
            {
                currMethod = callStack.pop();
            }

            popContext();

            return currConstraint;
        }

        @Override
        public void caseReturnVoidStmt(ReturnVoidStmt stmt)
        {
            Expr currConstraint = handleReturnStmt(null);

            if (traceFormula != null)
            {
                addToTraceFormula(currConstraint);
            }
        }

        @Override
        public void caseReturnStmt(ReturnStmt stmt)
        {
            Expr currConstraint = handleReturnStmt(stmt.getOp());

            if (traceFormula != null)
            {
                addToTraceFormula(currConstraint);
            }
        }

        @Override
        public void caseInvokeStmt(InvokeStmt stmt)
        {
            SootMethod callee = stmt.getInvokeExpr().getMethod();
            if (Assumify.assumeMethod.equals(callee))
            {
                ExprGeneratorSwitch exprGen = new ExprGeneratorSwitch(exprFactory, getCurrMethod());
                stmt.getInvokeExpr().getArg(0).apply(exprGen);
                Expr cond = skolemize(exprGen.getResult());
                postCond = exprFactory.mkAND(preCond, cond);

                if (traceFormula != null)
                {
                    addToTraceFormula(cond);
                }
            }
            else if (Assumify.assumeNotMethod.equals(callee))
            {
                ExprGeneratorSwitch exprGen = new ExprGeneratorSwitch(exprFactory, getCurrMethod());
                stmt.getInvokeExpr().getArg(0).apply(exprGen);
                Expr cond = skolemize(exprGen.getResult());
                postCond = exprFactory.mkAND(preCond, exprFactory.mkNEG(cond));

                if (traceFormula != null)
                {
                    addToTraceFormula(exprFactory.mkNEG(cond));
                }
            }
            else
            {
                if (!mustAnalyze(spec.getApiClasses(), callee) || !targetMethodsOf(stmt).contains(callee))
                {
                    postCond = preCond;

                    if (traceFormula != null)
                    {
                        addToTraceFormula(exprFactory.mkTRUE());
                    }
                }
                else
                {
                    ExprGeneratorSwitch calleeExpGen = new ExprGeneratorSwitch(exprFactory, stmt.getInvokeExpr().getMethod());
                    ExprGeneratorSwitch callerExpGen = new ExprGeneratorSwitch(exprFactory, getCurrMethod());

                    createNewContext(stmt.getInvokeExpr().getMethod());

                    Expr argsExpr = getFormalToActualExpr(stmt.getInvokeExpr(), callerExpGen, calleeExpGen);

                    postCond = exprFactory.mkAND(preCond, argsExpr);

                    freshVarMapStack.push(currFreshVarMap);
                    currFreshVarMap = new HashMap<>(currFreshVarMap);

                    currMethod = stmt.getInvokeExpr().getMethod();
                }
            }
        }

        @Override
        public void caseThrowStmt(ThrowStmt stmt)
        {
            Expr currConstraint = handleReturnStmt(null);

            if (traceFormula != null)
            {
                addToTraceFormula(currConstraint);
            }
        }

        @Override
        public void caseEnterMonitorStmt(EnterMonitorStmt stmt)
        {
            noop();
        }

        @Override
        public void caseExitMonitorStmt(ExitMonitorStmt stmt)
        {
            noop();
        }

        private void noop()
        {
            postCond = preCond;

            if (traceFormula != null)
            {
                addToTraceFormula(exprFactory.mkTRUE());
            }
        }

        @Override
        public void defaultCase(Object obj)
        {
            throw new UnsupportedOperationException("Unsupported statement type: " + obj + " of class " + obj.getClass());
        }
    }

}

class ExprGeneratorSwitch extends AbstractJimpleValueSwitch
{
    private ExprFactory exprFactory;

    private SootMethod inMethod;

    private Expr result;

    ExprGeneratorSwitch(ExprFactory exprFactory, SootMethod meth)
    {
        this.exprFactory = exprFactory;
        inMethod = meth;
    }


    private BinOpExprOperands handleBinOp(BinopExpr v, boolean expectsIntOps)
    {
        ExprGeneratorSwitch exprSwitch = new ExprGeneratorSwitch(exprFactory, inMethod);

        v.getOp1().apply(exprSwitch);
        Expr left = exprSwitch.result;

        v.getOp2().apply(exprSwitch);
        Expr right = exprSwitch.result;

        IntegerType integerType = IntegerType.getInstance();

        if (left.getType() == integerType && ExprUtils.isBooleanConstant(exprFactory, right))
        {
            right = boolToInt(exprFactory, (ConstBool) right);
        }
        else if (right.getType() == integerType && ExprUtils.isBooleanConstant(exprFactory, left))
        {
            left = boolToInt(exprFactory, (ConstBool) left);
        }

        if (expectsIntOps && ExprUtils.isBooleanConstant(exprFactory, right))
        {
            right = boolToInt(exprFactory, (ConstBool) right);
        }

        if (expectsIntOps && ExprUtils.isBooleanConstant(exprFactory, left))
        {
            left = boolToInt(exprFactory, (ConstBool) left);
        }

        return new BinOpExprOperands(left, right);
    }

    @Override
    public Expr getResult()
    {
        return result;
    }

    @Override
    public void caseAddExpr(AddExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, true);
        result = exprFactory.mkPLUS(operands.left, operands.right);
    }

    @Override
    public void caseCastExpr(CastExpr v)
    {
        //throw new UnsupportedOperationException("Unsupported expression type " + v.getClass());
        //Since types don't matter, the result the CastExpr is equivalent to the result of the op
        ExprGeneratorSwitch exprSwitch = new ExprGeneratorSwitch(exprFactory, this.inMethod);
        v.getOp().apply(exprSwitch);
        result = exprSwitch.result;
    }

    @Override
    public void caseSubExpr(SubExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, true);
        result = exprFactory.mkMINUS(operands.left, operands.right);
    }

    @Override
    public void caseAndExpr(AndExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, false);
        Type op1Ty = v.getOp1().getType();
        Type op2Ty = v.getOp2().getType();
        if (!(op1Ty instanceof soot.BooleanType) && !(op2Ty instanceof soot.BooleanType))
        {
            ExprType ty = operands.left.getType();
            FuncDecl decl = exprFactory.mkFDECL("bitwiseAnd_" + op1Ty.toString() + "_" + op2Ty.toString(), new FunctionType(new ExprType[]{ty, ty}, ty));
            result = exprFactory.mkFAPP(decl, Arrays.asList(operands.left, operands.right));
        }
        else
        {
            result = exprFactory.mkAND(operands.left, operands.right);
        }
    }

    @Override
    public void caseDivExpr(DivExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, true);
        if (operands.right instanceof ConstInteger)
            result = exprFactory.mkDIV(operands.left, operands.right);
        else
        {
            // SMTInterpol does not support non-linear arithmetic. We over-approximate div with an uninterpreted function in such cases.
            FuncDecl remUf = exprFactory.mkFDECL("div", new FunctionType(new ExprType[]{IntegerType.getInstance(), IntegerType.getInstance()}, IntegerType.getInstance()));
            result = exprFactory.mkFAPP(remUf, Arrays.asList(operands.left, operands.right));
        }
    }

    @Override
    public void caseRemExpr(RemExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, true);
        if (operands.right instanceof ConstInteger)
            result = exprFactory.mkRem(operands.left, operands.right);
        else
        {
            // SMTInterpol does not support non-linear arithmetic. We over-approximate rem with an uninterpreted function in such cases.
            FuncDecl remUf = exprFactory.mkFDECL("rem", new FunctionType(new ExprType[]{IntegerType.getInstance(), IntegerType.getInstance()}, IntegerType.getInstance()));
            result = exprFactory.mkFAPP(remUf, Arrays.asList(operands.left, operands.right));
        }
    }

    @Override
    public void caseEqExpr(EqExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, false);
        result = exprFactory.mkEQ(operands.left, operands.right);
    }

    @Override
    public void caseGeExpr(GeExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, true);
        result = exprFactory.mkGEQ(operands.left, operands.right);
    }

    @Override
    public void caseGtExpr(GtExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, true);
        result = exprFactory.mkGT(operands.left, operands.right);
    }

    @Override
    public void caseLeExpr(LeExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, true);
        result = exprFactory.mkLEQ(operands.left, operands.right);
    }

    @Override
    public void caseLtExpr(LtExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, true);
        result = exprFactory.mkLT(operands.left, operands.right);
    }

    @Override
    public void caseMulExpr(MulExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, true);
        result = exprFactory.mkMULT(operands.left, operands.right);
    }

    @Override
    public void caseNeExpr(NeExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, false);
        result = exprFactory.mkNEG(exprFactory.mkEQ(operands.left, operands.right));
    }

    @Override
    public void caseOrExpr(OrExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, false);
        Type op1Ty = v.getOp1().getType();
        Type op2Ty = v.getOp2().getType();
        if (!(op1Ty instanceof soot.BooleanType) && !(op2Ty instanceof soot.BooleanType))
        {
            ExprType ty = operands.left.getType();
            FuncDecl decl = exprFactory.mkFDECL("bitwiseOr_" + op1Ty.toString() + "_" + op2Ty.toString(), new FunctionType(new ExprType[]{ty, ty}, ty));
            result = exprFactory.mkFAPP(decl, Arrays.asList(operands.left, operands.right));
        }
        else
        {
            result = exprFactory.mkOR(operands.left, operands.right);
        }
    }

    @Override
    public void caseNegExpr(NegExpr v)
    {
        ExprGeneratorSwitch exprSwitch = new ExprGeneratorSwitch(exprFactory, this.inMethod);
        v.getOp().apply(exprSwitch);
        result = exprFactory.mkNEG(exprSwitch.result);
    }

    @Override
    public void caseLengthExpr(LengthExpr v)
    {
        ExprGeneratorSwitch exprSwitch = new ExprGeneratorSwitch(exprFactory, this.inMethod);
        new JArrayRef(v.getOp(), IntConstant.v(-1)).apply(exprSwitch);
        result = exprSwitch.getResult();
    }

    @Override
    public void caseLocal(Local v)
    {
        result = getFuncAppExpr(exprFactory, inMethod, v);
    }

    @Override
    public void caseInstanceFieldRef(InstanceFieldRef v)
    {
        // This method is called only on load operations.
        FuncApp arrayVar = getArrayVar(exprFactory, v.getField(), this.inMethod);
        Expr base = getFuncAppExpr(exprFactory, inMethod, v.getBase());
        result = exprFactory.mkSelectExpr(arrayVar, base);
    }

    @Override
    public void caseParameterRef(ParameterRef v)
    {
        result = getFuncAppExpr(exprFactory, inMethod, v);
    }

    @Override
    public void caseThisRef(ThisRef v)
    {
        result = getFuncAppExpr(exprFactory, inMethod, v);
    }

    @Override
    public void caseArrayRef(ArrayRef v)
    {
        result = getFuncAppExpr(exprFactory, inMethod, v);
    }

    @Override
    public void caseStaticFieldRef(StaticFieldRef v)
    {
        result = getFuncAppExpr(exprFactory, inMethod, v);
    }

    @Override
    public void caseNewExpr(NewExpr v)
    {
        result = getFuncAppExpr(exprFactory, inMethod, v);
    }

    @Override
    public void caseIntConstant(IntConstant v)
    {
        result = AnalysisUtils.handleIntConstant(exprFactory, v);
    }

    @Override
    public void caseLongConstant(LongConstant v)
    {
        result = AnalysisUtils.handleLongConstant(exprFactory, v);
    }

    @Override
    public void caseShlExpr(ShlExpr v)
    {
        Type type = v.getType();
        ExprType exprType = sootToExprType(type);
        BinOpExprOperands operands = handleBinOp(v, true);
        FuncDecl decl = exprFactory.mkFDECL("shl" + type.toString(), new FunctionType(new ExprType[]{exprType, exprType}, exprType));
        result = exprFactory.mkFAPP(decl, Arrays.asList(operands.left, operands.right));
    }

    @Override
    public void caseShrExpr(ShrExpr v)
    {
        Type type = v.getType();
        ExprType exprType = sootToExprType(type);
        BinOpExprOperands operands = handleBinOp(v, true);
        FuncDecl decl = exprFactory.mkFDECL("shr" + type.toString(), new FunctionType(new ExprType[]{exprType, exprType}, exprType));
        result = exprFactory.mkFAPP(decl, Arrays.asList(operands.left, operands.right));
    }

    @Override
    public void caseUshrExpr(UshrExpr v)
    {
        Type type = v.getType();
        ExprType exprType = sootToExprType(type);
        BinOpExprOperands operands = handleBinOp(v, true);
        FuncDecl decl = exprFactory.mkFDECL("ushr" + type.toString(), new FunctionType(new ExprType[]{exprType, exprType}, exprType));
        result = exprFactory.mkFAPP(decl, Arrays.asList(operands.left, operands.right));
    }

    @Override
    public void caseNullConstant(NullConstant v)
    {
        result = exprFactory.mkINT(BigInteger.ZERO);
    }

    @Override
    public void caseStringConstant(StringConstant v)
    {
        result = exprFactory.mkINT(BigInteger.valueOf(v.value.hashCode()));
    }

    @Override
    public void caseClassConstant(ClassConstant v)
    {
        result = exprFactory.mkINT(BigInteger.valueOf(v.getType().hashCode()));
    }

    @Override
    public void caseInstanceOfExpr(InstanceOfExpr v)
    {
        ExprGeneratorSwitch exprGen = new ExprGeneratorSwitch(exprFactory, inMethod);
        v.getOp().apply(exprGen);
//        FuncDecl instanceOfFunc = exprFactory.mkFDECL("instanceOf" + v.getCheckType().toString(),
//                                                      new FunctionType(new ExprType[]{IntegerType.getInstance()},
//                                                                       BooleanType.getInstance()));
//        result = exprFactory.mkFAPP(instanceOfFunc, Collections.singletonList(exprGen.getResult()));
        FuncApp dTypeAr = AnalysisUtils.getDTypeArray(exprFactory);
        Type checkTy = v.getCheckType();
        result = exprFactory.mkEQ(exprFactory.mkSelectExpr(dTypeAr, exprGen.getResult()), exprFactory.mkINT(BigInteger.valueOf(checkTy.hashCode())));
    }

    @Override
    public void caseCmpExpr(CmpExpr v)
    {
        BinOpExprOperands operands = handleBinOp(v, true);
        result = exprFactory.mkMINUS(operands.left, operands.right);
    }

    @Override
    public void caseNewArrayExpr(NewArrayExpr v)
    {
        result = getFuncAppExpr(exprFactory, inMethod, v);
    }

    @Override
    public void caseNewMultiArrayExpr(NewMultiArrayExpr v)
    {
        result = getFuncAppExpr(exprFactory, inMethod, v);
    }

    @Override
    public void defaultCase(Object v)
    {
        //result = exprFactory.mkTRUE();
        throw new UnsupportedOperationException("Unsupported expression type " + v.getClass());
    }

    private class BinOpExprOperands
    {
        Expr left, right;

        BinOpExprOperands(Expr left, Expr right)
        {
            this.left = left;
            this.right = right;
        }
    }
}



