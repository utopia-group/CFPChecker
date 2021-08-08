#!/bin/bash

CHECKER_JAR="target/cfpchecker-1.0-SNAPSHOT.jar"
ANDROID_JAR="benchmarks/lib/android.jar"

COMMON_SOOT_OPTS="-w -exclude java* -no-bodies-for-excluded -allow-phantom-refs -cp benchmarks/lib/android.jar -p cg.spark enabled:true,rta:true,on-fly-cg:false"

echo "Running WifiLock"

echo "   - Running: ExoPlayer-Bug (WifiLock)"
${JAVA_HOME}/bin/java -cp ${ANDROID_JAR}:${CHECKER_JAR} -Djava.library.path=${Z3_BUILD} edu.utexas.cs.utopia.cfpchecker.Driver --stats --spec wifiLock --unroll-count 5 -- -process-dir ../cfp-checker-bench-slicer/ExoPlayer-WifiLock-Bug/ -process-dir benchmarks/dist/new-models/ ${COMMON_SOOT_OPTS}

echo "   - Running: ExoPlayer-Fix (WifiLock)"
${JAVA_HOME}/bin/java -cp ${ANDROID_JAR}:${CHECKER_JAR} -Djava.library.path=${Z3_BUILD} edu.utexas.cs.utopia.cfpchecker.Driver --stats --spec wifiLock --unroll-count 5 -- -process-dir ../cfp-checker-bench-slicer/ExoPlayer-WifiLock-Fix/ -process-dir benchmarks/dist/new-models/ ${COMMON_SOOT_OPTS}

echo "Running WakeLock"

echo "   - Running: ExoPlayer-Bug (WakeLock)"
${JAVA_HOME}/bin/java -cp ${ANDROID_JAR}:${CHECKER_JAR} -Djava.library.path=${Z3_BUILD} edu.utexas.cs.utopia.cfpchecker.Driver --stats --spec wakeLock --unroll-count 5 -- -process-dir ../cfp-checker-bench-slicer/ExoPlayer-WakeLock-Bug/ -process-dir benchmarks/dist/new-models/ ${COMMON_SOOT_OPTS}

echo "   - Running: ExoPlayer-Fix (WakeLock)"
${JAVA_HOME}/bin/java -cp ${ANDROID_JAR}:${CHECKER_JAR} -Djava.library.path=${Z3_BUILD} edu.utexas.cs.utopia.cfpchecker.Driver --stats --spec wakeLock --unroll-count 5 -- -process-dir ../cfp-checker-bench-slicer/ExoPlayer-WakeLock-Fix/ -process-dir benchmarks/dist/new-models/ ${COMMON_SOOT_OPTS}

echo "   - Running: ConnectBot-Bug"
${JAVA_HOME}/bin/java -cp ${ANDROID_JAR}:${CHECKER_JAR} -Djava.library.path=${Z3_BUILD} edu.utexas.cs.utopia.cfpchecker.Driver --stats --spec wakeLock --unroll-count 5 -- -process-dir ../cfp-checker-bench-slicer/ConnectBot-Bug/ -process-dir benchmarks/dist/new-models/ ${COMMON_SOOT_OPTS}

echo "   - Running: ConnectBot-Fix"
${JAVA_HOME}/bin/java -cp ${ANDROID_JAR}:${CHECKER_JAR} -Djava.library.path=${Z3_BUILD} edu.utexas.cs.utopia.cfpchecker.Driver --stats --spec wakeLock --unroll-count 5 -- -process-dir ../cfp-checker-bench-slicer/ConnectBot-Fix/ -process-dir benchmarks/dist/new-models/ ${COMMON_SOOT_OPTS}

echo "Running ReentrantLock"

echo "   - Running: Hystrix (ReLock)"
${JAVA_HOME}/bin/java -ea -cp ${ANDROID_JAR}:${CHECKER_JAR} -Djava.library.path=${Z3_BUILD} edu.utexas.cs.utopia.cfpchecker.Driver --stats --spec chained-reentrant-lock --unroll-count 5 -- -process-dir ../cfp-checker-bench-slicer/Hystrix-ReLock/ -process-dir benchmarks/dist/new-models/ ${COMMON_SOOT_OPTS}

echo "   - Running: Bitcoinj"
${JAVA_HOME}/bin/java -cp ${ANDROID_JAR}:${CHECKER_JAR} -Djava.library.path=${Z3_BUILD} edu.utexas.cs.utopia.cfpchecker.Driver --stats --spec chained-reentrant-lock --unroll-count 5 -- -process-dir ../cfp-checker-bench-slicer/Bitcoinj/ -process-dir benchmarks/dist/new-models/ ${COMMON_SOOT_OPTS}

echo "   - Running: Guice"
${JAVA_HOME}/bin/java -cp ${ANDROID_JAR}:${CHECKER_JAR} -Djava.library.path=${Z3_BUILD} edu.utexas.cs.utopia.cfpchecker.Driver --stats --spec chained-reentrant-lock --unroll-count 5 -- -process-dir ../cfp-checker-bench-slicer/Guice/ -process-dir benchmarks/dist/new-models/ ${COMMON_SOOT_OPTS}

echo "Running JSON"

echo "   - Running: Hadoop"
${JAVA_HOME}/bin/java -cp ${ANDROID_JAR}:${CHECKER_JAR} -Djava.library.path=${Z3_BUILD} edu.utexas.cs.utopia.cfpchecker.Driver --stats --spec jsongenerator-spec --unroll-count 5 -- -main-class org.apache.hadoop.mapred.Harness  -process-dir ../cfp-checker-bench-slicer/Hadoop/ -process-dir benchmarks/dist/new-models/ ${COMMON_SOOT_OPTS}

echo "   - Running: Hystrix (Json1)"
${JAVA_HOME}/bin/java -cp ${ANDROID_JAR}:${CHECKER_JAR} -Djava.library.path=${Z3_BUILD} edu.utexas.cs.utopia.cfpchecker.Driver --stats --spec jsongenerator-spec --unroll-count 5 -- -process-dir ../cfp-checker-bench-slicer/Hystrix-Json1/ -process-dir benchmarks/dist/new-models/ ${COMMON_SOOT_OPTS}

echo "   - Running: Hystrix (Json2)"
${JAVA_HOME}/bin/java -cp ${ANDROID_JAR}:${CHECKER_JAR} -Djava.library.path=${Z3_BUILD} edu.utexas.cs.utopia.cfpchecker.Driver --stats --spec jsongenerator-spec --unroll-count 5 -- -process-dir ../cfp-checker-bench-slicer/Hystrix-Json2/ -process-dir benchmarks/dist/new-models/ ${COMMON_SOOT_OPTS}

echo "Running Canvas"

echo "   - Running: Glide"
${JAVA_HOME}/bin/java -cp ${ANDROID_JAR}:${CHECKER_JAR} -Djava.library.path=${Z3_BUILD} edu.utexas.cs.utopia.cfpchecker.Driver --stats --spec canvas-save-restore --unroll-count 5 -- -process-dir ../cfp-checker-bench-slicer/Glide/ -process-dir benchmarks/dist/new-models/ ${COMMON_SOOT_OPTS}

echo "   - Running: RxTool"
${JAVA_HOME}/bin/java -cp ${ANDROID_JAR}:${CHECKER_JAR} -Djava.library.path=${Z3_BUILD} edu.utexas.cs.utopia.cfpchecker.Driver --stats --spec canvas-save-restore --unroll-count 5 -- -process-dir ../cfp-checker-bench-slicer/RxTool/ -process-dir benchmarks/dist/new-models/ ${COMMON_SOOT_OPTS}

echo "   - Running: Litho"
${JAVA_HOME}/bin/java -cp ${ANDROID_JAR}:${CHECKER_JAR} -Djava.library.path=${Z3_BUILD} edu.utexas.cs.utopia.cfpchecker.Driver --stats --spec canvas-save-restore --unroll-count 5 -- -process-dir ../cfp-checker-bench-slicer/Litho/ -process-dir benchmarks/dist/new-models/ ${COMMON_SOOT_OPTS}
