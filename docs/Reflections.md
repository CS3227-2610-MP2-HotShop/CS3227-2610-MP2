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
