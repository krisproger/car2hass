#!/bin/bash
set -euo pipefail

PROJECT=$(cd "$(dirname "$0")" && pwd)
SRC="$PROJECT/app/src/main/java"
TEST="$PROJECT/app/src/test/java"
OUT="$PROJECT/build/test-classes"

SDK=${ANDROID_HOME:-/opt/AndroidStudio/Android/sdk}
ANDROID_JAR=$SDK/platforms/android-35/android.jar
# org.json is needed by the JSON-model tests. Resolve it robustly: fixed gradle
# cache path first (local dev), then search the cache, then download from Maven
# Central (CI runners have no gradle cache at this point).
JSON_JAR=${JSON_JAR:-$HOME/.gradle/caches/modules-2/files-2.1/org.json/json/20180813/8566b2b0391d9d4479ea225645c6ed47ef17fe41/json-20180813.jar}
if [ ! -f "$JSON_JAR" ] && [ -d "$HOME/.gradle" ]; then
    FOUND=$(find "$HOME/.gradle" -path "*org.json/json*" -name "json-*.jar" 2>/dev/null | head -1 || true)
    if [ -n "$FOUND" ]; then
        JSON_JAR="$FOUND"
    fi
fi
if [ ! -f "$JSON_JAR" ]; then
    mkdir -p "$PROJECT/build/libs"
    JSON_JAR="$PROJECT/build/libs/json-20180813.jar"
    if [ ! -f "$JSON_JAR" ]; then
        echo "Downloading org.json jar (Maven Central) -> $JSON_JAR" >&2
        curl -fsSL -o "$JSON_JAR" "https://repo1.maven.org/maven2/org/json/json/20180813/json-20180813.jar" \
            || { echo "ERROR: org.json download failed" >&2; exit 1; }
    fi
fi
if [ ! -f "$JSON_JAR" ]; then
    echo "ERROR: org.json jar not found and download failed" >&2
    exit 1
fi
if [ ! -f "$ANDROID_JAR" ]; then
    echo "ERROR: android.jar not found at $ANDROID_JAR" >&2
    echo "Set ANDROID_HOME to a valid Android SDK path." >&2
    exit 1
fi

rm -rf "$OUT"
mkdir -p "$OUT"

echo "=== Compiling project log buffer ==="
javac -d "$OUT" -classpath "$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/LogBuffer.java"

echo "=== Compiling SignalTranslator ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/SignalTranslator.java"

echo "=== Compiling tests ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/SignalTranslatorTest.java"

echo "=== Compiling ELM327 session (pure Java) ==="
javac -d "$OUT" -classpath "$OUT" -source 17 -target 17 \
  "$SRC/com/car2hass/vehicle/Elm327Parser.java" \
  "$SRC/com/car2hass/vehicle/obd/Elm327Io.java" \
  "$SRC/com/car2hass/vehicle/obd/ObdSession.java" \
  "$SRC/com/car2hass/vehicle/obd/Elm327Session.java"

echo "=== Compiling Elm327SessionTest ==="
javac -d "$OUT" -classpath "$OUT" -source 17 -target 17 \
  "$TEST/com/car2hass/vehicle/Elm327SessionTest.java"

echo "=== Overriding with test-only LogBuffer stub ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/LogBuffer.java"

echo "=== Compiling NetSafety ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/NetSafety.java"

echo "=== Compiling NetSafetyTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/NetSafetyTest.java"

echo "=== Compiling DiplusApi ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/DiplusApi.java"

echo "=== Compiling DiplusApiTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/DiplusApiTest.java"

echo "=== Compiling DiplusUnavailableException ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/DiplusUnavailableException.java"

echo "=== Compiling DiplusErrorClassifier ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/DiplusErrorClassifier.java"

echo "=== Compiling UnsupportedRecoveryGate ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/UnsupportedRecoveryGate.java"

echo "=== Compiling classifier/recovery tests ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/DiplusErrorClassifierTest.java"
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/UnsupportedRecoveryGateTest.java"

echo "=== Compiling DashboardLogic ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/DashboardLogic.java"

echo "=== Compiling DashboardLogicTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/DashboardLogicTest.java"

echo "=== Compiling RuleOperator ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/rules/RuleOperator.java"

echo "=== Compiling JSON model classes (no Android dep) ==="
javac -d "$OUT" -classpath "$OUT:$JSON_JAR" -source 17 -target 17 \
  "$SRC/com/car2hass/rules/LogicalOperator.java" \
  "$SRC/com/car2hass/rules/RuleCondition.java" \
  "$SRC/com/car2hass/rules/RuleAction.java" \
  "$SRC/com/car2hass/rules/Rule.java"

echo "=== Compiling RuleEvaluator ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR:$JSON_JAR" -source 17 -target 17 "$SRC/com/car2hass/rules/RuleEvaluator.java"

echo "=== Compiling AntiLoopGuard ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/rules/AntiLoopGuard.java"

echo "=== Compiling rules tests ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/RuleOperatorTest.java"
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/RuleEvaluatorTest.java"
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/AntiLoopGuardTest.java"

echo "=== Compiling new model tests ==="
javac -d "$OUT" -classpath "$OUT:$JSON_JAR" -source 17 -target 17 \
  "$TEST/com/car2hass/rules/NewModelTest.java" \
  "$TEST/com/car2hass/rules/RuleMigrationTest.java"

echo "=== Compiling SensorValueHistory ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/SensorValueHistory.java"

echo "=== Compiling SensorValueHistoryTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/SensorValueHistoryTest.java"

echo "=== Compiling GeofenceZone (JSON model, no Android dep) ==="
javac -d "$OUT" -classpath "$OUT:$JSON_JAR" -source 17 -target 17 "$SRC/com/car2hass/GeofenceZone.java"

echo "=== Compiling GeofenceZoneTest ==="
javac -d "$OUT" -classpath "$OUT:$JSON_JAR" -source 17 -target 17 "$TEST/com/car2hass/GeofenceZoneTest.java"

echo "=== Compiling GeofenceRowText (pure row-format helper, no Android dep) ==="
javac -d "$OUT" -source 17 -target 17 "$SRC/com/car2hass/GeofenceRowText.java"

echo "=== Compiling GeofenceRowTextTest ==="
javac -d "$OUT" -classpath "$OUT" -source 17 -target 17 "$TEST/com/car2hass/GeofenceRowTextTest.java"

echo "=== Compiling PresetIconResolver ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/PresetIconResolver.java"

echo "=== Compiling PresetIconResolverTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/PresetIconResolverTest.java"

echo "=== Compiling LogDedup (export-log dedup, no Android dep) ==="
javac -d "$OUT" -source 17 -target 17 "$SRC/com/car2hass/LogDedup.java"

echo "=== Compiling LogDedupTest ==="
javac -d "$OUT" -classpath "$OUT" -source 17 -target 17 "$TEST/com/car2hass/LogDedupTest.java"

echo "=== Compiling PresetParamValues (JSON model, no Android dep) ==="
javac -d "$OUT" -classpath "$OUT:$JSON_JAR" -source 17 -target 17 "$SRC/com/car2hass/PresetParamValues.java"

echo "=== Compiling PresetParamValuesTest ==="
javac -d "$OUT" -classpath "$OUT:$JSON_JAR" -source 17 -target 17 "$TEST/com/car2hass/PresetParamValuesTest.java"

echo "=== Compiling SendHistoryCore (pure JSON) ==="
javac -d "$OUT" -classpath "$OUT:$JSON_JAR" -source 17 -target 17 "$SRC/com/car2hass/SendHistoryCore.java"

echo "=== Compiling SendHistoryCoreTest ==="
javac -d "$OUT" -classpath "$OUT:$JSON_JAR" -source 17 -target 17 "$TEST/com/car2hass/SendHistoryCoreTest.java"

echo "=== Compiling QueueIndicator (pure) ==="
javac -d "$OUT" -classpath "$OUT" -source 17 -target 17 "$SRC/com/car2hass/QueueIndicator.java"

echo "=== Compiling QueueIndicatorTest ==="
javac -d "$OUT" -classpath "$OUT" -source 17 -target 17 "$TEST/com/car2hass/QueueIndicatorTest.java"

echo "=== Compiling SentinelDecoder ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/SentinelDecoder.java"

echo "=== Compiling ParamDecoder ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/ParamDecoder.java"

echo "=== Compiling decoder tests ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/SentinelDecoderTest.java"
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/ParamDecoderTest.java"

echo "=== Compiling NativeSignalMap ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/NativeSignalMap.java"

echo "=== Compiling NativeSignalMapTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/NativeSignalMapTest.java"

echo "=== Compiling NativeCommandBuilder ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/NativeCommandBuilder.java"

echo "=== Compiling NativeOutputParser ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/NativeOutputParser.java"

echo "=== Compiling builder/parser tests ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/NativeCommandBuilderTest.java"
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/NativeOutputParserTest.java"

echo "=== Compiling NativeSyncGate (pure Java, no Android dep) ==="
javac -d "$OUT" -source 17 -target 17 "$SRC/com/car2hass/NativeSyncGate.java"

echo "=== Compiling NativeSyncGateTest ==="
javac -d "$OUT" -classpath "$OUT" -source 17 -target 17 "$TEST/com/car2hass/NativeSyncGateTest.java"

echo "=== Compiling CANDataItem ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/CANDataItem.java"

echo "=== Compiling NativeReader ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/NativeReader.java"

echo "=== Compiling NativeReaderTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/NativeReaderTest.java"

echo "=== Compiling NativeCommandWriter ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/NativeCommandWriter.java"

echo "=== Compiling NativeCommandWriterTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/NativeCommandWriterTest.java"

echo "=== Compiling SensorCommandMapTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR:$JSON_JAR" -source 17 -target 17 "$TEST/com/car2hass/SensorCommandMapTest.java"

echo "=== Compiling test-only R stub ==="
javac -d "$OUT" -classpath "$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/R.java"

echo "=== Compiling CommandRegistry (with R stub) ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/CommandRegistry.java"

echo "=== Compiling NativeCommandMap ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR:$JSON_JAR" -source 17 -target 17 "$SRC/com/car2hass/NativeCommandMap.java"

echo "=== Compiling NativeCommandMapTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR:$JSON_JAR" -source 17 -target 17 "$TEST/com/car2hass/NativeCommandMapTest.java"

echo "=== Compiling CommandWriter (with R stub + CommandRegistry) ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR:$JSON_JAR" -source 17 -target 17 "$SRC/com/car2hass/CommandWriter.java"

echo "=== Compiling TestUtil ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR:$JSON_JAR" -source 17 -target 17 "$TEST/com/car2hass/TestUtil.java"

echo "=== Compiling CommandWriter tests ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR:$JSON_JAR" -source 17 -target 17 "$TEST/com/car2hass/CommandWriterTest.java"
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR:$JSON_JAR" -source 17 -target 17 "$TEST/com/car2hass/CommandWriterFallbackTest.java"

echo "=== Compiling VehicleProfile ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/vehicle/VehicleProducer.java" "$SRC/com/car2hass/vehicle/VehicleProfile.java"

echo "=== Compiling VehicleProfileTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/vehicle/VehicleProfileTest.java"

echo "=== Compiling ChannelResult ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/vehicle/ChannelResult.java" "$SRC/com/car2hass/vehicle/DataChannel.java"

echo "=== Compiling ChannelResultTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/vehicle/ChannelResultTest.java"

echo "=== Compiling VehicleProfileDetect ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/vehicle/VehicleProfileDetect.java"

echo "=== Compiling VehicleProfileDetectTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/vehicle/VehicleProfileDetectTest.java"

echo "=== Compiling test-only BuildConfig stub ==="
javac -d "$OUT" -classpath "$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/BuildConfig.java"

echo "=== Compiling Phase-2 probe engine (pure classes) ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR:$JSON_JAR" -source 17 -target 17 \
  "$SRC/com/car2hass/CANDataItem.java" \
  "$SRC/com/car2hass/AppInfo.java" \
  "$SRC/com/car2hass/vehicle/DataChannel.java" \
  "$SRC/com/car2hass/vehicle/ChannelResult.java" \
  "$SRC/com/car2hass/vehicle/VehicleProducer.java" \
  "$SRC/com/car2hass/vehicle/VehicleProfile.java" \
  "$SRC/com/car2hass/vehicle/RegistryStore.java" \
  "$SRC/com/car2hass/vehicle/ProbeResult.java" \
  "$SRC/com/car2hass/vehicle/ProfileScorer.java" \
  "$SRC/com/car2hass/vehicle/BrandSelector.java" \
  "$SRC/com/car2hass/vehicle/CommandProber.java" \
  "$SRC/com/car2hass/vehicle/ProbeReport.java" \
  "$SRC/com/car2hass/vehicle/DeviceAnon.java" \
  "$SRC/com/car2hass/vehicle/VehicleResearch.java"

echo "=== Compiling Phase-3 source manager (pure classes) ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 \
  "$SRC/com/car2hass/CANDataItem.java" \
  "$SRC/com/car2hass/vehicle/RegistryStore.java" \
  "$SRC/com/car2hass/vehicle/SourceManager.java" \
  "$SRC/com/car2hass/vehicle/SnapshotStore.java" \
  "$SRC/com/car2hass/vehicle/LocationSource.java" \
  "$SRC/com/car2hass/vehicle/SnapshotQueue.java"

echo "=== Compiling Phase-4 research UI model ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 \
  "$SRC/com/car2hass/vehicle/RegistryStore.java" \
  "$SRC/com/car2hass/vehicle/ResearchUiModel.java"

echo "=== Compiling Phase-5 probe uploader (pure parts) ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 \
  "$SRC/com/car2hass/AppInfo.java" \
  "$SRC/com/car2hass/NetSafety.java" \
  "$SRC/com/car2hass/AppApi.java" \
  "$SRC/com/car2hass/vehicle/DeviceAnon.java" \
  "$SRC/com/car2hass/ProbeUploader.java"

echo "=== Compiling Phase-2 probe engine tests ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 \
  "$TEST/com/car2hass/vehicle/RegistryStoreTest.java" \
  "$TEST/com/car2hass/vehicle/ProbeResultTest.java" \
  "$TEST/com/car2hass/vehicle/ProfileScorerTest.java" \
  "$TEST/com/car2hass/vehicle/BrandSelectorTest.java" \
  "$TEST/com/car2hass/vehicle/CommandProberTest.java" \
  "$TEST/com/car2hass/vehicle/ProbeReportTest.java" \
  "$TEST/com/car2hass/vehicle/VehicleResearchTest.java"

echo "=== Compiling Phase-3 source manager tests ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 \
  "$TEST/com/car2hass/vehicle/SourceManagerTest.java" \
  "$TEST/com/car2hass/vehicle/SnapshotStoreTest.java" \
  "$TEST/com/car2hass/vehicle/LocationSourceTest.java" \
  "$TEST/com/car2hass/vehicle/SnapshotQueueTest.java"

echo "=== Compiling Phase-4 research UI model test ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 \
  "$TEST/com/car2hass/vehicle/ResearchUiModelTest.java"

echo "=== Compiling Phase-5 probe uploader tests ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR:$JSON_JAR" -source 17 -target 17 \
  "$TEST/com/car2hass/vehicle/DeviceAnonTest.java" \
  "$TEST/com/car2hass/ProbeUploaderTest.java"

echo "=== Compiling Phase-6 OBD codec (pure classes) ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 \
  "$SRC/com/car2hass/vehicle/Elm327Parser.java" \
  "$SRC/com/car2hass/vehicle/ObdPidCodec.java"

echo "=== Compiling Phase-6 OBD codec test ==="
javac -d "$OUT" -classpath "$OUT" -source 17 -target 17 \
  "$TEST/com/car2hass/vehicle/ObdCodecTest.java"

echo "=== Compiling Phase-6 OBD transport (pure classes) ==="
javac -d "$OUT" -classpath "$OUT" -source 17 -target 17 \
  "$SRC/com/car2hass/vehicle/obd/ObdTransport.java" \
  "$SRC/com/car2hass/vehicle/obd/Elm327Io.java"

echo "=== Compiling Phase-6 TcpTransport (needs LogBuffer stub) ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 \
  "$SRC/com/car2hass/vehicle/obd/TcpTransport.java"

echo "=== Compiling Phase-6 OBD transport test ==="
javac -d "$OUT" -classpath "$OUT" -source 17 -target 17 \
  "$TEST/com/car2hass/vehicle/obd/ObdIoTest.java"

echo "=== Compiling VehicleResearch ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$SRC/com/car2hass/AppInfo.java" "$SRC/com/car2hass/vehicle/VehicleResearch.java"

echo "=== Compiling VehicleResearchTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR" -source 17 -target 17 "$TEST/com/car2hass/vehicle/VehicleResearchTest.java"

echo "=== Running tests ==="
java -cp "$OUT:$ANDROID_JAR" com.car2hass.SignalTranslatorTest
java -cp "$OUT:$ANDROID_JAR" com.car2hass.NetSafetyTest
java -cp "$OUT:$ANDROID_JAR" com.car2hass.DiplusApiTest
java -cp "$OUT:$ANDROID_JAR" com.car2hass.DiplusErrorClassifierTest
java -cp "$OUT:$ANDROID_JAR" com.car2hass.UnsupportedRecoveryGateTest
java -cp "$OUT:$ANDROID_JAR" com.car2hass.DashboardLogicTest
java -cp "$OUT:$ANDROID_JAR" com.car2hass.RuleOperatorTest
java -cp "$OUT:$ANDROID_JAR" com.car2hass.RuleEvaluatorTest
java -cp "$OUT:$ANDROID_JAR" com.car2hass.AntiLoopGuardTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.car2hass.rules.NewModelTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.car2hass.rules.RuleMigrationTest
java -cp "$OUT:$ANDROID_JAR" com.car2hass.SensorValueHistoryTest
java -cp "$OUT:$JSON_JAR" com.car2hass.GeofenceZoneTest
java -cp "$OUT" com.car2hass.GeofenceRowTextTest
java -cp "$OUT:$ANDROID_JAR" com.car2hass.PresetIconResolverTest
java -cp "$OUT" com.car2hass.LogDedupTest
java -cp "$OUT:$JSON_JAR" com.car2hass.PresetParamValuesTest

echo "=== Running SendHistoryCoreTest ==="
java -cp "$OUT:$JSON_JAR" com.car2hass.SendHistoryCoreTest

echo "=== Running QueueIndicatorTest ==="
java -cp "$OUT" com.car2hass.QueueIndicatorTest
java -cp "$OUT:$ANDROID_JAR" com.car2hass.SentinelDecoderTest
java -cp "$OUT:$ANDROID_JAR" com.car2hass.ParamDecoderTest
java -cp "$OUT:$ANDROID_JAR" com.car2hass.NativeSignalMapTest
java -cp "$OUT:$ANDROID_JAR" com.car2hass.NativeCommandBuilderTest
java -cp "$OUT:$ANDROID_JAR" com.car2hass.NativeOutputParserTest
java -cp "$OUT" com.car2hass.NativeSyncGateTest
java -cp "$OUT:$ANDROID_JAR" com.car2hass.NativeReaderTest
java -cp "$OUT:$ANDROID_JAR" com.car2hass.NativeCommandWriterTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.car2hass.SensorCommandMapTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.car2hass.NativeCommandMapTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.car2hass.CommandWriterTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.car2hass.CommandWriterFallbackTest

echo "=== Running VehicleProfileTest ==="
java -cp "$OUT:$ANDROID_JAR" com.car2hass.vehicle.VehicleProfileTest

echo "=== Running ChannelResultTest ==="
java -cp "$OUT:$ANDROID_JAR" com.car2hass.vehicle.ChannelResultTest

echo "=== Running VehicleProfileDetectTest ==="
java -cp "$OUT:$ANDROID_JAR" com.car2hass.vehicle.VehicleProfileDetectTest

echo "=== Running VehicleResearchTest ==="
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.car2hass.vehicle.VehicleResearchTest

echo "=== Running Phase-2 probe engine tests ==="
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.car2hass.vehicle.RegistryStoreTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.car2hass.vehicle.BrandSelectorTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.car2hass.vehicle.ProbeResultTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.car2hass.vehicle.ProfileScorerTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.car2hass.vehicle.CommandProberTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.car2hass.vehicle.ProbeReportTest

echo "=== Running Phase-3 source manager tests ==="
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.car2hass.vehicle.SourceManagerTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.car2hass.vehicle.SnapshotStoreTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.car2hass.vehicle.LocationSourceTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.car2hass.vehicle.SnapshotQueueTest

echo "=== Running Phase-4 research UI model test ==="
java -cp "$OUT:$JSON_JAR" com.car2hass.vehicle.ResearchUiModelTest

echo "=== Running Phase-5 probe uploader tests ==="
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.car2hass.vehicle.DeviceAnonTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.car2hass.ProbeUploaderTest

echo "=== Compiling UpdateChecker ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR:$JSON_JAR" -source 17 -target 17 \
  "$SRC/com/car2hass/AppInfo.java" \
  "$SRC/com/car2hass/LogBuffer.java" \
  "$SRC/com/car2hass/UpdateChecker.java"

echo "=== Compiling UpdateCheckerTest ==="
javac -d "$OUT" -classpath "$OUT:$ANDROID_JAR:$JSON_JAR" -source 17 -target 17 \
  "$TEST/com/car2hass/UpdateCheckerTest.java"

echo "=== Running Phase-6 OBD codec test ==="
java -cp "$OUT" com.car2hass.vehicle.ObdCodecTest

echo "=== Running Phase-6 OBD transport test ==="
java -cp "$OUT" com.car2hass.vehicle.obd.ObdIoTest
java -cp "$OUT" com.car2hass.vehicle.Elm327SessionTest
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" com.car2hass.UpdateCheckerTest
