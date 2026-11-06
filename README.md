# USSD Lab

An Android app that automates multi-step USSD menus (e.g. mobile money). You define a USSD code and a list of reply steps; an accessibility service reads each USSD dialog and types the configured reply, only while a run you started is in progress.

- PIN is stored encrypted with an Android Keystore key gated by biometric/device auth (`PinVault`) and never written to logs.
- Dual-SIM support (`Sims`).
- Nothing leaves the phone.

## Build

With Gradle (Android Gradle Plugin 8.5, compileSdk 34, minSdk 26):

```bash
gradle :app:assembleDebug
```

Or without Gradle, using standalone SDK tools:

```bash
AAPT2=... D8_JAR=... APKSIGNER_JAR=... ANDROID_JAR=... ./build.sh
```

`build.sh` generates a local `ussdlab.keystore` if none exists (set `KEYSTORE` to use your own).

## Tests

`app/src/test/java/app/ussdlab/StepEngineTest.java` covers the step engine.
