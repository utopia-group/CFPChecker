package edu.utexas.cs.utopia.cfpchecker.grammarcomp;

import grammarcomp.grammar.EBNFGrammar;
import grammarcomp.parsing.PLeaf;
import grammarcomp.parsing.PNode;
import grammarcomp.parsing.ParseTree;
import scala.collection.JavaConversions;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Created by kferles on 5/25/18.
 */
public class InclusionCheckExample
{

    public static void traverseCex(ParseTree<String> cex)
    {
        if (cex instanceof PLeaf)
        {
            // This would be an API call for us.
            System.out.println(cex);
        }
        else if (cex instanceof PNode)
        {
            // These nodes would be basic blocks for us.
            PNode<String> node = (PNode<String>) cex;
            System.out.println(node.r().leftSide());
            List<ParseTree<String>> parseTrees = JavaConversions.seqAsJavaList(node.children());
            if (!parseTrees.isEmpty())
            {
                for (ParseTree<String> child : parseTrees)
                {
                    traverseCex(child);
                }
            }
            else
                System.out.println("epsilon");
        }
        else
            assert false : "Unexpected Class";
    }

    public static void main(String[] args)
    {
        GrammarCompProxy gComp = GrammarCompProxy.getInstance();
        EBNFGrammar.BNFGrammar<String> g1 = gComp.createBNFGrammar(Arrays.asList("S -> a S b",
                                                                                 "S -> \"\""));
        EBNFGrammar.BNFGrammar<String> g2 = gComp.createBNFGrammar(Arrays.asList("S1 -> a S1 b",
                                                                                 "S1 -> \"\""));

        Set<ParseTree<String>> subsetRes = gComp.isSubsetOf(g1.cfGrammar(), g2.cfGrammar());
        System.out.println(subsetRes.isEmpty());                    // Should print true

        g2 = gComp.createBNFGrammar(Arrays.asList("S1 -> a S1 bb",
                                                  "S1 -> \"\""));
        subsetRes = gComp.isSubsetOf(g1.cfGrammar(), g2.cfGrammar());
        System.out.println(subsetRes.isEmpty());                    // Should print false

        // Print the counter example.
        for (ParseTree<String> tree : subsetRes)
        {
            traverseCex(tree);
        }
    }
}
