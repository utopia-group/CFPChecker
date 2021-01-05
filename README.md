# CFPChecker: A Tool for Verifying Correct Usage of Context-Free API Protocols.

This is the tool proposed in the following POPL'21 paper: [Verifying Correct Usage of Context-Free API Protocols](http://kferles.github.io/docs/publications/POPL-21.pdf)

This repository only contains the verification component described in
the above publication. The program slicing component is implemented as
a separate tool. We are planning to release this tool as well soon, if
you need access to this tool in the meantime please contact me
[here](mailto:kferles@gmail.com).

## Building CFPChecker

### Prerequisite

CFPChecker requires a Z3 installation. Please follow [these instuctions](https://github.com/Z3Prover/z3) on how to install Z3. *Impoprtant:* Make sure to configure your build with the Java bidings (see [here](https://github.com/Z3Prover/z3#java)).

### Generating CFPChecker JAR

To generate CFPChecker's JAR file execute the following commands (requires Maven, please look [here](https://maven.apache.org/) for system-specific installation instructions):

```
$ mvn initialize
$ mvn package
```

## Using CFPChecker

### Command Line

### Regression Tests

### Adding More API Protocols
