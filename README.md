# SecurityChecker

Android library for on-device security checks — root, emulator, debugger, cloned-app, blacklisted-app
detection and APK signature verification. Works with or without a DI framework.

## Features

- **Root detection** — known `su` binary paths, build tags, and [RootBeer](https://github.com/scottyab/rootbeer)
- **Emulator detection** — restricted to production builds so debug/QA builds on emulators aren't blocked
- **Debugger detection**
- **Cloned-app detection** — flags parallel-space / multi-app-cloner installs
- **Blacklisted-app detection** — flags known repackaging/cloning tools installed on the device
- **APK signature verification** *(optional)* — compares the running APK's signing certificate against a
  known-good SHA-256 hash you provide

None of these checks read or depend on the consuming app's own `BuildConfig` — anything app-specific
(build variant, expected signature) is passed in explicitly, so the library stays app-agnostic.

## Installation

Published via [JitPack](https://jitpack.io). Add the repository:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}
```

Then add the `:security` module as a dependency, referencing a tag/release of this repo as the version:

```kotlin
dependencies {
    implementation("com.github.alicansekban.securitychecker:security:<tag>")
}
```

(`<tag>` = a Git tag or release version of this repo, e.g. `1.0.0`. JitPack builds it on first request.)

## Usage

### Constructor injection (DI frameworks — Koin, Hilt, Dagger, ...)

```kotlin
val securityChecker = SecurityChecker(
    context = context,
    isProductionEnvironment = BuildConfig.FLAVOR == "prod",
    expectedSignatureHash = "AB:CD:EF:..." // optional, omit to skip signature verification
)

if (securityChecker.isDeviceNotSecure()) {
    // block access / navigate to a lock screen
}
```

Koin example:

```kotlin
single {
    SecurityChecker(
        context = androidContext(),
        isProductionEnvironment = BuildConfig.FLAVOR == "prod"
    )
}
```

### DI-agnostic entry point (no DI framework)

```kotlin
val isNotSecure = SecurityChecker.isDeviceNotSecure(
    context = context,
    isProductionEnvironment = BuildConfig.FLAVOR == "prod"
)
```

### Individual checks

Every underlying check is also exposed publicly, so you can build a custom combination instead of the
aggregate `isDeviceNotSecure()` (e.g. skip emulator detection on QA builds):

```kotlin
val checker = SecurityChecker(context, isProductionEnvironment = true)

checker.isDeviceRooted()
checker.isDeviceRootedViaRootBeer()
checker.canExecuteSu()
checker.isClonedApp()
checker.isBlackListAppInstalled()
checker.isEmulatorForProdRelease()
checker.isDebuggerAttached()
checker.verifyAppSignature(expectedSignature = "AB:CD:EF:...")
```

## Requirements

- minSdk 24
- Kotlin

## License

MIT — see [LICENSE](LICENSE).
