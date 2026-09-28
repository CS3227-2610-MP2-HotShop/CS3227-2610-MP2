---
  layout: default.md
  title: "Development Workflow"
  pageNav: 3
---

## Setup

Use JDK 25 and the included Gradle 9.1.0 Wrapper. Set JAVA_HOME to your JDK if
necessary. Import the root directory as a Gradle project in your IDE.

```powershell
.\gradlew.bat run
.\gradlew.bat test checkstyleMain checkstyleTest
.\gradlew.bat check build shadowJar
```

Use `./gradlew` on macOS/Linux. Initial dependency resolution requires network access.

### Diagrams

The seven diagrams of listings, offers, sales, meetups, and chat (the
`*_uml.puml` files) are PlantUML sources in `docs/diagrams/`, committed next to
the PNGs the guide shows. After editing a source, regenerate its PNG with the
PlantUML jar (1.2026.8, from the
[PlantUML releases](https://github.com/plantuml/plantuml/releases)) placed in the
git-ignored `tools/` folder:

```powershell
java -jar tools\plantuml.jar -tpng -charset UTF-8 docs\diagrams\sale_models_uml.puml
```

The class and state diagrams use PlantUML's built-in Smetana layout, so Graphviz
is not needed. Commit the `.puml` and the regenerated `.png` together.

## Dependencies and checks

### Libraries and build configuration

JavaFX 25.0.2 uses controls and FXML through OpenJFX Gradle plugin 0.1.0.
JUnit Jupiter 5.13.4 is configured with the JUnit Platform launcher.
Checkstyle 12.3.1 enforces mechanical SE-EDU conventions; semantic naming and
clarity still require review. Shadow 9.2.2 bundles runtime dependencies.
Native access is enabled in Gradle launch scripts and the JAR manifest for JavaFX.

### Targeted model checks

Model tests cover validation boundaries, lifecycle transitions, immutable
snapshots, and cancellation permissions/history. For a targeted run, use
`.\gradlew.bat test --tests hotshop.model.TransactionTest`.

## Packaging and CI

### Building the distribution

`shadowJar` writes `release/HotShop.jar`. Build separately for each target OS and
architecture because JavaFX native libraries are platform-specific.
The JAR requires a separately installed Java 25 runtime.

### Continuous integration

GitHub Actions runs tests, Checkstyle, check, build, and shadowJar on pull
requests and pushes to main/master, and uploads a Linux JAR artifact.
The workflow also supports manual dispatch.

## GitHub Pages

The guides are a [MarkBind](https://markbind.org/) site. Every push to `main`
runs `.github/workflows/docs.yml`, which builds the site with MarkBind and
deploys `_site/` to GitHub Pages, at
<https://cs3227-2610-mp2-hotshop.github.io/CS3227-2610-MP2/>. The repository's
Pages source is set to **GitHub Actions**.

To preview the site locally, install Node.js and run:

```powershell
npm ci
npm run docs:serve
```

`npm run docs:build` writes the site to the git-ignored `_site/` folder instead.
The site is served from the repository's subpath, so `baseUrl` in `site.json`
must stay `/CS3227-2610-MP2`. Links to files outside `docs/`, such as `logs/`,
must be full GitHub links, because only `docs/` pages are published.

## Engineering skills

### Agent configuration

Agent skill configuration lives in [docs/agents](https://github.com/CS3227-2610-MP2-HotShop/CS3227-2610-MP2/tree/main/docs/agents). It defines the
[team GitHub issue tracker](../agents/issue-tracker.md),
[triage labels](../agents/triage-labels.md), and
[domain documentation rules](../agents/domain.md). `AGENTS.md` directs agents
to read these files when needed.

Edit these configuration files directly to adjust the workflow. Re-run
`setup-matt-pocock-skills` when switching trackers or restarting setup.
Domain documentation uses a root `CONTEXT.md` and `docs/adr/`, created by
`domain-modeling` as terminology and decisions are resolved.

### Sharing skills between coding agents

The skills are installed once in `.agents/skills/`, where Codex reads them.
Claude Code reads `.claude/skills/` instead, so link that path to the same
folder rather than copying it. On Windows (no administrator rights needed):

```powershell
New-Item -ItemType Directory -Force .claude
cmd /c mklink /J .claude\skills .agents\skills
Add-Content .git\info\exclude ".claude/skills"
```

On macOS/Linux, use `ln -s ../.agents/skills .claude/skills`. The link is
excluded locally rather than committed because the repository does not enable
Git symlinks. Restart Claude Code if the skills do not appear.

