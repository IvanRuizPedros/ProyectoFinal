// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.1.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
        classpath("com.google.gms:google-services:4.3.15")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.autonomousapps.dependency-analysis") version "1.24.0" apply false
}