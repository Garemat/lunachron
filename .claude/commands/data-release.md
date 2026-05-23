# Data Release Bump

Bump `lunachron/` to reference a new `lunachron-data` release. Handles both data pins:
- `data.version` — release tag used by Gradle and runtime fetching
- `data/` submodule — commit SHA used by F-Droid source builds

## Steps

1. **Get the target tag**: if $ARGUMENTS is provided use it directly, otherwise fetch the latest release from lunachron-data:
   ```bash
   gh release list -R Garemat/lunachron-data --limit 1 --json tagName --jq '.[0].tagName'
   ```

2. **Sync main and create a worktree**:
   ```bash
   git sync
   # EnterWorktree with branch name: chore/data-<tag>  (e.g. chore/data-v0.5.0)
   ```

3. **Update `data.version`**: write the tag (e.g. `v0.5.0`) as the sole content of the file.

4. **Update the submodule** (worktrees don't auto-init submodules):
   ```bash
   git submodule update --init --quiet
   git -C data fetch --quiet --tags
   git -C data checkout --quiet <tag>
   git add data
   ```

5. **Commit**:
   ```bash
   git add data.version
   git commit -m "chore: bump lunachron-data to <tag>"
   ```
   Post-commit hook pushes automatically.

6. **Raise a PR**:
   ```bash
   gh pr create --title "chore: bump lunachron-data to <tag>" --body "..."
   ```
   PR body should note the tag, link to the lunachron-data release, and list any notable data changes if known.

7. **Enable auto-merge**:
   ```bash
   gh pr merge --auto --squash
   ```
