package zio.blocks.template

import zio.test._

object OptimizationEquivalenceSpec extends ZIOSpecDefault {
  def spec = suite("Optimization Equivalence")(
    suite("optimizedApply vs runtimeApply")(
      test("div with single string child") {
        val optimized = div.optimizedApply("hello").render
        val runtime   = div.runtimeApply("hello").render
        assertTrue(optimized == runtime)
      },
      test("div with single attribute") {
        val optimized = div.optimizedApply(id := "main").render
        val runtime   = div.runtimeApply(id := "main").render
        assertTrue(optimized == runtime)
      },
      test("div with multiple attributes and text") {
        val optimized = div.optimizedApply(id := "main", className := "box", "text").render
        val runtime   = div.runtimeApply(id := "main", className := "box", "text").render
        assertTrue(optimized == runtime)
      },
      test("nested elements") {
        val innerOpt  = p.optimizedApply("inner")
        val innerRt   = p.runtimeApply("inner")
        val optimized = div.optimizedApply(innerOpt).render
        val runtime   = div.runtimeApply(innerRt).render
        assertTrue(optimized == runtime)
      },
      test("multiple children in ul/li") {
        val li1Opt    = li.optimizedApply("one")
        val li2Opt    = li.optimizedApply("two")
        val li1Rt     = li.runtimeApply("one")
        val li2Rt     = li.runtimeApply("two")
        val optimized = ul.optimizedApply(li1Opt, li2Opt).render
        val runtime   = ul.runtimeApply(li1Rt, li2Rt).render
        assertTrue(optimized == runtime)
      },
      test("void element br with className") {
        val optimized = br.optimizedApply(className := "spacer").render
        val runtime   = br.runtimeApply(className := "spacer").render
        assertTrue(optimized == runtime)
      },
      test("void element hr with id") {
        val optimized = hr.optimizedApply(id := "separator").render
        val runtime   = hr.runtimeApply(id := "separator").render
        assertTrue(optimized == runtime)
      },
      test("img with src attribute") {
        val optimized = img.optimizedApply(src := "x.png").render
        val runtime   = img.runtimeApply(src := "x.png").render
        assertTrue(optimized == runtime)
      },
      test("img with multiple attributes") {
        val optimized = img.optimizedApply(src := "photo.jpg", alt := "A photo").render
        val runtime   = img.runtimeApply(src := "photo.jpg", alt := "A photo").render
        assertTrue(optimized == runtime)
      },
      test("span with string child") {
        val optimized = span.optimizedApply("inline text").render
        val runtime   = span.runtimeApply("inline text").render
        assertTrue(optimized == runtime)
      },
      test("h1 with string child") {
        val optimized = h1.optimizedApply("Title").render
        val runtime   = h1.runtimeApply("Title").render
        assertTrue(optimized == runtime)
      },
      test("a with href and text") {
        val optimized = a.optimizedApply(href := "https://example.com", "link").render
        val runtime   = a.runtimeApply(href := "https://example.com", "link").render
        assertTrue(optimized == runtime)
      },
      test("deeply nested elements") {
        val innerLi   = li.optimizedApply("item")
        val innerUl   = ul.optimizedApply(innerLi)
        val optimized = div.optimizedApply(innerUl).render
        val innerLiR  = li.runtimeApply("item")
        val innerUlR  = ul.runtimeApply(innerLiR)
        val runtime   = div.runtimeApply(innerUlR).render
        assertTrue(optimized == runtime)
      },
      test("input with type attribute") {
        val optimized = input.optimizedApply(`type` := "text").render
        val runtime   = input.runtimeApply(`type` := "text").render
        assertTrue(optimized == runtime)
      },
      test("table structure") {
        val cell      = td.optimizedApply("data")
        val row       = tr.optimizedApply(cell)
        val optimized = table.optimizedApply(row).render
        val cellR     = td.runtimeApply("data")
        val rowR      = tr.runtimeApply(cellR)
        val runtime   = table.runtimeApply(rowR).render
        assertTrue(optimized == runtime)
      },
      test("form with action attribute and children") {
        val btn       = button.optimizedApply("Submit")
        val optimized = form.optimizedApply(action := "/submit", btn).render
        val btnR      = button.runtimeApply("Submit")
        val runtime   = form.runtimeApply(action := "/submit", btnR).render
        assertTrue(optimized == runtime)
      },
      test("element with boolean attribute") {
        val optimized = input.optimizedApply(disabled).render
        val runtime   = input.runtimeApply(disabled).render
        assertTrue(optimized == runtime)
      },
      test("multiple string children") {
        val optimized = p.optimizedApply("hello ", "world").render
        val runtime   = p.runtimeApply("hello ", "world").render
        assertTrue(optimized == runtime)
      }
    ),
    suite("constant-folded interpolators")(
      test("css literal matches Css.Raw") {
        val interpolated = css"margin: 10px".render
        val direct       = Css.Raw("margin: 10px").render
        assertTrue(interpolated == direct)
      },
      test("css multi-property literal matches Css.Raw") {
        val interpolated = css"color: red; font-size: 14px".render
        val direct       = Css.Raw("color: red; font-size: 14px").render
        assertTrue(interpolated == direct)
      },
      test("js literal matches Js constructor") {
        val interpolated = js"console.log('hi')".value
        val direct       = Js("console.log('hi')").value
        assertTrue(interpolated == direct)
      },
      test("js multi-statement literal matches Js constructor") {
        val interpolated = js"var x = 1; var y = 2".value
        val direct       = Js("var x = 1; var y = 2").value
        assertTrue(interpolated == direct)
      },
      test("selector literal matches CssSelector.Raw") {
        val interpolated = selector".my-class".render
        val direct       = CssSelector.Raw(".my-class").render
        assertTrue(interpolated == direct)
      },
      test("selector element literal matches CssSelector.Raw") {
        val interpolated = selector"div.active".render
        val direct       = CssSelector.Raw("div.active").render
        assertTrue(interpolated == direct)
      },
      test("selector id literal matches CssSelector.Raw") {
        val interpolated = selector"#main-content".render
        val direct       = CssSelector.Raw("#main-content").render
        assertTrue(interpolated == direct)
      }
    )
  )
}
