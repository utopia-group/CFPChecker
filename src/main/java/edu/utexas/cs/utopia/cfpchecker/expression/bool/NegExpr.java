package edu.utexas.cs.utopia.cfpchecker.expression.bool;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.type.BooleanType;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.BoolExprRetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.BoolExprVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

/**
 * Created by kferles on 5/17/17.
 */
public class NegExpr extends BoolExpr
{
    private Expr arg;

    public NegExpr(Expr arg)
    {
        if (arg.getType() != BooleanType.getInstance())
            throw new IllegalArgumentException("Non boolean argument for operator !");

        this.arg = arg;
    }

    public Expr getArg()
    {
        return this.arg;
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
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NegExpr negExpr = (NegExpr) o;

        return arg.equals(negExpr.arg);
    }

    @Override
    public int hashCode()
    {
        return 33 * arg.hashCode();
    }

    @Override
    public String toString()
    {
        return "!(" + arg.toString() + ")";
    }
}
