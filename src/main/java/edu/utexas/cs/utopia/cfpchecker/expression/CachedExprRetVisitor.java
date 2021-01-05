package edu.utexas.cs.utopia.cfpchecker.expression;

import edu.utexas.cs.utopia.cfpchecker.expression.visitor.RetVisitor;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by kferles on 5/19/17.
 */
abstract class CachedExprRetVisitor<R> implements RetVisitor<R>
{
    private Set<Expr> visited = new HashSet<>();

    boolean alreadyVisited(Expr e)
    {
        boolean rv = visited.contains(e);

        if (!rv)
            visited.add(e);

        return rv;
    }
}
