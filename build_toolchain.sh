#!/usr/bin/env bash
# Восстановление тулчейна GSYNK после снапшота (JDK 17 + Android SDK + swap).
set -e
export HOME=/home/user
mkdir -p /opt/tools/tmp /opt/tools/gradle-home /opt/tools/android-sdk

# ——— swap (сборка Compose требует памяти) ———
if ! swapon --show 2>/dev/null | grep -q swapfile; then
  sudo fallocate -l 4G /swapfile || sudo dd if=/dev/zero of=/swapfile bs=1M count=4096
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile >/dev/null
  sudo swapon /swapfile
fi
echo "swap: $(free -m | awk '/Swap/ {print $2" MB"}')"

# ——— JDK 17 ———
if [ ! -x /opt/tools/jdk17/bin/javac ]; then
  echo "качаю JDK 17…"
  curl -sL -o /opt/tools/jdk17.tar.gz \
    "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
  mkdir -p /opt/tools/jdk17
  tar xzf /opt/tools/jdk17.tar.gz -C /opt/tools/jdk17 --strip-components=1
  rm -f /opt/tools/jdk17.tar.gz
fi
export JAVA_HOME=/opt/tools/jdk17
echo "java: $(/opt/tools/jdk17/bin/java -version 2>&1 | head -1)"

# ——— Android SDK ———
export ANDROID_HOME=/opt/tools/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
if [ ! -x /opt/tools/android-sdk/cmdline-tools/latest/bin/sdkmanager ]; then
  echo "качаю cmdline-tools…"
  curl -sL -o /opt/tools/cmdtools.zip \
    "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  mkdir -p /opt/tools/android-sdk/cmdline-tools
  unzip -q -o /opt/tools/cmdtools.zip -d /opt/tools/android-sdk/cmdline-tools
  mv /opt/tools/android-sdk/cmdline-tools/cmdline-tools /opt/tools/android-sdk/cmdline-tools/latest
  rm -f /opt/tools/cmdtools.zip
fi

if [ ! -d /opt/tools/android-sdk/platforms/android-36 ]; then
  echo "ставлю platforms;android-36 + build-tools…"
  yes | /opt/tools/android-sdk/cmdline-tools/latest/bin/sdkmanager \
    --sdk_root=$ANDROID_HOME --licenses >/dev/null 2>&1 || true
  /opt/tools/android-sdk/cmdline-tools/latest/bin/sdkmanager \
    --sdk_root=$ANDROID_HOME "platform-tools" "platforms;android-36" "build-tools;36.0.0"
fi

echo "sdk: $(ls /opt/tools/android-sdk/platforms 2>/dev/null | tr '\n' ' ')"
echo "TOOLCHAIN_READY"
