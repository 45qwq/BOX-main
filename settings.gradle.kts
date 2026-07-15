pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        flatDir { dirs("$rootDir/app/libs") }
        maven("https://jitpack.io")
    }
}
include(":app")
include(":catvod")
include(":danmaku")
include(":forcetech")
include(":hook")
include(":jianpian")
include(":quickjs")
rootProject.name = "XMBOX"
