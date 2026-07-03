# route-pattern-typed-vars learnings

- A jj-created workspace still needs git worktree metadata for sbt/git discovery in this repo. The minimal fix was to create the workspace root `.git` pointer plus the matching admin files under the main repo's `.git/worktrees/<workspace-name>/` directory.
- `SegmentCodec.PathVars` had to remain a separate phantom track from the existing runtime value type `A`; leaf segments can carry the named phantom marker directly, while the non-leaf composition cases stay neutral for this todo.
- The Scala 2/3 test split works best as separate files under `scala-2/` and `scala-3/`, with the shared backward-compat regression test left in the unsuffixed test source tree.
