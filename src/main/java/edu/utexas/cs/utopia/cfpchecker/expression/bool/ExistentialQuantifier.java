package edu.utexas.cs.utopia.cfpchecker.expression.bool;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.function.BoundVar;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.BoolExprRetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.BoolExprVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

/**
 * Created by kferles on 7/26/17.
 */
public class ExistentialQuantifier extends Quantifier
{
    public ExistentialQuantifier(BoundVar[] boundVars, Expr body)
    {
        super(boundVars, body);
    }

    @Override
    public void accept(BoolExprVisitor v)
    {
        v.visit(this);
    }

    @Override
    public <R> R accept(BoolExprRetVisitor<R> v)
    {
        return v.visit(this);
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

    @Override
    public String toString()
    {
        StringBuilder rv = new StringBuilder("(exist ");

        for (int i = 0, e = getBoundedVarNum(); i < e; ++i)
        {
            if (i > 0)
                rv.append(", ");
            rv.append(boundedVarAt(i).toString());
        }

        rv.append(": ").append(body.toString()).append(")");
        return rv.toString();
    }
}
