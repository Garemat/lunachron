---
name: pr-creation
description: Writes pull request description and raises the PR. Use when the user asks to create or open a PR.
---

When creating a PR:

1. Gather context by running these in parallel:
   - `git log main...HEAD --oneline` — understand what commits are included
   - `git diff main...HEAD` — understand what changed
   - Read `.github/pull_request_template.md` for the required format

2. Draft a PR title (under 70 chars, imperative tense) and body following the template exactly. All checklist items must be ticked (`[x]`) — items marked "N/A for now" in the template should still be checked.

3. Raise the PR:
```bash
gh pr create --title "<title>" --body "$(cat <<'EOF'
<body>
EOF
)"
```

4. After creation, share the PR URL with the user. Once CI checks pass, let them know the PR is ready to merge.
