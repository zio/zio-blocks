package zio.blocks.sql

import zio.test._
import zio.blocks.schema._

object DbCodecSpec extends ZIOSpecDefault {

  case class SimpleRecord(name: String, age: Int)
  object SimpleRecord {
    implicit val schema: Schema[SimpleRecord] = Schema.derived
  }

  case class WithOption(id: Int, nickname: Option[String])
  object WithOption {
    implicit val schema: Schema[WithOption] = Schema.derived
  }

  case class WithTransient(
    name: String,
    @Modifier.transient() hidden: Int = 0
  )
  object WithTransient {
    implicit val schema: Schema[WithTransient] = Schema.derived
  }

  case class WithRename(
    @Modifier.rename("user_name") name: String,
    age: Int
  )
  object WithRename {
    implicit val schema: Schema[WithRename] = Schema.derived
  }

  case class AllPrimitives(
    b: Boolean,
    by: Byte,
    s: Short,
    i: Int,
    l: Long,
    f: Float,
    d: Double,
    str: String
  )
  object AllPrimitives {
    implicit val schema: Schema[AllPrimitives] = Schema.derived
  }

  case class CamelCaseFields(firstName: String, lastName: String)
  object CamelCaseFields {
    implicit val schema: Schema[CamelCaseFields] = Schema.derived
  }

  private def deriveCodec[A](implicit s: Schema[A]): DbCodec[A] =
    s.deriving(DbCodecDeriver).derive

  private def deriveCodecWithMapper[A](
    mapper: SqlNameMapper
  )(implicit s: Schema[A]): DbCodec[A] =
    s.deriving(DbCodecDeriver.withColumnNameMapper(mapper)).derive

  def spec: Spec[TestEnvironment, Any] = suite("DbCodecSpec")(
    suite("primitive derivation")(
      test("Int codec has single column") {
        val codec = deriveCodec[Int]
        assertTrue(
          codec.columns.size == 1,
          codec.columnCount == 1
        )
      },
      test("String codec has single column") {
        val codec = deriveCodec[String]
        assertTrue(codec.columns.size == 1)
      },
      test("Boolean codec has single column") {
        val codec = deriveCodec[Boolean]
        assertTrue(codec.columns.size == 1)
      },
      test("Long codec has single column") {
        val codec = deriveCodec[Long]
        assertTrue(codec.columns.size == 1)
      },
      test("Int toDbValues produces DbInt") {
        val codec  = deriveCodec[Int]
        val values = codec.toDbValues(42)
        assertTrue(values == IndexedSeq(DbValue.DbInt(42)))
      },
      test("String toDbValues produces DbString") {
        val codec  = deriveCodec[String]
        val values = codec.toDbValues("hello")
        assertTrue(values == IndexedSeq(DbValue.DbString("hello")))
      },
      test("Boolean toDbValues produces DbBoolean") {
        val codec  = deriveCodec[Boolean]
        val values = codec.toDbValues(true)
        assertTrue(values == IndexedSeq(DbValue.DbBoolean(true)))
      },
      test("Unit codec has zero columns") {
        val codec = deriveCodec[Unit]
        assertTrue(
          codec.columns.isEmpty,
          codec.columnCount == 0,
          codec.toDbValues(()) == IndexedSeq.empty
        )
      }
    ),
    suite("record derivation")(
      test("simple case class columns match field names") {
        val codec = deriveCodec[SimpleRecord]
        assertTrue(
          codec.columns == IndexedSeq("name", "age"),
          codec.columnCount == 2
        )
      },
      test("simple case class toDbValues") {
        val codec  = deriveCodec[SimpleRecord]
        val values = codec.toDbValues(SimpleRecord("Alice", 30))
        assertTrue(
          values == IndexedSeq(DbValue.DbString("Alice"), DbValue.DbInt(30))
        )
      },
      test("all primitives record has correct column count") {
        val codec = deriveCodec[AllPrimitives]
        assertTrue(codec.columnCount == 8)
      },
      test("all primitives record toDbValues") {
        val codec = deriveCodec[AllPrimitives]
        val value = AllPrimitives(
          b = true,
          by = 1,
          s = 2,
          i = 3,
          l = 4L,
          f = 5.0f,
          d = 6.0,
          str = "hello"
        )
        val dbValues = codec.toDbValues(value)
        assertTrue(
          dbValues == IndexedSeq(
            DbValue.DbBoolean(true),
            DbValue.DbByte(1),
            DbValue.DbShort(2),
            DbValue.DbInt(3),
            DbValue.DbLong(4L),
            DbValue.DbFloat(5.0f),
            DbValue.DbDouble(6.0),
            DbValue.DbString("hello")
          )
        )
      }
    ),
    suite("option handling")(
      test("Option field is nullable column") {
        val codec = deriveCodec[WithOption]
        assertTrue(
          codec.columns == IndexedSeq("id", "nickname"),
          codec.columnCount == 2
        )
      },
      test("Option Some produces inner value") {
        val codec  = deriveCodec[WithOption]
        val values = codec.toDbValues(WithOption(1, Some("nick")))
        assertTrue(
          values == IndexedSeq(DbValue.DbInt(1), DbValue.DbString("nick"))
        )
      },
      test("Option None produces DbNull") {
        val codec  = deriveCodec[WithOption]
        val values = codec.toDbValues(WithOption(1, None))
        assertTrue(
          values == IndexedSeq(DbValue.DbInt(1), DbValue.DbNull)
        )
      }
    ),
    suite("modifier handling")(
      test("transient field excluded from columns") {
        val codec = deriveCodec[WithTransient]
        assertTrue(
          codec.columns == IndexedSeq("name"),
          codec.columnCount == 1
        )
      },
      test("transient field excluded from toDbValues") {
        val codec  = deriveCodec[WithTransient]
        val values = codec.toDbValues(WithTransient("Alice", 42))
        assertTrue(values == IndexedSeq(DbValue.DbString("Alice")))
      },
      test("rename modifier uses custom name") {
        val codec = deriveCodec[WithRename]
        assertTrue(
          codec.columns == IndexedSeq("user_name", "age")
        )
      }
    ),
    suite("column name mapping")(
      test("default SnakeCase mapper: camelCase fields become snake_case columns") {
        val codec = deriveCodec[CamelCaseFields]
        assertTrue(
          codec.columns == IndexedSeq("first_name", "last_name"),
          codec.columnCount == 2
        )
      },
      test("Modifier.rename overrides SnakeCase mapper") {
        val codec = deriveCodec[WithRename]
        assertTrue(
          codec.columns == IndexedSeq("user_name", "age")
        )
      },
      test("Identity mapper preserves camelCase field names") {
        val codec = CamelCaseFields.schema.deriving(DbCodecDeriver.withColumnNameMapper(SqlNameMapper.Identity)).derive
        assertTrue(
          codec.columns == IndexedSeq("firstName", "lastName"),
          codec.columnCount == 2
        )
      },
      test("Custom mapper applies custom function") {
        val upperMapper = SqlNameMapper.Custom(_.toUpperCase)
        val codec       = CamelCaseFields.schema.deriving(DbCodecDeriver.withColumnNameMapper(upperMapper)).derive
        assertTrue(
          codec.columns == IndexedSeq("FIRSTNAME", "LASTNAME")
        )
      }
    ),
    suite("unsupported types")(
      test("List throws UnsupportedOperationException") {
        val result =
          try {
            deriveCodec[List[Int]]
            false
          } catch {
            case _: UnsupportedOperationException => true
          }
        assertTrue(result)
      }
    )
  )
}
