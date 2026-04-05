# Why sbt prints `fatal: No names found, cannot describe anything`
```
sbt "schemaJVM/testOnly zio.blocks.schema.migration.*"
[info] welcome to sbt 1.12.8 (Debian Java 21.0.11-ea)
[info] loading settings for project zio-blocks-build-build from sbt-updates.sbt...
[info] loading project definition from /mnt/windows-e/Users/patri/Desktop/HACKERTHON/APP-TESTS/algora/zio-blocks/project/project
[info] loading settings for project root from build.sbt, plugins.sbt...
[info] loading project definition from /mnt/windows-e/Users/patri/Desktop/HACKERTHON/APP-TESTS/algora/zio-blocks/project
[info] loading settings for project root from build.sbt...
[info] resolving key references (73745 settings) ...
[error] fatal: No names found, cannot describe anything.
[info] set current project to root (in build file:/mnt/windows-e/Users/patri/Desktop/HACKERTHON/APP-TESTS/algora/zio-blocks/)
[info] compiling 1 Scala source to /mnt/windows-e/Users/patri/Desktop/HACKERTHON/APP-TESTS/algora/zio-blocks/chunk/jvm/target/scala-3.8.3/classes ...
[info] compiling 1 Scala source to /mnt/windows-e/Users/patri/Desktop/HACKERTHON/APP-TESTS/algora/zio-blocks/markdown/jvm/target/scala-3.8.3/classes ...
[info] compiling 1 Scala source to /mnt/windows-e/Users/patri/Desktop/HACKERTHON/APP-TESTS/algora/zio-blocks/typeid/jvm/target/scala-3.8.3/classes ...
[info] compiling 1 Scala source to /mnt/windows-e/Users/patri/Desktop/HACKERTHON/APP-TESTS/algora/zio-blocks/schema/jvm/target/scala-3.8.3/classes ...
[info] compiling 142 Scala sources to /mnt/windows-e/Users/patri/Desktop/HACKERTHON/APP-TESTS/algora/zio-blocks/schema/jvm/target/scala-3.8.3/test-classes ...
+ TrackedMigrationBuilder
  + build rejects when source or target remnants are non-empty - 153 ms
  + build after preserveField + addField - 489 ms
  + buildPartial allows incomplete field tracking - 489 ms
+ Migration
  + reverse is involutive on actions - 231 ms
  + composition associativity structure - 8 ms
  + identity round-trip - 473 ms
  + addField with literal default - 489 ms
  + dynamicMigration schema round-trip via DynamicValue - 605 ms
8 tests passed. 0 tests failed. 0 tests ignored.

Executed in 868 ms

[info] Completed tests
[success] Total time: 220 s (0:03:40.0), completed Apr 5, 2026, 1:16:26 AM
              
```
This line is **not** from your Scala code or a failed compile. It comes from **Git**, during **sbt project load**.

## What happens

1. The **zio-sbt-website** plugin (`dev.zio` / `zio-sbt-website` in `project/plugins.sbt`) runs logic that calls **`git describe`** (or equivalent) to derive a version or hash label for website/docs-related settings.
2. If the repository has **no tags** reachable from the current commit, `git describe` **exits with an error** and prints to **stderr**:
   `fatal: No names found, cannot describe anything`
3. sbt surfaces that stderr as `[error]`, but **project loading usually still succeeds**. It is noisy, not a build failure.

## How to get quieter logs

Create **any** tag on `HEAD` so `git describe` has a name, for example:

```bash
git tag v0.0.0-local
# optional: git tag -a v0.0.0-local -m "Local tag for git describe"
```

Reload sbt. You should see a normal describe output instead (or no fatal line).

**If you push to GitHub:** use a tag name that is clearly local or fork-only (e.g. `v0.0.0-local`), or avoid pushing tags until upstream agrees, so release tags on the main repo stay clean. I don't really have an issue with this but I just prefer my tests not having any types of errors, don't matter what it is.
