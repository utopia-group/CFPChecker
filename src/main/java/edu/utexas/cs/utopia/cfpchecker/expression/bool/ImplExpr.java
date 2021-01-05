package edu.utexas.cs.utopia.cfpchecker.expression.bool;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.type.BooleanType;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.BoolExprRetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.BoolExprVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

/**
 * Created by kferles on 5/17/17.
 */
public class ImplExpr extends BoolExpr
{
    private Expr premise;

    private Expr conclusion;

    public ImplExpr(Expr premise, Expr conclusion)
    {
        BooleanType booleanType = BooleanType.getInstance();

        if (premise.getType() != booleanType || conclusion.getType() != booleanType)
            throw new IllegalArgumentException("Non boolean argument for operator ->");

        this.premise = premise;
        this.conclusion = conclusion;
    }

    public Expr getPremise()
    {
        return premise;
    }

    public Expr getConclusion()
    {
        return conclusion;
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
    public void accept(BoolExprVisitor v)
    {
        v.visit(this);
    }

    @Override
    public <R> R accept(BoolExprRetVisitor<R> v)
    {
        return v.visit(this);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ImplExpr implExpr = (ImplExpr) o;

        if (!premise.equals(implExpr.premise)) return false;
        return conclusion.equals(implExpr.conclusion);
    }

    @Override
    public int hashCode()
    {
        int result = premise.hashCode();
        result = 127 * result + conclusion.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        return premise.toString() + " -> " + conclusion.toString();
    }
}
