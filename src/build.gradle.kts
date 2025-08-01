// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.10.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version "1.9.20" apply false
}

tasks.register("clean", Delete::class) {
    delete(layout.buildDirectory)
} 