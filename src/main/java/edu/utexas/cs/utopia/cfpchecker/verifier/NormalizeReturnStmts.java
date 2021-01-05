package edu.utexas.cs.utopia.cfpchecker.verifier;

import edu.utexas.cs.utopia.cfpchecker.speclang.Specification;
import polyglot.ast.Return;
import soot.*;
import soot.jimple.*;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static edu.utexas.cs.utopia.cfpchecker.verifier.VerifierUtil.reachableFromMain;

public class NormalizeReturnStmts extends SceneTransformer
{
    private Specification spec;

    public NormalizeReturnStmts(Specification spec)
    {
        this.spec = spec;
    }

    @Override
    protected void internalTransform(String s, Map<String, String> map)
    {
        for (SootClass cl : Scene.v().getClasses())
        {
            for (SootMethod m : cl.getMethods())
                if (VerifierUtil.mustAnalyze(spec.getApiClasses(), m) && reachableFromMain(m))
                    normalize(m);

        }
    }

    private void normalize(SootMethod m)
    {
        Body b = m.getActiveBody();
        UnitPatchingChain units = b.getUnits();
        Set<Unit> retStmts = units.stream()
                                  .filter(u -> u instanceof ReturnVoidStmt || u instanceof ReturnStmt
                                               // Temporary solution, until we support exceptions.
                                               || u instanceof ThrowStmt
                                  )
                                  .collect(Collectors.toSet());

        if (retStmts.size() > 1)
        {
            Jimple jimpleGen = Jimple.v();
            Local retLocal = null;
            if (!(m.getReturnType() instanceof VoidType))
            {
                retLocal = jimpleGen.newLocal("new_ret_local", m.getReturnType());
                b.getLocals().add(retLocal);
            }

            Unit retStmt;
            if (retLocal != null)
                retStmt = jimpleGen.newReturnStmt(retLocal);
            else
                retStmt = jimpleGen.newReturnVoidStmt();

            units.addLast(retStmt);

            for (Unit oldRet : retStmts)
            {
                if (oldRet instanceof ReturnStmt && retLocal != null)
                {
                    Value retVal = ((ReturnStmt) oldRet).getOp();
                    AssignStmt retAssign = jimpleGen.newAssignStmt(retLocal, retVal);
                    units.swapWith(oldRet, retAssign);
                    units.insertAfter(jimpleGen.newGotoStmt(retStmt), retAssign);
                }
                else if (oldRet instanceof ThrowStmt && units.getSuccOf(oldRet) != null)
                {
                    units.swapWith(oldRet, jimpleGen.newGotoStmt(units.getSuccOf(oldRet)));
                }
                else
                {
                    units.swapWith(oldRet, jimpleGen.newGotoStmt(retStmt));
                }
            }
            b.validate();
        }
    }
}
