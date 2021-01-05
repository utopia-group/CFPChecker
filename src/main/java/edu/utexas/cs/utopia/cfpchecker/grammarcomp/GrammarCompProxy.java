package edu.utexas.cs.utopia.cfpchecker.grammarcomp;

import edu.utexas.cs.utopia.cfpchecker.CmdLine;
import edu.utexas.cs.utopia.cfpchecker.verifier.PerfStats;
import grammarcomp.equivalence.EquivalenceChecker;
import grammarcomp.equivalence.StudentGrammarEquivalenceChecker;
import grammarcomp.grammar.*;
import grammarcomp.parsing.*;
import scala.Option;
import scala.Tuple2;
import scala.collection.JavaConversions;
import scala.collection.immutable.$colon$colon;
import scala.collection.immutable.List;
import scala.collection.immutable.Nil$;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.lang.System;

/**
 * Created by kferles on 5/28/18.
 */
public class GrammarCompProxy {
    public static String parseTreePretty(ParseTree<String> tree) {
        StringBuilder rv = new StringBuilder();

        parseTreePretty(tree, rv, 1);

        return rv.toString();
    }

    private static void parseTreePretty(ParseTree<String> tree, StringBuilder treeStr, int indent) {
        StringBuilder indentation = new StringBuilder();

        for (int i = 0; i < indent; ++i)
            indentation.append("    ");

        treeStr.append(indentation);

        if (tree instanceof PNode) {
            PNode<String> internalNode = (PNode<String>) tree;

            treeStr.append(internalNode.r().leftSide().toString())
                    .append("\n");

            for (ParseTree<String> child : JavaConversions.seqAsJavaList(internalNode.children())) {
                parseTreePretty(child, treeStr, indent + 1);
            }
        } else {
            PLeaf<String> leaf = (PLeaf<String>) tree;
            treeStr.append(leaf.toString())
                    .append("\n");
        }
    }

    private EquivalenceCheckingContext eqCtx;
    // The arguments come from the Scala default constructors.
    private GlobalContext gctx = new GlobalContext(false, false, true,
            "cfg-checker.stats", "cfgchecker.log");
    private EnumerationContext enumctx = new EnumerationContext(0, false, 10, Integer.MAX_VALUE);
    private ParseContext parsectx = new ParseContext(false, "./bin/", "./lib/antlr-4.7.1-complete.jar");

    private static GrammarCompProxy ourInstance = new GrammarCompProxy();

    public static GrammarCompProxy getInstance() {
        return ourInstance;
    }

    private GrammarCompProxy() {
    }

    private <T> List<T> emptyList() {
        return (List<T>) Nil$.MODULE$;
    }

    private <T> List<T> concat(T t, List<T> l) {
        return new $colon$colon<>(t, l);
    }

    public EBNFGrammar.BNFGrammar<String> createBNFGrammar(java.util.List<String> rules) {
        List<String> gramRules = emptyList();

        ListIterator<String> it = rules.listIterator(rules.size());

        while (it.hasPrevious()) {
            gramRules = concat(it.previous(), gramRules);
        }

        GrammarParser parser = new GrammarParser();
        Tuple2<Option<EBNFGrammar.BNFGrammar<String>>, String> grammarOpt = parser.parseGrammar(gramRules);

        String errStr = grammarOpt._2();
        if (!errStr.isEmpty())
            throw new RuntimeException("Error while creating BNF grammar. " + errStr);

        return grammarOpt._1().get();
    }

    private boolean ruleMatches(CFGrammar.Rule<String> r, List<ParseTree<String>> rhsTrees, Set<String> nullables)
    {
        List<CFGrammar.Symbol<String>> rhs = r.rightSide();
        int rhsSize = rhs.size();
        int treesSz = rhsTrees.size();

        if (treesSz == 1 && rhsTrees.apply(0) instanceof PLeaf)
        {
            // We are looking to match a single terminal.
            PLeaf<String> trgTree = (PLeaf<String>)rhsTrees.apply(0);
            CFGrammar.Terminal<String> trg = trgTree.t();

            if (rhsSize == 0)
                return false;

            CFGrammar.Symbol<String> first = rhs.apply(0);
            if (!first.toString().equals(trg.toString())) return false;

            for (int i = 1; i < rhsSize; ++i) {
                if (!nullables.contains(rhs.apply(i).toString())) return false;
            }

            return true;
        }
        else
        {
            // TODO: check if this the case for rules with 3 symbols in the RHS.
            // Otherwise look for an exact match.
            if (rhsSize != rhsTrees.size())
                return false;

            for (int i = 0; i < rhsSize; ++i)
            {
                ParseTree<String> iTree = rhsTrees.apply(i);
                String rootStr = iTree instanceof PNode ? ((PNode<String>)iTree).r().leftSide().toString() : ((PLeaf<String>)iTree).t().toString();

                if (!rootStr.equals(rhs.apply(i).toString())) return false;
            }

            return true;
        }
    }

    private CFGrammar.Nonterminal getNonterminal(CFGrammar.Grammar<String> g, String nonTerminalName)
    {
        java.util.List<CFGrammar.Nonterminal> nonTerms = JavaConversions.seqAsJavaList(g.nonTerminals()).stream().filter(n -> n.toString().equals(nonTerminalName)).collect(Collectors.toList());

        return nonTerms.isEmpty() ? null : nonTerms.get(0);
    }

    private CFGrammar.Terminal<String> getTerminal(CFGrammar.Grammar<String> g, String terminalName)
    {
        java.util.List<CFGrammar.Terminal<String>> term = JavaConversions.setAsJavaSet(g.terminals()).stream().filter(n -> n.toString().equals(terminalName)).collect(Collectors.toList());

        assert term.size() == 1;
        return term.get(0);
    }

    private boolean isTerminal(CFGrammar.Grammar<String> g, CFGrammar.Symbol<String> sym)
    {
        return !JavaConversions.setAsJavaSet(g.terminals())
                .stream()
                .filter(t -> t.toString().equals(sym.toString()))
                .collect(Collectors.toSet())
                .isEmpty();
    }

    private boolean isTreeValid(ParseTree<String> t, Set<CFGrammar.Rule<String>> rules)
    {
        if (t instanceof PLeaf) return true;

        PNode<String> node = (PNode<String>) t;
        if (!rules.contains(node.r()))
            return false;

        for (ParseTree<String> child : JavaConversions.seqAsJavaList(node.children()))
            if (!isTreeValid(child, rules)) return false;
        return true;
    }

    private int treeSize(ParseTree<String> t)
    {
        if (t instanceof PLeaf)
        {
            return 1;
        }

        PNode<String> node = (PNode<String>) t;
        List<ParseTree<String>> children = node.children();

        int sz = 1;
        for (ParseTree<String> child : JavaConversions.seqAsJavaList(children))
            sz += treeSize(child);

        return sz;
    }

    private Set<ParseTree<String>> getNullTrees(CFGrammar.Rule<String> r, CFGrammar.Grammar<String> g, Set<String> nullables, Set<CFGrammar.Rule<String>> visitedRules, boolean searchInterProc) {
        List<CFGrammar.Symbol<String>> rhs = r.rightSide();

        if (rhs.size() == 0)
            return Collections.singleton(new PNode<>(r, emptyList()));

        if (visitedRules.contains(r))
            return Collections.emptySet();

        visitedRules.add(r);

        java.util.List<String> rhsSymbols = new ArrayList<>();

        for (int i = 0; i < rhs.size(); ++i)
        {
            rhsSymbols.add(rhs.apply(i).toString());
        }

        if (!nullables.containsAll(rhsSymbols))
            return Collections.emptySet();

        CFGrammar.Nonterminal lhs = r.leftSide();
        if (rhsSymbols.size() == 1)
        {
            return getNullTrees(getNonterminal(g, rhsSymbols.get(0)), g, nullables, visitedRules, searchInterProc).stream()
                                                                                                                  .map(t -> new PNode<>(r, concat(t, emptyList()))).collect(Collectors.toSet());
        }
        else if (rhsSymbols.size() == 2 && searchInterProc)
        {
            CFGrammar.Nonterminal nt1 = getNonterminal(g, rhsSymbols.get(0));
            Set<ParseTree<String>> leftTrees = nullTreesForRecursiveMethods.containsKey(nt1) ? nullTreesForRecursiveMethods.get(nt1) : getNullTrees(nt1, g, nullables, new HashSet<>(visitedRules), true);
            Set<ParseTree<String>> righTrees = getNullTrees(getNonterminal(g, rhsSymbols.get(1)), g, nullables, new HashSet<>(visitedRules), true);

            Set<ParseTree<String>> crossProduct = new HashSet<>();
            for (ParseTree<String> left : leftTrees)
            {
                for (ParseTree<String> right : righTrees)
                {
                    crossProduct.add(new PNode<>(r, concat(left, concat(right, emptyList()))));
                }
            }

            return crossProduct;
        }

        return Collections.emptySet();
    }

    private static int MAX_NUM_TREES = 5;

    private Set<ParseTree<String>> getNullTrees(CFGrammar.Nonterminal src, CFGrammar.Grammar<String> g, Set<String> nullables, Set<CFGrammar.Rule<String>> visitedRules, boolean searchInterProc)
    {
        if (memoizedNullTrees.containsKey(src))
        {
            Set<ParseTree<String>> trees = memoizedNullTrees.get(src);
            assert trees.size() > 0;
            return trees;
        }

        Set<ParseTree<String>> nullTrees = new HashSet<>();
        Map<CFGrammar.Nonterminal, List<CFGrammar.Rule<String>>> rulesMap = JavaConversions.mapAsJavaMap(g.nontermToRules());

        for (CFGrammar.Rule<String> r : getGrammarRulesSorted(rulesMap.get(src)))
        {
            nullTrees.addAll(getNullTrees(r, g, nullables, visitedRules, searchInterProc));
            if (!nullTrees.isEmpty()) break;
        }

        // Keep smallest top-N.
        nullTrees = nullTrees.stream()
                             .sorted(Comparator.comparingInt(this::treeSize))
                             .limit(MAX_NUM_TREES)
                             .collect(Collectors.toSet());

        if (searchInterProc && !nullTrees.isEmpty())
            memoizedNullTrees.putIfAbsent(src, nullTrees);

        return nullTrees;
    }

    private java.util.List<CFGrammar.Rule<String>> getGrammarRulesSorted(List<CFGrammar.Rule<String>> rules)
    {
        return JavaConversions.seqAsJavaList(rules)
                              .stream()
                              .sorted(Collections.reverseOrder(Comparator.comparing(r -> r.leftSide().toString())))
                              .collect(Collectors.toList());
    }

    private Set<ParseTree<String>> completeTree(CFGrammar.Nonterminal src, ParseTree<String> partialTree, CFGrammar.Grammar<String> g, Set<String> nullables, Set<CFGrammar.Rule<String>> visitedRules)
    {
        if (memoizedCompleteTreeQueries.containsKey(src))
        {
            ConcurrentHashMap<ParseTree<String>, Set<ParseTree<String>>> memoizedTrees = memoizedCompleteTreeQueries.get(src);
            if (memoizedTrees.containsKey(partialTree))
            {
                Set<ParseTree<String>> trees = memoizedTrees.get(partialTree);
                assert trees.size() > 0;
                return trees;
            }
        }

        assert partialTree instanceof PNode;

        PNode<String> trgRoot = (PNode<String>) partialTree;

        CFGrammar.Nonterminal trgLhs = trgRoot.r().leftSide();
        assert src.equals(trgLhs) || CFGrammar.reach(g, src, trgLhs);

        if (src.equals(trgLhs))
        {
            return Collections.singleton(partialTree);
        }

        Set<ParseTree<String>> rv = new HashSet<>();
        Map<CFGrammar.Nonterminal, List<CFGrammar.Rule<String>>> ruleMap = JavaConversions.mapAsJavaMap(g.nontermToRules());

        for (CFGrammar.Rule<String> r : getGrammarRulesSorted(ruleMap.get(src)))
        {
            if (visitedRules.contains(r))
                continue;

            visitedRules.add(r);

            List<CFGrammar.Symbol<String>> rhs = r.rightSide();
            if (rhs.size() == 1)
            {
                CFGrammar.Nonterminal nonTerm = getNonterminal(g, rhs.apply(0).toString());
                if (nonTerm != null && (CFGrammar.reach(g, nonTerm, trgLhs) || trgLhs.equals(nonTerm)))
                {
                    rv.addAll(completeTree(nonTerm, partialTree, g, nullables, visitedRules).stream().map(t -> new PNode<>(r, concat(t, emptyList()))).collect(Collectors.toSet()));
                }
            }
            else if (rhs.size() == 2)
            {
                CFGrammar.Nonterminal t1 = getNonterminal(g, rhs.apply(0).toString()), t2 = getNonterminal(g, rhs.apply(1).toString());

                if (t1 == null || t2 == null)
                    continue;

                if ((CFGrammar.reach(g, t1, trgLhs) || trgLhs.equals(t1)) && nullables.contains(t2.toString()))
                {
                    for (ParseTree<String> t1Tree : completeTree(t1, partialTree, g, nullables, new HashSet<>(visitedRules)))
                        for (ParseTree<String> t2Tree : getNullTrees(t2, g, nullables, new HashSet<>(), true))
                            rv.add(new PNode<>(r, concat(t1Tree, concat(t2Tree, emptyList()))));
                }

                if ((CFGrammar.reach(g, t2, trgLhs) || trgLhs.equals(t2)) && nullables.contains(t1.toString()))
                {
                    for (ParseTree<String> t1Tree : getNullTrees(t1, g, nullables, new HashSet<>(), true))
                        for (ParseTree<String> t2Tree : completeTree(t2, partialTree, g, nullables, new HashSet<>(visitedRules)))
                            rv.add(new PNode<>(r, concat(t1Tree, concat(t2Tree, emptyList()))));
                }

            }

            if (!rv.isEmpty()) break;
        }

        // Keep smallest top-N.
        rv = rv.stream()
               .sorted(Comparator.comparingInt(this::treeSize))
               .limit(MAX_NUM_TREES)
               .collect(Collectors.toSet());

        if (rv.size() > 0)
        {
            memoizedCompleteTreeQueries.putIfAbsent(src, new ConcurrentHashMap<>());
            memoizedCompleteTreeQueries.get(src).putIfAbsent(partialTree, rv);
        }

        return rv;
    }

    private Set<ParseTree<String>> getParseTrees(CFGrammar.Nonterminal src, List<ParseTree<String>> rhsTrees, CFGrammar.Grammar<String> g, Set<String> nullables)
    {
        Set<ParseTree<String>> trees = new HashSet<>();
        Map<CFGrammar.Nonterminal, List<CFGrammar.Rule<String>>> gRules = JavaConversions.mapAsJavaMap(g.nontermToRules());

        Set<CFGrammar.Rule<String>> matchingRules = gRules.values()
                                                          .stream()
                                                          .flatMap(l -> JavaConversions.seqAsJavaList(l).stream())
                                                          .filter(r -> ruleMatches(r, rhsTrees, nullables) && (CFGrammar.reach(g, src, r.leftSide()) || src.equals(r.leftSide())))
                                                          .collect(Collectors.toSet());

        Stack<PNode<String>> pendingTrees = new Stack<>();
        for (CFGrammar.Rule<String> r : matchingRules)
        {
            List<CFGrammar.Symbol<String>> rhs = r.rightSide();
            if (rhs.size() == 1)
            {
                assert rhsTrees.size() == 1;
                // Just a terminal (probably unreachable)
                pendingTrees.add(new PNode<>(r, rhsTrees));
            }
            else if (rhs.size() == 2)
            {
                if (rhsTrees.size() != 2)
                {
                    assert rhsTrees.size() == 1;
                    CFGrammar.Nonterminal nullNonTerm = getNonterminal(g, rhs.apply(1).toString());
                    getNullTrees(nullNonTerm, g, nullables, new HashSet<>(), true)
                            .forEach(t -> pendingTrees.add(new PNode<>(r, concat(rhsTrees.apply(0), concat(t, emptyList())))));
                }
                else
                {
                    pendingTrees.add(new PNode<>(r, rhsTrees));
                }
            }
            else
            {
                assert rhs.size() == 3;
                if (rhsTrees.size() != 3)
                {
                    assert rhsTrees.size() == 1;
                    CFGrammar.Nonterminal nullNonTerm1 = getNonterminal(g, rhs.apply(1).toString());
                    CFGrammar.Nonterminal nullNonTerm2 = getNonterminal(g, rhs.apply(2).toString());

                    Set<ParseTree<String>> nullTrees1 = getNullTrees(nullNonTerm1, g, nullables, new HashSet<>(), true);
                    Set<ParseTree<String>> nullTrees2 = getNullTrees(nullNonTerm2, g, nullables, new HashSet<>(), true);

                    for (ParseTree<String> t1 : nullTrees1) {
                        for (ParseTree<String> t2 : nullTrees2) {
                            pendingTrees.add(new PNode<>(r, concat(rhsTrees.apply(0), concat(t1, concat(t2, emptyList())))));
                        }
                    }
                }
                else
                {
                    pendingTrees.add(new PNode<>(r, rhsTrees));
                }
            }
        }

        for (ParseTree<String> t : pendingTrees)
        {
            trees.addAll(completeTree(src, t, g, nullables, new HashSet<>()));
            if (!trees.isEmpty()) break;
        }

        return trees;
    }

    private Set<ParseTree<String>> mapTreeToGrammar(ParseTree<String> tree, CFGrammar.Grammar<String> g, Set<String> nullables)
    {
        if (tree instanceof PLeaf)
            return Collections.singleton(tree);

        assert tree instanceof PNode;

        PNode<String> node = (PNode<String>) tree;
        CFGrammar.Rule<String> r = node.r();

        java.util.List<ParseTree<String>> children = JavaConversions.seqAsJavaList(node.children());

        int nChildren = children.size();

        if (nChildren == 1)
        {
            ParseTree<String> child = children.get(0);
            assert child instanceof PLeaf;

            return getParseTrees(r.leftSide(), concat(child, emptyList()), g, nullables);
        }
        // Can simplify the logic below.
        // We need a method that given a List<Set<ParseTree<String>>>,
        // it returns a Set<List<ParseTree<String>>> that contains the cartesian
        // product of the input. (Guava seems to have such a functionality).
        else if (nChildren == 2)
        {
            Set<ParseTree<String>> s1Trees = mapTreeToGrammar(children.get(0), g, nullables);
            Set<ParseTree<String>> s2Trees = mapTreeToGrammar(children.get(1), g, nullables);

            Set<ParseTree<String>> rv = new HashSet<>();
            for (ParseTree<String> t1 : s1Trees)
                for (ParseTree<String> t2 : s2Trees)
                    rv.addAll(getParseTrees(r.leftSide(), concat(t1, concat(t2, emptyList())), g, nullables));

            return rv;
        }

        assert false : tree.toString();
        return null;
    }

    private ConcurrentHashMap<CFGrammar.Nonterminal, Set<ParseTree<String>>> nullTreesForRecursiveMethods = new ConcurrentHashMap<>();

    private ConcurrentHashMap<CFGrammar.Nonterminal, Set<ParseTree<String>>> memoizedNullTrees = new ConcurrentHashMap<>();

    private ConcurrentHashMap<CFGrammar.Nonterminal, ConcurrentHashMap<ParseTree<String>, Set<ParseTree<String>>>> memoizedCompleteTreeQueries = new ConcurrentHashMap<>();

    private void removeInvalidCachedTrees(CFGrammar.Grammar<String> g)
    {
        Set<CFGrammar.Rule<String>> rules = new HashSet<>(JavaConversions.seqAsJavaList(g.rules()));
        Set<CFGrammar.Nonterminal> nonTerms = new HashSet<>(JavaConversions.seqAsJavaList(g.nonTerminals()));

        Set<CFGrammar.Nonterminal> mappingsToRemove = new HashSet<>();
        for (CFGrammar.Nonterminal nt : memoizedNullTrees.keySet())
        {
            if (!nonTerms.contains(nt))
            {
                mappingsToRemove.add(nt);
                continue;
            }

            Set<ParseTree<String>> cachedTrees = memoizedNullTrees.get(nt);

            cachedTrees.removeIf(t -> !isTreeValid(t, rules));

            if (cachedTrees.isEmpty())
                mappingsToRemove.add(nt);
        }
        mappingsToRemove.forEach(memoizedNullTrees::remove);

        mappingsToRemove.clear();
        for (CFGrammar.Nonterminal nt : memoizedCompleteTreeQueries.keySet())
        {
            if (!nonTerms.contains(nt))
            {
                mappingsToRemove.add(nt);
                continue;
            }

            ConcurrentHashMap<ParseTree<String>, Set<ParseTree<String>>> partialTreesMemoized = memoizedCompleteTreeQueries.get(nt);
            Set<ParseTree<String>> emptyMappings = new HashSet<>();
            for (ParseTree<String> partialTree : partialTreesMemoized.keySet())
            {
                Set<ParseTree<String>> memTrees = partialTreesMemoized.get(partialTree);
                memTrees.removeIf(t -> !isTreeValid(t, rules));
                if (memTrees.isEmpty())
                    emptyMappings.add(partialTree);
            }
            emptyMappings.forEach(partialTreesMemoized::remove);
        }
        mappingsToRemove.forEach(memoizedCompleteTreeQueries::remove);
    }

    public Set<ParseTree<String>> isSubsetOf(CFGrammar.Grammar<String> g1, CFGrammar.Grammar<String> g2) {
        assert eqCtx != null;

        Instant start = null;

        if (CmdLine.STATS_MODE)
            start = Instant.now();

        CFGrammar.Grammar<String> g1CNF = g1.cnfGrammar();

        if (CmdLine.STATS_MODE)
        {
            Instant end = Instant.now();
            PerfStats.getInstance().addCNFConversion(Duration.between(start, end));
        }

        if (CmdLine.PROFILING_MODE || CmdLine.STATS_MODE)
            start = Instant.now();

        EquivalenceChecker<String> equiv = new StudentGrammarEquivalenceChecker(g2, gctx, eqCtx, enumctx, parsectx);
        List<List<CFGrammar.Terminal<String>>> subsetRes = equiv.isSubset(g1CNF);

        if (subsetRes.isEmpty())
        {
            System.out.println(g1CNF);
        }

        if (CmdLine.PROFILING_MODE)
        {
            Instant end = Instant.now();
            System.out.println("subset check: " + Duration.between(start, end));
        }

        if (CmdLine.STATS_MODE)
        {
            Instant end = Instant.now();
            PerfStats.getInstance().addInclusionCheck(Duration.between(start, end));
        }

        if (CmdLine.PROFILING_MODE || CmdLine.STATS_MODE)
            start = Instant.now();

        removeInvalidCachedTrees(g1);

        nullTreesForRecursiveMethods.clear();
        Set<String> nullables = JavaConversions.setAsJavaSet(GrammarUtils.nullables(g1)).stream().map(CFGrammar.Nonterminal::toString).collect(Collectors.toSet());
        for (CFGrammar.Nonterminal nt : JavaConversions.seqAsJavaList(g1.nonTerminals()))
        {
            if (CFGrammar.reach(g1, nt, nt))
            {
                Set<ParseTree<String>> localNullTrees = getNullTrees(nt, g1, nullables, new HashSet<>(), false);
                if (!localNullTrees.isEmpty())
                {
                    nullTreesForRecursiveMethods.putIfAbsent(nt, localNullTrees);
                }
            }
        }

        CYKParser<String> g1Parser = new CYKParser<>(g1CNF);
        Set<ParseTree<String>> rv = JavaConversions.seqAsJavaList(subsetRes)
                                                   .stream()
                                                   .map(cex -> g1Parser.parseWithTree(cex, gctx).get())
                                                   .limit(MAX_NUM_TREES)
                                                   .flatMap(t -> mapTreeToGrammar(t, g1, nullables).stream())
                                                   .collect(Collectors.toSet());

        if (CmdLine.PROFILING_MODE)
        {
            Instant end = Instant.now();
            System.out.println("reconstruct trees: " + Duration.between(start, end));
        }

        if (CmdLine.STATS_MODE)
        {
            Instant end = Instant.now();
            PerfStats.getInstance().addReconstructTrees(Duration.between(start, end));
        }

        assert !(subsetRes.size() > 0) || rv.size() > 0;
        return rv;
    }

    // Not the best place for this method, but it will do for now.
    public static java.util.Set<String> getSetOfTerminals(EBNFGrammar.BNFGrammar<String> g) {
        return JavaConversions.setAsJavaSet(g.cfGrammar().terminals())
                              .stream()
                              .map((t) -> "'" + t.obj() + "'")                    // Unpacks the String from the CFGrammar.Terminal class
                              .collect(Collectors.toSet());
    }

    public static String unquoteRuleName(String ruleName) {
        return ruleName.charAt(0) == '\'' ? ruleName.substring(1) : ruleName;
    }

    public static boolean isDummyRule(String ruleName) {
        return unquoteRuleName(ruleName).startsWith("t-c");
    }

    public void initEquivCtxForGrammar(CFGrammar.Grammar<String> g, int nUnroll) {
        CFGrammar.Grammar<String> gCNF = g.cnfGrammar();
        eqCtx = new EquivalenceCheckingContext(0, 0, 0,
                                               false, 100, 1,
                                               maxWordLen(gCNF, nUnroll), -1, false);
    }

    private static int maxWordLen(CFGrammar.Grammar<String> g, int nUnroll)
    {
        Map<CFGrammar.Nonterminal, List<CFGrammar.Rule<String>>> ruleMap = JavaConversions.mapAsJavaMap(g.nontermToRules());

        int wordLen = additiveTraverseNonterm(nUnroll, g.start(), ruleMap, new HashSet<>(), new HashMap<>(), new HashSet<>(), new HashMap<>());
        return wordLen;
    }

    private static int multiplicativeTraverseNonterm(int nUnroll, CFGrammar.Nonterminal nonTerm, Map<CFGrammar.Nonterminal, List<CFGrammar.Rule<String>>> ruleMap,
                                       HashSet<CFGrammar.Nonterminal> seen, HashMap<CFGrammar.Nonterminal, Integer> computed,
                                       HashSet<CFGrammar.Nonterminal> downstream, HashMap<CFGrammar.Nonterminal, Set<CFGrammar.Nonterminal>> succMap) {
        downstream.add(nonTerm);

        if(seen.contains(nonTerm)) {
            //computed.put(nonTerm, 0);
            return 0;
        }
        if(computed.containsKey(nonTerm)) {
            downstream.addAll(succMap.get(nonTerm));
            return computed.get(nonTerm);
        }

        int maxLen = 0;
        seen.add(nonTerm);

        HashSet<CFGrammar.Nonterminal> succs = new HashSet<>();
        succMap.put(nonTerm, succs);

        for(CFGrammar.Rule r : JavaConversions.seqAsJavaList(ruleMap.get(nonTerm))) {
            HashSet<CFGrammar.Nonterminal> pathSucc = new HashSet<>();
            int newMax = multiplicativeTraverseRule(nUnroll, r, ruleMap, seen, computed, pathSucc, succMap);

            if(pathSucc.contains(nonTerm)) {
                newMax *= nUnroll;
            }

            succs.addAll(pathSucc);
            maxLen = (newMax > maxLen) ? newMax : maxLen;
        }

        seen.remove(nonTerm);
        computed.put(nonTerm, maxLen);
        downstream.addAll(succs);

        return maxLen;
    }

    private static int multiplicativeTraverseRule(int nUnroll, CFGrammar.Rule r, Map<CFGrammar.Nonterminal, List<CFGrammar.Rule<String>>> ruleMap,
                                    HashSet<CFGrammar.Nonterminal> seen, HashMap<CFGrammar.Nonterminal, Integer> computed,
                                    HashSet<CFGrammar.Nonterminal> downstream, HashMap<CFGrammar.Nonterminal, Set<CFGrammar.Nonterminal>> succMap) {
        int maxLen = 0;
        for (Object s : JavaConversions.seqAsJavaList(r.rightSide()))
        {
            if (s instanceof CFGrammar.Nonterminal) {
                maxLen += multiplicativeTraverseNonterm(nUnroll, (CFGrammar.Nonterminal) s, ruleMap, seen, computed, downstream, succMap);
            }
            else {
                maxLen += 1;
            }
        }

        return maxLen;
    }

    private static int additiveTraverseNonterm(int nUnroll, CFGrammar.Nonterminal nonTerm, Map<CFGrammar.Nonterminal, List<CFGrammar.Rule<String>>> ruleMap,
                                               HashSet<CFGrammar.Nonterminal> seen, HashMap<CFGrammar.Nonterminal, Tuple2<Integer, Boolean>> computed,
                                               HashSet<CFGrammar.Nonterminal> downstream, HashMap<CFGrammar.Nonterminal, Set<CFGrammar.Nonterminal>> succMap) {
        downstream.add(nonTerm);

        if(seen.contains(nonTerm)) {
            //computed.put(nonTerm, 0);
            return 0;
        }
        if(computed.containsKey(nonTerm)) {
            downstream.addAll(succMap.get(nonTerm));
            return computed.get(nonTerm)._1;
        }

        int maxLen = 0;
        seen.add(nonTerm);

        HashSet<CFGrammar.Nonterminal> succs = new HashSet<>();
        succMap.put(nonTerm, succs);
        boolean isLoop = false;

        for(CFGrammar.Rule r : JavaConversions.seqAsJavaList(ruleMap.get(nonTerm))) {
            HashSet<CFGrammar.Nonterminal> pathSucc = new HashSet<>();
            int newMax = additiveTraverseRule(nUnroll, r, ruleMap, seen, computed, pathSucc, succMap);
            boolean pathLoops = false;

            if(pathSucc.contains(nonTerm)) {
                int outOfScope = 0;
                for(CFGrammar.Nonterminal succ : pathSucc) {
                    //the current nonterm be only one with no computed entry
                    if(computed.containsKey(succ) && computed.get(succ)._2 && computed.get(succ)._1 > outOfScope) {
                        outOfScope = computed.get(succ)._1;
                    }
                }

                pathLoops = true;
                newMax += (newMax - outOfScope + 1) * nUnroll;
            }

            succs.addAll(pathSucc);

            if(newMax > maxLen) {
                maxLen = newMax;
                isLoop = pathLoops;
            }
        }

        seen.remove(nonTerm);
        computed.put(nonTerm, Tuple2.apply(maxLen, isLoop));
        downstream.addAll(succs);

        return maxLen;
    }

    private static int additiveTraverseRule(int nUnroll, CFGrammar.Rule r, Map<CFGrammar.Nonterminal, List<CFGrammar.Rule<String>>> ruleMap,
                        HashSet<CFGrammar.Nonterminal> seen, HashMap<CFGrammar.Nonterminal, Tuple2<Integer, Boolean>> computed,
                        HashSet<CFGrammar.Nonterminal> downstream, HashMap<CFGrammar.Nonterminal, Set<CFGrammar.Nonterminal>> succMap) {
        int maxLen = 0;
        for (Object s : JavaConversions.seqAsJavaList(r.rightSide()))
        {
            if (s instanceof CFGrammar.Nonterminal) {
                maxLen += additiveTraverseNonterm(nUnroll, (CFGrammar.Nonterminal) s, ruleMap, seen, computed, downstream, succMap);
            }
            else {
                maxLen += 1;
            }
        }

        return maxLen;

    }
}


