---
name: generate-report
description: Create a detailed, human-readable HTML report that documents the steps taken by an LLM for human analysis and testing.
---

# HTML Report Generation

## Description
The goal of this skill is to read a JSONL file that was created after an LLM had finished running, and generate a human-readable HTML report file, documenting the thought process and commands that were executed by the LLM for human analysis.

## Output Specification
- All .html files created using this skill should be stored within the `run-reports/html/` directory.
- The name of the final .html file should have the same name as the .jsonl file that was analysed.
  - Example:
    - `test-workflow.jsonl` would result in `test-workflow.html` being created

### Steps
1. Inspect the .jsonl file requested by the user
2. Create a HTML-based report that follows this structure:
    - What was the prompt provided by the user
    - What overall task was performed
    - Was the end goal of the prompt achieved
      - If the end goal was not achieved, what suggestions would you give the user to rectify the workflow
      - If the end goal was achieved, what suggestions would you give the user to improve the workflow (e.g. Implementing skills, writing clearer prompts etc.)
    - List out every action performed by the LLM, why it was performed, and whether is succeeded or failed
    - List the number of input tokens, cached input tokens, cache write input tokens, output tokens, reasoning output tokens consumed.
3. Check that a .css file exists within run-reports/ for the styling of .html report files. If it does not exist, create one. The style of the file should be dark-themed and modern.
