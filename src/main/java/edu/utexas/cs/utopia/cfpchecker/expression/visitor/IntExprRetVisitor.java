package edu.utexas.cs.utopia.cfpchecker.expression.visitor;

import edu.utexas.cs.utopia.cfpchecker.expression.integer.*;

/**
 * Created by kferles on 5/18/17.
 */
public interface IntExprRetVisitor<R>
{
    R visit(DivExpr e);

    R visit(RemExpr e);

    R visit(ModExpr e);

    R visit(MinusExpr e);

    R visit(MultExpr e);

    R visit(PlusExpr e);
}
