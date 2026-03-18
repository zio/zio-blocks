package zio.blocks.template

import zio.blocks.chunk.Chunk
import zio.test._

object CssAdtSpec extends ZIOSpecDefault {
  def spec = suite("Css ADT")(
    suite("Css.Raw")(
      test("renders raw value verbatim") {
        assertTrue(Css.Raw("color: red;").render == "color: red;")
      },
      test("render(indent) returns same value") {
        assertTrue(Css.Raw("color: red;").render(2) == "color: red;")
      },
      test("stripMargin works") {
        val raw = Css.Raw(
          """|body {
             |  margin: 0;
             |}""".stripMargin
        )
        assertTrue(raw.render == "body {\n  margin: 0;\n}")
      }
    ),
    suite("Css.apply factory")(
      test("Css(string) returns Css.Raw") {
        val result = Css("color: red")
        assertTrue(result == Css.Raw("color: red"))
      }
    ),
    suite("Css.Comment")(
      test("renders minified comment") {
        assertTrue(Css.Comment("reset styles").render == "/*reset styles*/")
      },
      test("renders indented comment with spaces") {
        assertTrue(Css.Comment("reset styles").render(2) == "/* reset styles */")
      }
    ),
    suite("Css.Declaration")(
      test("has property and value") {
        val decl = Css.Declaration("color", "red")
        assertTrue(decl.property == "color", decl.value == "red")
      }
    ),
    suite("Css.Rule")(
      test("renders minified rule") {
        val rule = Css.Rule(
          CssSelector.Element("div"),
          Chunk(Css.Declaration("color", "red"), Css.Declaration("margin", "0"))
        )
        assertTrue(rule.render == "div{color:red;margin:0;}")
      },
      test("renders indented rule") {
        val rule = Css.Rule(
          CssSelector.Element("div"),
          Chunk(Css.Declaration("color", "red"), Css.Declaration("margin", "0"))
        )
        val expected = "div {\n  color: red;\n  margin: 0;\n}"
        assertTrue(rule.render(2) == expected)
      },
      test("render(0) falls back to minified") {
        val rule = Css.Rule(
          CssSelector.Element("p"),
          Chunk(Css.Declaration("font-size", "14px"))
        )
        assertTrue(rule.render(0) == rule.render)
      },
      test("empty declarations renders empty rule") {
        val rule = Css.Rule(CssSelector.Element("div"), Chunk.empty)
        assertTrue(rule.render == "div{}")
      }
    ),
    suite("Css.Sheet")(
      test("renders minified sheet") {
        val sheet = Css.Sheet(
          Chunk(
            Css.Rule(CssSelector.Element("div"), Chunk(Css.Declaration("color", "red"))),
            Css.Rule(CssSelector.Element("p"), Chunk(Css.Declaration("margin", "0")))
          )
        )
        assertTrue(sheet.render == "div{color:red;}p{margin:0;}")
      },
      test("renders indented sheet with newlines between rules") {
        val sheet = Css.Sheet(
          Chunk(
            Css.Rule(CssSelector.Element("div"), Chunk(Css.Declaration("color", "red"))),
            Css.Rule(CssSelector.Element("p"), Chunk(Css.Declaration("margin", "0")))
          )
        )
        val expected = "div {\n  color: red;\n}\np {\n  margin: 0;\n}"
        assertTrue(sheet.render(2) == expected)
      },
      test("render(0) falls back to minified") {
        val sheet = Css.Sheet(
          Chunk(Css.Rule(CssSelector.Element("div"), Chunk(Css.Declaration("color", "red"))))
        )
        assertTrue(sheet.render(0) == sheet.render)
      },
      test("empty sheet renders empty string") {
        val sheet = Css.Sheet(Chunk.empty)
        assertTrue(sheet.render == "")
      }
    ),
    suite("mixed Css types in Sheet")(
      test("sheet can contain Raw, Rule, and Comment") {
        val sheet = Css.Sheet(
          Chunk(
            Css.Comment("reset"),
            Css.Raw("* { box-sizing: border-box; }"),
            Css.Rule(CssSelector.Element("body"), Chunk(Css.Declaration("margin", "0")))
          )
        )
        val rendered = sheet.render
        assertTrue(
          rendered == "/*reset*/* { box-sizing: border-box; }body{margin:0;}"
        )
      }
    )
  )
}
