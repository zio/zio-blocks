package zio.blocks.template

import zio.test._

object SafeNameSpec extends ZIOSpecDefault {
  def spec = suite("SafeNames")(
    suite("SafeTagName")(
      test("allows standard tags") {
        assertTrue(
          SafeTagName("div").isDefined,
          SafeTagName("span").isDefined,
          SafeTagName("p").isDefined,
          SafeTagName("a").isDefined,
          SafeTagName("ul").isDefined,
          SafeTagName("li").isDefined
        )
      },
      test("blocks script tag") {
        assertTrue(SafeTagName("script").isEmpty)
      },
      test("blocks style tag") {
        assertTrue(SafeTagName("style").isEmpty)
      },
      test("blocks unknown tags") {
        assertTrue(
          SafeTagName("INVALID").isEmpty,
          SafeTagName("custom-element").isEmpty,
          SafeTagName("xyz").isEmpty
        )
      },
      test("case-insensitive: uppercase becomes lowercase") {
        val result = SafeTagName("DIV")
        assertTrue(result.isDefined, result.get.value == "div")
      },
      test("case-insensitive: mixed case") {
        val result = SafeTagName("Span")
        assertTrue(result.isDefined, result.get.value == "span")
      },
      test("unsafe creates without validation") {
        assertTrue(SafeTagName.unsafe("anything").value == "anything")
      },
      test("extracts correct value") {
        assertTrue(SafeTagName("div").get.value == "div")
      }
    ),
    suite("SafeAttrName")(
      test("allows standard attributes") {
        assertTrue(
          SafeAttrName("id").isDefined,
          SafeAttrName("class").isDefined,
          SafeAttrName("style").isDefined,
          SafeAttrName("href").isDefined,
          SafeAttrName("src").isDefined
        )
      },
      test("blocks event handler attributes") {
        assertTrue(
          SafeAttrName("onclick").isEmpty,
          SafeAttrName("onmouseover").isEmpty,
          SafeAttrName("onload").isEmpty,
          SafeAttrName("onerror").isEmpty
        )
      },
      test("allows data- attributes") {
        assertTrue(
          SafeAttrName("data-custom").isDefined,
          SafeAttrName("data-id").isDefined,
          SafeAttrName("data-value-123").isDefined
        )
      },
      test("allows aria- attributes") {
        assertTrue(
          SafeAttrName("aria-label").isDefined,
          SafeAttrName("aria-hidden").isDefined,
          SafeAttrName("aria-describedby").isDefined
        )
      },
      test("blocks invalid data- attributes") {
        assertTrue(SafeAttrName("data-").isEmpty)
      },
      test("blocks invalid aria- attributes") {
        assertTrue(SafeAttrName("aria-").isEmpty)
      },
      test("blocks unknown attributes") {
        assertTrue(
          SafeAttrName("xyzattr").isEmpty,
          SafeAttrName("custom").isEmpty
        )
      },
      test("case-insensitive") {
        assertTrue(SafeAttrName("ID").isDefined, SafeAttrName("ID").get.value == "id")
      },
      test("unsafe creates without validation") {
        assertTrue(SafeAttrName.unsafe("anything").value == "anything")
      }
    ),
    suite("EventAttrName")(
      test("allows event attributes") {
        assertTrue(
          EventAttrName("onclick").isDefined,
          EventAttrName("onmouseover").isDefined,
          EventAttrName("onsubmit").isDefined,
          EventAttrName("onkeydown").isDefined,
          EventAttrName("onfocus").isDefined
        )
      },
      test("blocks non-event attributes") {
        assertTrue(
          EventAttrName("id").isEmpty,
          EventAttrName("class").isEmpty,
          EventAttrName("href").isEmpty
        )
      },
      test("case-insensitive") {
        assertTrue(EventAttrName("ONCLICK").isDefined, EventAttrName("ONCLICK").get.value == "onclick")
      },
      test("blocks unknown event names") {
        assertTrue(
          EventAttrName("onfake").isEmpty,
          EventAttrName("oncustom").isEmpty
        )
      },
      test("unsafe creates without validation") {
        assertTrue(EventAttrName.unsafe("anything").value == "anything")
      }
    )
  )
}
