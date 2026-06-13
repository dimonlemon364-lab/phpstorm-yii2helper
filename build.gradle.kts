import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // PhpStorm is the base IDE: it bundles the PHP plugin and the php-capable
        // platform module the plugin depends on (IntelliJ IDEA cannot host it).
        phpstorm("2025.3.5")
        testFramework(TestFrameworkType.Platform)

        // PHP plugin: bundled with PhpStorm, needed for PHP PSI at compile and runtime.
        bundledPlugin("com.jetbrains.php")
    }
}

tasks {
    runIde {
        // Open this project automatically in the sandbox IDE when running the plugin.
        argumentProviders += CommandLineArgumentProvider {
            listOf("/home/user/doc/master")
        }
    }
}
