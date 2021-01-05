package edu.utexas.cs.utopia.cfpchecker.expression.factory;

import edu.utexas.cs.utopia.cfpchecker.expression.terminal.ConstInteger;
import edu.utexas.cs.utopia.cfpchecker.expression.terminal.ConstString;
import edu.utexas.cs.utopia.cfpchecker.expression.terminal.FalseConst;
import edu.utexas.cs.utopia.cfpchecker.expression.terminal.TrueConst;

import java.math.BigInteger;

/**
 * Created by kferles on 5/18/17.
 */
public interface TermExprFactory
{
    ConstInteger mkINT(BigInteger val);

    ConstString mkSTRING(String val);

    FalseConst mkFALSE();

    TrueConst mkTRUE();
}
