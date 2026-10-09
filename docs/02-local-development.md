# 2. Local development (Windows 11 + Android Studio)

## Requirements

| Tool | Version used by this project | Notes |
|---|---|---|
| Android Studio | Quail 3 (2026.1.3) | Supports AGP up to 9.3 |
| Android Gradle Plugin | 9.3.3 | `gradle/libs.versions.toml` |
| Gradle (wrapper) | 9.5.0 | Minimum for AGP 9.3; downloaded by `gradlew.bat` |
| Kotlin | 2.4.0 | AGP 9 "built-in Kotlin"; no `kotlin-android` plugin |
| JDK for Gradle | 17 or newer (Studio's bundled JBR 25 is fine) | Bytecode targets Java 17 |
| Android SDK Platform | API 37 (`compileSdk`) | targetSdk 36, minSdk 26 |

**Gradle JDK in Android Studio:** *Settings → Build, Execution, Deployment →
Build Tools → Gradle → Gradle JDK.* The bundled JetBrains Runtime works. On the
command line, Gradle uses `JAVA_HOME`.

## First run

1. Open the `JenkinsLearningApp` folder in Android Studio and let it sync.
   Studio creates `local.properties` with your `sdk.dir`.
2. If SDK Platform 37 is missing, Studio offers to install it (or use *SDK Manager*).
3. Run the `app` configuration on an emulator. The app starts in **Sample mode**
   and needs no Jenkins.

Command-line equivalents (PowerShell, from the project root):

```powershell
.\gradlew.bat --version                    # downloads Gradle 9.5.0 the first time
.\gradlew.bat :app:testDebugUnitTest       # JVM unit tests
.\gradlew.bat :app:assembleDebug           # app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat :app:installDebug            # install on the running emulator/device
.\gradlew.bat :app:lintDebug               # Android Lint
.\gradlew.bat :app:connectedDebugAndroidTest   # instrumentation tests (needs emulator/device)
```

Test report: `app\build\reports\tests\testDebugUnitTest\index.html`.

## `local.properties`: what it is and isn't for

`local.properties` lives in the project root, is **ignored by Git**, and is
**different on every machine**. Android Studio writes `sdk.dir` there. This
project also reads one optional, **non-secret** key from it:

```properties
sdk.dir=C\:\\Users\\YOUR_USER\\AppData\\Local\\Android\\Sdk
JENKINS_BASE_URL=http://10.0.2.2:8080/
```

Start from `local.properties.example`. (Backslashes and `:` are escaped because
this is a Java `.properties` file.)

How `app/build.gradle.kts` resolves `JENKINS_BASE_URL` (first match wins):

1. Environment variable `JENKINS_BASE_URL` (e.g. set by Jenkins)
2. Gradle property: `.\gradlew.bat assembleDebug -PjenkinsBaseUrl=http://10.0.2.2:8080/`
3. `local.properties` key `JENKINS_BASE_URL`
4. Default `http://10.0.2.2:8080/`

The value is validated (http/https, has a host, no `user:password@`, no query).
An invalid value logs a warning *without printing the value* and falls back to
the default. The result becomes `BuildConfig.DEFAULT_JENKINS_BASE_URL`, which is
public on purpose: it's only a pre-filled default in Settings.

**Don't** put tokens, passwords or keystore details in `local.properties`. It's
plain text, tools read it, it gets copied with project folders, and it's easy
to commit by accident in another repo. For a local secret, use an environment
variable for the current shell session (below).

## A local build-time secret (only if you need one)

The `:app:verifySampleToken` task shows how a build-time secret is consumed. To
try it locally without leaving the secret in your PowerShell history:

```powershell
# PowerShell 7.1+ (pwsh): input is masked, and this line has no secret in it
$env:SAMPLE_TOKEN = Read-Host -MaskInput "Sample token"

# Windows PowerShell 5.1 alternative:
# $s = Read-Host -AsSecureString "Sample token"
# $env:SAMPLE_TOKEN = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))

.\gradlew.bat :app:verifySampleToken
Remove-Item Env:SAMPLE_TOKEN           # forget it when done
```

Typing `$env:SAMPLE_TOKEN = "abc123"` directly puts the value in your session
history and possibly in PSReadLine's history file
(`%APPDATA%\Microsoft\Windows\PowerShell\PSReadLine\ConsoleHost_history.txt`).
Recent PSReadLine versions skip lines that look sensitive, but don't rely on that.

## Connecting the app to your local Jenkins

| Where the app runs | URL to use | Why |
|---|---|---|
| Android Emulator | `http://10.0.2.2:8080/` | Inside the emulator, `localhost` is the emulator itself. `10.0.2.2` is its alias for the host PC. |
| Physical device over USB | `http://127.0.0.1:8080/` after `adb reverse tcp:8080 tcp:8080` | adb forwards the phone's port 8080 to the PC. Works without Wi-Fi or firewall changes. |
| Physical device over Wi-Fi | `http://<PC LAN IP>:8080/` | Allow inbound TCP 8080 in Windows Defender Firewall, and add the IP to the **debug** network security config (below). Better: put Jenkins behind HTTPS. |

Then, in the app: **Settings → Live Jenkins → URL, username, API token → Save & test.**
Create the token in Jenkins under *your user menu → Security → API Token → Add new Token*.
The token stays **in memory only** and is forgotten when the app process ends.

### Cleartext HTTP (http://) and the network security config

Android blocks cleartext HTTP by default. This project keeps that default and
adds a narrow, debug-only exception:

- `app/src/main/res/xml/network_security_config.xml` (all builds): `cleartextTrafficPermitted="false"`.
- `app/src/debug/res/xml/network_security_config.xml` (debug only, overrides the above): cleartext allowed **only** for `10.0.2.2`, `127.0.0.1`, `localhost`.

So a release APK can never talk to Jenkins over plain HTTP, and a debug APK can
do so only with your own machine. To use a LAN IP from a debug build, add
`<domain includeSubdomains="false">192.168.1.20</domain>` to the **debug** file.
Never enable `usesCleartextTraffic="true"` globally. For anything beyond local
experiments, use HTTPS.

If you see *"HTTP blocked by Android"* in the app, the host isn't in that list.
