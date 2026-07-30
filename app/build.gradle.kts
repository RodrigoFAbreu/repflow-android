plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

android {
    namespace = "com.repflow.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.repflow.app"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // AGP's built-in Kotlin compiler (no separate org.jetbrains.kotlin.android
    // plugin is applied here) derives its JVM bytecode target from these same
    // compileOptions, so this is the single place controlling both javac and
    // kotlinc output bytecode (currently Java 17), independent of whichever
    // JDK runs the Gradle daemon itself (pinned separately to 21 via
    // gradle/gradle-daemon-jvm.properties).

    buildFeatures {
        compose = true
    }

    // Instrumented Room DAO/repository tests open the exported schema JSON
    // (see the ksp room.schemaLocation argument below) as a test asset, e.g.
    // via androidx.room.testing.MigrationTestHelper in a future milestone.
    sourceSets {
        getByName("androidTest") {
            assets.directories.add("$projectDir/schemas")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.json.org.java)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.room.testing)
}

/*
 * `androidx.room:room-testing:2.8.4`'s schema-bundle deserialization needs
 * `kotlinx-serialization-core` 1.8.1's `GeneratedSerializer.typeParametersSerializers()`,
 * but several other androidx artifacts (e.g. lifecycle, compose) declare a
 * `strictly 1.7.3` constraint on the same module, which otherwise wins
 * conflict resolution and produces an `AbstractMethodError` at runtime in
 * `com.repflow.app.infrastructure.database.RepFlowDatabaseMigrationTest`.
 * Forcing 1.8.1 (backwards-compatible for every other consumer here) is the
 * narrowest fix; revisit once a future Room/Compose BOM release aligns
 * these versions itself.
 */
configurations.all {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1",
            "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1",
            "org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1",
            "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1",
        )
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}
