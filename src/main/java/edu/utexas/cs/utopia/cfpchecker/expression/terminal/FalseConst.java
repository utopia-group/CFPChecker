package edu.utexas.cs.utopia.cfpchecker.expression.terminal;

import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.TermExprRetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.TermExprVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

/**
 * Created by kferles on 5/22/17.
 */
public class FalseConst extends ConstBool
{
    private static FalseConst INSTANCE = new FalseConst();

    private FalseConst()
    {

    }

    public static FalseConst getInstace()
    {
        return INSTANCE;
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
    public boolean isTrue()
    {
        return false;
    }

    @Override
    public boolean isFalse()
    {
        return true;
    }

    @Override
    public String toString()
    {
        return "FALSE";
    }
}
