# Learnings - markdown-pr-review

## Conventions
- Inline types are duplicated at package level AND in `object Inline` - must handle BOTH in pattern matches
- Follow existing ToMarkdown pattern: Scaladoc comment + single-line SAM syntax
- Tests extend `MarkdownBaseSpec`

## Patterns
- Renderer.scala lines 191-250 shows how to handle both `Text(v)` and `Inline.Text(v)` variants

## Block Rendering Implementation

- `Renderer.render(Doc(Chunk(block)))` converts a single Block to markdown string
- `.trim()` removes leading/trailing whitespace but preserves internal newlines
- BlockQuote renderer adds trailing `\n>` to output (empty line with quote marker)
- Test expectations must account for exact renderer behavior:
  - Paragraph: `"hello"` (no trailing content)
  - CodeBlock: includes backticks with newlines preserved
  - BlockQuote: includes internal newlines and trailing quote marker
  - Heading: renders with `# Title` format
  - ThematicBreak: renders as `---`

## Doc Methods Implementation

### ++  Operator
- Simple concatenation of blocks and metadata using Scala's `Map.++` operator
- Right metadata wins on key conflicts (standard Map behavior)
- Works with empty chunks and all block types

### toString Override
- Uses `Renderer.render(this)` to convert Doc to markdown
- Integrates seamlessly with existing Renderer infrastructure
- Produces GFM-compliant markdown output

### Normalization Algorithm
- **Text node merging**: Folds adjacent Text nodes regardless of variant (`Text` or `Inline.Text`)
- **Empty filtering**: Removes empty Text nodes ("") after merging
- **Recursive normalization**: Handles nested structures (Strong, Emphasis, BlockQuote, etc.)
- **isEmpty check**: Treats paragraphs with only empty Text nodes as removable
- Pattern matching must handle BOTH inline type variants in fold logic
- Key insight: Use `.lastOption` to check if previous element is Text before merging

### equals/hashCode
- Both based on normalized form to enable semantic equality
- Two docs with different structure but same rendering are equal
- hashCode is computed from `(normalized.blocks, normalized.metadata).hashCode()`
- Consistent with standard case class behavior

### Test Coverage
- All 12 new tests pass with one unified test suite under "Doc"
- Four sub-suites: "++ concatenation", "toString", "normalize", "equality via normalization"
- Tests verify both positive cases and edge cases (empty blocks, conflicts, nesting)

### Companion Object Helpers
- Split normalization into five public/private methods:
  - `normalizeBlocks`: Public - maps and filters blocks
  - `normalizeBlock`: Public - dispatches on Block type
  - `normalizeInlines`: Public - handles Text merging
  - `normalizeInline`: Public - recursive inline normalization
  - `isEmpty`: Public - identifies removable blocks
  - `normalizeListItem` (private): Helper for list items
  - `normalizeTableRow` (private): Helper for table rows

### Stubbing Methods
- `toHtml`, `toHtmlFragment`, `toTerminal` commented out with TODO markers
- Prevents compilation errors while waiting for HtmlRenderer and TerminalRenderer tasks
- Clear markers make it easy to uncomment after dependent tasks complete

## HtmlRenderer Implementation

### Design Decisions
- `render()` produces full HTML5 document with DOCTYPE, html, head, body tags
- `renderFragment()` produces just the content HTML for embedding
- All user-provided text content goes through `escape()` to prevent XSS
- HTML passthrough elements (HtmlBlock, HtmlInline) are NOT escaped - by design
- Table alignments use inline styles (`style="text-align:left"`) for maximum compatibility

### HTML Entity Escaping
- Order matters: `&` must be escaped FIRST to avoid double-escaping
- Escape sequence: `&` → `&amp;`, `<` → `&lt;`, `>` → `&gt;`, `"` → `&quot;`, `'` → `&#39;`
- Applied to: Text, Code content, Link URLs/titles, Image URLs/alt/titles, Autolink URLs
- NOT applied to: HtmlBlock, HtmlInline (passthrough by design)

### Block Type Rendering
- **Paragraph**: `<p>{content}</p>`
- **Heading**: `<h1>` through `<h6>` based on level.value
- **CodeBlock**: `<pre><code>` with optional `class="language-{lang}"` attribute
- **ThematicBreak**: `<hr>` (self-closing in HTML5)
- **BlockQuote**: `<blockquote>{nested blocks}</blockquote>`
- **Lists**: `<ul>` for BulletList, `<ol>` (with optional `start` attribute) for OrderedList
- **ListItem**: `<li>` with optional `<input type="checkbox">` for task lists
- **Table**: Full `<table><thead><tbody>` structure with alignment styles

### Inline Type Rendering
- All inline types require handling BOTH variants (package-level and `Inline.*`)
- **Text**: Escaped content only
- **Code**: `<code>{escaped content}</code>`
- **Emphasis**: `<em>` (semantic, not `<i>`)
- **Strong**: `<strong>` (semantic, not `<b>`)
- **Strikethrough**: `<del>` (semantic strikethrough)
- **Link**: `<a href>` with optional `title` attribute
- **Image**: `<img>` with `src`, `alt`, and optional `title`
- **SoftBreak**: Single space (` `) - collapses with other whitespace in HTML
- **HardBreak**: `<br>` - forces line break
- **Autolink**: `<a href>` for URLs, `<a href="mailto:">` for emails

### Table Rendering Specifics
- Header cells use `<th>` tags with alignment styles
- Data cells use `<td>` tags with alignment styles
- Alignment mapping:
  - `Alignment.Left` → `style="text-align:left"`
  - `Alignment.Right` → `style="text-align:right"`
  - `Alignment.Center` → `style="text-align:center"`
  - `Alignment.None` → no style attribute

### Test Coverage
- 59 comprehensive tests covering all Block and Inline types
- Tests for both package-level and `Inline.*` variants of inline types
- HTML entity escaping edge cases (nested entities, XSS attempts)
- Table rendering with all alignment types
- Empty document rendering
- Full document vs fragment rendering

### Known Issues
- Pre-existing test failure: "MdInterpolatorSpec / Compile-time validation / rejects List without ToMarkdown instance"
  - This test is now outdated because `List` DOES have a `ToMarkdown` instance (added in Task 2)
  - Not part of this task's scope - should be fixed separately
  - All HtmlRenderer tests pass (396 total passing tests include all 59 HtmlRenderer tests)

### Integration Points
- Doc.toHtml() → HtmlRenderer.render() - full document
- Doc.toHtmlFragment() → HtmlRenderer.renderFragment() - content only
- Both methods uncommented and working after HtmlRenderer implementation

## TerminalRenderer Implementation (2026-01-30)

### Pattern Matching Variable Shadowing
- **Issue**: ANSI constant `Strikethrough` shadowed the pattern match variable in `case Strikethrough(content)`
- **Solution**: Renamed constant to `StrikeStyle` to avoid naming conflict
- **Learning**: When creating constants that match case class names, use distinct names (e.g., `StrikeStyle`, `BoldStyle`) or use qualified access

### BlockQuote Rendering for Terminal
- **Challenge**: BlockQuote with Paragraph children produces "text\n\n", leading to extra empty lines
- **Solution**: Filter out empty strings after splitting: `.filterNot(_.isEmpty)`
- **Rationale**: Terminal output doesn't need to preserve empty quote lines like markdown does

### Code Organization
- Helper methods like `renderBlocks` should be used consistently
- Initially used `item.content.map(renderBlock).mkString` directly
- Changed to use `renderBlocks(item.content)` for consistency with HtmlRenderer

### Test Coverage
- All 11 Inline types tested with BOTH variants (package-level and object-nested)
- All 10 Block types tested
- Edge cases: empty documents, nested styles, all heading levels with colors
- Total: 60+ tests for TerminalRenderer

### ANSI Color Choices
Heading colors chosen for readability on both light and dark terminals:
- H1: Red (high contrast, attention-grabbing)
- H2: Yellow (stands out without being as bold as red)
- H3: Green (readable, distinct)
- H4: Cyan (good contrast)
- H5: Blue (readable)
- H6: Magenta (less common, clearly distinct)

### Implementation Time
- Initial implementation: ~10 minutes
- Debugging shadowing issue: ~5 minutes
- Fixing BlockQuote logic: ~5 minutes
- Total: ~20 minutes from start to passing tests
