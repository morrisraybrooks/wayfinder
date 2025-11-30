#!/bin/bash

# Script to build the app with the correct environment variables

# Set the necessary JVM arguments
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
    echo "Build failed. Trying alternative build configuration..."
    
    # Try the no-kapt configuration
    ./switch-build-config.sh no-kapt
    
    # Build again
    ./gradlew clean assembleDebug --no-daemon
    
    if [ $? -eq 0 ]; then
        echo "Alternative build successful! APK location: app/build/outputs/apk/debug/app-debug.apk"
        
        # Ask if the user wants to install the APK
        read -p "Do you want to install the APK on a connected device? (y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            adb install -r app/build/outputs/apk/debug/app-debug.apk
        fi
    else
        echo "Both build configurations failed. Please check the error messages above."
    fi
fi
