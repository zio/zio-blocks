# ZIO HTTP Template2 - Design Analysis & Improvement Proposals

## TL;DR

> **Current State**: Template2 is a well-architected, type-safe HTML/CSS/JS DSL for Scala with compile-time validation. It features a sealed DOM ADT, modifier-based composition, and basic string interpolators for CSS/JS.
>
> **Key Strengths**: Clean architecture, XSS protection via automatic escaping, Schema/Codec integration, CSS selector DSL
>
> **Improvement Opportunities**: Enhanced interpolators, performance optimizations, missing HTML interpolator, context-aware escaping

---

## Part 1: Current State Analysis

### 1.1 Architecture Overview

Template2 is located in `zio-http/shared/src/main/scala/zio/http/template2/` with Scala 2/3 split for macro implementations.

```
template2/
├── Dom.scala              # Core DOM ADT (764 lines)
├── HtmlElements.scala     # HTML elements + attributes (259 lines)
├── CssSelector.scala      # CSS selector DSL (204 lines)
├── Css.scala              # CSS value wrapper
├── Js.scala               # JS value wrapper
├── package.scala          # Package object with implicits
├── scala-2/
│   ├── CssInterpolator.scala
│   ├── CssInterpolatorMacros.scala
│   ├── JsInterpolator.scala
│   └── JSInterpolatorMacros.scala
└── scala-3/
    ├── CssInterpolator.scala
    ├── CssInterpolatorMacros.scala
    ├── JsInterpolator.scala
    └── JsInterpolatorMacros.scala
```

### 1.2 Core DOM ADT

The `Dom` sealed trait is the foundation:

```scala
sealed trait Dom extends Modifier with Product with Serializable {
  def render: String
  def render(indentation: Boolean): String
  def renderMinified: String
  def collect(predicate: PartialFunction[Dom, Dom]): List[Dom]
  def filter(predicate: Dom => Boolean): Dom
  def find(predicate: Dom => Boolean): Option[Dom]
  def transform(f: Dom => Dom): Dom
}
```

**Variants:**
| Type | Description | Escaping |
|------|-------------|----------|
| `Element.Generic` | Standard HTML elements | Content escaped |
| `Element.Script` | `<script>` element | Content NOT escaped |
| `Element.Style` | `<style>` element | Content NOT escaped |
| `Text` | Text content | HTML-escaped on render |
| `RawHtml` | Raw HTML string | NOT escaped |
| `Fragment` | Multiple siblings without wrapper | N/A |
| `Empty` | No output | N/A |

**Key Design Decisions:**
1. **Sealed ADT** - Exhaustive pattern matching, compile-time safety
2. **Modifier Trait** - Unifies attributes and children for composition
3. **Script/Style Specialization** - Distinct types prevent escaping issues
4. **Immutable with copy()** - Thread-safe, functional style

### 1.3 Element/Attribute System

**Element Application:**
```scala
div(id := "main", className := "container", "Hello World")
// Mixes attributes and children freely via Modifier trait
```

**Attribute Types:**
| Type | Example | Rendering |
|------|---------|-----------|
| `PartialAttribute` | `id := "main"` | `id="main"` |
| `BooleanAttribute` | `required` | `required` (no value) |
| `PartialMultiAttribute` | `className := ("a", "b")` | `class="a b"` |
| `CompleteAttribute` | Result of `:=` | Final attribute |

**Attribute Values:**
```scala
sealed trait AttributeValue
├── StringValue(value: String)
├── MultiValue(values: Vector[String], separator: AttributeSeparator)
├── BooleanValue (True/False objects)
└── JsValue(js: Js)
```

### 1.4 CSS Selector DSL

A type-safe CSS selector builder:

```scala
sealed trait CssSelector extends CssSelectable {
  def render: String
}

// Combinators via operators
div > p         // Child: "div > p"
div >> p        // Descendant: "div p"
div + p         // Adjacent sibling: "div + p"
div ~ p         // General sibling: "div ~ p"
div & .active   // And (concatenation): "div.active"
div | p         // Or (grouping): "div, p"

// Pseudo-classes/elements
div.hover       // "div:hover"
div.firstChild  // "div:first-child"
div.before      // "div::before"
div.nthChild(2) // "div:nth-child(2)"
```

### 1.5 String Interpolators

**CSS Interpolator:**
```scala
val color = "red"
css"color: $color;"  // Css("color: red;")
selector".my-class"  // CssSelector.raw(".my-class")
```

**JS Interpolator:**
```scala
val count = 42
js"console.log($count);"  // Js("console.log(42);")
```

**Macro Validation (Scala 3):**
- Simple patterns without interpolation are validated at compile time
- Regex-based validation for basic syntax checking
- Invalid syntax produces compile error with message

**Current Limitations:**
1. **Validation is basic** - Only regex patterns, no real parsing
2. **No context-aware escaping** - Interpolated values just `.toString`
3. **No type constraints** - Any type accepted via `Any*`
4. **No HTML interpolator** - Missing the most useful case!

### 1.6 Integration Points

**Schema Integration:**
```scala
implicit val schema: Schema[Dom] = 
  Schema[String].transform(raw, _.render)
```

**HTTP Content Codec:**
```scala
implicit val htmlCodec: HttpContentCodec[Dom] = 
  HttpContentCodec(ListMap(
    MediaType.text.`html` -> BinaryCodecWithSchema.fromBinaryCodec(...)
  ))
```

**Response Helper:**
```scala
// In Response.scala
def html(data: zio.http.template2.Dom): Response
def html(data: zio.http.template2.Dom, status: Status): Response
```

### 1.7 Security Model

**XSS Protection:**
| Context | Behavior |
|---------|----------|
| Text in normal elements | HTML-escaped via `htmlEscape()` |
| Attribute values | HTML-escaped |
| Script/Style content | NOT escaped (intentional) |
| `RawHtml` | NOT escaped (explicit bypass) |

**Escape Function:**
```scala
private def htmlEscape(text: String): String = {
  text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#x27;")
}
```

**Limitation:** No context-aware escaping (e.g., JS context, URL context, CSS value context).

---

## Part 2: Improvement Proposals

### 2.1 HTML String Interpolator (HIGH PRIORITY)

**The Problem:**
There's no `html""` interpolator - the most obvious use case is missing! Users must use the DSL for everything.

**Proposal:**

```scala
val userName = "<script>evil</script>"
val items = List("Apple", "Banana")

html"""
  <div class="container">
    <h1>Hello, $userName!</h1>
    <ul>
      ${items.map(i => html"<li>$i</li>")}
    </ul>
  </div>
"""
// userName is HTML-escaped automatically
// Nested html"" fragments compose safely
```

**Key Features:**
1. **Automatic XSS escaping** for interpolated String values
2. **Type-safe composition** - `Dom` values pass through unchanged
3. **Compile-time HTML parsing** for structure validation
4. **Context-aware escaping** (see 2.2)

**Implementation Approach:**
```scala
// Scala 3
extension(inline sc: StringContext) {
  inline def html(inline args: Any*): Dom = 
    ${ HtmlInterpolatorMacros.htmlImpl('args, 'sc) }
}

// The macro would:
// 1. Parse the HTML structure at compile time
// 2. Generate Dom construction code
// 3. Apply appropriate escaping based on context
// 4. Report errors for malformed HTML
```

**Example Type Signatures:**
```scala
// Typeclass for safe interpolation
trait HtmlEncodable[A] {
  def encode(value: A): Dom
}

// Built-in instances
given HtmlEncodable[String] = s => Dom.Text(s)  // Escaped!
given HtmlEncodable[Int] = i => Dom.Text(i.toString)
given HtmlEncodable[Dom] = identity  // Pass through
given [A: HtmlEncodable]: HtmlEncodable[Option[A]] = ...
given [A: HtmlEncodable]: HtmlEncodable[List[A]] = ...
```

### 2.2 Context-Aware Escaping (HIGH PRIORITY)

**The Problem:**
Current escaping is one-size-fits-all HTML escaping. Different contexts need different escaping:

| Context | Dangerous Chars | Escaping Needed |
|---------|-----------------|-----------------|
| HTML text | `<`, `>`, `&`, quotes | HTML entities |
| HTML attribute | quotes, `<`, `>` | HTML entities |
| JavaScript string | `"`, `\`, newlines, `</script>` | JS escaping |
| URL | Many | Percent-encoding |
| CSS value | `</style>`, expressions | CSS escaping |

**Current Risk:**
```scala
val userInput = "'; alert('xss'); //"
script.inlineJs(js"var name = '$userInput';")
// VULNERABLE! No JS escaping applied
```

**Proposal:**

```scala
// Type-safe wrappers for different contexts
opaque type JsString = String  // JS-escaped string literal
opaque type UrlString = String // URL-encoded string
opaque type CssString = String // CSS-escaped string

// Context-aware encoding
trait JsEncodable[A] {
  def encodeJs(value: A): JsString
}

// Usage
val userInput = "'; alert('xss'); //"
js"var name = ${userInput.asJsString};"
// Produces: var name = '\'; alert(\'xss\'); //';
```

**Implementation in html"" interpolator:**
```scala
html"""
  <div onclick="handleClick('${userName.asJsString}')">
    ${userName}  <!-- HTML context - HTML escaped -->
  </div>
  <a href="/user?name=${userName.asUrl}">Link</a>
"""
```

### 2.3 Compile-Time HTML Validation (MEDIUM PRIORITY)

**The Problem:**
The DSL allows any tag composition, but some combinations are invalid HTML:
- `<div>` inside `<p>` (invalid)
- `<li>` outside `<ul>` or `<ol>` (invalid)
- Void elements with children (invalid)

**Proposal: Phantom Type Enforcement**

```scala
// Element categories
sealed trait FlowContent
sealed trait PhrasingContent extends FlowContent
sealed trait HeadingContent extends FlowContent
// etc.

// Elements with category markers
val div: Element[FlowContent] = ...
val p: Element[PhrasingContent] = ...
val span: Element[PhrasingContent] = ...

// Apply methods constrain children
def apply[C <: AllowedChildOf[FlowContent]](children: C*): Element[FlowContent]
```

**Benefits:**
- Invalid nesting caught at compile time
- Better IDE autocomplete suggestions
- Self-documenting API

**Downsides:**
- Significant type complexity
- May break existing code
- HTML5 content model is complex

**Recommendation:** Offer as opt-in via alternate import:
```scala
import zio.http.template2._           // Current behavior
import zio.http.template2.strict._    // With validation
```

### 2.4 Performance Optimizations (MEDIUM PRIORITY)

**Current Issues:**
1. String concatenation in hot paths (should use StringBuilder)
2. Map operations for attributes (could use ListMap or Array)
3. No pre-allocation for known sizes

**Proposal: Optimized Rendering**

```scala
// Current (Dom.scala lines 283-324)
val attributeString = if (attributes.nonEmpty) {
  val sb = ThreadLocals.stringBuilder  // Good - reuses StringBuilder
  attributes.foreach { ... }           // OK but could be while loop
  sb.toString                          // Allocates new String
}

// Improved
def renderTo(writer: Appendable): Unit = {
  // Write directly to output, no intermediate strings
  writer.append("<").append(tag)
  renderAttributesTo(writer)
  if (VoidElements.contains(tag)) {
    writer.append("/>")
  } else {
    writer.append(">")
    children.foreach(_.renderTo(writer))
    writer.append("</").append(tag).append(">")
  }
}

// Provide both APIs
def render: String = {
  val sb = new StringBuilder(estimatedSize)
  renderTo(sb)
  sb.toString
}
```

**Benchmark Targets:**
- Avoid intermediate String allocations
- Use while loops in hot paths (per ScalaTags patterns)
- Pre-allocate StringBuilder with estimated size

### 2.5 CSS-in-Scala DSL (MEDIUM PRIORITY)

**The Problem:**
CSS is written as strings, losing type safety:
```scala
style.inlineCss(css"colr: red;")  // Typo not caught!
```

**Proposal: Type-Safe CSS Properties**

```scala
import zio.http.template2.css._

val myStyles = stylesheet(
  body(
    margin := 0.px,
    fontFamily := sansSerif
  ),
  selector".container"(
    display := flex,
    justifyContent := center,
    backgroundColor := rgb(255, 0, 0)
  ),
  div.hover(
    opacity := 0.8
  )
)

// Renders to:
// body { margin: 0px; font-family: sans-serif; }
// .container { display: flex; justify-content: center; background-color: rgb(255, 0, 0); }
// div:hover { opacity: 0.8; }
```

**Benefits:**
- Typos caught at compile time
- IDE autocomplete for properties
- Type-safe values (can't use "red" for margin)
- Integrates with existing CssSelector DSL

### 2.6 JS Expression Builder (LOW PRIORITY)

**The Problem:**
JavaScript strings are error-prone:
```scala
js"document.getElementById('${id}').innerHtml = '${content}'"
// Easy to make mistakes, XSS risk
```

**Proposal: Minimal JS DSL for Common Patterns**

```scala
import zio.http.template2.js._

// Safe DOM manipulation
val action = JsExpr.getElementById(id)
  .setInnerText(content)  // Properly escaped

// Event handlers
val onClick = JsExpr.fetch("/api/data")
  .then(JsExpr.updateElement("#result"))

// Renders to safe, escaped JavaScript
```

**Scope:** Focus on common patterns (DOM updates, fetch, event handling) rather than full JS DSL.

### 2.7 SVG Element Support (LOW PRIORITY)

**The Problem:**
SVG has its own namespace and elements, not currently specialized:
```scala
val icon = svg(/* works but no SVG-specific helpers */)
```

**Proposal:**
```scala
import zio.http.template2.svg._

val icon = svg(
  viewBox := "0 0 100 100",
  circle(cx := 50, cy := 50, r := 40, fill := "blue"),
  path(d := "M10 80 Q 95 10 180 80")
)
```

---

## Part 3: Implementation Priority Matrix

| Proposal | Impact | Effort | Priority |
|----------|--------|--------|----------|
| 2.1 HTML Interpolator | HIGH | HIGH | P0 |
| 2.2 Context-Aware Escaping | HIGH | MEDIUM | P0 |
| 2.4 Performance Optimizations | MEDIUM | LOW | P1 |
| 2.5 CSS-in-Scala DSL | MEDIUM | HIGH | P2 |
| 2.3 Compile-Time HTML Validation | LOW | HIGH | P3 |
| 2.6 JS Expression Builder | LOW | MEDIUM | P3 |
| 2.7 SVG Element Support | LOW | LOW | P3 |

---

## Part 4: Typeclass-Based Interpolator Design

### 4.1 Design Philosophy

Each interpolator (`html""`, `css""`, `js""`, `selector""`) should have its own typeclass that controls:
1. **What types can be interpolated** (compile-time safety)
2. **How values are encoded** (context-appropriate escaping)
3. **Composition rules** (how fragments combine)

This provides:
- Extensibility (users can add instances for their types)
- Type safety (only valid types compile)
- Context-aware escaping (each interpolator handles its domain)

---

### 4.2 Position-Based Typeclasses Overview

The HTML interpolator uses **four distinct typeclasses** based on interpolation position:

```
<$tagName $attrName="$attrValue">
    ↑          ↑          ↑
    │          │          └── ToAttrValue[A]
    │          └───────────── ToAttrName[A]  
    └──────────────────────── ToTagName[A]

  $elements
      ↑
      └── ToElements[A]
</$tagName>
```

Each position has different:
- **Security requirements** (tag names are most restricted)
- **Output types** (String vs Dom vs AttributeValue)
- **Valid types** (what can be interpolated there)

---

### 4.3 ToTagName (Tag Position)

```scala
package zio.http.template2

/**
 * Typeclass for values that can be interpolated as HTML tag names.
 * 
 * Position: html"<$tagName>...</$tagName>"
 * 
 * SECURITY: This is the most restricted typeclass.
 * Only whitelisted, validated tag names are allowed.
 * Arbitrary strings could enable XSS via custom elements.
 */
trait ToTagName[-A] {
  def toTagName(value: A): String
}

object ToTagName {
  def apply[A](implicit ev: ToTagName[A]): ToTagName[A] = ev
  
  // ═══════════════════════════════════════════════════════════════
  // SECURITY: Very restricted set of instances
  // ═══════════════════════════════════════════════════════════════
  
  /** 
   * NO instance for raw String - too dangerous!
   * 
   * WRONG: val tag = "script"; html"<$tag>evil</$tag>"
   * 
   * If you need dynamic tags, use Dom.element() explicitly:
   *   Dom.element(validateTag(userInput))
   */
  // NO implicit val string: ToTagName[String]
  
  /**
   * SafeTagName is a validated wrapper that only allows known HTML tags.
   */
  final case class SafeTagName private (value: String) extends AnyVal
  
  object SafeTagName {
    private val validTags = Set(
      // Document
      "html", "head", "body", "title",
      // Sections  
      "header", "footer", "main", "nav", "section", "article", "aside",
      // Headings
      "h1", "h2", "h3", "h4", "h5", "h6",
      // Text content
      "p", "div", "span", "pre", "blockquote", "hr", "br",
      // Lists
      "ul", "ol", "li", "dl", "dt", "dd",
      // Tables
      "table", "thead", "tbody", "tfoot", "tr", "th", "td", "caption",
      // Forms
      "form", "input", "button", "select", "option", "textarea", "label", "fieldset", "legend",
      // Media
      "img", "video", "audio", "source", "canvas", "svg",
      // Inline
      "a", "strong", "em", "code", "mark", "small", "sub", "sup",
      // Other
      "details", "summary", "dialog", "template", "slot"
      // NOTE: "script" and "style" intentionally omitted - use Dom.script/Dom.style
    )
    
    def apply(tag: String): Option[SafeTagName] =
      if (validTags.contains(tag.toLowerCase)) Some(new SafeTagName(tag.toLowerCase))
      else None
    
    def unsafe(tag: String): SafeTagName = new SafeTagName(tag)
  }
  
  implicit val safeTagName: ToTagName[SafeTagName] = 
    instance(_.value)
  
  /** Dom.Element can provide its tag name */
  implicit val domElement: ToTagName[Dom.Element] = 
    instance(_.tag)
  
  private def instance[A](f: A => String): ToTagName[A] = f(_)
}
```

---

### 4.4 ToAttrName (Attribute Name Position)

```scala
package zio.http.template2

/**
 * Typeclass for values that can be interpolated as HTML attribute names.
 * 
 * Position: html"<div $attrName=$value>"
 * 
 * SECURITY: Restricted to prevent injection of event handlers.
 * Arbitrary strings could enable onclick="evil()" injection.
 */
trait ToAttrName[-A] {
  def toAttrName(value: A): String
}

object ToAttrName {
  def apply[A](implicit ev: ToAttrName[A]): ToAttrName[A] = ev
  
  // ═══════════════════════════════════════════════════════════════
  // SECURITY: Restricted instances
  // ═══════════════════════════════════════════════════════════════
  
  /**
   * NO instance for raw String - could inject event handlers!
   * 
   * WRONG: val attr = "onclick"; html"<div $attr=$evil>"
   */
  // NO implicit val string: ToAttrName[String]
  
  /**
   * SafeAttrName only allows known safe attribute names.
   * Event handlers (onclick, onload, etc.) are NOT in this list.
   */
  final case class SafeAttrName private (value: String) extends AnyVal
  
  object SafeAttrName {
    private val safeAttrs = Set(
      // Global attributes
      "id", "class", "style", "title", "lang", "dir", "tabindex", "hidden",
      "accesskey", "draggable", "contenteditable", "spellcheck", "translate",
      // Data attributes (prefix)
      // Aria attributes (prefix)
      // Links
      "href", "target", "rel", "download",
      // Media
      "src", "alt", "width", "height", "loading", "srcset", "sizes",
      // Forms
      "name", "value", "type", "placeholder", "required", "disabled", "readonly",
      "checked", "selected", "multiple", "min", "max", "step", "pattern",
      "autocomplete", "autofocus", "form", "formaction", "formmethod",
      "minlength", "maxlength", "size", "cols", "rows", "wrap",
      // Tables
      "colspan", "rowspan", "scope", "headers",
      // Other
      "role", "for", "action", "method", "enctype"
      // NOTE: on* event handlers intentionally omitted
    )
    
    def apply(attr: String): Option[SafeAttrName] = {
      val lower = attr.toLowerCase
      if (safeAttrs.contains(lower)) Some(new SafeAttrName(lower))
      else if (lower.startsWith("data-")) Some(new SafeAttrName(lower))
      else if (lower.startsWith("aria-")) Some(new SafeAttrName(lower))
      else None
    }
    
    /** For known-safe usage in library code */
    def unsafe(attr: String): SafeAttrName = new SafeAttrName(attr)
  }
  
  /**
   * EventAttrName specifically for event handlers.
   * Requires explicit opt-in for security awareness.
   */
  final case class EventAttrName private (value: String) extends AnyVal
  
  object EventAttrName {
    private val eventAttrs = Set(
      "onclick", "ondblclick", "onmousedown", "onmouseup", "onmouseover",
      "onmousemove", "onmouseout", "onmouseenter", "onmouseleave",
      "onkeydown", "onkeyup", "onkeypress",
      "onfocus", "onblur", "onchange", "oninput", "onsubmit", "onreset",
      "onload", "onunload", "onerror", "onresize", "onscroll"
    )
    
    def apply(attr: String): Option[EventAttrName] =
      if (eventAttrs.contains(attr.toLowerCase)) Some(new EventAttrName(attr.toLowerCase))
      else None
  }
  
  implicit val safeAttrName: ToAttrName[SafeAttrName] = instance(_.value)
  implicit val eventAttrName: ToAttrName[EventAttrName] = instance(_.value)
  
  /** Dom.PartialAttribute provides its name */
  implicit val partialAttribute: ToAttrName[Dom.PartialAttribute] = instance(_.name)
  
  private def instance[A](f: A => String): ToAttrName[A] = f(_)
}
```

---

### 4.5 ToAttrValue (Attribute Value Position)

```scala
package zio.http.template2

/**
 * Typeclass for values that can be interpolated as HTML attribute values.
 * 
 * Position: html"<div class=$value>" or html"<a href=$url>"
 * 
 * Values are escaped appropriately for attribute context.
 * Js IS allowed here (for onclick handlers, etc.)
 */
trait ToAttrValue[-A] {
  def toAttrValue(value: A): Dom.AttributeValue
}

object ToAttrValue {
  def apply[A](implicit ev: ToAttrValue[A]): ToAttrValue[A] = ev
  
  def instance[A](f: A => Dom.AttributeValue): ToAttrValue[A] = f(_)
  
  def fromString[A](f: A => String): ToAttrValue[A] = 
    instance(a => Dom.AttributeValue.StringValue(f(a)))

  // ═══════════════════════════════════════════════════════════════
  // Primitive Instances
  // ═══════════════════════════════════════════════════════════════
  
  implicit val string: ToAttrValue[String] = 
    instance(Dom.AttributeValue.StringValue(_))
  
  implicit val int: ToAttrValue[Int] = fromString(_.toString)
  implicit val long: ToAttrValue[Long] = fromString(_.toString)
  implicit val double: ToAttrValue[Double] = fromString(_.toString)
  implicit val char: ToAttrValue[Char] = fromString(_.toString)
  
  implicit val boolean: ToAttrValue[Boolean] = 
    instance(Dom.AttributeValue.BooleanValue(_))
  
  // ═══════════════════════════════════════════════════════════════
  // AttributeValue passthrough
  // ═══════════════════════════════════════════════════════════════
  
  implicit val attributeValue: ToAttrValue[Dom.AttributeValue] = instance(identity)
  
  // ═══════════════════════════════════════════════════════════════
  // Special Types
  // ═══════════════════════════════════════════════════════════════
  
  /** Js IS allowed in attribute values (for event handlers) */
  implicit val js: ToAttrValue[Js] = 
    instance(js => Dom.AttributeValue.JsValue(js))
  
  /** Css for style attribute */
  implicit val css: ToAttrValue[Css] = fromString(_.value)
  
  /** URL is encoded appropriately */
  implicit val url: ToAttrValue[zio.http.URL] = fromString(_.encode)
  
  /** UUID renders as string */
  implicit val uuid: ToAttrValue[java.util.UUID] = fromString(_.toString)
  
  // ═══════════════════════════════════════════════════════════════
  // Collection Instances (for multi-value attributes like class)
  // ═══════════════════════════════════════════════════════════════
  
  implicit val iterableString: ToAttrValue[Iterable[String]] =
    instance(iter => Dom.AttributeValue.MultiValue(iter.toVector, AttributeSeparator.Space))
  
  implicit val listString: ToAttrValue[List[String]] =
    instance(list => Dom.AttributeValue.MultiValue(list.toVector, AttributeSeparator.Space))
  
  implicit def option[A](implicit ev: ToAttrValue[A]): ToAttrValue[Option[A]] =
    instance {
      case Some(a) => ev.toAttrValue(a)
      case None    => Dom.AttributeValue.BooleanValue(false)
    }
  
  implicit def tuple2String: ToAttrValue[(String, String)] =
    instance { case (a, b) => 
      Dom.AttributeValue.MultiValue(Vector(a, b), AttributeSeparator.Space)
    }
}
```

---

### 4.6 ToElements (Child Content Position)

```scala
package zio.http.template2

/**
 * Typeclass for values that can be interpolated as HTML element children.
 * 
 * Position: html"<div>$content</div>"
 * 
 * Values become Dom nodes. Strings are HTML-escaped.
 * Js is NOT allowed here (use <script> element instead).
 */
trait ToElements[-A] {
  def toElements(value: A): Dom
}

object ToElements {
  def apply[A](implicit ev: ToElements[A]): ToElements[A] = ev
  
  def instance[A](f: A => Dom): ToElements[A] = f(_)

  // ═══════════════════════════════════════════════════════════════
  // Primitive Instances
  // ═══════════════════════════════════════════════════════════════
  
  /** Strings become Dom.Text, HTML-escaped during render */
  implicit val string: ToElements[String] = instance(Dom.Text(_))
  
  implicit val int: ToElements[Int] = instance(i => Dom.Text(i.toString))
  implicit val long: ToElements[Long] = instance(l => Dom.Text(l.toString))
  implicit val double: ToElements[Double] = instance(d => Dom.Text(d.toString))
  implicit val boolean: ToElements[Boolean] = instance(b => Dom.Text(b.toString))
  implicit val char: ToElements[Char] = instance(c => Dom.Text(c.toString))
  
  // ═══════════════════════════════════════════════════════════════
  // Dom Types (pass through unchanged)
  // ═══════════════════════════════════════════════════════════════
  
  implicit val dom: ToElements[Dom] = instance(identity)
  implicit val element: ToElements[Dom.Element] = instance(identity)
  implicit val text: ToElements[Dom.Text] = instance(identity)
  implicit val fragment: ToElements[Dom.Fragment] = instance(identity)
  implicit val rawHtml: ToElements[Dom.RawHtml] = instance(identity)
  
  // ═══════════════════════════════════════════════════════════════
  // Special Types
  // ═══════════════════════════════════════════════════════════════
  
  /**
   * Js is NOT allowed in element content - XSS risk!
   * 
   * WRONG: html"<div>$jsCode</div>"
   * RIGHT: html"<script>$jsCode</script>" or script.inlineJs(jsCode)
   */
  // NO implicit val js: ToElements[Js]
  
  /** Css in content (for documentation, rare) */
  implicit val css: ToElements[Css] = instance(css => Dom.Text(css.value))
  
  // ═══════════════════════════════════════════════════════════════
  // Collection Instances
  // ═══════════════════════════════════════════════════════════════
  
  implicit def option[A](implicit ev: ToElements[A]): ToElements[Option[A]] =
    instance(_.fold(Dom.Empty: Dom)(ev.toElements))
  
  implicit def iterable[A](implicit ev: ToElements[A]): ToElements[Iterable[A]] =
    instance(iter => Dom.Fragment(iter.map(ev.toElements).toVector))
  
  implicit def list[A](implicit ev: ToElements[A]): ToElements[List[A]] =
    instance(list => Dom.Fragment(list.map(ev.toElements).toVector))
  
  implicit def vector[A](implicit ev: ToElements[A]): ToElements[Vector[A]] =
    instance(vec => Dom.Fragment(vec.map(ev.toElements)))
  
  // ═══════════════════════════════════════════════════════════════
  // Tuple Instances
  // ═══════════════════════════════════════════════════════════════
  
  implicit def tuple2[A: ToElements, B: ToElements]: ToElements[(A, B)] =
    instance { case (a, b) =>
      Dom.Fragment(Vector(ToElements[A].toElements(a), ToElements[B].toElements(b)))
    }
  
  implicit def tuple3[A: ToElements, B: ToElements, C: ToElements]: ToElements[(A, B, C)] =
    instance { case (a, b, c) =>
      Dom.Fragment(Vector(
        ToElements[A].toElements(a), 
        ToElements[B].toElements(b), 
        ToElements[C].toElements(c)
      ))
    }
}
```

---

### 4.7 ToText (Text-Only Content)

```scala
package zio.http.template2

/**
 * Typeclass for values that render as plain text (not elements).
 * 
 * Used when you specifically want text content, not DOM structure.
 * The output is always a String that will be HTML-escaped.
 * 
 * Difference from ToElements:
 * - ToElements: A → Dom (can be Element, Fragment, Text, etc.)
 * - ToText: A → String (always becomes escaped text)
 */
trait ToText[-A] {
  def toText(value: A): String
}

object ToText {
  def apply[A](implicit ev: ToText[A]): ToText[A] = ev
  
  def instance[A](f: A => String): ToText[A] = f(_)

  // ═══════════════════════════════════════════════════════════════
  // Primitive Instances
  // ═══════════════════════════════════════════════════════════════
  
  implicit val string: ToText[String] = instance(identity)
  implicit val int: ToText[Int] = instance(_.toString)
  implicit val long: ToText[Long] = instance(_.toString)
  implicit val double: ToText[Double] = instance(_.toString)
  implicit val float: ToText[Float] = instance(_.toString)
  implicit val boolean: ToText[Boolean] = instance(_.toString)
  implicit val char: ToText[Char] = instance(_.toString)
  implicit val byte: ToText[Byte] = instance(_.toString)
  implicit val short: ToText[Short] = instance(_.toString)
  
  // ═══════════════════════════════════════════════════════════════
  // Common Types
  // ═══════════════════════════════════════════════════════════════
  
  implicit val bigInt: ToText[BigInt] = instance(_.toString)
  implicit val bigDecimal: ToText[BigDecimal] = instance(_.toString)
  implicit val uuid: ToText[java.util.UUID] = instance(_.toString)
  
  // ═══════════════════════════════════════════════════════════════
  // Date/Time (ISO-8601 format)
  // ═══════════════════════════════════════════════════════════════
  
  implicit val instant: ToText[java.time.Instant] = instance(_.toString)
  implicit val localDate: ToText[java.time.LocalDate] = instance(_.toString)
  implicit val localTime: ToText[java.time.LocalTime] = instance(_.toString)
  implicit val localDateTime: ToText[java.time.LocalDateTime] = instance(_.toString)
  
  // ═══════════════════════════════════════════════════════════════
  // Dom.Text extraction
  // ═══════════════════════════════════════════════════════════════
  
  implicit val domText: ToText[Dom.Text] = instance(_.content)
  
  // ═══════════════════════════════════════════════════════════════
  // Collections (concatenated)
  // ═══════════════════════════════════════════════════════════════
  
  implicit def option[A](implicit ev: ToText[A]): ToText[Option[A]] =
    instance(_.fold("")(ev.toText))
  
  implicit def iterable[A](implicit ev: ToText[A]): ToText[Iterable[A]] =
    instance(_.map(ev.toText).mkString)
  
  // ═══════════════════════════════════════════════════════════════
  // NOT allowed (use appropriate context)
  // ═══════════════════════════════════════════════════════════════
  
  // NO Js - not text content
  // NO Css - not text content  
  // NO Dom.Element - use ToElements instead
}
```

---

### 4.8 ToJs (JavaScript Interpolation)

```scala
package zio.http.template2

/**
 * Typeclass for values that can be interpolated into JavaScript code.
 * 
 * Used by: js"console.log($value)"
 * 
 * SECURITY: Values are properly escaped to prevent:
 * - Script breakout via </script>
 * - String injection via quotes
 * - Code injection via special characters
 */
trait ToJs[-A] {
  def toJs(value: A): String
}

object ToJs {
  def apply[A](implicit ev: ToJs[A]): ToJs[A] = ev
  
  def instance[A](f: A => String): ToJs[A] = f(_)
  
  /** Create instance that quotes the result as a JS string literal */
  def quoted[A](f: A => String): ToJs[A] = 
    instance(a => s"'${escapeJsString(f(a))}'")

  // ═══════════════════════════════════════════════════════════════
  // JavaScript String Escaping
  // ═══════════════════════════════════════════════════════════════
  
  private[template2] def escapeJsString(s: String): String = {
    val sb = new StringBuilder(s.length + 16)
    var i = 0
    while (i < s.length) {
      s.charAt(i) match {
        case '"'  => sb.append("\\\"")
        case '\'' => sb.append("\\'")
        case '\\' => sb.append("\\\\")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case '\b' => sb.append("\\b")
        case '\f' => sb.append("\\f")
        case '<'  => sb.append("\\u003c")  // Prevent </script> breakout
        case '>'  => sb.append("\\u003e")  // Prevent --> breakout
        case '&'  => sb.append("\\u0026")  // Prevent HTML entity issues
        case c if c < 32 => sb.append(f"\\u${c.toInt}%04x")
        case c    => sb.append(c)
      }
      i += 1
    }
    sb.toString
  }

  // ═══════════════════════════════════════════════════════════════
  // Primitive Instances - Numbers (no escaping needed)
  // ═══════════════════════════════════════════════════════════════
  
  implicit val int: ToJs[Int] = instance(_.toString)
  implicit val long: ToJs[Long] = instance(_.toString)
  implicit val double: ToJs[Double] = instance { d =>
    if (d.isNaN) "NaN"
    else if (d.isInfinite) if (d > 0) "Infinity" else "-Infinity"
    else d.toString
  }
  implicit val float: ToJs[Float] = instance { f =>
    if (f.isNaN) "NaN"
    else if (f.isInfinite) if (f > 0) "Infinity" else "-Infinity"
    else f.toString
  }
  implicit val boolean: ToJs[Boolean] = instance(_.toString)
  
  // ═══════════════════════════════════════════════════════════════
  // String - QUOTED and ESCAPED
  // ═══════════════════════════════════════════════════════════════
  
  /** Strings become quoted JS string literals with escaping */
  implicit val string: ToJs[String] = quoted(identity)
  
  implicit val char: ToJs[Char] = quoted(_.toString)
  
  // ═══════════════════════════════════════════════════════════════
  // Js passthrough (already valid JS)
  // ═══════════════════════════════════════════════════════════════
  
  implicit val js: ToJs[Js] = instance(_.value)
  
  // ═══════════════════════════════════════════════════════════════
  // null/undefined
  // ═══════════════════════════════════════════════════════════════
  
  implicit val unit: ToJs[Unit] = instance(_ => "undefined")
  
  implicit def option[A](implicit ev: ToJs[A]): ToJs[Option[A]] =
    instance(_.fold("null")(ev.toJs))
  
  // ═══════════════════════════════════════════════════════════════
  // Collections → JS Arrays
  // ═══════════════════════════════════════════════════════════════
  
  implicit def iterable[A](implicit ev: ToJs[A]): ToJs[Iterable[A]] =
    instance(iter => iter.map(ev.toJs).mkString("[", ", ", "]"))
  
  implicit def list[A](implicit ev: ToJs[A]): ToJs[List[A]] =
    instance(list => list.map(ev.toJs).mkString("[", ", ", "]"))
  
  implicit def vector[A](implicit ev: ToJs[A]): ToJs[Vector[A]] =
    instance(vec => vec.map(ev.toJs).mkString("[", ", ", "]"))
  
  implicit def set[A](implicit ev: ToJs[A]): ToJs[Set[A]] =
    instance(set => set.map(ev.toJs).mkString("[", ", ", "]"))
  
  // ═══════════════════════════════════════════════════════════════
  // Maps → JS Objects
  // ═══════════════════════════════════════════════════════════════
  
  implicit def stringMap[V](implicit ev: ToJs[V]): ToJs[Map[String, V]] =
    instance { map =>
      val entries = map.map { case (k, v) =>
        s"${escapeJsString(k)}: ${ev.toJs(v)}"
      }
      entries.mkString("{", ", ", "}")
    }
  
  // ═══════════════════════════════════════════════════════════════
  // Tuples → JS Arrays
  // ═══════════════════════════════════════════════════════════════
  
  implicit def tuple2[A: ToJs, B: ToJs]: ToJs[(A, B)] =
    instance { case (a, b) => s"[${ToJs[A].toJs(a)}, ${ToJs[B].toJs(b)}]" }
  
  implicit def tuple3[A: ToJs, B: ToJs, C: ToJs]: ToJs[(A, B, C)] =
    instance { case (a, b, c) => 
      s"[${ToJs[A].toJs(a)}, ${ToJs[B].toJs(b)}, ${ToJs[C].toJs(c)}]" 
    }
}
```

---

### 4.9 ToCss (CSS Interpolation)

```scala
package zio.http.template2

/**
 * Typeclass for values that can be interpolated into CSS.
 * 
 * Used by: css"margin: $value; color: $color"
 */
trait ToCss[-A] {
  def toCss(value: A): String
}

object ToCss {
  def apply[A](implicit ev: ToCss[A]): ToCss[A] = ev
  
  def instance[A](f: A => String): ToCss[A] = f(_)

  // ═══════════════════════════════════════════════════════════════
  // Primitive Instances
  // ═══════════════════════════════════════════════════════════════
  
  implicit val string: ToCss[String] = instance(identity)
  implicit val int: ToCss[Int] = instance(_.toString)
  implicit val long: ToCss[Long] = instance(_.toString)
  implicit val double: ToCss[Double] = instance(_.toString)
  implicit val float: ToCss[Float] = instance(_.toString)
  
  // ═══════════════════════════════════════════════════════════════
  // Css/CssSelector passthrough
  // ═══════════════════════════════════════════════════════════════
  
  implicit val css: ToCss[Css] = instance(_.value)
  implicit val cssSelector: ToCss[CssSelector] = instance(_.render)
  
  // ═══════════════════════════════════════════════════════════════
  // CSS Length Values
  // ═══════════════════════════════════════════════════════════════
  
  sealed trait CssUnit {
    def suffix: String
  }
  object CssUnit {
    case object Px extends CssUnit { val suffix = "px" }
    case object Em extends CssUnit { val suffix = "em" }
    case object Rem extends CssUnit { val suffix = "rem" }
    case object Percent extends CssUnit { val suffix = "%" }
    case object Vh extends CssUnit { val suffix = "vh" }
    case object Vw extends CssUnit { val suffix = "vw" }
    case object Ch extends CssUnit { val suffix = "ch" }
    case object Ex extends CssUnit { val suffix = "ex" }
  }
  
  final case class CssLength(value: Double, unit: CssUnit) {
    override def toString: String = s"$value${unit.suffix}"
  }
  
  implicit val cssLength: ToCss[CssLength] = instance(_.toString)
  
  // Extension methods for creating lengths
  implicit class IntCssOps(private val n: Int) extends AnyVal {
    def px: CssLength = CssLength(n.toDouble, CssUnit.Px)
    def em: CssLength = CssLength(n.toDouble, CssUnit.Em)
    def rem: CssLength = CssLength(n.toDouble, CssUnit.Rem)
    def percent: CssLength = CssLength(n.toDouble, CssUnit.Percent)
    def vh: CssLength = CssLength(n.toDouble, CssUnit.Vh)
    def vw: CssLength = CssLength(n.toDouble, CssUnit.Vw)
  }
  
  implicit class DoubleCssOps(private val n: Double) extends AnyVal {
    def px: CssLength = CssLength(n, CssUnit.Px)
    def em: CssLength = CssLength(n, CssUnit.Em)
    def rem: CssLength = CssLength(n, CssUnit.Rem)
    def percent: CssLength = CssLength(n, CssUnit.Percent)
    def vh: CssLength = CssLength(n, CssUnit.Vh)
    def vw: CssLength = CssLength(n, CssUnit.Vw)
  }
  
  // ═══════════════════════════════════════════════════════════════
  // CSS Color Values
  // ═══════════════════════════════════════════════════════════════
  
  sealed trait CssColor {
    def render: String
  }
  
  object CssColor {
    final case class Hex(value: String) extends CssColor {
      def render: String = if (value.startsWith("#")) value else s"#$value"
    }
    
    final case class Rgb(r: Int, g: Int, b: Int) extends CssColor {
      def render: String = s"rgb($r, $g, $b)"
    }
    
    final case class Rgba(r: Int, g: Int, b: Int, a: Double) extends CssColor {
      def render: String = s"rgba($r, $g, $b, $a)"
    }
    
    final case class Hsl(h: Int, s: Int, l: Int) extends CssColor {
      def render: String = s"hsl($h, $s%, $l%)"
    }
    
    final case class Named(name: String) extends CssColor {
      def render: String = name
    }
    
    // Constructors
    def hex(value: String): CssColor = Hex(value)
    def rgb(r: Int, g: Int, b: Int): CssColor = Rgb(r, g, b)
    def rgba(r: Int, g: Int, b: Int, a: Double): CssColor = Rgba(r, g, b, a)
    def hsl(h: Int, s: Int, l: Int): CssColor = Hsl(h, s, l)
    
    // Common colors
    val transparent: CssColor = Named("transparent")
    val inherit: CssColor = Named("inherit")
    val currentColor: CssColor = Named("currentColor")
  }
  
  implicit val cssColor: ToCss[CssColor] = instance(_.render)
  
  // ═══════════════════════════════════════════════════════════════
  // CSS Time Values
  // ═══════════════════════════════════════════════════════════════
  
  final case class CssTime(value: Double, unit: String) {
    override def toString: String = s"$value$unit"
  }
  
  object CssTime {
    def ms(value: Double): CssTime = CssTime(value, "ms")
    def s(value: Double): CssTime = CssTime(value, "s")
  }
  
  implicit val cssTime: ToCss[CssTime] = instance(_.toString)
  
  implicit class IntTimeOps(private val n: Int) extends AnyVal {
    def ms: CssTime = CssTime(n.toDouble, "ms")
    def s: CssTime = CssTime(n.toDouble, "s")
  }
  
  // ═══════════════════════════════════════════════════════════════
  // Option - empty string for None
  // ═══════════════════════════════════════════════════════════════
  
  implicit def option[A](implicit ev: ToCss[A]): ToCss[Option[A]] =
    instance(_.fold("")(ev.toCss))
}
```

---

### 4.10 Complete Typeclass Summary

| Typeclass | Interpolator | Position | Output | Example |
|-----------|--------------|----------|--------|---------|
| `ToTagName` | `html""` | `<$tag>` | `String` | `html"<$safeTag>..."` |
| `ToAttrName` | `html""` | `$attr=...` | `String` | `html"<div $safeAttr=...>"` |
| `ToAttrValue` | `html""` | `attr=$value` | `AttributeValue` | `html"<div class=$cls>"` |
| `ToElements` | `html""` | `>$content<` | `Dom` | `html"<div>$children</div>"` |
| `ToText` | `html""` | text only | `String` | Simple text rendering |
| `ToJs` | `js""` | JS code | `String` | `js"var x = $val"` |
| `ToCss` | `css""` | CSS code | `String` | `css"margin: $len"` |

### Security Matrix

| Typeclass | String? | Js? | Danger Level | Protection |
|-----------|---------|-----|--------------|------------|
| `ToTagName` | NO | NO | **CRITICAL** | Whitelist only |
| `ToAttrName` | NO | NO | **HIGH** | Safe/Event split |
| `ToAttrValue` | YES | YES | MEDIUM | Attribute escaping |
| `ToElements` | YES | NO | MEDIUM | HTML escaping |
| `ToText` | YES | NO | LOW | HTML escaping |
| `ToJs` | YES (quoted) | YES | MEDIUM | JS string escaping |
| `ToCss` | YES | NO | LOW | Minimal escaping |

### Instance Availability

| Type | ToTagName | ToAttrName | ToAttrValue | ToElements | ToText | ToJs | ToCss |
|------|-----------|------------|-------------|------------|--------|------|-------|
| `String` | - | - | ✓ | ✓ | ✓ | ✓ (quoted) | ✓ |
| `Int` | - | - | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Boolean` | - | - | ✓ | ✓ | ✓ | ✓ | - |
| `Js` | - | - | ✓ | - | - | ✓ | - |
| `Css` | - | - | ✓ | ✓ | - | - | ✓ |
| `Dom` | - | - | - | ✓ | - | - | - |
| `Dom.Element` | ✓ (tag) | - | - | ✓ | - | - | - |
| `SafeTagName` | ✓ | - | - | - | - | - | - |
| `SafeAttrName` | - | ✓ | - | - | - | - | - |
| `EventAttrName` | - | ✓ | - | - | - | - | - |
| `List[A]` | - | - | ✓ | ✓ | ✓ | ✓ (array) | - |
| `Option[A]` | - | - | ✓ | ✓ | ✓ | ✓ (null) | ✓ |
| `Map[String,V]` | - | - | - | - | - | ✓ (object) | - |
| `CssLength` | - | - | - | - | - | - | ✓ |
| `CssColor` | - | - | - | - | - | - | ✓ |
```

---

### 4.3 The ToCss Typeclass

```scala
package zio.http.template2

/**
 * Typeclass for values that can be safely interpolated into CSS.
 */
trait ToCss[-A] {
  def toCss(value: A): String
}

object ToCss {
  def apply[A](implicit ev: ToCss[A]): ToCss[A] = ev
  
  def instance[A](f: A => String): ToCss[A] = f(_)

  // ═══════════════════════════════════════════════════════════════
  // Primitive Instances
  // ═══════════════════════════════════════════════════════════════
  
  implicit val stringToCss: ToCss[String] = instance(identity)
  implicit val intToCss: ToCss[Int] = instance(_.toString)
  implicit val longToCss: ToCss[Long] = instance(_.toString)
  implicit val doubleToCss: ToCss[Double] = instance(_.toString)
  
  // ═══════════════════════════════════════════════════════════════
  // CSS-Specific Types
  // ═══════════════════════════════════════════════════════════════
  
  implicit val cssToCss: ToCss[Css] = instance(_.value)
  implicit val cssSelectorToCss: ToCss[CssSelector] = instance(_.render)
  
  // ═══════════════════════════════════════════════════════════════
  // CSS Value Types (could be extended with typed CSS values)
  // ═══════════════════════════════════════════════════════════════
  
  /** Length with unit */
  case class CssLength(value: Double, unit: String) {
    override def toString: String = s"$value$unit"
  }
  object CssLength {
    def px(value: Double): CssLength = CssLength(value, "px")
    def em(value: Double): CssLength = CssLength(value, "em")
    def rem(value: Double): CssLength = CssLength(value, "rem")
    def percent(value: Double): CssLength = CssLength(value, "%")
  }
  
  implicit val cssLengthToCss: ToCss[CssLength] = instance(_.toString)
  
  /** Color values */
  case class CssColor(value: String)
  object CssColor {
    def rgb(r: Int, g: Int, b: Int): CssColor = CssColor(s"rgb($r, $g, $b)")
    def rgba(r: Int, g: Int, b: Int, a: Double): CssColor = CssColor(s"rgba($r, $g, $b, $a)")
    def hex(value: String): CssColor = CssColor(if (value.startsWith("#")) value else s"#$value")
  }
  
  implicit val cssColorToCss: ToCss[CssColor] = instance(_.value)
  
  // ═══════════════════════════════════════════════════════════════
  // Extension Methods (optional sugar)
  // ═══════════════════════════════════════════════════════════════
  
  implicit class IntCssOps(private val value: Int) extends AnyVal {
    def px: CssLength = CssLength.px(value.toDouble)
    def em: CssLength = CssLength.em(value.toDouble)
    def rem: CssLength = CssLength.rem(value.toDouble)
    def percent: CssLength = CssLength.percent(value.toDouble)
  }
  
  implicit class DoubleCssOps(private val value: Double) extends AnyVal {
    def px: CssLength = CssLength.px(value)
    def em: CssLength = CssLength.em(value)
    def rem: CssLength = CssLength.rem(value)
    def percent: CssLength = CssLength.percent(value)
  }
}
```

---

### 4.4 The ToJs Typeclass

```scala
package zio.http.template2

/**
 * Typeclass for values that can be safely interpolated into JavaScript.
 * 
 * CRITICAL: JavaScript interpolation requires careful escaping to prevent XSS.
 * This typeclass ensures values are properly escaped for JS string/expression context.
 */
trait ToJs[-A] {
  /**
   * Convert value to a JavaScript expression fragment.
   * The result should be safe to embed in JavaScript code.
   */
  def toJs(value: A, context: JsContext): String
}

object ToJs {
  def apply[A](implicit ev: ToJs[A]): ToJs[A] = ev
  
  def instance[A](f: A => String): ToJs[A] = (value, _) => f(value)
  
  def contextual[A](f: (A, JsContext) => String): ToJs[A] = f(_, _)
  
  // ═══════════════════════════════════════════════════════════════
  // JavaScript String Escaping
  // ═══════════════════════════════════════════════════════════════
  
  private def escapeJsString(s: String): String = {
    val sb = new StringBuilder
    s.foreach {
      case '"'  => sb.append("\\\"")
      case '\'' => sb.append("\\'")
      case '\\' => sb.append("\\\\")
      case '\n' => sb.append("\\n")
      case '\r' => sb.append("\\r")
      case '\t' => sb.append("\\t")
      case '<'  => sb.append("\\u003c")  // Prevent </script> injection
      case '>'  => sb.append("\\u003e")
      case '&'  => sb.append("\\u0026")
      case c if c < 32 => sb.append(f"\\u${c.toInt}%04x")
      case c    => sb.append(c)
    }
    sb.toString
  }
  
  // ═══════════════════════════════════════════════════════════════
  // Primitive Instances
  // ═══════════════════════════════════════════════════════════════
  
  /** Strings are JS-escaped and quoted in string context */
  implicit val stringToJs: ToJs[String] = contextual { (s, ctx) =>
    ctx match {
      case JsContext.StringLiteral => s"'${escapeJsString(s)}'"
      case JsContext.Expression    => s"'${escapeJsString(s)}'"  // Default to string
      case JsContext.Identifier    => 
        // Validate it's a valid JS identifier
        if (s.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*$")) s
        else throw new IllegalArgumentException(s"Invalid JS identifier: $s")
    }
  }
  
  /** Numbers don't need escaping */
  implicit val intToJs: ToJs[Int] = instance(_.toString)
  implicit val longToJs: ToJs[Long] = instance(_.toString)
  implicit val doubleToJs: ToJs[Double] = instance(_.toString)
  implicit val booleanToJs: ToJs[Boolean] = instance(_.toString)
  
  // ═══════════════════════════════════════════════════════════════
  // JS-Specific Types
  // ═══════════════════════════════════════════════════════════════
  
  /** Js values pass through unchanged (already validated) */
  implicit val jsToJs: ToJs[Js] = instance(_.value)
  
  /** JSON-serializable values */
  implicit val jsonValueToJs: ToJs[io.circe.Json] = 
    instance(_.noSpaces)  // If using circe
  
  // ═══════════════════════════════════════════════════════════════
  // Collection Instances (render as JS arrays)
  // ═══════════════════════════════════════════════════════════════
  
  implicit def listToJs[A](implicit ev: ToJs[A]): ToJs[List[A]] =
    contextual { (list, ctx) =>
      list.map(a => ev.toJs(a, JsContext.Expression)).mkString("[", ", ", "]")
    }
  
  implicit def optionToJs[A](implicit ev: ToJs[A]): ToJs[Option[A]] =
    contextual { (opt, ctx) =>
      opt.fold("null")(a => ev.toJs(a, ctx))
    }
  
  // ═══════════════════════════════════════════════════════════════
  // Map to JS Object
  // ═══════════════════════════════════════════════════════════════
  
  implicit def mapToJs[V](implicit ev: ToJs[V]): ToJs[Map[String, V]] =
    contextual { (map, ctx) =>
      val entries = map.map { case (k, v) =>
        s"${escapeJsString(k)}: ${ev.toJs(v, JsContext.Expression)}"
      }
      entries.mkString("{", ", ", "}")
    }
}

/**
 * Context in which a JavaScript interpolation occurs.
 */
sealed trait JsContext
object JsContext {
  /** Inside a string literal: `"...${HERE}..."` */
  case object StringLiteral extends JsContext
  
  /** As a general expression: `var x = ${HERE}` */
  case object Expression extends JsContext
  
  /** As an identifier: `${HERE}.method()` */
  case object Identifier extends JsContext
}
```

---

### 4.5 The ToSelector Typeclass

```scala
package zio.http.template2

/**
 * Typeclass for values that can be interpolated into CSS selectors.
 */
trait ToSelector[-A] {
  def toSelector(value: A): String
}

object ToSelector {
  def apply[A](implicit ev: ToSelector[A]): ToSelector[A] = ev
  
  def instance[A](f: A => String): ToSelector[A] = f(_)
  
  // ═══════════════════════════════════════════════════════════════
  // Instances
  // ═══════════════════════════════════════════════════════════════
  
  /** Strings are used directly (should be valid selector fragments) */
  implicit val stringToSelector: ToSelector[String] = instance { s =>
    // Basic validation
    if (s.matches("^[a-zA-Z0-9\\-_.#\\[\\]():>~\\s+*=\"'|$,n]+$")) s
    else throw new IllegalArgumentException(s"Invalid selector fragment: $s")
  }
  
  /** CssSelector values pass through */
  implicit val cssSelectorToSelector: ToSelector[CssSelector] = 
    instance(_.render)
  
  /** Dom.Element can be used as element selector */
  implicit val domElementToSelector: ToSelector[Dom.Element] =
    instance(_.tag)
}
```

---

### 4.6 Interpolator Macro Integration

```scala
package zio.http.template2

// Scala 3 implementation
trait HtmlInterpolator {
  extension(inline sc: StringContext) {
    /**
     * HTML string interpolator with type-safe escaping.
     * 
     * Usage:
     *   val name = "Alice"
     *   html"<div class=$cls>Hello, $name!</div>"
     * 
     * The macro analyzes the HTML structure and:
     * - Summons ToHtml[A] for values in text context
     * - Summons ToHtmlAttribute[A] for values in attribute context
     * - Reports compile errors for missing instances or invalid HTML
     */
    inline def html(inline args: Any*): Dom = 
      ${ HtmlInterpolatorMacros.htmlImpl('args, 'sc) }
  }
}

// Updated CssInterpolator
trait CssInterpolator {
  extension(inline sc: StringContext) {
    /**
     * CSS string interpolator with type-safe value insertion.
     * Uses ToCss typeclass for value conversion.
     */
    inline def css(inline args: Any*): Css = 
      ${ CssInterpolatorMacros.cssImpl('args, 'sc) }
    
    /**
     * CSS selector interpolator.
     * Uses ToSelector typeclass for value conversion.
     */
    inline def selector(inline args: Any*): CssSelector = 
      ${ CssInterpolatorMacros.selectorImpl('args, 'sc) }
  }
}

// Updated JsInterpolator  
trait JsInterpolator {
  extension(inline sc: StringContext) {
    /**
     * JavaScript string interpolator with XSS protection.
     * Uses ToJs typeclass for safe value conversion.
     */
    inline def js(inline args: Any*): Js = 
      ${ JsInterpolatorMacros.jsImpl('args, 'sc) }
  }
}
```

---

### 4.7 Macro Implementation (Scala 3)

```scala
package zio.http.template2

import scala.quoted.*

object HtmlInterpolatorMacros {
  
  def htmlImpl(args: Expr[Seq[Any]], sc: Expr[StringContext])(using Quotes): Expr[Dom] = {
    import quotes.reflect.*
    
    sc.value match {
      case Some(stringContext) =>
        val parts = stringContext.parts.toList
        
        // Parse HTML structure at compile time
        val htmlStructure = parseHtml(parts)
        
        // Validate structure
        htmlStructure match {
          case Left(error) => 
            report.errorAndAbort(s"Invalid HTML: $error")
          case Right(ast) =>
            // Generate code using the appropriate typeclass per context
            generateDomCode(ast, args)
        }
        
      case None =>
        report.errorAndAbort("html interpolator requires a literal string")
    }
  }
  
  private def generateDomCode(ast: HtmlAst, args: Expr[Seq[Any]])(using Quotes): Expr[Dom] = {
    import quotes.reflect.*
    
    ast match {
      case HtmlAst.Element(tag, attrs, children) =>
        val attrExprs = attrs.map {
          case HtmlAst.StaticAttr(name, value) =>
            '{ Dom.attr(${ Expr(name) }, ${ Expr(value) }) }
            
          case HtmlAst.InterpolatedAttr(name, argIndex) =>
            // ATTRIBUTE CONTEXT: Use ToHtmlAttribute
            '{
              val value = $args(${ Expr(argIndex) })
              val encoder = summon[ToHtmlAttribute[value.type]]
              Dom.CompleteAttribute(
                ${ Expr(name) }, 
                encoder.toHtmlAttribute(value)
              )
            }
        }
        
        val childExprs = children.map {
          case HtmlAst.TextNode(text) =>
            '{ Dom.Text(${ Expr(text) }) }
            
          case HtmlAst.InterpolatedNode(argIndex) =>
            // TEXT CONTEXT: Use ToHtml
            '{
              val value = $args(${ Expr(argIndex) })
              val encoder = summon[ToHtml[value.type]]
              encoder.toHtml(value)
            }
            
          case nested: HtmlAst.Element =>
            generateDomCode(nested, args)
        }
        
        '{ 
          Dom.element(${ Expr(tag) })
            .addAttributes(${ Expr.ofSeq(attrExprs) })
            .addChildren(${ Expr.ofSeq(childExprs) })
        }
        
      case HtmlAst.Fragment(children) =>
        val childExprs = children.map(c => generateDomCode(c, args))
        '{ Dom.Fragment(${ Expr.ofSeq(childExprs) }.toVector) }
    }
  }
  
  // ═══════════════════════════════════════════════════════════════
  // HTML AST
  // ═══════════════════════════════════════════════════════════════
  
  private sealed trait HtmlAst
  private object HtmlAst {
    case class Element(tag: String, attrs: List[Attr], children: List[HtmlAst]) extends HtmlAst
    case class TextNode(text: String) extends HtmlAst
    case class InterpolatedNode(argIndex: Int) extends HtmlAst
    case class Fragment(children: List[HtmlAst]) extends HtmlAst
    
    sealed trait Attr
    case class StaticAttr(name: String, value: String) extends Attr
    case class InterpolatedAttr(name: String, argIndex: Int) extends Attr
  }
  
  // ═══════════════════════════════════════════════════════════════
  // HTML Parser (simplified)
  // ═══════════════════════════════════════════════════════════════
  
  private def parseHtml(parts: List[String]): Either[String, HtmlAst] = {
    // Parse HTML with placeholders marked by special tokens
    // Each ${} in the original becomes a boundary between parts
    // 
    // Example: html"<div class=$cls>$content</div>"
    // parts = List("<div class=", ">", "</div>")
    // 
    // Parser must:
    // 1. Track if we're in a tag, attribute, or text context
    // 2. Record interpolation point indices and their contexts
    // 3. Build the AST with proper structure
    // 4. Validate matching open/close tags
    // 5. Reject dynamic tag names and attribute names
    ???
  }
}
```

---

### 4.8 Usage Examples

```scala
import zio.http.template2._

// ═══════════════════════════════════════════════════════════════
// HTML Interpolator: ToHtml (text) vs ToHtmlAttribute (attributes)
// ═══════════════════════════════════════════════════════════════

// TEXT CONTEXT - uses ToHtml
val userName = "<script>evil</script>"  // XSS attempt
val page = html"<div>Hello, $userName!</div>"
// Renders: <div>Hello, &lt;script&gt;evil&lt;/script&gt;!</div>
// ToHtml[String] is used → value becomes Dom.Text → escaped on render

// ATTRIBUTE CONTEXT - uses ToHtmlAttribute
val cssClass = "container active"
val userId = 42
val box = html"<div class=$cssClass id=$userId>Content</div>"
// Renders: <div class="container active" id="42">Content</div>
// ToHtmlAttribute[String] → StringValue
// ToHtmlAttribute[Int] → StringValue("42")

// Js is allowed in attributes (onclick) but NOT in text
val handler = js"handleClick(this)"
val button = html"<button onclick=$handler>Click</button>"
// Renders: <button onclick="handleClick(this)">Click</button>
// ToHtmlAttribute[Js] exists → works fine

// Multi-value attributes with collections
val classes = List("btn", "btn-primary", "large")
val styledButton = html"<button class=$classes>Submit</button>"
// Renders: <button class="btn btn-primary large">Submit</button>
// ToHtmlAttribute[List[String]] → MultiValue with space separator

// Dom values pass through in text context
val header = h1("Welcome")
val content = html"<main>$header<p>Content here</p></main>"

// Lists in text context become fragments
val items = List("Apple", "Banana", "Cherry")
val list = html"<ul>${items.map(i => html"<li>$i</li>")}</ul>"
// ToHtml[List[Dom]] → Dom.Fragment

// Options: None becomes empty in text, omits attribute
val maybeError: Option[String] = None
val maybeClass: Option[String] = Some("error")
val form = html"<form class=$maybeClass>$maybeError<input/></form>"
// Renders: <form class="error"><input/></form>
// Text: Option[String] None → Dom.Empty
// Attr: Option[String] Some → StringValue

// ═══════════════════════════════════════════════════════════════
// CSS Interpolator with ToCss
// ═══════════════════════════════════════════════════════════════

val margin = 10.px
val color = CssColor.hex("ff0000")
val styles = css"margin: $margin; color: $color;"
// Result: Css("margin: 10px; color: #ff0000;")

// CssSelector values work in selectors
val base = div.hover
val fullSelector = selector"$base > p"
// Result: CssSelector.raw("div:hover > p")

// ═══════════════════════════════════════════════════════════════
// JS Interpolator with ToJs
// ═══════════════════════════════════════════════════════════════

val message = "Hello <script>evil</script>"  // Will be escaped
val code = js"alert($message);"
// Result: Js("alert('Hello \\u003cscript\\u003eevil\\u003c/script\\u003e');")

val count = 42
val increment = js"counter = $count + 1;"
// Result: Js("counter = 42 + 1;")

val data = Map("name" -> "Alice", "age" -> 30)
val jsonCode = js"var user = $data;"
// Result: Js("var user = {name: 'Alice', age: 30};")

// ═══════════════════════════════════════════════════════════════
// Custom Typeclass Instances
// ═══════════════════════════════════════════════════════════════

case class User(name: String, email: String)

object User {
  // For text context: render as a DOM fragment
  implicit val toHtml: ToHtml[User] = ToHtml.instance { user =>
    div(className := "user-card")(
      span(className := "name")(user.name),
      span(className := "email")(user.email)
    )
  }
  
  // For attribute context: render as data attribute value
  implicit val toHtmlAttribute: ToHtmlAttribute[User] = 
    ToHtmlAttribute.fromString(u => s"${u.name}|${u.email}")
}

val user = User("Alice", "alice@example.com")

// In text context → uses ToHtml[User]
val profile = html"<section>$user</section>"
// Renders: <section><div class="user-card">...</div></section>

// In attribute context → uses ToHtmlAttribute[User]
val dataDiv = html"<div data-user=$user>Info</div>"
// Renders: <div data-user="Alice|alice@example.com">Info</div>

// ═══════════════════════════════════════════════════════════════
// Type-Directed Context Selection
// ═══════════════════════════════════════════════════════════════

// The macro determines context from HTML structure, not from type:
val value = "test"
html"<div title=$value>$value</div>"
//              ^^^^^^ ToHtmlAttribute[String] - attribute context
//                     ^^^^^^ ToHtml[String] - text context

// Same value, different typeclasses based on position!
```

---

### 4.9 Error Messages

```scala
// ═══════════════════════════════════════════════════════════════
// Missing ToHtml instance (text context)
// ═══════════════════════════════════════════════════════════════

case class CustomType(value: Int)
val custom = CustomType(42)
html"<div>$custom</div>"
// Error: No given instance of type ToHtml[CustomType] was found.
//        
//        To render CustomType in HTML text context, define:
//          given ToHtml[CustomType] = ToHtml.instance { ct =>
//            Dom.Text(ct.value.toString)
//          }

// ═══════════════════════════════════════════════════════════════
// Missing ToHtmlAttribute instance (attribute context)
// ═══════════════════════════════════════════════════════════════

html"<div class=$custom>Content</div>"
// Error: No given instance of type ToHtmlAttribute[CustomType] was found.
//        
//        To use CustomType in HTML attributes, define:
//          given ToHtmlAttribute[CustomType] = 
//            ToHtmlAttribute.fromString(ct => ct.value.toString)

// ═══════════════════════════════════════════════════════════════
// Js in text context (NOT allowed - security)
// ═══════════════════════════════════════════════════════════════

val script = js"alert('hi')"
html"<p>$script</p>"
// Error: No given instance of type ToHtml[Js] was found.
//        
//        Js values cannot be used in HTML text context (XSS risk).
//        Use one of these patterns instead:
//          html"<script>$$script</script>"   // In script element
//          script.inlineJs(script)           // Using DSL
//          html"<button onclick=$$script>"   // In event attribute (OK)

// ═══════════════════════════════════════════════════════════════
// Js in attribute context (allowed)
// ═══════════════════════════════════════════════════════════════

html"<button onclick=$script>Click</button>"
// ✓ Compiles: ToHtmlAttribute[Js] exists for event handlers

// ═══════════════════════════════════════════════════════════════
// Invalid HTML structure
// ═══════════════════════════════════════════════════════════════

html"<div><p>Text</div>"
// Error: Invalid HTML at position 9: tag <p> was not closed.
//        Expected </p> before </div>.

html"<div class=>Content</div>"
// Error: Invalid HTML at position 11: empty attribute value.

html"<$tagName>Content</$tagName>"
// Error: Dynamic tag names are not allowed for security reasons.
//        Use Dom.element(tagName) for dynamic tags.
```

---

### 4.10 Typeclass Summary

| Typeclass | Purpose | Key Instances |
|-----------|---------|---------------|
| **ToHtml[A]** | Text context: `<p>$a</p>` | String, Int, Dom, Option, List |
| **ToHtmlAttribute[A]** | Attribute context: `class=$a` | String, Int, Boolean, Js, URL, List[String] |
| **ToCss[A]** | CSS values: `css"color: $a"` | String, CssLength, CssColor |
| **ToJs[A]** | JavaScript: `js"var x = $a"` | String, Int, Boolean, Map, List |
| **ToSelector[A]** | CSS selectors: `selector"$a > p"` | String, CssSelector |

### Security Matrix

| Scenario | Typeclass | Allowed? | Why |
|----------|-----------|----------|-----|
| String in `<p>$s</p>` | ToHtml | ✓ | Escaped via Dom.Text |
| String in `class=$s` | ToHtmlAttribute | ✓ | Escaped in attribute |
| Js in `<p>$js</p>` | ToHtml | ✗ | No instance (XSS risk) |
| Js in `onclick=$js` | ToHtmlAttribute | ✓ | Valid for event handlers |
| List in `<ul>$items</ul>` | ToHtml | ✓ | Becomes Fragment |
| List in `class=$items` | ToHtmlAttribute | ✓ | Space-separated |
| Dom in `<div>$dom</div>` | ToHtml | ✓ | Pass-through |
| Dynamic tag `<$tag>` | N/A | ✗ | Compile error always |

---

## Part 5: Migration Path

### Phase 1: Add HTML Interpolator (Non-Breaking)
- Add `html""` interpolator as new feature
- No changes to existing DSL
- Users can adopt incrementally

### Phase 2: Add Context-Aware Escaping (Non-Breaking)
- Add `.asJsString`, `.asUrl` methods
- Add `JsString`, `UrlString` opaque types
- Deprecate raw string in `js""` without wrapper

### Phase 3: Performance Optimizations (Non-Breaking)
- Add `renderTo(Appendable)` method
- Optimize internal rendering
- Maintain API compatibility

### Phase 4: Optional Strict Mode (Non-Breaking)
- Add `template2.strict` package with validation
- Document as opt-in feature
- No changes to default behavior

---

## Part 6: Comparison with Alternatives

| Feature | Template2 | ScalaTags | Twirl | Scalatags-rx |
|---------|-----------|-----------|-------|--------------|
| Type-safe elements | Yes | Yes | No | Yes |
| String interpolator | CSS/JS only | No | Yes | No |
| Compile-time validation | Basic | No | Yes | No |
| XSS protection | Partial | Yes | Yes | Yes |
| Reactive support | No | No | No | Yes |
| Zero dependencies | Yes | Yes | No | No |
| CSS DSL | Selector only | Inline only | No | Inline only |

Template2's strengths: Zero-dep, ZIO integration, CSS selector DSL, specialized Script/Style elements.

Template2's gaps: No HTML interpolator, limited compile-time validation, basic escaping.

---

## Summary

Template2 is a solid foundation with good architectural decisions. The main improvements needed are:

### Complete Typeclass Family

**HTML Interpolator (`html"..."`):**

| Typeclass | Position | Output | Security |
|-----------|----------|--------|----------|
| `ToTagName` | `<$tag>` | `String` | **CRITICAL** - whitelist only |
| `ToAttrName` | `$attr=...` | `String` | **HIGH** - Safe/Event split |
| `ToAttrValue` | `=$value` | `AttributeValue` | MEDIUM - escaping |
| `ToElements` | `>$content<` | `Dom` | MEDIUM - HTML escaping |
| `ToText` | text only | `String` | LOW - simple text |

**Other Interpolators:**

| Typeclass | Interpolator | Output | Key Features |
|-----------|--------------|--------|--------------|
| `ToJs` | `js"..."` | `String` | XSS protection, `</script>` escaping |
| `ToCss` | `css"..."` | `String` | CssLength, CssColor, CssTime |

### Position-Based Security Model

```
<$tagName $attrName="$attrValue">
    ↑          ↑          ↑
    │          │          └── ToAttrValue (Js OK, escaped)
    │          └───────────── ToAttrName (whitelist only)
    └──────────────────────── ToTagName (whitelist only)

  $elements
      ↑
      └── ToElements (No Js, HTML escaped)
</$tagName>
```

### Key Security Decisions

| Type | ToTagName | ToAttrName | ToAttrValue | ToElements | ToJs |
|------|:---------:|:----------:|:-----------:|:----------:|:----:|
| `String` | ✗ | ✗ | ✓ | ✓ | ✓ (quoted) |
| `Js` | ✗ | ✗ | ✓ | ✗ | ✓ |
| `SafeTagName` | ✓ | - | - | - | - |
| `SafeAttrName` | - | ✓ | - | - | - |

### Benefits

1. **Position-Specific Types**: Each interpolation position has its own typeclass
2. **Security by Design**: No `String` instances for dangerous positions
3. **Fine-Grained Control**: Users can define exactly what works where
4. **Compile-Time Safety**: Missing instance = compile error
5. **Rich CSS/JS Support**: Type-safe values with `CssLength`, `CssColor`, JS escaping

### Priority Order

| Priority | Feature | Effort |
|----------|---------|--------|
| **P0** | Core typeclasses (ToElements, ToAttrValue, ToText) | MEDIUM |
| **P0** | Security typeclasses (ToTagName, ToAttrName) | MEDIUM |
| **P0** | `html""` interpolator macro | HIGH |
| **P0** | `ToJs` with XSS protection | MEDIUM |
| **P1** | `ToCss` with typed values | MEDIUM |
| **P1** | Performance optimizations | LOW |

### File Structure (Proposed)

```
template2/
├── ToTagName.scala        # Tag name position (most restricted)
├── ToAttrName.scala       # Attribute name position
├── ToAttrValue.scala      # Attribute value position
├── ToElements.scala       # Child elements position
├── ToText.scala           # Text-only rendering
├── ToJs.scala             # JavaScript interpolation
├── ToCss.scala            # CSS interpolation + CssLength, CssColor
├── scala-3/
│   ├── HtmlInterpolator.scala
│   ├── HtmlInterpolatorMacros.scala  # Summons position-specific typeclasses
│   ├── CssInterpolatorMacros.scala   # Uses ToCss
│   └── JsInterpolatorMacros.scala    # Uses ToJs
└── scala-2/
    └── ... (mirror structure)
```

### Usage Example

```scala
import zio.http.template2._

// All four HTML positions in action
val tag = SafeTagName.unsafe("div")
val attr = SafeAttrName.unsafe("data-id") 
val value = 42
val content = List("Hello", "World")

html"<$tag $attr=$value>$content</$tag>"
//     ↑     ↑     ↑      ↑
//     │     │     │      └── ToElements[List[String]]
//     │     │     └───────── ToAttrValue[Int]
//     │     └─────────────── ToAttrName[SafeAttrName]
//     └───────────────────── ToTagName[SafeTagName]

// JS with proper escaping
val userInput = "'; alert('xss'); //"
js"var name = $userInput"
// → var name = '\'; alert(\'xss\'); //'

// CSS with typed values
css"margin: ${10.px}; transition: ${200.ms}"
// → margin: 10px; transition: 200ms
```

This design provides **maximum type safety** with **position-aware security** for HTML templating.
