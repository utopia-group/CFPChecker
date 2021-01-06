# CFPChecker: A Tool for Verifying Correct Usage of Context-Free API Protocols.

This is the tool proposed in the following POPL'21 paper: [Verifying Correct Usage of Context-Free API Protocols](http://kferles.github.io/docs/publications/POPL-21.pdf)

This repository only contains the verification component described in
the above publication. The program slicing component is implemented as
a separate tool. We are planning to release this tool as well soon, if
you need access to this tool in the meantime please contact me
[here](mailto:kferles@gmail.com).

## Building CFPChecker

### Prerequisite

CFPChecker requires a Z3 installation. Please follow [these
instuctions](https://github.com/Z3Prover/z3) on how to install
Z3. *Impoprtant:* Make sure to configure your build with the Java
bidings (see [here](https://github.com/Z3Prover/z3#java)).

### Generating CFPChecker JAR

To generate CFPChecker's JAR file execute the following commands
(requires Maven, please look [here](https://maven.apache.org/) for
system-specific installation instructions):

```
$ mvn initialize
$ mvn package
```

The JAR is located in the generated "target" folder.

## Using CFPChecker

### Command Line

CFPChecker's command line consists of two parts:
1. CFPChecker-specific arguments. 2. Soot command line arguments.  The
most important command-line arguments are listed below, the actual
implementation has more options that were introduces while we were
debugging the tool. To see how to obtain an actual invocation of
CFPChecker, refer to the "Regression Tests" section below.

CFPChecker's command line:
```
java -jar cfp-checker.jar --spec <spec-name> -- [soot-options]
--spec <spec-name>:                              specification to be used
  * available options:                           chained-reentrant-lock,canvas-save-restore,jsongenerator-spec,wifiLock,wakeLock
--debug                                          run on debug mode (default false)
--profile                                        profile execution time of refinement loop
--stats                                          print statistics about the tool's execution
Additional Options:
-h, --help:                                      print this message and exit
```

### Regression Tests

### Adding More API Protocols
