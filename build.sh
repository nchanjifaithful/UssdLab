#!/usr/bin/env bash
# Builds the APK without Gradle, using standalone copies of Google's tools.
# Usage: AAPT2=... D8_JAR=... APKSIGNER_JAR=... ANDROID_JAR=... ./build.sh
set -euo pipefail
cd "$(dirname "$0")"
OUT=build; rm -rf "$OUT"; mkdir -p "$OUT/res" "$OUT/classes" "$OUT/dex" "$OUT/gen"
M=app/src/main
"$AAPT2" compile --dir "$M/res" -o "$OUT/res/res.zip"
"$AAPT2" link -o "$OUT/unsigned.apk" -I "$ANDROID_JAR" --manifest "$M/AndroidManifest.xml" \
    --min-sdk-version 26 --target-sdk-version 34 --java "$OUT/gen" "$OUT/res/res.zip"
if [ -n "${ECJ_JAR:-}" ]; then java -jar "$ECJ_JAR" -8 -nowarn -cp "$ANDROID_JAR" -d "$OUT/classes" $(find "$M/java" "$OUT/gen" -name "*.java"); else javac -nowarn -Xlint:-options --release 8 -cp "$ANDROID_JAR" -d "$OUT/classes" $(find "$M/java" "$OUT/gen" -name "*.java"); fi
java -cp "$D8_JAR" com.android.tools.r8.D8 --release --min-api 26 --lib "$ANDROID_JAR" \
    --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
cp "$OUT/unsigned.apk" "$OUT/app.apk"
(cd "$OUT/dex" && zip -q -9 ../app.apk classes.dex)
KS=${KEYSTORE:-ussdlab.keystore}
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass ussdlab -keypass ussdlab -alias ussdlab \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=USSD Lab" >/dev/null 2>&1
java -jar "$APKSIGNER_JAR" sign --ks "$KS" --ks-pass pass:ussdlab --key-pass pass:ussdlab \
    --out UssdLab.apk "$OUT/app.apk"
java -jar "$APKSIGNER_JAR" verify --print-certs UssdLab.apk | head -3
ls -la UssdLab.apk
