package zio.blocks.sql

import zio.test._

object RepoSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("RepoSpec")(
    suite("buildInsertFrag")(
      test("builds correct INSERT Frag for 3 values") {
        val values = IndexedSeq(
          DbValue.DbInt(1): DbValue,
          DbValue.DbString("Alice"): DbValue,
          DbValue.DbString("alice@example.com"): DbValue
        )
        val frag = Repo.buildInsertFrag("user", "id, name, email", values)
        assertTrue(
          frag.sql(SqlDialect.SQLite) == "INSERT INTO user (id, name, email) VALUES (?, ?, ?)",
          frag.sql(SqlDialect.PostgreSQL) == "INSERT INTO user (id, name, email) VALUES ($1, $2, $3)",
          frag.queryParams == values
        )
      },
      test("builds correct INSERT Frag for 1 value") {
        val values = IndexedSeq(DbValue.DbInt(42): DbValue)
        val frag   = Repo.buildInsertFrag("t", "id", values)
        assertTrue(
          frag.sql(SqlDialect.SQLite) == "INSERT INTO t (id) VALUES (?)",
          frag.queryParams == values
        )
      },
      test("builds INSERT Frag with no values") {
        val frag = Repo.buildInsertFrag("t", "", IndexedSeq.empty)
        assertTrue(
          frag.sql(SqlDialect.SQLite) == "INSERT INTO t DEFAULT VALUES",
          frag.queryParams.isEmpty
        )
      }
    ),
    suite("buildUpdateFrag")(
      test("builds correct UPDATE Frag for 2 columns + 1 id") {
        val columns      = IndexedSeq("name", "email")
        val entityValues = IndexedSeq(DbValue.DbString("Bob"): DbValue, DbValue.DbString("bob@test.com"): DbValue)
        val idValues     = IndexedSeq(DbValue.DbInt(1): DbValue)
        val frag         = Repo.buildUpdateFrag("user", columns, entityValues, "id", idValues)
        assertTrue(
          frag.sql(SqlDialect.SQLite) == "UPDATE user SET name = ?, email = ? WHERE id = ?",
          frag.sql(SqlDialect.PostgreSQL) == "UPDATE user SET name = $1, email = $2 WHERE id = $3",
          frag.queryParams == entityValues ++ idValues
        )
      },
      test("builds correct UPDATE Frag for 1 column + 1 id") {
        val columns      = IndexedSeq("name")
        val entityValues = IndexedSeq(DbValue.DbString("Alice"): DbValue)
        val idValues     = IndexedSeq(DbValue.DbInt(5): DbValue)
        val frag         = Repo.buildUpdateFrag("t", columns, entityValues, "id", idValues)
        assertTrue(
          frag.sql(SqlDialect.SQLite) == "UPDATE t SET name = ? WHERE id = ?",
          frag.queryParams == entityValues ++ idValues
        )
      }
    ),
    suite("Repo construction")(
      test("exposes table metadata") {
        import zio.blocks.schema._
        case class Item(id: Int, name: String)
        object Item {
          implicit val schema: Schema[Item] = Schema.derived
        }
        val table                 = Table.derived[Item](SqlDialect.SQLite)
        val idCodec: DbCodec[Int] = new DbCodec[Int] {
          val columns: IndexedSeq[String]                                          = IndexedSeq("value")
          def readValue(reader: DbResultReader, startIndex: Int): Int              = reader.getInt(startIndex)
          def writeValue(writer: DbParamWriter, startIndex: Int, value: Int): Unit =
            writer.setInt(startIndex, value)
          def toDbValues(value: Int): IndexedSeq[DbValue] = IndexedSeq(DbValue.DbInt(value))
        }
        val repo = Repo(table, "id", idCodec, (_: Item).id)
        assertTrue(
          repo.table.name == "item",
          repo.idColumn == "id",
          repo.table.columns == IndexedSeq("id", "name")
        )
      }
    )
  )
}
