buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.0.3")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.31")
    }
}

plugins {
    id("com.diffplug.spotless") version "5.14.2"
}

subprojects {
    repositories {
        google()
        mavenCentral()
    }
    apply(plugin = "com.diffplug.spotless")

    spotless {
        kotlin {
            target("**/*.kt")
            targetExclude("$buildDir/**/*.kt")
            targetExclude("bin/**/*.kt")

            ktlint("0.41.0")
            licenseHeaderFile(rootProject.file("spotless/copyright.kt"))
        }
    }
}
