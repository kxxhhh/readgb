pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            // JitPack's generated POM incorrectly reports master-SNAPSHOT for
            // commit-pinned AARs; resolve the published AAR artifact directly.
            metadataSources { artifact() }
        }
    }
}

rootProject.name = "Dutongjian"
include(":app")
