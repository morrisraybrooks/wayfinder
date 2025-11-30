#!/bin/bash

# Script to build the app in Android Studio

echo "Setting up the build environment for Android Studio..."

# 1. Make sure we're using the no-KAPT configuration
./switch-build-config.sh no-kapt

# 2. Set the necessary environment variables
export _JAVA_OPTIONS="--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED"

# 3. Create a .env file for Android Studio to read
cat > .env << EOL
_JAVA_OPTIONS="--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED"
EOL

echo "Environment set up. Now you can:"
echo "1. Open Android Studio"
echo "2. Import the project"
echo "3. Build the project using the 'Build > Make Project' menu option"
echo ""
echo "If you want to build from the command line, run:"
echo "./gradlew clean assembleDebug --no-daemon"
echo ""
echo "The APK will be generated at: app/build/outputs/apk/debug/app-debug.apk"
