package edu.utexas.cs.utopia.cfpchecker.expression;

import edu.utexas.cs.utopia.cfpchecker.expression.array.ArrayExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.array.SelectExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.array.StoreExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.bool.*;
import edu.utexas.cs.utopia.cfpchecker.expression.function.BoundVar;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncApp;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncDecl;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.integer.*;
import edu.utexas.cs.utopia.cfpchecker.expression.terminal.*;

/**
 * Created by kferles on 5/19/17.
 */
public abstract class ExprPostOrderRetVisitor<R> extends CachedExprRetVisitor<R>
{
    @Override
    public R visit(AndExpr e)
    {
        if (alreadyVisited(e))
            return null;

        for (Expr arg : e)
            arg.accept(this);

        return visit((Expr) e);
    }

    @Override
    public R visit(EqExpr e)
    {
        if (alreadyVisited(e))
            return null;

        e.getLeft().accept(this);
        e.getRight().accept(this);

        return visit((Expr) e);
    }

    private R visit(Quantifier e)
    {
        if (alreadyVisited(e))
            return null;

        for (int i = 0, end = e.getBoundedVarNum(); i < end; ++i)
            e.boundedVarAt(i).accept(this);

        e.getBody().accept(this);

        return visit((Expr) e);
    }

    @Override
    public R visit(ExistentialQuantifier e)
    {
        return visit((Quantifier) e);
    }

    @Override
    public R visit(UniversalQuantifier e)
    {
        return visit((Quantifier) e);
    }

    @Override
    public R visit(GreaterEqExpr e)
    {
        if (alreadyVisited(e))
            return null;

        e.getLeft().accept(this);
        e.getRight().accept(this);

        return visit((Expr) e);
    }

    @Override
    public R visit(GreaterExpr e)
    {
        if (alreadyVisited(e))
            return null;

        e.getLeft().accept(this);
        e.getRight().accept(this);

        return visit((Expr) e);
    }

    @Override
    public R visit(BoundVar e)
    {
        if (alreadyVisited(e))
            return null;

        e.getDecl().accept(this);

        return visit((Expr) e);
    }

    @Override
    public R visit(FuncApp e)
    {
        if (alreadyVisited(e))
            return null;

        e.getDecl().accept(this);

        if (e.argNum() > 0)
            for (Expr arg : e)
                arg.accept(this);

        return visit((Expr) e);
    }

    @Override
    public R visit(ConstInteger e)
    {
        if (alreadyVisited(e))
            return null;

        return visit((Expr) e);
    }

    @Override
    public R visit(ConstString e)
    {
        if (alreadyVisited(e))
            return null;

        return visit((Expr) e);
    }

    @Override
    public R visit(FalseConst e)
    {
        if (alreadyVisited(e))
            return null;

        return visit((Expr) e);
    }

    @Override
    public R visit(TrueConst e)
    {
        if (alreadyVisited(e))
            return null;

        return visit((Expr) e);
    }

    @Override
    public R visit(ImplExpr e)
    {
        if (alreadyVisited(e))
            return null;

        e.getPremise().accept(this);
        e.getConclusion().accept(this);

        return visit((Expr) e);
    }

    @Override
    public R visit(FuncDecl e)
    {
        if (alreadyVisited(e))
            return null;

        return visit((Expr) e);
    }

    @Override
    public R visit(LessEqExpr e)
    {
        if (alreadyVisited(e))
            return null;

        e.getLeft().accept(this);
        e.getRight().accept(this);

        return visit((Expr) e);
    }

    @Override
    public R visit(LessExpr e)
    {
        if (alreadyVisited(e))
            return null;

        e.getLeft().accept(this);
        e.getRight().accept(this);

        return visit((Expr) e);
    }

    @Override
    public R visit(NegExpr e)
    {
        if (alreadyVisited(e))
            return null;

        e.getArg().accept(this);

        return visit((Expr) e);
    }

    @Override
    public R visit(OrExpr e)
    {
        if (alreadyVisited(e))
            return null;

        for (Expr arg : e)
            arg.accept(this);

        return visit((Expr) e);
    }

    @Override
    public R visit(DivExpr e)
    {
        if (alreadyVisited(e))
            return null;

        e.getDividend().accept(this);
        e.getDivisor().accept(this);

        return visit((Expr) e);
    }

    @Override
    public R visit(RemExpr e)
    {
        if (alreadyVisited(e))
            return null;

        e.getDividend().accept(this);
        e.getDivisor().accept(this);

        return visit((Expr) e);
    }

    @Override
    public R visit(ModExpr e)
    {
        if (alreadyVisited(e))
            return null;

        e.getDividend().accept(this);
        e.getDivisor().accept(this);

        return visit((Expr) e);
    }

    @Override
    public R visit(MinusExpr e)
    {
        if (alreadyVisited(e))
            return null;

        for (Expr arg : e)
            arg.accept(this);

        return visit((Expr) e);
    }

    @Override
    public R visit(MultExpr e)
    {
        if (alreadyVisited(e))
            return null;

        for (Expr arg : e)
            arg.accept(this);

        return visit((Expr) e);
    }

    @Override
    public R visit(PlusExpr e)
    {
        if (alreadyVisited(e))
            return null;

        for (Expr arg : e)
            arg.accept(this);

        return visit((Expr) e);
    }

    @Override
    public R visit(SelectExpr e)
    {
        if (alreadyVisited(e))
            return null;

        e.getArray().accept(this);
        e.getIndexExpr().accept(this);

        return visit((Expr) e);
    }

    @Override
    public R visit(StoreExpr e)
    {
        if (alreadyVisited(e))
            return null;

        e.getArray().accept(this);
        e.getIndexExpr().accept(this);
        e.getNewValue().accept(this);

        return visit((Expr) e);
    }

    // Nothing to do for the abstract classes

    @Override
    public R visit(BoolExpr e)
    {
        return null;
    }

    @Override
    public R visit(FuncExpr e)
    {
        return null;
    }

    @Override
    public R visit(IntExpr e)
    {
        return null;
    }

    @Override
    public R visit(TermExpr e)
    {
        return null;
    }

    @Override
    public R visit(ArrayExpr e)
    {
        return null;
    }
}
