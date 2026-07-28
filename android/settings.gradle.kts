pluginManagement {
    val flutterSdkPath =
        run {
            val properties = java.util.Properties()
            file("local.properties").inputStream().use { properties.load(it) }
            val flutterSdkPath = properties.getProperty("flutter.sdk")
            require(flutterSdkPath != null) { "flutter.sdk not set in local.properties" }
            flutterSdkPath
        }

    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    // Held on AGP 8 / Gradle 8: on AGP 9 the built-in-Kotlin migration
    // splits the plugin ecosystem. file_picker skips applying KGP and needs
    // android.builtInKotlin=true, while file_saver, share_plus and
    // flutter_foreground_task still apply KGP themselves and break under it.
    // AGP 8.11.1 keeps every plugin on the same path.
    id("com.android.application") version "8.11.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
}

include(":app")
