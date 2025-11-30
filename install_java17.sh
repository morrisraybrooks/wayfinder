#!/bin/bash

# Define the JDK archive path
JDK_ARCHIVE="/home/morris/Downloads/OpenJDK17U-jdk_x64_linux_hotspot_17.0.17_10.tar.gz"

# Check if the archive exists
if [ ! -f "$JDK_ARCHIVE" ]; then
    echo "Error: JDK archive not found at $JDK_ARCHIVE"
    echo "Please ensure the file is downloaded to this location or update the script with the correct path."
    exit 1
fi

# Prompt for installation directory with a default
read -p "Enter the desired installation directory for Java 17 (e.g., /opt/java/jdk-17, default: $HOME/jdk-17): " INSTALL_DIR
INSTALL_DIR=${INSTALL_DIR:-"$HOME/jdk-17"}

echo "Installing Java 17 to: $INSTALL_DIR"

# Create the installation directory if it doesn't exist
mkdir -p "$INSTALL_DIR" || { echo "Error: Could not create installation directory $INSTALL_DIR"; exit 1; }

# Extract the JDK
echo "Extracting JDK archive..."
tar -xzf "$JDK_ARCHIVE" -C "$INSTALL_DIR" --strip-components=1 || { echo "Error: Failed to extract JDK archive"; exit 1; }

echo "Java 17 installed successfully to $INSTALL_DIR"

# Set JAVA_HOME and update PATH for the current session
export JAVA_HOME="$INSTALL_DIR"
export PATH="$JAVA_HOME/bin:$PATH"

echo ""
echo "--------------------------------------------------------"
echo "Java 17 is now set up for your current terminal session."
echo "JAVA_HOME is set to: $JAVA_HOME"
echo "PATH is updated to include: $JAVA_HOME/bin"
echo "--------------------------------------------------------"
echo ""
echo "To make these changes permanent, you need to add the following lines"
echo "to your shell's profile file (e.g., ~/.bashrc, ~/.zshrc, or ~/.profile):"
echo ""
echo 'export JAVA_HOME="'"$INSTALL_DIR"'"'
echo 'export PATH="$JAVA_HOME/bin:$PATH"'
echo ""
echo "After adding these lines, restart your terminal or run 'source ~/.bashrc' (or your respective profile file)."
echo ""
echo "Once Java 17 is permanently configured, please try building the Wayfinder project again:"
echo "cd /home/morris/Desktop/wayfinder-master"
echo "./gradlew clean assembleDebug"
