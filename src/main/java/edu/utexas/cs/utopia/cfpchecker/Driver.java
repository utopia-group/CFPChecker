package edu.utexas.cs.utopia.cfpchecker;

import com.microsoft.z3.Context;
import edu.utexas.cs.utopia.cfpchecker.expression.CachedExprFactory;
import edu.utexas.cs.utopia.cfpchecker.expression.factory.ExprFactory;
import edu.utexas.cs.utopia.cfpchecker.speclang.APISpecCall;
import edu.utexas.cs.utopia.cfpchecker.speclang.Specification;
import edu.utexas.cs.utopia.cfpchecker.verifier.*;
import soot.*;
import soot.options.Options;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

/**
 * Created by kferles on 6/28/18.
 */
public class Driver
{
    private static int argsSplitIndex(String[] args)
    {
        for (int i = 0; i < args.length; ++i)
            if (args[i].equals("--"))
                return i;

        return -1;
    }

    public static void main(String[] args)
    {
        int splitIndex = argsSplitIndex(args);

        String[] sootOptions, checkerOptions;

        CmdLine cmdLine = new CmdLine();

        if (splitIndex == -1)
        {
            checkerOptions = args;
            sootOptions = new String[]{};
        }
        else
        {
            checkerOptions = Arrays.copyOfRange(args, 0, splitIndex);
            sootOptions = Arrays.copyOfRange(args, splitIndex + 1, args.length);
        }

        PackManager packManager = PackManager.v();
        Options sootCmdLine = Options.v();

        sootCmdLine.parse(sootOptions);

        //sootCmdLine.set_ignore_resolution_errors(true);

        // Do not convert code to BAF
        sootCmdLine.set_output_format(Options.output_format_jimple);

        Scene.v().loadNecessaryClasses();
        packManager.runPacks();

        Instant start = Instant.now();

        String err = cmdLine.parseArgs(checkerOptions);

        if (err != null || cmdLine.isHelp())
        {
            if (err != null)
                System.err.println(err);

            System.err.println(cmdLine.usage());
            System.exit(1);
        }

        Specification spec = cmdLine.getSpec();

        ExprFactory exprFactory = new CachedExprFactory();
        Context ctx = new Context();

        // Adding our transformers!
        Pack wjtpPack = packManager.getPack("wjtp");

        ImmediateRecursionEliminator recursionEliminator = new ImmediateRecursionEliminator(spec);
        APICallRewriter apiCallRewriter = new APICallRewriter(spec);
        WildcardTransformation wildcardTransformation = new WildcardTransformation(spec);
        Map<Unit, APISpecCall> invToSpecCalls = wildcardTransformation.getInvokeStmtToSpecCalls();
        StaticLocalizeTransformation localizeTransform = new StaticLocalizeTransformation(spec);
        TransitiveReadWriteSetGenerator writeSetGenerator = TransitiveReadWriteSetGenerator.v(spec);

        NormalizeBlocks normBBs = new NormalizeBlocks(spec);
        NormalizeReturnStmts normRet = new NormalizeReturnStmts(spec);
        SwitchStatementRemover swRemover = new SwitchStatementRemover(spec);
        Assumify assumify = new Assumify(spec);
        Devirtualize devirt = new Devirtualize(spec);
        BreakBlocksOnCalls breakBlocks = new BreakBlocksOnCalls(invToSpecCalls, spec);
        AbstractionLifter progAbs = new AbstractionLifter(spec, exprFactory, ctx, invToSpecCalls, cmdLine.getnUnroll(), breakBlocks.getReturnLocations());

        Transform recurElimT = new Transform("wjtp.recelim", recursionEliminator);
        Transform apiCallRewriteT = new Transform("wjtp.apirew", apiCallRewriter);
        Transform wildcardT = new Transform("wjtp.wct", wildcardTransformation);
        Transform localizeT = new Transform("wjtp.lct", localizeTransform);
        Transform programAbsT = new Transform("wjtp.abs", progAbs);
        Transform normBBsT = new Transform("wjtp.normbb", normBBs);
        Transform normRetT = new Transform("wjtp.normRet", normRet);
        Transform swRemoverT = new Transform("wjtp.swremov", swRemover);
        Transform assumeT = new Transform("wjtp.assume", assumify);
        Transform devirtT = new Transform("wjtp.devirt", devirt);
        Transform breakCallsT = new Transform("wjtp.breakb", breakBlocks);
        Transform writeSetT = new Transform("wjtp.writeSet", writeSetGenerator);

        wjtpPack.add(recurElimT);
        wjtpPack.add(apiCallRewriteT);
        wjtpPack.add(devirtT);
        wjtpPack.add(wildcardT);
        wjtpPack.add(localizeT);
        wjtpPack.add(programAbsT);
        wjtpPack.add(normBBsT);
        wjtpPack.add(normRetT);
        wjtpPack.add(swRemoverT);
        wjtpPack.add(assumeT);
        wjtpPack.add(breakCallsT);
        wjtpPack.add(writeSetT);

        VerifierUtil.calculateReachableMethods();

        swRemoverT.apply();
        normBBsT.apply();
        normRetT.apply();
        devirtT.apply();
        apiCallRewriteT.apply();
        wildcardT.apply();
        //writeSetT.apply();
        localizeT.apply();
        recurElimT.apply();
        assumeT.apply();
        breakCallsT.apply();
        writeSetT.apply();
        programAbsT.apply();

        InterpolantGenerator interpGen = new InterpolantGenerator(exprFactory, spec, ctx, breakBlocks.getReturnLocations());
        Checker cfpChecker = new Checker(spec, progAbs, interpGen);
        cfpChecker.check();
        Instant end = Instant.now();
        System.out.println("Total exec: " + Duration.between(start, end));

        if(CmdLine.STATS_MODE) {
            cfpChecker.printExecutionStats();
        }
    }
}
