package zio.blocks.sql.query

import zio.test._
import zio.blocks.schema._
import zio.blocks.sql._

object UpdateSpec extends ZIOSpecDefault {

  case class User(id: Int, name: String, age: Int)
  object User extends CompanionOptics[User] {
    implicit val schema: Schema[User] = Schema.derived
    val id = optic(_.id)
    val name = optic(_.name)
    val age = optic(_.age)
  }

  def spec = suite("UpdateSpec")(
    test("update with set and where") {
      given SqlDialect = SqlDialect.PostgreSQL
      
      val frag = Update.table[User]
        .set(User.name.set("John"))
        .where(User.age > 21)
        .toFrag

      assertTrue(frag.sql(SqlDialect.PostgreSQL) == "UPDATE user SET name = $1 WHERE age > $2") &&
      assertTrue(frag.queryParams == IndexedSeq(DbValue.DbString("John"), DbValue.DbInt(21)))
    }
  )
}
