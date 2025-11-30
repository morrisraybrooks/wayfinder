#!/bin/bash

echo "=== Setting up environment for JVM target 17 ==="

# Clear any environment variables that might override our settings
unset _JAVA_OPTIONS
unset JAVA_TOOL_OPTIONS
unset GRADLE_OPTS

# Stop existing Gradle daemons
echo "=== Stopping Gradle daemons ==="
./gradlew --stop

# Clean the project first
echo "=== Cleaning project ==="
./gradlew clean

# Run the build with explicit JVM target settings
echo "=== Running build with JVM target 17 ==="
./gradlew --no-daemon \
  -Dorg.gradle.jvmargs="-Xmx2048m -Dfile.encoding=UTF-8" \
  -Dkotlin.jvm.target=17 \
  -Dkotlin.jvm.target.validation=ignore \
  -Dkapt.kotlin.jvm.target=17 \
  -Dkapt.use.worker.api=false \
  :app:assembleDebug

BUILD_STATUS=$?
echo "Build completed with status: $BUILD_STATUS"
exit $BUILD_STATUS 