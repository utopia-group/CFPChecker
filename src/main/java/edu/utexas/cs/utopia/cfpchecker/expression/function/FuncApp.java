package edu.utexas.cs.utopia.cfpchecker.expression.function;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

import java.util.Iterator;
import java.util.List;

/**
 * Created by kferles on 5/18/17.
 */
public class FuncApp extends Expr implements Iterable<Expr>
{
    private FuncDecl decl;

    private List<Expr> args;

    public FuncApp(FuncDecl decl, List<Expr> args)
    {
        super(decl.getType().getCoDomain());

        if (!decl.getType().isCompatibleWith(args))
            throw new IllegalArgumentException("Arguments do not match the function type");

        this.decl = decl;
        this.args = args;
    }

    public FuncApp(FuncDecl decl)
    {
        super(decl.getType().getCoDomain());

        if (decl.getType().getArity() != 0)
            throw new IllegalArgumentException("Arguments do not match the function type");

        this.decl = decl;
    }

    public FuncDecl getDecl()
    {
        return decl;
    }

    public Iterator<Expr> iterator()
    {
        return args.iterator();
    }

    public int argNum()
    {
        return args != null ? args.size() : 0;
    }

    public Expr argAt(int i)
    {
        return args != null ? args.get(i) : null;
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

        FuncApp exprs = (FuncApp) o;

        if (!decl.equals(exprs.decl)) return false;
        return args != null ? args.equals(exprs.args) : exprs.args == null;
    }

    @Override
    public int hashCode()
    {
        int result = decl.hashCode();
        result = 31 * result + (args != null ? args.hashCode() : 0);
        return result;
    }

    @Override
    public String toString()
    {
        StringBuilder rv = new StringBuilder(decl.getName());

        if (args != null)
        {
            rv.append("(").append(args.get(0));

            for (int i = 1, size = args.size(); i < size; ++i)
                rv.append(", ").append(args.get(i));
        }

        return args != null ? rv.append(")").toString() : rv.toString();
    }
}
