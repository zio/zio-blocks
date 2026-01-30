/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.markdown

import zio.test._

object RoundTripSpec extends ZIOSpecDefault {

  def spec = suite("RoundTripSpec")(
    headingRoundTripSpec,
    paragraphRoundTripSpec,
    codeBlockRoundTripSpec,
    listRoundTripSpec,
    blockQuoteRoundTripSpec,
    tableRoundTripSpec,
    inlineRoundTripSpec,
    complexRoundTripSpec
  )

  private def roundTrip(input: String): TestResult = {
    val doc1     = Parser.parseUnsafe(input)
    val rendered = Renderer.render(doc1)
    val doc2     = Parser.parseUnsafe(rendered)
    assertTrue(doc1.plainText == doc2.plainText)
  }

  private def exactRoundTrip(input: String): TestResult = {
    val doc1     = Parser.parseUnsafe(input)
    val rendered = Renderer.render(doc1)
    val doc2     = Parser.parseUnsafe(rendered)
    assertTrue(doc1 == doc2)
  }

  // Round-trip with normalized whitespace comparison (for edge cases)
  private def normalizedRoundTrip(input: String): TestResult = {
    val doc1       = Parser.parseUnsafe(input)
    val rendered   = Renderer.render(doc1)
    val doc2       = Parser.parseUnsafe(rendered)
    val normalize  = (s: String) => s.replaceAll("\\s+", " ").trim
    assertTrue(normalize(doc1.plainText) == normalize(doc2.plainText))
  }

  val headingRoundTripSpec = suite("Heading Round-Trip")(
    test("H1")                  { roundTrip("# Heading 1") },
    test("H2")                  { roundTrip("## Heading 2") },
    test("H3")                  { roundTrip("### Heading 3") },
    test("H4")                  { roundTrip("#### Heading 4") },
    test("H5")                  { roundTrip("##### Heading 5") },
    test("H6")                  { roundTrip("###### Heading 6") },
    test("heading with emphasis") { roundTrip("# *Emphasized* Heading") },
    test("heading with strong") { roundTrip("# **Strong** Heading") },
    test("heading with code")   { roundTrip("# `Code` Heading") },
    test("heading with link")   { roundTrip("# [Link](url) Heading") }
  )

  val paragraphRoundTripSpec = suite("Paragraph Round-Trip")(
    test("simple paragraph")      { roundTrip("Hello world") },
    test("multiline paragraph")   { roundTrip("Line 1\nLine 2") },
    test("multiple paragraphs")   { roundTrip("Para 1\n\nPara 2") },
    test("paragraph with formatting") { roundTrip("Hello *world* and **bold**") },
    test("paragraph with link")   { roundTrip("Visit [example](https://example.com)") },
    test("paragraph with image")  { roundTrip("![alt](image.png)") },
    test("paragraph with code")   { roundTrip("Use `code` here") },
    test("paragraph with all formatting") {
      roundTrip("*em* **strong** ~~strike~~ `code` [link](url)")
    }
  )

  val codeBlockRoundTripSpec = suite("Code Block Round-Trip")(
    test("simple code block")   { roundTrip("```\ncode\n```") },
    test("code block with lang") { roundTrip("```scala\nval x = 1\n```") },
    test("multiline code block") { roundTrip("```\nline 1\nline 2\nline 3\n```") },
    test("code block with blank lines") { roundTrip("```\nline 1\n\nline 3\n```") },
    test("code block with special chars") { roundTrip("```\n<html>&amp;</html>\n```") },
    test("code with backticks in content") { roundTrip("```\nuse `code` here\n```") }
  )

  val listRoundTripSpec = suite("List Round-Trip")(
    test("simple bullet list")   { roundTrip("- item 1\n- item 2") },
    test("simple ordered list")  { roundTrip("1. first\n2. second") },
    test("nested bullet list")   { roundTrip("- outer\n  - inner") },
    test("nested ordered list")  { roundTrip("1. outer\n   1. inner") },
    test("task list checked")    { roundTrip("- [x] done") },
    test("task list unchecked")  { roundTrip("- [ ] todo") },
    test("mixed task list")      { roundTrip("- [x] done\n- [ ] todo\n- regular") },
    test("list with formatting") { roundTrip("- *emphasis*\n- **strong**") },
    test("list with code")       { roundTrip("- `code`\n- more") },
    test("list with multiple paragraphs") { normalizedRoundTrip("- para 1\n\n  para 2") }
  )

  val blockQuoteRoundTripSpec = suite("BlockQuote Round-Trip")(
    test("simple block quote")   { roundTrip("> quoted") },
    test("multiline block quote") { roundTrip("> line 1\n> line 2") },
    test("nested block quote")   { roundTrip("> > nested") },
    test("block quote with heading") { roundTrip("> # Heading") },
    test("block quote with list") { roundTrip("> - item") },
    test("block quote with code") { roundTrip("> `code`") },
    test("block quote multiple paragraphs") { roundTrip("> para 1\n>\n> para 2") }
  )

  val tableRoundTripSpec = suite("Table Round-Trip")(
    test("simple table") {
      roundTrip("| A | B |\n|---|---|\n| 1 | 2 |")
    },
    test("table with alignment") {
      roundTrip("| L | C | R |\n|:---|:---:|---:|\n| l | c | r |")
    },
    test("table with formatting") {
      roundTrip("| **Bold** | *Italic* |\n|---|---|\n| `code` | text |")
    },
    test("table with multiple rows") {
      roundTrip("| A | B |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |")
    },
    test("table header only") {
      roundTrip("| A | B |\n|---|---|")
    }
  )

  val inlineRoundTripSpec = suite("Inline Round-Trip")(
    test("emphasis asterisk")    { roundTrip("*emphasis*") },
    test("emphasis underscore")  { roundTrip("_emphasis_") },
    test("strong asterisk")      { roundTrip("**strong**") },
    test("strong underscore")    { roundTrip("__strong__") },
    test("strikethrough")        { roundTrip("~~deleted~~") },
    test("inline code")          { roundTrip("`code`") },
    test("inline code with backticks") { roundTrip("`` `code` ``") },
    test("link")                 { roundTrip("[text](url)") },
    test("link with title")      { roundTrip("[text](url \"title\")") },
    test("image")                { roundTrip("![alt](url)") },
    test("image with title")     { roundTrip("![alt](url \"title\")") },
    test("autolink URL")         { roundTrip("<https://example.com>") },
    test("autolink email")       { roundTrip("<test@example.com>") },
    test("hard break")           { roundTrip("line 1  \nline 2") },
    test("nested emphasis")      { roundTrip("***bold italic***") },
    test("combined formatting")  { roundTrip("**bold *and italic* text**") }
  )

  val complexRoundTripSpec = suite("Complex Round-Trip")(
    test("document with all elements") {
      roundTrip(
        """# Heading
          |
          |Paragraph with *emphasis* and **strong**.
          |
          |> Block quote
          |
          |- List item 1
          |- List item 2
          |
          |```scala
          |val x = 1
          |```
          |
          || A | B |
          ||---|---|
          || 1 | 2 |
          |""".stripMargin
      )
    },
    test("nested structures") {
      roundTrip(
        """> - nested list in quote
          |>   - deeper
          |>
          |> ```
          |> code in quote
          |> ```
          |""".stripMargin
      )
    },
    test("multiple headings and paragraphs") {
      roundTrip(
        """# H1
          |
          |Para 1
          |
          |## H2
          |
          |Para 2
          |
          |### H3
          |
          |Para 3
          |""".stripMargin
      )
    },
    test("links and images mixed") {
      roundTrip("[![Image](img.png)](url) and [link](url2)")
    },
    test("code in various contexts") {
      roundTrip(
        """# `Code` in heading
          |
          |`Code` in paragraph
          |
          |- `Code` in list
          |
          |> `Code` in quote
          |""".stripMargin
      )
    }
  )
}
