pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 認証情報は ~/.gradle/gradle.properties の GitHubPackagesUsername / GitHubPackagesPassword
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/jig-SABERA/sabera-sdk-packages")
            credentials(PasswordCredentials::class)
        }
    }
}

rootProject.name = "sabera-acchi-muite-hoi"
include(":app")
