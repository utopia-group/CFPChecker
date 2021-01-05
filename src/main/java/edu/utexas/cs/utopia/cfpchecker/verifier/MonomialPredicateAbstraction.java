package edu.utexas.cs.utopia.cfpchecker.verifier;

import com.microsoft.z3.Context;
import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.bool.NegExpr;
import edu.utexas.cs.utopia.cfpchecker.expression.factory.ExprFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static edu.utexas.cs.utopia.cfpchecker.expression.ExprUtils.checkUNSATWithZ3;

/**
 * Created by kferles on 7/23/18.
 */
public class MonomialPredicateAbstraction implements PredicateAbstraction
{
    @Override
    public Expr abstractFormula(ExprFactory exprFactory, Expr formula, Set<Expr> predicates)
    {
        List<Expr> conjucts = new ArrayList<>();
        Context ctx = new Context();

        for (Expr pred : predicates)
        {
            if (checkUNSATWithZ3(ctx, exprFactory.mkNEG(exprFactory.mkIMPL(formula, pred))))
                conjucts.add(pred);

            NegExpr notPred = exprFactory.mkNEG(pred);
            if (checkUNSATWithZ3(ctx, exprFactory.mkNEG(exprFactory.mkIMPL(formula, notPred))))
                conjucts.add(notPred);
        }

        int conjSize = conjucts.size();
        if (conjSize > 1)
        {
            return exprFactory.mkAND(conjucts.get(0), conjucts.get(1), conjucts.subList(2, conjSize).toArray(new Expr[conjSize - 2]));
        }
        else if (conjSize == 1)
        {
            return conjucts.get(0);
        }
        else
            return exprFactory.mkTRUE();
    }
}
