<div align="center">

<a href="https://www.insta4dl.com/"><img src="docs/assets/banner.png" alt="Insta4DL for Android – save Instagram Reels, videos, photos and stories" width="100%"></a>

<br>

[![Android CI](https://github.com/emin3m68-hue/Insta4DL-Android/actions/workflows/build-apk.yml/badge.svg?branch=main)](https://github.com/emin3m68-hue/Insta4DL-Android/actions/workflows/build-apk.yml)
[![Latest release](https://img.shields.io/github/v/release/emin3m68-hue/Insta4DL-Android?label=release&color=A855F7)](https://github.com/emin3m68-hue/Insta4DL-Android/releases/latest)
![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)
![Permissions](https://img.shields.io/badge/permissions-Internet%20only-0EA5E9)
![No ads SDK](https://img.shields.io/badge/ads%20SDK-none-22C55E)

**The official Android app for [Insta4DL.com](https://www.insta4dl.com/) – download public Instagram Reels, videos, photos, stories and carousels straight to your phone.**

[**⬇ Download the APK**](https://github.com/emin3m68-hue/Insta4DL-Android/releases/latest) &nbsp;·&nbsp; [Website](https://www.insta4dl.com/) &nbsp;·&nbsp; [Report a bug](https://github.com/emin3m68-hue/Insta4DL-Android/issues/new/choose)

</div>

---

## Highlights

<table>
<tr>
<td width="33%" valign="top">

### ↗ Share to save
In Instagram, tap **Share → Insta4DL**. The link opens in the app, ready to download.

</td>
<td width="33%" valign="top">

### ▣ One-tap paste
Copied a link? The **Paste** button drops it into the downloader for you.

</td>
<td width="33%" valign="top">

### ↓ Saved where you expect
Files land in **`Downloads/Insta4DL`**. **Files** opens the folder in one tap.

</td>
</tr>
<tr>
<td valign="top">

### ✦ Full quality
Reels and videos as HD MP4, photos at full size, whole carousels at once.

</td>
<td valign="top">

### 🔒 Private by design
No sign-up and no Instagram login. The app asks for the **Internet** permission only.

</td>
<td valign="top">

### ⚡ Light and fast
A small native wrapper with no ads or analytics SDKs, always running the latest website.

</td>
</tr>
</table>

## Install

1. Open the **[latest release](https://github.com/emin3m68-hue/Insta4DL-Android/releases/latest)** and download `Insta4DL-x.y.z.apk` on your phone.
2. Open the file. If Android asks, allow installs from your browser or file manager.
3. Launch **Insta4DL** – that's it.

> **Requires Android 10 (API 29) or newer.** You can also install the app as a web app straight from [insta4dl.com/app](https://www.insta4dl.com/app/).

## How to use

| Step | What to do |
|---|---|
| **1. Find a post** | Any public Reel, video, photo, story or carousel on Instagram. |
| **2. Send it to the app** | **Share → Insta4DL**, or copy the link and tap **Paste**. |
| **3. Download** | Pick the quality you want. The file is saved to `Downloads/Insta4DL`. |

Private accounts and content that needs a login can't be downloaded – the app never asks for your Instagram account.

## Build from source

```bash
git clone https://github.com/emin3m68-hue/Insta4DL-Android.git
cd Insta4DL-Android
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
```

You need **JDK 17** and the **Android SDK (API 35)**. Android Studio installs both and can open the project as is.

Every push to `main` is built by **[Android CI](https://github.com/emin3m68-hue/Insta4DL-Android/actions/workflows/build-apk.yml)**; the debug APK is attached to each run as an artifact.

<details>
<summary><strong>Publishing a release</strong></summary>

<br>

Push a version tag and CI builds a release APK and publishes it on the **Releases** page with generated notes:

```bash
git tag v1.1.0
git push origin v1.1.0
```

The version name comes from the tag (`v1.1.0` → `1.1.0`) and the version code from the CI run number.

To sign releases with your own key, add these repository secrets (**Settings → Secrets and variables → Actions**):

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | Your `.jks` file, base64-encoded (`base64 -w0 release.jks`) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

Without them the release APK is signed with the debug key: it installs fine, but keep using the same key once you start, or users will have to uninstall before updating.

</details>

<details>
<summary><strong>Technical details</strong></summary>

<br>

| Setting | Value |
|---|---|
| Application ID | `com.insta4dl.app` |
| Min / target SDK | 29 (Android 10) / 35 |
| Language | Java 17 |
| Build | Gradle 8.10.2 (wrapper) · Android Gradle Plugin 8.7.3 |
| WebView | AndroidX WebKit 1.14.0 |
| Permissions | `android.permission.INTERNET` |
| Downloads | `Downloads/Insta4DL` via `DownloadManager` / `MediaStore` |

```text
Insta4DL-Android/
├── app/src/main/
│   ├── java/com/insta4dl/app/MainActivity.java   WebView, downloads, share & paste
│   ├── assets/blob-bridge.js                     saves in-page (blob:) downloads
│   ├── res/                                      icons, theme, strings
│   └── AndroidManifest.xml
├── docs/assets/                                  banner, icon, social preview
├── .github/workflows/build-apk.yml               CI and releases
└── gradlew                                       Gradle wrapper
```

The app shows the live website in an Android WebView, so new website features reach the app without an update.

</details>

## FAQ

<details>
<summary><strong>Is it free?</strong></summary>
<br>
Yes. No account, no subscription, and the app adds no advertising SDK of its own.
</details>

<details>
<summary><strong>Do I need to log in to Instagram?</strong></summary>
<br>
No. The app works with public posts and never asks for your Instagram account.
</details>

<details>
<summary><strong>Where are my downloads?</strong></summary>
<br>
In <code>Downloads/Insta4DL</code> on your phone. Tap <b>Files</b> in the app to open it.
</details>

<details>
<summary><strong>A download doesn't work – what now?</strong></summary>
<br>
Try the same link on <a href="https://www.insta4dl.com/">insta4dl.com</a>. If it fails there too, the post is probably private, deleted or age-restricted. If it only fails in the app, please <a href="https://github.com/emin3m68-hue/Insta4DL-Android/issues/new/choose">open an issue</a> with your device and Android version.
</details>

<details>
<summary><strong>Why isn't it on Google Play?</strong></summary>
<br>
Apps that download from social networks are often removed from Google Play, so Insta4DL is distributed as an APK through GitHub Releases and as a web app on <a href="https://www.insta4dl.com/app/">insta4dl.com/app</a>.
</details>

## Responsible use

Download only content you own or have permission to use, and respect creators' rights and Instagram's terms.
Insta4DL is an independent project and is **not affiliated with, endorsed or sponsored by Instagram or Meta**. Instagram is a trademark of its owner.

---

<div align="center">
<sub>Made for <a href="https://www.insta4dl.com/">insta4dl.com</a> · <a href="CHANGELOG.md">Changelog</a> · <a href="https://github.com/emin3m68-hue/Insta4DL-Android/issues">Issues</a></sub>
</div>
