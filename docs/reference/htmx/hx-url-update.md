---
id: hx-url-update
title: HxUrlUpdate
---

`HxUrlUpdate` represents the `hx-push-url` and `hx-replace-url` attributes, controlling whether and how the browser's URL bar updates after an HTMX request. You can enable/disable URL updates as booleans or specify a custom URL to push/replace.

Use `HxUrlUpdate.Enabled` or `HxUrlUpdate(true)` to update the URL with the response URL, or pass a custom URL string/Path/URL to update to a different address. Use `HxUrlUpdate.Disabled` or `HxUrlUpdate(false)` to prevent updates.

```scala
import zio.http.htmx._
import zio.http.Path

// Enable URL update (default)
HxUrlUpdate.Enabled

// Disable URL update
HxUrlUpdate.Disabled

// Custom URL string
HxUrlUpdate("/custom-url")

// Custom URL using Path
HxUrlUpdate(Path("/custom-url"))

// Boolean shorthand
HxUrlUpdate(true)
HxUrlUpdate(false)
```

## Strategies

**`HxUrlUpdate.Enabled`** updates the browser URL with the response URL. This is the default behavior when `hxPushUrl` or `hxReplaceUrl` are set:

```scala mdoc:compile-only
import zio.blocks.html._
import zio.http.htmx._

a(
  href := "/page-2",
  hxPushUrl := HxUrlUpdate.Enabled,
  "Page 2"
)
```

**`HxUrlUpdate.Disabled`** prevents the URL from updating, even though the page content changes:

```scala mdoc:compile-only
import zio.blocks.html._
import zio.http.htmx._

form(
  hxPost := "/search",
  hxPushUrl := HxUrlUpdate.Disabled,
  "Search (URL won't change)"
)
```

**`HxUrlUpdate(customUrl: String)`** updates the URL to a specific custom address instead of the response URL:

```scala mdoc:compile-only
import zio.blocks.html._
import zio.http.htmx._

form(
  hxPost := "/api/search-internal",
  hxPushUrl := HxUrlUpdate("/results"),
  "Search (URL becomes /results)"
)
```

**`HxUrlUpdate(path: Path)` or `HxUrlUpdate(url: URL)`** accepts typed paths and URLs, which are then encoded to strings:

```scala mdoc:compile-only
import zio.blocks.html._
import zio.http.htmx._
import zio.http.Path

form(
  hxPost := "/api/search",
  hxPushUrl := HxUrlUpdate(Path("/search-results")),
  button("Search")
)
```

## Boolean Constructors

Convenient overloads let you use booleans directly:

```scala mdoc:compile-only
import zio.blocks.html._
import zio.http.htmx._

form(
  hxPost := "/search",
  hxPushUrl := HxUrlUpdate(true),  // same as HxUrlUpdate.Enabled
  button("Search")
)

button(
  hxPost := "/action",
  hxPushUrl := HxUrlUpdate(false), // same as HxUrlUpdate.Disabled
  "Action (URL unchanged)"
)
```

## Rendering & Parsing

**`render: String`** produces the literal attribute value string:

```scala mdoc:compile-only
import zio.http.htmx._

HxUrlUpdate.Enabled.render                    // "true"
HxUrlUpdate.Disabled.render                   // "false"
HxUrlUpdate("/results").render                // "/results"
```

**`HxUrlUpdate.parse(value: String): Either[String, HxUrlUpdate]`** parses a rendered string back into a typed `HxUrlUpdate`:

```scala mdoc:compile-only
import zio.http.htmx._

HxUrlUpdate.parse("true")                     // Right(HxUrlUpdate.Enabled)
HxUrlUpdate.parse("false")                    // Right(HxUrlUpdate.Disabled)
HxUrlUpdate.parse("/custom")                  // Right(HxUrlUpdate("/custom"))
```

## Common Patterns

### Progressive Enhancement with URL Updates

Keep the URL in sync with SPA-like navigation while using server-side rendering:

```scala mdoc:compile-only
import zio.blocks.html._
import zio.http.htmx._

nav(
  a(
    href := "/users",
    hxPushUrl := HxUrlUpdate.Enabled,
    hxTarget := HxTarget.css("#content"),
    "Users"
  ),
  a(
    href := "/settings",
    hxPushUrl := HxUrlUpdate.Enabled,
    hxTarget := HxTarget.css("#content"),
    "Settings"
  )
)
```

### API-Only Requests Without URL Change

Make internal API calls that don't affect the URL:

```scala mdoc:compile-only
import zio.blocks.html._
import zio.http.htmx._

button(
  hxPost := "/api/save-draft",
  hxPushUrl := HxUrlUpdate.Disabled,
  "Save Draft"
)
```

### Redirect to a Different URL

Use an internal API endpoint but show a different user-friendly URL:

```scala mdoc:compile-only
import zio.blocks.html._
import zio.http.htmx._

form(
  hxPost := "/api/checkout-internal",
  hxPushUrl := HxUrlUpdate("/order-confirmation"),
  hxTarget := HxTarget.css("#main"),
  button("Complete Order")
)
```

### Search with Query Parameter URL

Push a URL with query parameters to reflect the search state:

```scala mdoc:compile-only
import zio.blocks.html._
import zio.http.htmx._

input(
  name := "query",
  hxPost := "/search",
  hxPushUrl := HxUrlUpdate("/search?query=..."),  // placeholder; dynamically set by server
  placeholder := "Search..."
)
```

## Replace vs Push

Use `hxReplaceUrl` instead of `hxPushUrl` to replace the current history entry rather than adding a new one:

```scala mdoc:compile-only
import zio.blocks.html._
import zio.http.htmx._

// This request replaces the current history entry
form(
  hxPost := "/search",
  hxReplaceUrl := HxUrlUpdate("/search-results"),
  button("Search")
)
```

Both attributes accept the same `HxUrlUpdate` type. Use `replace` for searches, filters, and state changes that shouldn't create browser history entries. Use `push` for navigation to new pages.

## Integration with Other Attributes

`HxUrlUpdate` is used with `hxPushUrl` and `hxReplaceUrl`, both of which control history and URL bar updates:

```scala mdoc:compile-only
import zio.blocks.html._
import zio.http.htmx._

form(
  hxPost := "/api/search",
  hxTarget := HxTarget.css("#results"),
  hxSwap := HxSwap.InnerHTML,
  hxReplaceUrl := HxUrlUpdate("/search?q=example"),
  input(placeholder := "Search...")
)
```

The `ToHtmxValue[HxUrlUpdate]` instance renders automatically, so `HxUrlUpdate` values work seamlessly with both attribute keys.
