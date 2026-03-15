package zio.blocks.schema.binding

import zio.Scope
import zio.test.Assertion._
import zio.test._

import java.util.UUID

object RegisterTypeSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] = suite("RegisterTypeSpec")(
    suite("RegisterType.Object")(
      test("has consistent hashCode and equals") {
        assert(RegisterType.Object[String]())(equalTo(RegisterType.Object[String]())) &&
        assert(RegisterType.Object[String]().hashCode)(equalTo(RegisterType.Object[String]().hashCode)) &&
        assert(RegisterType.Object[UUID](): Any)(equalTo(RegisterType.Object[String]())) &&
        assert(RegisterType.Object[UUID]().hashCode)(equalTo(RegisterType.Object[String]().hashCode)) &&
        assert(RegisterType.Double: Any)(not(equalTo(RegisterType.Object[String]()))) &&
        assert(RegisterType.Unit: Any)(not(equalTo(RegisterType.Object[String]()))) &&
        assert(RegisterType.Int: Any)(not(equalTo(RegisterType.Object[String]())))
      }
    )
  )
}
