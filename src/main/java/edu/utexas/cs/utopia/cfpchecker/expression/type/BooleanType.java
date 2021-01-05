package edu.utexas.cs.utopia.cfpchecker.expression.type;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.ExprTypeRetVisitor;

import java.util.List;

/**
 * Created by kferles on 5/17/17.
 */
public class BooleanType implements ExprType
{

    private static BooleanType INSTANCE = new BooleanType();

    private BooleanType()
    {

    }

    public static BooleanType getInstance()
    {
        return INSTANCE;
    }

    public String toString()
    {
        return "Bool";
    }

    @Override
    public <R> R accept(ExprTypeRetVisitor<R> v)
    {
        return v.visit(this);
    }

    @Override
    public int getArity()
    {
        return 1;
    }

    @Override
    public boolean isCompatibleWith(List<Expr> args)
    {
        return !(args.size() != 1 || args.get(0).getType() != INSTANCE);
    }

}
