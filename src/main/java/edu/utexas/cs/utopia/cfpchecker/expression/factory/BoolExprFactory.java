package edu.utexas.cs.utopia.cfpchecker.expression.factory;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.bool.*;
import edu.utexas.cs.utopia.cfpchecker.expression.function.BoundVar;

/**
 * Created by kferles on 5/18/17.
 */
public interface BoolExprFactory
{
    AndExpr mkAND(Expr e1, Expr e2, Expr... es);

    EqExpr mkEQ(Expr left, Expr right);

    ExistentialQuantifier mkEXIST(Expr[] exprToBound, Expr body);

    ExistentialQuantifier mkEXIST(BoundVar[] bVars, Expr body, Expr[] exprToBound);

    GreaterEqExpr mkGEQ(Expr left, Expr right);

    GreaterExpr mkGT(Expr left, Expr right);

    ImplExpr mkIMPL(Expr left, Expr right);

    LessEqExpr mkLEQ(Expr left, Expr right);

    LessExpr mkLT(Expr left, Expr right);

    NegExpr mkNEG(Expr arg);

    OrExpr mkOR(Expr e1, Expr e2, Expr... es);

    UniversalQuantifier mkFORALL(Expr[] exprToBound, Expr body);

    UniversalQuantifier mkFORALL(BoundVar[] bVars, Expr body, Expr[] exprToBound);
}
