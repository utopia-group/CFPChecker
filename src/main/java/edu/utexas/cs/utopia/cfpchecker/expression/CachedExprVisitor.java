package edu.utexas.cs.utopia.cfpchecker.expression;

import edu.utexas.cs.utopia.cfpchecker.expression.visitor.Visitor;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by kferles on 5/19/17.
 */
public abstract class CachedExprVisitor implements Visitor
{
    private Set<Expr> visitedNodes = new HashSet<>();

    protected boolean alreadyVisited(Expr e)
    {
        boolean rv = visitedNodes.contains(e);

        if (!rv)
            visitedNodes.add(e);

        return rv;
    }
}
