package zio.blocks.sql

import zio.test._

object SqlNameMapperSpec extends ZIOSpecDefault {

  def spec = suite("SqlNameMapperSpec")(
    suite("SnakeCase")(
      test("camelCase to snake_case") {
        assertTrue(SqlNameMapper.SnakeCase("firstName") == "first_name")
      },
      test("PascalCase to snake_case") {
        assertTrue(SqlNameMapper.SnakeCase("FirstName") == "first_name")
      },
      test("already snake_case unchanged") {
        assertTrue(SqlNameMapper.SnakeCase("first_name") == "first_name")
      },
      test("ID suffix handled correctly") {
        assertTrue(SqlNameMapper.SnakeCase("userID") == "user_id")
      },
      test("consecutive capitals") {
        assertTrue(SqlNameMapper.SnakeCase("HTTPResponse") == "http_response")
      },
      test("single word lowercase unchanged") {
        assertTrue(SqlNameMapper.SnakeCase("name") == "name")
      },
      test("single word uppercase to lowercase") {
        assertTrue(SqlNameMapper.SnakeCase("Name") == "name")
      },
      test("empty string") {
        assertTrue(SqlNameMapper.SnakeCase("") == "")
      },
      test("kebab-case converted to snake_case") {
        assertTrue(SqlNameMapper.SnakeCase("first-name") == "first_name")
      },
      test("mixed separators") {
        assertTrue(SqlNameMapper.SnakeCase("first_name-value") == "first_name_value")
      },
      test("numbers in name") {
        assertTrue(SqlNameMapper.SnakeCase("field2Name") == "field2_name")
      }
    ),
    suite("Identity")(
      test("camelCase unchanged") {
        assertTrue(SqlNameMapper.Identity("firstName") == "firstName")
      },
      test("PascalCase unchanged") {
        assertTrue(SqlNameMapper.Identity("FirstName") == "FirstName")
      },
      test("snake_case unchanged") {
        assertTrue(SqlNameMapper.Identity("first_name") == "first_name")
      },
      test("empty string") {
        assertTrue(SqlNameMapper.Identity("") == "")
      }
    ),
    suite("Custom")(
      test("applies custom function") {
        val upper = SqlNameMapper.Custom(_.toUpperCase)
        assertTrue(upper("firstName") == "FIRSTNAME")
      },
      test("chains transformations") {
        val snakeThenUpper = SqlNameMapper.Custom { s =>
          SqlNameMapper.SnakeCase(s).toUpperCase
        }
        assertTrue(snakeThenUpper("firstName") == "FIRST_NAME")
      }
    )
  )
}
