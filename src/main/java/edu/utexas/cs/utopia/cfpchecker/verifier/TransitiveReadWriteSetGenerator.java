package edu.utexas.cs.utopia.cfpchecker.verifier;

import edu.utexas.cs.utopia.cfpchecker.speclang.Specification;
import soot.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.pointer.MethodRWSet;
import soot.jimple.toolkits.pointer.RWSet;
import soot.jimple.toolkits.pointer.SideEffectAnalysis;
import soot.util.Chain;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static edu.utexas.cs.utopia.cfpchecker.verifier.VerifierUtil.mustAnalyze;

public class TransitiveReadWriteSetGenerator extends SceneTransformer {
    //Map<SootMethod, RWSet> writeSets = new ConcurrentHashMap<>();
    Map<SootMethod, Set<SootField>> writeSets = new ConcurrentHashMap<>();
    Map<SootMethod, Set<SootField>> readSets = new ConcurrentHashMap<>();
    Specification spec;

    private static TransitiveReadWriteSetGenerator instance = null;

    public static TransitiveReadWriteSetGenerator v() {
        return instance;
    }

    public static TransitiveReadWriteSetGenerator v(Specification spec) {
        if(instance == null) {
            instance = new TransitiveReadWriteSetGenerator(spec);
        }

        return instance;
    }


    private TransitiveReadWriteSetGenerator(Specification spec) {
        this.spec = spec;
    }

    public Set<SootField> getFieldReadWriteSet(SootMethod m) {
        assert writeSets.containsKey(m) && readSets.containsKey(m);

        Set<SootField> readWriteSet = new HashSet<>();
        readWriteSet.addAll(writeSets.get(m));
        readWriteSet.addAll(readSets.get(m));

        return readWriteSet;
    }

    public Set<SootField> getFieldWriteSet(SootMethod m) {
        assert writeSets.containsKey(m);

        Set<SootField> writeSet = new HashSet<>();
        writeSet.addAll(writeSets.get(m));
        //writeSet.addAll(fieldWriteSets.get(Scene.v().getMainMethod()));

        return writeSet;
    }

    void addEdges(CallGraph cg, HashMap<SootMethod, HashSet<SootMethod>> calledBy, SootMethod m) {
        Iterator<Edge> edges = cg.edgesInto(m);
        while(edges.hasNext()) {
            Edge e = edges.next();
            SootMethod src = e.getSrc().method();

            if(mustAnalyze(spec.getApiClasses(), src) && src.hasActiveBody()) {
                if(calledBy.containsKey(m)) {
                    calledBy.get(m).add(src);
                }
                else {
                    HashSet<SootMethod> srcs = new HashSet<>();
                    srcs.add(src);
                    calledBy.put(m, srcs);
                }
            }
        }
    }

    @Override
    protected void internalTransform(String s, Map<String, String> map) {
        SideEffectAnalysis sideEffects = Scene.v().getSideEffectAnalysis();
        CallGraph cg = Scene.v().getCallGraph();
        Chain<SootClass> classes = Scene.v().getClasses();
        Stack<SootMethod> worklist = new Stack<>();
        HashMap<SootMethod, HashSet<SootMethod>> calledBy = new HashMap<>();

        for(SootClass c : classes) {
            for(SootMethod m : c.getMethods()) {
                if(mustAnalyze(spec.getApiClasses(), m) && m.hasActiveBody()) {
                    sideEffects.findNTRWSets(m);

                    //statics are contained in globals, we are only concerned about fields
                    Set<SootField> fieldWriteSet = ConcurrentHashMap.newKeySet();
                    Set<SootField> fieldReadSet = ConcurrentHashMap.newKeySet();

                    //The RWSets don't have a few special cases where they don't return a SootField, if array
                    //elements are modified, they contain the string element ARRAY_ELEMENTS_NODE
                    RWSet writes = sideEffects.nonTransitiveWriteSet(m);
                    if(writes != null) {
                        for(Object write : writes.getFields()) {
                            if(write instanceof SootField) {
                                fieldWriteSet.add((SootField) write);
                            }
                        }
                    }

                    RWSet reads = sideEffects.nonTransitiveReadSet(m);
                    if(reads != null) {
                        for(Object read : reads.getFields()) {
                            if(read instanceof SootField) {
                                fieldReadSet.add((SootField) read);
                            }
                        }
                    }

                    writeSets.put(m, fieldWriteSet);
                    readSets.put(m, fieldReadSet);

                    addEdges(cg, calledBy, m);
                    worklist.add(m);
                }
            }
        }

        while(!worklist.empty()) {
            SootMethod tgt = worklist.pop();
            Set<SootField> tgtWrites = writeSets.get(tgt);
            Set<SootField> tgtReads = readSets.get(tgt);

            if(calledBy.containsKey(tgt)) {
                for (SootMethod src : calledBy.get(tgt)) {
                    Set<SootField> srcWrites = writeSets.get(src);
                    Set<SootField> srcReads = readSets.get(src);

                    boolean changed = srcWrites.addAll(tgtWrites);
                    changed = srcReads.addAll(tgtReads) || changed;

                    if (changed) {
                        if (!worklist.contains(src)) {
                            worklist.add(src);
                        }
                    }
                }
            }
        }
    }
}
