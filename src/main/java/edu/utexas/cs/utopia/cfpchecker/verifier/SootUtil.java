package edu.utexas.cs.utopia.cfpchecker.verifier;

import soot.Body;
import soot.Unit;
import soot.options.Options;
import soot.toolkits.graph.DirectedGraph;
import soot.util.cfgcmd.AltClassLoader;
import soot.util.cfgcmd.CFGGraphType;
import soot.util.cfgcmd.CFGToDotGraph;
import soot.util.dot.DotGraph;

public class SootUtil
{

    private static SootUtil _instance = null;

    // static method to create instance of Singleton class
    public static SootUtil getInstance()
    {
        if (_instance == null)
            _instance = new SootUtil();

        return _instance;
    }

    public String unit2String(Unit unit)
    {
        String ret = normalString(unit.toString());
        return ret;
    }

    // Convert a statement to string, replace spaces to "_"
    public String normalString(String str)
    {
        String ret = str.replaceAll("\\s", "_");
        return ret;
    }

    public void print_cfg(Body body)
    {
        CFGToDotGraph drawer = new CFGToDotGraph();
        drawer.setBriefLabels(true);
        drawer.setOnePage(true);
        drawer.setUnexceptionalControlFlowAttr("color", "black");
        drawer.setExceptionalControlFlowAttr("color", "red");
        drawer.setExceptionEdgeAttr("color", "lightgray");
        drawer.setShowExceptions(Options.v().show_exception_dests());
        CFGGraphType graphtype = CFGGraphType.getGraphType("ExceptionalUnitGraph");

        AltClassLoader.v().setAltClassPath("graph-type");
        AltClassLoader.v()
                      .setAltClasses(new String[]{"soot.toolkits.graph.ArrayRefBlockGraph", "soot.toolkits.graph.Block",
                              "soot.toolkits.graph.Block$AllMapTo", "soot.toolkits.graph.BlockGraph", "soot.toolkits.graph.BriefBlockGraph",
                              "soot.toolkits.graph.BriefUnitGraph", "soot.toolkits.graph.CompleteBlockGraph",
                              "soot.toolkits.graph.CompleteUnitGraph", "soot.toolkits.graph.TrapUnitGraph", "soot.toolkits.graph.UnitGraph",
                              "soot.toolkits.graph.ZonedBlockGraph",});

        DirectedGraph<Unit> graph = graphtype.buildGraph(body);
        DotGraph canvas = graphtype.drawGraph(drawer, graph, body);

        String methodname = body.getMethod().getSubSignature();
        String classname = body.getMethod().getDeclaringClass().getName().replaceAll("\\$", "\\.");
        String filename = soot.SourceLocator.v().getOutputDir();
        if (filename.length() > 0)
        {
            filename = filename + java.io.File.separator;
        }
        filename = filename + classname + " " + methodname.replace(java.io.File.separatorChar, '.') + DotGraph.DOT_EXTENSION;

        System.out.println("Generate dot file in " + filename);
        canvas.plot(filename);
    }
}
