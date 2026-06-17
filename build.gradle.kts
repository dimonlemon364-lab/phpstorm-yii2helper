import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

val pluginVersion = providers.gradleProperty("version")

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

        // Tooling used by the verifyPlugin and signPlugin tasks.
        pluginVerifier()
        zipSigner()
    }
}

// Marketplace metadata, signing, publishing and verification.
intellijPlatform {
    pluginConfiguration {
        version = pluginVersion

        ideaVersion {
            sinceBuild = "253"
            // Open-ended: only a lower bound, so the plugin installs on future PhpStorm builds.
            untilBuild = provider { null }
        }

        // Marketplace "What's new" section, rendered from CHANGELOG.md for this version.
        changeNotes = provider {
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion.get()) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }
    }

    // Reads secrets from the environment so they are never committed.
    // CERTIFICATE_CHAIN / PRIVATE_KEY / PRIVATE_KEY_PASSWORD — see signPlugin docs.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    // PUBLISH_TOKEN — a Marketplace permanent token (https://plugins.jetbrains.com/author/me/tokens).
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            create(IntelliJPlatformType.PhpStorm, "2025.3.5")
        }
    }
}

changelog {
    groups.empty()
}

tasks {
    runIde {
        // Open this project automatically in the sandbox IDE when running the plugin.
        argumentProviders += CommandLineArgumentProvider {
            listOf("/home/user/doc/master")
        }
    }
}
