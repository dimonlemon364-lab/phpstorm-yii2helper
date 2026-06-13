# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Yii2Helper is an IntelliJ Platform plugin (Kotlin/JVM) that targets the PHP IDE
stack to add tooling for the [Yii2](https://www.yiiframework.com/) PHP framework.
It is bootstrapped from the JetBrains IntelliJ Platform Plugin Template and is
framework. Kotlin sources live under `src/main/kotlin/com/yii2helper/`; the first
feature (view navigation from render calls) is implemented under `.../view/`.

Note: JDK 21 (a JetBrains Runtime works) is required for the Gradle build.

## Commands

The project uses the Gradle wrapper with the IntelliJ Platform Gradle Plugin (v2).

- Run the plugin in a sandbox IDE: `./gradlew runIde`
- Run tests: `./gradlew test`
- Run a single test class: `./gradlew test --tests "com.yii2helper.SomeTest"`
- Verify plugin compatibility against target IDEs: `./gradlew verifyPlugin`
- Build the distributable: `./gradlew buildPlugin` (output in `build/distributions/`)
- Publish to JetBrains Marketplace: `./gradlew publishPlugin`

The same tasks are wired as IDE Run configs in `.run/` (Run Plugin, Run Tests,
Run Verifications).

## Architecture & key facts

- **Platform target**: builds against **PhpStorm** `2025.3.5` (`phpstorm(...)` in
  `build.gradle.kts`) and uses the bundled PHP plugin (`bundledPlugin("com.jetbrains.php")`).
  PhpStorm is required as the base IDE because the PHP plugin hard-depends on the
  `com.intellij.modules.php-capable` platform module, which IntelliJ IDEA (even
  Ultimate) does not provide — so tests/`runIde` cannot load PHP on an IDEA base.
  The PHP dependency is declared in two places that must stay in sync —
  `build.gradle.kts` (`bundledPlugin`) and `plugin.xml` (`<depends>`).
- **View navigation feature** (`src/main/kotlin/com/yii2helper/view/`): makes the
  view-name string in `render`/`renderPartial`/`renderAjax`/`renderFile` calls a
  navigable reference, and warns + offers a create-file quick fix when the view is
  missing. `Yii2ViewResolver` is the core (implements `yii\base\View::findViewFile`
  rules over the directory layout); `ViewReferenceContributor`/`ViewFileReference`
  provide Go-To navigation; `MissingViewInspection`/`CreateViewFileQuickFix` handle
  the missing-file case. The quick fix recomputes the target at fix time and creates
  the file via the VFS relative to an existing anchor dir (so it works in light tests
  too — do not switch it to `java.io`/`LocalFileSystem` path-string creation).
- **Extending the plugin**: register functionality (inspections, completion,
  references, line markers, etc.) under `<extensions defaultExtensionNs="com.intellij">`
  in `src/main/resources/META-INF/plugin.xml`. To interact with PHP/Yii2 code,
  use PSI APIs from the `com.jetbrains.php` plugin.
- **Dependencies**: managed via the Gradle version catalog at
  `gradle/libs.versions.toml`. Add new libraries there, not inline in
  `build.gradle.kts`.
- **Kotlin stdlib is NOT bundled** (`kotlin.stdlib.default.dependency=false` in
  `gradle.properties`) — it is provided by the platform at runtime.
- **Changelog**: managed by the `org.jetbrains.changelog` Gradle plugin; the
  plugin description/changelog shown in Marketplace is patched from
  `CHANGELOG.md` and `plugin.xml` at build time.

## Before publishing

`plugin.xml` still contains template placeholders that must be filled in: the
`<vendor>` (`YourCompany` / `yourcompany.com`) and the `<description>`.
