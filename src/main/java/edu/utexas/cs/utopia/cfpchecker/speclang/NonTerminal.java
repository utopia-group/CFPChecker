package edu.utexas.cs.utopia.cfpchecker.speclang;

import org.apache.commons.lang.Validate;
import soot.SootMethod;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Created by kferles on 6/27/18.
 */
public class NonTerminal implements SpecElement
{
    private String name;

    public NonTerminal(String name)
    {
        Validate.notNull(name);

        this.name = name;
    }

    @Override
    public Map<SootMethod, Set<APISpecCall>> getAPICallInstances()
    {
        return Collections.emptyMap();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NonTerminal that = (NonTerminal) o;

        return name.equals(that.name);
    }

    @Override
    public int hashCode()
    {
        return name.hashCode();
    }

    @Override
    public String toString()
    {
        return name;
    }
}
