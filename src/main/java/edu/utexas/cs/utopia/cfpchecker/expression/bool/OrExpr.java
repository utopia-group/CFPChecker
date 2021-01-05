package edu.utexas.cs.utopia.cfpchecker.expression.bool;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.type.BooleanType;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.BoolExprRetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.BoolExprVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Created by kferles on 5/17/17.
 */
public class OrExpr extends BoolExpr implements Iterable<Expr>
{
    private List<Expr> args = new ArrayList<>();

    public OrExpr(Expr e1, Expr e2, Expr... es)
    {
        BooleanType booleanType = BooleanType.getInstance();
        if (e1.getType() != booleanType || e2.getType() != booleanType)
            throw new IllegalArgumentException("Non boolean argument for operator ||");

        if (es != null)
        {
            for (Expr e : es)
            {
                if (e.getType() != booleanType)
                    throw new IllegalArgumentException("Non boolean argument for operator ||");
            }
        }

        Collections.addAll(args, e1, e2);
        if (es != null) Collections.addAll(args, es);
    }

    public Iterator<Expr> iterator()
    {
        return args.iterator();
    }

    public int argNum()
    {
        return args.size();
    }

    public Expr argAt(int index)
    {
        return args.get(index);
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

        OrExpr orExpr = (OrExpr) o;

        return args.equals(orExpr.args);
    }

    @Override
    public int hashCode()
    {
        return 63 * args.hashCode();
    }

    @Override
    public String toString()
    {
        StringBuilder s = new StringBuilder("( || ");

        for (Expr e : args)
            s.append("(").append(e.toString()).append(") ");

        return s.append(")").toString();
    }
}
