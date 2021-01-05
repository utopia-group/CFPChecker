package edu.utexas.cs.utopia.cfpchecker.expression.terminal;

import edu.utexas.cs.utopia.cfpchecker.expression.type.BooleanType;

/**
 * Created by kferles on 5/24/17.
 */
public abstract class ConstBool extends TermExpr
{
    ConstBool()
    {
        super(BooleanType.getInstance());
    }

    public abstract boolean isTrue();

    public abstract boolean isFalse();
}
