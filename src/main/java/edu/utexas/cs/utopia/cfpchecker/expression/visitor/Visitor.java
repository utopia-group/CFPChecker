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
public interface Visitor extends BoolExprVisitor, FuncExprVisitor, IntExprRetVisitor, TermExprVisitor, ArrayExprVisitor
{
    void visit(BoolExpr e);

    void visit(Expr e);

    void visit(FuncExpr e);

    void visit(IntExpr e);

    void visit(TermExpr e);

    void visit(ArrayExpr e);
}
