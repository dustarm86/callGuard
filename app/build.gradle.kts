import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.dustin.callguard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dustin.callguard"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
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

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.googlecode.libphonenumber:libphonenumber:8.13.40")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    val roomVersion = "2.7.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    testImplementation("junit:junit:4.13.2")
    // android's own org.json is a stub in unit tests, so tests use the real one
    testImplementation("org.json:json:20240303")
}

// unit tests: always run, list every test with pass/fail, and finish with a one-line total
tasks.withType<Test>().configureEach {
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
        showStandardStreams = false
    }
    doLast {
        var tests = 0
        var skipped = 0
        var failed = 0
        reports.junitXml.outputLocation.get().asFile
            .listFiles { file -> file.extension == "xml" }
            ?.forEach { file ->
                val match = Regex("""<testsuite [^>]*?tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"""")
                    .find(file.readText())
                if (match != null) {
                    val (t, s, f, e) = match.destructured
                    tests += t.toInt()
                    skipped += s.toInt()
                    failed += f.toInt() + e.toInt()
                }
            }
        println("unit tests: $tests ran, ${tests - skipped - failed} passed, $failed failed, $skipped skipped")
    }
}
