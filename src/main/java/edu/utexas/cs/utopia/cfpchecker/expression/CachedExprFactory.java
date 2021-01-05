package edu.utexas.cs.utopia.cfpchecker.expression;

import edu.utexas.cs.utopia.cfpchecker.expression.array.SelectExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.array.StoreExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.bool.*;
import edu.utexas.cs.utopia.cfpchecker.expression.factory.ExprFactory;
import edu.utexas.cs.utopia.cfpchecker.expression.function.BoundVar;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncApp;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncDecl;
import edu.utexas.cs.utopia.cfpchecker.expression.integer.*;
import edu.utexas.cs.utopia.cfpchecker.expression.terminal.ConstInteger;
import edu.utexas.cs.utopia.cfpchecker.expression.terminal.ConstString;
import edu.utexas.cs.utopia.cfpchecker.expression.terminal.FalseConst;
import edu.utexas.cs.utopia.cfpchecker.expression.terminal.TrueConst;
import edu.utexas.cs.utopia.cfpchecker.expression.type.FunctionType;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static edu.utexas.cs.utopia.cfpchecker.expression.ExprUtils.*;

/**
 * Created by kferles on 5/18/17.
 */
public class CachedExprFactory implements ExprFactory
{
    private final ConcurrentHashMap<Expr, Expr> cache = new ConcurrentHashMap<>();

    @Override
    public DivExpr mkDIV(Expr left, Expr right)
    {
        DivExpr divExpr = new DivExpr(left, right);
        cache.putIfAbsent(divExpr, divExpr);

        return (DivExpr) cache.get(divExpr);
    }

    @Override
    public RemExpr mkRem(Expr left, Expr right)
    {
        RemExpr remExpr = new RemExpr(left, right);
        cache.putIfAbsent(remExpr, remExpr);

        return (RemExpr) cache.get(remExpr);
    }

    @Override
    public ModExpr mkMod(Expr left, Expr right)
    {
        ModExpr modExpr = new ModExpr(left, right);
        cache.putIfAbsent(modExpr, modExpr);

        return (ModExpr) cache.get(modExpr);
    }

    @Override
    public MinusExpr mkMINUS(Expr e1, Expr e2, Expr... es)
    {
        MinusExpr minusExpr = new MinusExpr(e1, e2, es);
        cache.putIfAbsent(minusExpr, minusExpr);

        return (MinusExpr) cache.get(minusExpr);
    }

    @Override
    public MultExpr mkMULT(Expr e1, Expr e2, Expr... es)
    {
        MultExpr multExpr = new MultExpr(e1, e2, es);
        cache.putIfAbsent(multExpr, multExpr);

        return (MultExpr) cache.get(multExpr);
    }

    @Override
    public PlusExpr mkPLUS(Expr e1, Expr e2, Expr... es)
    {
        PlusExpr plusExpr = new PlusExpr(e1, e2, es);
        cache.putIfAbsent(plusExpr, plusExpr);

        return (PlusExpr) cache.get(plusExpr);
    }

    @Override
    public AndExpr mkAND(Expr e1, Expr e2, Expr... es)
    {
        AndExpr andExpr = new AndExpr(e1, e2, es);
        cache.putIfAbsent(andExpr, andExpr);

        return (AndExpr) cache.get(andExpr);
    }

    @Override
    public EqExpr mkEQ(Expr left, Expr right)
    {
        EqExpr eqExpr = new EqExpr(left, right);
        cache.putIfAbsent(eqExpr, eqExpr);

        return (EqExpr) cache.get(eqExpr);
    }

    private BoundVar[] boundVars(Expr[] exprToBound)
    {
        int boundLen = exprToBound.length;
        BoundVar[] boundVars = new BoundVar[boundLen];

        for (int i = 0; i < boundLen; ++i)
        {
            Expr e = exprToBound[i];

            if (!isVar(e))
                throw new IllegalArgumentException("Only variables can be bounded");

            boundVars[i] = mkBoundVar(getFunDecl(e));
        }
        return boundVars;
    }

    private void addBodyRefToBoundVars(BoundVar[] boundVars, Quantifier quant)
    {
        for (BoundVar bVar : boundVars)
            bVar.setQuantExpr(quant);
    }

    private Expr replaceBoundVars(Expr[] exprs, BoundVar[] boundVars, Expr body)
    {
        Map<Expr, Expr> replMap = new HashMap<>();

        for (int i = 0, end = exprs.length; i < end; ++i)
            replMap.put(exprs[i], boundVars[i]);

        return replace(this, body, replMap);
    }

    @Override
    public ExistentialQuantifier mkEXIST(BoundVar[] bVars, Expr body, Expr[] exprToBound)
    {
        ExistentialQuantifier existExpr = new ExistentialQuantifier(bVars, body);

        addBodyRefToBoundVars(bVars, existExpr);

        if (exprToBound != null)
            existExpr = (ExistentialQuantifier) replaceBoundVars(exprToBound, bVars, existExpr);

        cache.putIfAbsent(existExpr, existExpr);

        return (ExistentialQuantifier) cache.get(existExpr);
    }

    @Override
    public ExistentialQuantifier mkEXIST(Expr[] exprToBound, Expr body)
    {
        BoundVar[] boundVars = boundVars(exprToBound);

        return mkEXIST(boundVars, body, exprToBound);
    }

    @Override
    public UniversalQuantifier mkFORALL(BoundVar[] bVars, Expr body, Expr[] exprToBound)
    {
        UniversalQuantifier forallExpr = new UniversalQuantifier(bVars, body);

        if (exprToBound != null)
            forallExpr = (UniversalQuantifier) replaceBoundVars(exprToBound, bVars, forallExpr);

        addBodyRefToBoundVars(bVars, forallExpr);

        cache.putIfAbsent(forallExpr, forallExpr);

        return (UniversalQuantifier) cache.get(forallExpr);
    }

    @Override
    public UniversalQuantifier mkFORALL(Expr[] exprToBound, Expr body)
    {
        BoundVar[] boundVars = boundVars(exprToBound);

        return mkFORALL(boundVars, body, exprToBound);
    }

    @Override
    public GreaterEqExpr mkGEQ(Expr left, Expr right)
    {
        GreaterEqExpr greaterEqExpr = new GreaterEqExpr(left, right);
        cache.putIfAbsent(greaterEqExpr, greaterEqExpr);

        return (GreaterEqExpr) cache.get(greaterEqExpr);
    }

    @Override
    public GreaterExpr mkGT(Expr left, Expr right)
    {
        GreaterExpr greaterExpr = new GreaterExpr(left, right);
        cache.putIfAbsent(greaterExpr, greaterExpr);

        return (GreaterExpr) cache.get(greaterExpr);
    }

    @Override
    public ImplExpr mkIMPL(Expr left, Expr right)
    {
        ImplExpr implExpr = new ImplExpr(left, right);
        cache.putIfAbsent(implExpr, implExpr);

        return (ImplExpr) cache.get(implExpr);
    }

    @Override
    public LessEqExpr mkLEQ(Expr left, Expr right)
    {
        LessEqExpr lessEqExpr = new LessEqExpr(left, right);
        cache.putIfAbsent(lessEqExpr, lessEqExpr);

        return (LessEqExpr) cache.get(lessEqExpr);
    }

    @Override
    public LessExpr mkLT(Expr left, Expr right)
    {
        LessExpr lessExpr = new LessExpr(left, right);
        cache.putIfAbsent(lessExpr, lessExpr);

        return (LessExpr) cache.get(lessExpr);
    }

    @Override
    public NegExpr mkNEG(Expr arg)
    {
        NegExpr negExpr = new NegExpr(arg);
        cache.putIfAbsent(negExpr, negExpr);

        return (NegExpr) cache.get(negExpr);
    }

    @Override
    public OrExpr mkOR(Expr e1, Expr e2, Expr... es)
    {
        OrExpr orExpr = new OrExpr(e1, e2, es);
        cache.putIfAbsent(orExpr, orExpr);

        return (OrExpr) cache.get(orExpr);
    }

    @Override
    public ConstInteger mkINT(BigInteger val)
    {
        ConstInteger constInteger = new ConstInteger(val);
        cache.putIfAbsent(constInteger, constInteger);

        return (ConstInteger) cache.get(constInteger);
    }

    @Override
    public ConstString mkSTRING(String val)
    {
        ConstString constString = new ConstString(val);
        cache.putIfAbsent(constString, constString);

        return (ConstString) cache.get(constString);
    }

    @Override
    public FalseConst mkFALSE()
    {
        return FalseConst.getInstace();
    }

    @Override
    public TrueConst mkTRUE()
    {
        return TrueConst.getInstace();
    }

    @Override
    public FuncApp mkFAPP(FuncDecl decl, List<Expr> args)
    {
        FuncApp funcApp = new FuncApp(decl, args);
        cache.putIfAbsent(funcApp, funcApp);

        return (FuncApp) cache.get(funcApp);
    }

    @Override
    public BoundVar mkBoundVar(FuncDecl decl)
    {
        // We deliberately do not cache bound vars!
        return new BoundVar(decl);
    }

    @Override
    public FuncApp mkFAPP(FuncDecl decl)
    {
        FuncApp funcApp = new FuncApp(decl);
        cache.putIfAbsent(funcApp, funcApp);

        return (FuncApp) cache.get(funcApp);
    }

    @Override
    public FuncDecl mkFDECL(String name, FunctionType type)
    {
        FuncDecl funcDecl = new FuncDecl(name, type);
        cache.putIfAbsent(funcDecl, funcDecl);

        return (FuncDecl) cache.get(funcDecl);
    }

    @Override
    public SelectExpr mkSelectExpr(FuncApp array, Expr indexExpr)
    {
        SelectExpr select = new SelectExpr(array, indexExpr);
        cache.putIfAbsent(select, select);

        return (SelectExpr) cache.get(select);
    }

    @Override
    public StoreExpr mkStoreExpr(FuncApp array, Expr indexExpr, Expr newValueExpr)
    {
        StoreExpr store = new StoreExpr(array, indexExpr, newValueExpr);
        cache.putIfAbsent(store, store);

        return (StoreExpr) cache.get(store);
    }
}
