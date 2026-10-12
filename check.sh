#!/usr/bin/env bash
# Проверка GSYNK: тесты + lint + сборка APK.
# Использование: bash check.sh [задачи gradle...]   (по умолчанию testDebugUnitTest lintDebug assembleDebug)
set -o pipefail
export HOME=/home/user
export JAVA_HOME=/opt/tools/jdk17
export ANDROID_HOME=/opt/tools/android-sdk
export ANDROID_SDK_ROOT=/opt/tools/android-sdk
export GRADLE_USER_HOME=/opt/tools/gradle-home
export TMPDIR=/opt/tools/tmp

TASKS="$*"
if [ -z "$TASKS" ]; then
  TASKS="testDebugUnitTest lintDebug assembleDebug"
fi

cd /home/user/GSYNK
bash gradlew $TASKS --no-daemon --max-workers=1 --console=plain \
  -Pkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.jvmargs="-Xmx1500m -XX:MaxMetaspaceSize=400m -XX:+UseSerialGC"
