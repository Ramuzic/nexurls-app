# NexURLs app: build guide

This folder is the NexURLs phone app for iPhone and Android. It shows nexurls.com inside a real native app with:

- a native bottom tab bar (Home, Next, Reels, Community, Chat)
- native onboarding screens on first launch, ending with the notifications prompt
- push notifications through your existing OneSignal app ("nexurls"), so chat, reply and like alerts from the site reach the app
- a native offline screen with a Try again button
- pull to refresh and swipe back on iPhone, the back button on Android
- the phone's own share sheet for the site's Share buttons
- links to other websites open in the phone's browser; nexurls.com links stay in the app

The site side is already live (NexURLs Site Fixes 1.2.6). Inside the app it hides the web bottom nav, the footer, the "install the app" popup, the browser push prompts and the floating chat button.

You build it on your Mac. Nothing here needs editing by hand except `www/app.json` if you want to change tabs or wording.

---

## 1. One-time setup on your Mac

1. Install **Node.js** (the LTS version) from nodejs.org.
2. Install **Xcode** from the Mac App Store, open it once and let it finish installing its parts.
3. Install **Android Studio** from developer.android.com/studio and open it once (it downloads the Android SDK).

## 2. Get the project ready

1. Unzip this folder somewhere easy, like your Desktop.
2. Open **Terminal**, type `cd ` (with a space), drag the folder into the Terminal window, and press Return.
3. Run:

```
npm install
```

4. **App icon:** put your icon at `resources/icon.png` (1024 x 1024 pixels, square, no transparency). Optionally add `resources/splash.png` (2732 x 2732, logo in the middle). Then run:

```
npm run icons
npx cap sync
```

## 3. iPhone (Xcode)

```
npx cap open ios
```

In Xcode:

1. Click **App** at the top of the left sidebar, then the **App** target, then **Signing & Capabilities**.
2. **Team:** pick your Apple Developer account. Bundle Identifier stays `com.nexurls.app`.
3. Click **+ Capability** and add **Push Notifications**.
4. Click **+ Capability** again, add **Background Modes**, and tick **Remote notifications**.
5. **Add OneSignal:** File > Add Package Dependencies. Paste `https://github.com/OneSignal/OneSignal-XCFramework`, set the rule to "Up to Next Major Version" from 5.0.0, click Add Package, then tick only **OneSignalFramework** for the **App** target and click Add Package.
6. Pick an iPhone simulator (or your plugged-in iPhone) at the top and press the Play button.

If you skip step 5 the app still builds and runs; push just stays off until you add it.

## 4. Android (Android Studio)

```
npx cap open android
```

1. Wait for "Gradle sync" to finish (bottom bar). The first time takes a few minutes.
2. Pick an emulator or your plugged-in phone at the top and press the green Run button.

## 5. Turn on push for the app (OneSignal dashboard)

Your OneSignal app "nexurls" already handles the website. Add the phone platforms to the same app:

- **iPhone:** Apple Developer site > Certificates, IDs & Profiles > Keys > create a key with **Apple Push Notifications service (APNs)**, download the .p8 file. In OneSignal: nexurls > Settings > Push & In-App > **Apple iOS (APNs)** > choose the .p8 key, enter the Key ID, your Team ID and `com.nexurls.app`.
- **Android:** in the Firebase console create a project, add an Android app `com.nexurls.app`, then Project settings > Service accounts > Generate new private key (a JSON file). In OneSignal: nexurls > Settings > Push & In-App > **Google Android (FCM)** > upload that JSON.

Keys stay in the OneSignal and Apple dashboards. Nothing secret goes in this project.

How it connects: when a signed-in member opens the app, the site tells the app who they are, and the app registers that phone with OneSignal under the same member ID the site already sends to. Tapping a notification opens that page inside the app.

## 6. Test checklist

- First launch shows the 4 onboarding screens; the last one asks for notifications. It only shows once.
- Each tab opens the right page and lights up. Tapping the tab you are on scrolls to the top.
- Chat opens the chat panel.
- Turn on airplane mode: the offline screen shows. Turn it off: the page comes back.
- Share on a link page opens the phone's share sheet.
- Visit on a link opens the website in Safari or Chrome, not inside the app.
- No web bottom nav, no footer, no "install the app" popup inside the app.
- Sign in, send yourself a chat message from another account: the notification arrives, and tapping it opens the chat.

To see the web page's console on iPhone: Safari on the Mac > Settings > Advanced > Show features for web developers, then the Develop menu > your simulator > the page.

## 7. Changing tabs, colors or onboarding text

Everything is in `www/app.json`. Change it, then run `npx cap copy` and build again. Icons available for tabs and onboarding: home, next, reels, community, chat, share, bell.

## 8. Before you submit to the stores

Accounts you create yourself: Apple Developer Program ($99 a year) and Google Play Console ($25 once). Both ask for ID verification.

Apple will check these, so sort them out first:

1. **Paid things inside the app.** Pro membership and NexCredits are digital purchases. Apple normally requires its own in-app purchase for those. US apps may link out to your web checkout; outside the US the rules differ. The simplest safe route is to hide the Pro and credit checkout inside the iPhone app (the app sets `html.nexapp` on every page, so this is a small CSS change) or link out to the website for US users.
2. **Sign in with Apple.** The site offers Google, Facebook and X sign-in. Apple requires an equivalent privacy-friendly option in that case, which in practice means adding Sign in with Apple (Nextend Social Login supports Apple).
3. **Delete account.** Members must be able to delete their account from inside the app. Make sure there is a "Delete my account" option in the profile settings.
4. **Reporting and blocking.** Links and posts can already be reported. Apple also wants members to be able to block other members.
5. **Privacy policy URL** and the App Privacy answers in App Store Connect.
6. **Screenshots:** 6.7 inch and 6.5 inch iPhone sizes, plus Android phone screenshots for Google Play.

To make a store build:

- iPhone: in Xcode set the version and build number, choose Any iOS Device, then Product > Archive > Distribute App > App Store Connect.
- Android: Build > Generate Signed App Bundle (keep the keystore file and password safe forever; you need the same one for every update), then upload the .aab in Google Play Console.

## What is where

| File | What it does |
| --- | --- |
| `www/app.json` | Tabs, colors, onboarding text, OneSignal app id |
| `capacitor.config.json` | App id, start page, settings |
| `ios/App/App/RootViewController.swift` | iPhone tab bar, offline screen, pull to refresh, chat tab |
| `ios/App/App/OnboardingViewController.swift` | iPhone onboarding |
| `ios/App/App/NexAppPlugin.swift` | What the website can ask the app to do |
| `android/app/src/main/java/com/nexurls/app/MainActivity.java` | Android tab bar, offline screen, chat tab |
| `android/app/src/main/java/com/nexurls/app/OnboardingActivity.java` | Android onboarding |
| `android/app/src/main/java/com/nexurls/app/MainApplication.java` | Android push setup |
