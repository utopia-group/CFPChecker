package edu.utexas.cs.utopia.cfpchecker.expression.type;

/**
 * Created by kferles on 9/17/18.
 */
public class ArrayType extends FunctionType
{

    public ArrayType(ExprType domain, ExprType coDomain)
    {
        super(new ExprType[]{domain}, coDomain);
    }

    @Override
    public String toString()
    {
        return "ArrayTy: " + super.toString();
    }
}
