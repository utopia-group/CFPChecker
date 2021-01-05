package edu.utexas.cs.utopia.cfpchecker.expression.visitor;

import edu.utexas.cs.utopia.cfpchecker.expression.type.*;

/**
 * Created by kferles on 6/8/17.
 */
public interface ExprTypeRetVisitor<R>
{
    R visit(BooleanType type);

    R visit(FunctionType type);

    R visit(IntegerType type);

    R visit(UnitType type);

    R visit(ArrayType type);

    R visit(StringType type);
}
