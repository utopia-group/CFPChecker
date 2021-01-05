package edu.utexas.cs.utopia.cfpchecker.expression.visitor;

import edu.utexas.cs.utopia.cfpchecker.expression.integer.*;

/**
 * Created by kferles on 5/18/17.
 */
public interface IntExprVisitor
{
    void visit(DivExpr e);

    void visit(RemExpr e);

    void visit(ModExpr e);

    void visit(MinusExpr e);

    void visit(MultExpr e);

    void visit(PlusExpr e);
}
