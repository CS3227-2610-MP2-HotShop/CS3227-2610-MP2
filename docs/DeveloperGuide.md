# HotShop Developer Guide

## Setup

Use JDK 25 and the included Gradle 9.1.0 Wrapper. Set JAVA_HOME to your JDK if
necessary. Import the root directory as a Gradle project in your IDE.

```powershell
.\gradlew.bat run
.\gradlew.bat test checkstyleMain checkstyleTest
.\gradlew.bat check build shadowJar
```

Use `./gradlew` on macOS/Linux. Initial dependency resolution requires network access.

## Structure

- `src/main/java/hotshop/Launcher.java`: executable JAR entry point.
- `src/main/java/hotshop/Main.java`: JavaFX lifecycle and scene loading.
- `src/main/resources/hotshop/`: FXML welcome view and stylesheet.
- `src/test/java/hotshop/`: reserved for future JUnit 5 tests.
- `config/checkstyle/checkstyle.xml`: executable style checks.
- `docs/`: guides and GitHub Pages source.
- `logs/`: agent interaction records.
- `release/HotShop.jar`: generated distribution, ignored by Git.

This is a single-project, non-modular build. Launcher is separate from the
Application subclass so the bundled JAR can launch JavaFX from the classpath.
No business layers or persistence have been introduced.

## Dependencies and checks

JavaFX 25.0.2 uses controls and FXML through OpenJFX Gradle plugin 0.1.0.
JUnit Jupiter 5.13.4 is configured with the JUnit Platform launcher.
Checkstyle 12.3.1 enforces mechanical SE-EDU conventions; semantic naming and
clarity still require review. Shadow 9.2.2 bundles runtime dependencies.
Native access is enabled in Gradle launch scripts and the JAR manifest for JavaFX.

The setup skill's exact file allow-list excludes test classes, so this scaffold
contains no automated tests yet. Test and test Checkstyle tasks report NO-SOURCE.
Add JUnit 5 tests when implementing behavior in a subsequent task.
Empty test directories exist locally but are not tracked by Git.

## Packaging and CI

`shadowJar` writes `release/HotShop.jar`. Build separately for each target OS and
architecture because JavaFX native libraries are platform-specific.
The JAR requires a separately installed Java 25 runtime.

GitHub Actions runs tests, Checkstyle, check, build, and shadowJar on pull
requests and pushes to main/master, and uploads a Linux JAR artifact.
The workflow also supports manual dispatch.

## GitHub Pages

After pushing, open repository Settings > Pages, select **Deploy from a branch**,
select the branch containing these files and **/docs**, and save.
GitHub publishes the site after its Pages build completes.
Hosting has not been enabled by this local setup.

## Acknowledgements

- [OpenJFX Gradle plugin](https://github.com/openjfx/javafx-gradle-plugin): dependency configuration.
- [SE-EDU Java conventions](https://se-education.org/guides/conventions/java/intermediate.html):
  basis for the Checkstyle rules.
- [Gradle](https://docs.gradle.org/9.1.0/release-notes.html): wrapper and Java 25 build support.
- [Shadow](https://gradleup.com/shadow/): executable dependency bundling.
