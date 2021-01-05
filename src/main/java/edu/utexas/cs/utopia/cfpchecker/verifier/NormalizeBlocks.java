package edu.utexas.cs.utopia.cfpchecker.verifier;

import edu.utexas.cs.utopia.cfpchecker.speclang.Specification;
import soot.*;
import soot.jimple.*;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.BlockGraph;
import soot.toolkits.graph.CompleteBlockGraph;

import java.util.Map;

import static edu.utexas.cs.utopia.cfpchecker.verifier.VerifierUtil.reachableFromMain;

/**
 * Created by kferles on 6/7/17.
 */
public class NormalizeBlocks extends SceneTransformer
{
    private Specification spec;

    public static void normalizeBBs(SootMethod method)
    {
        Body body = method.getActiveBody();
        BlockGraph graph = new CompleteBlockGraph(body);
        PatchingChain<Unit> units = body.getUnits();

        for (Block block : graph)
        {
            Unit term = block.getTail();
            AbstractStmtSwitch explicitControlTransfer = new AbstractStmtSwitch()
            {
                boolean result = false;

                @Override
                public void caseGotoStmt(GotoStmt stmt)
                {
                    result = true;
                }

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
                public void caseRetStmt(RetStmt stmt)
                {
                    result = true;
                }

                @Override
                public void caseReturnStmt(ReturnStmt stmt)
                {
                    result = true;
                }

                @Override
                public void caseReturnVoidStmt(ReturnVoidStmt stmt)
                {
                    result = true;
                }

                @Override
                public void caseTableSwitchStmt(TableSwitchStmt stmt)
                {
                    result = true;
                }

                @Override
                public void caseThrowStmt(ThrowStmt stmt)
                {
                    result = true;
                }

                @Override
                public Object getResult()
                {
                    return result;
                }
            };

            term.apply(explicitControlTransfer);

            if (!(Boolean) explicitControlTransfer.getResult())
            {
                assert term.fallsThrough() : "Undetected terminator instruction";
                units.insertAfter(Jimple.v().newGotoStmt(block.getSuccs().get(0).getHead()), term);
            }
        }
    }


    @Override
    protected void internalTransform(String s, Map<String, String> map)
    {
        for (SootClass cl : Scene.v().getClasses())
        {
            for (SootMethod m : cl.getMethods())
                if (VerifierUtil.mustAnalyze(spec.getApiClasses(), m) && reachableFromMain(m))
                    normalizeBBs(m);
        }
    }

    public NormalizeBlocks(Specification spec)
    {
        this.spec = spec;
    }
}
