package edu.utexas.cs.utopia.cfpchecker.expression.bool;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.function.BoundVar;

/**
 * Created by kferles on 7/26/17.
 */
public abstract class Quantifier extends BoolExpr
{
    protected BoundVar[] boundVars;

    protected Expr body;

    public Quantifier(BoundVar[] boundVars, Expr body)
    {
        this.boundVars = boundVars;
        this.body = body;
    }

    public int getBoundedVarNum()
    {
        return boundVars.length;
    }

    public Expr getBody()
    {
        return this.body;
    }

    public BoundVar boundedVarAt(int i)
    {
        assert i >= 0 && i < getBoundedVarNum();

        return boundVars[i];
    }

    public BoundVar[] getBoundVars()
    {
        return boundVars;
    }
}
