package edu.utexas.cs.utopia.cfpchecker.expression;

import com.microsoft.z3.*;
import com.microsoft.z3.Tactic;
import edu.utexas.cs.utopia.cfpchecker.expression.array.ArrayExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.array.SelectExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.array.StoreExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.bool.*;
import edu.utexas.cs.utopia.cfpchecker.expression.bool.BoolExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.factory.ExprFactory;
import edu.utexas.cs.utopia.cfpchecker.expression.function.BoundVar;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncApp;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncDecl;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.integer.*;
import edu.utexas.cs.utopia.cfpchecker.expression.integer.IntExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.terminal.*;
import edu.utexas.cs.utopia.cfpchecker.expression.type.*;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.ExprTypeRetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.TopLevelRetVisitor;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.java_smt.SolverContextFactory;
import org.sosy_lab.java_smt.api.*;
import org.sosy_lab.java_smt.api.visitors.FormulaVisitor;
import soot.jimple.toolkits.typing.TypeException;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by kferles on 5/19/17.
 */
public class ExprUtils
{
    public static int size(Expr e)
    {
        ExprPostOrderRetVisitor<Integer> sizeVis = new ExprPostOrderRetVisitor<Integer>()
        {
            Integer size = 0;

            @Override
            public Integer visit(Expr e)
            {
                return size + 1;
            }
        };

        return e.accept(sizeVis);
    }

    /**
     * Prove a statement
     *
     * @param f statement to prove
     */
    private static Status prove(Context ctx, com.microsoft.z3.BoolExpr f)
    {
        Solver s = ctx.mkSolver();
        s.add(f);
        return s.check();
    }

    public static SolverContext setupJavaSMT() {
        SolverContext context = null;
        InterpolatingProverEnvironment<?> prover = null;
        FormulaManager mgr = null;

        try {
            Configuration config = Configuration.defaultConfiguration();
            LogManager logger = BasicLogManager.create(config);
            ShutdownNotifier notifier = ShutdownNotifier.createDummy();

            SolverContextFactory.Solvers solver = SolverContextFactory.Solvers.SMTINTERPOL;
            context = SolverContextFactory.createSolverContext(config, logger, notifier, solver);
        } catch(Exception ex) {
            System.out.println("Failed!");
        }

        return context;
    }

    public static List<Expr> computeTreeInterp(List<Expr> traceFormula, int[] treeIndices, ExprFactory exprFactory)
    {
        try {
            Configuration config = Configuration.defaultConfiguration();
            LogManager logger = BasicLogManager.create(config);
            ShutdownNotifier notifier = ShutdownNotifier.createDummy();

            SolverContextFactory.Solvers solver = SolverContextFactory.Solvers.SMTINTERPOL;
            SolverContext context = SolverContextFactory.createSolverContext(config, logger, notifier, solver);
            InterpolatingProverEnvironment<?> prover = context.newProverEnvironmentWithInterpolation();
            FormulaManager mgr = context.getFormulaManager();

            List handles = new ArrayList();
            for (int i = 0; i < traceFormula.size(); i++)
            {
                JavaSmtMarshalVisitor marshal = new JavaSmtMarshalVisitor(mgr);
                SmtExpr expr = traceFormula.get(i).accept(marshal);
                handles.add(Collections.singleton(prover.push((BooleanFormula) expr.f)));
            }

            boolean unsat = prover.isUnsat();

            assert unsat;

            List<BooleanFormula> itps = prover.getTreeInterpolants(handles, treeIndices);

            return itps.stream()
                       .map(f -> toExpresso(exprFactory, new HashMap<>(), mgr, f))
                       .collect(Collectors.toList());


        } catch(Exception ex) {
            ex.printStackTrace();
            System.out.println("Failed!");
        }

        assert false;
        return null;
    }

    public static boolean isEquivalent(FormulaManager mgr, BasicProverEnvironment prover, Expr interp0, Expr interp1) {
        Map<SmtExpr, Expr> replMap = new HashMap<>();
        JavaSmtMarshalVisitor marshal = new JavaSmtMarshalVisitor(mgr);
        BooleanFormula left = (BooleanFormula) interp0.accept(marshal).f;
        BooleanFormula right = (BooleanFormula) interp1.accept(marshal).f;

        BooleanFormulaManager bmgr = mgr.getBooleanFormulaManager();
        BooleanFormula equiv = bmgr.not(bmgr.equivalence(left, right));

        //IntegerFormulaManager imgr = mgr.getIntegerFormulaManager();

        prover.push();
        boolean sat = false;
        try {
            prover.addConstraint(equiv);
            sat = prover.isUnsat();
        } catch(Exception e) {
            assert false;
        }
        prover.pop();

        prover.close();
        return sat;
    }

    private static ConcurrentHashMap<Expr, Boolean> unsatMemoizedQueries = new ConcurrentHashMap<>();

    public static boolean checkSATWithZ3(Context ctx, Expr formula)
    {
        return !checkUNSATWithZ3(ctx, formula);
    }

    public static boolean areEquivalent(Expr f1, Expr f2, ExprFactory exprFactory, Context ctx)
    {
        return f1 == f2 || checkUNSATWithZ3(ctx, exprFactory.mkNEG(exprFactory.mkAND(exprFactory.mkIMPL(f1, f2),
                                                                         exprFactory.mkIMPL(f2, f1))));
    }

    public static com.microsoft.z3.BoolExpr elimminateQuantifiers(Context ctx, com.microsoft.z3.BoolExpr formula)
    {
        Tactic qe = ctx.mkTactic("qe");
        Goal goal = ctx.mkGoal(false, false, false);
        goal.add(formula);
        ApplyResult apply = qe.apply(goal);
        com.microsoft.z3.BoolExpr rv = ctx.mkTrue();
        for (Goal sg : apply.getSubgoals())
            rv = ctx.mkAnd(rv, ctx.mkAnd(sg.getFormulas()));
        return rv;
    }

    public static boolean checkUNSATWithZ3(Context ctx, Expr formula)
    {
        unsatMemoizedQueries.computeIfAbsent(formula, f -> {
            Context z3Context = new Context();
            com.microsoft.z3.BoolExpr z3expr = (com.microsoft.z3.BoolExpr)
                    ExprUtils.toZ3Expr(z3Context, f);
            Status proverRes = prove(z3Context, z3expr);
            z3Context.close();
            return proverRes == Status.UNSATISFIABLE;
        });

        return unsatMemoizedQueries.get(formula);
    }

    public static boolean contains(Expr src, Expr e, Expr... es)
    {
        CheckInclusionVisitor vis = new CheckInclusionVisitor(e, es);
        return src.accept(vis);
    }

    public static Set<Expr> containedFilter(Expr src, Set<Expr> queries)
    {
        InclusionFilter vis = new InclusionFilter(queries);
        return src.accept(vis);
    }

    public static boolean containsAny(Expr src, Set<Expr> queries)
    {
        CheckInclusionVisitor vis = new CheckInclusionVisitor(queries);
        return src.accept(vis);
    }

    public static boolean containsQuantifier(Expr e)
    {
        return e.accept(new ExprPostOrderRetVisitor<Boolean>()
        {
            boolean rv = false;

            @Override
            public Boolean visit(Expr e)
            {
                return rv |= false;
            }

            @Override
            public Boolean visit(ExistentialQuantifier e)
            {
                return rv = true;
            }
        });
    }

    /**
     * Replaces expression src in e with expression trg
     *
     * @param e   original expression
     * @param src expression to be replaced
     * @param trg expression that replaces src
     * @return e[trg/src]
     */
    public static Expr replace(ExprFactory exprFactory, Expr e, Expr src, Expr trg)
    {
        Map<Expr, Expr> replMap = new HashMap<>();
        replMap.put(src, trg);

        return replace(exprFactory, e, replMap);
    }

    public static boolean containsConjuct(Expr e1, Expr conj)
    {
        if (e1 instanceof AndExpr)
        {
            for (Expr e : (AndExpr) e1)
            {
                if (e == conj)
                    return true;
            }
            return false;
        }
        else
        {
            return e1 == conj;
        }

    }

    /**
     * Replaces every key of the replMap map with the value
     * associated with this key.
     *
     * @param e       original expression
     * @param replMap maps expressions to be replaced in e with their replacement.
     * @return ks = keySet(replMap): for all indices i of ks. e[ks[i]/replMap[ks[i]]]
     */
    public static Expr replace(ExprFactory exprFactory, Expr e, Map<Expr, Expr> replMap)
    {
        ReplaceExprVisitor replaceExprVisitor = new ReplaceExprVisitor(exprFactory, replMap);

        return e.accept(replaceExprVisitor);
    }

    public static Expr restoreFunctionApplications(ExprFactory factory, Map<Expr, Expr> funcMap, Expr expr)
    {
        RestoreFunAppVis sym = new RestoreFunAppVis(factory, funcMap);
        return expr.accept(sym);
    }

    public static Expr abstractFunctionApplications(ExprFactory factory, Map<Expr, Expr> varMap, Map<Expr, Expr> funcMap, Expr expr)
    {
        AbstractFunAppsVis sym = new AbstractFunAppsVis(factory, varMap, funcMap);
        return expr.accept(sym);
    }

    public static Expr booleanSimplification(ExprFactory exprFactory, Expr e)
    {
        GatherBooleanArgsVisitor gatherArgs = new GatherBooleanArgsVisitor(exprFactory);
        BooleanSimplificationVisitor booleanSimpl = new BooleanSimplificationVisitor(exprFactory);
        return e.accept(gatherArgs).accept(booleanSimpl);
    }

    public static boolean isBooleanConstant(ExprFactory exprFactory, Expr e)
    {
        Expr trueConst = exprFactory.mkTRUE(), falseConst = exprFactory.mkFALSE();

        return e == trueConst || e == falseConst;
    }

    public static com.microsoft.z3.Expr toZ3Expr(Context z3Context, Expr e)
    {
        Z3MarshalVisitor marshalVisitor = new Z3MarshalVisitor(z3Context);
        return (com.microsoft.z3.Expr) e.accept(marshalVisitor);
    }

    public static com.microsoft.z3.Expr toZ3Expr(Context z3Context, Map<AST, Expr> replMap, Expr e)
    {
        Z3MarshalVisitor marshalVisitor = new Z3MarshalVisitor(z3Context);
        com.microsoft.z3.Expr z3Expr = (com.microsoft.z3.Expr) e.accept(marshalVisitor);
        Map<Expr, AST> internalMapping = marshalVisitor.getInternalMapping();

        for (Expr expr : internalMapping.keySet())
            replMap.put(internalMapping.get(expr), expr);

        return z3Expr;
    }

    public static boolean containsBoundVar(Expr expr)
    {
        ExprPostOrderRetVisitor<Boolean> v = new ExprPostOrderRetVisitor<Boolean>()
        {
            boolean result = false;

            @Override
            public Boolean visit(BoundVar e)
            {
                return (result = true);
            }

            @Override
            public Boolean visit(Expr e)
            {
                return result;
            }
        };

        return expr.accept(v);
    }

    public static Expr unboundVars(final ExprFactory exprFactory, Expr expr)
    {
        AbstractExprTransformer tr = new AbstractExprTransformer(exprFactory)
        {
            @Override
            public Expr visit(BoundVar e)
            {
                return exprFactory.mkFAPP(e.getDecl());
            }

            @Override
            public Expr visit(Expr e)
            {
                return e;
            }
        };

        return expr.accept(tr);
    }

    public static boolean containsUnknownExpr(com.microsoft.z3.Expr e, Map<com.microsoft.z3.AST, Expr> replMap)
    {
        // TODO: are these checks sufficient?
        if (e.isQuantifier())
            return true;
        if (e.isConst() && !e.isTrue() && !e.isFalse())
            return !replMap.containsKey(e);

        // This is a bit weird, some formulas (e.g., existential quantified ones)
        // have negative number of arguments
        boolean hasArgs = e.getNumArgs() > 0;
        boolean unknownFound = false;

        if (hasArgs)
        {
            com.microsoft.z3.Expr[] args = e.getArgs();
            for (int i = 0, sz = args.length; i < sz && !unknownFound; ++i)
                unknownFound = containsUnknownExpr(args[i], replMap);
        }

        return unknownFound;
    }

    public static Expr toExpresso(ExprFactory factory, Map<SmtExpr, Expr> replMap, FormulaManager mgr, Formula f) {
        JavaSmtUnmarshalVisitor visitor = new JavaSmtUnmarshalVisitor(mgr, factory, replMap);
        return mgr.visit(f, visitor);
    }

    // TODO: Clean this guy a bit. We are currently assume that the formula
    // does not contain any quantifier and we unbound all the variables.
    public static Expr toExpresso(ExprFactory factory, Map<com.microsoft.z3.AST, Expr> replMap, com.microsoft.z3.Expr e)
    {
        // NOTE: Assuming no quantifiers in the formula
        Expr[] args = new Expr[e.getNumArgs()];
        com.microsoft.z3.Expr[] z3Args = e.getArgs();
        for (int i = 0; i < e.getNumArgs(); i++)
            args[i] = unboundVars(factory, toExpresso(factory, replMap, z3Args[i]));

        if (e.isAnd())
        {
            if (args.length == 0)
                return factory.mkTRUE();
            if (args.length == 1)
                return args[0];

            Expr[] subArgs = args.length > 2 ? Arrays.copyOfRange(args, 2, args.length) : null;
            return factory.mkAND(args[0], args[1], subArgs);
        }
        else if (e.isOr())
        {
            if (args.length == 1)
                return args[0];

            Expr[] subArgs = args.length > 2 ? Arrays.copyOfRange(args, 2, args.length) : null;
            return factory.mkOR(args[0], args[1], subArgs);
        }
        else if (e.isEq())
        {
            return factory.mkEQ(args[0], args[1]);
        }
        else if (e.isGE())
        {
            return factory.mkGEQ(args[0], args[1]);
        }
        else if (e.isGT())
        {
            return factory.mkGT(args[0], args[1]);
        }
        else if (e.isImplies())
        {
            return factory.mkIMPL(args[0], args[1]);
        }
        else if (e.isLE())
        {
            return factory.mkLEQ(args[0], args[1]);
        }
        else if (e.isLT())
        {
            return factory.mkLT(args[0], args[1]);
        }
        else if (e.isNot())
        {
            return factory.mkNEG(args[0]);
        }
        else if (e.isDiv())
        {
            return factory.mkDIV(args[0], args[1]);
        }
        if (e.isRemainder())
        {
            return factory.mkRem(args[0], args[1]);
        }
        if (e.isModulus())
        {
            return factory.mkMod(args[0], args[1]);
        }
        else if (e.isSub())
        {
            return factory.mkMINUS(args[0], args[1]);
        }
        else if (e.isMul())
        {
            return factory.mkMULT(args[0], args[1]);
        }
        else if (e.isAdd())
        {
            return factory.mkPLUS(args[0], args[1]);
        }
        else if (e.isRemainder())
        {
            return factory.mkRem(args[0], args[1]);
        }
        else if (e.isIntNum())
        {
            return factory.mkINT(new BigInteger(e.toString()));
        }
        else if (e.isFalse())
        {
            return factory.mkFALSE();
        }
        else if (e.isTrue())
        {
            return factory.mkTRUE();
        }
        else if (e.isIff())
        {
            return factory.mkAND(factory.mkIMPL(args[0], args[1]),
                                 factory.mkIMPL(args[1], args[0]));
        }
        else if (e.isSelect())
        {
            return factory.mkSelectExpr((FuncApp) args[0], args[1]);
        }
        else if (e.isStore())
        {
            return factory.mkStoreExpr((FuncApp) args[0], args[1], args[2]);
        }
        else
        {
            assert e.isApp();
            if (args.length > 0)
            {
                return factory.mkFAPP((FuncDecl) replMap.get(e.getFuncDecl()), Arrays.asList(args));
            }

            if (!replMap.containsKey(e))
                throw new IllegalStateException("Unable to translate z3Expr: " + e);
            return replMap.get(e);
        }
    }

    public static boolean isVar(Expr e)
    {
        IsVarVisitor varVisitor = new IsVarVisitor();
        return e.accept(varVisitor);
    }

    public static boolean isQuantifiedFormula(Expr e)
    {
        TopLevelRetVisitor<Boolean> isQuantVis = new TopLevelRetVisitor<Boolean>()
        {
            @Override
            public Boolean visit(Expr e)
            {
                return false;
            }

            @Override
            public Boolean visit(ExistentialQuantifier e)
            {
                return true;
            }

            @Override
            public Boolean visit(UniversalQuantifier e)
            {
                return true;
            }
        };

        return e.accept(isQuantVis);
    }

    public static FuncDecl getFunDecl(Expr e)
    {
        RetrieveFuncDeclVis funcDeclVis = new RetrieveFuncDeclVis();
        return e.accept(funcDeclVis);
    }

    public static Set<FuncApp> getFreeVars(Expr expr)
    {
        final Set<FuncApp> freeVars = new HashSet<>();
        ExprPostOrderRetVisitor<Set<FuncApp>> freeVarsVisitor = new ExprPostOrderRetVisitor<Set<FuncApp>>()
        {
            public Set<FuncApp> visit(FuncApp e)
            {
                if (ExprUtils.isVar(e))
                {
                    freeVars.add(e);
                }

                return super.visit(e);
            }

            public Set<FuncApp> visit(Expr e)
            {
                return freeVars;
            }
        };

        Set<FuncApp> ret = expr.accept(freeVarsVisitor);

        return ret;
    }

    public static boolean isBoolConst(ExprFactory exprFactory, Expr e)
    {
        return e == exprFactory.mkTRUE() || e == exprFactory.mkFALSE();
    }

    public static List<FuncApp> collectAllFuncApps(Expr e)
    {
        ExprPostOrderRetVisitor<List<FuncApp>> vis = new ExprPostOrderRetVisitor<List<FuncApp>>()
        {
            List<FuncApp> rv = new ArrayList<>();

            @Override
            public List<FuncApp> visit(Expr e)
            {
                return rv;
            }

            @Override
            public List<FuncApp> visit(FuncApp e)
            {
                super.visit(e);
                rv.add(e);
                return rv;
            }
        };

        return e.accept(vis);
    }
}

class CheckInclusionVisitor extends ExprPostOrderRetVisitor<Boolean>
{
    private Set<Expr> queries = new HashSet<>();

    private boolean found = false;

    CheckInclusionVisitor(Expr e, Expr... es)
    {
        Collections.addAll(queries, e);
        Collections.addAll(queries, es);
    }

    CheckInclusionVisitor(Set<Expr> queries)
    {
        this.queries = queries;
    }

    @Override
    public Boolean visit(Expr e)
    {
        if (!found && queries.contains(e))
            found = true;

        return found;
    }

    @Override
    public Boolean visit(BoundVar e)
    {
        if (!found && queries.contains(e))
            found = true;

        return found;
    }
}

class InclusionFilter extends ExprPostOrderRetVisitor<Set<Expr>>
{
    private Set<Expr> queries;

    private Set<Expr> found = new HashSet<>();

    InclusionFilter(Set<Expr> queries)
    {
        this.queries = queries;
    }

    @Override
    public Set<Expr> visit(BoundVar e)
    {
        if (queries.contains(e))
            found.add(e);

        return found;
    }

    @Override
    public Set<Expr> visit(Expr e)
    {
        if (queries.contains(e))
            found.add(e);

        return found;
    }
}

class ReplaceExprVisitor extends AbstractExprTransformer
{
    private Map<Expr, Expr> replMap;

    ReplaceExprVisitor(ExprFactory exprFactory, Map<Expr, Expr> replMap)
    {
        super(exprFactory);
        this.replMap = replMap;
    }

    @Override
    public Expr visit(Expr e)
    {
        if (replMap.containsKey(e))
            return replMap.get(e);

        return e;
    }
}

class AbstractFunAppsVis extends AbstractExprTransformer
{
    private Map<Expr, Expr> exprToFreshVarMap;

    private Map<Expr, Expr> freshVarToExprMap;

    private Map<FuncDecl, Integer> declAppTracker = new HashMap<>();

    private Map<FuncDecl, FunctionType> declType = new HashMap<>();

    // TODO whether the SVR replaces the thing with @this or @r0 may mess up the invariant prover
    // because I'm explicitly replacing the original r0 with the testing segment r0
    AbstractFunAppsVis(ExprFactory exprFactory, Map<Expr, Expr> exprToFreshVarMap,
                       Map<Expr, Expr> freshVarToExprMap)
    {
        super(exprFactory);
        this.exprToFreshVarMap = exprToFreshVarMap;
        this.freshVarToExprMap = freshVarToExprMap;
    }

    @Override
    public Expr visit(FuncApp e)
    {
        if (e.argNum() == 0 || e instanceof BoundVar)
            return super.visit(e);

        if (!alreadyVisited(e))
        {
            FuncDecl decl = (FuncDecl) e.getDecl().accept(this);
            ExprType codomain = decl.getType().getCoDomain();

            int lastAppId = declAppTracker.getOrDefault(decl, 0);
            declAppTracker.put(decl, ++lastAppId);

            if (!declType.containsKey(decl))
                declType.put(decl, new FunctionType(new ExprType[0], codomain));

            String varName = decl.getName() + "_" + lastAppId;
            FuncDecl newDecl = exprFactory.mkFDECL(varName, declType.get(decl));
            Expr newVar = exprFactory.mkFAPP(newDecl);

            exprToFreshVarMap.put(e, newVar);
            freshVarToExprMap.put(newVar, e);
        }

        return exprToFreshVarMap.get(e);
    }
}

class RestoreFunAppVis extends AbstractExprTransformer
{
    private Map<Expr, Expr> funcMap;

    RestoreFunAppVis(ExprFactory exprFactory, Map<Expr, Expr> funcMap)
    {
        super(exprFactory);
        this.funcMap = funcMap;
    }

    @Override
    public Expr visit(FuncApp e)
    {
        Expr func = funcMap.get(e);

        return func == null ? super.visit(e) : func;
    }
}

class GatherBooleanArgsVisitor extends AbstractExprTransformer
{
    GatherBooleanArgsVisitor(ExprFactory exprFactory)
    {
        super(exprFactory);
    }

    @Override
    public Expr visit(AndExpr e)
    {
        List<Expr> newArgs = new ArrayList<>();

        for (Expr arg : e)
        {
            arg = arg.accept(this);

            if (arg instanceof AndExpr)
            {
                for (Expr argsArg : (AndExpr)arg)
                    newArgs.add(argsArg);
            }
            else
                newArgs.add(arg);
        }

        assert newArgs.size() >= 2;

        return exprFactory.mkAND(newArgs.get(0), newArgs.get(1),
                                 newArgs.subList(2, newArgs.size()).toArray(new Expr[newArgs.size() - 2]));
    }

    @Override
    public Expr visit(OrExpr e)
    {
        List<Expr> newArgs = new ArrayList<>();

        for (Expr arg : e)
        {
            arg = arg.accept(this);

            if (arg instanceof OrExpr)
            {
                for (Expr argsArg : (OrExpr)arg)
                    newArgs.add(argsArg);
            }
            else
                newArgs.add(arg);
        }

        assert newArgs.size() >= 2;

        return exprFactory.mkOR(newArgs.get(0), newArgs.get(1),
                                newArgs.subList(2, newArgs.size()).toArray(new Expr[newArgs.size() - 2]));
    }
}

class BooleanSimplificationVisitor extends AbstractExprTransformer
{
    private TrueConst trueConst = exprFactory.mkTRUE();

    private FalseConst falseConst = exprFactory.mkFALSE();

    BooleanSimplificationVisitor(ExprFactory exprFactory)
    {
        super(exprFactory);
    }

    @Override
    public Expr visit(AndExpr e)
    {
        Map<Expr, Expr> newArgs = new IdentityHashMap<>();

        for (Expr arg : e)
        {
            arg = arg.accept(this);

            if (arg == trueConst)
                continue;

            if (arg == falseConst)
            {
                newArgs = null;
                break;
            }

            newArgs.put(arg, arg);
        }

        if (newArgs == null)
            return falseConst;
        else
        {
            int newArgsSize = newArgs.size();
            Expr[] newArgsArray = newArgs.keySet().toArray(new Expr[newArgsSize]);

            if (newArgsSize == 0)
                return trueConst;

            Expr arg0 = newArgsArray[0];

            if (newArgsSize > 1)
            {
                Expr arg1 = newArgsArray[1];
                return exprFactory.mkAND(arg0, arg1, newArgsSize == 2 ? null : Arrays.copyOfRange(newArgsArray, 2, newArgsArray.length));
            }
            else
                return arg0;
        }
    }

    @Override
    public Expr visit(ImplExpr e)
    {
        Expr premise = e.getPremise().accept(this);
        Expr conclusion = e.getConclusion().accept(this);

        if (premise == falseConst)
            return trueConst;
        else if (premise == trueConst)
            return conclusion;
        else if (conclusion == trueConst)
            return trueConst;
        else if (conclusion == falseConst)
            return exprFactory.mkNEG(premise).accept(this);
        else
            return exprFactory.mkIMPL(premise, conclusion);
    }

    @Override
    public Expr visit(NegExpr e)
    {
        Expr arg = e.getArg().accept(this);

        if (arg == trueConst)
            return falseConst;
        else if (arg == falseConst)
            return trueConst;
        else if (arg instanceof NegExpr)
            return ((NegExpr) arg).getArg().accept(this);
        else
            return exprFactory.mkNEG(arg);
    }

    @Override
    public Expr visit(OrExpr e)
    {
        Map<Expr, Expr> newArgs = new IdentityHashMap<>();

        for (Expr arg : e)
        {
            arg = arg.accept(this);

            if (arg == trueConst)
            {
                newArgs = null;
                break;
            }

            if (arg == falseConst)
                continue;

            newArgs.put(arg, arg);
        }

        if (newArgs == null)
            return trueConst;
        else
        {
            int newArgsSize = newArgs.size();
            Expr[] newArgsArray = newArgs.keySet().toArray(new Expr[newArgsSize]);

            if (newArgsSize == 0)
                return falseConst;

            Expr arg0 = newArgsArray[0];

            if (newArgsSize > 1)
            {
                Expr arg1 = newArgsArray[1];

                return exprFactory.mkOR(arg0, arg1, newArgsSize == 2 ? null : Arrays.copyOfRange(newArgsArray, 2, newArgsArray.length));
            }
            else
                return arg0;
        }

    }

    @Override
    public Expr visit(EqExpr e)
    {
        Expr left = e.getLeft().accept(this), right = e.getRight().accept(this);

        if (left == right)
            return exprFactory.mkTRUE();

        if (ExprUtils.isBooleanConstant(exprFactory, left) && ExprUtils.isBooleanConstant(exprFactory, right))
            return exprFactory.mkFALSE();

        return exprFactory.mkEQ(left, right);
    }

    @Override
    public Expr visit(ExistentialQuantifier e)
    {
        Expr body = e.getBody().accept(this);

        if (ExprUtils.isBoolConst(exprFactory, body))
            return body;

        return exprFactory.mkEXIST(e.getBoundVars(), body, null);
    }

    @Override
    public Expr visit(UniversalQuantifier e)
    {
        Expr body = e.getBody().accept(this);

        if (ExprUtils.isBoolConst(exprFactory, body))
            return body;

        return exprFactory.mkFORALL(e.getBoundVars(), body, null);
    }

    @Override
    public Expr visit(Expr e)
    {
        return e;
    }

}

class Z3MarshalVisitor extends CachedExprRetVisitor<com.microsoft.z3.AST>
{
    private Map<Expr, com.microsoft.z3.AST> cachedResults;

    private Context z3Context;

    public Z3MarshalVisitor(Context z3Context)
    {
        this(z3Context, null);
    }

    public Z3MarshalVisitor(Context z3Context, Map<Expr, com.microsoft.z3.AST> results)
    {
        this.z3Context = z3Context;
        if (results != null)
            cachedResults = results;
        else
            cachedResults = new HashMap<>();
    }

    public Map<Expr, com.microsoft.z3.AST> getInternalMapping()
    {
        return cachedResults;
    }

    @Override
    public com.microsoft.z3.AST visit(AndExpr e)
    {
        if (!alreadyVisited(e))
        {
            int argNum = e.argNum();
            com.microsoft.z3.BoolExpr[] z3Args = new com.microsoft.z3.BoolExpr[argNum];
            for (int i = 0; i < argNum; ++i)
                z3Args[i] = (com.microsoft.z3.BoolExpr) e.argAt(i).accept(this);

            cachedResults.put(e, z3Context.mkAnd(z3Args));
        }

        return cachedResults.get(e);
    }

    @Override
    public com.microsoft.z3.AST visit(EqExpr e)
    {
        if (!alreadyVisited(e))
        {
            com.microsoft.z3.Expr left = (com.microsoft.z3.Expr) e.getLeft().accept(this);
            com.microsoft.z3.Expr right = (com.microsoft.z3.Expr) e.getRight().accept(this);

            cachedResults.put(e, z3Context.mkEq(left, right));
        }

        return cachedResults.get(e);
    }

    @Override
    public AST visit(ExistentialQuantifier e)
    {
        if (!alreadyVisited(e))
        {
            int boundedVarNum = e.getBoundedVarNum();
            com.microsoft.z3.Expr[] boundVars = new com.microsoft.z3.Expr[boundedVarNum];

            for (int i = 0; i < boundedVarNum; ++i)
            {
                boundVars[i] = (com.microsoft.z3.Expr) e.boundedVarAt(i).accept(this);
            }

            cachedResults.put(e, z3Context.mkExists(boundVars, (com.microsoft.z3.Expr) e.getBody().accept(this), 1, null, null, null, null));
        }

        return cachedResults.get(e);
    }

    @Override
    public AST visit(UniversalQuantifier e)
    {
        if (!alreadyVisited(e))
        {
            int boundedVarNum = e.getBoundedVarNum();
            com.microsoft.z3.Expr[] boundVars = new com.microsoft.z3.Expr[boundedVarNum];

            for (int i = 0; i < boundedVarNum; ++i)
            {
                boundVars[i] = (com.microsoft.z3.Expr) e.boundedVarAt(i).accept(this);
            }

            cachedResults.put(e, z3Context.mkForall(boundVars, (com.microsoft.z3.Expr) e.getBody().accept(this), 1, null, null, null, null));
        }

        return cachedResults.get(e);
    }

    @Override
    public com.microsoft.z3.AST visit(GreaterEqExpr e)
    {
        if (!alreadyVisited(e))
        {
            com.microsoft.z3.ArithExpr left = (com.microsoft.z3.ArithExpr) e.getLeft().accept(this);
            com.microsoft.z3.ArithExpr right = (com.microsoft.z3.ArithExpr) e.getRight().accept(this);

            cachedResults.put(e, z3Context.mkGe(left, right));
        }

        return cachedResults.get(e);

    }

    @Override
    public com.microsoft.z3.AST visit(GreaterExpr e)
    {
        if (!alreadyVisited(e))
        {
            com.microsoft.z3.ArithExpr left = (com.microsoft.z3.ArithExpr) e.getLeft().accept(this);
            com.microsoft.z3.ArithExpr right = (com.microsoft.z3.ArithExpr) e.getRight().accept(this);

            cachedResults.put(e, z3Context.mkGt(left, right));
        }

        return cachedResults.get(e);
    }

    @Override
    public com.microsoft.z3.AST visit(ImplExpr e)
    {
        if (!alreadyVisited(e))
        {
            com.microsoft.z3.BoolExpr premise = (com.microsoft.z3.BoolExpr) e.getPremise().accept(this);
            com.microsoft.z3.BoolExpr conclusion = (com.microsoft.z3.BoolExpr) e.getConclusion().accept(this);

            cachedResults.put(e, z3Context.mkImplies(premise, conclusion));
        }

        return cachedResults.get(e);

    }

    @Override
    public com.microsoft.z3.AST visit(LessEqExpr e)
    {
        if (!alreadyVisited(e))
        {
            com.microsoft.z3.ArithExpr left = (com.microsoft.z3.ArithExpr) e.getLeft().accept(this);
            com.microsoft.z3.ArithExpr right = (com.microsoft.z3.ArithExpr) e.getRight().accept(this);

            cachedResults.put(e, z3Context.mkLe(left, right));
        }

        return cachedResults.get(e);

    }

    @Override
    public com.microsoft.z3.AST visit(LessExpr e)
    {
        if (!alreadyVisited(e))
        {
            com.microsoft.z3.ArithExpr left = (com.microsoft.z3.ArithExpr) e.getLeft().accept(this);
            com.microsoft.z3.ArithExpr right = (com.microsoft.z3.ArithExpr) e.getRight().accept(this);

            cachedResults.put(e, z3Context.mkLt(left, right));
        }

        return cachedResults.get(e);

    }

    @Override
    public com.microsoft.z3.AST visit(NegExpr e)
    {
        if (!alreadyVisited(e))
        {
            com.microsoft.z3.BoolExpr arg = (com.microsoft.z3.BoolExpr) e.getArg().accept(this);

            cachedResults.put(e, z3Context.mkNot(arg));
        }

        return cachedResults.get(e);

    }

    @Override
    public com.microsoft.z3.AST visit(OrExpr e)
    {
        if (!alreadyVisited(e))
        {
            int argNum = e.argNum();
            com.microsoft.z3.BoolExpr[] z3Args = new com.microsoft.z3.BoolExpr[argNum];
            for (int i = 0; i < argNum; ++i)
                z3Args[i] = (com.microsoft.z3.BoolExpr) e.argAt(i).accept(this);

            cachedResults.put(e, z3Context.mkOr(z3Args));
        }

        return cachedResults.get(e);

    }

    @Override
    public com.microsoft.z3.AST visit(ConstInteger e)
    {
        if (!alreadyVisited(e))
        {
            cachedResults.put(e, z3Context.mkInt(e.getVal().toString()));
        }

        return cachedResults.get(e);

    }

    @Override
    public AST visit(ConstString e)
    {
        if (!alreadyVisited(e))
        {
            cachedResults.put(e, z3Context.mkString(e.getVal()));
        }

        return cachedResults.get(e);
    }

    @Override
    public com.microsoft.z3.AST visit(FalseConst e)
    {
        if (!alreadyVisited(e))
        {
            cachedResults.put(e, z3Context.mkFalse());
        }

        return cachedResults.get(e);

    }

    @Override
    public AST visit(BoundVar e)
    {
        if (!alreadyVisited(e))
        {
            com.microsoft.z3.FuncDecl decl = (com.microsoft.z3.FuncDecl) e.getDecl().accept(this);

            cachedResults.put(e, z3Context.mkConst(decl.getName(), decl.getRange()));
        }

        return cachedResults.get(e);
    }

    @Override
    public com.microsoft.z3.AST visit(FuncApp e)
    {
        if (!alreadyVisited(e))
        {
            com.microsoft.z3.FuncDecl decl = (com.microsoft.z3.FuncDecl) e.getDecl().accept(this);

            int argNum = e.argNum();
            com.microsoft.z3.Expr[] z3Args = new com.microsoft.z3.Expr[argNum];

            for (int i = 0; i < argNum; ++i)
            {
                z3Args[i] = (com.microsoft.z3.Expr) e.argAt(i).accept(this);
            }

            cachedResults.put(e, z3Context.mkApp(decl, z3Args));
        }

        return cachedResults.get(e);

    }

    @Override
    public com.microsoft.z3.AST visit(TrueConst e)
    {
        if (!alreadyVisited(e))
        {
            cachedResults.put(e, z3Context.mkTrue());
        }

        return cachedResults.get(e);

    }

    @Override
    public com.microsoft.z3.AST visit(FuncDecl e)
    {
        if (!alreadyVisited(e))
        {
            ExprTypeRetVisitor<Sort> tyVisitor = new ExprTypeRetVisitor<Sort>()
            {
                @Override
                public Sort visit(BooleanType type)
                {
                    return z3Context.mkBoolSort();
                }

                @Override
                public Sort visit(FunctionType type)
                {
                    // This is called only for array types.
                    Sort range = type.getCoDomain().accept(this);

                    // Z3 crashes on MAC if we use the generic method with n domain arguments.
                    return z3Context.mkArraySort(type.getDomain()[0].accept(this), range);
                }

                @Override
                public Sort visit(IntegerType type)
                {
                    return z3Context.mkIntSort();
                }

                @Override
                public Sort visit(StringType type)
                {
                    return z3Context.mkStringSort();
                }

                @Override
                public Sort visit(edu.utexas.cs.utopia.cfpchecker.expression.type.ArrayType type)
                {
                    Sort domain = type.getDomain()[0].accept(this);
                    Sort coDomain = type.getCoDomain().accept(this);
                    return z3Context.mkArraySort(domain, coDomain);
                }

                @Override
                public Sort visit(UnitType type)
                {
                    // Z3 doesn't seem to have a unit type.
                    return null;
                }
            };

            FunctionType funTy = e.getType();

            ExprType[] expressoDomain = funTy.getDomain();
            Sort[] allDomains = new Sort[expressoDomain.length];

            for (int i = 0; i < expressoDomain.length; i++)
            {
                ExprType exprType = expressoDomain[i];
                allDomains[i] = exprType.accept(tyVisitor);
            }
            Sort range = funTy.getCoDomain().accept(tyVisitor);

            cachedResults.put(e, z3Context.mkFuncDecl(e.getName(), allDomains, range));
        }

        return cachedResults.get(e);

    }

    @Override
    public com.microsoft.z3.AST visit(DivExpr e)
    {
        if (!alreadyVisited(e))
        {
            com.microsoft.z3.ArithExpr dividend = (com.microsoft.z3.ArithExpr) e.getDividend().accept(this);
            com.microsoft.z3.ArithExpr divisor = (com.microsoft.z3.ArithExpr) e.getDivisor().accept(this);

            cachedResults.put(e, z3Context.mkDiv(dividend, divisor));
        }

        return cachedResults.get(e);

    }

    @Override
    public AST visit(RemExpr e)
    {
        if (!alreadyVisited(e))
        {
            com.microsoft.z3.IntExpr dividend = (com.microsoft.z3.IntExpr) e.getDividend().accept(this);
            com.microsoft.z3.IntExpr divisor = (com.microsoft.z3.IntExpr) e.getDivisor().accept(this);

            cachedResults.put(e, z3Context.mkRem(dividend, divisor));
        }

        return cachedResults.get(e);
    }

    @Override
    public AST visit(ModExpr e)
    {
        if (!alreadyVisited(e))
        {
            com.microsoft.z3.IntExpr dividend = (com.microsoft.z3.IntExpr) e.getDividend().accept(this);
            com.microsoft.z3.IntExpr divisor = (com.microsoft.z3.IntExpr) e.getDivisor().accept(this);

            cachedResults.put(e, z3Context.mkMod(dividend, divisor));
        }

        return cachedResults.get(e);
    }

    @Override
    public com.microsoft.z3.AST visit(MinusExpr e)
    {
        if (!alreadyVisited(e))
        {
            int argNum = e.argNum();
            com.microsoft.z3.ArithExpr[] z3Args = new com.microsoft.z3.ArithExpr[argNum];

            for (int i = 0; i < argNum; i++)
                z3Args[i] = (com.microsoft.z3.ArithExpr) e.argAt(i).accept(this);

            cachedResults.put(e, z3Context.mkSub(z3Args));
        }

        return cachedResults.get(e);

    }

    @Override
    public com.microsoft.z3.AST visit(MultExpr e)
    {
        if (!alreadyVisited(e))
        {
            int argNum = e.argNum();
            com.microsoft.z3.ArithExpr[] z3Args = new com.microsoft.z3.ArithExpr[argNum];

            for (int i = 0; i < argNum; i++)
                z3Args[i] = (com.microsoft.z3.ArithExpr) e.argAt(i).accept(this);

            cachedResults.put(e, z3Context.mkMul(z3Args));
        }

        return cachedResults.get(e);

    }

    @Override
    public com.microsoft.z3.AST visit(PlusExpr e)
    {
        if (!alreadyVisited(e))
        {
            int argNum = e.argNum();
            com.microsoft.z3.ArithExpr[] z3Args = new com.microsoft.z3.ArithExpr[argNum];

            for (int i = 0; i < argNum; i++)
                z3Args[i] = (com.microsoft.z3.ArithExpr) e.argAt(i).accept(this);

            cachedResults.put(e, z3Context.mkAdd(z3Args));
        }

        return cachedResults.get(e);

    }

    @Override
    public AST visit(SelectExpr e)
    {
        if (!alreadyVisited(e))
        {
            com.microsoft.z3.ArrayExpr arrayExpr = (com.microsoft.z3.ArrayExpr) e.getArray().accept(this);
            com.microsoft.z3.Expr indexExpr = (com.microsoft.z3.Expr) e.getIndexExpr().accept(this);

            cachedResults.put(e, z3Context.mkSelect(arrayExpr, indexExpr));
        }

        return cachedResults.get(e);
    }

    @Override
    public AST visit(StoreExpr e)
    {
        if (!alreadyVisited(e))
        {
            com.microsoft.z3.ArrayExpr arrayExpr = (com.microsoft.z3.ArrayExpr) e.getArray().accept(this);
            com.microsoft.z3.Expr indexExpr = (com.microsoft.z3.Expr) e.getIndexExpr().accept(this);
            com.microsoft.z3.Expr newValExpr = (com.microsoft.z3.Expr) e.getNewValue().accept(this);

            cachedResults.put(e, z3Context.mkStore(arrayExpr, indexExpr, newValExpr));
        }

        return cachedResults.get(e);
    }

    // These cases will not called!
    @Override
    public com.microsoft.z3.AST visit(BoolExpr e)
    {
        assert false;

        return null;

    }

    @Override
    public com.microsoft.z3.AST visit(Expr e)
    {
        assert false;

        return null;

    }

    @Override
    public com.microsoft.z3.AST visit(FuncExpr e)
    {
        assert false;

        return null;

    }

    @Override
    public com.microsoft.z3.AST visit(IntExpr e)
    {
        assert false;

        return null;

    }

    @Override
    public com.microsoft.z3.AST visit(TermExpr e)
    {
        assert false;

        return null;

    }

    @Override
    public AST visit(ArrayExpr e)
    {
        assert false;

        return null;
    }
}

class IsVarVisitor extends TopLevelRetVisitor<Boolean>
{
    @Override
    public Boolean visit(Expr e)
    {
        return false;
    }

    @Override
    public Boolean visit(FuncApp e)
    {
        return e.argNum() == 0;
    }
}

class RetrieveFuncDeclVis extends TopLevelRetVisitor<FuncDecl>
{
    @Override
    public FuncDecl visit(Expr e)
    {
        return null;
    }

    @Override
    public FuncDecl visit(FuncApp e)
    {
        return e.getDecl();
    }

    @Override
    public FuncDecl visit(FuncDecl e)
    {
        return e;
    }
}

class SmtExpr {
    public final Formula f;
    public final FunctionDeclaration decl;

    public SmtExpr(Formula f) {
        this.f = f;
        this.decl = null;
    }

    public SmtExpr(FunctionDeclaration decl) {
        this.f = null;
        this.decl = decl;
    }
}

class JavaSmtMarshalVisitor extends CachedExprRetVisitor<SmtExpr>
{
    private Map<Expr, SmtExpr> cachedResults;

    private FormulaManager mgr;

    ExprTypeRetVisitor<FormulaType> typeVisitor = new ExprTypeRetVisitor<FormulaType>()
    {
        @Override
        public FormulaType visit(BooleanType type)
        {
            return FormulaType.BooleanType;
        }

        @Override
        public FormulaType visit(FunctionType type)
        {
            // This is called only for array types.
            FormulaType range = type.getCoDomain().accept(this);

            // Z3 crashes on MAC if we use the generic method with n domain arguments.
            return FormulaType.getArrayType(type.getDomain()[0].accept(this), range);
        }

        @Override
        public FormulaType visit(IntegerType type)
        {
            return FormulaType.IntegerType;
        }

        @Override
        public FormulaType visit(StringType type)
        {
            // TODO: Temporary solution to bypass the lack of string in java-smt.
            return FormulaType.IntegerType;
        }

        @Override
        public FormulaType visit(edu.utexas.cs.utopia.cfpchecker.expression.type.ArrayType type)
        {
            FormulaType domain = type.getDomain()[0].accept(this);
            FormulaType coDomain = type.getCoDomain().accept(this);
            return FormulaType.getArrayType(domain, coDomain);
        }

        @Override
        public FormulaType visit(UnitType type)
        {
            // Z3 doesn't seem to have a unit type.
            return null;
        }
    };

    public JavaSmtMarshalVisitor(FormulaManager mgr)
    {
        this(mgr, null);
    }

    public JavaSmtMarshalVisitor(FormulaManager mgr, Map<Expr, SmtExpr> results)
    {
        this.mgr = mgr;
        if (results != null)
            cachedResults = results;
        else
            cachedResults = new HashMap<>();
    }

    public Map<Expr, SmtExpr> getInternalMapping()
    {
        return cachedResults;
    }

    private FormulaType getOperableType(FormulaType t1, FormulaType t2) throws TypeException {
        if(t1.equals(t2)) {
            return t1;
        }

        if(t1.isArrayType() || t2.isArrayType()) {
            throw new TypeException("Both types must be arrays");
        }

        if(t1.isFloatingPointType() || t2.isFloatingPointType() || t1.isFloatingPointRoundingModeType() || t2.isFloatingPointRoundingModeType()) {
            return t1.isFloatingPointType() || t1.isFloatingPointRoundingModeType() ? t1 : t2;
        }

        if(t1.isBitvectorType() || t2.isBitvectorType()) {
            throw new TypeException("A bitvector can only operate with another bitvector or a float");
        }

        if(t1.isRationalType() || t2.isRationalType()) {
            return t1.isRationalType() ? t1 : t2;
        }

        if(t1.isIntegerType() || t2.isIntegerType()) {
            return t1.isIntegerType() ? t1 : t2;
        }

        throw new TypeException("Unknown reason, at this point we should only have BooleanType");
    }

    private Formula convertTo(FormulaManager mgr, Formula f, FormulaType t) throws TypeException {
        if(mgr.getFormulaType(f).equals(t)) {
            return f;
        }

        //floating point has ability to cast. Don't know if it can cast from say a boolean or not
        if(t.isFloatingPointType() || t.isFloatingPointRoundingModeType()) {
            //Doesn't specify castable types but I imagine an Array can't be cast to a float. Appears you can cast bitvectors
            assert !mgr.getFormulaType(f).isArrayType();

            //probably need to revisit the signed at some point
            return mgr.getFloatingPointFormulaManager().castFrom(f, true, (FormulaType.FloatingPointType) t);
        }

        //rational type can operate on integers
        if(t.isRationalType()) {
            assert mgr.getFormulaType(f).isIntegerType();

            return f;
        }

        //may be other ways to convert
        throw new TypeException("Cannot convert " + mgr.getFormulaType(f) + " to " + t);
    }

    @Override
    public SmtExpr visit(AndExpr e)
    {
        if (!alreadyVisited(e))
        {
            BooleanFormulaManager bmgr = mgr.getBooleanFormulaManager();
            int argNum = e.argNum();
            BooleanFormula[] args = new BooleanFormula[argNum];
            for (int i = 0; i < argNum; ++i) {
                Formula arg = e.argAt(i).accept(this).f;

                assert(mgr.getFormulaType(arg).isBooleanType());

                args[i] = (BooleanFormula) arg;
            }

            BooleanFormula newF = bmgr.and(args);

            cachedResults.put(e, new SmtExpr(newF));
        }

        return cachedResults.get(e);
    }

    @Override
    public SmtExpr visit(EqExpr e) {
        if (!alreadyVisited(e)) {
            Formula left = e.getLeft().accept(this).f;
            Formula right = e.getRight().accept(this).f;

            try {
                FormulaType t = getOperableType(mgr.getFormulaType(left), mgr.getFormulaType(right));

                if(t.isArrayType()) {
                    ArrayFormulaManager amgr = mgr.getArrayFormulaManager();
                    cachedResults.put(e, new SmtExpr(amgr.equivalence((ArrayFormula) convertTo(mgr, left, t), (ArrayFormula) convertTo(mgr, right, t))));
                }
                else if(t.isFloatingPointRoundingModeType()) {
                    FloatingPointFormulaManager fmgr = mgr.getFloatingPointFormulaManager();
                    cachedResults.put(e, new SmtExpr(fmgr.equalWithFPSemantics((FloatingPointFormula) convertTo(mgr, left, t), (FloatingPointFormula) convertTo(mgr, right, t))));
                }
                else if(t.isBitvectorType()) {
                    BitvectorFormulaManager bitmgr = mgr.getBitvectorFormulaManager();
                    cachedResults.put(e, new SmtExpr(bitmgr.equal((BitvectorFormula) convertTo(mgr, left, t), (BitvectorFormula) convertTo(mgr, right, t))));
                }
                else if(t.isRationalType()) {
                    RationalFormulaManager rmgr = mgr.getRationalFormulaManager();
                    cachedResults.put(e, new SmtExpr(rmgr.equal((NumeralFormula) convertTo(mgr, left, t), (NumeralFormula) convertTo(mgr, right, t))));
                }
                else if(t.isIntegerType()) {
                    IntegerFormulaManager imgr = mgr.getIntegerFormulaManager();
                    cachedResults.put(e, new SmtExpr(imgr.equal((NumeralFormula.IntegerFormula) convertTo(mgr, left, t), (NumeralFormula.IntegerFormula) convertTo(mgr, right, t))));
                }
                else if(t.isBooleanType()) {
                    BooleanFormulaManager bmgr = mgr.getBooleanFormulaManager();
                    cachedResults.put(e, new SmtExpr(bmgr.equivalence((BooleanFormula) convertTo(mgr, left, t), (BooleanFormula) convertTo(mgr, right, t))));
                }
                else {
                    assert false;
                }
            }
            catch(Exception ex) {
                assert false;
            }
        }

        return cachedResults.get(e);
    }

    @Override
    public SmtExpr visit(ExistentialQuantifier e)
    {
        if (!alreadyVisited(e))
        {
            QuantifiedFormulaManager qmgr = mgr.getQuantifiedFormulaManager();

            int boundedVarNum = e.getBoundedVarNum();
            List<Formula> boundVars = new ArrayList<>(boundedVarNum);

            for (int i = 0; i < boundedVarNum; ++i) {
                boundVars.add(e.boundedVarAt(i).accept(this).f);
            }

            Formula body = e.getBody().accept(this).f;

            assert(mgr.getFormulaType(body).isBooleanType());

            cachedResults.put(e, new SmtExpr(qmgr.exists(boundVars, (BooleanFormula) body)));
        }

        return cachedResults.get(e);
    }

    @Override
    public SmtExpr visit(UniversalQuantifier e)
    {
        if (!alreadyVisited(e))
        {
            QuantifiedFormulaManager qmgr = mgr.getQuantifiedFormulaManager();

            int boundedVarNum = e.getBoundedVarNum();
            List<Formula> boundVars = new ArrayList<>(boundedVarNum);

            for (int i = 0; i < boundedVarNum; ++i) {
                boundVars.add(e.boundedVarAt(i).accept(this).f);
            }

            Formula body = e.getBody().accept(this).f;

            assert(mgr.getFormulaType(body).isBooleanType());

            cachedResults.put(e, new SmtExpr(qmgr.forall(boundVars, (BooleanFormula) body)));
        }

        return cachedResults.get(e);
    }

    @Override
    public SmtExpr visit(GreaterEqExpr e)
    {
        if (!alreadyVisited(e))
        {
            Formula left = e.getLeft().accept(this).f;
            Formula right = e.getRight().accept(this).f;

            try {
                FormulaType t = getOperableType(mgr.getFormulaType(left), mgr.getFormulaType(right));

                if(t.isFloatingPointRoundingModeType()) {
                    FloatingPointFormulaManager fmgr = mgr.getFloatingPointFormulaManager();
                    cachedResults.put(e, new SmtExpr(fmgr.greaterOrEquals((FloatingPointFormula) convertTo(mgr, left, t), (FloatingPointFormula) convertTo(mgr, right, t))));
                }
                else if(t.isBitvectorType()) {
                    BitvectorFormulaManager bitmgr = mgr.getBitvectorFormulaManager();
                    //for now assuming bitvector is signed
                    cachedResults.put(e, new SmtExpr(bitmgr.greaterOrEquals((BitvectorFormula) convertTo(mgr, left, t), (BitvectorFormula) convertTo(mgr, right, t), true)));
                }
                else if(t.isRationalType()) {
                    RationalFormulaManager rmgr = mgr.getRationalFormulaManager();
                    cachedResults.put(e, new SmtExpr(rmgr.greaterOrEquals((NumeralFormula) convertTo(mgr, left, t), (NumeralFormula) convertTo(mgr, right, t))));
                }
                else if(t.isIntegerType()) {
                    IntegerFormulaManager imgr = mgr.getIntegerFormulaManager();
                    cachedResults.put(e, new SmtExpr(imgr.greaterOrEquals((NumeralFormula.IntegerFormula) convertTo(mgr, left, t), (NumeralFormula.IntegerFormula) convertTo(mgr, right, t))));
                }
                else {
                    assert false;
                }
            }
            catch(Exception ex) {
                assert false;
            }
        }

        return cachedResults.get(e);

    }

    @Override
    public SmtExpr visit(GreaterExpr e)
    {
        if (!alreadyVisited(e))
        {
            Formula left = e.getLeft().accept(this).f;
            Formula right = e.getRight().accept(this).f;

            try {
                FormulaType t = getOperableType(mgr.getFormulaType(left), mgr.getFormulaType(right));

                if(t.isFloatingPointRoundingModeType()) {
                    FloatingPointFormulaManager fmgr = mgr.getFloatingPointFormulaManager();
                    cachedResults.put(e, new SmtExpr(fmgr.greaterThan((FloatingPointFormula) convertTo(mgr, left, t), (FloatingPointFormula) convertTo(mgr, right, t))));
                }
                else if(t.isBitvectorType()) {
                    BitvectorFormulaManager bitmgr = mgr.getBitvectorFormulaManager();
                    //for now assuming bitvector is signed
                    cachedResults.put(e, new SmtExpr(bitmgr.greaterThan((BitvectorFormula) convertTo(mgr, left, t), (BitvectorFormula) convertTo(mgr, right, t), true)));
                }
                else if(t.isRationalType()) {
                    RationalFormulaManager rmgr = mgr.getRationalFormulaManager();
                    cachedResults.put(e, new SmtExpr(rmgr.greaterThan((NumeralFormula) convertTo(mgr, left, t), (NumeralFormula) convertTo(mgr, right, t))));
                }
                else if(t.isIntegerType()) {
                    IntegerFormulaManager imgr = mgr.getIntegerFormulaManager();
                    cachedResults.put(e, new SmtExpr(imgr.greaterThan((NumeralFormula.IntegerFormula) convertTo(mgr, left, t), (NumeralFormula.IntegerFormula) convertTo(mgr, right, t))));
                }
                else {
                    assert false;
                }
            }
            catch(Exception ex) {
                assert false;
            }
        }

        return cachedResults.get(e);
    }

    @Override
    public SmtExpr visit(ImplExpr e)
    {
        if (!alreadyVisited(e))
        {
            BooleanFormulaManager bmgr = mgr.getBooleanFormulaManager();

            Formula premise = e.getPremise().accept(this).f;
            Formula conclusion = e.getConclusion().accept(this).f;

            assert(mgr.getFormulaType(premise).isBooleanType());
            assert(mgr.getFormulaType(conclusion).isBooleanType());

            cachedResults.put(e, new SmtExpr(bmgr.implication((BooleanFormula) premise, (BooleanFormula) conclusion)));
        }

        return cachedResults.get(e);

    }

    @Override
    public SmtExpr visit(LessEqExpr e)
    {
        if (!alreadyVisited(e))
        {
            Formula left = e.getLeft().accept(this).f;
            Formula right = e.getRight().accept(this).f;

            try {
                FormulaType t = getOperableType(mgr.getFormulaType(left), mgr.getFormulaType(right));

                if(t.isFloatingPointRoundingModeType()) {
                    FloatingPointFormulaManager fmgr = mgr.getFloatingPointFormulaManager();
                    cachedResults.put(e, new SmtExpr(fmgr.lessOrEquals((FloatingPointFormula) convertTo(mgr, left, t), (FloatingPointFormula) convertTo(mgr, right, t))));
                }
                else if(t.isBitvectorType()) {
                    BitvectorFormulaManager bitmgr = mgr.getBitvectorFormulaManager();
                    //for now assuming bitvector is signed
                    cachedResults.put(e, new SmtExpr(bitmgr.lessOrEquals((BitvectorFormula) convertTo(mgr, left, t), (BitvectorFormula) convertTo(mgr, right, t), true)));
                }
                else if(t.isRationalType()) {
                    RationalFormulaManager rmgr = mgr.getRationalFormulaManager();
                    cachedResults.put(e, new SmtExpr(rmgr.lessOrEquals((NumeralFormula) convertTo(mgr, left, t), (NumeralFormula) convertTo(mgr, right, t))));
                }
                else if(t.isIntegerType()) {
                    IntegerFormulaManager imgr = mgr.getIntegerFormulaManager();
                    cachedResults.put(e, new SmtExpr(imgr.lessOrEquals((NumeralFormula.IntegerFormula) convertTo(mgr, left, t), (NumeralFormula.IntegerFormula) convertTo(mgr, right, t))));
                }
                else {
                    assert false;
                }
            }
            catch(Exception ex) {
                assert false;
            }
        }

        return cachedResults.get(e);

    }

    @Override
    public SmtExpr visit(LessExpr e)
    {
        if (!alreadyVisited(e))
        {
            Formula left = e.getLeft().accept(this).f;
            Formula right = e.getRight().accept(this).f;

            try {
                FormulaType t = getOperableType(mgr.getFormulaType(left), mgr.getFormulaType(right));

                if(t.isFloatingPointRoundingModeType()) {
                    FloatingPointFormulaManager fmgr = mgr.getFloatingPointFormulaManager();
                    cachedResults.put(e, new SmtExpr(fmgr.lessThan((FloatingPointFormula) convertTo(mgr, left, t), (FloatingPointFormula) convertTo(mgr, right, t))));
                }
                else if(t.isBitvectorType()) {
                    BitvectorFormulaManager bitmgr = mgr.getBitvectorFormulaManager();
                    //for now assuming bitvector is signed
                    cachedResults.put(e, new SmtExpr(bitmgr.lessThan((BitvectorFormula) convertTo(mgr, left, t), (BitvectorFormula) convertTo(mgr, right, t), true)));
                }
                else if(t.isRationalType()) {
                    RationalFormulaManager rmgr = mgr.getRationalFormulaManager();
                    cachedResults.put(e, new SmtExpr(rmgr.lessThan((NumeralFormula) convertTo(mgr, left, t), (NumeralFormula) convertTo(mgr, right, t))));
                }
                else if(t.isIntegerType()) {
                    IntegerFormulaManager imgr = mgr.getIntegerFormulaManager();
                    cachedResults.put(e, new SmtExpr(imgr.lessThan((NumeralFormula.IntegerFormula) convertTo(mgr, left, t), (NumeralFormula.IntegerFormula) convertTo(mgr, right, t))));
                }
                else {
                    assert false;
                }
            }
            catch(Exception ex) {
                assert false;
            }
        }

        return cachedResults.get(e);

    }

    @Override
    public SmtExpr visit(NegExpr e)
    {
        if (!alreadyVisited(e))
        {
            BooleanFormulaManager bmgr = mgr.getBooleanFormulaManager();

            Formula arg = e.getArg().accept(this).f;

            assert(mgr.getFormulaType(arg).isBooleanType());

            cachedResults.put(e, new SmtExpr(bmgr.not((BooleanFormula) arg)));
        }

        return cachedResults.get(e);

    }

    @Override
    public SmtExpr visit(OrExpr e)
    {
        if (!alreadyVisited(e))
        {
            BooleanFormulaManager bmgr = mgr.getBooleanFormulaManager();
            int argNum = e.argNum();
            List<BooleanFormula> args = new ArrayList<>(argNum);
            for (int i = 0; i < argNum; ++i) {
                Formula arg = e.argAt(i).accept(this).f;

                assert(mgr.getFormulaType(arg).isBooleanType());

                args.add((BooleanFormula) arg);
            }

            cachedResults.put(e, new SmtExpr(bmgr.or(args)));
        }

        return cachedResults.get(e);

    }

    @Override
    public SmtExpr visit(ConstInteger e)
    {
        if (!alreadyVisited(e)) {
            IntegerFormulaManager imgr = mgr.getIntegerFormulaManager();

            cachedResults.put(e, new SmtExpr(imgr.makeNumber(e.getVal())));
        }

        return cachedResults.get(e);

    }

    @Override
    public SmtExpr visit(ConstString e)
    {
        if (!alreadyVisited(e))
        {
            IntegerFormulaManager imgr = mgr.getIntegerFormulaManager();

            cachedResults.put(e, new SmtExpr(imgr.makeNumber(e.getVal().hashCode())));
        }

        return cachedResults.get(e);
    }

    @Override
    public SmtExpr visit(FalseConst e)
    {
        if (!alreadyVisited(e))
        {
            BooleanFormulaManager bmgr = mgr.getBooleanFormulaManager();

            cachedResults.put(e, new SmtExpr(bmgr.makeFalse()));
        }

        return cachedResults.get(e);
    }

    @Override
    public SmtExpr visit(BoundVar e)
    {
        if (!alreadyVisited(e))
        {
            FunctionDeclaration decl = e.getDecl().accept(this).decl;

            cachedResults.put(e, new SmtExpr(mgr.makeVariable(decl.getType(), decl.getName())));
        }

        return cachedResults.get(e);
    }

    @Override
    public SmtExpr visit(FuncApp e)
    {
        if (!alreadyVisited(e))
        {
            UFManager fnmgr = mgr.getUFManager();
            FunctionDeclaration decl = e.getDecl().accept(this).decl;

            int argNum = e.argNum();
            Formula[] args = new Formula[argNum];

            for (int i = 0; i < argNum; ++i)
            {
                args[i] = e.argAt(i).accept(this).f;
            }

            cachedResults.put(e, new SmtExpr(fnmgr.callUF(decl, args)));
        }

        return cachedResults.get(e);

    }

    @Override
    public SmtExpr visit(TrueConst e)
    {
        if (!alreadyVisited(e))
        {
            BooleanFormulaManager bmgr = mgr.getBooleanFormulaManager();

            cachedResults.put(e, new SmtExpr(bmgr.makeTrue()));
        }

        return cachedResults.get(e);

    }

    @Override
    public SmtExpr visit(FuncDecl e)
    {
        if (!alreadyVisited(e))
        {
            FunctionType funTy = e.getType();

            ExprType[] expressoDomains = funTy.getDomain();
            FormulaType domains[] = new FormulaType[expressoDomains.length];

            for (int i = 0; i < expressoDomains.length; i++)
            {
                domains[i] = expressoDomains[i].accept(typeVisitor);
            }
            FormulaType range = funTy.getCoDomain().accept(typeVisitor);

            FunctionDeclaration decl = mgr.getUFManager().declareUF(e.getName(), range, domains);
            cachedResults.put(e, new SmtExpr(decl));
        }

        return cachedResults.get(e);

    }

    @Override
    public SmtExpr visit(DivExpr e)
    {
        if (!alreadyVisited(e))
        {
            Formula dividend = e.getDividend().accept(this).f;
            Formula divisor = e.getDivisor().accept(this).f;

            try {
                FormulaType t = getOperableType(mgr.getFormulaType(dividend), mgr.getFormulaType(divisor));

                if(t.isFloatingPointRoundingModeType()) {
                    FloatingPointFormulaManager fmgr = mgr.getFloatingPointFormulaManager();
                    cachedResults.put(e, new SmtExpr(fmgr.divide((FloatingPointFormula) convertTo(mgr, dividend, t), (FloatingPointFormula) convertTo(mgr, divisor, t))));
                }
                else if(t.isBitvectorType()) {
                    BitvectorFormulaManager bitmgr = mgr.getBitvectorFormulaManager();
                    //for now assuming bitvector is signed
                    cachedResults.put(e, new SmtExpr(bitmgr.divide((BitvectorFormula) convertTo(mgr, dividend, t), (BitvectorFormula) convertTo(mgr, divisor, t), true)));
                }
                else if(t.isRationalType()) {
                    RationalFormulaManager rmgr = mgr.getRationalFormulaManager();
                    cachedResults.put(e, new SmtExpr(rmgr.divide((NumeralFormula) convertTo(mgr, dividend, t), (NumeralFormula) convertTo(mgr, divisor, t))));
                }
                else if(t.isIntegerType()) {
                    IntegerFormulaManager imgr = mgr.getIntegerFormulaManager();
                    cachedResults.put(e, new SmtExpr(imgr.divide((NumeralFormula.IntegerFormula) convertTo(mgr, dividend, t), (NumeralFormula.IntegerFormula) convertTo(mgr, divisor, t))));
                }
                else {
                    assert false;
                }
            }
            catch(Exception ex) {
                assert false;
            }
        }

        return cachedResults.get(e);

    }

    @Override
    public SmtExpr visit(RemExpr e)
    {
        //TODO: should I convert this to behave according to the rem semantics?
        Formula dividend = e.getDividend().accept(this).f;
        Formula divisor = e.getDivisor().accept(this).f;

        try {
            FormulaType t = getOperableType(mgr.getFormulaType(dividend), mgr.getFormulaType(divisor));

            if(t.isBitvectorType()) {
                BitvectorFormulaManager bitmgr = mgr.getBitvectorFormulaManager();
                //for now assuming bitvector is signed
                cachedResults.put(e, new SmtExpr(bitmgr.modulo((BitvectorFormula) convertTo(mgr, dividend, t), (BitvectorFormula) convertTo(mgr, divisor, t), true)));
            }
            else if(t.isIntegerType()) {
                IntegerFormulaManager imgr = mgr.getIntegerFormulaManager();
                cachedResults.put(e, new SmtExpr(imgr.modulo((NumeralFormula.IntegerFormula) convertTo(mgr, dividend, t), (NumeralFormula.IntegerFormula) convertTo(mgr, divisor, t))));
            }
            else {
                assert false;
            }
        }
        catch(Exception ex) {
            assert false;
        }

        return cachedResults.get(e);
    }

    @Override
    public SmtExpr visit(ModExpr e) {
        Formula dividend = e.getDividend().accept(this).f;
        Formula divisor = e.getDivisor().accept(this).f;

        try {
            FormulaType t = getOperableType(mgr.getFormulaType(dividend), mgr.getFormulaType(divisor));

            if(t.isBitvectorType()) {
                BitvectorFormulaManager bitmgr = mgr.getBitvectorFormulaManager();
                //for now assuming bitvector is signed
                cachedResults.put(e, new SmtExpr(bitmgr.modulo((BitvectorFormula) convertTo(mgr, dividend, t), (BitvectorFormula) convertTo(mgr, divisor, t), true)));
            }
            else if(t.isIntegerType()) {
                IntegerFormulaManager imgr = mgr.getIntegerFormulaManager();
                cachedResults.put(e, new SmtExpr(imgr.modulo((NumeralFormula.IntegerFormula) convertTo(mgr, dividend, t), (NumeralFormula.IntegerFormula) convertTo(mgr, divisor, t))));
            }
            else {
                assert false;
            }
        }
        catch(Exception ex) {
            assert false;
        }

        return cachedResults.get(e);
    }

    @Override
    public SmtExpr visit(MinusExpr e)
    {
        if (!alreadyVisited(e))
        {
            int argNum = e.argNum();
            Formula[] args = new Formula[argNum];

            args[0] = e.argAt(0).accept(this).f;
            FormulaType t = mgr.getFormulaType(args[0]);

            try {
                for(int i = 1; i < argNum; i++) {
                    args[i] = e.argAt(i).accept(this).f;
                    t = getOperableType(t, mgr.getFormulaType(args[i]));
                }

                if(t.isFloatingPointRoundingModeType()) {
                    FloatingPointFormulaManager fmgr = mgr.getFloatingPointFormulaManager();
                    FloatingPointFormula result = (FloatingPointFormula) convertTo(mgr, args[0], t);
                    for(int i = 1; i < argNum; i++) {
                        result = fmgr.subtract(result, (FloatingPointFormula) convertTo(mgr, args[i], t));
                    }
                    cachedResults.put(e, new SmtExpr(result));
                }
                else if(t.isBitvectorType()) {
                    BitvectorFormulaManager bitmgr = mgr.getBitvectorFormulaManager();
                    //for now assuming bitvector is signed
                    BitvectorFormula result = (BitvectorFormula) convertTo(mgr, args[0], t);
                    for(int i = 1; i < argNum; i++) {
                        result = bitmgr.subtract(result, (BitvectorFormula) convertTo(mgr, args[i], t));
                    }
                    cachedResults.put(e, new SmtExpr(result));
                }
                else if(t.isRationalType()) {
                    RationalFormulaManager rmgr = mgr.getRationalFormulaManager();
                    NumeralFormula.RationalFormula result = (NumeralFormula.RationalFormula) convertTo(mgr, args[0], t);
                    for(int i = 1; i < argNum; i++) {
                        result = rmgr.subtract(result, (NumeralFormula.RationalFormula) convertTo(mgr, args[i], t));
                    }
                    cachedResults.put(e, new SmtExpr(result));
                }
                else if(t.isIntegerType()) {
                    IntegerFormulaManager imgr = mgr.getIntegerFormulaManager();
                    NumeralFormula.IntegerFormula result = (NumeralFormula.IntegerFormula) convertTo(mgr, args[0], t);
                    for(int i = 1; i < argNum; i++) {
                        result = imgr.subtract(result, (NumeralFormula.IntegerFormula) convertTo(mgr, args[i], t));
                    }
                    cachedResults.put(e, new SmtExpr(result));
                }
            }
            catch(Exception ex) {
                assert false;
            }
        }

        return cachedResults.get(e);

    }

    @Override
    public SmtExpr visit(MultExpr e)
    {
        if (!alreadyVisited(e))
        {
            int argNum = e.argNum();
            Formula[] args = new Formula[argNum];

            args[0] = e.argAt(0).accept(this).f;
            FormulaType t = mgr.getFormulaType(args[0]);

            try {
                for(int i = 1; i < argNum; i++) {
                    args[i] = e.argAt(i).accept(this).f;
                    t = getOperableType(t, mgr.getFormulaType(args[i]));
                }

                if(t.isFloatingPointRoundingModeType()) {
                    FloatingPointFormulaManager fmgr = mgr.getFloatingPointFormulaManager();
                    FloatingPointFormula result = (FloatingPointFormula) convertTo(mgr, args[0], t);
                    for(int i = 1; i < argNum; i++) {
                        result = fmgr.multiply(result, (FloatingPointFormula) convertTo(mgr, args[i], t));
                    }
                    cachedResults.put(e, new SmtExpr(result));
                }
                else if(t.isBitvectorType()) {
                    BitvectorFormulaManager bitmgr = mgr.getBitvectorFormulaManager();
                    //for now assuming bitvector is signed
                    BitvectorFormula result = (BitvectorFormula) convertTo(mgr, args[0], t);
                    for(int i = 1; i < argNum; i++) {
                        result = bitmgr.multiply(result, (BitvectorFormula) convertTo(mgr, args[i], t));
                    }
                    cachedResults.put(e, new SmtExpr(result));
                }
                else if(t.isRationalType()) {
                    RationalFormulaManager rmgr = mgr.getRationalFormulaManager();
                    NumeralFormula.RationalFormula result = (NumeralFormula.RationalFormula) convertTo(mgr, args[0], t);
                    for(int i = 1; i < argNum; i++) {
                        result = rmgr.multiply(result, (NumeralFormula.RationalFormula) convertTo(mgr, args[i], t));
                    }
                    cachedResults.put(e, new SmtExpr(result));
                }
                else if(t.isIntegerType()) {
                    IntegerFormulaManager imgr = mgr.getIntegerFormulaManager();
                    NumeralFormula.IntegerFormula result = (NumeralFormula.IntegerFormula) convertTo(mgr, args[0], t);
                    for(int i = 1; i < argNum; i++) {
                        result = imgr.multiply(result, (NumeralFormula.IntegerFormula) convertTo(mgr, args[i], t));
                    }
                    cachedResults.put(e, new SmtExpr(result));
                }
            }
            catch(Exception ex) {
                assert false;
            }
        }

        return cachedResults.get(e);

    }

    @Override
    public SmtExpr visit(PlusExpr e)
    {
        if (!alreadyVisited(e))
        {
            int argNum = e.argNum();
            Formula[] args = new Formula[argNum];

            args[0] = e.argAt(0).accept(this).f;
            FormulaType t = mgr.getFormulaType(args[0]);

            try {
                for(int i = 1; i < argNum; i++) {
                    args[i] = e.argAt(i).accept(this).f;
                    t = getOperableType(t, mgr.getFormulaType(args[i]));
                }

                if(t.isFloatingPointRoundingModeType()) {
                    FloatingPointFormulaManager fmgr = mgr.getFloatingPointFormulaManager();
                    FloatingPointFormula result = (FloatingPointFormula) convertTo(mgr, args[0], t);
                    for(int i = 1; i < argNum; i++) {
                        result = fmgr.add(result, (FloatingPointFormula) convertTo(mgr, args[i], t));
                    }
                    cachedResults.put(e, new SmtExpr(result));
                }
                else if(t.isBitvectorType()) {
                    BitvectorFormulaManager bitmgr = mgr.getBitvectorFormulaManager();
                    //for now assuming bitvector is signed
                    BitvectorFormula result = (BitvectorFormula) convertTo(mgr, args[0], t);
                    for(int i = 1; i < argNum; i++) {
                        result = bitmgr.add(result, (BitvectorFormula) convertTo(mgr, args[i], t));
                    }
                    cachedResults.put(e, new SmtExpr(result));
                }
                else if(t.isRationalType()) {
                    RationalFormulaManager rmgr = mgr.getRationalFormulaManager();
                    List<NumeralFormula> convertedArgs = new ArrayList<>();

                    for(int i = 0; i < argNum; i++) {
                        convertedArgs.add((NumeralFormula) convertTo(mgr, args[i], t));
                    }

                    cachedResults.put(e, new SmtExpr(rmgr.sum(convertedArgs)));
                }
                else if(t.isIntegerType()) {
                    IntegerFormulaManager imgr = mgr.getIntegerFormulaManager();
                    List<NumeralFormula.IntegerFormula> convertedArgs = new ArrayList<>();

                    for(int i = 0; i < argNum; i++) {
                        convertedArgs.add((NumeralFormula.IntegerFormula) convertTo(mgr, args[i], t));
                    }

                    cachedResults.put(e, new SmtExpr(imgr.sum(convertedArgs)));
                }
                else {
                    assert false;
                }
            }
            catch(Exception ex) {
                assert false;
            }
        }

        return cachedResults.get(e);

    }

    @Override
    public SmtExpr visit(SelectExpr e)
    {
        if (!alreadyVisited(e))
        {
            ArrayFormulaManager amgr = mgr.getArrayFormulaManager();

            Formula array = e.getArray().accept(this).f;
            Formula index = e.getIndexExpr().accept(this).f;

            assert(mgr.getFormulaType(array).isArrayType());

            cachedResults.put(e, new SmtExpr(amgr.select((ArrayFormula) array, index)));
        }

        return cachedResults.get(e);
    }

    @Override
    public SmtExpr visit(StoreExpr e)
    {
        if (!alreadyVisited(e))
        {
            ArrayFormulaManager amgr = mgr.getArrayFormulaManager();

            Formula array = e.getArray().accept(this).f;
            Formula index = e.getIndexExpr().accept(this).f;
            Formula val = e.getNewValue().accept(this).f;

            assert(mgr.getFormulaType(array).isArrayType());

            cachedResults.put(e, new SmtExpr(amgr.store((ArrayFormula) array, index, val)));
        }

        return cachedResults.get(e);
    }

    // These cases will not called!
    @Override
    public SmtExpr visit(BoolExpr e)
    {
        assert false;

        return null;

    }

    @Override
    public SmtExpr visit(Expr e)
    {
        assert false;

        return null;

    }

    @Override
    public SmtExpr visit(FuncExpr e)
    {
        assert false;

        return null;

    }

    @Override
    public SmtExpr visit(IntExpr e)
    {
        assert false;

        return null;

    }

    @Override
    public SmtExpr visit(TermExpr e)
    {
        assert false;

        return null;

    }

    @Override
    public SmtExpr visit(ArrayExpr e)
    {
        assert false;

        return null;
    }
}

class JavaSmtUnmarshalVisitor implements FormulaVisitor<Expr> {
    FormulaManager mgr;
    Map<SmtExpr, Expr> cachedResults;
    ExprFactory factory;

    public JavaSmtUnmarshalVisitor(FormulaManager mgr, ExprFactory factory) {
        this(mgr, factory, null);
    }

    public JavaSmtUnmarshalVisitor(FormulaManager mgr, ExprFactory factory, Map<SmtExpr, Expr> results) {
        this.mgr = mgr;
        this.factory = factory;
        if (results != null)
            cachedResults = results;
        else
            cachedResults = new HashMap<>();
    }

    private ExprType getType(FormulaType t) {
        if(t.isBooleanType()) {
            return BooleanType.getInstance();
        }
        else if(t.isIntegerType()) {
            return IntegerType.getInstance();
        }
        else if(t.isArrayType()) {
            FormulaType.ArrayFormulaType arrayType = (FormulaType.ArrayFormulaType) t;

            return new edu.utexas.cs.utopia.cfpchecker.expression.type.ArrayType(getType(arrayType.getIndexType()), getType(arrayType.getElementType()));
        }

        assert false;

        return null;
    }

    public Expr visitFunction(Formula f, List<Formula> formulaArgs, FunctionDeclaration<?> decl) {
        SmtExpr key = new SmtExpr(f);

        if(!cachedResults.containsKey(key)) {
            Expr[] args = new Expr[formulaArgs.size()];
            for (int i = 0; i < formulaArgs.size(); i++) {
                args[i] = mgr.visit(formulaArgs.get(i), this);
            }

            Expr unmarshaled = null;

            FunctionDeclarationKind formulaKind = decl.getKind();

            if(formulaKind == FunctionDeclarationKind.OTHER) {
                String name = decl.getName();
                switch(name) {
                    case "<":
                        formulaKind = FunctionDeclarationKind.LT;
                        break;
                    case "<=":
                        formulaKind = FunctionDeclarationKind.LTE;
                        break;
                    case ">":
                        formulaKind = FunctionDeclarationKind.GT;
                        break;
                    case ">=":
                        formulaKind = FunctionDeclarationKind.GTE;
                        break;
                    case "+":
                        formulaKind = FunctionDeclarationKind.ADD;
                        break;
                    case "-":
                        formulaKind = args.length == 1 ? FunctionDeclarationKind.UMINUS : FunctionDeclarationKind.SUB;
                        break;
                    case "*":
                        formulaKind = FunctionDeclarationKind.MUL;
                        break;
                    case "div":
                        formulaKind = FunctionDeclarationKind.DIV;
                        break;
                    case "@diff":
                        formulaKind = FunctionDeclarationKind.OTHER;
                        break;
                    default:
                        System.out.println("We don't support " + name);
                        assert(false);
                }
            }

            switch (formulaKind) {
                case AND:
                    if (args.length == 0) {
                        unmarshaled = factory.mkTRUE();
                    } else if (args.length == 1) {
                        unmarshaled = args[0];
                    } else {
                        Expr[] subArgs = args.length > 2 ? Arrays.copyOfRange(args, 2, args.length) : null;
                        unmarshaled = factory.mkAND(args[0], args[1], subArgs);
                    }
                    break;
                case OR:
                    if (args.length == 1) {
                        unmarshaled = args[0];
                    } else {
                        Expr[] subArgs = args.length > 2 ? Arrays.copyOfRange(args, 2, args.length) : null;
                        unmarshaled = factory.mkOR(args[0], args[1], subArgs);
                    }
                    break;
                case NOT:
                    assert args.length == 1;
                    unmarshaled = factory.mkNEG(args[0]);
                    break;
                case IMPLIES:
                    assert args.length == 2;
                    unmarshaled = factory.mkIMPL(args[0], args[1]);
                    /*if(args[0] instanceof NegExpr) {
                        NegExpr e = (NegExpr) args[0];
                        unmarshaled = factory.mkOR(e.getArg(), args[1]);
                    }
                    else {
                        unmarshaled = factory.mkOR(factory.mkNEG(args[0]), args[1]);
                    }*/
                    break;
                case IFF:
                    assert args.length == 2;
                    unmarshaled = factory.mkAND(factory.mkIMPL(args[0], args[1]), factory.mkIMPL(args[1], args[0]));
                    break;
                case ITE:
                    assert args.length == 3;
                    assert args[1].getType().equals(BooleanType.getInstance());
                    assert args[2].getType().equals(BooleanType.getInstance());
                    unmarshaled = factory.mkOR(factory.mkAND(args[0], args[1]), factory.mkAND(factory.mkNEG(args[0]), args[2]));
                    break;
                case LT:
                    assert args.length == 2;
                    unmarshaled = factory.mkLT(args[0], args[1]);
                    break;
                case LTE:
                    assert args.length == 2;
                    unmarshaled = factory.mkLEQ(args[0], args[1]);
                    break;
                case GT:
                    assert args.length == 2;
                    unmarshaled = factory.mkGT(args[0], args[1]);
                    break;
                case GTE:
                    assert args.length == 2;
                    unmarshaled = factory.mkGEQ(args[0], args[1]);
                    break;
                case GTE_ZERO:
                    assert args.length == 1;
                    unmarshaled = factory.mkGEQ(args[0], factory.mkINT(new BigInteger("0")));
                    break;
                case EQ:
                    assert args.length == 2;
                    unmarshaled = factory.mkEQ(args[0], args[1]);
                    break;
                case EQ_ZERO:
                    assert args.length == 1;
                    unmarshaled = factory.mkEQ(args[0], factory.mkINT(new BigInteger("0")));
                    break;
                case ADD:
                    assert args.length >= 2;
                    unmarshaled = factory.mkPLUS(args[0], args[1], Arrays.copyOfRange(args, 2, args.length));
                    break;
                case SUB:
                    //assert args.length == 2;
                    if(args.length == 1) {
                        unmarshaled = factory.mkMULT(factory.mkINT(new BigInteger("-1")), args[0]);
                    }
                    else if(args.length == 2) {
                        unmarshaled = factory.mkMINUS(args[0], args[1]);
                    }
                    else {
                        assert false;
                    }
                    break;
                case MUL:
                    assert args.length == 2;
                    unmarshaled = factory.mkMULT(args[0], args[1]);
                    break;
                case DIV:
                    assert args.length == 2;
                    unmarshaled = factory.mkDIV(args[0], args[1]);
                    break;
                case MODULO:
                    assert args.length == 2;
                    unmarshaled = factory.mkMod(args[0], args[1]);
                    break;
                case DISTINCT:
                    assert args.length == 2;
                    unmarshaled = factory.mkNEG(factory.mkEQ(args[0], args[1]));
                    break;
                case UMINUS:
                    assert args.length == 1;
                    unmarshaled = factory.mkMULT(factory.mkINT(new BigInteger("-1")), args[0]);
                    break;
                case SELECT:
                    assert args.length == 2;
                    unmarshaled = factory.mkSelectExpr((FuncApp) args[0], args[1]);
                    break;
                case STORE:
                    assert args.length == 3;
                    unmarshaled = factory.mkStoreExpr((FuncApp) args[0], args[1], args[2]);
                    break;
                case UF:

                    if (args.length > 0) {
                        ExprType[] tys = new ExprType[args.length];
                        for (int i = 0; i < args.length; ++i)
                            tys[i] = args[i].getType();
                        FuncDecl exprDecl = factory.mkFDECL(decl.getName(), new FunctionType(tys, getType(mgr.getFormulaType(f))));//(FuncDecl) cachedResults.get(new SmtExpr(decl));
                        unmarshaled = factory.mkFAPP(exprDecl, Arrays.asList(args));
                    }
                    else {
                        if (!cachedResults.containsKey(key)) {
                            throw new IllegalStateException("Unable to translate z3Expr: " + f);
                        }
                        unmarshaled = cachedResults.get(key);
                    }
                    break;
                case OTHER:
                    assert decl.getName().equals("@diff");
                    assert args.length == 2 && args[0].getType().equals(args[1].getType());
                    ArrayType t = (ArrayType)args[0].getType();
                    String funcName = "@diff_" + (t.getDomain()[0]).toString().replace(' ', '_') + "_" + (t.getCoDomain()).toString().replace(' ', '_');
                    unmarshaled = factory.mkFAPP(factory.mkFDECL(funcName, new FunctionType(new ExprType[]{t, t}, IntegerType.getInstance())), Arrays.asList(args[0], args[1]));
                    break;
                default:
                    assert false;
            }

            cachedResults.put(key, unmarshaled);
        }

        return cachedResults.get(key);
    }

    public Expr visitBoundVariable(Formula f, int ind) {
        SmtExpr key = new SmtExpr(f);

        if(!cachedResults.containsKey(key)) {
            FunctionType t = new FunctionType(new ExprType[0], getType(mgr.getFormulaType(f)));
            Expr unmarshaled = factory.mkBoundVar(factory.mkFDECL(t.getCoDomain().toString() + "Func", t));
            cachedResults.put(key, unmarshaled);
        }

        return cachedResults.get(key);
    }

    public Expr visitFreeVariable(Formula f, String name) {
        SmtExpr key = new SmtExpr(f);

        if(!cachedResults.containsKey(key)) {
            FunctionType t = new FunctionType(new ExprType[0], getType(mgr.getFormulaType(f)));
            //Expr unmarshaled = factory.mkBoundVar(factory.mkFDECL(name, t));
            Expr unmarshaled = factory.mkFAPP(factory.mkFDECL(name, t));
            cachedResults.put(key, unmarshaled);
        }

        return cachedResults.get(key);
    }

    public Expr visitQuantifier(BooleanFormula f, QuantifiedFormulaManager.Quantifier q, List<Formula> boundVars, BooleanFormula body) {
        SmtExpr key = new SmtExpr(f);

        if(!cachedResults.containsKey(key)) {
            BoundVar[] vars = new BoundVar[boundVars.size()];
            Expr unmarshaled = null;

            for (int i = 0; i < boundVars.size(); i++) {
                vars[i] = (BoundVar) mgr.visit(boundVars.get(i), this);
            }

            if (q.equals(QuantifiedFormulaManager.Quantifier.EXISTS)) {
                unmarshaled = factory.mkEXIST(vars, mgr.visit(body, this), null);
            } else {
                unmarshaled = factory.mkFORALL(vars, mgr.visit(body, this), null);
            }

            cachedResults.put(key, unmarshaled);
        }

        return cachedResults.get(key);
    }

    public Expr visitConstant(Formula f, Object value) {
        SmtExpr key = new SmtExpr(f);

        if(!cachedResults.containsKey(key)) {
            Expr unmarshaled = null;

            if(mgr.getFormulaType(f).isBooleanType()) {
                unmarshaled = ((Boolean) value).booleanValue() ? factory.mkTRUE() : factory.mkFALSE();
            }
            else if(mgr.getFormulaType(f).isIntegerType()) {
                unmarshaled = factory.mkINT((BigInteger) value);
            }
            else {
                //don't know object types yet.
                assert false;
            }

            cachedResults.put(key, unmarshaled);
        }

        return cachedResults.get(key);
    }
}
