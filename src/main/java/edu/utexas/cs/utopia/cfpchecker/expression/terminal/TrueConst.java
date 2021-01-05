package edu.utexas.cs.utopia.cfpchecker.expression.terminal;

import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.TermExprRetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.TermExprVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

/**
 * Created by kferles on 5/22/17.
 */
public class TrueConst extends ConstBool
{
    private static TrueConst INSTANCE = new TrueConst();

    private TrueConst()
    {

    }

    public static TrueConst getInstace()
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
        return true;
    }

    @Override
    public boolean isFalse()
    {
        return false;
    }

    @Override
    public String toString()
    {
        return "TRUE";
    }
}
