package edu.utexas.cs.utopia.cfpchecker;

import edu.utexas.cs.utopia.cfpchecker.speclang.Specification;
import edu.utexas.cs.utopia.cfpchecker.speclang.Specs;
import org.apache.commons.lang.math.NumberUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by kferles on 6/28/18.
 */
public class CmdLine
{
    public static boolean DEBUG_MODE = false;

    public static boolean PROFILING_MODE = false;

    public static boolean STATS_MODE = false;

    private Set<String> availableSpecs = new HashSet<>(Arrays.asList("reentrant-lock",
                                                                     "canvas-save-restore",
                                                                     "sensor-register-unregister",
                                                                     "chained-reentrant-lock",
                                                                     "wifiLock",
                                                                     "wakeLock",
                                                                     "location-manager-looper",
                                                                     "location-manager",
                                                                     "nested-spec",
                                                                     "chained-spec",
                                                                     "unbalanced-spec",
                                                                     "wrapped-spec",
                                                                     "jsongenerator-spec"));

    private Specification spec;

    private int nUnroll = 5;

    private boolean isHelp = false;

    public Specification getSpec()
    {
        return spec;
    }

    public int getnUnroll()
    {
        return nUnroll;
    }

    String usage()
    {
        StringBuilder rv = new StringBuilder();

        rv.append("java -jar cfp-checker.jar --spec <spec-name> -- [soot-options]\n")
          .append("--spec <spec-name>:                              specification to be used\n")
          .append("  * available options:                           ").append(String.join(",", availableSpecs)).append("\n")
          .append("--unroll-count <n>                               times to unroll recursive calls and loops\n")
          .append("--debug                                          run on debug mode (default false)")
          .append("--profile                                        profile execution time of refinement loop")
          .append("--stats                                          print statistics about the tool's execution")
          .append("\nAdditional Options:\n")
          .append("-h, --help:                                      print this message and exit");

        return rv.toString();
    }

    String parseArgs(String[] args)
    {
        String parseError = null;

        parseLoop:
        for (int i = 0, e = args.length; i < e; )
        {
            switch (args[i])
            {
                case "-h":
                case "--help":
                    isHelp = true;
                    break parseLoop;
                case "--spec":
                    if (i + 1 == e)
                    {
                        parseError = "Missing argument for option --spec";
                        break parseLoop;
                    }

                    String specName = args[i + 1];
                    if (!availableSpecs.contains(specName))
                    {
                        parseError = "Invalid option for --spec";
                        break parseLoop;
                    }

                    switch (specName)
                    {
                        case "reentrant-lock":
                            spec = Specs.createReentrantLockSpec();
                            break;
                        case "chained-reentrant-lock":
                            spec = Specs.createChainedReentrantLockSpec();
                            break;
                        case "wifiLock":
                            spec = Specs.createWifiLockSpec();
                            break;
                        case "wakeLock":
                            spec = Specs.createWakeLockSpec();
                            break;
                        case "canvas-save-restore":
                            spec = Specs.createCanvasSaveRestoreSpec();
                            break;
                        case "sensor-register-unregister":
                            spec = Specs.createSensorManagerListenerSpec();
                            break;
                        case "location-manager-looper":
                            spec = Specs.createLocationManagerLooperSpec();
                            break;
                        case "location-manager":
                            spec = Specs.createLocationManagerSpec();
                            break;
                        case "nested-spec":
                            spec = Specs.createNestedSpec();
                            break;
                        case "chained-spec":
                            spec = Specs.createChainedSpec();
                            break;
                        case "unbalanced-spec":
                            spec = Specs.createUnbalancedSpec();
                            break;
                        case "wrapped-spec":
                            spec = Specs.createWrappedSpec();
                            break;
                        case "jsongenerator-spec":
                            spec = Specs.createJsonGeneratorSpec();
                            break;
                        default:
                            assert false;
                    }

                    i += 2;
                    break;
                case "--unroll-count":
                    if (i + 1 == e)
                    {
                        parseError = "Missing argument for option --unroll-count";
                        break parseLoop;
                    }

                    if (!NumberUtils.isNumber(args[i + 1]))
                    {
                        parseError = "Invalid argument for options --unroll-count, an integer value is expected";
                        break parseLoop;
                    }

                    nUnroll = Integer.parseInt(args[i + 1]);
                    i += 2;
                    break;
                case "--debug":
                    DEBUG_MODE = true;
                    ++i;
                    break;
                case "--profile":
                    PROFILING_MODE = true;
                    ++i;
                    break;
                case "--stats":
                    STATS_MODE = true;
                    ++i;
                    break;
                default:
                    parseError = "Invalid option: " + args[i];
                    break parseLoop;
            }
        }

        if (!isHelp)
        {
            if (spec == null)
            {
                parseError = "Missing required argument --spec.";
            }
        }

        return parseError;
    }

    public boolean isHelp()
    {
        return isHelp;
    }
}
