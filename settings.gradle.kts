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
    }
}

rootProject.name = "gv-android-sdk"

include(":gvintelligence")
// The example app is optional and only included when present (it is shipped in
// the public mirror but ignored by JitPack's library resolution).
if (file("example/build.gradle.kts").exists()) {
    include(":example")
}
