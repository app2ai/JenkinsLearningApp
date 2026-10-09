# 3. Local Jenkins on Windows 11

This sets up a Jenkins controller on your PC that builds this project with the
root `Jenkinsfile` (Windows `bat` steps). Menu names are from current Jenkins
LTS. If a label differs slightly in your version, the structure is the same.

## 3.1 Install Java and Jenkins

1. **JDK:** current Jenkins LTS runs on **Java 21 or 25** (Java 17 support has
   ended; see the [Jenkins Java support policy](https://www.jenkins.io/doc/book/platform-information/support-policy-java/)).
   Install a JDK 21 (e.g. Eclipse Temurin) to a path like `C:\Program Files\Eclipse Adoptium\jdk-21…`.
   The same JDK can run Gradle for the build (AGP 9.3 needs 17+).
2. **Jenkins:** download the Windows LTS installer (`.msi`) from jenkins.io and run it.
   - It installs Jenkins as a Windows **service** and asks which account runs it.
     The service account matters: environment variables, the Gradle cache and
     SDK licenses are all per-account (see 3.3).
   - Keep port **8080** unless it's taken.
3. Open `http://localhost:8080/`, unlock with the initial admin password (the
   wizard shows the file path), choose **Install suggested plugins**, and create
   your admin user.

## 3.2 Verify the required plugins

*Manage Jenkins → Plugins → Installed plugins.* Make sure these are installed and enabled:

| Plugin | Used for |
|---|---|
| Pipeline (`workflow-aggregator`) | `pipeline { }`, stages, `bat`, `catchError`, `timeout` |
| Git | `checkout scm` |
| Credentials + Credentials Binding | credential store, `withCredentials`, `string`, `file`, `usernamePassword` |
| JUnit | `junit` test report publishing |
| Pipeline: Stage View *(optional)* | stage boxes on the job page |

All of them come with "suggested plugins". Install any that are missing under *Available plugins*.

## 3.3 Android SDK for the Jenkins service

The Jenkins service doesn't see **your** user's environment variables, and it
may not be able to write to your user profile. Give it its own SDK in a path
without spaces:

```powershell
# 1. Download "Command line tools only" for Windows from developer.android.com/studio
# 2. Unzip so this path exists: C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot"   # your JDK path
cd C:\Android\Sdk\cmdline-tools\latest\bin
.\sdkmanager.bat --sdk_root=C:\Android\Sdk "platform-tools" "platforms;android-37" "build-tools;36.0.0"
.\sdkmanager.bat --sdk_root=C:\Android\Sdk --licenses     # answer y to each
```

Reusing Android Studio's SDK (`%LOCALAPPDATA%\Android\Sdk`) can work too, but a
separate SDK avoids permission problems and surprises when Studio updates things.

## 3.4 Tell Jenkins where Java and the SDK are (node environment variables)

*Manage Jenkins → Nodes → Built-In Node → Configure → Node Properties →
☑ Environment variables → Add:*

| Name | Value (example) |
|---|---|
| `ANDROID_HOME` | `C:\Android\Sdk` |
| `JAVA_HOME` | `C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot` |
| `GRADLE_USER_HOME` *(optional)* | `C:\Jenkins\gradle-home` |

Why on the node and not in the Jenkinsfile? These are **machine facts**, like
`sdk.dir` in a developer's `local.properties`. The same Jenkinsfile then works
on any agent that has its own paths. The `Verify environment` stage prints them
and fails early if something is missing.

`GRADLE_USER_HOME` is optional. Without it, a service running as *Local System*
keeps the Gradle cache under `C:\Windows\System32\config\systemprofile\.gradle`,
which is hard to find and clean.

> **Built-in node:** Jenkins recommends running builds on separate agents, not
> on the controller. For a personal learning setup on one PC, building on the
> built-in node is fine; make sure it has at least 1 executor
> (*Built-In Node → Configure → Number of executors*).

## 3.5 Put the project in Git

Jenkins checks out the Jenkinsfile and code from Git, so push this project to a
repository Jenkins can reach, e.g. a **private GitHub repository**:

```powershell
cd C:\path\to\JenkinsLearningApp
git init -b main
git add .
git status            # check: no local.properties, no *.jks, no secrets listed
git commit -m "JenkinsLearningApp"
git remote add origin https://github.com/<you>/JenkinsLearningApp.git
git push -u origin main
```

Jenkins' Git plugin refuses `file://` / local-path repositories by default (a
security fix), so a hosted repository is the simplest option.

## 3.6 Create the credentials

*Manage Jenkins → Credentials → System → Global credentials (unrestricted) →
Add Credentials.* Use **exactly these IDs**: the Jenkinsfile refers to them by ID.

| ID | Kind | Value | Needed for |
|---|---|---|---|
| `git-checkout` | Username with password | GitHub username + a fine-grained personal access token (read-only *Contents*) | Cloning a private repo (job config, not Jenkinsfile) |
| `sample-api-token` | Secret text | any dummy value, e.g. `demo-123456` | `RUN_CREDENTIALS_DEMO` |
| `sample-config-file` | Secret file | any small text file | `RUN_CREDENTIALS_DEMO`. Create it in a **folder** store to see folder scope (3.7). |
| `android-release-keystore` | Secret file | your `.jks` | `BUILD_RELEASE` (optional) |
| `android-keystore-password` | Secret text | keystore password | `BUILD_RELEASE` |
| `android-release-key` | Username with password | username = key alias, password = key password | `BUILD_RELEASE` |

Details on scopes, masking and what not to do: [04-credentials-and-secrets.md](04-credentials-and-secrets.md).

## 3.7 Create the pipeline job

Optional, to see **folder-scoped credentials**: *New Item → Folder* named
`android`. Open it → *Credentials* → the folder's store → add
`sample-config-file` there. Only jobs inside `android` can use it.

1. *New Item* (inside the folder, or at top level) → name `JenkinsLearningApp` → **Pipeline** → OK.
2. *Pipeline* section → Definition: **Pipeline script from SCM**
   - SCM: Git, Repository URL: `https://github.com/<you>/JenkinsLearningApp.git`
   - Credentials: `git-checkout` (for private repos)
   - Branch: `*/main`
   - Script Path: `Jenkinsfile` (or `ci/linux/Jenkinsfile` on a Linux agent)
3. Save → **Build Now**.

On the **first** build Jenkins learns the `parameters { }` block. After that
the button becomes **Build with Parameters** (`RUN_LINT`,
`RUN_CREDENTIALS_DEMO`, `BUILD_RELEASE`).

## 3.8 Get the APK

- Build page → **Build Artifacts** → `app/build/outputs/apk/debug/app-debug.apk`.
- Direct link to the newest successful one:
  `http://localhost:8080/job/JenkinsLearningApp/lastSuccessfulBuild/artifact/app/build/outputs/apk/debug/app-debug.apk`
  (inside a folder: `/job/android/job/JenkinsLearningApp/…`).
- Install it: `adb install -r app-debug.apk`.
- Open the app → **Settings**. "This APK was built by" shows **Jenkins (CI)** and
  the Jenkins build number, injected through `BUILD_NUMBER` → `BuildConfig`.

Test results: build page → **Test Result** (from the `junit` step). HTML
reports: Build Artifacts → `app/build/reports/…`.

## 3.9 Let the app talk to this Jenkins

1. Your user menu → **Security → API Token → Add new Token** → copy it once.
2. In the app on the emulator: **Settings → Live Jenkins**, URL `http://10.0.2.2:8080/`,
   your username, the token → **Save & test**.
3. Dashboard and Jobs now show real data. "Build now" queues a real build.

`Manage Jenkins → System → Jenkins URL` (e.g. `http://localhost:8080/`) is the
address Jenkins uses in links it *generates*. The app ignores those and builds
artifact links from the URL you configured, because `localhost` inside an
emulator would point at the emulator.
