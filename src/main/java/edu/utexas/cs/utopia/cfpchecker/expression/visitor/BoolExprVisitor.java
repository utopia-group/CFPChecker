package edu.utexas.cs.utopia.cfpchecker.expression.visitor;

import edu.utexas.cs.utopia.cfpchecker.expression.bool.*;

/**
 * Created by kferles on 5/18/17.
 */
public interface BoolExprVisitor
{
    void visit(AndExpr e);

    void visit(EqExpr e);

    void visit(ExistentialQuantifier e);

    void visit(GreaterEqExpr e);

    void visit(GreaterExpr e);

    void visit(ImplExpr e);

    void visit(LessEqExpr e);

    void visit(LessExpr e);

    void visit(NegExpr e);

    void visit(OrExpr e);

    void visit(UniversalQuantifier e);
}
