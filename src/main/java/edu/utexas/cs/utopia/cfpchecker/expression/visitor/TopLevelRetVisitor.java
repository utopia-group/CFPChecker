package edu.utexas.cs.utopia.cfpchecker.expression.visitor;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
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
 * Created by kferles on 7/26/17.
 */
public abstract class TopLevelRetVisitor<R> implements RetVisitor<R>
{
    @Override
    public R visit(AndExpr e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(EqExpr e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(ExistentialQuantifier e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(UniversalQuantifier e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(GreaterEqExpr e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(BoundVar e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(FuncApp e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(GreaterExpr e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(FuncDecl e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(ImplExpr e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(LessEqExpr e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(LessExpr e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(NegExpr e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(DivExpr e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(RemExpr e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(ModExpr e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(OrExpr e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(ConstInteger e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(ConstString e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(MinusExpr e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(FalseConst e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(MultExpr e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(PlusExpr e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(TrueConst e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(SelectExpr e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(StoreExpr e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(ArrayExpr e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(BoolExpr e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(FuncExpr e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(IntExpr e)
    {
        return visit((Expr) e);
    }

    @Override
    public R visit(TermExpr e)
    {
        return visit((Expr) e);
    }
}
