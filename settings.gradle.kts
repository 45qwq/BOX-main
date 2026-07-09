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
        maven {
            url = uri("http://4thline.org/m2")
            isAllowInsecureProtocol = true
        }
    }
}
include(":app")
include(":catvod")
include(":danmaku")
include(":dlna-core")
include(":dlna-dmc")
include(":dlna-dmr")
include(":forcetech")
include(":hook")
include(":jianpian")
include(":quickjs")
rootProject.name = "XMBOX"
