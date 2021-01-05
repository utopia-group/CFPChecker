package edu.utexas.cs.utopia.cfpchecker.expression.visitor;

import edu.utexas.cs.utopia.cfpchecker.expression.function.BoundVar;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncApp;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncDecl;

/**
 * Created by kferles on 5/19/17.
 */
public interface FuncExprVisitor
{
    void visit(BoundVar e);

    void visit(FuncApp e);

    void visit(FuncDecl e);
}
