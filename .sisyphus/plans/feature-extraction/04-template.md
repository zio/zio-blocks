# PR 4: Template Module

## Summary

Extract the `template2` type-safe HTML/CSS/JS DSL from zio-http into zio-blocks.

## What Template2 Is

A modern, type-safe DOM library inspired by ScalaTags and Laminar.

```scala
import zio.blocks.template._

val page = html(
  head(
    title("My Page"),
    css"""
      body { font-family: sans-serif; }
    """
  ),
  body(
    h1("Hello World"),
    p(cls := "intro", "Welcome to zio-blocks"),
    button(onClick := "alert('Hi!')", "Click me")
  )
)

page.render // → HTML string
```

## Key Features

- **Type-safe HTML construction** - Elements, attributes, children
- **Composable modifiers** - Attributes and children as unified concept
- **CSS DSL** - Type-safe CSS with selectors
- **JS DSL** - JavaScript string interpolation
- **Transform/filter/collect** - DOM tree operations
- **Multiple render modes** - Pretty-printed, minified

## Source Files

| File | Purpose |
|------|---------|
| `Dom.scala` | Core tree structure |
| `Modifier.scala` | Attribute/child composition |
| `HtmlElements.scala` | Element definitions (div, span, etc.) |
| `HtmlAttributes.scala` | Attribute definitions (class, id, etc.) |
| `Css.scala` | CSS value type |
| `CssSelector.scala` | CSS selector DSL |
| `Js.scala` | JavaScript value type |
| `CssInterpolator.scala` | `css"..."` interpolator (Scala 2/3) |
| `JsInterpolator.scala` | `js"..."` interpolator (Scala 2/3) |
| `package.scala` | Convenience imports |

Source location: `zio-http/zio-http/shared/src/main/scala/zio/http/template2/`

## Target Location

```
zio-blocks/
├── template/
│   └── shared/
│       └── src/
│           ├── main/
│           │   ├── scala/
│           │   │   └── zio/blocks/template/
│           │   │       ├── Dom.scala
│           │   │       ├── Modifier.scala
│           │   │       ├── HtmlElements.scala
│           │   │       ├── HtmlAttributes.scala
│           │   │       ├── Css.scala
│           │   │       ├── CssSelector.scala
│           │   │       ├── Js.scala
│           │   │       └── package.scala
│           │   ├── scala-2/
│           │   │   └── zio/blocks/template/
│           │   │       ├── CssInterpolator.scala
│           │   │       ├── CssInterpolatorMacros.scala
│           │   │       ├── JsInterpolator.scala
│           │   │       └── JsInterpolatorMacros.scala
│           │   └── scala-3/
│           │       └── zio/blocks/template/
│           │           ├── CssInterpolator.scala
│           │           ├── CssInterpolatorMacros.scala
│           │           ├── JsInterpolator.scala
│           │           └── JsInterpolatorMacros.scala
│           └── test/
│               └── scala/
│                   └── zio/blocks/template/
│                       └── ...
```

## Dependencies

| Current Dependency | Resolution |
|-------------------|------------|
| `zio.schema.Schema` | Use `zio.blocks.schema.Schema` ✅ |
| `zio.http.MediaType` | Use `zio.blocks.mediatype.MediaType` (PR 2) |
| `zio.http.codec.HttpContentCodec` | **Remove** - not needed for core template |
| `zio.http.codec.BinaryCodecWithSchema` | **Remove** - HTTP-specific |
| `zio.http.codec.TextBinaryCodec` | **Remove** - HTTP-specific |

## Required Changes

### Core Files (Dom.scala, HtmlElements.scala, etc.)
1. Change package: `zio.http.template2` → `zio.blocks.template`
2. Replace Schema import with zio-blocks schema
3. Replace MediaType import with zio-blocks mediatype
4. Remove HttpContentCodec integration (stays in zio-http)

### Interpolator Macros
1. Update package references
2. Scala 2: Update macro implementations
3. Scala 3: Update inline/macro implementations
4. Keep both Scala 2 and Scala 3 versions

### Package Object
1. Simplify - remove HTTP-specific implicits
2. Keep DOM-related implicits

## What Stays in zio-http

- `HttpContentCodec` integration for Dom
- Response body helpers (`Dom.toBody`, etc.)
- Any HTTP-specific rendering

zio-http can depend on zio-blocks-template and add these integrations.

## Build Configuration

```scala
lazy val template = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .in(file("template"))
  .settings(stdSettings("zio-blocks-template"))
  .settings(
    libraryDependencies ++= Seq(
      // Scala 2 macro dependencies
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
    ).filterNot(_ => scalaVersion.value.startsWith("3"))
  )
  .dependsOn(schema, mediatype)
```

## Testing

- Element construction tests
- Attribute handling tests
- CSS/JS interpolator tests
- Render output tests (pretty, minified)
- Cross-platform tests (JVM/JS/Native)

## Estimated Effort

**Medium** - Multiple files, macro code, Scala 2/3 split.

- Core extraction: 4-8 hours
- Macro adaptation: 4-8 hours  
- Testing: 4 hours
- Total: 2-3 days

## Acceptance Criteria

- [ ] All HTML elements available
- [ ] All common attributes available
- [ ] CSS interpolator works (`css"..."`)
- [ ] JS interpolator works (`js"..."`)
- [ ] DOM renders to HTML string
- [ ] Cross-platform build works (JVM/JS/Native)
- [ ] Scala 2.12, 2.13, 3.x all work
- [ ] No ZIO effect dependencies
- [ ] No zio-http dependencies

## Future Enhancements

- Server-side rendering utilities
- DOM diffing for updates
- Additional HTML5 elements/attributes
- ARIA accessibility attributes
