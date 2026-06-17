<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Yii2Helper Changelog

## Unreleased

## 0.2.0

### Added

- Route/action navigation now recognizes more route-producing calls: `Url::remember()`,
  `Html::beginForm()`, `UrlManager::createUrl()` / `createAbsoluteUrl()`, and the
  `'action'` config key of `ActiveForm::begin([...])`.
- Mailer view navigation: Ctrl/Cmd+Click the view in `compose()` (a plain name or the
  `html` / `text` config keys) to jump to the mail view under `@app/mail`, with a
  missing-view inspection + create-file quick fix.

### Fixed

- Relative routes (e.g. `Url::to(['create'])`, `Html::a()`, `Url::toRoute('create')`) now
  resolve to the controller action when written inside a **view file**, mapped from the
  view path to its rendering controller.

## 0.1.0

### Added

- View navigation: Ctrl/Cmd+Click a view name in `render()`, `renderPartial()`,
  `renderAjax()` or `renderFile()` to jump to the view file, following Yii2's
  `View::findViewFile()` resolution rules (controller, widget and nested-view
  contexts; `@app`, `//` and `/` prefixes).
- Missing-view inspection with a quick fix that creates the view file from a Yii2
  view template.
- Route/action navigation: Ctrl/Cmd+Click an action name in a route
  (`redirect([...])`, `Url::to()`, `Url::toRoute()`, `Html::a()`) to jump to the
  controller action method (current-controller, cross-controller, absolute and
  sub-path routes).
- Missing-action inspection with a quick fix that creates the `actionXxx()` method
  rendering the matching view.
