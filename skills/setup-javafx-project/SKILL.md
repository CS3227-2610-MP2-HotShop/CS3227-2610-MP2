---
name: setup-javafx-project
description: Set up the basic project structure for a Gradle-managed JavaFX
  application. Use this skill when initializing a new JavaFX project.
---

# JavaFX Project Setup

## Objective

Set up a clean, production-ready baseline structure for a JavaFX application managed using Gradle. Do not run this skill if there are existing files within this project directory, excluding files related to LLM and skills configuration. This skill should only be used for the creation of a fresh new JavaFX project, and not to add on to an existing one.

The requirements of the project are as such:
- All java source code should live in the src/ directory
- All documentation for this project including UserGuide.md and DeveloperGuide.md should live in the docs/ directory
- A Reflections.md file is needed for the user to record their reflections on AI workflows throughout the duration of project development. This file should live within docs/ and be empty at the start
- All LLM interactions during the course of project development are to be logged and stored within the logs/ directory. 
- This project is managed using Gradle. As such, gradle should be configured with the appropriate dependencies (such as JavaFX, JUnit and shadowJar)
- Gradle should also be used to maintain a consistent coding convention throughout the project, a checkstyle function should be set up that checks the codebase for violations of coding standards, following the [SE-EDU Java Coding Standard requirements](https://se-education.org/guides/conventions/java/intermediate.html)
- CI is also required for this project, as such set up a gradle.yml Github Actions workflow, that runs the included JUnit tests as well as checkstyle on every pull request.
- A website for this application is also required and is to be set up via Github Pages, with the docs/ directory serving as the base directory
- A JAR file of this application will need too be created using shadowJar and put into the release/ directory

## Filesystem Structure

The following filesystem structure is an exact allow-list, not an example.

The agent MUST create only the files and directories listed below unless an
additional file is strictly required for Gradle itself to function.

Do NOT introduce additional architecture, package layers, modules, directories,
placeholder files, or generated source sets.

current-directory/
- .github/
  - workflows/
    - gradle.yml
- config/
  - checkstyle/
    - checkstyle.xml
- docs/
  - DeveloperGuide.md
  - Reflections.md
  - UserGuide.md
  - index.md
  - _config.yml
- logs/
- release/
- src/
  - main/
    - java/
      - package-name/
        - Launcher.java
        - Main.java
      - resources/
        - package-name/
          - main.fxml
          - styles.css
  - test/
    - java/
      - package-name/
        - resources/
- .gitignore
- build.gradle
- settings.gradle
- gradlew
- gradlew.bat
- gradle/
  - wrapper/
    - gradle-wrapper.jar
    - gradle-wrapper.properties

## Steps

1. Verify that the current directory contains no existing project files. If files already exist, stop and report that this skill only supports fresh projects.
2. Create exactly the directory and file structure defined in "Filesystem Structure".
3. Create `settings.gradle` manually and set the root project name to the user-provided project name.
4. Create `build.gradle` manually with:
   - Java;
   - application;
   - JavaFX;
   - JUnit;
   - Checkstyle;
   - ShadowJar.
5. Generate the Gradle Wrapper without generating any additional application structure. Do NOT run `gradle init`
6. Configure the application entry point as: `<package-name>.Launcher`
7. Create only:
   - `Launcher.java`
   - `Main.java`
   under:
   `src/main/java/<package-name>/`
9. Create:
   - `main.fxml`
   - `styles.css`
   under:
   `src/main/resources/<package-name>/`
10. Create the Checkstyle configuration.
11. Configure Gradle Checkstyle tasks.
12. Create `.github/workflows/gradle.yml`.
13. Create the GitHub Pages files inside `docs/`.
14. Verify that the final directory matches the "Filesystem Structure" as defined above, if not, create any missing files as needed.

## Things to avoid
- Do not delete any existing files without explicit permission from the user.
- Do not create any unnecessary files that do not exist within the expected finished file structure without explicit permission from the user.
- Do not create all the files within a new directory, they should be created within the existing directory.
- Ensure that all dependencies specified within build.gradle are compatible with the project's Java and Gradle versions
- Do not create any of the following unless specified otherwise by the user:
  - app/
  - src/name/
  - src/*/java/com/
  - controller/
  - controllers/
  - model/
  - models/
  - service/
  - services/
  - repository/
  - repositories/
  - util/
  - utils/
  - module-info.java
  - .gitkeep
- Do NOT create a multi-project Gradle build.
- Do NOT place the application inside an app/ Gradle subproject.
- Do NOT introduce a com.<project-name> package automatically.
- Do NOT create empty architectural directories for possible future use.
