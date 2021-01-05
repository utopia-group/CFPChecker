package edu.utexas.cs.utopia.cfpchecker.expression.array;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncApp;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncDecl;
import edu.utexas.cs.utopia.cfpchecker.expression.type.ExprType;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

import java.util.List;

/**
 * Created by kferles on 9/17/18.
 */
public abstract class ArrayExpr extends FuncApp
{
    public ArrayExpr(FuncDecl decl, List<Expr> args)
    {
        super(decl, args);
    }

    public ArrayExpr(FuncDecl decl) {
        super(decl);
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
}
