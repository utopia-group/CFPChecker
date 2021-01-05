package edu.utexas.cs.utopia.cfpchecker.expression.visitor;

import edu.utexas.cs.utopia.cfpchecker.expression.array.SelectExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.array.StoreExpr;

/**
 * Created by kferles on 9/18/18.
 */
public interface ArrayExprVisitor
{
    void visit(SelectExpr e);

    void visit(StoreExpr e);
}
