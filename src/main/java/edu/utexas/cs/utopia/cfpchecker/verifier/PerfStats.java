package edu.utexas.cs.utopia.cfpchecker.verifier;

import java.time.Duration;

public class PerfStats
{
    static private PerfStats INSTANCE = new PerfStats();

    public static PerfStats getInstance()
    {
        return INSTANCE;
    }

    private Duration inclusionCheck = Duration.ZERO;

    private Duration cnfConversion = Duration.ZERO;

    private Duration reconstructTrees = Duration.ZERO;

    private Duration refinement = Duration.ZERO;

    private Duration interpolation = Duration.ZERO;

    private Duration cexSAT = Duration.ZERO;

    public void addInclusionCheck(Duration d)
    {
        inclusionCheck = inclusionCheck.plus(d);
    }

    public void addCNFConversion(Duration d)
    {
        cnfConversion = cnfConversion.plus(d);
    }

    public void addReconstructTrees(Duration d)
    {
        reconstructTrees = reconstructTrees.plus(d);
    }

    public void addRefinement(Duration d)
    {
        refinement = refinement.plus(d);
    }

    public void addInterpolation(Duration d)
    {
        interpolation = interpolation.plus(d);
    }

    public void addCexSAT(Duration d)
    {
        cexSAT = cexSAT.plus(d);
    }

    public String toString()
    {
        return  "Inclusion Check:   " + inclusionCheck   + "\n" +
                "CNF Conversion:    " + cnfConversion    + "\n" +
                "Reconstruct Trees: " + reconstructTrees + "\n" +
                "Refinement:        " + refinement       + "\n" +
                "Interpolation:     " + interpolation    + "\n" +
                "CEX SAT:           " + cexSAT;
    }
}
