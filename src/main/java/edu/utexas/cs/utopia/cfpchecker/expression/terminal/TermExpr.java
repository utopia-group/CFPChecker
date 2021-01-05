package edu.utexas.cs.utopia.cfpchecker.expression.terminal;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.type.ExprType;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.TermExprRetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.TermExprVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

/**
 * Created by kferles on 5/18/17.
 */
public abstract class TermExpr extends Expr
{
    public TermExpr(ExprType type)
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

    public abstract void accept(TermExprVisitor v);

    public abstract <R> R accept(TermExprRetVisitor<R> v);
}
