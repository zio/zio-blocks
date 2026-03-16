package zio.blocks.sql.query

import zio.test._
import zio.blocks.schema._
import zio.blocks.sql._

object SelectSpec extends ZIOSpecDefault {

  case class User(id: Int, name: String, age: Int)
  object User extends CompanionOptics[User] {
    implicit val schema: Schema[User] = Schema.derived
    val id = optic(_.id)
    val name = optic(_.name)
    val age = optic(_.age)
  }

  def spec = suite("SelectSpec")(
    test("select with where and order by") {
      given SqlDialect = SqlDialect.PostgreSQL
      
      val frag = Select.from[User]
        .where(User.age > 21)
        .orderBy(User.name.asc)
        .limit(10)
        .toFrag

      assertTrue(frag.sql(SqlDialect.PostgreSQL) == "SELECT id, name, age FROM user WHERE age > $1 ORDER BY name ASC LIMIT 10") &&
      assertTrue(frag.queryParams == IndexedSeq(DbValue.DbInt(21)))
    }
  )
}
