## Yeo Jian Ming (b2-4ac)

During the development of HotShop, I used an AI coding agent with several specialised skills rather than relying on one general prompt for every task. Each skill was designed for a different type of software engineering activity. This allowed the same agent to behave differently depending on whether I needed project setup, requirements analysis, implementation, or review.

Three particularly useful skills were `setup-javafx-project`, `grill-with-docs`, and the `to-spec` skill.

### `setup-javafx-project`: Automating Deterministic Project Setup

One of the first skills I used was `setup-javafx-project`. Its purpose was to create the initial JavaFX project structure and configure common infrastructure such as Gradle, JavaFX, JUnit, Checkstyle, ShadowJar, logging, and project documentation.

This was a suitable task for a skill because project setup involves many repetitive steps that should be performed consistently. Instead of repeatedly prompting the agent with requirements such as which directories to create or which Gradle plugins to configure, these expectations could be encoded once in the skill.

An important part of defining the skill was deciding which requirements belonged inside it. I included requirements that were specific to creating a new project, such as:

- creating the required source and documentation directories;
- configuring the Gradle build;
- adding testing and code-quality infrastructure;
- preparing documentation such as `UserGuide.md`, `DeveloperGuide.md`, and `Reflections.md`; and
- verifying that the generated project could build successfully.

The first version of this workflow also demonstrated why a skill must be precise. The agent initially relied on `gradle init` to create the project. Although this produced a valid Gradle project, it also generated an opinionated directory structure and files that were not exactly what I wanted for HotShop.

As a result, I had to refine the skill so that it described the required final repository structure, instead of simply instructing the agent to run a convenient project-generation command. This made the outcome more deterministic.

There were also times while I was testing the skill where the agent would do something I had explicitly told it not to do in the skill description. An example would be naming the source directories as `/src/main/java/com` even when I had laid out the full expected file structure in detail. I realised that this was because the agent would occasionally choose not to use the skill while I was trying to trigger the skill implicitly.

This was an example where the AI agent initially created additional work rather than reducing it. The output was technically reasonable, but not sufficiently aligned with the project's constraints. The problem was not that the agent could not create a Gradle project, but that the skill allowed too much freedom in deciding how to do so.

If I were designing this skill again, I would specify:

- the exact files and directories that must exist;
- files that must not be generated;
- the required Gradle plugins and versions;
- commands that must successfully run before the task is considered complete; and
- whether the skill may use project-generation utilities such as `gradle init`.

From this skill, I learned that deterministic tasks should be described in terms of **postconditions**. The agent should know what the repository must look like after the task, not only what steps it should attempt.

### `grill-with-docs`: Resolving Requirements Before Implementation

A second skill that became particularly useful was `grill-with-docs`. Instead of immediately asking the agent to implement a feature, I used this skill to examine a proposed feature and identify decisions that had not yet been specified.

Many times when wanting to plan out a feature for a project, it is quite hard to consider every single edge case and condense all of them into a singular prompt when working with an agent. This skill solves that problem by having the agent understand the basic implementation of the feature, and then interviewing the engineer on any gaps in understanding that the agent might have. This utilises the agent's thinking capabilities to form a more cohesive specification by querying the user, as opposed to a singular prompt.

This was especially useful when designing `AccountService`, because account management involved considerably more than simply storing usernames and passwords.

The design process required decisions about:

- whether registration should automatically log the user in;
- how many users could be authenticated in one application instance;
- whether usernames could be changed;
- what information should be visible through a public profile;
- how credentials should be separated from the shared `User` model;
- how passwords should be validated and stored securely;
- how profile images should be persisted;
- what should happen if filesystem operations fail after a database update; and
- which account operations should use the authenticated session instead of accepting an arbitrary user ID.

The `grill-with-docs` skill was effective because it moved these decisions before implementation. Instead of allowing the implementation agent to silently choose authentication, persistence, or privacy behaviour, these questions were explicitly discussed and recorded in the `AccountService` design.

For example, one important architectural decision was to keep credentials separate from the shared `User` model. Other services only require information such as the user's ID, display name, or profile image, and therefore should not gain access to authentication data.

Another decision was to distinguish the user's full private profile from the restricted `PublicProfile` representation. A user's preferred pickup location remains private, whereas other authenticated users may access only their ID, display name, and optional profile image.

The design process also exposed a consistency problem involving profile images. SQLite and the filesystem cannot participate in one atomic transaction. Replacing an image therefore required a staged strategy: import the new image, update the persisted reference, remove the new image if persistence fails, and retire the old image only after the database operation succeeds.

These details in implemetation are things that I might not have considered right off the bat, but were able to factor them into the design of the application after I had been asked to clarify by the agent. Hence this skill allowed for a more seamless design and implementation workflow, by splitting up planning and implementing steps.

### `to-spec`: Storing discussed topics for another session.

One of the things that I had run into after creating this application using an Agentic Workflow was the limit of token usage, as well as the limit of an agent's context window.

As described earlier, the agent was used to understand and clarify doubts about the specification and design of a feature before implementation could begin. While useful, this meant that the agent would sometimes spend a sizeable amount of tokens in the planning phase, leaving limited resources during the actual implementation of the code. In fact I had run into a few occasions where the agent was unable to complete its entire workflow due to me hitting my five hour limit on token usage.

A problem with the agent's context window also arose after long discussions where it had a lot of context to keep hold of, and had to compress its own context window in order to continue working.

The solution to both of these problems came in the form of the `to-spec` skill. This skill is intended to be used in conjunction with the `grill-with-docs` skill. Where it would instruct the agent to retrieve the final agreed upon specification of the feature and store it, either locally as a `.md` file or as an issue on the GitHub respository. Then, in a separate session, with a fresh context window and a full limit, we would be able to instruct the agent to read from the saved specification and implement the feature.

With a full context window and full tokens to use, it allowed me to ensure that the agent completed its full implementation workflow including code review. It also allowed me to be more confident that the agent was able to operate with the same level of understanding of the problem that it had before, without needing to compress its context window.

Hence, using this skill allowed me to ensure my agent would finish the task it was given without having to worry about any implications that might arise from having an agent pickup halfway after token limits refreshed.
