#!/bin/bash

# GitHub Upload Script for Wayfinder Project
# This script uses GitHub CLI for secure authentication

echo "=== GitHub Upload Script for Wayfinder ==="
echo "This script helps you upload your project to GitHub securely"
echo ""

# Check if GitHub CLI is installed
if ! command -v gh &> /dev/null; then
    echo "GitHub CLI not found. Would you like to install it? (y/n)"
    read -r install_cli
    
    if [[ $install_cli == "y" ]]; then
        echo "Installing GitHub CLI..."
        # Instructions for Ubuntu
        curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | sudo dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg
        echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" | sudo tee /etc/apt/sources.list.d/github-cli.list > /dev/null
        sudo apt update
        sudo apt install gh
    else
        echo "GitHub CLI is required for this script. Please install it manually and try again."
        echo "Visit: https://github.com/cli/cli#installation"
        exit 1
    fi
fi

# Initialize Git if needed
if [ ! -d .git ]; then
    echo "Initializing Git repository..."
    git init
    
    # Create .gitignore file
    echo "Creating .gitignore file..."
    cat > .gitignore << 'EOL'
# Gradle files
.gradle/
build/
.idea/
*.iml
local.properties

# Generated files
bin/
gen/
out/

# Log Files
*.log

# Android Studio Navigation
.navigation/

# Android Studio captures folder
captures/

# IntelliJ
*.iml
*.ipr
*.iws
.idea/
misc.xml
deploymentTargetDropDown.xml
render.experimental.xml

# Keystore files
*.jks
*.keystore

# External native build folder
.externalNativeBuild
.cxx/

# Google Services (e.g. APIs or Firebase)
google-services.json

# Version control
vcs.xml

# Temporary build files
*.tmp
temp_gradle.properties
*.tmp.gradle

# MacOS
.DS_Store

# Linux
*~
EOL
fi

# Create README if it doesn't exist
if [ ! -f README.md ]; then
    echo "Creating README.md file..."
    cat > README.md << 'EOL'
# Wayfinder

An Android application designed to enhance accessibility for users with various needs.

## Features

- Object recognition and detection
- Text recognition
- Face detection and recognition
- Barcode scanning
- Emergency SOS capabilities
- Navigation assistance
- Gesture control
- Sound recognition

## Tech Stack

- Kotlin
- Android Jetpack
- CameraX
- ML Kit
- Room Database
- Retrofit
EOL
fi

# Login to GitHub using GitHub CLI
echo "Logging in to GitHub..."
echo "You'll be prompted to authenticate in your browser."
gh auth login -w

# Set up Git identity based on GitHub user info
echo "Setting up Git identity..."
# Get the GitHub username and email from GitHub CLI
GITHUB_USERNAME=$(gh api user | jq -r .login)
GITHUB_EMAIL=$(gh api user/emails | jq -r '.[0].email')

if [ -z "$GITHUB_EMAIL" ]; then
    echo "Could not get your email from GitHub. Please enter your email:"
    read -r GITHUB_EMAIL
fi

# Set Git configuration for this repository
git config user.name "$GITHUB_USERNAME"
git config user.email "$GITHUB_EMAIL"
echo "Git identity set to: $GITHUB_USERNAME <$GITHUB_EMAIL>"

# Create GitHub repository
echo "Creating GitHub repository 'wayfinder'..."
gh repo create wayfinder --private --source=. --remote=origin

# Add files to Git and make initial commit if needed
echo "Adding files to Git..."
git add .
git commit -m "Initial commit: Wayfinder Android accessibility app"

# Push to GitHub
echo "Pushing to GitHub..."
git push -u origin HEAD

echo "=== Upload Complete ==="
echo "Your Wayfinder project has been uploaded to GitHub."
echo "You can view it at: https://github.com/$GITHUB_USERNAME/wayfinder"

exit 0 