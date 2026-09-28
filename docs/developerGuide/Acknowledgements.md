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
- [Claude Code](https://claude.com/claude-code) (Anthropic): AI assistance for
  the listing, offer, sale, meetup, and chat services and screens, including
  design interviews, implementation, tests, reviews, merge-conflict
  resolution, these guides, and the PlantUML diagrams in `docs/diagrams/`.
  Each session is recorded in the repository's
  [logs](https://github.com/CS3227-2610-MP2-HotShop/CS3227-2610-MP2/tree/main/logs);
  all output was reviewed and adapted to the project's requirements.

### Frameworks and engineering resources

- [JavaFX/OpenJFX](https://openjfx.io/): the application's desktop UI toolkit;
  JavaFX controls, layouts, images, CSS, and application lifecycle are used
  throughout the UI. The libraries are bundled in the platform-specific JAR.

- [Matt Pocock's engineering skills](https://github.com/mattpocock/skills)
  (`mattpocock/skills`, all 25 pinned in `skills-lock.json`): agent
  configuration adapted from the installed `setup-matt-pocock-skills`
  templates in `.agents/skills/setup-matt-pocock-skills/`. The skills'
  instructions were used unmodified to run the development process for the
  listing, offer, sale, meetup, and chat features: `grilling` and
  `domain-modeling` for the design interviews recorded in the `*Design.md`
  documents and `CONTEXT.md`, `implement` and `tdd` for building each feature
  test first, `code-review` for the two-axis standards and specification
  reviews, and `resolving-merge-conflicts` for rebases. The skills shaped the
  process only; none of their text is part of the application.

- [OpenJFX Gradle plugin](https://github.com/openjfx/javafx-gradle-plugin): dependency configuration.
- [SE-EDU Java conventions](https://se-education.org/guides/conventions/java/intermediate.html):
  basis for the Checkstyle rules.
- [SE-EDU Git conventions](https://se-education.org/guides/conventions/git.html):
  commit message format used throughout the history.
- [JUnit 5](https://junit.org/junit5/) (5.13.4): the testing framework for every
  model, service, database, and UI test, including parameterised tests.
- [PlantUML](https://plantuml.com/) (1.2026.8): renders the listing, offer, sale,
  meetup, and chat diagrams from their `.puml` sources in `docs/diagrams/`; see
  "Diagrams" in [Development Workflow](DevelopmentWorkflow.html). It is a
  documentation tool only and is not part of the build.
- [Gradle](https://docs.gradle.org/9.1.0/release-notes.html): wrapper and Java 25 build support.
- [Shadow](https://gradleup.com/shadow/): executable dependency bundling.
- [Xerial SQLite JDBC](https://github.com/xerial/sqlite-jdbc): bundled SQLite driver.
- [OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
  and [Java security providers](https://docs.oracle.com/en/java/javase/25/security/oracle-providers.html):
  password-storage implementation guidance. The user-selected composition policy
  is a project requirement, not a claim of NIST compliance.
