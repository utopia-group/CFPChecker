#!/usr/bin/python
import multiprocessing
import os
import platform
import subprocess
import sys
import time
from collections import namedtuple
from functools import reduce
from threading import Thread, Lock

Config = namedtuple('Config', 'javaExec z3Build, androidLib, verbose')
Test = namedtuple('Test', 'name spec success')

# Regression tests
toy_tests = [Test('toy-simple', 'reentrant-lock', True),
             Test('toy-call', 'reentrant-lock', True),
             Test('toy-recursive', 'reentrant-lock', True),
             Test('toy-field', 'reentrant-lock', True),
             Test('simpleRecursive', 'reentrant-lock', True),
             Test('toy-nd-receiver', 'reentrant-lock', True)]

hard_tests = [Test('chainedRecursive', 'chained-reentrant-lock', True),
              Test('chainedRecursive', 'reentrant-lock', False),
              Test('deepRecursion', 'reentrant-lock', True),
              Test('multipleLocks', 'reentrant-lock', True),
              Test('stackoverflow', 'reentrant-lock', False),
              Test('wakelock-1', 'wakeLock', True),
              Test('wakelock-2', 'wakeLock', False),
              Test('wakelock-3', 'wakeLock', False),
              Test('mutualRecursion', 'reentrant-lock', True),
              Test('nestedLoopRecursion', 'chained-reentrant-lock', True),
              Test('recursiveDatastructureDynamic', 'reentrant-lock', True),
              Test('recursiveDatastructureStatic', 'reentrant-lock', True)]

def runTest(config, test, lock, results):
    cmd = [config.javaExec, '-Djava.library.path=%s:%s' % (config.z3Build, config.androidLib), '-ea', '-cp', 'benchmarks/lib/android.jar:target/cfpchecker-1.0-SNAPSHOT.jar', 'edu.utexas.cs.utopia.cfpchecker.Driver', '--spec', test.spec, '--unroll-count', '5', '--', '-process-dir', 'benchmarks/dist/%s' % (test.name), '-process-dir', 'benchmarks/dist/models', '-w', '-exclude', 'java*', '-no-bodies-for-excluded', '-allow-phantom-refs', '-cp','benchmarks/lib/android.jar']

    if config.verbose:
        lock.acquire()
        print("")
        print(' '.join(cmd))
        lock.release()

    stdout, stderr = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=procEnv).communicate()

    lock.acquire()
    print("")
    passed = False
    # Check result
    if(stdout.find("Total exec:") == -1):
        print(stderr)
        print("%s Error" % test.name)
    else:
        result = stdout.find("Total exec:") != -1 and stdout.find("UNSAT") == -1
        if (test.success and result) or not (test.success or result):
            passed = True
            print("%s Success" % test.name)
        else:
            print("%s Fail" % test.name)

    results.append(passed)
    lock.release()
    

verbose = False

tests = toy_tests + hard_tests

# Process command line arguments
for arg in sys.argv[1:]:
    if arg == '-c':
        print("Building cfp-checker")
        stdout, stderr = subprocess.Popen(['mvn', 'package'], stdout=subprocess.PIPE, stderr=subprocess.PIPE).communicate()
        if stdout.find('BUILD SUCCESS') == -1:
            print("Build failed")
            sys.exit(1)
    if arg == '-b':
        print("Building Benchmarks")
        os.system('bash benchmarks/build-benchmarks.sh')
    if arg == '-v':
        verbose = True
    if arg == '--only-toy':
        tests = toy_tests

# Setup environment
procEnv = os.environ.copy()
z3Build = procEnv['Z3_BUILD'] if 'Z3_BUILD' in procEnv else os.getcwd() + "/z3/build"
androidLib = os.getcwd() + '/benchmarks/lib'

os = platform.system()

if os == 'Darwin':
    procEnv['DYLD_LIBRARY_PATH'] = z3Build + ":" + procEnv['DYLD_LIBRARY_PATH'] if 'DYLD_LIBRARY_PATH' in procEnv else z3Build
elif os == 'Linux':
    procEnv['LD_LIBRARY_PATH'] = z3Build + ":" + procEnv['LD_LIBRARY_PATH'] if 'LD_LIBRARY_PATH' in procEnv else z3Build

stdout, stderr = subprocess.Popen(['mvn', '-v'], stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=procEnv).communicate()
javaLoc = filter(lambda s : s.find('jdk1.8') != -1, stdout.split())
javaExec = javaLoc[0] + '/../bin/java' if len(javaLoc) == 1 else 'java'

config = Config(javaExec, z3Build, androidLib, verbose)
numCores = multiprocessing.cpu_count()
lock = Lock()
results = []
testThreads = []
# Run tests
for test in tests:
    t = Thread(target = runTest, args = (config, test, lock, results))
    t.start()
    testThreads.append(t)

    if len(testThreads) == numCores:
        dead = filter((lambda x : not x.is_alive()), testThreads)
        while not dead:
            if verbose:
                lock.acquire()
                sys.stdout.write('.')
                sys.stdout.flush()
                lock.release()
            time.sleep(10)
            dead = filter((lambda x : not x.is_alive()), testThreads)
        for t in dead:
            t.join()
            testThreads.remove(t)

for t in testThreads:
    while t.is_alive():
        if verbose:
            lock.acquire()
            sys.stdout.write('.')
            sys.stdout.flush()
            lock.release()
        time.sleep(10)
    t.join()

all_passed = reduce((lambda x, y : x and y), results)

print all_passed
sys.exit(0 if all_passed else 1)
