package edu.utexas.cs.utopia.cfpchecker.speclang;

import soot.SootMethod;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by kferles on 6/28/18.
 */
class SpecUtils
{
    static Map<SootMethod, Set<APISpecCall>> mergeAllAPICalls(SpecElement... elements)
    {
        Map<SootMethod, Set<APISpecCall>> rv = Collections.emptyMap();

        // Merge all maps
        for (SpecElement e : elements)
        {
            rv = Stream.concat(rv.entrySet().stream(), e.getAPICallInstances().entrySet().stream())
                       .collect(Collectors.toMap(
                               Map.Entry::getKey,
                               Map.Entry::getValue,
                               (v1, v2) -> Stream.concat(v1.stream(), v2.stream()).collect(Collectors.toSet())
                       ));
        }

        return rv;
    }
}
