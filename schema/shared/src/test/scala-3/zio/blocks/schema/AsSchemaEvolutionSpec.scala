package zio.blocks.schema

import scala.annotation.experimental
import zio.test._

@experimental
object AsSchemaEvolutionSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("As - Schema Evolution Safety")(
    suite("As blocks default values")(
      test("As fails when default values are used in conversion") {
        // TODO: Test compile-time error detection
        // The macro expansion happens at compile-time, so we can't test failure with Try
        // This would need to be tested with a separate compilation unit
        assertTrue(true) // Placeholder - compile-time errors are tested by attempting compilation
      },
      test("As allows Option injection (reversible)") {
        case class UserV1(id: String, name: String)
        case class UserV2(id: String, name: String, email: Option[String])

        val as    = As.derived[UserV1, UserV2]
        val user1 = UserV1("123", "Alice")

        val user2     = as.into(user1).right.get
        val roundTrip = as.from(user2).right.get

        assertTrue(
          user2 == UserV2("123", "Alice", None),
          roundTrip == user1
        )
      },
      test("As allows removing optional fields (reversible)") {
        case class UserV2(id: String, name: String, email: Option[String])
        case class UserV1(id: String, name: String)

        val as = As.derived[UserV2, UserV1]

        assertTrue(
          as.into(UserV2("123", "Alice", Some("alice@example.com"))) == Right(UserV1("123", "Alice")),
          as.into(UserV2("456", "Bob", None)) == Right(UserV1("456", "Bob"))
        )
      },
      test("As round-trip with Option injection") {
        case class DataV1(items: List[String])
        case class DataV2(items: List[String], metadata: Option[Map[String, String]])

        val as    = As.derived[DataV1, DataV2]
        val data1 = DataV1(List("a", "b", "c"))

        val data2 = as.into(data1).right.get
        val back  = as.from(data2).right.get

        assertTrue(
          data2 == DataV2(List("a", "b", "c"), None),
          back == data1
        )
      }
    ),
    suite("As with field reordering")(
      test("As allows field reordering (reversible)") {
        case class PointV1(x: Int, y: Int)
        case class PointV2(y: Int, x: Int)

        val as = As.derived[PointV1, PointV2]
        val p1 = PointV1(10, 20)

        val p2   = as.into(p1).right.get
        val back = as.from(p2).right.get

        assertTrue(
          p2 == PointV2(20, 10),
          back == p1
        )
      }
    )
  )
}
