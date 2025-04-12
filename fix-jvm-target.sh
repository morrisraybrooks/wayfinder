#!/bin/bash

echo "=== Setting up build environment with fixed JVM target ==="

# Clear any custom JVM arguments that might override our settings
unset _JAVA_OPTIONS
unset JAVA_TOOL_OPTIONS
unset GRADLE_OPTS

# Set explicit JVM target 
export KAPT_JVM_TARGET=17
export KOTLIN_JVM_TARGET=17

# Stop existing gradle daemons
./gradlew --stop

# Clean the project first
./gradlew clean

# Run the build with explicit JVM args
./gradlew --no-daemon \
  -Dkotlin.compiler.execution.strategy=in-process \
  -Dkotlin.daemon.jvm.options="-Xmx2048m" \
  -Dkotlin.compiler.arguments.K2="-jvm-target=17" \
  -Dkotlin.jvm.target=17 \
  -Dkotlin.jvm.target.validation=ignore \
  -Dkotlin.stdlib.jdk.variant=17 \
  -Dkapt.use.worker.api=false \
  -Dkapt.kotlin.generated=build/generated/source/kapt/main \
  :app:assembleDebug

echo "Build completed with status: $?" 