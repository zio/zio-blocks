# How-To Guide Review Checklist

After writing, verify every item on this checklist:

## Content Quality

- [ ] The guide has a clear, stated goal in the introduction
- [ ] The guide has a "Problem" section that concretely states what problem is being solved
- [ ] The problem section explains why the problem matters (real consequences)
- [ ] The problem section includes an example of the pain (code or concrete scenario)
- [ ] A reader who follows every step will have a working result at the end
- [ ] Every section introduces exactly one new concept or capability
- [ ] No section is pure prose without a code example
- [ ] The running example is realistic and relatable
- [ ] Types and APIs are introduced only when needed (no front-loaded theory dumps)
- [ ] The "Putting It Together" section is a complete, self-contained, copy-paste-ready example

## Technical Accuracy

- [ ] All method signatures and type names match the actual source code
- [ ] All code examples use correct mdoc modifiers and would compile
- [ ] Imports are complete and correct in every code block
- [ ] The sbt dependency in Prerequisites is correct
- [ ] No deprecated methods or outdated patterns are used
- [ ] Run `sbt "docs/mdoc --in docs/guides/<guide-id>.md"` and confirm zero `[error]` lines (this is mandatory before claiming the guide is done)

## Companion Examples

- [ ] A package directory exists in `schema-examples/src/main/scala/<packagename>/`
- [ ] There is one example file per major guide step (typically 3-5 files)
- [ ] There is a `CompleteExample.scala` (or descriptively named equivalent) with the full "Putting It Together" code
- [ ] Each example file is fully self-contained (compiles and runs independently)
- [ ] Each example file has complete imports
- [ ] Each example file has a scaladoc with guide title, step description, and `sbt runMain` command
- [ ] Each example file includes `println` output showing meaningful results
- [ ] All examples compile successfully (`sbt "schema-examples/compile"`)

## Running the Examples Section

- [ ] The guide includes a "Running the Examples" section after "Putting It Together"
- [ ] The section includes `git clone https://github.com/zio/zio-blocks.git` and `cd zio-blocks`
- [ ] Every companion example file is listed with its `sbt "schema-examples/runMain ..."` command
- [ ] The section includes `sbt "schema-examples/compile"` as an alternative

## Style and Integration

- [ ] The frontmatter `id` matches the filename
- [ ] The guide is added to `sidebars.js`
- [ ] The guide is linked from `docs/index.md`
- [ ] Related reference pages link back to this guide
- [ ] Writing style follows the rules (present tense, "we"/"you", concise, no emojis)
- [ ] Admonitions are used sparingly and for genuinely important callouts
