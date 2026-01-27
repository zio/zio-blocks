# GitHub Issue Draft: Template Module

## Title

Add type-safe HTML/CSS/JS template DSL

## Labels

- `enhancement`
- `new module`

## Body

### Summary

Extract the `template2` type-safe DOM library from zio-http into zio-blocks. This provides a ScalaTags/Laminar-inspired DSL for building HTML, CSS, and JavaScript.

### Motivation

`template2` is a modern, composable template library that:
- Constructs type-safe HTML
- Supports CSS and JS with string interpolators
- Has no effect system dependencies
- Works cross-platform (JVM/JS/Native)

It currently lives in zio-http but the core functionality is HTTP-independent. Extracting it to zio-blocks enables:
- Use without zio-http dependency
- Server-side rendering in any Scala application
- HTML generation for emails, reports, documentation

### Example

```scala
import zio.blocks.template._

val page = html(
  head(
    title("My App"),
    css"""
      .container { max-width: 800px; margin: auto; }
      .btn { padding: 10px 20px; }
    """
  ),
  body(
    div(cls := "container",
      h1("Welcome"),
      p("This is a type-safe HTML template."),
      button(cls := "btn", onClick := "handleClick()", "Click Me")
    )
  )
)

page.render           // Pretty-printed HTML
page.renderMinified   // Minified HTML
```

### What to Extract

| Component | Description |
|-----------|-------------|
| `Dom` | Core DOM tree (Element, Text, Fragment, etc.) |
| `Modifier` | Attribute/child composition |
| `HtmlElements` | All HTML5 elements (div, span, table, etc.) |
| `HtmlAttributes` | Common attributes (class, id, href, etc.) |
| `Css` / `CssSelector` | CSS value types and selectors |
| `Js` | JavaScript value type |
| `css"..."` interpolator | Compile-time CSS |
| `js"..."` interpolator | Compile-time JS |

### Dependencies

- `zio.blocks.schema.Schema` - For optional codec support
- `zio.blocks.mediatype.MediaType` - For content type (requires #XX MediaType issue)

### What Stays in zio-http

- `HttpContentCodec[Dom]` integration
- Response body helpers
- HTTP-specific rendering utilities

zio-http will depend on zio-blocks-template for these integrations.

### Tasks

- [ ] Create `template` module with cross-platform setup
- [ ] Extract core DOM types (Dom.scala, Modifier.scala)
- [ ] Extract HTML elements and attributes
- [ ] Extract CSS/JS support
- [ ] Port Scala 2 macro interpolators
- [ ] Port Scala 3 macro interpolators
- [ ] Update imports to use zio-blocks Schema/MediaType
- [ ] Remove HTTP-specific code
- [ ] Add comprehensive tests
- [ ] Update zio-http to depend on zio-blocks-template (separate PR)

### Acceptance Criteria

- All HTML5 elements available
- Attributes work correctly
- CSS interpolator works
- JS interpolator works
- Renders to HTML string (pretty and minified)
- Cross-platform (JVM/JS/Native)
- Scala 2.12, 2.13, 3.x support
- No ZIO effect dependencies
- No zio-http dependencies

### Blocked By

- #XX MediaType module (should be merged first)

### Related

- zio-http template2: https://github.com/zio/zio-http/tree/main/zio-http/shared/src/main/scala/zio/http/template2
