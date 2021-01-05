package edu.utexas.cs.utopia.cfpchecker.expression.type;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.visitor.ExprTypeRetVisitor;

import java.util.List;

/**
 * Created by kferles on 5/17/17.
 */
public interface ExprType
{
    int getArity();

    // TODO: convert this to isCompatibleWith(Type ty)
    boolean isCompatibleWith(List<Expr> args);

    <R> R accept(ExprTypeRetVisitor<R> v);
}
