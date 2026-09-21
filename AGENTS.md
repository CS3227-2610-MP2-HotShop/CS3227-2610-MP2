# AGENTS.md

This file defines the repository-wide instructions that AI coding agents must follow when working on HotShop.

These instructions apply to all files in this repository unless a more specific instruction file exists for a particular directory.

---

## Project Overview

HotShop is a Java-based e-commerce application.

Users can interact with the application primarily as buyers or sellers.

Core functionality includes:

- Creating user accounts;
- Authenticating existing users;
- Creating listings for items that are offered for sale;
- Viewing available listings;
- Managing functionality associated with buyers and sellers.

Do not assume that a feature exists merely because it is described as part of the intended product. Inspect the current implementation before modifying or documenting functionality.

---

## Project Specifications
- Java version 25
- Gradle version 9.1.0
- Testing framework: JUnit 5

Always use the Gradle Wrapper when executing Gradle tasks:

```bash
./gradlew <task>
```

Do not assume that the system-installed Gradle version matches the project's required Gradle version.

Do not downgrade Java or Gradle versions to solve compatibility problems.

---

## Repository Structure
Respect the existing repository structure.

Application source code belongs under:
```
src/main/java/
```
Application resources belong under:
```
src/main/resources/
```
Tests belong under:
```
src/test/java/
```
Test resources bellong under:
```
src/test/resources/
```
Project documentation belongs under:
```
docs/
```
Agent interaction logs belong under:
```
logs/
```
Do not create new top-level directories unless they are required by the task or
explicitly requested by the user.

Do not reorganize existing packages or directories merely because another structure appears preferable.

---

## Before Making Changes
Before modifying the repository:
1. Read the user's request completely.
2. Inspect the files relevant to the requested change.
3. Inspect related tests.
4. Inspect relevant interfaces, models, controllers, services, or other
dependencies before modifying their behaviour.
5. Check existing conventions in nearby code before introducing a new pattern.
6. Determine the smallest reasonable set of files that must be changed.

Do not begin by creating new classes when existing classes may already provide the required functionality.

Prefer modifying or extending the existing design over introducing parallel implementations.

---

## Scope of Changes
Make only changes necessary to satisfy the user's requested task.

Do not:
- perform unrelated refactoring;
- rename unrelated classes or methods;
- reformat unrelated files;
- change public APIs unnecessarily;
- introduce speculative functionality;
- create abstractions solely for possible future requirements;
- delete existing functionality without explicit justification;
- modify unrelated tests merely to make a build pass.

If an unrelated problem is discovered, report it separately rather than fixing it as part of the current task unless it prevents completion of the requested work.

---

## Architecture and Design

Follow the architecture already established in the repository.

Before introducing a new architectural pattern, inspect how similar existing functionality is implemented.

Maintain clear separation of responsibilities.

In particular:
- UI code should primarily handle presentation and user interaction.
- Business rules should not be embedded unnecessarily in UI classes.
- Persistence concerns should remain separate from presentation concerns.
- Domain objects should represent application concepts and enforce appropriate domain invariants.
- Authentication and authorization checks should not depend solely on UI
visibility or navigation restrictions.

Avoid creating unnecessary layers such as additional managers, services,
repositories, factories, or utility classes unless they solve a concrete
design problem.

Prefer simple designs that satisfy the current requirements.

---

## Java Coding Standards
All Java code must follow the [SE-EDU Java Coding Standard](https://se-education.org/guides/conventions/java/intermediate.html)

The configured Checkstyle rules are the executable source of truth for
automatically enforceable style requirements.

When existing code demonstrates an additional consistent convention not covered by Checkstyle, follow the existing convention.

General Java Guidelines
- Use descriptive class, method, field, and variable names.
- Keep methods focused on a single responsibility.
- Avoid excessive method length and deeply nested control flow.
- Avoid duplicated logic.
- Prefer clear code over unnecessarily clever code.
- Avoid magic numbers and unexplained string literals where a named constant is appropriate.
- Use the narrowest appropriate visibility.
- Avoid mutable global state.
- Do not leave commented-out code.
- Do not leave debugging output such as temporary System.out.println
statements.
- Remove unused imports and variables.
- Handle exceptional conditions deliberately rather than silently ignoring them.

Do not suppress compiler or Checkstyle warnings simply to make verification pass.

---

## Dependencies

Before adding a new dependency:
1. Check whether the required functionality can reasonably be implemented using Java's standard library or an existing project dependency.
2. Verify compatibility with Java 25 and Gradle 9.1.0.
3. Prefer actively maintained and established libraries.
4. Add only the dependency modules actually required.

Do not introduce a dependency merely to avoid implementing trivial
functionality.

Do not remove or upgrade unrelated dependencies unless required by the task.

Any significant new dependency should be mentioned in the final task summary.

---

## Documentation

### User Documentation
All user-facing features added to this application must be documented in:
```
docs/UserGuide.md
```
Documentation for user-facing feature should include, where applicable:
- an explanation of the feature;
- instructions for using the feature;
- example use cases;
- expected outcomes;
- relevant restrictions;
- warnings or important behaviour the user should know.

Documentation must describe the actual current implementation.

Do not:
- document functionality that has not been implemented;
- describe planned behaviour as existing behaviour;
- invent commands, screens, buttons, error messages, or workflows;
- claim that functionality has been tested when it has not.

When an existing user-facing feature changes, update its existing User Guide documentation as part of the same task.

### Developer Documentation
Changes that materially affect architecture, design, dependencies, setup, development workflows, or major implementation decisions must also be reflected in:
```
docs/DeveloperGuide.md
```
Do not update the Developer Guide for trivial implementation details that do not affect developers' understanding of the system.

When external code, ideas, designs, or documentation are reused or adapted, ensure that any acknowledgement required by the project is recorded in the Developer Guide's acknowledgement section.

---

## Testing
All automated tests for this project will be written using JUnit5.

New behaviour should be accompanied by appropriate tests unless the behavious cannot reasonably be tested automatically.

Bug fixes should include a regression test when practical.

### Test Case Design
Functions requiring testing should have tests covering their relevant
equivalence partitions.

Equivalence partitions are not limited to explicit method parameters. Consider also:
- the state of the target object;
- relevant collaborating objects;
- persistent state;
- application state;
- global state accessed by the function.

Use Boundary Value Analysis (BVA) where boundaries exist.

### Positive and Negative Cases
Compatible positive conditions may be combined when doing so does not obscure which behaviour is being tested.

Test negative conditions separately when separate tests make failures easier to diagnose.

Do not combine unrelated failure conditions into one test.

### Test Naming
JUnit test methods should follow:
```
<functionName>_<inputOrState>_<expectedOutcome>()
```
Example:
```
execute_duplicatePerson_throwsCommandException()
```
Names should identify:
1. the function or operation under test;
2. the relevant input/state;
3. the expected result.

### Test Quality
Tests must:
- be deterministic;
- be independent where practical;
- test observable behaviour rather than implementation details;
- contain meaningful assertions;
- avoid depending on execution order;
- avoid external services unless explicitly testing an integration.

Do not modify a valid test simply because the implementation fails it.

Determine whether the implementation or the test is incorrect before making changes.

---

## Build and Verification
After modifying Java source code, run the relevant verification tasks.

At minimum:
```bash
./gradlew test
./gradlew checkstyleMain
./gradlew checkstyleTest
```
Before completing a substantial implementation task, run:
```bash
./gradlew check
./gradlew build
```
When changes afffect application packaging, also run:
```bash
./gradlew shadowJar
```
Do not claim that a command passed unless it was actually executed successfully.

If a verification command cannot be run because of an environment limitation, state this explicitly in the final response.

If verification fails:
- inspect the failure;
- determine its cause;
- fix failures caused by the current task;
- rerun the relevant verification.

Do not disable tests, Checkstyle, or other verification mechanisms merely to make the build succeed.

## Logging Agent Interactions
At the end of every workflow task performed by the LLM, create a new Markdown file under:
```
logs/
```
Do not overwrite previous task logs.

Use a descriptive filename that allows logs to be distinguished from one
another. Prefer:
```
YYYY-MM-DD-<short-task-description>.md
```
If multiple tasks with the same description occur on the same date, add a numeric suffix.

Each log must contain:
```
# Agent Interaction Log

## User Prompt

Record the user's original task prompt.

## Steps Taken

Summarize the significant steps performed by the LLM.

## Reasoning Summary

Explain the engineering rationale behind significant decisions.

Do not include hidden chain-of-thought or private internal reasoning.
Record concise, user-facing engineering justifications instead.

## Changes Made

List the important files created, modified, or deleted and explain why.

## Verification

Record the commands or checks actually performed and their outcomes.

## Final Output and Conclusion

Summarize the final result, including any unresolved issues.

```
Logs must accurately reflect what occured.

Do not claim that commands, tests, or manual checks were performed when they were not.

Do not include passwords, tokens, credentials, personal data, or other secrets in logs

---

## Handling Ambiguity
When requirements are ambiguous:
- inspect existing behaviour and documentation for evidence;
- prefer behaviour consistent with the existing system;
- avoid inventing new product requirements.

If multiple interpretations would result in materially different user-facing behaviour, data models, security properties, or architecture, ask the user for clarification before implementing the decision.

For minor implementation details that do not affect externally observable behaviour, use reasonable engineering judgement and follow existing project conventions.

---

## Definition of Done
A task is complete only when all applicable conditions below are satisfied:

- [ ] The requested functionality is implemented.
- [ ] Changes remain within the requested scope.
- [ ] Existing architecture and conventions have been respected.
- [ ] Relevant JUnit tests have been added or updated.
- [ ] Existing relevant tests still pass.
- [ ] Boundary and negative cases have been considered.
- [ ] Checkstyle passes for modified Java code.
- [ ] The project builds successfully.
- [ ] User-facing changes are reflected in docs/UserGuide.md.
- [ ] Material design/development changes are reflected in docs/DeveloperGuide.md.
- [ ] No credentials or sensitive information were introduced.
- [ ] No temporary/debugging code remains.
- [ ]The final diff has been inspected for unrelated changes.
- [ ]The agent interaction has been recorded under logs/.
- [ ]Any verification that could not be performed is clearly reported.

---

## Final Response
When completing a task, provide a concise summary containing:
- what was changed;
- important implementation or design decisions;
- tests added or modified;
- verification performed and its results;
- documentation updated;
- any limitations or unresolved issues.

Never report a test, build, Checkstyle check, application run, or other verification as successful unless it was actually executed successfully.
