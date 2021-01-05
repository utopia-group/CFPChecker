package edu.utexas.cs.utopia.cfpchecker.verifier;

import edu.utexas.cs.utopia.cfpchecker.speclang.Specification;
import soot.*;
import soot.jimple.*;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.BlockGraph;
import soot.toolkits.graph.CompleteBlockGraph;

import java.util.*;

import static edu.utexas.cs.utopia.cfpchecker.verifier.VerifierUtil.reachableFromMain;

/**
 * Created by kferles on 7/23/18.
 */
public class Assumify extends SceneTransformer
{
    public static SootMethod assumeMethod = new SootMethod("assume",
                                                           Collections.singletonList(BooleanType.v()),
                                                           VoidType.v(),
                                                           Modifier.STATIC);

    public static SootMethod assumeNotMethod = new SootMethod("assume_not",
                                                           Collections.singletonList(BooleanType.v()),
                                                           VoidType.v(),
                                                           Modifier.STATIC);

    {
        SootClass mainClass = Scene.v().getMainClass();

        SootMethod existingAssume = mainClass.getMethodByNameUnsafe("assume");
        if (existingAssume == null)
            mainClass.addMethod(assumeMethod);
        else
            assumeMethod = existingAssume;
        SootMethod existingNotAssume = mainClass.getMethodByNameUnsafe("assume_not");
        if (existingNotAssume == null)
            mainClass.addMethod(assumeNotMethod);
        else
            assumeNotMethod = existingNotAssume;
    }

    private Specification spec;

    private void internalTransform(Body b)
    {
        BlockGraph blockGraph = new CompleteBlockGraph(b);

        List<Unit> workList = new ArrayList<>();
        for (Block block : blockGraph.getBlocks())
        {
            Unit tail = block.getTail();
            MustAssumifySwitch sw = new MustAssumifySwitch();
            tail.apply(sw);

            if (sw.getResult())
                workList.add(tail);
        }

        Jimple jimpleFactory = Jimple.v();

        SootMethod method = b.getMethod();
        UnitPatchingChain units = b.getUnits();

        for (Unit u : workList)
        {
            u.apply(new AbstractStmtSwitch()
            {
                @Override
                public void caseIfStmt(IfStmt stmt)
                {
                    if (stmt.getCondition().toString().contains("devirt$")) return;
                    Local condCheckLocal = InstrumentationUtils.getTempBoolLocal(method);
                    units.insertBefore(jimpleFactory.newAssignStmt(condCheckLocal, stmt.getCondition()), stmt);

                    // Add assume(condCheckLocal) before target.
                    InvokeStmt newTrg = jimpleFactory.newInvokeStmt(jimpleFactory.newStaticInvokeExpr(assumeMethod.makeRef(),
                                                                                                      condCheckLocal));
                    Stmt currTarget = stmt.getTarget();
                    boolean removePredOfAssume = units.getPredOf(currTarget) instanceof ReturnVoidStmt ||
                            units.getPredOf(currTarget) instanceof ReturnStmt ||
                            units.getPredOf(currTarget) instanceof ThrowStmt;
                    units.insertOnEdge(newTrg, stmt, currTarget);
                    stmt.setTarget(newTrg);

                    // Bypass bug in insertOnEdge procedure.
                    if (removePredOfAssume)
                        units.remove(units.getPredOf(newTrg));

                    // Add assume(!condCheckLocal) after IfStmt

                    units.insertAfter(jimpleFactory.newInvokeStmt(jimpleFactory.newStaticInvokeExpr(assumeNotMethod.makeRef(),
                                                                                                    condCheckLocal)),
                                      stmt);
                }

                @Override
                public void caseLookupSwitchStmt(LookupSwitchStmt stmt)
                {
                    throw new UnsupportedOperationException("Implement caseLookupSwitchStmt, you lazy bum!");
                }

                @Override
                public void caseTableSwitchStmt(TableSwitchStmt stmt)
                {
                    throw new UnsupportedOperationException("Move your ass and implement caseTableSwitchStmt");
                }
            });
        }
    }

    @Override
    protected void internalTransform(String s, Map<String, String> map)
    {
        Set<SootMethod> workSet = new HashSet<>();
        for (SootClass cl : Scene.v().getClasses())
        {
            for (SootMethod m : cl.getMethods())
                if (VerifierUtil.mustAnalyze(spec.getApiClasses(), m) && reachableFromMain(m))
                    workSet.add(m);
        }

        for (SootMethod m : workSet)
            internalTransform(m.getActiveBody());
    }

    public Assumify(Specification spec)
    {
        this.spec = spec;
    }
}

class MustAssumifySwitch extends AbstractStmtSwitch
{
    boolean result = false;

    @Override
    public void caseIfStmt(IfStmt stmt)
    {
        result = true;
    }

    @Override
    public void caseLookupSwitchStmt(LookupSwitchStmt stmt)
    {
        result = true;
    }

    @Override
    public void caseTableSwitchStmt(TableSwitchStmt stmt)
    {
        result = true;
    }

    @Override
    public Boolean getResult()
    {
        return result;
    }
}
