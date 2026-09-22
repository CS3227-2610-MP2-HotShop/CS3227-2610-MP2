# Issue tracker: GitHub

Issues and specs live in CS3227-2610-MP2-HotShop/CS3227-2610-MP2.

Use the gh CLI. Always pass
--repo CS3227-2610-MP2-HotShop/CS3227-2610-MP2
to issue and PR commands because origin points to a personal fork.
For gh api, explicitly target the team repository in the endpoint.

## Conventions

The commands below require the --repo argument above.

- Create: gh issue create --title "..." --body-file <file>
- Read: gh issue view <number> --comments
- Read structured data: gh issue view <number> --json number,title,body,labels,comments
- List: gh issue list --state open --json number,title,body,labels,comments
- Comment: gh issue comment <number> --body-file <file>
- Add labels: gh issue edit <number> --add-label "..."
- Remove labels: gh issue edit <number> --remove-label "..."
- Close: gh issue close <number>

Use appropriate label and state filters. Put multiline bodies in a UTF-8
file and pass --body-file, preserving actual newlines.

When a skill says "publish to the issue tracker", create a GitHub issue.
When it says "fetch the relevant ticket", read the issue and its comments.

## Pull requests as a triage surface

**PRs as a request surface: no.**

GitHub issues and PRs share a number space. If a reference is ambiguous,
check gh pr view <number>, then gh issue view <number>.

## Wayfinding operations

- Map: one issue labelled wayfinder:map containing Notes, Decisions-so-far,
  and Fog.
- Child tickets: link as GitHub sub-issues. If unavailable, use a task list
  in the map and a Part of #<map> reference in each child.
- Ticket types: wayfinder:research, wayfinder:prototype, wayfinder:grilling,
  or wayfinder:task.
- Blocking: use native GitHub issue dependencies when available; otherwise
  record Blocked by: #<number> references in the ticket body.
- Frontier: select the first open child in map order with no open blockers
  and no assignee.
- Claim: assign the ticket to the driving developer.
- Resolve: comment with the result, close the ticket, and add a summary
  and link to the map's Decisions-so-far.
