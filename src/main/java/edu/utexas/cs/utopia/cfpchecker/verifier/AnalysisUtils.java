package edu.utexas.cs.utopia.cfpchecker.verifier;

import edu.utexas.cs.utopia.cfpchecker.expression.AbstractExprTransformer;
import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.factory.ExprFactory;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncApp;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncDecl;
import edu.utexas.cs.utopia.cfpchecker.expression.terminal.ConstBool;
import edu.utexas.cs.utopia.cfpchecker.expression.type.*;
import edu.utexas.cs.utopia.cfpchecker.expression.type.IntegerType;
import soot.*;
import soot.ArrayType;
import soot.BooleanType;
import soot.jimple.*;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static edu.utexas.cs.utopia.cfpchecker.expression.ExprUtils.areEquivalent;
import static edu.utexas.cs.utopia.cfpchecker.expression.ExprUtils.isVar;


/**
 * Created by kferles on 5/24/17.
 */
public class AnalysisUtils
{
    static final String FRESH_VAR_SUFFIX = "@fresh";

    private static Map<FuncDecl, SootField> fieldExprDecls = new HashMap<>();

    private static Map<FuncDecl, Local> localExprDecl = new HashMap<>();

    private static Map<SootMethod, Unit> lastIdStmt = new HashMap<>();

    public static Expr cleanExpr(Expr e, ExprFactory exprFactory)
    {
        return e.accept(new AbstractExprTransformer(exprFactory)
        {
            @Override
            public Expr visit(FuncApp e)
            {
                FuncDecl decl = e.getDecl();
                String declName = decl.getName();
                int suffixIdx = declName.indexOf(FRESH_VAR_SUFFIX);

                if (!isVar(e))
                {
                    assert suffixIdx == -1;
                    List<Expr> newArgs = new ArrayList<>();
                    for (Expr arg : e)
                    {
                        newArgs.add(arg.accept(this));
                    }

                    return exprFactory.mkFAPP(decl, newArgs);
                }
                if (suffixIdx == -1)
                    return e;

                return exprFactory.mkFAPP(exprFactory.mkFDECL(declName.substring(0, suffixIdx), decl.getType()));
            }
        });
    }

    // This is a bit ugly, but we don't have an ExprType factory.
    private static ConcurrentHashMap<ArrayType, ExprType> cachedArrayTypes = new ConcurrentHashMap<>();

    public static FuncDecl getArrayDecl(ArrayRef v, ExprFactory exprFactory)
    {
        Local base = (Local) v.getBase();
        ArrayType arrayTy = (ArrayType) base.getType();
        String funcName = "ArrayOf" + arrayTy.getArrayElementType() + arrayTy.numDimensions;

        cachedArrayTypes.computeIfAbsent(arrayTy, arrayType -> {
            TypeGeneratorSwitch tySwitch = new TypeGeneratorSwitch();
            arrayType.getArrayElementType().apply(tySwitch);
            ExprType eTy = tySwitch.getResult();

            for (int i = 0; i < arrayType.numDimensions; ++i)
                eTy = new edu.utexas.cs.utopia.cfpchecker.expression.type.ArrayType(IntegerType.getInstance(), eTy);
            return new FunctionType(new ExprType[]{IntegerType.getInstance()}, eTy);
        });

        FunctionType funcTy = (FunctionType) cachedArrayTypes.get(arrayTy);

        return exprFactory.mkFDECL(funcName, funcTy);
    }

    public static Expr getIndexExpr(ArrayRef v, ExprFactory exprFactory, SootMethod inMethod)
    {
        ExprGeneratorSwitch exprSwitch = new ExprGeneratorSwitch(exprFactory, inMethod);
        v.getIndex().apply(exprSwitch);
        Expr indexExpr = exprSwitch.getResult();

        if (indexExpr.getType() instanceof edu.utexas.cs.utopia.cfpchecker.expression.type.BooleanType)
            indexExpr = AnalysisUtils.boolToInt(exprFactory, (ConstBool) indexExpr);

        assert indexExpr.getType() instanceof IntegerType;
        return indexExpr;
    }

    public static Expr handleIntConstant(ExprFactory exprFactory, IntConstant v)
    {
        Expr result;
        if (v.equals(IntConstant.v(0)))
            result = exprFactory.mkFALSE();
        else if (v.equals(IntConstant.v(1)))
            result = exprFactory.mkTRUE();
        else
            result = exprFactory.mkINT(new BigInteger(Integer.toString(v.value)));

        return result;
    }

    public static Expr handleLongConstant(ExprFactory exprFactory, LongConstant v)
    {
        Expr result;
        if (v.equals(LongConstant.v(0)))
            result = exprFactory.mkFALSE();
        else if (v.equals(LongConstant.v(1)))
            result = exprFactory.mkTRUE();
        else
            result = exprFactory.mkINT(new BigInteger(Long.toString(v.value)));

        return result;
    }

    private static String ALLOC_ARRAY = "Alloc";

    public static FuncApp getAllocArrayVar(ExprFactory exprFactory, SootMethod forMethod)
    {
        edu.utexas.cs.utopia.cfpchecker.expression.type.ArrayType arrayType = new edu.utexas.cs.utopia.cfpchecker.expression.type.ArrayType(IntegerType.getInstance(), edu.utexas.cs.utopia.cfpchecker.expression.type.BooleanType.getInstance());
        FuncDecl funcDecl = exprFactory.mkFDECL(ALLOC_ARRAY + "@" + forMethod.getSignature(), new FunctionType(new ExprType[0], arrayType));
        return exprFactory.mkFAPP(funcDecl);
    }

    public static FuncApp getArrayVar(ExprFactory exprFactory, SootField field, SootMethod forMethod)
    {
        assert !field.isStatic();
        edu.utexas.cs.utopia.cfpchecker.expression.type.ArrayType arrayType = new edu.utexas.cs.utopia.cfpchecker.expression.type.ArrayType(IntegerType.getInstance(), sootToExprType(field.getType()));
        FuncDecl funcDecl = exprFactory.mkFDECL(field.getSignature() + "@" + forMethod.getSignature(), new FunctionType(new ExprType[0], arrayType));
        return exprFactory.mkFAPP(funcDecl);
    }

    public static FuncApp getSymVarForField(ExprFactory exprFactory, SootField field, SootMethod forMethod)
    {
        assert !field.isStatic();
        edu.utexas.cs.utopia.cfpchecker.expression.type.ArrayType arrayType = new edu.utexas.cs.utopia.cfpchecker.expression.type.ArrayType(IntegerType.getInstance(), sootToExprType(field.getType()));
        FuncDecl funcDecl = exprFactory.mkFDECL("param@" + field.getSignature() + "@" + forMethod.getSignature(), new FunctionType(new ExprType[0], arrayType));
        return exprFactory.mkFAPP(funcDecl);
    }

    public static FuncApp getSymAllocArray(ExprFactory exprFactory, SootMethod forMethod)
    {
        edu.utexas.cs.utopia.cfpchecker.expression.type.ArrayType arrayType = new edu.utexas.cs.utopia.cfpchecker.expression.type.ArrayType(IntegerType.getInstance(), edu.utexas.cs.utopia.cfpchecker.expression.type.BooleanType.getInstance());
        FuncDecl funcDecl = exprFactory.mkFDECL("param" + ALLOC_ARRAY + "@" + forMethod.getSignature(), new FunctionType(new ExprType[0], arrayType));
        return exprFactory.mkFAPP(funcDecl);
    }

    public static FuncApp getSymRetForField(ExprFactory exprFactory, SootField field, SootMethod forMethod)
    {
        assert !field.isStatic();
        edu.utexas.cs.utopia.cfpchecker.expression.type.ArrayType arrayType = new edu.utexas.cs.utopia.cfpchecker.expression.type.ArrayType(IntegerType.getInstance(), sootToExprType(field.getType()));
        FuncDecl funcDecl = exprFactory.mkFDECL("ret@" + field.getSignature() + "@" + forMethod.getSignature(), new FunctionType(new ExprType[0], arrayType));
        return exprFactory.mkFAPP(funcDecl);
    }

    public static FuncApp getSymRetForAllocArray(ExprFactory exprFactory, SootMethod forMethod)
    {
        edu.utexas.cs.utopia.cfpchecker.expression.type.ArrayType arrayType = new edu.utexas.cs.utopia.cfpchecker.expression.type.ArrayType(IntegerType.getInstance(), edu.utexas.cs.utopia.cfpchecker.expression.type.BooleanType.getInstance());
        FuncDecl funcDecl = exprFactory.mkFDECL("ret@" + ALLOC_ARRAY + "@" + forMethod.getSignature(), new FunctionType(new ExprType[0], arrayType));
        return exprFactory.mkFAPP(funcDecl);
    }

    public static FuncApp getSymRetVarForMethod(ExprFactory exprFactory, SootMethod method)
    {
        assert !method.getReturnType().equals(VoidType.v()) : method.getSignature();
        FuncDecl funcDecl = exprFactory.mkFDECL("ret@" + method.getSignature(), new FunctionType(new ExprType[0], sootToExprType(method.getReturnType())));
        return exprFactory.mkFAPP(funcDecl);
    }

    public static Expr getFuncAppExpr(ExprFactory exprFactory, SootMethod method, Value v)
    {
        UninterpretedFuncGen funcGen = new UninterpretedFuncGen(method, exprFactory);
        v.apply(funcGen);
        return funcGen.getResult();
    }

    public static FuncApp getDTypeArray(ExprFactory exprFactory)
    {
        edu.utexas.cs.utopia.cfpchecker.expression.type.ArrayType arrayType = new edu.utexas.cs.utopia.cfpchecker.expression.type.ArrayType(IntegerType.getInstance(), IntegerType.getInstance());
        FuncDecl decl = new FuncDecl("DType", new FunctionType(new ExprType[0], arrayType));
        return exprFactory.mkFAPP(decl);
    }

    public static ExprType sootToExprType(Type t)
    {
        TypeGeneratorSwitch sw = new TypeGeneratorSwitch();
        t.apply(sw);

        return sw.getResult();
    }

    public static Expr boolToInt(ExprFactory exprFactory, ConstBool boolConst)
    {
        if (boolConst.isTrue())
            return exprFactory.mkINT(new BigInteger("1"));
        else
            return exprFactory.mkINT(new BigInteger("0"));
    }

    public static boolean isInvokeExpr(Value v)
    {
        IsInvokeSwitch isInvSw = new IsInvokeSwitch();
        v.apply(isInvSw);
        return isInvSw.getResult();
    }

    public static boolean isAllocationExpr(Value v)
    {
        IsAllocationSwitch isAllocSw = new IsAllocationSwitch();
        v.apply(isAllocSw);
        return isAllocSw.getResult();
    }

    public static boolean isInstanceFieldRef(Value v)
    {
        AbstractJimpleValueSwitch sw = new AbstractJimpleValueSwitch()
        {
            boolean result = false;

            @Override
            public void caseInstanceFieldRef(InstanceFieldRef v)
            {
                result = true;
            }

            @Override
            public Object getResult()
            {
                return result;
            }
        };

        v.apply(sw);
        return (Boolean) sw.getResult();
    }

    public static boolean isArrayRef(Value v)
    {
        AbstractJimpleValueSwitch sw = new AbstractJimpleValueSwitch()
        {
            boolean result = false;

            @Override
            public void caseArrayRef(ArrayRef v)
            {
                result = true;
            }

            @Override
            public Boolean getResult()
            {
                return result;
            }
        };

        v.apply(sw);
        return (Boolean) sw.getResult();
    }

    public static void registerFieldExprDecl(FuncDecl decl, SootField fld)
    {
        fieldExprDecls.put(decl, fld);
    }

    public static void registerLocalExprDecl(FuncDecl decl, Local l)
    {
        localExprDecl.put(decl, l);
    }

    public static SootField getSootFieldFromExpr(FuncDecl decl)
    {
        return fieldExprDecls.get(decl);
    }

    public static Local getLocalFromExpr(FuncDecl decl)
    {
        return localExprDecl.get(decl);
    }

    public static Set<SootField> getRelevantFieldsForMethod(SootMethod method)
    {
        return TransitiveReadWriteSetGenerator.v().getFieldReadWriteSet(method);
    }

    public static Unit getLastIdentityStatement(SootMethod method)
    {
        if (!lastIdStmt.containsKey(method))
        {
            Body b = method.getActiveBody();
            PatchingChain<Unit> units = b.getUnits();
            Unit head = units.getFirst();
            for (Unit u : units)
            {
                if (!(u instanceof IdentityStmt))
                {
                    if (u != head)
                        lastIdStmt.put(method, units.getPredOf(u));
                    else
                        lastIdStmt.put(method, u);
                    break;
                }
            }
        }

        return lastIdStmt.get(method);
    }

    private static boolean hasRegisterFieldOrLocal(FuncDecl decl)
    {
        return fieldExprDecls.containsKey(decl) || localExprDecl.containsKey(decl);
    }
}

class IsAllocationSwitch extends AbstractJimpleValueSwitch
{
    private boolean isAlloc = false;

    @Override
    public void caseNewArrayExpr(NewArrayExpr v)
    {
        isAlloc = true;
    }

    @Override
    public void caseNewMultiArrayExpr(NewMultiArrayExpr v)
    {
        isAlloc = true;
    }

    @Override
    public void caseNewExpr(NewExpr v)
    {
        isAlloc = true;
    }

    @Override
    public Boolean getResult()
    {
        return isAlloc;
    }
}

class IsInvokeSwitch extends AbstractJimpleValueSwitch
{
    boolean isInv = false;

    @Override
    public void caseInterfaceInvokeExpr(InterfaceInvokeExpr v)
    {
        isInv = true;
    }

    @Override
    public void caseSpecialInvokeExpr(SpecialInvokeExpr v)
    {
        isInv = true;
    }

    @Override
    public void caseStaticInvokeExpr(StaticInvokeExpr v)
    {
        isInv = true;
    }

    @Override
    public void caseVirtualInvokeExpr(VirtualInvokeExpr v)
    {
        isInv = true;
    }

    @Override
    public Boolean getResult()
    {
        return isInv;
    }
}

class TypeGeneratorSwitch extends TypeSwitch
{
    private ExprType result;

    @Override
    public ExprType getResult()
    {
        return result;
    }

    @Override
    public void caseBooleanType(BooleanType t)
    {
        result = edu.utexas.cs.utopia.cfpchecker.expression.type.BooleanType.getInstance();
    }

    @Override
    public void caseIntType(IntType t)
    {
        result = edu.utexas.cs.utopia.cfpchecker.expression.type.IntegerType.getInstance();
    }

    @Override
    public void caseByteType(ByteType t) {
        result = edu.utexas.cs.utopia.cfpchecker.expression.type.IntegerType.getInstance();
    }

    @Override
    public void caseLongType(LongType t)
    {
        result = edu.utexas.cs.utopia.cfpchecker.expression.type.IntegerType.getInstance();
    }

    @Override
    public void caseRefType(RefType t)
    {
        result = edu.utexas.cs.utopia.cfpchecker.expression.type.IntegerType.getInstance();
    }

    @Override
    public void caseShortType(ShortType t)
    {
        result = edu.utexas.cs.utopia.cfpchecker.expression.type.IntegerType.getInstance();
    }

    @Override
    public void caseNullType(NullType t)
    {
        result = edu.utexas.cs.utopia.cfpchecker.expression.type.IntegerType.getInstance();
    }

    @Override
    public void caseArrayType(ArrayType t)
    {
        result = edu.utexas.cs.utopia.cfpchecker.expression.type.IntegerType.getInstance();
    }

    @Override
    public void caseCharType(CharType t)
    {
        result = edu.utexas.cs.utopia.cfpchecker.expression.type.IntegerType.getInstance();
    }

    // Havoc any definitions of these types and treat them
    // as integers so the interpolation still works.
    @Override
    public void caseDoubleType(DoubleType t)
    {
        result = edu.utexas.cs.utopia.cfpchecker.expression.type.IntegerType.getInstance();
    }

    @Override
    public void caseFloatType(FloatType t)
    {
        result = edu.utexas.cs.utopia.cfpchecker.expression.type.IntegerType.getInstance();
    }

    @Override
    public void defaultCase(Type t)
    {
        throw new UnsupportedOperationException("Unsupported type " + t.getClass().getName());
    }
}

class UninterpretedFuncGen extends AbstractJimpleValueSwitch
{
    private SootMethod inMethod;

    private ExprFactory exprFactory;

    private Expr result;

    UninterpretedFuncGen(SootMethod inMethod, ExprFactory exprFactory)
    {
        this.inMethod = inMethod;
        this.exprFactory = exprFactory;
    }

    private ExprType getCodomainForDecl(Value v)
    {
        Type type = v.getType();
        return AnalysisUtils.sootToExprType(type);
    }

    private String getSuffix()
    {
        return "@" + inMethod.getSignature();
    }

    @Override
    public void caseInstanceFieldRef(InstanceFieldRef v)
    {
        assert false;
    }

    @Override
    public void caseLocal(Local v)
    {
        String funcName = v.getName() + getSuffix();

        FuncDecl funcDecl = exprFactory.mkFDECL(funcName, new FunctionType(new ExprType[0], getCodomainForDecl(v)));

        AnalysisUtils.registerLocalExprDecl(funcDecl, v);
        result = exprFactory.mkFAPP(funcDecl);
    }

    // TODO: Should we also register these in AnalysisUtils?
    @Override
    public void caseParameterRef(ParameterRef v)
    {
        String funcName = "@param" + v.getIndex() + getSuffix();
        FuncDecl funcDecl = exprFactory.mkFDECL(funcName, new FunctionType(new ExprType[0], getCodomainForDecl(v)));

        result = exprFactory.mkFAPP(funcDecl);
    }

    @Override
    public void caseThisRef(ThisRef v)
    {
        String funcName = "@this" + getSuffix();
        FuncDecl funcDecl = exprFactory.mkFDECL(funcName, new FunctionType(new ExprType[0], IntegerType.getInstance()));

        result = exprFactory.mkFAPP(funcDecl);
    }

    @Override
    public void caseStaticFieldRef(StaticFieldRef v)
    {
        SootField field = v.getField();
        String funcName = field.toString();

        FuncDecl funcDecl = exprFactory.mkFDECL(funcName, new FunctionType(new ExprType[0], getCodomainForDecl(v)));

        AnalysisUtils.registerFieldExprDecl(funcDecl, field);
        result = exprFactory.mkFAPP(funcDecl);
    }

    @Override
    public void caseNewExpr(NewExpr v)
    {
        String funcName = v.getType().toString() + getSuffix();
        FuncDecl funcDecl = exprFactory.mkFDECL(funcName, new FunctionType(new ExprType[0], IntegerType.getInstance()));

        result = exprFactory.mkFAPP(funcDecl);
    }

    @Override
    public void caseNewArrayExpr(NewArrayExpr v)
    {
        String funcName = v.getType().toString() + getSuffix();
        FuncDecl funcDecl = exprFactory.mkFDECL(funcName, new FunctionType(new ExprType[0], IntegerType.getInstance()));

        result = exprFactory.mkFAPP(funcDecl);
    }

    @Override
    public void caseNewMultiArrayExpr(NewMultiArrayExpr v) {
        String funcName = v.getType().toString() + getSuffix();
        FuncDecl funcDecl = exprFactory.mkFDECL(funcName, new FunctionType(new ExprType[0], IntegerType.getInstance()));

        result = exprFactory.mkFAPP(funcDecl);
    }

    // This should be in the ExprGenerator
    @Override
    public void caseArrayRef(ArrayRef v)
    {
        FuncDecl funcDecl = AnalysisUtils.getArrayDecl(v, exprFactory);
        Expr indexExpr = AnalysisUtils.getIndexExpr(v, exprFactory, inMethod);
        ExprGeneratorSwitch exprSwitch = new ExprGeneratorSwitch(exprFactory, inMethod);

        v.getBase().apply(exprSwitch);
        Expr baseExpr = exprSwitch.getResult();
        result = exprFactory.mkSelectExpr(exprFactory.mkFAPP(funcDecl, Collections.singletonList(baseExpr)), indexExpr);
    }

    @Override
    public void defaultCase(Object v)
    {
        throw new UnsupportedOperationException("Unsupported element for UninterpretedFuncGen: " + v.getClass());
    }

    @Override
    public Expr getResult()
    {
        return result;
    }
}

class VariableCollector extends AbstractJimpleValueSwitch {
    HashSet<Value> result = new HashSet<>();

    @Override
    public void caseLocal(Local local) {
        result.add(local);
    }

    @Override
    public void caseStaticFieldRef(StaticFieldRef staticFieldRef) {
        result.add(staticFieldRef);
    }

    @Override
    public void caseInstanceFieldRef(InstanceFieldRef instanceFieldRef) {
        result.add(instanceFieldRef);
    }

    @Override
    public void caseParameterRef(ParameterRef parameterRef) {
        result.add(parameterRef);
    }

    @Override
    public void caseThisRef(ThisRef thisRef) {
        result.add(thisRef);
    }

    @Override
    public void defaultCase(Object o) {
        if(o instanceof Value) {
            Value uses = (Value) o;

            for(ValueBox v : uses.getUseBoxes()) {
                v.getValue().apply(this);
            }
        }
    }

    public HashSet<Value> getResult() {
        return result;
    }
}
