# Simkl Check-in

A small Android app that opens simkl.com once a day for up to three accounts,
each in its own isolated WebView profile, and confirms each one reached its
logged-in page.

It does not alter the browser identity in any way. The WebView reports this
phone's real user agent, headers and device characteristics, and the app never
tries to solve or skip a Cloudflare check. If a check needs a tap, the app
sends a notification and waits for you.

## How it behaves

- Simkl's day runs on UTC. Attempts happen at minute 05 and 35 of every UTC
  hour, which is 5:35 AM IST onwards, every 30 minutes.
- Each account is tracked separately. One that succeeded is skipped for the
  rest of that UTC day; only the failed ones are retried.
- No internet means nothing is marked done, and the next slot tries again.
- When all accounts are done, the next attempt is 00:05 UTC the following day.
- The screen wakes and shows the page over the lock screen for a few seconds
  per account. The phone does not need to be unlocked.
- The log keeps the last 150 lines and is shown on the main screen.

## One-time setup

### 1. Create the repository

Create a **public** GitHub repository and upload these files to the `main`
branch. It must be public for the app to read its own releases. Nothing
private is in the code: your Simkl logins live only on the phone.

In the repository, open Settings > Actions > General > Workflow permissions and
choose "Read and write permissions".

### 2. Create a signing key

Android only installs an update that is signed with the same key as the
installed app. On any computer with Java installed, run:

    keytool -genkeypair -alias simkl -keyalg RSA -keysize 2048 -validity 20000 \
      -storetype PKCS12 -keystore release.p12 -storepass CHOOSE_A_PASSWORD \
      -dname "CN=Simkl Checkin"

    base64 -w0 release.p12        # macOS: base64 -i release.p12

Add two secrets under Settings > Secrets and variables > Actions:

- `KEYSTORE_BASE64`: the long text printed by the second command
- `KEYSTORE_PASSWORD`: the password you chose

Keep `release.p12` somewhere safe and never commit it.

### 3. Build and install

Open the Actions tab, choose "Build and release", and run it. When it
finishes, download `simkl-checkin.apk` from the Releases page on the phone and
install it.

### 4. Set up the app

1. Tap each "Required:" button until it says all permissions are granted.
2. Tap Account 1, log in to Simkl, then tap "I'm logged in: save". Repeat for
   accounts 2 and 3.
3. Tap "Run check-in now (test)" and confirm the log shows three check-ins.
4. On Samsung, also add the app to Settings > Battery > Never sleeping apps.
   On Realme, enable Auto-launch for the app.

## Automatic updates

- **Browser engine**: Android System WebView is updated by the Play Store on
  its own.
- **Libraries**: Dependabot opens an update pull request every Saturday. The
  workflow compiles it and merges it only if the build passes.
- **Releases**: every Monday the workflow publishes a new signed APK if
  anything was merged. Pushing to `main` yourself also publishes one.
- **The app**: once a day it checks the latest release and installs it. Android
  asks you to confirm the first update; after that it can usually install
  without asking.

## When something needs you

- A notification saying an account needs attention: open it, then log in again
  or complete the check shown on the page.
- A dependency pull request that stays open: its build failed, usually because
  of a major version jump. It is safe to leave or close.
- GitHub pauses scheduled workflows after 60 days without repository activity
  and emails you a one-click link to resume them.
