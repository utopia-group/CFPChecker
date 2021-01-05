package edu.utexas.cs.utopia.cfpchecker.verifier;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.annotation.purity.DirectedCallGraph;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.StronglyConnectedComponentsFast;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by kferles on 7/2/18.
 */
public class VerifierUtil
{
    private static Set<SootMethod> reachableMethods = ConcurrentHashMap.newKeySet();

    static public boolean mustAnalyze(Set<SootClass> apiClasses, SootMethod method)
    {
        // TODO: Handle <clinit>'s
        if (method.getName().equals("<clinit>") ||
            method.getName().startsWith("nd$") ||
            method == Assumify.assumeMethod ||
            method == Assumify.assumeNotMethod ||
            !method.hasActiveBody() ||
            // Bit hacky, but for some cases cannot find a workaround.
            method.getActiveBody().toString().contains("Unresolved compilation error:") ||
            !method.isDeclared()
        )
            return false;

        SootClass sootClass = method.getDeclaringClass();
        return !sootClass.isLibraryClass();
    }

    public static boolean reachableFromMain(SootMethod method)
    {
        return reachableMethods.contains(method);
    }

    public static void addToReachableMethods(SootMethod m)
    {
        reachableMethods.add(m);
    }

    public static void calculateReachableMethods()
    {
        CallGraph cg = Scene.v().getCallGraph();
        DirectedCallGraph dcg = new DirectedCallGraph(cg, (m) -> (mustAnalyze(Collections.emptySet(), m)), Collections.singletonList(Scene.v().getMainMethod()).iterator(), false);
        StronglyConnectedComponentsFast<SootMethod> sccs = new StronglyConnectedComponentsFast<>(dcg);

        sccs.getComponents()
                .forEach(scc -> reachableMethods.addAll(scc));
    }

    public static Set<SootMethod> targetMethodsOf(Unit u)
    {
        Set<SootMethod> outMeth = new HashSet<>();
        CallGraph cg = Scene.v().getCallGraph();
        Iterator<Edge> it = cg.edgesOutOf(u);
        while (it.hasNext())
        {
            outMeth.add(it.next().tgt());
        }
        return outMeth;
    }

    public static Set<Edge> edgedOutOf(Unit u)
    {
        Set<Edge> outMeth = new HashSet<>();
        CallGraph cg = Scene.v().getCallGraph();
        Iterator<Edge> it = cg.edgesOutOf(u);
        while (it.hasNext())
        {
            outMeth.add(it.next());
        }
        return outMeth;
    }
}
