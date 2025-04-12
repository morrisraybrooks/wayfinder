#!/bin/bash

echo "=== Setting up environment for KAPT ==="

# Clear any previous Java options
unset _JAVA_OPTIONS
unset JAVA_TOOL_OPTIONS
unset GRADLE_OPTS

# Set up Java options for KAPT to work with the Java module system
export _JAVA_OPTIONS="--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"

# Also set JAVA_TOOL_OPTIONS as a backup
export JAVA_TOOL_OPTIONS="--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"

echo "=== Stopping Gradle daemons ==="
./gradlew --stop

echo "=== Cleaning project ==="
./gradlew clean

echo "=== Running build with special arguments ==="
./gradlew --no-daemon \
  -Dkapt.use.worker.api=false \
  -Dkapt.incremental.apt=false \
  -Dkapt.include.compile.classpath=false \
  -Dorg.gradle.jvmargs="-Xmx2048m -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8 -XX:+UseParallelGC" \
  :app:assembleDebug :app:assembleDebugUnitTest :app:assembleDebugAndroidTest

BUILD_STATUS=$?
echo "Build completed with status: $BUILD_STATUS"
exit $BUILD_STATUS 