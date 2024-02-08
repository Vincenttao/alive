pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google() // 添加Google Maven仓库
        mavenCentral() // 添加Maven Central仓库
        // 这里可以根据需要添加更多的仓库
    }
}

rootProject.name = "alive"
include(":app")
 