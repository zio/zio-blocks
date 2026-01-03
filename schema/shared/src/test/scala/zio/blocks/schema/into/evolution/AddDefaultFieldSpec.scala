package zio.blocks.schema.into.evolution

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for schema evolution with default values. */
object AddDefaultFieldSpec extends ZIOSpecDefault {

  case class PersonV1(name: String)
  case class PersonV2(name: String, age: Int = 0)

  case class ConfigV1(key: String)
  case class ConfigV2(key: String, timeout: Int = 30, retries: Int = 3)

  def spec: Spec[TestEnvironment, Any] = suite("AddDefaultFieldSpec")(
    test("uses default value for missing field") {
      val result = Into.derived[PersonV1, PersonV2].into(PersonV1("Alice"))
      assert(result)(isRight(equalTo(PersonV2("Alice", 0))))
    },
    test("uses multiple defaults for missing fields") {
      val result = Into.derived[ConfigV1, ConfigV2].into(ConfigV1("database"))
      assert(result)(isRight(equalTo(ConfigV2("database", 30, 3))))
    },
    test("source value overrides default") {
      case class SourceWithAge(name: String, age: Int)
      val result = Into.derived[SourceWithAge, PersonV2].into(SourceWithAge("Charlie", 25))
      assert(result)(isRight(equalTo(PersonV2("Charlie", 25))))
    }
  )
}
