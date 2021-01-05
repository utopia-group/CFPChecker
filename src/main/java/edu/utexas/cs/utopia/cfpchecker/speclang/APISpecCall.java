package edu.utexas.cs.utopia.cfpchecker.speclang;

import org.apache.commons.lang.Validate;
import soot.SootMethod;

import java.util.*;

import static edu.utexas.cs.utopia.cfpchecker.grammarcomp.GrammarCompUtil.formatTerminal;

/**
 * Created by kferles on 6/27/18.
 */
public class APISpecCall implements SpecElement
{
    private SootMethod method;

    private Wildcard[] params;

    private int actualParamCount(SootMethod method)
    {
        int paramCount = method.getParameterCount();
        return method.isStatic() ? paramCount : paramCount + 1;
    }

    public APISpecCall(SootMethod method, Wildcard[] params)
    {
        Validate.notNull(method);
        Validate.notNull(params);

        if (params.length != actualParamCount(method))
            throw new IllegalArgumentException("Wildcards do not match number of parameters in " + method.getSignature());

        this.method = method;
        this.params = params;
    }

    public SootMethod getMethod()
    {
        return method;
    }

    public Wildcard[] getParams()
    {
        return params;
    }

    @Override
    public Map<SootMethod, Set<APISpecCall>> getAPICallInstances()
    {
        Set<APISpecCall> s = Collections.singleton(this);
        HashMap<SootMethod, Set<APISpecCall>> rv = new HashMap<>();
        rv.put(method, s);
        return rv;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        APISpecCall apiSpecCall = (APISpecCall) o;

        if (!method.equals(apiSpecCall.method)) return false;
        return Arrays.equals(params, apiSpecCall.params);
    }

    @Override
    public int hashCode()
    {
        int result = method.hashCode();
        result = 31 * result + Arrays.hashCode(params);
        return result;
    }

    @Override
    public String toString()
    {
        StringBuilder rv = new StringBuilder(method.getSignature()).append("<");

        int paramLen = params.length;

        if (paramLen >= 1)
            rv.append(params[0].toString());

        for (int i = 1; i < paramLen; ++i)
            rv.append(",").append(params[i].toString());

        return formatTerminal(rv.append(">").toString());
    }
}
