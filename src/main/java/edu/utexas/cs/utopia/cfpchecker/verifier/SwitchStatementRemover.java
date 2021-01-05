package edu.utexas.cs.utopia.cfpchecker.verifier;

// Borrowed from Jayhorn: https://github.com/jayhorn/jayhorn/blob/dd0b77ccb63ca1b5ed46160d7b50f6ba22289e5b/soottocfg/src/main/java/soottocfg/soot/transformers/SwitchStatementRemover.java

import java.util.*;
import java.util.Map.Entry;

import edu.utexas.cs.utopia.cfpchecker.speclang.Specification;
import soot.*;
import soot.jimple.*;
import soot.tagkit.Host;

import static edu.utexas.cs.utopia.cfpchecker.verifier.VerifierUtil.reachableFromMain;

public class SwitchStatementRemover extends SceneTransformer
{
    private Specification spec;

    public SwitchStatementRemover(Specification spec)
    {
        this.spec = spec;
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
        {
            Body body = m.getActiveBody();
            Map<Unit, List<Unit>> toReplace = new LinkedHashMap<>();
            PatchingChain<Unit> units = body.getUnits();
            for (Unit u : units) {
                if (u instanceof SwitchStmt) {
                    toReplace.put(u, replaceSwitchStatement((SwitchStmt) u));
                }
            }
            for (Entry<Unit, List<Unit>> entry : toReplace.entrySet()) {
                units.insertBefore(entry.getValue(), entry.getKey());
                units.remove(entry.getKey());
            }
            body.validate();
        }
    }

    /**
     * Replace a SwitchStatement by a sequence of IfStmts and a Goto for the
     * default case.
     *
     * @param s
     * @return
     */
    private List<Unit> replaceSwitchStatement(SwitchStmt s) {
        List<Unit> result = new LinkedList<>();

        List<Expr> cases = new LinkedList<>();
        List<Unit> targets = new LinkedList<>();
        Unit defaultTarget = s.getDefaultTarget();

        if (s instanceof TableSwitchStmt) {
            TableSwitchStmt arg0 = (TableSwitchStmt) s;
            int counter = 0;
            for (int i = arg0.getLowIndex(); i <= arg0.getHighIndex(); i++) {
                cases.add(Jimple.v().newEqExpr(arg0.getKey(), IntConstant.v(i)));
                targets.add(arg0.getTarget(counter));
                counter++;
            }
        } else {
            LookupSwitchStmt arg0 = (LookupSwitchStmt) s;
            for (int i = 0; i < arg0.getTargetCount(); i++) {
                cases.add(Jimple.v().newEqExpr(arg0.getKey(), IntConstant.v(arg0.getLookupValue(i))));
                targets.add(arg0.getTarget(i));
            }
        }

        for (int i = 0; i < cases.size(); i++) {
            // create the ifstmt
            Unit ifstmt = ifStmtFor(cases.get(i), targets.get(i), s);
            result.add(ifstmt);
        }
        if (defaultTarget != null) {
            Unit gotoStmt = gotoStmtFor(defaultTarget, s);
            result.add(gotoStmt);
        }
        return result;
    }

    private Unit ifStmtFor(Value condition, Unit target, Host createdFrom)
    {
        IfStmt stmt = Jimple.v().newIfStmt(condition, target);
        stmt.addAllTagsOf(createdFrom);
        return stmt;
    }

    private Unit gotoStmtFor(Unit target, Host createdFrom)
    {
        GotoStmt stmt = Jimple.v().newGotoStmt(target);
        stmt.addAllTagsOf(createdFrom);
        return stmt;
    }
}
