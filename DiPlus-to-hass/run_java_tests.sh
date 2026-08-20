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

echo "=== Compiling DiplusUnavailableException ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/DiplusUnavailableException.java"

echo "=== Compiling DiplusErrorClassifier ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/DiplusErrorClassifier.java"

echo "=== Compiling UnsupportedRecoveryGate ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/UnsupportedRecoveryGate.java"

echo "=== Compiling classifier/recovery tests ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/DiplusErrorClassifierTest.java"
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/UnsupportedRecoveryGateTest.java"

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

echo "=== Compiling LogDedup (export-log dedup, no Android dep) ==="
javac -d "$OUT" -source 17 -target 17 "$SRC/com/diplustohass/LogDedup.java"

echo "=== Compiling LogDedupTest ==="
javac -d "$OUT" -classpath "$OUT" -source 17 -target 17 "$TEST/com/diplustohass/LogDedupTest.java"

echo "=== Compiling PresetParamValues (JSON model, no Android dep) ==="
javac -d "$OUT" -classpath "$OUT:$JSON_JAR" -source 17 -target 17 "$SRC/com/diplustohass/PresetParamValues.java"

echo "=== Compiling PresetParamValuesTest ==="
javac -d "$OUT" -classpath "$OUT:$JSON_JAR" -source 17 -target 17 "$TEST/com/diplustohass/PresetParamValuesTest.java"

echo "=== Compiling SentinelDecoder ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/SentinelDecoder.java"

echo "=== Compiling ParamDecoder ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/ParamDecoder.java"

echo "=== Compiling decoder tests ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/SentinelDecoderTest.java"
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/ParamDecoderTest.java"

echo "=== Compiling NativeSignalMap ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/NativeSignalMap.java"

echo "=== Compiling NativeSignalMapTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/NativeSignalMapTest.java"

echo "=== Compiling NativeCommandBuilder ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/NativeCommandBuilder.java"

echo "=== Compiling NativeOutputParser ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/NativeOutputParser.java"

echo "=== Compiling builder/parser tests ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/NativeCommandBuilderTest.java"
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/NativeOutputParserTest.java"

echo "=== Compiling NativeSyncGate (pure Java, no Android dep) ==="
javac -d "$OUT" -source 17 -target 17 "$SRC/com/diplustohass/NativeSyncGate.java"

echo "=== Compiling NativeSyncGateTest ==="
javac -d "$OUT" -classpath "$OUT" -source 17 -target 17 "$TEST/com/diplustohass/NativeSyncGateTest.java"

echo "=== Compiling CANDataItem ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/CANDataItem.java"

echo "=== Compiling NativeReader ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/NativeReader.java"

echo "=== Compiling NativeReaderTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/NativeReaderTest.java"

echo "=== Compiling NativeCommandWriter ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/NativeCommandWriter.java"

echo "=== Compiling NativeCommandWriterTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/NativeCommandWriterTest.java"

echo "=== Compiling SensorCommandMapTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR:$JSON_JAR" -source 17 -target 17 "$TEST/com/diplustohass/SensorCommandMapTest.java"

echo "=== Compiling test-only R stub ==="
javac -d "$OUT" -classpath "$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/R.java"

echo "=== Compiling CommandRegistry (with R stub) ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/CommandRegistry.java"

echo "=== Compiling NativeCommandMap ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR:$JSON_JAR" -source 17 -target 17 "$SRC/com/diplustohass/NativeCommandMap.java"

echo "=== Compiling NativeCommandMapTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR:$JSON_JAR" -source 17 -target 17 "$TEST/com/diplustohass/NativeCommandMapTest.java"

echo "=== Compiling CommandWriter (with R stub + CommandRegistry) ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR:$JSON_JAR" -source 17 -target 17 "$SRC/com/diplustohass/CommandWriter.java"

echo "=== Compiling TestUtil ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR:$JSON_JAR" -source 17 -target 17 "$TEST/com/diplustohass/TestUtil.java"

echo "=== Compiling CommandWriter tests ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR:$JSON_JAR" -source 17 -target 17 "$TEST/com/diplustohass/CommandWriterTest.java"
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR:$JSON_JAR" -source 17 -target 17 "$TEST/com/diplustohass/CommandWriterFallbackTest.java"

echo "=== Compiling VehicleProfile ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/vehicle/VehicleProducer.java" "$SRC/com/diplustohass/vehicle/VehicleProfile.java"

echo "=== Compiling VehicleProfileTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/vehicle/VehicleProfileTest.java"

echo "=== Compiling ChannelResult ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/vehicle/ChannelResult.java" "$SRC/com/diplustohass/vehicle/DataChannel.java"

echo "=== Compiling ChannelResultTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/vehicle/ChannelResultTest.java"

echo "=== Compiling VehicleProfileDetect ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/vehicle/VehicleProfileDetect.java"

echo "=== Compiling VehicleProfileDetectTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/vehicle/VehicleProfileDetectTest.java"

echo "=== Compiling test-only BuildConfig stub ==="
javac -d "$OUT" -classpath "$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/BuildConfig.java"

echo "=== Compiling VehicleResearch ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/diplustohass/AppInfo.java" "$SRC/com/diplustohass/vehicle/VehicleResearch.java"

echo "=== Compiling VehicleResearchTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/diplustohass/vehicle/VehicleResearchTest.java"

echo "=== Running tests ==="
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.SignalTranslatorTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.NetSafetyTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.DiplusApiTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.DiplusErrorClassifierTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.UnsupportedRecoveryGateTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.DashboardLogicTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.RuleOperatorTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.RuleEvaluatorTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.AntiLoopGuardTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.diplustohass.rules.NewModelTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.diplustohass.rules.RuleMigrationTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.SensorValueHistoryTest
java -cp "$OUT:$JSON_JAR" com.diplustohass.GeofenceZoneTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.PresetIconResolverTest
java -cp "$OUT" com.diplustohass.LogDedupTest
java -cp "$OUT:$JSON_JAR" com.diplustohass.PresetParamValuesTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.SentinelDecoderTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.ParamDecoderTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.NativeSignalMapTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.NativeCommandBuilderTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.NativeOutputParserTest
java -cp "$OUT" com.diplustohass.NativeSyncGateTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.NativeReaderTest
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.NativeCommandWriterTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.diplustohass.SensorCommandMapTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.diplustohass.NativeCommandMapTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.diplustohass.CommandWriterTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.diplustohass.CommandWriterFallbackTest

echo "=== Running VehicleProfileTest ==="
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.vehicle.VehicleProfileTest

echo "=== Running ChannelResultTest ==="
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.vehicle.ChannelResultTest

echo "=== Running VehicleProfileDetectTest ==="
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.vehicle.VehicleProfileDetectTest

echo "=== Running VehicleResearchTest ==="
java -cp "$OUT:$ANDROID_JAR" com.diplustohass.vehicle.VehicleResearchTest
