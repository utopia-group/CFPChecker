package edu.utexas.cs.utopia.cfpchecker.expression.visitor;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.array.ArrayExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.bool.BoolExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.integer.IntExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.terminal.TermExpr;

/**
 * Created by kferles on 5/18/17.
 */
public interface RetVisitor<R> extends BoolExprRetVisitor<R>, FuncExprRetVisitor<R>, IntExprRetVisitor<R>, TermExprRetVisitor<R>, ArrayExprRetVisitor<R>
{
    R visit(BoolExpr e);

    R visit(Expr e);

    R visit(FuncExpr e);

    R visit(IntExpr e);

    R visit(TermExpr e);

    R visit(ArrayExpr e);
}
