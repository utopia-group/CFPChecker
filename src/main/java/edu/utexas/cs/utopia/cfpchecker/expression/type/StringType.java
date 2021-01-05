package edu.utexas.cs.utopia.cfpchecker.expression.type;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.ExprTypeRetVisitor;

import java.util.List;

public class StringType implements ExprType
{
    private static StringType INSTANCE = new StringType();

    private StringType()
    {

    }

    public static StringType getInstance()
    {
        return INSTANCE;
    }

    @Override
    public int getArity()
    {
        return 1;
    }

    @Override
    public boolean isCompatibleWith(List<Expr> args) {
        return !(args.size() != 1 || args.get(0).getType() != INSTANCE);
    }

    @Override
    public <R> R accept(ExprTypeRetVisitor<R> v) {
        return v.visit(this);
    }
}
