# Fix Coverage Gap in Markdown Module

## TL;DR

> **Quick Summary**: Add targeted tests to reach 95% statement coverage threshold in markdown module.
> 
> **Deliverables**:
> - Additional tests in AdtSpec.scala covering uncovered code paths
> - Coverage report showing 95%+ statement coverage
> 
> **Estimated Effort**: Quick (30 minutes)
> **Parallel Execution**: NO - sequential
> **Critical Path**: Task 1 (add tests) → Task 2 (verify coverage) → Task 3 (format & push)

---

## Context

### Current State
- PR #900 has all CI checks passing
- However, local coverage run shows 93.49% statement coverage
- The module requires 95% minimum coverage
- 6 specific code paths are not covered by tests

### Coverage Gaps Identified

From scoverage report, the following lines in `Doc.scala` are not covered:

1. **Line 72**: `equals` returning `false` for non-Doc objects
2. **Lines 85-87**: `toHtml`, `toHtmlFragment`, `toTerminal` instance methods
3. **Line 121**: `ListItem` case in `normalizeBlock`
4. **Lines 164-166**: `normalizeInlines` merging `Inline.Text` variants
5. **Lines 171-172**: `normalizeInlines` filtering empty `Inline.Text`
6. **Lines 209-210**: `isEmpty` for empty `Inline.Text`

---

## Work Objectives

### Core Objective
Add tests to cover all uncovered code paths and reach 95%+ statement coverage.

### Concrete Deliverables
- Updated `markdown/shared/src/test/scala/zio/blocks/markdown/AdtSpec.scala` with new tests

### Definition of Done
- [x] `sbt "project markdownJVM; coverage; test; coverageReport"` shows >= 95% statement coverage (95.08%)
- [x] All tests pass (462 tests)
- [x] Code is formatted

### Must NOT Have (Guardrails)
- NO changes to production code (only test code)
- NO unnecessary tests beyond what's needed for coverage
- NO complex test fixtures

---

## Verification Strategy

### Automated Verification
```bash
sbt "project markdownJVM; coverage; test; coverageReport"
# Assert: Statement coverage >= 95%
# Assert: All 450+ tests pass
```

---

## TODOs

- [x] 1. Add tests for uncovered Doc code paths

  **What to do**:
  Add the following tests to `markdown/shared/src/test/scala/zio/blocks/markdown/AdtSpec.scala` inside the `suite("Doc")` block, after the existing `suite("equality via normalization")`:

  ```scala
      suite("equality via normalization")(
        // ... existing tests ...
        test("equals returns false for non-Doc objects") {
          val doc = Doc(Chunk(Paragraph(Chunk(Text("Hello")))))
          assertTrue(
            doc != "a string",
            doc != 42,
            !doc.equals(null)
          )
        }
      ),
      suite("instance methods")(
        test("toHtml returns HTML via Doc instance method") {
          val doc = Doc(Chunk(Paragraph(Chunk(Text("Hello")))))
          assertTrue(doc.toHtml.contains("<p>Hello</p>"))
        },
        test("toHtmlFragment returns fragment via Doc instance method") {
          val doc = Doc(Chunk(Paragraph(Chunk(Text("Hello")))))
          assertTrue(
            doc.toHtmlFragment.contains("<p>Hello</p>"),
            !doc.toHtmlFragment.contains("<!DOCTYPE")
          )
        },
        test("toTerminal returns ANSI output via Doc instance method") {
          val doc = Doc(Chunk(Paragraph(Chunk(Text("Hello")))))
          assertTrue(doc.toTerminal.contains("Hello"))
        }
      ),
      suite("normalize edge cases")(
        test("normalizes ListItem blocks in BulletList") {
          val doc = Doc(
            Chunk(
              BulletList(
                Chunk(ListItem(Chunk(Paragraph(Chunk(Text("a"), Text("b")))), None)),
                tight = true
              )
            )
          )
          val normalized = doc.normalize
          val list       = normalized.blocks.head.asInstanceOf[BulletList]
          val item       = list.items.head
          val para       = item.content.head.asInstanceOf[Paragraph]
          assertTrue(para.content.size == 1, para.content.head == Text("ab"))
        },
        test("normalizes ListItem blocks in OrderedList") {
          val doc = Doc(
            Chunk(
              OrderedList(
                1,
                Chunk(ListItem(Chunk(Paragraph(Chunk(Text("x"), Text("y")))), Some(true))),
                tight = true
              )
            )
          )
          val normalized = doc.normalize
          val list       = normalized.blocks.head.asInstanceOf[OrderedList]
          val item       = list.items.head
          val para       = item.content.head.asInstanceOf[Paragraph]
          assertTrue(para.content.size == 1, para.content.head == Text("xy"))
        },
        test("normalizeInlines merges adjacent Inline.Text variants") {
          val inlines    = Chunk[Inline](Inline.Text("a"), Inline.Text("b"))
          val normalized = Doc.normalizeInlines(inlines)
          assertTrue(normalized.size == 1, normalized.head == Text("ab"))
        },
        test("normalizeInlines merges Text with Inline.Text") {
          val inlines    = Chunk[Inline](Text("a"), Inline.Text("b"))
          val normalized = Doc.normalizeInlines(inlines)
          assertTrue(normalized.size == 1, normalized.head == Text("ab"))
        },
        test("normalizeInlines merges Inline.Text with Text") {
          val inlines    = Chunk[Inline](Inline.Text("a"), Text("b"))
          val normalized = Doc.normalizeInlines(inlines)
          assertTrue(normalized.size == 1, normalized.head == Text("ab"))
        },
        test("normalizeInlines filters empty Inline.Text") {
          val inlines    = Chunk[Inline](Inline.Text("a"), Inline.Text(""), Text("b"))
          val normalized = Doc.normalizeInlines(inlines)
          assertTrue(normalized.size == 1, normalized.head == Text("ab"))
        },
        test("isEmpty returns true for paragraph with only empty Inline.Text") {
          val para = Paragraph(Chunk(Inline.Text("")))
          assertTrue(Doc.isEmpty(para))
        },
        test("normalizes Table row content") {
          val header = TableRow(Chunk(Chunk(Text("a"), Text("b"))))
          val row    = TableRow(Chunk(Chunk(Text("x"), Text("y"))))
          val doc    = Doc(Chunk(Table(header, Chunk(Alignment.Left), Chunk(row))))
          val normalized = doc.normalize
          val table      = normalized.blocks.head.asInstanceOf[Table]
          assertTrue(
            table.header.cells.head.head == Text("ab"),
            table.rows.head.cells.head.head == Text("xy")
          )
        }
      )
  ```

  **Must NOT do**:
  - Do not modify production code
  - Do not add tests for already-covered code

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`
  - **Reason**: Simple test additions, no complex logic

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential
  - **Blocks**: Task 2
  - **Blocked By**: None

  **References**:
  - `markdown/shared/src/test/scala/zio/blocks/markdown/AdtSpec.scala:316-332` - Existing equality tests (add new suites after this)
  - `markdown/shared/src/main/scala/zio/blocks/markdown/Doc.scala:68-87` - Code to cover (equals, toHtml, etc.)
  - `markdown/shared/src/main/scala/zio/blocks/markdown/Doc.scala:159-216` - Normalization code to cover

  **Acceptance Criteria**:
  - [x] Test file compiles: `sbt markdownJVM/compile`
  - [x] Tests pass: `sbt markdownJVM/test`

  **Commit**: NO (group with Task 2)

---

- [x] 2. Verify coverage reaches 95%

  **What to do**:
  Run coverage report and verify threshold is met.

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`

  **Parallelization**:
  - **Blocks**: Task 3
  - **Blocked By**: Task 1

  **Acceptance Criteria**:
  ```bash
  sbt "project markdownJVM; coverage; test; coverageReport"
  # Must show: Statement coverage >= 95.00%
  ```

  **Commit**: NO (group with Task 3)

---

- [x] 3. Format, commit, and push

  **What to do**:
  1. Format test code: `sbt "markdownJVM/Test/scalafmt"`
  2. Commit changes
  3. Push to origin

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `["git-master"]`

  **Parallelization**:
  - **Blocks**: None
  - **Blocked By**: Task 2

  **Acceptance Criteria**:
  - [x] `sbt markdownJVM/Test/scalafmtCheck` passes
  - [x] Changes committed and pushed to `docs` branch (commit 04ccf788)
  - [x] CI passes (all 12 checks green)

  **Commit**: YES
  - Message: `test(markdown): add coverage tests for Doc instance methods and normalization`
  - Files: `markdown/shared/src/test/scala/zio/blocks/markdown/AdtSpec.scala`

---

## Success Criteria

### Verification Commands
```bash
sbt "project markdownJVM; coverage; test; coverageReport"
# Expected: Statement coverage >= 95.00%

git push origin docs
# Expected: CI passes
```

### Final Checklist
- [x] Coverage >= 95% (95.08%)
- [x] All tests pass (462 tests)
- [x] Code formatted
- [x] CI passes
