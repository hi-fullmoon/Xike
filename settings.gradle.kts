pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // TinyPinyin 2.0.3 was published to JCenter; use the upstream-recommended mirror.
        exclusiveContent {
            forRepository { maven("https://maven.aliyun.com/repository/public") }
            filter { includeGroup("com.github.promeg") }
        }
    }
}

rootProject.name = "Xike"

include(":app")
