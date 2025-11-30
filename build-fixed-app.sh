#!/bin/bash

# Script to build the app with fixed dependencies

echo "Building the app with fixed dependencies..."

# Set the necessary environment variables
export _JAVA_OPTIONS="--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED"

# Clean and build the project
./gradlew clean assembleDebug --no-daemon

# Check if the build was successful
if [ $? -eq 0 ]; then
    echo "Build successful! APK location: app/build/outputs/apk/debug/app-debug.apk"
    
    # Ask if the user wants to install the APK
    read -p "Do you want to install the APK on a connected device? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        adb install -r app/build/outputs/apk/debug/app-debug.apk
    fi
else
    echo "Build failed. Please check the error messages above."
fi
