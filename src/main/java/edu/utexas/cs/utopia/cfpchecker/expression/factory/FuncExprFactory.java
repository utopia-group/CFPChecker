package edu.utexas.cs.utopia.cfpchecker.expression.factory;

import edu.utexas.cs.utopia.cfpchecker.expression.Expr;
import edu.utexas.cs.utopia.cfpchecker.expression.function.BoundVar;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncApp;
import edu.utexas.cs.utopia.cfpchecker.expression.function.FuncDecl;
import edu.utexas.cs.utopia.cfpchecker.expression.type.FunctionType;

import java.util.List;

/**
 * Created by kferles on 5/19/17.
 */
public interface FuncExprFactory
{
    BoundVar mkBoundVar(FuncDecl decl);

    FuncApp mkFAPP(FuncDecl decl, List<Expr> args);

    FuncApp mkFAPP(FuncDecl decl);

    FuncDecl mkFDECL(String name, FunctionType type);
}
