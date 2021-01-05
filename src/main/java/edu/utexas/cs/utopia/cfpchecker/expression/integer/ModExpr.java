package edu.utexas.cs.utopia.cfpchecker.expression.integer;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.type.IntegerType;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.IntExprRetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.IntExprVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

/**
 * Created by kferles on 10/29/18.
 */
public class ModExpr extends IntExpr
{
    private Expr dividend, divisor;

    public ModExpr(Expr dividend, Expr divisor)
    {
        IntegerType integerType = IntegerType.getInstance();

        if (dividend.getType() != integerType || divisor.getType() != integerType)
            throw new IllegalArgumentException("Non integer argument for operator %");

        this.dividend = dividend;
        this.divisor = divisor;
    }

    public Expr getDividend()
    {
        return dividend;
    }

    public Expr getDivisor()
    {
        return divisor;
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
    public void accept(IntExprVisitor v)
    {
        v.visit(this);
    }

    @Override
    public <R> R accept(IntExprRetVisitor<R> v)
    {
        return v.visit(this);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ModExpr modExpr = (ModExpr) o;

        if (!dividend.equals(modExpr.dividend)) return false;
        return divisor.equals(modExpr.divisor);
    }

    @Override
    public int hashCode()
    {
        int result = dividend.hashCode();
        result = 31 * result + divisor.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        return "(mod " + dividend.toString() + " " + divisor.toString() + ")";
    }
}
