<h1 align="center">
  <br>
    <img src="./fastlane/metadata/android/en-US/images/icon.png" alt="LibreFind icon" width="150" />
  <br>
  LibreFind
  <br>
</h1>

<h4 align="center">Discover and replace proprietary apps with FOSS alternatives.</h4>

<h4 align=center>
  <a href="https://developer.android.com">
    <img src="https://img.shields.io/badge/Platform-Android-brightgreen?logo=android" alt="Android Platform">
  </a>
  <img src="https://img.shields.io/badge/API-24%2B-3DDC84?logo=android&color=blue" alt="Minimum SDK">
  <img alt="GitHub Release" src="https://img.shields.io/github/v/release/jksalcedo/librefind?include_prereleases&logo=github&label=Release&color=blue">
  <img alt="GitHub Downloads (all assets, all releases)" src="https://img.shields.io/github/downloads/jksalcedo/librefind/total?label=APK%20Downloads&color=brightgreen">
  <img alt="GitHub License" src="https://img.shields.io/github/license/jksalcedo/librefind?logo=MIT&label=License&color=blue">
  <a href="https://github.com/jksalcedo/librefind/actions/workflows/codeql.yml">
    <img src="https://github.com/jksalcedo/librefind/actions/workflows/codeql.yml/badge.svg" alt="CodeQL Advanced">
  </a>
  <img src="https://img.shields.io/github/last-commit/jksalcedo/librefind?color=blue" alt="Last Commit">
</h4>

<p align="center">
<a href="https://apt.izzysoft.de/packages/com.jksalcedo.librefind">
  <img alt="Get it on IzzyOnDroid" src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" width="160">
</a>

<a href="https://f-droid.org/packages/com.jksalcedo.librefind">
    <img src="https://f-droid.org/badge/get-it-on.png"
    alt="Get it on F-Droid"
    width="160">
</a>

  <a href="https://github.com/jksalcedo/librefind/releases">
  <img src="https://github.com/SilentCoderHere/aihub/blob/main/fastlane/metadata/android/en-US/images/badge_github.png" width="160" alt="Get it on GitHub">
  </a>

  <br>

  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/jksalcedo/librefind">
    <img alt="Get it on Obtainium" src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" width="140">
  </a>

   <a href="https://zapstore.dev/apps/naddr1qqtkxmmd9e4xkumpd33k2er09ekxjcnjv4nxjmnyqgsx2k5ctva9q7wztnpgrcrcsjnt30s0hq8mjjna7sjcef0j0saqa8grqsqqqlstgxhlut">
    <img alt="Zapstore" src="https://github.com/zapstore/zapstore/blob/master/assets/images/badge.png" width="140">
  </a>
</p>

> [!IMPORTANT]
> **Google has announced that, starting in 2026/2027, all apps on certified Android devices
> will require the developer to submit personal identity details directly to Google.
> Since the developers of this app do not agree to this requirement, this app will no longer
> work on certified Android devices after that time.**
> https://keepandroidopen.org/

## What is LibreFind?

LibreFind is a free, lightweight, and community-driven Android app designed to help you de-Google
your device. It scans your installed packages locally and queries our open-source database to flag
proprietary software, instantly pairing them with privacy-respecting FOSS alternatives.

### Core Features

* **Local Device Scanner:** Audits your installed apps entirely on-device to calculate your personal
  sovereignty score.
* **Global DB Search (Discover):** Look up any FOSS or proprietary app directly in our crowdsourced
  database to plan your next setup.
* **Alternative Recommendations:** Seamlessly browse community-vetted open-source alternatives
  tailored to replace mainstream big-tech software.
* **The Hub (Community):** View, review, and interact with pending app submissions to help maintain
  the database's integrity.
* **Gamified Progression:** Earn points for your contributions and unlock community rank badges (
  *Scout, Pathfinder, Guide, Curator, Vanguard*) directly on your profile.

---

## Screenshots

|                                      Dashboard                                      |                                     Discover                                      |                                       Alternative List                                        |
|:-----------------------------------------------------------------------------------:|:---------------------------------------------------------------------------------:|:---------------------------------------------------------------------------------------------:|
| ![Dashboard](fastlane/metadata/android/en-US/images/phoneScreenshots/dashboard.png) | ![Discover](fastlane/metadata/android/en-US/images/phoneScreenshots/discover.png) | ![Alternatives](fastlane/metadata/android/en-US/images/phoneScreenshots/alternative_list.png) |
|                         Scan apps & view sovereignty score                          |                             Search the FOSS database                              |                                  Browse curated alternatives                                  |

|                                      Community                                      |                                      Submission                                       |                                     Profile                                     |
|:-----------------------------------------------------------------------------------:|:-------------------------------------------------------------------------------------:|:-------------------------------------------------------------------------------:|
| ![Community](fastlane/metadata/android/en-US/images/phoneScreenshots/community.png) | ![Submission](fastlane/metadata/android/en-US/images/phoneScreenshots/submission.png) | ![Profile](fastlane/metadata/android/en-US/images/phoneScreenshots/profile.png) |
|                            Interact with pending entries                            |                                 Propose or link apps                                  |                          Track impact & unlock badges                           |

---

### Community Contributions

* **Discover & Vote:** Help the best alternatives rise to the top by upvoting high-quality
  open-source recommendations.
* **Propose New Pairings:** Suggest new FOSS alternatives for proprietary apps directly from your
  device.
* **Review and Vet:** Submit pros, cons, and detailed feedback for existing alternative profiles.
* **Moderate Pending Queue:** Use the Community tab to peer-review pending entries before they go
  live globally.

You can contribute directly within the Android client, or head over to the web version
at [librefind-submission.web.app](https://librefind-submission.web.app/).

---

### Required Permissions

* `QUERY_ALL_PACKAGES` – Used strictly to scan your local application list for proprietary tracking
  components. *(Restricted Permission)*
* `INTERNET` – Used securely to check app configurations against our decentralized Supabase
  database.

### 💬 Join the Community

Connect with fellow open-source advocates, discuss alternative apps, and help shape the project
roadmap:

* [Matrix Room](https://matrix.to/#/#librefind-community:matrix.org)
* [Reddit Subreddit](https://www.reddit.com/r/LibreFind)
* [Telegram Group](https://t.me/librefind)

---

## ☕ Support

LibreFind is a free, independently managed open-source project.

If you find this tool useful, consider supporting the project to help cover server costs and fund new features!

<div align="center">
  <a href="https://ko-fi.com/jksalcedo">
    <img src="https://storage.ko-fi.com/cdn/kofi3.png?v=3" alt="Buy Me a Coffee at ko-fi.com" height="50">
  </a>
</div>

---

## Contributing

You can make a massive difference to the project's health by:

* **Starring the repo**
* **Reporting bugs & suggesting features** via our issue tracker
* **Translating** via [Weblate](https://hosted.weblate.org/engage/librefind/) (Trial) or [Crowdin](https://crowdin.com/project/librefind)
* **Contributing code** via Pull Requests

Every single contribution counts.

---

## 🌍 Translations

[![Crowdin](https://badges.crowdin.net/librefind/localized.svg)](https://crowdin.com/project/librefind)
[![Weblate](https://hosted.weblate.org/widgets/librefind/-/svg-badge.svg)](https://hosted.weblate.org/engage/librefind/) *(Trial)*

LibreFind is a global project. If your native language isn't represented, head to
our [Crowdin Project Page](https://crowdin.com/project/librefind) or [Weblate](https://hosted.weblate.org/engage/librefind/) (currently on trial), choose your language, and help
make alternative discovery accessible to everyone.

---

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=jksalcedo/librefind&type=date&legend=top-left&theme=dark)](https://www.star-history.com/#jksalcedo/librefind&type=date&legend=top-left&theme=dark)

## License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.