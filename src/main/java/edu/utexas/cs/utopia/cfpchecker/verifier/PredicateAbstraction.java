package edu.utexas.cs.utopia.cfpchecker.verifier;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.factory.ExprFactory;

import java.util.Set;

/**
 * Created by kferles on 7/23/18.
 */
public interface PredicateAbstraction
{
    Expr abstractFormula(ExprFactory exprFactory, Expr formula, Set<Expr> predicates);
}
