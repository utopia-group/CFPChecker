package edu.utexas.cs.utopia.cfpchecker.expression;

import edu.utexas.cs.utopia.cfpchecker.expression.type.ExprType;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

/**
 * Created by kferles on 5/17/17.
 */
public abstract class Expr
{
    ExprType type;

    public Expr(ExprType type)
    {
        this.type = type;
    }

    public ExprType getType()
    {
        return this.type;
    }

    public abstract void accept(Visitor v);

    public abstract <R> R accept(RetVisitor<R> v);
}
