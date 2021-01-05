package edu.utexas.cs.utopia.cfpchecker.expression.integer;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.type.IntegerType;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.IntExprRetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.IntExprVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

/**
 * Created by kferles on 5/18/17.
 */
public abstract class IntExpr extends Expr
{
    public IntExpr()
    {
        super(IntegerType.getInstance());
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

    public abstract void accept(IntExprVisitor v);

    public abstract <R> R accept(IntExprRetVisitor<R> v);
}
