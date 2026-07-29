rootProject.name = "ohmyssh"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // usb-serial-for-android is published on JitPack only.
        maven("https://jitpack.io") {
            mavenContent { includeGroup("com.github.mik3y") }
        }
    }
}

include(":shared")
include(":androidApp")
