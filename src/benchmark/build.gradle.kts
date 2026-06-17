plugins {
    id("com.android.test")
}

android {
    namespace = "com.melodee.autoplayer.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        testInstrumentationRunnerArguments += mapOf(
            "androidx.benchmark.suppressErrors" to "EMULATOR,DEBUGGABLE"
        )
    }

    buildTypes {
        create("release") {
            isMinifyEnabled = false  // Disable minification for benchmarks
            isDebuggable = false
        }
        create("benchmark") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/DEPENDENCIES"
        }
        jniLibs {
            keepDebugSymbols += setOf(
                "**/libandroidx.graphics.path.so",
                "**/libbenchmarkNative.so",
                "**/libdatastore_shared_counter.so",
                "**/libtracing_perfetto.so"
            )
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test.espresso:espresso-core:3.7.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
}
