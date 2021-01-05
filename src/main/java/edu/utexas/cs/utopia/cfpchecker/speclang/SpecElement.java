package edu.utexas.cs.utopia.cfpchecker.speclang;

import soot.SootMethod;

import java.util.Map;
import java.util.Set;

/**
 * Created by kferles on 6/27/18.
 */
public interface SpecElement
{
    Map<SootMethod, Set<APISpecCall>> getAPICallInstances();
}
