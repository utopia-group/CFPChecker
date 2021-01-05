package edu.utexas.cs.utopia.cfpchecker.expression.visitor;

import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncApp;
import edu.utexas.cs.utopia.cfpchecker.expression.terminal.ConstInteger;
import edu.utexas.cs.utopia.cfpchecker.expression.terminal.ConstString;
import edu.utexas.cs.utopia.cfpchecker.expression.terminal.FalseConst;
import edu.utexas.cs.utopia.cfpchecker.expression.terminal.TrueConst;

/**
 * Created by kferles on 5/18/17.
 */
public interface TermExprRetVisitor<R>
{
    R visit(ConstInteger e);

    R visit(FalseConst e);

    R visit(FuncApp e);

    R visit(ConstString e);

    R visit(TrueConst e);
}
