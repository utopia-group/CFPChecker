package edu.utexas.cs.utopia.cfpchecker.expression.bool;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.type.IntegerType;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.BoolExprRetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.BoolExprVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

/**
 * Created by kferles on 5/18/17.
 */
public class LessEqExpr extends BoolExpr
{
    private Expr left, right;

    public LessEqExpr(Expr left, Expr right)
    {
        IntegerType integerType = IntegerType.getInstance();

        if (left.getType() != integerType || right.getType() != integerType)
            throw new IllegalArgumentException("Non integer argument for operator <=");

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

        LessEqExpr that = (LessEqExpr) o;

        if (!left.equals(that.left)) return false;
        return right.equals(that.right);
    }

    @Override
    public int hashCode()
    {
        int result = left.hashCode();
        result = 255 * result + right.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        return left.toString() + " <= " + right.toString();
    }
}
