# 6. Troubleshooting

Read the **first** error in the console log; later errors are usually consequences.

## Jenkins / build machine

| Symptom (console) | Likely cause | Fix |
|---|---|---|
| `SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable or by setting the sdk.dir path in your project's local properties file` | `ANDROID_HOME` not set for the Jenkins service | Set it on the node (03 §3.4). Don't commit `local.properties` to work around this. |
| `ERROR: ANDROID_HOME is not set.` from *Verify environment* | same | same |
| `'java' is not recognized…` / `JAVA_HOME is set to an invalid directory` | No JDK for the service account | Install JDK 21/25, set `JAVA_HOME` on the node |
| `Android Gradle plugin requires Java 17 to run. You are currently using Java 11` | Old JDK on `PATH` | Point `JAVA_HOME` at JDK 17+ |
| `Unsupported class file major version …` | Gradle too old for the JDK | Use the wrapper (`gradlew.bat`), not a system Gradle; it pins 9.5.0 |
| `Minimum supported Gradle version is 9.5.0` | Wrapper changed / system Gradle used | Run `gradlew.bat`, keep `gradle-wrapper.properties` as committed |
| `Failed to install the following Android SDK packages … licences have not been accepted` | Licenses not accepted **for that SDK folder** | `sdkmanager --sdk_root=C:\Android\Sdk --licenses` |
| `Failed to find target with hash string 'android-37'` | Platform 37 missing and auto-download not possible | `sdkmanager "platforms;android-37"` |
| `'gradlew.bat' is not recognized` | Checkout failed, or Jenkinsfile isn't at repo root | Check the Checkout stage and the job's Script Path |
| `Could not resolve com.android.tools.build:gradle:9.3.3` / timeouts downloading | No internet for the service, or a corporate proxy | Configure proxy in `%GRADLE_USER_HOME%\gradle.properties` (`systemProp.https.proxyHost=…`) for the service account |
| `Timeout waiting to lock … It is currently in use by another Gradle instance` | Concurrent builds or a leftover daemon | `disableConcurrentBuilds()` is set; kill stray `java.exe` processes, use `--no-daemon` |
| `Filename too long` / odd file errors on Windows | Deep workspace paths | Enable Win32 long paths, or set a shorter workspace root (e.g. `C:\J\ws`) |
| `ERROR: Couldn't find any revision to build` | Branch name mismatch (`master` vs `main`) | Fix *Branch Specifier* in the job |
| `The provided repository URL is a local path… ` / local checkout blocked | Git plugin forbids local `file://` checkouts | Use a hosted repo (GitHub/GitLab/Gitea) |
| `ERROR: Could not find credentials entry with ID 'sample-api-token'` | Credential not created, ID typo, or folder-scoped credential used from outside the folder | Create it with the exact ID, in a store visible to the job |
| Build *UNSTABLE* (yellow) | Lint found problems | Open `app/build/reports/lint-results-debug.html` in Build Artifacts |
| `No test report files were found` | Tests didn't run (earlier failure), or path changed | Check the Unit tests stage; path is `app/build/test-results/testDebugUnitTest/*.xml` |
| Parameters not shown on first build | Normal | Jenkins reads `parameters {}` during the first run; use *Build with Parameters* afterwards |
| `Warning: A secret was passed to "bat" using Groovy String interpolation` | Double-quoted string with `${SECRET}` | Use single quotes so the shell expands the variable |
| `Keystore was tampered with, or password was incorrect` (release) | Wrong `android-keystore-password` | Fix the credential; never echo it to debug |

## App ↔ Jenkins (Live mode)

| Message in the app | Cause | Fix |
|---|---|---|
| **Can't reach Jenkins** | Wrong host/port. `localhost` used on the emulator. Jenkins not running. Firewall. | Emulator: `http://10.0.2.2:8080/`. Device: `adb reverse tcp:8080 tcp:8080` + `http://127.0.0.1:8080/` |
| **HTTP blocked by Android** | Cleartext to a host not in the debug network security config, or a release build | Use HTTPS, or add the host to `src/debug/res/xml/network_security_config.xml` |
| **Authentication failed (401)** | Wrong username or token (or using the password instead of an API token) | Create a new API token in Jenkins |
| **Permission denied (403)** | User lacks Overall/Read, Job/Read or Job/Build, or anonymous access disabled with no token | Grant permissions (Manage Jenkins → Security) or set the token |
| **Not found (404)** | Job renamed or moved into a folder; or the URL is missing a context path like `/jenkins/` | Refresh the job list; check the base URL |
| **This job needs parameters** | Parameterized job without defaults | Give each parameter a default |
| **Unexpected response from server** | URL points at a login page, proxy or non-Jenkins server | Open the URL + `api/json` in a browser to check |
| **Secure connection failed** | Self-signed HTTPS certificate | Use a certificate the device trusts |
| Artifact link asks for login in the browser | Expected: the browser has its own Jenkins session; the app never shares its token | Log in in the browser |

## Local Android Studio

| Symptom | Fix |
|---|---|
| Sync fails: `Unsupported Kotlin plugin version` / Compose compiler mismatch | Keep `kotlin` and `org.jetbrains.kotlin.plugin.compose` on the same version (both use `versions.kotlin` in the catalog). Don't add `org.jetbrains.kotlin.android`: AGP 9 has built-in Kotlin. |
| `compileSdk 37` not installed | SDK Manager → SDK Platforms → API 37 |
| Studio says the AGP version is newer than supported | Use Android Studio Quail 3 or newer (supports AGP 9.3) |
| Unit tests can't find `jenkins/server.json` | Run tests through Gradle or Studio's JUnit runner for the `app` module (resources come from `src/test/resources`) |
