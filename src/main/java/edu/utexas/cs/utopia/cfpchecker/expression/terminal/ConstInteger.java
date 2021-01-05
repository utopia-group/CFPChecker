package edu.utexas.cs.utopia.cfpchecker.expression.terminal;

import edu.utexas.cs.utopia.cfpchecker.expression.type.IntegerType;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.TermExprRetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.TermExprVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

import java.math.BigInteger;

/**
 * Created by kferles on 5/18/17.
 */
public class ConstInteger extends TermExpr
{
    private BigInteger val;

    public ConstInteger(BigInteger val)
    {
        super(IntegerType.getInstance());
        this.val = val;
    }

    public BigInteger getVal()
    {
        return val;
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
    public void accept(TermExprVisitor v)
    {
        v.visit(this);
    }

    @Override
    public <R> R accept(TermExprRetVisitor<R> v)
    {
        return v.visit(this);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConstInteger that = (ConstInteger) o;

        return val.equals(that.val);
    }

    @Override
    public int hashCode()
    {
        return val.hashCode();
    }

    @Override
    public String toString()
    {
        return val.toString();
    }
}
