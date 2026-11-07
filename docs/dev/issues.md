# ZIO Blocks: issues found while writing docs (index)

This list was split on 2026-10-29 into two files:

- **[`library-issues.md`](library-issues.md)**: library bugs, usability traps and library test/CI problems (flaky tests, test assertions, build and packaging), each with a reproducer, ready to file on GitHub. Most "a known bug in 0.0.56" notes in the docs and Scaladoc point here.
- **[`docs-issues.md`](docs-issues.md)**: documentation errors (pages, Scaladoc and examples that contradict the code) and docs-tooling problems (mdoc, the docs build, the example projects).

Each file has its own Resolved section. Scaladoc comments that say "see `docs/dev/issues.md`" refer to library behaviour, so look in `library-issues.md`.

Only the coordinating session edits these files. Writing sessions send it complete entries (see AGENTS.md, "Report every bug you find while writing docs").
