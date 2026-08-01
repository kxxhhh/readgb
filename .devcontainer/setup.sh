#!/bin/bash

set -e


echo "===== System update ====="

sudo apt update

sudo apt install -y \
wget \
curl \
unzip \
zip \
git \
jq \
python3-pip \
build-essential



echo "===== Android SDK ====="


ANDROID_HOME=/home/vscode/android-sdk


mkdir -p $ANDROID_HOME/cmdline-tools


cd /tmp


wget -q \
https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip


unzip -q commandlinetools-linux-*.zip \
-d $ANDROID_HOME/cmdline-tools


mv \
$ANDROID_HOME/cmdline-tools/cmdline-tools \
$ANDROID_HOME/cmdline-tools/latest



echo "export ANDROID_HOME=$ANDROID_HOME" >> ~/.bashrc

echo "export ANDROID_SDK_ROOT=$ANDROID_HOME" >> ~/.bashrc

echo 'export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin' >> ~/.bashrc



export ANDROID_HOME=$ANDROID_HOME


yes | sdkmanager --licenses



sdkmanager \
"platform-tools" \
"platforms;android-35" \
"build-tools;35.0.0"



echo "===== Gradle ====="


sudo apt install -y gradle



echo "===== Kotlin ====="


curl -s https://get.sdkman.io | bash || true


source ~/.sdkman/bin/sdkman-init.sh || true


sdk install kotlin || true



echo "===== Codex ====="


npm install -g @openai/codex



echo "===== Python AI crawler ====="


pip3 install --user \
requests \
httpx \
beautifulsoup4 \
lxml \
scrapy \
playwright \
fastapi \
uvicorn



playwright install chromium



echo "===== Codex config ====="


mkdir -p ~/.codex


cp .codex/config.toml ~/.codex/config.toml



echo "===== Verify ====="


java -version

node -v

python3 --version

gradle -v

adb version

codex --version



echo "================================"
echo " Android + Codex Environment Ready "
echo "================================"
