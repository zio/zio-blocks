package zio.blocks.docs.frontmatter.yaml

import zio.blocks.docs.ParseError
import zio.blocks.schema.yaml.Yaml
import zio.durationInt
import zio.test.*

object DocWithYamlFrontmatterSpec extends ZIOSpecDefault {
  override def aspects: zio.Chunk[TestAspectAtLeastR[TestEnvironment]] =
    if (TestPlatform.isJVM) zio.Chunk(TestAspect.timeout(120.seconds), TestAspect.timed)
    else zio.Chunk(TestAspect.timeout(120.seconds), TestAspect.timed, TestAspect.sequential, TestAspect.size(10))

  def roundTrip(input: String): TestResult = {
    val parsed: Either[ParseError, DocWithYamlFrontmatter] = Parser.parse(input)
    val rendered: Either[ParseError, String] = parsed.map(Renderer.render)
    val reparsed: Either[ParseError, DocWithYamlFrontmatter] = rendered.flatMap(Parser.parse)
    assertTrue(parsed == reparsed)
  }

  def error(input: String, verifier: ParseError => Boolean): TestResult = {
    val parsed = Parser.parse(input)
    assertTrue(
      parsed.isLeft,
      verifier(parsed.swap.toOption.get)
    )
  }

  override def spec = suite("DocWithYamlFrontmatter")(
    test("round-trip without frontmatter") {
      roundTrip("# Hello\n")
    },
    test("round-trip with frontmatter") {
      roundTrip(
        """---
          |title: Hello
          |date: 2026-03-22
          |tags: [yaml, markdown, test]
          |---
          |# Hello
          |""".stripMargin
      )
    },
    test("frontmatter keys") {
      val parsed: Either[ParseError, DocWithYamlFrontmatter] = Parser.parse(
        """---
          |title: Hello
          |date: 2026-03-22
          |tags: [yaml, markdown, test]
          |---
          |# Hello
          |""".stripMargin
      )
      assertTrue(
        parsed.isRight,
        parsed.toOption.get.frontmatterKey("title").isDefined,
        parsed.toOption.get.frontmatterKey("title").get match {
          case Yaml.Scalar("Hello", _) => true
          case _ => false
        }
      )
    },
    test("malformed Markdown without frontmatter") {
      error("####### Too deep", _.line == 1)
    },
    test("malformed Markdown with frontmatter") {
      error(
        """---
          |title: Hello
          |date: 2026-03-22
          |tags: [yaml, markdown, test]
          |---
          |####### Too deep
          |""".stripMargin,
        _.line == 6
      )
    },
    test("frontmatter must be a mapping") {
      error(
        """---
          |[yaml, markdown, test]
          |---
          |# Hello
          |""".stripMargin,
        _.message.contains("must be a mapping")
      )
    }
  )
}
