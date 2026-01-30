package zio.blocks.schema.into.product

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for field renaming with unique type matching. */
object FieldRenamingSpec extends ZIOSpecDefault {

  case class PersonV1(fullName: String, yearOfBirth: Int)
  case class PersonV2(name: String, birthYear: Int)

  def spec: Spec[TestEnvironment, Any] = suite("FieldRenamingSpec")(
    test("maps renamed fields by unique type") {
      val result = Into.derived[PersonV1, PersonV2].into(PersonV1("Alice Smith", 1990))
      assert(result)(isRight(equalTo(PersonV2("Alice Smith", 1990))))
    },
    test("partial renaming - some by name, others by type") {
      case class Source(hostName: String, portNumber: Int, timeout: Long)
      case class Target(host: String, port: Int, timeout: Long)

      val result = Into.derived[Source, Target].into(Source("localhost", 8080, 5000L))
      assert(result)(isRight(equalTo(Target("localhost", 8080, 5000L))))
    },
    test("maps renamed fields with coercion") {
      case class Source(identifier: Int, label: String)
      case class Target(id: Long, name: String)

      val result = Into.derived[Source, Target].into(Source(1, "test"))
      assert(result)(isRight(equalTo(Target(1L, "test"))))
    }
  )
}
