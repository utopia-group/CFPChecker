package edu.utexas.cs.utopia.cfpchecker.expression.function;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.type.ExprType;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.FuncExprRetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.FuncExprVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

/**
 * Created by kferles on 5/19/17.
 */
public abstract class FuncExpr extends Expr
{
    public FuncExpr(ExprType type)
    {
        super(type);
    }

    @Override
    public void accept(Visitor v)
    {
        v.visit(this);
    }

    @Override
    public <R> R accept(RetVisitor<R> v)
    {
        return v.visit(this);
    }

    public abstract void accept(FuncExprVisitor v);

    public abstract <R> R accept(FuncExprRetVisitor<R> v);
}
