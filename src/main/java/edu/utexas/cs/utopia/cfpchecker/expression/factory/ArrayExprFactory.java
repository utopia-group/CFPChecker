package edu.utexas.cs.utopia.cfpchecker.expression.factory;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.array.SelectExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.array.StoreExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncApp;

/**
 * Created by kferles on 9/18/18.
 */
public interface ArrayExprFactory
{
    SelectExpr mkSelectExpr(FuncApp array, Expr indexExpr);

    StoreExpr mkStoreExpr(FuncApp array, Expr indexExpr, Expr newValueExpr);
}
