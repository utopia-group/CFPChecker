package edu.utexas.cs.utopia.cfpchecker.grammarcomp;

/**
 * Created by kferles on 5/29/18.
 */
public class GrammarCompUtil
{
    public static String formatTerminal(String terminal)
    {
        return "'" + terminal.replaceAll(" ", "\\\\") + "'";
    }
}
