package zio.blocks.docs

import zio.test._

object ParserErrorSpec extends MarkdownBaseSpec {
  def spec = suite("Parser Errors")(
    suite("Frontmatter rejection")(
      test("rejects YAML frontmatter at start") {
        val input  = "---\ntitle: test\n---\n# Hello"
        val result = Parser.parse(input)
        assertTrue(
          result.isLeft,
          result.swap.toOption.get.message.toLowerCase.contains("frontmatter")
        )
      },
      test("rejects frontmatter with various content") {
        val input  = "---\nkey: value\nother: data\n---\nContent"
        val result = Parser.parse(input)
        assertTrue(result.isLeft)
      },
      test("allows thematic break not at start") {
        val input  = "# Title\n\n---\n\nContent"
        val result = Parser.parse(input)
        assertTrue(result.isRight)
      }
    ),
    suite("Invalid heading level")(
      test("rejects 7 hash heading") {
        val result = Parser.parse("####### Too deep")
        assertTrue(
          result.isLeft,
          result.swap.toOption.get.message.toLowerCase.contains("level")
        )
      },
      test("rejects 8 hash heading") {
        val result = Parser.parse("######## Way too deep")
        assertTrue(result.isLeft)
      },
      test("error includes line number") {
        val result = Parser.parse("####### Invalid")
        val error  = result.swap.toOption.get
        assertTrue(error.line == 1)
      }
    ),
    suite("Unclosed code fence")(
      test("rejects unclosed backtick fence") {
        val result = Parser.parse("```\ncode without closing")
        assertTrue(
          result.isLeft,
          result.swap.toOption.get.message.toLowerCase.contains("unclosed")
        )
      },
      test("rejects unclosed tilde fence") {
        val result = Parser.parse("~~~\ncode without closing")
        assertTrue(result.isLeft)
      },
      test("rejects mismatched fence characters") {
        val result = Parser.parse("```\ncode\n~~~")
        assertTrue(result.isLeft)
      },
      test("error includes position info") {
        val result = Parser.parse("```\nunclosed")
        val error  = result.swap.toOption.get
        assertTrue(
          error.line >= 1,
          error.column >= 1,
          error.input.nonEmpty
        )
      }
    ),
    suite("Error position accuracy")(
      test("reports correct line for error on line 3") {
        val input  = "# Title\n\n####### Invalid"
        val result = Parser.parse(input)
        val error  = result.swap.toOption.get
        assertTrue(error.line == 3)
      },
      test("reports correct column") {
        val result = Parser.parse("####### Invalid")
        val error  = result.swap.toOption.get
        assertTrue(error.column >= 1)
      },
      test("includes offending input in error") {
        val result = Parser.parse("####### Too many hashes")
        val error  = result.swap.toOption.get
        assertTrue(error.input.contains("#######"))
      }
    )
  )
}
