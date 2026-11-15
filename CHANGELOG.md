# Changelog

All notable changes to Insta4DL for Android are listed here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [semantic versioning](https://semver.org/).

## [Unreleased]

### Added
- Gradle wrapper (`./gradlew`), so the project builds the same way everywhere.
- Tagged releases: pushing a `v*` tag builds a release APK and publishes it on the Releases page.
- Optional release signing through repository secrets.
- Issue templates, `.editorconfig` and `.gitattributes`.

### Changed
- Redesigned README with a project banner and a shorter install guide.
- CI renamed to **Android CI**; it now also runs on pull requests and caches Gradle.

## [1.0.0]

### Added
- First version: WebView client for insta4dl.com with Share → Insta4DL, one-tap paste,
  downloads to `Downloads/Insta4DL`, blob download support and a Home / Paste / Files / Reload bar.
