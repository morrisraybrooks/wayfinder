#!/bin/bash

# Script to configure Android Studio VM options for Java module access

# Find the Android Studio VM options file
STUDIO_HOME="$HOME/.config/Google/AndroidStudio2022.3"
VM_OPTIONS_FILE="$STUDIO_HOME/studio64.vmoptions"

# Create the directory if it doesn't exist
mkdir -p "$STUDIO_HOME"

# Check if the file exists
if [ -f "$VM_OPTIONS_FILE" ]; then
    echo "Backing up existing VM options file to ${VM_OPTIONS_FILE}.bak"
    cp "$VM_OPTIONS_FILE" "${VM_OPTIONS_FILE}.bak"
fi

# Create the VM options file with the necessary options
cat > "$VM_OPTIONS_FILE" << EOL
-Xms256m
-Xmx2048m
-XX:ReservedCodeCacheSize=512m
-XX:+UseG1GC
-XX:SoftRefLRUPolicyMSPerMB=50
-XX:CICompilerCount=2
-Dsun.io.useCanonCaches=false
-Djdk.http.auth.tunneling.disabledSchemes=""
-Djdk.attach.allowAttachSelf=true
-Djdk.module.illegalAccess.silent=true
-Dkotlinx.coroutines.debug=off
-Djna.nosys=true
-Djna.boot.library.path=
-Didea.vendor.name=Google

# Add Java module access options
--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED
--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
--add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED
--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED
--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED
--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED
--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
EOL

echo "Android Studio VM options configured successfully."
echo "Please restart Android Studio for the changes to take effect."

# Also create a gradle.properties file in the project
cat > gradle.properties << EOL
# Project-wide Gradle settings.
# IDE (e.g. Android Studio) users:
# Gradle settings configured through the IDE *will override*
# any settings specified in this file.
# For more details on how to configure your build environment visit
# http://www.gradle.org/docs/current/userguide/build_environment.html
# Specifies the JVM arguments used for the daemon process.
# The setting is particularly useful for tweaking memory settings.
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 --add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
# When configured, Gradle will run in incubating parallel mode.
# This option should only be used with decoupled projects. More details, visit
# http://www.gradle.org/docs/current/userguide/multi_project_builds.html#sec:decoupled_projects
# org.gradle.parallel=true
# AndroidX package structure to make it clearer which packages are bundled with the
# Android operating system, and which are packaged with your app's APK
# https://developer.android.com/topic/libraries/support-library/androidx-rn
android.useAndroidX=true
# Kotlin code style for this project: "official" or "obsolete":
kotlin.code.style=official
# Enables namespacing of each library's R class so that its R class includes only the
# resources declared in the library itself and none from the library's dependencies,
# thereby reducing the size of the R class for that library
android.nonTransitiveRClass=true

# Explicit JVM target settings
kotlin.jvm.target=17
kotlin.jvm.target.validation=ignore
# Needed to avoid JDK compatibility issues
kotlin.stdlib.jdk.variant=17

# Disable incremental compilation for Kotlin
kotlin.incremental=false
# Disable KAPT incremental processing
kapt.incremental.apt=false
kapt.use.worker.api=false
kapt.include.compile.classpath=false

# Gradle daemon settings
org.gradle.daemon=true
org.gradle.configureondemand=true
EOL

echo "Project gradle.properties file updated."

# Create a local.properties file with the correct SDK path
if [ ! -f local.properties ]; then
    ANDROID_SDK_PATH=$(echo $ANDROID_HOME)
    if [ -z "$ANDROID_SDK_PATH" ]; then
        ANDROID_SDK_PATH="$HOME/Android/Sdk"
    fi
    
    echo "sdk.dir=$ANDROID_SDK_PATH" > local.properties
    echo "Created local.properties with SDK path: $ANDROID_SDK_PATH"
fi

# Create a script to run the build with the correct environment variables
cat > build-with-jvm-args.sh << EOL
#!/bin/bash

# Set the necessary JVM arguments
export _JAVA_OPTIONS="--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED"

# Clean and build the project
./gradlew clean assembleDebug --no-daemon

echo "Build completed. APK location: app/build/outputs/apk/debug/app-debug.apk"
EOL

chmod +x build-with-jvm-args.sh
echo "Created build-with-jvm-args.sh script."

echo "Setup complete. Please follow these steps:"
echo "1. Close Android Studio if it's running"
echo "2. Run the build-with-jvm-args.sh script to build from command line"
echo "3. Restart Android Studio and try building again"
