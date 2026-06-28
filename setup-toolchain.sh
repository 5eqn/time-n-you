#!/usr/bin/env bash
# Installs OpenJDK 17 + Android SDK (command-line tools, platform-tools,
# android-34 platform, build-tools 34.0.0). Logs to setup-toolchain.log.
set -euxo pipefail
export DEBIAN_FRONTEND=noninteractive

LOG=/home/seqn/time-watcher/setup-toolchain.log
exec > >(tee -a "$LOG") 2>&1
echo "=== toolchain setup start $(date) ==="

sudo apt-get update -qq
sudo apt-get install -y openjdk-17-jdk unzip

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/home/seqn/Android/Sdk
mkdir -p "$ANDROID_HOME/cmdline-tools"

cd /tmp
curl -fL -o cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q -o cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools"
# zip extracts a top-level "cmdline-tools" dir; rename it to "latest"
if [ -d "$ANDROID_HOME/cmdline-tools/cmdline-tools" ]; then
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi

export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH"

java -version
sdkmanager --version

yes | sdkmanager --licenses > /dev/null
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

echo "ANDROID_HOME=$ANDROID_HOME"
echo "=== TOOLCHAIN_DONE ==="
