---
  layout: default.md
  title: "Acknowledgements"
  pageNav: 3
---

## Acknowledgements

### AI assistance and local tooling

- [OpenAI Codex](https://openai.com/codex/): AI assistance with project scaffolding,
  implementation, tests, documentation, and review. The task records in
  [logs](../logs/) describe the scope and verification of individual interactions;
  generated output was adapted to this project's requirements.
- [`setup-javafx-project`](../.agents/skills/setup-javafx-project/SKILL.md):
  repository-local skill used to guide the initial JavaFX/Gradle scaffold, build
  configuration, CI, and guide structure, as recorded in the
  [scaffold log](../logs/2026-09-16-create-hotshop-javafx-scaffold.md). This link is
  the local source; no external author or upstream source is recorded here.

### Frameworks and engineering resources

- [JavaFX/OpenJFX](https://openjfx.io/): the application's desktop UI toolkit;
  JavaFX controls, layouts, images, CSS, and application lifecycle are used
  throughout the UI. The libraries are bundled in the platform-specific JAR.

- Matt Pocock's engineering skills: agent configuration adapted from the
  installed `setup-matt-pocock-skills` templates in
  `.agents/skills/setup-matt-pocock-skills/`.

- [OpenJFX Gradle plugin](https://github.com/openjfx/javafx-gradle-plugin): dependency configuration.
- [SE-EDU Java conventions](https://se-education.org/guides/conventions/java/intermediate.html):
  basis for the Checkstyle rules.
- [Gradle](https://docs.gradle.org/9.1.0/release-notes.html): wrapper and Java 25 build support.
- [Shadow](https://gradleup.com/shadow/): executable dependency bundling.
- [Xerial SQLite JDBC](https://github.com/xerial/sqlite-jdbc): bundled SQLite driver.
- [OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
  and [Java security providers](https://docs.oracle.com/en/java/javase/25/security/oracle-providers.html):
  password-storage implementation guidance. The user-selected composition policy
  is a project requirement, not a claim of NIST compliance.
