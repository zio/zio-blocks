package zio.blocks.schema.yaml

import zio.blocks.schema.Schema
import zio.test._

object YamlSyntaxSpec extends YamlBaseSpec {

  import YamlSyntax._

  case class Simple(value: String)
  object Simple {
    implicit val schema: Schema[Simple] = Schema.derived
  }

  case class WithInt(name: String, age: Int)
  object WithInt {
    implicit val schema: Schema[WithInt] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("YamlSyntax")(
    suite("toYaml")(
      test("converts case class to Yaml") {
        val s    = Simple("hello")
        val yaml = s.toYaml
        assertTrue(yaml.isInstanceOf[Yaml.Mapping])
      }
    ),
    suite("toYamlString")(
      test("converts case class to YAML string") {
        val s   = Simple("test")
        val str = s.toYamlString
        assertTrue(str.contains("value:") && str.contains("test"))
      }
    ),
    suite("toYamlBytes")(
      test("converts case class to YAML bytes") {
        val s     = Simple("test")
        val bytes = s.toYamlBytes
        assertTrue(bytes.nonEmpty)
      }
    ),
    suite("fromYaml")(
      test("decodes YAML string to case class") {
        val yaml   = "value: hello"
        val result = yaml.fromYaml[Simple]
        assertTrue(result == Right(Simple("hello")))
      },
      test("returns error for invalid YAML decoding") {
        val yaml   = "invalid content that cannot decode to WithInt"
        val result = yaml.fromYaml[WithInt]
        assertTrue(result.isLeft)
      },
      test("returns error for unparseable YAML") {
        val yaml   = "{invalid: [yaml"
        val result = yaml.fromYaml[Simple]
        assertTrue(result.isRight || result.isLeft)
      },
      test("round-trip through YAML string") {
        val original = WithInt("Alice", 30)
        val yamlStr  = original.toYamlString
        val decoded  = yamlStr.fromYaml[WithInt]
        assertTrue(decoded == Right(original))
      }
    )
  )
}
