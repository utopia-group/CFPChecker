package edu.utexas.cs.utopia.cfpchecker.expression.integer;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.type.IntegerType;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.IntExprRetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.IntExprVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Created by kferles on 5/18/17.
 */
public class PlusExpr extends IntExpr implements Iterable<Expr>
{
    private List<Expr> args = new ArrayList<>();

    public PlusExpr(Expr e1, Expr e2, Expr... es)
    {
        IntegerType integerType = IntegerType.getInstance();
        if (e1.getType() != integerType || e2.getType() != integerType)
            throw new IllegalArgumentException("Non integer argument for operator +");

        if (es != null)
        {
            for (Expr e : es)
            {
                if (e.getType() != integerType)
                    throw new IllegalArgumentException("Non integer argument for operator +");
            }
        }

        Collections.addAll(args, e1, e2);
        if (es != null) Collections.addAll(args, es);
    }

    @Override
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
    public void accept(IntExprVisitor v)
    {
        v.visit(this);
    }

    @Override
    public <R> R accept(IntExprRetVisitor<R> v)
    {
        return v.visit(this);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PlusExpr plusExpr = (PlusExpr) o;

        return args.equals(plusExpr.args);
    }

    @Override
    public int hashCode()
    {
        return 1023 * args.hashCode();
    }

    @Override
    public String toString()
    {
        StringBuilder rv = new StringBuilder("( + ");

        for (Expr expr : args)
            rv.append("(").append(expr.toString()).append(") ");

        return rv.append(")").toString();
    }
}
