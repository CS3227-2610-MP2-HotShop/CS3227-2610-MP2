# Agent Interaction Log

## User Prompt

"i need to push the latest jar into release folder"

The user chose to both commit the JARs and publish a GitHub Release, for
Windows and Linux. After Smart App Control blocked the local tests, they chose
to build the Windows JAR here without running the tests locally.

## Steps Taken

- Updated local `main` to `199dc7c` and confirmed that CI ("Gradle
  verification") passed on that commit and uploaded the `HotShop-linux`
  artifact.
- Ran `.\gradlew.bat check build shadowJar`. 393 of 688 tests failed in 23
  seconds with `UnsatisfiedLinkError`. The test reports named the cause: "An
  Application Control policy has blocked this file" for SQLite JDBC's DLL in
  `%TEMP%`.
- Confirmed that Smart App Control is on (registry state 1), and that the Code
  Integrity log recorded the block of `java.exe` loading the DLL at 1:28 pm.
- Downloaded the Linux JAR from the CI run and checked its JavaFX Linux
  libraries and manifest.
- Built the Windows JAR with `shadowJar`. It was up to date from the same
  commit. Compared both JARs: all 132 `hotshop/` and `db/` entries are
  identical, including byte-identical class files, and only the JavaFX native
  libraries differ.
- Launched the Windows JAR against a throwaway data folder for 15 seconds. The
  log showed `SQLException` caught by `Main.init`, so the startup error dialog
  opens rather than a crash.
- Committed both JARs under platform names, added `.gitignore` exceptions for
  them, and documented them in the User Guide, Developer Guide, and README.

## Reasoning Summary

- The platform names keep a local `shadowJar` build (`release/HotShop.jar`)
  ignored, so rebuilding never changes the committed files.
- The Windows JAR was not tested on this machine, but its application classes
  are identical to the Linux JAR's, and CI tested those.
- The User Guide warns about Smart App Control because Windows 11 testers may
  have it on. Turning it off cannot be reversed without reinstalling, so the
  guide does not tell users to do it.

## Changes Made

- `release/HotShop-windows.jar`, `release/HotShop-linux.jar`: built from `199dc7c`.
- `.gitignore`: exceptions for the two JARs.
- `docs/userGuide/GettingStarted.md`: download instructions and the Smart App
  Control warning.
- `docs/developerGuide/DevelopmentWorkflow.md`: how to refresh the published
  JARs, and the Smart App Control effect on local tests.
- `README.md`: a Prebuilt JARs section.

## Verification

- CI passed on `199dc7c`: `Gradle verification` run 36522490486.
- Local `check build shadowJar` failed with 393 failures, all caused by Smart
  App Control, as described above. The full suite was not run successfully on
  this machine.
- Compared both JARs' contents with `unzip`, as described above.
- `npm run docs:build` succeeded, and the crawl found 39 pages and 109 links and
  assets with none broken.
- The Linux JAR was not launched, since this machine runs Windows.

## Final Output and Conclusion

The JARs are committed through a pull request. The GitHub Release is created
from `main` after it merges. Until Smart App Control is off, HotShop and its
database tests cannot run on this machine.
