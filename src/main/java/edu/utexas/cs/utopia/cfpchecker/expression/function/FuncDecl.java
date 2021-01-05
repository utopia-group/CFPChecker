package edu.utexas.cs.utopia.cfpchecker.expression.function;

import edu.utexas.cs.utopia.cfpchecker.expression.type.FunctionType;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.FuncExprRetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.FuncExprVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

/**
 * Created by kferles on 5/18/17.
 */
public class FuncDecl extends FuncExpr
{
    private String name;

    private FunctionType type;

    public FuncDecl(String name, FunctionType type)
    {
        super(type);
        this.name = name;
        this.type = type;
    }

    public String getName()
    {
        return name;
    }

    public FunctionType getType()
    {
        return type;
    }

    @Override
    public void accept(FuncExprVisitor v)
    {
        v.visit(this);
    }

    @Override
    public <R> R accept(FuncExprRetVisitor<R> v)
    {
        return v.visit(this);
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

        FuncDecl that = (FuncDecl) o;

        if (!name.equals(that.name)) return false;
        return type.equals(that.type);
    }

    @Override
    public int hashCode()
    {
        int result = name.hashCode();
        result = 31 * result + type.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        return name + " : " + type.toString();
    }
}
