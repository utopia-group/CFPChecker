package edu.utexas.cs.utopia.cfpchecker.expression.array;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncApp;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncDecl;
import edu.utexas.cs.utopia.cfpchecker.expression.type.ArrayType;
import edu.utexas.cs.utopia.cfpchecker.expression.type.ExprType;
import edu.utexas.cs.utopia.cfpchecker.expression.type.FunctionType;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

import java.util.Arrays;

/**
 * Created by kferles on 9/18/18.
 */
public class SelectExpr extends ArrayExpr
{
    private FuncApp array;

    private Expr indexExpr;

    public SelectExpr(FuncApp array, Expr indexExpr)
    {
        // This constructor is a bit ugly. We need to clean this a bit!
        //super(((ArrayType) array.getDecl().getType().getCoDomain()).getCoDomain());
        super(new FuncDecl("sel_" + array.getDecl().getName(), new FunctionType(new ExprType[0], ((ArrayType) array.getDecl().getType().getCoDomain()).getCoDomain())));

        // Not the best solution, but it will do the trick for now.
        ExprType arrayTy = array.getDecl().getType().getCoDomain();
        if (!(arrayTy instanceof ArrayType))
            throw new IllegalArgumentException("Invalid argument, select expression can only be applied on arrays");

        if (!((ArrayType) arrayTy).getDomain()[0].equals(indexExpr.getType()))
            throw new IllegalArgumentException("Invalid index argument for array variable: " + array);

        this.array = array;
        this.indexExpr = indexExpr;
    }

    public FuncApp getArray()
    {
        return array;
    }

    public Expr getIndexExpr()
    {
        return indexExpr;
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

        SelectExpr that = (SelectExpr) o;

        if (!array.equals(that.array)) return false;
        return indexExpr.equals(that.indexExpr);
    }

    @Override
    public int hashCode()
    {
        int result = array.hashCode();
        result = 31 * result + indexExpr.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        return "(select " + array.toString() + " " + indexExpr.toString() + ")";
    }
}
