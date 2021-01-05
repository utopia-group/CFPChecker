package edu.utexas.cs.utopia.cfpchecker.expression.type;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.ExprTypeRetVisitor;

import java.util.List;

/**
 * Created by kferles on 5/18/17.
 */
public class UnitType implements ExprType
{
    static private UnitType INSTANCE = new UnitType();

    private UnitType()
    {

    }

    public static UnitType getInstance()
    {
        return INSTANCE;
    }

    @Override
    public <R> R accept(ExprTypeRetVisitor<R> v)
    {
        return v.visit(this);
    }

    @Override
    public String toString()
    {
        return "()";
    }

    @Override
    public int getArity()
    {
        return 0;
    }

    @Override
    public boolean isCompatibleWith(List<Expr> args)
    {
        return args.size() == 0;
    }
}
