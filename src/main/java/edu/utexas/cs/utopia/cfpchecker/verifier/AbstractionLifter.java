package edu.utexas.cs.utopia.cfpchecker.verifier;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.microsoft.z3.Context;
import edu.utexas.cs.utopia.cfpchecker.CmdLine;
import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.ExprUtils;
import edu.utexas.cs.utopia.cfpchecker.expression.factory.ExprFactory;
import edu.utexas.cs.utopia.cfpchecker.grammarcomp.GrammarCompProxy;
import edu.utexas.cs.utopia.cfpchecker.speclang.APISpecCall;
import edu.utexas.cs.utopia.cfpchecker.speclang.Specification;
import edu.utexas.cs.utopia.cfpchecker.speclang.Terminal;
import grammarcomp.grammar.CFGrammar;
import grammarcomp.parsing.PNode;
import grammarcomp.parsing.ParseTree;
import org.apache.commons.lang.Validate;
import scala.collection.JavaConversions;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.annotation.purity.DirectedCallGraph;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.BlockGraph;
import soot.toolkits.graph.CompleteBlockGraph;
import soot.toolkits.graph.StronglyConnectedComponentsFast;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static edu.utexas.cs.utopia.cfpchecker.expression.ExprUtils.checkSATWithZ3;
import static edu.utexas.cs.utopia.cfpchecker.expression.ExprUtils.checkUNSATWithZ3;
import static edu.utexas.cs.utopia.cfpchecker.verifier.VerifierUtil.mustAnalyze;

public class AbstractionLifter extends SceneTransformer
{
    private static final Map<Block, Integer> blockRefinementCount = new HashMap<>();

    final class AbstractBlock
    {
        private Block sootBlock;

        private SootMethod sootMethod;

        private Map<Unit, Expr> abstractPredicates = new HashMap<>();

        private ConcurrentHashMap<String, AbstractBlock> successors = new ConcurrentHashMap<>();

        private ConcurrentHashMap<String, AbstractBlock> predecessors = new ConcurrentHashMap<>();

        private String lhs;

        private List<String> ruleBody = new ArrayList<>();

        private Set<SootMethod> calledMethods;

        private Set<Expr> readWriteSet;

        void addPredecessor(AbstractBlock pred)
        {
            predecessors.put(pred.getRuleLHS(), pred);
        }

        void removePredecessor(AbstractBlock pred)
        {
            predecessors.remove(pred.getRuleLHS());
        }

        void removeSuccessor(AbstractBlock succ)
        {
            String succRuleName = succ.getRuleLHS();
            if (successors.containsKey(succRuleName))
                successors.remove(succRuleName);
//            else
//                throw new IllegalArgumentException(succRuleName + " is not a succesoor of " + this.getRuleLHS());
        }

        AbstractBlock(AbstractBlock o, Expr e) {
            this.sootBlock = o.sootBlock;
            this.sootMethod = o.sootMethod;
            this.ruleBody = o.ruleBody;
            this.calledMethods = o.calledMethods;
            this.abstractPredicates = new HashMap<>(o.abstractPredicates);

            Unit tail = sootBlock.getTail();
            this.abstractPredicates.put(tail, ExprUtils.booleanSimplification(exprFactory, exprFactory.mkAND(e, o.getPredicateForUnit(tail))));

            if (calledMethods != null)
            {
                Unit tailPred = sootBlock.getPredOf(tail);
                this.abstractPredicates.put(tailPred, ExprUtils.booleanSimplification(exprFactory, exprFactory.mkAND(e, o.getPredicateForUnit(tailPred))));
            }

            this.successors = new ConcurrentHashMap<>(o.successors);
            this.predecessors = new ConcurrentHashMap<>(o.predecessors);
            this.readWriteSet = o.readWriteSet;
        }

        AbstractBlock(AbstractBlock o, Unit stmt, Expr e)
        {
            this.sootBlock = o.sootBlock;
            this.sootMethod = o.sootMethod;
            this.ruleBody = o.ruleBody;
            this.calledMethods = o.calledMethods;

            this.abstractPredicates = new HashMap<>(o.abstractPredicates);

            this.abstractPredicates.put(stmt, ExprUtils.booleanSimplification(exprFactory, exprFactory.mkAND(e, o.getPredicateForUnit(stmt))));

            this.successors = new ConcurrentHashMap<>(o.successors);
            this.predecessors = new ConcurrentHashMap<>(o.predecessors);
            this.readWriteSet = o.readWriteSet;
        }

        AbstractBlock(Block sootBlock)
        {
            Validate.notNull(sootBlock);

            this.sootBlock = sootBlock;
            this.sootMethod = sootBlock.getBody().getMethod();

            VariableCollector collector = new VariableCollector();
            for(Unit u : sootBlock) {
                for(ValueBox v : u.getUseAndDefBoxes()) {
                    v.getValue().apply(collector);
                }
            }
            HashSet<Value> readWriteValues = collector.getResult();
            readWriteSet = new HashSet<>();

            ExprGeneratorSwitch exprGen = new ExprGeneratorSwitch(exprFactory, sootBlock.getBody().getMethod());
            for(Value v : readWriteValues) {
                v.apply(exprGen);
                readWriteSet.add(exprGen.getResult());
            }
        }

        Expr getPredicateForUnit(Unit stmt)
        {
            return abstractPredicates.getOrDefault(stmt, exprFactory.mkTRUE());
        }

        void constructInitAbstractEdges()
        {
            assert abstractPredicates.values().size() == 0 : "This method should only be called with an empty abstraction";

            ruleBody = new ArrayList<>();
            for (Unit u : sootBlock)
            {
                u.apply(new AbstractStmtSwitch()
                {
                    private void handleMethod(Unit u, SootMethod calledMeth)
                    {
                        if (!mustAnalyze(spec.getApiClasses(), calledMeth))
                            return;

                        CallGraph cg = Scene.v().getCallGraph();
                        Iterator<Edge> it = cg.edgesOutOf(u);

                        AbstractBlock.this.calledMethods = new HashSet<>();

                        while (it.hasNext())
                        {
                            SootMethod methImpl = it.next().tgt();

                            if (methImpl.getSubSignature().equals(calledMeth.getSubSignature()) && mustAnalyze(spec.getApiClasses(), methImpl))
                            {
                                AbstractBlock.this.calledMethods.add(methImpl);
                            }
                            else if (mustAnalyze(spec.getApiClasses(), methImpl))
                            {
                                System.err.println("Warning, possible StaticLocalizeTransformation bug: " + methImpl.getSignature());
                            }
                        }

                        if (AbstractBlock.this.calledMethods.isEmpty())
                            AbstractBlock.this.calledMethods = null;
                    }

                    // TODO: This needs more work. Currently it makes tons of assumptions about the input program.
                    @Override
                    public void caseInvokeStmt(InvokeStmt stmt)
                    {
                        SootMethod calledMeth = stmt.getInvokeExpr().getMethod();

                        String symbol;

                        if (invokeStmtToSpecCalls.containsKey(stmt))
                        {
                            symbol = invokeStmtToSpecCalls.get(stmt).toString();
                            ruleBody.add(symbol);
                            handleMethod(stmt, stmt.getInvokeExpr().getMethod());
                        }
                        else
                        {
                            handleMethod(stmt, calledMeth);
                        }
                    }

                    @Override
                    public void caseAssignStmt(AssignStmt stmt)
                    {
                        Value rightOp = stmt.getRightOp();
                        if (rightOp instanceof InvokeExpr)
                        {
                            if (invokeStmtToSpecCalls.containsKey(stmt))
                            {
                                String symbol = invokeStmtToSpecCalls.get(stmt).toString();
                                ruleBody.add(symbol);
                                handleMethod(stmt, stmt.getInvokeExpr().getMethod());
                            }
                            else
                            {
                                InvokeExpr invokeExpr = (InvokeExpr) rightOp;
                                handleMethod(stmt, invokeExpr.getMethod());
                            }
                        }
                    }
                });
            }

            Body mBody = sootMethod.getActiveBody();
            Set<Unit> exceptionBlockHead = mBody.getTraps().stream().map(Trap::getBeginUnit).collect(Collectors.toSet());
            for (Block sootSucc : sootBlock.getSuccs())
            {
                // Ignore catch blocks for now
                if (exceptionBlockHead.contains(sootBlock.getHead()))
                    continue;
                assert abstractBlockMap.containsKey(sootSucc) : "Cannot find abstract block for successor basic block";
                abstractBlockMap.get(sootSucc).forEach(abstractBlock -> successors.put(abstractBlock.getRuleLHS(), abstractBlock));
            }

            this.successors.values().forEach(b -> b.addPredecessor(this));
        }

        String getRuleLHS()
        {
            if (lhs == null)
            {
                Integer refinementCount = blockRefinementCount.getOrDefault(sootBlock, 0);
                blockRefinementCount.put(sootBlock, refinementCount + 1);
                lhs = getMethodRuleName(sootMethod) + "_bb_" + sootBlock.getIndexInMethod()
                      + "_ref_count_" + refinementCount;
            }

            return lhs;
        }

        Block getSootBlock()
        {
            return sootBlock;
        }

        void removeInfeasibleEdges()
        {
            Set<AbstractBlock> successorsToRemove;

            successorsToRemove = successors.values().parallelStream().map(succ -> {
                StrongestPostconditionCalculator spCalc = new StrongestPostconditionCalculator(exprFactory, spec, sootMethod, returnLocations);

                Block succSootBlock = succ.sootBlock;

                Expr edgeExpr = spCalc.skolemize(getPredicateForUnit(sootBlock.getTail()));
                Expr succEnc = exprFactory.mkTRUE();

                for (Unit u : succSootBlock)
                {
                    if (succ.calledMethods != null && u == succSootBlock.getTail())
                        break;

                    succEnc = exprFactory.mkAND(succEnc, spCalc.sp(u, exprFactory.mkTRUE()));
                    edgeExpr = exprFactory.mkAND(edgeExpr, spCalc.skolemize(succ.getPredicateForUnit(u)));
                }

                if (!Collections.disjoint(ExprUtils.getFreeVars(succEnc), ExprUtils.getFreeVars(edgeExpr)))
                    edgeExpr = exprFactory.mkAND(succEnc, edgeExpr);

                if (checkUNSATWithZ3(z3Ctx, ExprUtils.booleanSimplification(exprFactory, edgeExpr)))
                {
                    return succ;
                }
                return null;
            }).filter(Objects::nonNull).collect(Collectors.toSet());

            successorsToRemove.forEach(suc -> suc.removePredecessor(this));
            successorsToRemove.forEach(this::removeSuccessor);

            if (!methodAbstractExits.get(this.sootMethod).contains(this) && this.successors.size() == 0)
                removeFromAbstraction();
            else if (this.predecessors.size() == 0 &&
                     !methodAbstractEntries.get(this.sootMethod).contains(this))
                removeFromAbstraction();
        }

        void removeFromAbstraction()
        {
            Set<AbstractBlock> currAbstractBlocks = abstractBlockMap.get(sootBlock);
            currAbstractBlocks.remove(this);
            blockRuleNameBimap.remove(this);
            blockRuleNameBimapInv.remove(this.getRuleLHS());

            Set<AbstractBlock> currMethAbsEntries = methodAbstractEntries.get(sootMethod);

            if (currMethAbsEntries.contains(this))
                currMethAbsEntries.remove(this);

            Set<AbstractBlock> currMethAbsExits = methodAbstractExits.get(sootMethod);

            if (currMethAbsExits.contains(this))
            {
                currMethAbsExits.remove(this);

                if (summaries.containsKey(sootMethod))
                {
                    // This is a bit hacky, but it will do the trick for now.
                    summaries.get(sootMethod).removeIf(s -> s.exit == this);
                    reachableSummariesPerCallSite.values()
                                                 .forEach(sums -> sums.removeIf(s -> s.exit == this));
                }
            }

            predecessors.values().forEach(pred -> pred.removeSuccessor(this));

            successors.values().forEach(succ -> succ.removePredecessor(this));
        }

        void replaceThisWithNewBlock(AbstractBlock abstractBlock, boolean isMethodEntry, boolean isMethodExit)
        {
            Set<AbstractBlock> abstractBlocksForBB = abstractBlockMap.get(abstractBlock.sootBlock);

            // Add block to the abstraction.
            abstractBlocksForBB.add(abstractBlock);
            blockRuleNameBimap.put(abstractBlock, abstractBlock.getRuleLHS());

            // Update entries/exits if necessary.
            if (isMethodEntry)
                methodAbstractEntries.get(abstractBlock.sootMethod).add(abstractBlock);

            if (isMethodExit)
            {
                methodAbstractExits.get(abstractBlock.sootMethod).add(abstractBlock);
                summaries.get(abstractBlock.sootMethod).add(getNewMethodSummary(abstractBlock));
                methsWithNewSummaries.add(abstractBlock.sootMethod);
            }

            // Update predecessors
            abstractBlock.predecessors.values().forEach(pred -> pred.addSuccessor(abstractBlock));

            // Update successors
            abstractBlock.successors.values().forEach(succ -> succ.addPredecessor(abstractBlock));
        }

        Expr edgeExpr(Unit stmt, Expr post)
        {
            StrongestPostconditionCalculator spCal = new StrongestPostconditionCalculator(exprFactory, spec, sootMethod, returnLocations);
            Expr sp = exprFactory.mkTRUE();

            for (Unit u : sootBlock)
            {
                if (u == stmt)
                {
                    sp = exprFactory.mkAND(spCal.sp(u, sp), spCal.skolemize(post));
                }
                else
                {
                    sp = exprFactory.mkAND(spCal.sp(u, sp), spCal.skolemize(getPredicateForUnit(u)));
                }
            }

            return sp;
        }

        boolean usesCommonVars(Expr pred) {
            return ExprUtils.containsAny(pred, readWriteSet);
        }

        void unconditionalSplit(Expr pred) {
            boolean isMethEntry = methodAbstractEntries.get(sootMethod).contains(this);
            boolean isMethExit = methodAbstractExits.get(sootMethod).contains(this);

            AbstractBlock predAbs = new AbstractBlock(this, pred);
            AbstractBlock notPredAbs = new AbstractBlock(this, exprFactory.mkNEG(pred));

            removeFromAbstraction();

            replaceThisWithNewBlock(predAbs, isMethEntry, isMethExit);
            replaceThisWithNewBlock(notPredAbs, isMethEntry, isMethExit);
        }

        void split(Unit stmt, Expr pred)
        {
            boolean isMethEntry = methodAbstractEntries.get(sootMethod).contains(this);
            boolean isMethExit = methodAbstractExits.get(sootMethod).contains(this);

            AbstractBlock predAbs = this, notPredAbs = this;

            Expr currPred = getPredicateForUnit(stmt);
            Expr newPosPred = ExprUtils.booleanSimplification(exprFactory, exprFactory.mkAND(currPred, pred));
            if (checkSATWithZ3(z3Ctx, edgeExpr(stmt, newPosPred)))
            {
                predAbs = new AbstractBlock(this, stmt, pred);
            }

            Expr newNegPred = ExprUtils.booleanSimplification(exprFactory, exprFactory.mkAND(currPred, exprFactory.mkNEG(pred)));
            if (checkSATWithZ3(z3Ctx, edgeExpr(stmt, newNegPred)))
            {
                notPredAbs = new AbstractBlock(this, stmt, exprFactory.mkNEG(pred));
            }

            removeFromAbstraction();

            if (predAbs == this && notPredAbs == this)
            {
                return;
            }

            // Add new Blocks to the abstraction
            if (predAbs != this)
                replaceThisWithNewBlock(predAbs, isMethEntry, isMethExit);

            if (notPredAbs != this)
                replaceThisWithNewBlock(notPredAbs, isMethEntry, isMethExit);

        }

        void addSuccessor(AbstractBlock succ)
        {
            Validate.notNull(succ);
            successors.put(succ.getRuleLHS(), succ);
        }

        private void getRulesForSummary(List<String> progRules, AbstractionLifter.MethodSummary summary)
        {
            String rule = summary.getBlockRuleNameForSummary(this) + " -> " + String.join(" ", ruleBody);

            if (!methodAbstractExits.get(sootMethod).contains(this))
            {
                Set<AbstractBlock> succsInSummary = successors.values()
                                                              .stream()
                                                              .filter(summary.blocksForSummary::contains)
                                                              .collect(Collectors.toSet());

                if (calledMethods == null || calledMethods.isEmpty())
                {
                    succsInSummary.forEach(succ -> progRules.add(rule + " " + summary.getBlockRuleNameForSummary(succ)));
                }
                else
                {
                    Set<MethodSummary> compatibleSummaries = reachableSummariesPerCallSite.get(this);

                    assert !compatibleSummaries.isEmpty();

                    for (MethodSummary callSummary : compatibleSummaries)
                    {
                        succsInSummary.forEach(succ -> progRules.add(rule + " " + callSummary.getSummaryName() + " " + summary.getBlockRuleNameForSummary(succ)));
                    }
                }
            }
            else
            {
                progRules.add(rule + " " + Terminal.EPSILON_TRANSITION);
            }
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            AbstractBlock that = (AbstractBlock) o;

            if (!sootBlock.equals(that.sootBlock)) return false;
            if (!sootMethod.equals(that.sootMethod)) return false;
            return abstractPredicates.equals(that.abstractPredicates);
        }

        @Override
        public int hashCode()
        {
            int result = sootBlock.hashCode();
            result = 31 * result + sootMethod.hashCode();
            result = 31 * result + abstractPredicates.hashCode();
            return result;
        }
    }

    final class MethodSummary
    {
        SootMethod meth;

        Expr pred;

        AbstractBlock exit;

        int id;

        Set<AbstractBlock> blocksForSummary = new HashSet<>();

        MethodSummary(AbstractBlock exit, int id)
        {
            this.exit = exit;
            this.meth = exit.sootMethod;
            this.pred = exit.getPredicateForUnit(exit.sootBlock.getTail());
            this.id = id;
        }

        String getSummarySuffix()
        {
            return "_summary_" + id;
        }

        String getSummaryName()
        {
            return getMethodRuleName(meth) + getSummarySuffix();
        }

        String getBlockRuleNameForSummary(AbstractBlock b)
        {
            return b.getRuleLHS() + getSummarySuffix();
        }

        void calculateBlockForSummary()
        {
            blocksForSummary.clear();
            Set<AbstractBlock> closedSet = new HashSet<>();
            Queue<AbstractBlock> openSet = new ArrayDeque<>();

            openSet.add(exit);

            while (!openSet.isEmpty())
            {
                AbstractBlock node = openSet.poll();

                if (closedSet.contains(node))
                    continue;

                blocksForSummary.add(node);
                closedSet.add(node);

                node.predecessors.values().forEach(pred ->
                                                   {
                                                       if (!openSet.contains(pred) && !closedSet.contains(pred))
                                                           openSet.add(pred);
                                                   });
            }
        }

        void getRulesForInitStates(List<String> progRules)
        {
            String methSummaryName = this.getSummaryName();
            Set<AbstractBlock> entries = methodAbstractEntries.get(meth)
                                                              .stream()
                                                              .filter(this.blocksForSummary::contains)
                                                              .collect(Collectors.toSet());
            assert !entries.isEmpty();
            entries.forEach(e -> progRules.add(methSummaryName + " -> " + this.getBlockRuleNameForSummary(e)));
        }
    }

    private String removeSummarySuffix(String ruleName)
    {
        int summaryIdx = ruleName.lastIndexOf("_summary_");
        return summaryIdx == -1 ? ruleName : ruleName.substring(0, summaryIdx);
    }

    // BFS traversals over Abstract blocks.
    private void localBFS(SootMethod meth, Consumer<AbstractBlock> action)
    {
        assert methodAbstractEntries.containsKey(meth) : "Invalid Method";

        Set<AbstractBlock> closedSet = new HashSet<>();

        Queue<AbstractBlock> openSet = new ArrayDeque<>();

        openSet.addAll(methodAbstractEntries.get(meth));

        while (!openSet.isEmpty())
        {
            AbstractBlock node = openSet.poll();

            if (closedSet.contains(node))
                continue;

            closedSet.add(node);

            action.accept(node);

            Collection<AbstractBlock> absSucss;
            if (node.calledMethods != null)
            {
                Set<Block> succs = node.successors.values()
                        .stream()
                        .map(s -> s.sootBlock)
                        .collect(Collectors.toSet());
                assert succs.size() == 1;

                absSucss = abstractBlockMap.get(succs.toArray()[0]);
            }
            else
            {
                absSucss = node.successors.values();
            }

            for (AbstractBlock succ : absSucss)
                if (!closedSet.contains(succ) && !openSet.contains(succ))
                    openSet.add(succ);
        }
    }

    private static GrammarCompProxy G_COMP = GrammarCompProxy.getInstance();

    private static int METH_RULE_IDX = 0;

    private Specification spec;

    private Map<Unit, APISpecCall> invokeStmtToSpecCalls;

    private BiMap<SootMethod, String> methRuleNameBimap = Maps.synchronizedBiMap(HashBiMap.create());

    private BiMap<String, SootMethod> methRuleNameBimapInv = Maps.synchronizedBiMap(methRuleNameBimap.inverse());

    private BiMap<AbstractBlock, String> blockRuleNameBimap = Maps.synchronizedBiMap(HashBiMap.create());

    private BiMap<String, AbstractBlock> blockRuleNameBimapInv = Maps.synchronizedBiMap(blockRuleNameBimap.inverse());

    private ConcurrentHashMap<SootMethod, Set<AbstractBlock>> methodAbstractEntries = new ConcurrentHashMap<>();

    private ConcurrentHashMap<SootMethod, Set<AbstractBlock>> methodAbstractExits = new ConcurrentHashMap<>();

    private ConcurrentHashMap<Block, Set<AbstractBlock>> abstractBlockMap = new ConcurrentHashMap<>();

    private CFGrammar.Grammar<String> progGrammar;

    private ExprFactory exprFactory;

    private Context z3Ctx;

    private int nUnroll;

    private Map<GotoStmt, Unit> returnLocations;

    private Map<Unit, Set<Expr>> trackedPredicates = new HashMap<>();

    private ConcurrentHashMap<SootMethod, Set<MethodSummary>> summaries = new ConcurrentHashMap<>();

    private ConcurrentHashMap<SootMethod, Integer> methodSummaryIds = new ConcurrentHashMap<>();

    private Set<SootMethod> methsWithNewSummaries = ConcurrentHashMap.newKeySet();

    private String getMethodRuleName(SootMethod meth)
    {
        if (!methRuleNameBimap.containsKey(meth))
        {
            if (meth == Scene.v().getMainMethod())
            {
                methRuleNameBimap.put(meth, "MAIN");
            }
            else
            {
                methRuleNameBimap.put(meth, "M_" + METH_RULE_IDX++);
            }
        }

        return methRuleNameBimap.get(meth);
    }

    private void addNewAbstractBlock(Block sootBlock)
    {
        AbstractBlock absBlock = new AbstractBlock(sootBlock);
        abstractBlockMap.putIfAbsent(sootBlock, ConcurrentHashMap.newKeySet());

        abstractBlockMap.get(sootBlock).add(absBlock);
        blockRuleNameBimap.put(absBlock, absBlock.getRuleLHS());
    }

    //Each CFG return a list of Strings.
    private void abstractMethodBody(Body methBody)
    {
        BlockGraph blockGraph = new CompleteBlockGraph(methBody);

        // Create initial AbstractBlocks
        blockGraph.getBlocks().forEach(this::addNewAbstractBlock);

        // Build abstractEntries map
        Set<AbstractBlock> abstractEntries = ConcurrentHashMap.newKeySet();
        methodAbstractEntries.put(methBody.getMethod(), abstractEntries);

        for (Block head : blockGraph.getHeads())
        {
            abstractEntries.addAll(abstractBlockMap.get(head));
        }

        Set<AbstractBlock> abstractExits = ConcurrentHashMap.newKeySet();
        methodAbstractExits.put(methBody.getMethod(), abstractExits);

        for (Block exit : blockGraph.getTails())
        {
            Set<AbstractBlock> absExits = abstractBlockMap.get(exit);
            abstractExits.addAll(absExits);

            absExits.forEach(e -> summaries.get(e.sootMethod).add(getNewMethodSummary(e)));
        }
        methsWithNewSummaries.add(methBody.getMethod());
    }

    private List<Unit> collectPathFromCex(ParseTree<String> cex, ArrayList<Unit> units)
    {
        // We only care about internal nodes!
        if (cex instanceof PNode)
        {
            PNode<String> node = (PNode<String>) cex;
            String ruleName = GrammarCompProxy.unquoteRuleName(node.r().leftSide().toString());

            Unit callSuccStmt = null;
            // Ignore dummy non-terminals introduced during NF conversion.
            if (!GrammarCompProxy.isDummyRule(ruleName))
            {
                ruleName = removeSummarySuffix(ruleName);

                if (blockRuleNameBimapInv.containsKey(ruleName))
                {
                    AbstractBlock abstractBlock = blockRuleNameBimapInv.get(ruleName);
                    // Append all statements of current basic block
                    Block sootBlock = abstractBlock.getSootBlock();

                    // Ensure that the cex is properly nested.
                    // This catches bugs where a method that must
                    // be analyzed (VerifierUtil.mustAnalyze returns true)
                    // is not reachable from main.
                    assert abstractBlock.calledMethods == null || node.children().size() > 1;

                    // Remove goto statement after call
                    SootMethod meth = sootBlock.getBody().getMethod();
                    if (meth != Scene.v().getMainMethod() && methodAbstractEntries.get(meth).contains(abstractBlock))
                        callSuccStmt = units.remove(units.size() - 1);

                    for (Unit unit : sootBlock)
                        units.add(unit);
                }
                else if (!methRuleNameBimapInv.containsKey(ruleName))
                {
                    throw new IllegalStateException("Non-tracked rule name " + ruleName + " in AbstractionLifter!");
                }

                List<ParseTree<String>> parseTrees = JavaConversions.seqAsJavaList(node.children());
                if (!parseTrees.isEmpty())
                {
                    for (ParseTree<String> child : parseTrees)
                    {
                        collectPathFromCex(child, units);
                    }
                }

                if (callSuccStmt != null)
                    units.add(callSuccStmt);
            }
        }

        return units;
    }

    Collection<Block> collectBlocksFromCex(ParseTree<String> cex, Collection<Block> blockInCex)
    {
        if (cex instanceof PNode)
        {
            PNode<String> node = (PNode<String>) cex;
            String ruleName = GrammarCompProxy.unquoteRuleName(node.r().leftSide().toString());

            // Ignore dummy non-terminals introduced during NF conversion.
            if (!GrammarCompProxy.isDummyRule(ruleName))
            {
                ruleName = removeSummarySuffix(ruleName);

                if (blockRuleNameBimapInv.containsKey(ruleName))
                {
                    AbstractBlock absBlock = blockRuleNameBimapInv.get(ruleName);

                    blockInCex.add(absBlock.getSootBlock());
                }

                List<ParseTree<String>> parseTrees = JavaConversions.seqAsJavaList(node.children());
                if (!parseTrees.isEmpty())
                {
                    for (ParseTree<String> child : parseTrees)
                        collectBlocksFromCex(child, blockInCex);
                }
            }
        }

        return blockInCex;
    }

    private List<AbstractBlock> collectAbstractBlocksFromCex(ParseTree<String> cex, List<AbstractBlock> blockInCex)
    {
        if (cex instanceof PNode)
        {
            PNode<String> node = (PNode<String>) cex;
            String ruleName = GrammarCompProxy.unquoteRuleName(node.r().leftSide().toString());

            // Ignore dummy non-terminals introduced during NF conversion.
            if (!GrammarCompProxy.isDummyRule(ruleName))
            {
                if (blockRuleNameBimapInv.containsKey(ruleName))
                {
                    AbstractBlock absBlock = blockRuleNameBimapInv.get(ruleName);

                    blockInCex.add(absBlock);
                }

                List<ParseTree<String>> parseTrees = JavaConversions.seqAsJavaList(node.children());
                if (!parseTrees.isEmpty())
                {
                    for (ParseTree<String> child : parseTrees)
                        collectAbstractBlocksFromCex(child, blockInCex);
                }
            }
        }

        return blockInCex;
    }

    private void splitAbstractBlocks(Set<Block> blocksInCex, Map<Unit, Set<Expr>> newPredicates)
    {
        Instant start = null;

        if (CmdLine.PROFILING_MODE)
            start = Instant.now();

        for(Block b : blocksInCex) {
            StrongestPostconditionCalculator spCalc = new StrongestPostconditionCalculator(exprFactory, spec, b.getBody().getMethod(), returnLocations);
            Expr bbEnc = exprFactory.mkTRUE();

            for (Unit u : b)
                bbEnc = ExprUtils.booleanSimplification(exprFactory, exprFactory.mkAND(bbEnc, spCalc.sp(u, exprFactory.mkTRUE())));

            Set<Expr> varsInBB = ExprUtils.getFreeVars(bbEnc).stream().map(e -> AnalysisUtils.cleanExpr(e, exprFactory)).collect(Collectors.toSet());
            Unit tail = b.getTail();

            if(!newPredicates.containsKey(tail)) {
                continue;
            }

            //all abstract blocks have the same read/write set

            Set<Expr> preds = new HashSet<>();//newPredicates.get(tail).stream().filter(pred -> !first.usesCommonVars(pred)).collect(Collectors.toSet());

            for (Expr newPred : newPredicates.get(tail))
            {
                if (Collections.disjoint(varsInBB, ExprUtils.getFreeVars(newPred)))
                    preds.add(newPred);
            }

            if(preds.isEmpty()) {
                continue;
            }

            for(Unit u : b) {
                if(newPredicates.containsKey(u)) {
                    newPredicates.get(u).removeAll(preds);
                }
            }

            for(Expr pred : preds) {
                Set<AbstractBlock> workset = new HashSet<>(abstractBlockMap.get(b));
                for(AbstractBlock abstractBlock : workset) {
                    abstractBlock.unconditionalSplit(pred);
                }
            }
        }

        for (Block b : blocksInCex)
        {
            for (Unit u : b)
            {
                if (newPredicates.containsKey(u))
                {
                    for (Expr e : newPredicates.get(u))
                    {
                        Set<AbstractBlock> workSet = new HashSet<>(abstractBlockMap.get(b));
                        for (AbstractBlock abstractBlock : workSet)
                            abstractBlock.split(u, e);
                    }
                }
            }
        }

        if (CmdLine.PROFILING_MODE)
        {
            Instant end = Instant.now();
            System.out.println("splitAbstractBlocks: " + Duration.between(start, end));
        }
    }

    private void removeInfeasibleAbstractEdges(Set<Block> blocksInCex, Map<Unit, Set<Expr>> interpolants)
    {
        Instant start = null;

        if (CmdLine.PROFILING_MODE)
            start = Instant.now();

        blocksInCex.stream()
                   .filter(b -> {
                       for (Unit u : b)
                           if (interpolants.containsKey(u))
                               return true;
                        return false;
                   })
                   .forEach(b ->

                        abstractBlockMap.get(b)
                                        .parallelStream()
                                        .forEach(block -> block.removeInfeasibleEdges())

                    );

        if (CmdLine.PROFILING_MODE)
        {
            Instant end = Instant.now();
            System.out.println("removeInfeasibleAbstractEdges: " + Duration.between(start, end));
        }
    }

    private void removeLocalDeadNodes()
    {
        Instant start = null;

        if (CmdLine.PROFILING_MODE)
            start = Instant.now();

        Set<AbstractBlock> deadNodes;

        int totalDead = 0;

        do
        {
            deadNodes = abstractBlockMap.values().stream()
                                        .flatMap(Set::stream)
                    .filter(b -> !methodAbstractExits.get(b.sootMethod).contains(b) && b.successors.size() == 0)
                                        .collect(Collectors.toSet());

            deadNodes.addAll(abstractBlockMap.values().stream()
                                             .flatMap(Set::stream)
                                             .filter(b -> b.predecessors.size() == 0 &&
                                                          !methodAbstractEntries.get(b.sootMethod).contains(b))
                                             .collect(Collectors.toSet()));

            deadNodes.forEach(AbstractBlock::removeFromAbstraction);

            totalDead += deadNodes.size();
        } while (deadNodes.size() != 0);

        if (CmdLine.PROFILING_MODE)
        {
            Instant end = Instant.now();
            System.out.println("removeLocalDeadNodes: " + Duration.between(start, end));
            System.out.println("total dead: " + totalDead);
        }
    }

    private void removeDeadNodes()
    {
        Instant start = null;

        if (CmdLine.PROFILING_MODE)
            start = Instant.now();

        Set<AbstractBlock> deadNodes;

        int totalDead = 0;

        do
        {
            deadNodes = abstractBlockMap.values().stream()
                                        .flatMap(Set::stream)
                                        .filter(b -> !methodAbstractExits.get(b.sootMethod).contains(b) &&
                                                     (b.successors.size() == 0 || (b.calledMethods != null &&
                                                                                   reachableSummariesPerCallSite.get(b).isEmpty())))
                                        .collect(Collectors.toSet());

            Set<AbstractBlock> reachableMethodSummaries = reachableSummariesPerCallSite.values()
                                                                                       .stream()
                                                                                       .flatMap(Set::stream)
                                                                                       .map(s -> s.exit)
                                                                                       .collect(Collectors.toSet());

            deadNodes.addAll(methodAbstractExits.values()
                                                .stream()
                                                .flatMap(Set::stream)
                                                .filter(b -> b.sootMethod != Scene.v().getMainMethod() &&
                                                             !reachableMethodSummaries.contains(b))
                                                .collect(Collectors.toSet()));

            deadNodes.addAll(abstractBlockMap.values().stream()
                                             .flatMap(Set::stream)
                                             .filter(b -> b.predecessors.size() == 0 &&
                                                          !methodAbstractEntries.get(b.sootMethod).contains(b))
                                             .collect(Collectors.toSet()));

            deadNodes.forEach(AbstractBlock::removeFromAbstraction);

            totalDead += deadNodes.size();
        } while (deadNodes.size() != 0);

        if (CmdLine.PROFILING_MODE)
        {
            Instant end = Instant.now();
            System.out.println("removeDeadNodes: " + Duration.between(start, end));
            System.out.println("total dead: " + totalDead);
        }
    }

    private ConcurrentHashMap<AbstractBlock, Set<MethodSummary>> reachableSummariesPerCallSite = new ConcurrentHashMap<>();

    private void calculateReachableSummariesForCallSites()
    {
        Instant start = null;

        if (CmdLine.PROFILING_MODE)
            start = Instant.now();

        abstractBlockMap.values()
                        .stream()
                        .flatMap(Set::stream)
                        .filter(b -> b.calledMethods != null &&
                                     b.successors.size() > 0 &&
                                     (!Collections.disjoint(b.calledMethods, methsWithNewSummaries) ||
                                      !reachableSummariesPerCallSite.containsKey(b)))
                        .parallel()
                        .forEach(b -> reachableSummariesPerCallSite.put(b, getCompatibleMethodsForCall(b)));

        if (CmdLine.PROFILING_MODE)
        {
            Instant end = Instant.now();
            System.out.println("calculateReachableSummariesForCallSites: " + Duration.between(start, end));
        }
    }

    private Set<MethodSummary> getCompatibleMethodsForCall(AbstractBlock b)
    {
        // TODO: Might need to memoize these!
        assert b.calledMethods != null;

        Set<MethodSummary> rv = ConcurrentHashMap.newKeySet();

        for (SootMethod calledMethod : b.calledMethods)
        {
            rv.addAll(summaries.get(calledMethod).parallelStream().filter(summary -> {
                Unit tail = b.sootBlock.getTail();
                Unit callStmt = b.sootBlock.getPredOf(tail);

                Expr predAtReturn = b.getPredicateForUnit(tail);

                Expr summaryPred = summary.pred;
                StrongestPostconditionCalculator spCalc = new StrongestPostconditionCalculator(exprFactory, spec, b.sootMethod, returnLocations);

                spCalc.createNewContext(summary.meth);

                Expr preCallPred = spCalc.skolemize(b.getPredicateForUnit(callStmt));
                Expr skolemizedExit = spCalc.skolemize(summaryPred);

                Expr summaryFormula = exprFactory.mkAND(spCalc.sp(tail, preCallPred), skolemizedExit, spCalc.skolemize(predAtReturn));
                return ExprUtils.checkSATWithZ3(z3Ctx, ExprUtils.booleanSimplification(exprFactory, summaryFormula));
            }).collect(Collectors.toSet()));
        }

        return rv;
    }

    private MethodSummary getNewMethodSummary(AbstractBlock exit)
    {
        return new MethodSummary(exit, methodSummaryIds.computeIfPresent(exit.sootMethod, (m, i) -> i + 1));
    }

    private void buildAbstraction()
    {
        Instant start = null;

        if (CmdLine.PROFILING_MODE)
            start = Instant.now();

        List<String> progRules = new ArrayList<>();

        // 3. Construct CFG for each method
        for (SootMethod meth : summaries.keySet())
        {
            for (MethodSummary summary : summaries.get(meth))
            {
                summary.calculateBlockForSummary();

                for (AbstractBlock b : summary.blocksForSummary)
                    b.getRulesForSummary(progRules, summary);

                summary.getRulesForInitStates(progRules);
            }
        }

        SootMethod mainMethod = Scene.v().getMainMethod();

        for (MethodSummary mainSummary : summaries.get(mainMethod))
            progRules.add(0, "MAIN -> " + mainSummary.getSummaryName());

        progGrammar = G_COMP.createBNFGrammar(progRules).cfGrammar();

        if (CmdLine.PROFILING_MODE)
        {
            Instant end = Instant.now();
            System.out.println("buildAbstraction: " + Duration.between(start, end));
        }
    }

    protected void internalTransform(String s, Map<String, String> map)
    {
        CallGraph cg = Scene.v().getCallGraph();
        DirectedCallGraph dcg = new DirectedCallGraph(cg, (m) -> (mustAnalyze(spec.getApiClasses(), m)), Collections.singletonList(Scene.v().getMainMethod()).iterator(), false);
        StronglyConnectedComponentsFast<SootMethod> sccs = new StronglyConnectedComponentsFast<>(dcg);

        sccs.getComponents()
            .forEach(scc -> scc.forEach(m -> {
                methodSummaryIds.put(m, 0);
                summaries.put(m, ConcurrentHashMap.newKeySet());
            }));
        sccs.getComponents()
            .forEach(scc -> scc.forEach(m -> abstractMethodBody(m.getActiveBody())));

        // Link abstract blocks
        abstractBlockMap.values().forEach(bs -> bs.forEach(AbstractBlock::constructInitAbstractEdges));

        calculateReachableSummariesForCallSites();
        buildAbstraction();

        G_COMP.initEquivCtxForGrammar(progGrammar, nUnroll);
    }


    public AbstractionLifter(Specification spec, ExprFactory exprFactory, Context z3Ctx, Map<Unit, APISpecCall> invokeStmtToSpecCalls, int nUnroll, Map<GotoStmt, Unit> returnLocations)
    {
        this.spec = spec;
        this.invokeStmtToSpecCalls = invokeStmtToSpecCalls;
        this.exprFactory = exprFactory;
        this.z3Ctx = z3Ctx;
        this.nUnroll = nUnroll;
        this.returnLocations = returnLocations;
    }

    CFGrammar.Grammar<String> getCurrAbstraction()
    {
        return progGrammar;
    }

    void refineAbstraction(Set<ParseTree<String>> cexes, Map<Unit, Set<Expr>> interpolants, Map<Unit, Map<Expr, Map<Unit, Expr>>> interpImpl)
    {
        System.out.print("Refining... ");

        for (Unit u : interpolants.keySet())
        {
            if (!trackedPredicates.containsKey(u))
                trackedPredicates.put(u, new HashSet<>());

            Set<Expr> interpolnatsForUnit = interpolants.get(u);

            Expr[] interpsAsArray = interpolnatsForUnit.toArray(new Expr[interpolnatsForUnit.size()]);

            Expr trueExpr = exprFactory.mkTRUE();
            Expr falseExpr = exprFactory.mkFALSE();

            outer:
            for (int i = 0, e = interpsAsArray.length; i < e; ++i)
            {
                Expr i1 = interpsAsArray[i];

                for (Expr trackedPred : trackedPredicates.get(u))
                {
                    if (ExprUtils.areEquivalent(i1, trackedPred, exprFactory, z3Ctx))
                    {
                        interpolnatsForUnit.remove(i1);
                        continue outer;
                    }
                }

                if (ExprUtils.areEquivalent(i1, trueExpr, exprFactory, z3Ctx) ||
                    ExprUtils.areEquivalent(i1, falseExpr, exprFactory, z3Ctx))
                {
                    interpolnatsForUnit.remove(i1);
                    continue;
                }

                for (int k = i + 1; k < e; ++k)
                {
                    Expr i2 = interpsAsArray[k];
                    if (ExprUtils.areEquivalent(i1, exprFactory.mkNEG(i2), exprFactory, z3Ctx) ||
                        ExprUtils.areEquivalent(i1, i2, exprFactory, z3Ctx))
                    {
                        interpolnatsForUnit.remove(i1);
                        break;
                    }
                }
            }

            trackedPredicates.get(u).addAll(interpolnatsForUnit);
            trackedPredicates.get(u).addAll(interpolnatsForUnit.stream()
                    .map(e -> exprFactory.mkNEG(e)).collect(Collectors.toSet()));
        }

        Set<Block> blocksInCexes = new HashSet<>();
        cexes.forEach(cex -> blocksInCexes.addAll(collectBlocksFromCex(cex, new HashSet<>())));

        Map<ParseTree<String>, List<Block>> blocksInCexOrdered = new HashMap<>();

        if (CmdLine.DEBUG_MODE)
        {
            cexes.forEach(cex -> blocksInCexOrdered.put(cex, (List<Block>) collectBlocksFromCex(cex, new ArrayList<>())));
        }

        methsWithNewSummaries.clear();
        splitAbstractBlocks(blocksInCexes, interpolants);
        removeLocalDeadNodes();
        removeInfeasibleAbstractEdges(blocksInCexes, interpolants);
        removeLocalDeadNodes();
        calculateReachableSummariesForCallSites();
        removeDeadNodes();
        buildAbstraction();

        System.out.println("done!");
    }

    List<Unit> cexToJimplePath(ParseTree<String> cex)
    {
        return collectPathFromCex(cex, new ArrayList<>());
    }

    List<Expr> isCexFeasible(List<Unit> cex)
    {
        List<Expr> traceFormula = new ArrayList<>();
        StrongestPostconditionCalculator spCal = new StrongestPostconditionCalculator(exprFactory, spec, traceFormula, returnLocations);

        boolean isUnsat = checkUNSATWithZ3(z3Ctx, spCal.sp(cex));
        return isUnsat ? traceFormula : null;
    }

    String abstractionPrettyPrint()
    {
        StringBuilder str = new StringBuilder();
        for (SootMethod meth : methodAbstractEntries.keySet())
        {
            for (MethodSummary s : summaries.get(meth))
            {
                appendAbstractionForSummary(s, str);
            }
        }

        return str.toString();
    }

    private void appendAbstractionForSummary(MethodSummary s, StringBuilder str)
    {
        List<String> summaryRules = new ArrayList<>();

        s.getRulesForInitStates(summaryRules);

        localBFS(s.meth, b -> {
            if (s.blocksForSummary.contains(b))
                b.getRulesForSummary(summaryRules, s);
        });

        str.append("Method: ")
           .append(s.meth.getSignature())
           .append(" ")
           .append(s.getSummaryName())
           .append("\n");

        summaryRules.forEach(r -> str.append(r).append("\n"));
        str.append("\n");
    }

    public int getTotalPredicates() {
        HashSet<Expr> predicates = new HashSet<>();

        for(Set<Expr> unitPreds : trackedPredicates.values()) {
            predicates.addAll(unitPreds);
        }

        //trackedPredicates contains a predicate and its negation
        return predicates.size() / 2;
    }

    public int[] getPredicateStats() {
        int sum = 0;
        int numUnits = 0;
        int[] result = new int[3];

        if(trackedPredicates.isEmpty()) {
            return result;
        }

        int min = trackedPredicates.values().iterator().next().size();
        int max = min;

        for(Set<Expr> unitPreds : trackedPredicates.values()) {
            sum += unitPreds.size();

            if(unitPreds.size() != 0) {
                numUnits += 1;
            }

            if(unitPreds.size() < min) {
                min = unitPreds.size();
            }
            if(unitPreds.size() > max) {
                max = unitPreds.size();
            }
        }

        result[0] = min / 2;
        result[1] = (sum / 2) / numUnits;
        result[2] = max / 2;

        return result;
    }

    public int getNumUnitsWithPredicates() {
        int numUnits = 0;

        for(Set<Expr> unitPreds : trackedPredicates.values()) {
            if(unitPreds.size() != 0) {
                numUnits += 1;
            }
        }

        return numUnits;
    }

    public int getNumUnits() {
        int total = 0;
        for(Block b : abstractBlockMap.keySet()) {
            total += b.getBody().getUnits().size();
        }

        return total;
    }

    public int getPCFASize() {
        int total = 0;

        for(Set<AbstractBlock> blocks : abstractBlockMap.values()) {
            total += blocks.size();
        }

        return total;
    }

    public int getNumMethods() {
        return methodAbstractEntries.size();
    }
}
