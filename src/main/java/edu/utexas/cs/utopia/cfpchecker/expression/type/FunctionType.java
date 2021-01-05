package edu.utexas.cs.utopia.cfpchecker.expression.type;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.ExprTypeRetVisitor;

import java.util.Arrays;
import java.util.List;

/**
 * Created by kferles on 5/17/17.
 */
public class FunctionType implements ExprType
{
    private ExprType[] domain;

    private ExprType coDomain;

    public FunctionType(ExprType[] domain, ExprType coDomain)
    {
        this.domain = domain;
        this.coDomain = coDomain;
    }

    public ExprType[] getDomain()
    {
        return domain;
    }

    public ExprType getCoDomain()
    {
        return coDomain;
    }

    @Override
    public String toString()
    {
        StringBuilder rv = new StringBuilder("(");
        for (int i = 0; i < domain.length; i++)
        {
            ExprType ty = domain[i];
            rv.append(ty.toString()).append(i == domain.length - 1 ? "" : " x ");
        }

        rv.append(") -> ").append(coDomain.toString());
        return rv.toString();
    }

    @Override
    public <R> R accept(ExprTypeRetVisitor<R> v)
    {
        return v.visit(this);
    }

    @Override
    public int getArity()
    {
        return domain.length;
    }

    @Override
    public boolean isCompatibleWith(List<Expr> args)
    {
        if (args.size() != this.getArity())
            return false;

        for (int i = 0; i < domain.length; i++)
        {
            ExprType exprType = domain[i];
            if (!exprType.equals(args.get(i).getType()))
                return false;
        }

        return true;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FunctionType that = (FunctionType) o;

        if (!Arrays.equals(domain, that.domain)) return false;
        return coDomain.equals(that.coDomain);
    }

    @Override
    public int hashCode()
    {
        int result = Arrays.hashCode(domain);
        result = 31 * result + coDomain.hashCode();
        return result;
    }
}
