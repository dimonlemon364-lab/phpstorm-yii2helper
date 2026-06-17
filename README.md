# Yii2Helper

Navigation and tooling for the [Yii2](https://www.yiiframework.com/) PHP framework in
PhpStorm. Yii2Helper turns the "magic strings" Yii2 relies on — view names and routes —
into navigable references, and offers quick fixes to create the targets they point at when
they don't exist yet.

## Features

### View navigation

Ctrl/Cmd+Click a view name to jump straight to the view file, resolved with Yii2's own
`View::findViewFile()` rules (`@app`, `//` application-relative and `/` module-relative
prefixes; controller, widget and nested-view contexts):

- `render()`, `renderPartial()`, `renderAjax()`, `renderFile()`
- the mailer `compose()` view — a plain name or a `['html' => …, 'text' => …]` config,
  resolved against `@app/mail`

When the referenced view file is missing, an inspection warns on the view name and offers a
quick fix that creates the file from a Yii2 view template.

### Route / action navigation

Ctrl/Cmd+Click an action name in a route to jump to the controller action method
(`actionXxx`). Single-segment, cross-controller, absolute and sub-path routes are resolved;
inside a view file a relative route resolves against the rendering controller. Recognized
route-producing calls:

- `redirect([...])`
- `Url::to()`, `Url::toRoute()`, `Url::remember()`
- `Html::a()`, `Html::beginForm()`
- `UrlManager::createUrl()` / `createAbsoluteUrl()`
- the `'action'` config key of `ActiveForm::begin([...])`

When the route's action method is missing, an inspection warns and offers a quick fix that
creates the `actionXxx()` method rendering the matching view.

## Requirements

- **PhpStorm 2025.3.5+** (build `253`+). PhpStorm is required as the base IDE because the
  bundled PHP plugin depends on the `com.intellij.modules.php-capable` platform module,
  which IntelliJ IDEA — even Ultimate — does not provide.
- **JDK 21** for building (a JetBrains Runtime works).

## Building & running

The project uses the Gradle wrapper with the IntelliJ Platform Gradle Plugin (v2).

| Task | Command |
|------|---------|
| Run the plugin in a sandbox IDE | `./gradlew runIde` |
| Run tests | `./gradlew test` |
| Verify compatibility against target IDEs | `./gradlew verifyPlugin` |
| Build the distributable | `./gradlew buildPlugin` (output in `build/distributions/`) |
| Publish to JetBrains Marketplace | `./gradlew publishPlugin` |

The same tasks are wired as IDE run configurations in `.run/` (Run Plugin, Run Tests, Run
Verifications, Publish Plugin).

## Publishing

Releasing to [JetBrains Marketplace](https://plugins.jetbrains.com) uses the
`publishPlugin` Gradle task (or the **Publish Plugin** run configuration):

1. Bump `version` in `gradle.properties`.
2. Document the changes under a matching `## <version>` section in [CHANGELOG.md](./CHANGELOG.md)
   — the build renders it into the Marketplace "What's new".
3. `./gradlew buildPlugin` then `./gradlew verifyPlugin`.
4. `./gradlew publishPlugin` to sign + upload.

Publishing requires environment variables (never committed): `PUBLISH_TOKEN`, and for
signing `CERTIFICATE_CHAIN` / `PRIVATE_KEY` / `PRIVATE_KEY_PASSWORD`. The very first version
of a new plugin must be uploaded once via the [Marketplace web UI](https://plugins.jetbrains.com/plugin/add)
to create the listing; subsequent releases can use `publishPlugin`.

## License

Licensed under the [MIT License](./LICENSE).
