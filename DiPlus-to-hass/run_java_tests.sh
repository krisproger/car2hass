#!/bin/bash
set -euo pipefail

PROJECT=$(cd "$(dirname "$0")" && pwd)
SRC="$PROJECT/app/src/main/java"
TEST="$PROJECT/app/src/test/java"
OUT="$PROJECT/build/test-classes"

SDK=${ANDROID_HOME:-/opt/AndroidStudio/Android/sdk}
ANDROID_JAR=$SDK/platforms/android-35/android.jar
JSON_JAR=~/.gradle/caches/modules-2/files-2.1/org.json/json/20180813/8566b2b0391d9d4479ea225645c6ed47ef17fe41/json-20180813.jar
if [ ! -f "$ANDROID_JAR" ]; then
    echo "ERROR: android.jar not found at $ANDROID_JAR" >&2
    echo "Set ANDROID_HOME to a valid Android SDK path." >&2
    exit 1
fi

rm -rf "$OUT"
mkdir -p "$OUT"

echo "=== Compiling project log buffer ==="
javac -d "$OUT" -classpath "$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/LogBuffer.java"

echo "=== Compiling SignalTranslator ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/SignalTranslator.java"

echo "=== Compiling tests ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/SignalTranslatorTest.java"

echo "=== Overriding with test-only LogBuffer stub ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/LogBuffer.java"

echo "=== Compiling NetSafety ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/NetSafety.java"

echo "=== Compiling NetSafetyTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/NetSafetyTest.java"

echo "=== Compiling DiplusApi ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/DiplusApi.java"

echo "=== Compiling DiplusApiTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/DiplusApiTest.java"

echo "=== Compiling DashboardLogic ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/DashboardLogic.java"

echo "=== Compiling DashboardLogicTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/DashboardLogicTest.java"

echo "=== Compiling RuleOperator ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/rules/RuleOperator.java"

echo "=== Compiling JSON model classes (no Android dep) ==="
javac -d "$OUT" -classpath "$OUT:$JSON_JAR" -source 17 -target 17 \
  "$SRC/com/diplustohass/rules/LogicalOperator.java" \
  "$SRC/com/diplustohass/rules/RuleCondition.java" \
  "$SRC/com/diplustohass/rules/RuleAction.java" \
  "$SRC/com/diplustohass/rules/Rule.java"

echo "=== Compiling RuleEvaluator ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR:$JSON_JAR" -source 17 -target 17 "$SRC/com/diplustohass/rules/RuleEvaluator.java"

echo "=== Compiling AntiLoopGuard ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/rules/AntiLoopGuard.java"

echo "=== Compiling rules tests ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/RuleOperatorTest.java"
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/RuleEvaluatorTest.java"
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/AntiLoopGuardTest.java"

echo "=== Compiling new model tests ==="
javac -d "$OUT" -classpath "$OUT:$JSON_JAR" -source 17 -target 17 \
  "$TEST/com/diplustohass/rules/NewModelTest.java" \
  "$TEST/com/diplustohass/rules/RuleMigrationTest.java"

echo "=== Compiling SensorValueHistory ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/SensorValueHistory.java"

echo "=== Compiling SensorValueHistoryTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/SensorValueHistoryTest.java"

echo "=== Compiling GeofenceZone (JSON model, no Android dep) ==="
javac -d "$OUT" -classpath "$OUT:$JSON_JAR" -source 17 -target 17 "$SRC/com/diplustohass/GeofenceZone.java"

echo "=== Compiling GeofenceZoneTest ==="
javac -d "$OUT" -classpath "$OUT:$JSON_JAR" -source 17 -target 17 "$TEST/com/diplustohass/GeofenceZoneTest.java"

echo "=== Compiling PresetIconResolver ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/PresetIconResolver.java"

echo "=== Compiling PresetIconResolverTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/PresetIconResolverTest.java"

echo "=== Running tests ==="
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.SignalTranslatorTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.NetSafetyTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.DiplusApiTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.DashboardLogicTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.RuleOperatorTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.RuleEvaluatorTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.AntiLoopGuardTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.diplustohass.rules.NewModelTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.diplustohass.rules.RuleMigrationTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.SensorValueHistoryTest
java -cp "$OUT:$JSON_JAR" com.diplustohass.GeofenceZoneTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.PresetIconResolverTest
