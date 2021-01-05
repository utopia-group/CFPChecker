package edu.utexas.cs.utopia.cfpchecker.expression.visitor;

import edu.utexas.cs.utopia.cfpchecker.expression.function.BoundVar;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncApp;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncDecl;

/**
 * Created by kferles on 5/19/17.
 */
public interface FuncExprRetVisitor<R>
{
    R visit(BoundVar e);

    R visit(FuncApp e);

    R visit(FuncDecl e);
}
