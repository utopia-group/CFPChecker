package edu.utexas.cs.utopia.cfpchecker.expression.terminal;

import edu.utexas.cs.utopia.cfpchecker.expression.type.StringType;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.TermExprRetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.TermExprVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

import java.util.Objects;

public class ConstString extends TermExpr
{
    private String val;

    public ConstString(String val) {
        super(StringType.getInstance());
        this.val = val;
    }

    public String getVal()
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConstString that = (ConstString) o;
        return val.equals(that.val);
    }

    @Override
    public int hashCode() {
        return Objects.hash(val);
    }

    @Override
    public String toString()
    {
        return val;
    }
}
