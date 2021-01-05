package edu.utexas.cs.utopia.cfpchecker.speclang;

import org.apache.commons.lang.Validate;
import soot.SootMethod;

import java.util.Map;
import java.util.Set;

import static edu.utexas.cs.utopia.cfpchecker.speclang.SpecUtils.mergeAllAPICalls;

/**
 * Created by kferles on 6/27/18.
 */
public class SpecRule implements SpecElement
{
    private NonTerminal lhs;

    private SpecElement[] rhs;

    public SpecRule(NonTerminal lhs, SpecElement... rhs)
    {
        Validate.notNull(lhs);
        Validate.notNull(rhs);
        Validate.isTrue(rhs.length >= 1, "Empty RHS for rule");

        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public Map<SootMethod, Set<APISpecCall>> getAPICallInstances()
    {
        return mergeAllAPICalls(rhs);
    }

    @Override
    public String toString()
    {
        StringBuilder rv = new StringBuilder(lhs.toString()).append(" ->");
        for (SpecElement e : rhs)
            rv.append(" ").append(e.toString());

        return rv.toString();
    }

}
