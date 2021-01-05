package edu.utexas.cs.utopia.cfpchecker.expression.function;

import edu.utexas.cs.utopia.cfpchecker.expression.bool.Quantifier;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

/**
 * Created by kferles on 7/26/17.
 */
public class BoundVar extends FuncApp
{
    private Quantifier quantExpr;

    public BoundVar(FuncDecl decl)
    {
        super(decl);
    }

    public void setQuantExpr(Quantifier quantExpr)
    {
        this.quantExpr = quantExpr;
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
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        BoundVar exprs = (BoundVar) o;

        return quantExpr != null ? quantExpr.equals(exprs.quantExpr) : exprs.quantExpr == null;
    }

    @Override
    public int hashCode()
    {
        int result = super.hashCode();
        result = 31 * result + (quantExpr != null ? quantExpr.hashCode() : 0);
        return result;
    }
}
