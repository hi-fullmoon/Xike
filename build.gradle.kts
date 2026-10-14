plugins {
    id("com.diffplug.spotless") version "8.10.4"
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("com.google.devtools.ksp") version "2.3.9" apply false
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach { dependsOn(":spotlessCheck") }
}

spotless {
    kotlin {
        target("app/src/**/*.kt")
        ktfmt("0.64").kotlinlangStyle().configure {
            it.setRemoveUnusedImports(false)
        }
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktfmt("0.64").kotlinlangStyle().configure {
            it.setRemoveUnusedImports(false)
        }
    }
}
