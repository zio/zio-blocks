package zio.blocks.schema.into.product

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for case class to case class conversions. */
object CaseClassToCaseClassSpec extends ZIOSpecDefault {

  case class PersonA(name: String, age: Int)
  case class PersonB(name: String, age: Int)

  case class ConfigV1(timeout: Int, retries: Int)
  case class ConfigV2(timeout: Long, retries: Long)

  case class SourceWithName(name: String)
  case class TargetWithDefault(name: String, count: Int = 0)

  case class SourceUnique(name: String, age: Int, active: Boolean)
  case class TargetUnique(username: String, yearsOld: Int, enabled: Boolean)

  def spec: Spec[TestEnvironment, Any] = suite("CaseClassToCaseClassSpec")(
    test("converts when field names and types match exactly") {
      val result = Into.derived[PersonA, PersonB].into(PersonA("Alice", 30))
      assert(result)(isRight(equalTo(PersonB("Alice", 30))))
    },
    test("maps fields by unique type when names differ") {
      val result = Into.derived[SourceUnique, TargetUnique].into(SourceUnique("Alice", 30, true))
      assert(result)(isRight(equalTo(TargetUnique("Alice", 30, true))))
    },
    test("applies type coercion (Int to Long)") {
      val result = Into.derived[ConfigV1, ConfigV2].into(ConfigV1(30, 3))
      assert(result)(isRight(equalTo(ConfigV2(30L, 3L))))
    },
    test("uses default value for missing field") {
      val result = Into.derived[SourceWithName, TargetWithDefault].into(SourceWithName("test"))
      assert(result)(isRight(equalTo(TargetWithDefault("test", 0))))
    }
  )
}
