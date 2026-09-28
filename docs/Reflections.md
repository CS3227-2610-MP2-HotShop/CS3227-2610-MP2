# Reflections on AI-assisted development

## Jonathen Cheng (jonaturn)

I built HotShop's listing, offer, sale, meetup, and chat features, including the
five services behind them, the chat and meetup screens, and the database migration
system. I worked with Claude Code inside VS Code, guided by our `AGENTS.md` and
a set of engineering skills from `mattpocock/skills` (grilling,
domain-modeling, implement, tdd, and code-review). These are my reflections on
how that workflow went.

### Design interviews before code

Every feature started with a "grill me" session, where the AI asked me rounds of
numbered questions, each with a recommended answer, until every decision was
settled. The answers went into a design document and the project glossary
before any code was written.

This was the most valuable part of the workflow. It forced me to decide things I
would otherwise have left vague, such as whether a buyer can have two pending
offers on one listing, what happens to a meetup when its sale is cancelled, or
whether a seller can start a conversation. It also caught contradictions early,
when they were cheap to fix. Having the decisions written down meant later
sessions could pick up exactly where we left off, and my teammate could read why
something worked the way it did.

The one downside is time. Some interviews ran to five or six rounds, and
towards the end it was tempting to accept the recommended answers without
thinking. I learned to slow down on questions that changed what users would see.

### Building test first

With the design settled, the `implement` and `tdd` skills built each feature in
small steps. Each step wrote a test for one behaviour, then the code to pass it,
then ran the relevant tests before moving on. This made implementation feel simple, and
it made me more confident in the result. By the end the project had around 700
tests, and I noticed very few errors in behaviour I had asked for.

It was not perfectly disciplined. Sometimes the tests were written first but not
run on their own before the fix, so they were never seen failing. The logs are
honest about this, which I appreciated.

The other cost was speed. Our full test suite takes 13 to 17 minutes, mostly
because every test account hashes its password 600,000 times and the UI tests
open real windows. Early on the AI waited on these runs silently, and once I
interrupted to ask if it was stuck. After that it announced every long run and
how long it would take, which made the waits much easier to plan around.

### Reviewing on two axes

After each feature, the `code-review` skill ran two independent reviewers. One
checked our coding standards, and the other checked the code against the design
document. Keeping the two separate meant a standards problem could not hide a
behaviour problem, and the other way round.

These reviews found real bugs that I would probably have missed. They included
an error message that read "The seller already have a meetup", a tie between a
withdrawn offer and a new one made in the same millisecond, an upgrade that
would have flagged every old offer as unread, and a command in our Developer
Guide that failed in PowerShell. I also used the same process to review my
teammate's pull requests, which made my feedback more specific and less
personal.

### Logs, design documents, and project rules

`AGENTS.md` required a log for every task, a design document per feature, and
User and Developer Guide updates alongside the code. It felt heavy at first,
but it paid off. When I came back after a break, the logs told me what had been
decided and why. When a reviewer asked about a rule, I could point to the
interview where we agreed it.

### Branches, pull requests, and working with my teammate

This is where things went least smoothly, and most of it was down to our own
setup rather than the AI.

- **Stacked pull requests caused mistakes.** An offer pull request based on my
  listing branch was merged into that branch instead of into `main`, and it had
  to be carried over in a later pull request. After that I targeted `main`
  directly whenever I could.
- **We never set up an issue tracker or a status board** until late in the
  project. With several pull requests open at once, I lost track of what was
  merged, what was waiting on my teammate, and what was left to do. At one point
  I had to ask the AI to take stock of everything.
- **We had not agreed who owned which screens**, so my teammate built seller
  screens I had planned to build. We re-split the remaining work, but agreeing
  ownership up front would have avoided the overlap.

The pull request descriptions helped. Each one ended with a list of "shared
changes you should agree to", which kept our coordination explicit even when we
weren't talking.

### Checking the AI's work

The AI was right most of the time, but not always, and the times it was wrong it
sounded just as confident. Three moments stand out.

- **Editing listings with offers.** While designing offers, the AI proposed a
  rule for editing a listing with pending offers that did not match what we had
  already agreed for listings. I called it out ("I thought we agreed...") and we
  settled on one rule, that editing a listing rejects its pending offers.
- **Meetup times nobody books.** When the AI proposed that sellers publish
  general availability, I asked why there would ever be slots nobody booked.
  Answering that question showed the model didn't fit how I wanted meetups to
  work, and we changed to times the seller offers one buyer for one sale, which
  is simpler and safer.
- **Where meetups are arranged.** Later, when reviewing my teammate's screens,
  the AI suggested arranging meetups from the sale page. That was not my
  decision, because I had said meetups should be arranged in the chat. I corrected it
  before it went into the docs.

It also sometimes overstated things, for example claiming the course required a
particular diagram format when the course page only said what to do if you use
it. The lesson for me is to ask for evidence, such as a quote or a file and
line, whenever the AI makes a claim about requirements or about the code.

### What I would do differently

- **Agree who owns each screen and feature with my teammate before starting.**
- **Ask the AI for evidence** behind claims about requirements and existing
  behaviour, instead of taking them on trust.
- **Set up GitHub issues for each feature and a simple status board from day
  one**, and link every pull request to its issue.
- **Run several VS Code and Claude Code instances in parallel** on features that
  don't depend on each other, to make better use of the long test runs.

---

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
