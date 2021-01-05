package edu.utexas.cs.utopia.cfpchecker.expression.bool;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.BoolExprRetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.BoolExprVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

/**
 * Created by kferles on 5/22/17.
 */
public class EqExpr extends BoolExpr
{
    private Expr left, right;

    public EqExpr(Expr left, Expr right)
    {
        // TODO: fix type comparison and creation
        if (!left.getType().equals(right.getType()))
            throw new IllegalArgumentException("Operand types do not match for operator =");

        this.left = left;
        this.right = right;
    }

    public Expr getLeft()
    {
        return left;
    }

    public Expr getRight()
    {
        return right;
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

        EqExpr eqExpr = (EqExpr) o;

        return (left.equals(eqExpr.left) && right.equals(eqExpr.right)) ||
               (left.equals(eqExpr.right) && right.equals(eqExpr.left));
    }

    @Override
    public int hashCode()
    {
        int result = left.hashCode();
        result = 2047 * result + right.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        return left.toString() + " = " + right.toString();
    }
}
