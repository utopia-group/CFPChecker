package edu.utexas.cs.utopia.cfpchecker.expression.visitor;

import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncApp;
import edu.utexas.cs.utopia.cfpchecker.expression.terminal.ConstInteger;
import edu.utexas.cs.utopia.cfpchecker.expression.terminal.ConstString;
import edu.utexas.cs.utopia.cfpchecker.expression.terminal.FalseConst;
import edu.utexas.cs.utopia.cfpchecker.expression.terminal.TrueConst;

/**
 * Created by kferles on 5/18/17.
 */
public interface TermExprVisitor
{
    void visit(ConstInteger e);

    void visit(FalseConst e);

    void visit(FuncApp e);

    void visit(ConstString e);

    void visit(TrueConst e);
}
