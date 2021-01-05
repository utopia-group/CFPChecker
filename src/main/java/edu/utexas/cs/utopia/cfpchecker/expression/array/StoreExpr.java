package edu.utexas.cs.utopia.cfpchecker.expression.array;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncApp;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

/**
 * Created by kferles on 9/18/18.
 */
public class StoreExpr extends ArrayExpr
{
    private FuncApp array;

    private Expr indexExpr;

    private Expr newValue;

    public StoreExpr(FuncApp array, Expr indexExpr, Expr newValue)
    {
        super(array.getDecl());
        this.array = array;
        this.indexExpr = indexExpr;
        this.newValue = newValue;
    }

    public FuncApp getArray()
    {
        return array;
    }

    public Expr getIndexExpr()
    {
        return indexExpr;
    }

    public Expr getNewValue()
    {
        return newValue;
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
    public String toString()
    {
        return "(store " + array.toString() + " " + indexExpr.toString() + " " + newValue.toString() + ")";
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StoreExpr storeExpr = (StoreExpr) o;

        if (!array.equals(storeExpr.array)) return false;
        if (!indexExpr.equals(storeExpr.indexExpr)) return false;
        return newValue.equals(storeExpr.newValue);
    }

    @Override
    public int hashCode()
    {
        int result = array.hashCode();
        result = 31 * result + indexExpr.hashCode();
        result = 31 * result + newValue.hashCode();
        return result;
    }
}
