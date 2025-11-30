# Wayfinder App Setup Instructions

## Project Overview

Wayfinder is an Android application designed to assist blind users in navigating their environment, recognizing objects, and reading text. The app leverages the device's camera and machine learning capabilities to provide real-time audio feedback about the user's surroundings.

## Current Status

The project currently has 7 of the 18 planned features implemented. See `implementation_status.md` for details on which features are implemented and which are pending.

## Setup Instructions

### Prerequisites

1. **Install Android Studio**
   - Download and install the latest version of Android Studio from [developer.android.com](https://developer.android.com/studio)
   - Make sure to install the Android SDK during the installation process

2. **Install Required SDK Components**
   - Open Android Studio
   - Go to Tools > SDK Manager
   - Install Android SDK Platform 33 (or latest)
   - Install Android SDK Build-Tools 33.0.0 (or latest)
   - Install Android Emulator
   - Install Android SDK Platform-Tools

### Opening the Project

1. Open Android Studio
2. Select "Open an Existing Project"
3. Navigate to the Wayfinder project directory and select it
4. Wait for Gradle sync to complete

### Building the Project

1. Once the project is open, select Build > Make Project
2. If there are any build errors, check the "Build" tab for details
3. Most common issues have been fixed, but you may need to adjust some settings based on your environment

### Running the App

1. Connect an Android device with USB debugging enabled
   - Go to Settings > About Phone > Tap "Build Number" 7 times to enable Developer Options
   - Go to Settings > Developer Options > Enable USB Debugging
   - Connect the device to your computer via USB
   - Allow USB debugging when prompted on the device

2. Or set up an emulator
   - Go to Tools > AVD Manager
   - Create a new virtual device
   - Select a device definition (e.g., Pixel 4)
   - Select a system image (e.g., Android 13)
   - Configure the AVD and finish

3. Run the app
   - Select Run > Run 'app'
   - Select your device or emulator
   - The app should install and launch

## Completing the Implementation

To complete the implementation of all features:

1. Implement the remaining 11 activity classes in `app/src/main/java/com/wayfinder/app/`
2. Create layouts for the remaining 11 features in `app/src/main/res/layout/`
3. Update the MainActivity to properly handle all features
4. Test each feature individually

## Troubleshooting

If you encounter build issues:

1. **Gradle Sync Failed**
   - Check that you have the correct JDK version (Java 17)
   - Make sure all dependencies are available
   - Try File > Invalidate Caches / Restart

2. **Missing Dependencies**
   - Make sure you have an internet connection
   - Check that the repositories in build.gradle are accessible

3. **Compilation Errors**
   - Check the error messages in the "Build" tab
   - Make sure all required imports are available
   - Check for syntax errors in the code

4. **Runtime Errors**
   - Check the Logcat tab for error messages
   - Make sure all required permissions are granted
   - Check that the device has the necessary hardware (camera, etc.)
