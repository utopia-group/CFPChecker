package edu.utexas.cs.utopia.cfpchecker.expression.visitor;

import edu.utexas.cs.utopia.cfpchecker.expression.bool.*;

/**
 * Created by kferles on 5/18/17.
 */
public interface BoolExprRetVisitor<R>
{
    R visit(AndExpr e);

    R visit(EqExpr e);

    R visit(ExistentialQuantifier e);

    R visit(GreaterEqExpr e);

    R visit(GreaterExpr e);

    R visit(ImplExpr e);

    R visit(LessEqExpr e);

    R visit(LessExpr e);

    R visit(NegExpr e);

    R visit(OrExpr e);

    R visit(UniversalQuantifier e);
}
