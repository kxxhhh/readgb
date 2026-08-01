#!/bin/bash

set -e


echo "=== Installing Android SDK ==="


sudo apt update


sudo apt install -y \
wget \
unzip \
curl \
git \
python3-pip \
build-essential


mkdir -p $HOME/android-sdk


cd /tmp


wget -q \
https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip


mkdir -p $HOME/android-sdk/cmdline-tools


unzip -q commandlinetools-linux-*.zip \
-d $HOME/android-sdk/cmdline-tools


mv $HOME/android-sdk/cmdline-tools/cmdline-tools \
$HOME/android-sdk/cmdline-tools/latest


export ANDROID_HOME=$HOME/android-sdk


echo 'export ANDROID_HOME=$HOME/android-sdk' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools' >> ~/.bashrc


yes | sdkmanager --licenses


sdkmanager \
"platform-tools" \
"platforms;android-35" \
"build-tools;35.0.0"


echo "=== Installing Gradle ==="


sudo apt install -y gradle


echo "=== Installing Kotlin ==="


curl -s https://get.sdkman.io | bash || true


echo "=== Installing Codex ==="


npm install -g @openai/codex


echo "=== Installing Python crawler tools ==="


pip3 install --user \
requests \
beautifulsoup4 \
lxml \
scrapy \
playwright \
fastapi \
uvicorn


playwright install chromium


echo "DONE"