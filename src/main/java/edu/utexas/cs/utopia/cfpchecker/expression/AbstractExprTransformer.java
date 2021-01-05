package edu.utexas.cs.utopia.cfpchecker.expression;

import edu.utexas.cs.utopia.cfpchecker.expression.array.ArrayExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.array.SelectExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.array.StoreExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.bool.*;
import edu.utexas.cs.utopia.cfpchecker.expression.factory.ExprFactory;
import edu.utexas.cs.utopia.cfpchecker.expression.function.BoundVar;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncApp;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncDecl;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.integer.*;
import edu.utexas.cs.utopia.cfpchecker.expression.terminal.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by kferles on 5/19/17.
 */
public abstract class AbstractExprTransformer extends CachedExprRetVisitor<Expr>
{
    protected ExprFactory exprFactory;

    private Map<Expr, Expr> cachedResults = new HashMap<>();

    public AbstractExprTransformer(ExprFactory exprFactory)
    {
        this.exprFactory = exprFactory;
    }

    @Override
    public Expr visit(AndExpr e)
    {
        if (!alreadyVisited(e))
        {
            int argNum = e.argNum();

            assert argNum >= 2 : "Invalid And Expr Tree";

            Expr arg0 = e.argAt(0).accept(this);
            Expr arg1 = e.argAt(1).accept(this);

            Expr[] restArgs = argNum > 2 ? new Expr[argNum - 2] : null;

            for (int i = 2; i < argNum; ++i)
            {
                restArgs[i - 2] = e.argAt(i).accept(this);
            }

            Expr res = exprFactory.mkAND(arg0, arg1, restArgs);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(EqExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr left = e.getLeft().accept(this);
            Expr right = e.getRight().accept(this);

            Expr res = exprFactory.mkEQ(left, right);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(ExistentialQuantifier e)
    {
        if (!alreadyVisited(e))
        {
            int boundedVarNum = e.getBoundedVarNum();
            BoundVar[] newVars = new BoundVar[boundedVarNum];

            for (int i = 0; i < boundedVarNum; ++i)
            {
                newVars[i] = (BoundVar) e.boundedVarAt(i).accept(this);
            }

            Expr newBody = e.getBody().accept(this);
            Expr res = exprFactory.mkEXIST(newVars, newBody, null);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(UniversalQuantifier e)
    {
        if (!alreadyVisited(e))
        {
            int boundedVarNum = e.getBoundedVarNum();
            BoundVar[] newVars = new BoundVar[boundedVarNum];

            for (int i = 0; i < boundedVarNum; ++i)
            {
                newVars[i] = (BoundVar) e.boundedVarAt(i).accept(this);
            }

            Expr newBody = e.getBody().accept(this);
            Expr res = exprFactory.mkFORALL(newVars, newBody, null);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(GreaterEqExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr res = exprFactory.mkGEQ(e.getLeft().accept(this),
                                         e.getRight().accept(this));
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(GreaterExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr res = exprFactory.mkGT(e.getLeft().accept(this),
                                        e.getRight().accept(this));
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(BoundVar e)
    {
        if (!alreadyVisited(e))
        {
            Expr res = exprFactory.mkBoundVar((FuncDecl) e.getDecl().accept(this));
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(FuncApp e)
    {
        if (!alreadyVisited(e))
        {
            FuncDecl decl = (FuncDecl) e.getDecl().accept(this);

            List<Expr> newArgs = null;
            if (e.argNum() > 0)
            {
                newArgs = new ArrayList<>();
                for (Expr arg : e)
                {
                    newArgs.add(arg.accept(this));
                }
            }

            Expr res = newArgs == null ? exprFactory.mkFAPP(decl) :
                    exprFactory.mkFAPP(decl, newArgs);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(ConstInteger e)
    {
        if (!alreadyVisited(e))
        {
            cachedResults.put(e, visit((Expr) e));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(ConstString e)
    {
        if (!alreadyVisited(e))
        {
            cachedResults.put(e, visit((Expr) e));
        }
        return cachedResults.get(e);
    }

    @Override
    public Expr visit(FalseConst e)
    {
        if (!alreadyVisited(e))
        {
            cachedResults.put(e, visit((Expr) e));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(TrueConst e)
    {
        if (!alreadyVisited(e))
        {
            cachedResults.put(e, visit((Expr) e));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(ImplExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr premise = e.getPremise().accept(this);
            Expr conclusion = e.getConclusion().accept(this);

            Expr res = exprFactory.mkIMPL(premise, conclusion);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(FuncDecl e)
    {
        if (!alreadyVisited(e))
        {
            cachedResults.put(e, visit((Expr) e));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(LessEqExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr res = exprFactory.mkLEQ(e.getLeft().accept(this),
                                         e.getRight().accept(this));
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(LessExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr res = exprFactory.mkLT(e.getLeft().accept(this),
                                        e.getRight().accept(this));
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(NegExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr res = exprFactory.mkNEG(e.getArg().accept(this));
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(OrExpr e)
    {
        if (!alreadyVisited(e))
        {
            int argNum = e.argNum();

            assert argNum >= 2 : "Invalid Or Expr Tree";

            Expr arg0 = e.argAt(0).accept(this);
            Expr arg1 = e.argAt(1).accept(this);

            Expr[] restArgs = argNum > 2 ? new Expr[argNum - 2] : null;

            for (int i = 2; i < argNum; ++i)
            {
                restArgs[i - 2] = e.argAt(i).accept(this);
            }

            Expr res = exprFactory.mkOR(arg0, arg1, restArgs);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(DivExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr dividend = e.getDividend().accept(this);
            Expr divisor = e.getDivisor().accept(this);

            Expr res = exprFactory.mkDIV(dividend, divisor);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(RemExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr dividend = e.getDividend().accept(this);
            Expr divisor = e.getDivisor().accept(this);

            Expr res = exprFactory.mkRem(dividend, divisor);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(ModExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr dividend = e.getDividend().accept(this);
            Expr divisor = e.getDivisor().accept(this);

            Expr res = exprFactory.mkMod(dividend, divisor);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(MinusExpr e)
    {
        if (!alreadyVisited(e))
        {
            int argNum = e.argNum();

            assert argNum >= 2 : "Invalid Minus Expr Tree";

            Expr arg0 = e.argAt(0).accept(this);
            Expr arg1 = e.argAt(1).accept(this);

            Expr[] restArgs = argNum > 2 ? new Expr[argNum - 2] : null;

            for (int i = 2; i < argNum; ++i)
            {
                restArgs[i - 2] = e.argAt(i).accept(this);
            }

            Expr res = exprFactory.mkMINUS(arg0, arg1, restArgs);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(MultExpr e)
    {
        if (!alreadyVisited(e))
        {
            int argNum = e.argNum();

            assert argNum >= 2 : "Invalid Mult Expr Tree";

            Expr arg0 = e.argAt(0).accept(this);
            Expr arg1 = e.argAt(1).accept(this);

            Expr[] restArgs = argNum > 2 ? new Expr[argNum - 2] : null;

            for (int i = 2; i < argNum; ++i)
            {
                restArgs[i - 2] = e.argAt(i).accept(this);
            }

            Expr res = exprFactory.mkMULT(arg0, arg1, restArgs);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(PlusExpr e)
    {
        if (!alreadyVisited(e))
        {
            int argNum = e.argNum();

            assert argNum >= 2 : "Invalid Plus Expr Tree";

            Expr arg0 = e.argAt(0).accept(this);
            Expr arg1 = e.argAt(1).accept(this);

            Expr[] restArgs = argNum > 2 ? new Expr[argNum - 2] : null;

            for (int i = 2; i < argNum; ++i)
            {
                restArgs[i - 2] = e.argAt(i).accept(this);
            }

            Expr res = exprFactory.mkPLUS(arg0, arg1, restArgs);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(SelectExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr array = e.getArray().accept(this);
            Expr indexExpr = e.getIndexExpr().accept(this);

            Expr res = exprFactory.mkSelectExpr((FuncApp) array, indexExpr);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(StoreExpr e)
    {
        if (!alreadyVisited(e))
        {
            Expr array = e.getArray().accept(this);
            Expr indexExpr = e.getIndexExpr().accept(this);
            Expr newValueExpr = e.getNewValue().accept(this);

            Expr res = exprFactory.mkStoreExpr((FuncApp) array, indexExpr, newValueExpr);
            cachedResults.put(e, visit(res));
        }

        return cachedResults.get(e);
    }

    @Override
    public Expr visit(Expr e)
    {
        return e;
    }

    // Nothing to do for abstract classes.


    @Override
    public Expr visit(BoolExpr e)
    {
        return null;
    }

    @Override
    public Expr visit(FuncExpr e)
    {
        return null;
    }

    @Override
    public Expr visit(IntExpr e)
    {
        return null;
    }

    @Override
    public Expr visit(TermExpr e)
    {
        return null;
    }

    @Override
    public Expr visit(ArrayExpr e)
    {
        return null;
    }

}
