import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    // Manual dependsOn edges (jvmShared) suppress the default template, so it
    // is applied explicitly to keep iosMain and friends.
    applyDefaultHierarchyTemplate()

    androidLibrary {
        namespace = "com.example.ohmyssh.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
    }

    jvm("desktop")

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(libs.compose.material.icons.core)
                implementation(libs.compose.material.icons.extended)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.cryptography.core)
                implementation(libs.multiplatform.settings)
                implementation(libs.filekit.core)
            }
        }

        val jvmShared by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.sshj)
                implementation(libs.slf4j.nop)
                implementation(libs.cryptography.provider.jdk)
            }
        }

        val androidMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.usb.serial.android)
            }
        }

        val desktopMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.jserialcomm)
                implementation(libs.jna)
            }
        }

        val iosMain by getting {
            dependencies {
                implementation(libs.cryptography.provider.openssl3)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.example.ohmyssh.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ohmyssh"
            packageVersion = "1.0.0"
            description = "Simple, reliable SSH/SFTP client"
            // Regenerate all three with artwork/generate-app-icons.py; jpackage
            // wants a per-platform container and rejects a bare PNG on mac/Windows.
            macOS {
                bundleID = "com.example.ohmyssh"
                iconFile.set(rootProject.file("artwork/desktop/app-icon-macos.icns"))
            }
            windows { iconFile.set(rootProject.file("artwork/desktop/app-icon-windows.ico")) }
            linux { iconFile.set(rootProject.file("artwork/desktop/app-icon-linux.png")) }
        }
    }
}
