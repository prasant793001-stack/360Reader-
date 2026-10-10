// LibreOfficeKit for Android: the Java bindings (MPL 2.0, from the LibreOffice
// project) plus, once you've built it, the native engine and its assets.
//
//   src/main/java      org.libreoffice.kit.* JNI bindings (already here)
//   src/main/jniLibs   liblo-native-code.so and friends  (added by tools/)
//   src/main/assets    LibreOffice's runtime files       (added by tools/)
//
// See README.md, section "Android office engine".
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.libreoffice.kit"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
