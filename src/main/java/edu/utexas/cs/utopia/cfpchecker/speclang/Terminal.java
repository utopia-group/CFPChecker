package edu.utexas.cs.utopia.cfpchecker.speclang;

import org.apache.commons.lang.Validate;
import soot.SootMethod;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Created by kferles on 6/28/18.
 */
public class Terminal implements SpecElement
{
    public static final Terminal EPSILON_TRANSITION = new Terminal("\"\"");

    public static final Terminal DUMMY_RETURN_SYMBOL = new Terminal("@ret");

    public static final Terminal RETURN_SYMBOL_KLEENE = new Terminal(DUMMY_RETURN_SYMBOL + "*");

    private String name;

    public Terminal(String name)
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
    public String toString()
    {
        return name;
    }
}
