#!/bin/bash

echo "Checking project structure..."

# Check if the main directories exist
if [ -d "app/src/main/java/com/wayfinder/app" ]; then
    echo "✓ Main Java directory exists"
else
    echo "✗ Main Java directory missing"
fi

if [ -d "app/src/main/res/layout" ]; then
    echo "✓ Layout directory exists"
else
    echo "✗ Layout directory missing"
fi

if [ -d "app/src/main/res/values" ]; then
    echo "✓ Values directory exists"
else
    echo "✗ Values directory missing"
fi

# Check if key files exist
if [ -f "app/src/main/AndroidManifest.xml" ]; then
    echo "✓ AndroidManifest.xml exists"
else
    echo "✗ AndroidManifest.xml missing"
fi

if [ -f "app/build.gradle" ]; then
    echo "✓ app/build.gradle exists"
else
    echo "✗ app/build.gradle missing"
fi

if [ -f "build.gradle" ]; then
    echo "✓ build.gradle exists"
else
    echo "✗ build.gradle missing"
fi

if [ -f "gradle.properties" ]; then
    echo "✓ gradle.properties exists"
else
    echo "✗ gradle.properties missing"
fi

# Check if main activity files exist
if [ -f "app/src/main/java/com/wayfinder/app/MainActivity.kt" ]; then
    echo "✓ MainActivity.kt exists"
else
    echo "✗ MainActivity.kt missing"
fi

if [ -f "app/src/main/res/layout/activity_main.xml" ]; then
    echo "✓ activity_main.xml exists"
else
    echo "✗ activity_main.xml missing"
fi

# Count the number of activities
activity_count=$(find app/src/main/java/com/wayfinder/app -name "*Activity.kt" | wc -l)
echo "Number of Activity files: $activity_count"

# Count the number of layout files
layout_count=$(find app/src/main/res/layout -name "activity_*.xml" | wc -l)
echo "Number of layout files: $layout_count"

echo "Structure check complete."
