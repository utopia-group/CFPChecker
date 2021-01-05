package edu.utexas.cs.utopia.cfpchecker.verifier;

import soot.*;
import soot.jimple.internal.JimpleLocal;
import soot.util.Chain;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by kferles on 7/23/18.
 */
public class InstrumentationUtils
{
    static private Map<SootMethod, Integer> tempBoolCounters = new HashMap<>();

    static Local addOrGetLocal(Body b, String name, Type t)
    {
        Chain<Local> locals = b.getLocals();
        Local rv = null;

        for (Local l : locals)
            if (l.getName().equals(name))
            {
                assert l.getType().equals(t) : "Attempting to add existing local with different types.";
                rv = l;
                break;
            }

        if (rv == null)
        {
            rv = new JimpleLocal(name, t);
            locals.add(rv);
        }

        return rv;
    }

    static Local getTempBoolLocal(SootMethod method)
    {
        Integer currCount = tempBoolCounters.getOrDefault(method, 0);
        tempBoolCounters.put(method, currCount + 1);

        return addOrGetLocal(method.getActiveBody(), method.getName() + "_bool_tmp_" + currCount, BooleanType.v());
    }
}
