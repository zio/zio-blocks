package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Schema, SchemaBaseSpec}
import zio.test.Assertion.{equalTo, isLeft, isRight}
import zio.test._

object MigrationSpec extends SchemaBaseSpec {

  // Example types: V0 has name only, V1 has name and age
  case class PersonV0(name: String)
  object PersonV0 {
    implicit val schema: Schema[PersonV0] = Schema.derived[PersonV0]
  }

  case class PersonV1(name: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived[PersonV1]
  }

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSpec")(
    suite("Migration.identity")(
      test("identity returns the value unchanged") {
        val m = Migration.identity[PersonV1]
        val p = PersonV1("Alice", 30)
        assert(m(p))(isRight(equalTo(p)))
      }
    ),
    suite("DynamicMigration")(
      test("empty migration returns value unchanged") {
        val dv = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
        assert(DynamicMigration.empty(dv))(isRight(equalTo(dv)))
      },
      test("AddField inserts default at path") {
        val dv0 = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("age"),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )
        val dm = DynamicMigration(Chunk.single(action))
        val result = dm(dv0)
        assert(result)(isRight)
        result.foreach { dv =>
          val fields = dv.asInstanceOf[DynamicValue.Record].fields
          assert(fields.map(_._1))(equalTo(Chunk("name", "age")))
          assert(fields.find(_._1 == "age").map(_._2))(equalTo(Some(DynamicValue.Primitive(PrimitiveValue.Int(0)))))
        }
      },
      test("Rename renames a field") {
        val dv0 = DynamicValue.Record(Chunk(
          "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
          "lastName"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
        ))
        val action = MigrationAction.Rename(DynamicOptic.root.field("firstName"), "givenName")
        val dm     = DynamicMigration(Chunk.single(action))
        val result = dm(dv0)
        assert(result)(isRight)
        result.foreach { dv =>
          val fields = dv.asInstanceOf[DynamicValue.Record].fields
          assert(fields.exists(_._1 == "givenName"))(equalTo(true))
          assert(fields.exists(_._1 == "firstName"))(equalTo(false))
        }
      },
      test("DropField removes field") {
        val dv0 = DynamicValue.Record(Chunk(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
        ))
        val action = MigrationAction.DropField(
          DynamicOptic.root.field("age"),
          MigrationExpr.Literal(DynamicValue.Null)
        )
        val dm     = DynamicMigration(Chunk.single(action))
        val result = dm(dv0)
        assert(result)(isRight)
        result.foreach { dv =>
          val fields = dv.asInstanceOf[DynamicValue.Record].fields
          assert(fields.map(_._1))(equalTo(Chunk("name")))
        }
      },
      test("Join combines source paths with StringConcat") {
        val dv0 = DynamicValue.Record(Chunk(
          "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
          "lastName"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
        ))
        val action = MigrationAction.Join(
          DynamicOptic.root.field("fullName"),
          Chunk(DynamicOptic.root.field("firstName"), DynamicOptic.root.field("lastName")),
          MigrationAction.CombineOp.StringConcat(" ")
        )
        val dm     = DynamicMigration(Chunk.single(action))
        val result = dm(dv0)
        assert(result)(isRight)
        result.foreach { dv =>
          val fields = dv.asInstanceOf[DynamicValue.Record].fields
          val fullName = fields.find(_._1 == "fullName").map(_._2)
          assert(fullName)(equalTo(Some(DynamicValue.Primitive(PrimitiveValue.String("John Doe")))))
        }
      },
      test("Split splits value at path into target paths") {
        val dv0 = DynamicValue.Record(Chunk(
          "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("Jane Smith"))
        ))
        val action = MigrationAction.Split(
          DynamicOptic.root.field("fullName"),
          Chunk(DynamicOptic.root.field("firstName"), DynamicOptic.root.field("lastName")),
          MigrationAction.SplitOp.StringSplit(" ")
        )
        val dm     = DynamicMigration(Chunk.single(action))
        val result = dm(dv0)
        assert(result)(isRight)
        result.foreach { dv =>
          val fields = dv.asInstanceOf[DynamicValue.Record].fields
          val firstName = fields.find(_._1 == "firstName").map(_._2)
          val lastName  = fields.find(_._1 == "lastName").map(_._2)
          assert(firstName)(equalTo(Some(DynamicValue.Primitive(PrimitiveValue.String("Jane")))))
          assert(lastName)(equalTo(Some(DynamicValue.Primitive(PrimitiveValue.String("Smith")))))
        }
      }
    ),
    suite("Migration[A, B]")(
      test("addField migration: PersonV0 -> PersonV1 with default age") {
        val migration = Migration
          .newBuilder(PersonV0.schema, PersonV1.schema)
          .addField(
            DynamicOptic.root.field("age"),
            MigrationExpr.Literal(Schema[Int].toDynamicValue(0))
          )
          .build
        val v0 = PersonV0("Alice")
        assert(migration(v0))(isRight(equalTo(PersonV1("Alice", 0))))
      },
      test("compose migrations with ++") {
        case class PersonV2(name: String, age: Int, country: String)
        object PersonV2 {
          implicit val schema: Schema[PersonV2] = Schema.derived[PersonV2]
        }
        val m1 = Migration
          .newBuilder(PersonV0.schema, PersonV1.schema)
          .addField(DynamicOptic.root.field("age"), MigrationExpr.Literal(Schema[Int].toDynamicValue(0)))
          .build
        val m2 = Migration
          .newBuilder(PersonV1.schema, PersonV2.schema)
          .addField(DynamicOptic.root.field("country"), MigrationExpr.Literal(Schema[String].toDynamicValue("")))
          .build
        val composed = m1 ++ m2
        val v0       = PersonV0("Bob")
        assert(composed(v0))(isRight(equalTo(PersonV2("Bob", 0, ""))))
      },
      test("reverse migration best-effort") {
        val forward = Migration
          .newBuilder(PersonV0.schema, PersonV1.schema)
          .addField(DynamicOptic.root.field("age"), MigrationExpr.Literal(Schema[Int].toDynamicValue(0)))
          .build
        val v0 = PersonV0("Carol")
        val v1 = forward(v0).toOption.get
        val rev = forward.reverse
        assert(rev(v1))(isRight(equalTo(v0)))
      }
    ),
    suite("MigrationExpr")(
      test("Literal.eval returns the value") {
        val lit = MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        val dv  = DynamicValue.Record(Chunk.empty)
        assert(lit.eval(dv))(isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(42)))))
      },
      test("SourcePath.eval reads from root") {
        val dv   = DynamicValue.Record(Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.String("hello"))))
        val expr = MigrationExpr.SourcePath(DynamicOptic.root.field("x"))
        assert(expr.eval(dv))(isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.String("hello")))))
      }
    )
  )
}
