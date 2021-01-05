package edu.utexas.cs.utopia.cfpchecker.expression.factory;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.integer.*;

/**
 * Created by kferles on 5/18/17.
 */
public interface IntExprFactory
{
    DivExpr mkDIV(Expr left, Expr right);

    RemExpr mkRem(Expr left, Expr right);

    ModExpr mkMod(Expr left, Expr right);

    MinusExpr mkMINUS(Expr e1, Expr e2, Expr... es);

    MultExpr mkMULT(Expr e1, Expr e2, Expr... es);

    PlusExpr mkPLUS(Expr e1, Expr e2, Expr... es);
}
