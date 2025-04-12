#!/bin/bash

echo "=== FORCING JVM 17 FOR ALL BUILD COMPONENTS ==="

# Clear any environment variables that might override our settings
unset _JAVA_OPTIONS
unset JAVA_TOOL_OPTIONS
unset GRADLE_OPTS
unset KOTLIN_DAEMON_ARGS

# Create a temporary gradle.properties override
TEMP_PROPS="./temp_gradle.properties"
echo "# Temporary gradle properties" > $TEMP_PROPS
echo "org.gradle.jvmargs=-Xmx2048m" >> $TEMP_PROPS
echo "kotlin.jvm.target=17" >> $TEMP_PROPS
echo "kotlin.jvm.target.validation=ignore" >> $TEMP_PROPS
echo "kotlin.stdlib.jdk.variant=17" >> $TEMP_PROPS
echo "kapt.use.worker.api=false" >> $TEMP_PROPS
echo "kapt.kotlin.jvm.target=17" >> $TEMP_PROPS
echo "kapt.verbose=true" >> $TEMP_PROPS
echo "android.useAndroidX=true" >> $TEMP_PROPS
echo "android.enableJetifier=true" >> $TEMP_PROPS

# Kill all Java processes that might interfere
echo "Stopping Java processes..."
killall java 2>/dev/null

# Stop Gradle daemons
echo "Stopping Gradle daemons..."
./gradlew --stop

# Clean the project
echo "Cleaning project..."
./gradlew clean

# Create a temporary app build.gradle with simplified KAPT settings
TMP_GRADLE="./app/build.gradle.tmp"
cp ./app/build.gradle $TMP_GRADLE
cat ./app/build.gradle | grep -v "option(\"--add-opens" > ./app/build.gradle.tmp
mv ./app/build.gradle.tmp ./app/build.gradle

# Run the build with the temporary properties and direct JVM control
echo "Building with forced JVM 17..."
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
KOTLIN_DAEMON_JVM_OPTS="-Dkotlin.daemon.jvm.options=-Xmx2048m -Dkotlin.jvm.target=17" \
./gradlew --no-daemon \
  --init-script /dev/stdin \
  -Dorg.gradle.jvmargs="-Xmx2048m" \
  -Dkotlin.jvm.target=17 \
  -Dkotlin.jvm.target.validation=ignore \
  -Dkotlin.daemon.jvmargs="-Xmx2048m" \
  -Dkotlin.compiler.execution.strategy=in-process \
  -Dkotlin.incremental=false \
  -Dkapt.use.worker.api=false \
  -Dkapt.incremental.apt=false \
  -Dkapt.kotlin.jvm.target=17 \
  :app:assembleDebug \
<<EOF
allprojects {
    tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach {
        kotlinOptions {
            jvmTarget = "17"
        }
    }
    tasks.withType(org.jetbrains.kotlin.gradle.internal.KaptWithoutKotlincTask).configureEach {
        kotlinOptions {
            jvmTarget = "17"
        }
    }
    tasks.withType(org.jetbrains.kotlin.gradle.tasks.KaptGenerateStubsTask).configureEach {
        kotlinOptions {
            jvmTarget = "17"
        }
    }
}
EOF

# Store the build status
BUILD_STATUS=$?

# Restore original build.gradle (optional - comment this out if you want to keep the simplified one)
# git checkout -- ./app/build.gradle

echo "Build completed with status: $BUILD_STATUS"
exit $BUILD_STATUS 