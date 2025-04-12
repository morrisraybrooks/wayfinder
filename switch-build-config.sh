#!/bin/bash

# Script to switch between build configurations

if [ "$1" == "no-kapt" ]; then
    echo "Switching to no-kapt build configuration..."
    cp app/build.gradle app/build.gradle.with-kapt
    cp app/build.gradle.no-kapt app/build.gradle
    echo "Done. The build.gradle file has been updated to use the no-kapt configuration."
    echo "Run './gradlew clean assembleDebug' to build the project."
elif [ "$1" == "with-kapt" ]; then
    if [ -f app/build.gradle.with-kapt ]; then
        echo "Switching to with-kapt build configuration..."
        cp app/build.gradle.with-kapt app/build.gradle
        echo "Done. The build.gradle file has been updated to use the with-kapt configuration."
        echo "Run './gradlew clean assembleDebug' to build the project."
    else
        echo "Error: app/build.gradle.with-kapt not found. Run 'switch-build-config.sh no-kapt' first."
    fi
else
    echo "Usage: switch-build-config.sh [no-kapt|with-kapt]"
    echo "  no-kapt: Switch to build configuration without KAPT"
    echo "  with-kapt: Switch back to build configuration with KAPT"
fi
