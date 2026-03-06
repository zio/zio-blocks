package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.blocks.schema.migration._
import zio.test._
import zio.test.Assertion._

object MigrationSpec extends SchemaBaseSpec {

  // ── Test domain types ──────────────────────────────────────────────

  case class PersonV1(firstName: String, lastName: String)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  case class PersonV2(firstName: String, lastName: String, age: Int)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  case class PersonV3(fullName: String, age: Int)
  object PersonV3 {
    implicit val schema: Schema[PersonV3] = Schema.derived
  }

  case class Order(id: String, total: Int)
  object Order {
    implicit val schema: Schema[Order] = Schema.derived
  }

  case class OrderV2(id: String, total: Long)
  object OrderV2 {
    implicit val schema: Schema[OrderV2] = Schema.derived
  }

  case class WithOptional(name: String, nickname: Option[String])
  object WithOptional {
    implicit val schema: Schema[WithOptional] = Schema.derived
  }

  case class WithRequired(name: String, nickname: String)
  object WithRequired {
    implicit val schema: Schema[WithRequired] = Schema.derived
  }

  sealed trait Color
  case class Red(shade: Int)     extends Color
  case class Blue(shade: Int)    extends Color
  case class Green(shade: Int)   extends Color
  object Color {
    implicit val schema: Schema[Color] = Schema.derived
  }

  case class Item(name: String, price: Int)
  object Item {
    implicit val schema: Schema[Item] = Schema.derived
  }

  case class Cart(items: Vector[Item])
  object Cart {
    implicit val schema: Schema[Cart] = Schema.derived
  }

  // ── Tests ──────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSpec")(
    suite("DynamicMigration")(
      test("empty migration is identity") {
        val dv     = Schema[PersonV1].toDynamicValue(PersonV1("John", "Doe"))
        val result = DynamicMigration.empty(dv)
        assert(result)(isRight(equalTo(dv)))
      },
      test("add a field to a record") {
        val dv = Schema[PersonV1].toDynamicValue(PersonV1("John", "Doe"))
        val migration = DynamicMigration(Vector(
          MigrationAction.AddField(DynamicOptic.root, "age", DynamicValue.Primitive(new PrimitiveValue.Int(0)))
        ))
        val result = migration(dv)
        assertTrue(
          result.isRight,
          result.toOption.get.fields.length == 3,
          result.toOption.get.get("age").one.toOption.get == DynamicValue.Primitive(new PrimitiveValue.Int(0))
        )
      },
      test("drop a field from a record") {
        val dv = Schema[PersonV2].toDynamicValue(PersonV2("John", "Doe", 30))
        val migration = DynamicMigration(Vector(
          MigrationAction.DropField(DynamicOptic.root, "age", DynamicValue.Primitive(new PrimitiveValue.Int(0)))
        ))
        val result = migration(dv)
        assertTrue(
          result.isRight,
          result.toOption.get.fields.length == 2,
          result.toOption.get.get("age").toChunk.isEmpty
        )
      },
      test("rename a field") {
        val dv = Schema[PersonV1].toDynamicValue(PersonV1("John", "Doe"))
        val migration = DynamicMigration(Vector(
          MigrationAction.RenameField(DynamicOptic.root, "firstName", "fullName")
        ))
        val result = migration(dv)
        assertTrue(
          result.isRight,
          result.toOption.get.get("fullName").one.isRight,
          result.toOption.get.get("firstName").toChunk.isEmpty
        )
      },
      test("change field type Int -> Long") {
        val dv = Schema[Order].toDynamicValue(Order("ord-1", 100))
        val migration = DynamicMigration(Vector(
          MigrationAction.ChangeFieldType(
            DynamicOptic.root,
            "total",
            DynamicValue.Primitive(new PrimitiveValue.String("Long")),
            DynamicValue.Primitive(new PrimitiveValue.String("Int"))
          )
        ))
        val result = migration(dv)
        assertTrue(result.isRight) && {
          val totalDv = result.toOption.get.get("total").one.toOption.get
          assert(totalDv)(equalTo(DynamicValue.Primitive(new PrimitiveValue.Long(100L))))
        }
      },
      test("optionalize a field") {
        val dv = Schema[WithRequired].toDynamicValue(WithRequired("Alice", "Ali"))
        val migration = DynamicMigration(Vector(
          MigrationAction.Optionalize(DynamicOptic.root, "nickname")
        ))
        val result = migration(dv)
        assertTrue(result.isRight) && {
          val nicknameDv = result.toOption.get.get("nickname").one.toOption.get
          // should be wrapped in Variant("Some", ...)
          assert(nicknameDv.caseName)(isSome(equalTo("Some")))
        }
      },
      test("mandate a field (unwrap Some)") {
        val dv = Schema[WithOptional].toDynamicValue(WithOptional("Alice", Some("Ali")))
        val migration = DynamicMigration(Vector(
          MigrationAction.Mandate(
            DynamicOptic.root,
            "nickname",
            DynamicValue.Primitive(new PrimitiveValue.String("default"))
          )
        ))
        val result = migration(dv)
        assertTrue(result.isRight) && {
          val nicknameDv = result.toOption.get.get("nickname").one.toOption.get
          assert(nicknameDv)(equalTo(DynamicValue.Primitive(new PrimitiveValue.String("Ali"))))
        }
      },
      test("mandate a field (use default for None)") {
        val dv = Schema[WithOptional].toDynamicValue(WithOptional("Alice", None))
        val migration = DynamicMigration(Vector(
          MigrationAction.Mandate(
            DynamicOptic.root,
            "nickname",
            DynamicValue.Primitive(new PrimitiveValue.String("default"))
          )
        ))
        val result = migration(dv)
        assertTrue(result.isRight) && {
          val nicknameDv = result.toOption.get.get("nickname").one.toOption.get
          assert(nicknameDv)(equalTo(DynamicValue.Primitive(new PrimitiveValue.String("default"))))
        }
      },
      test("rename a variant case") {
        val dv = Schema[Color].toDynamicValue(Red(5))
        val migration = DynamicMigration(Vector(
          MigrationAction.RenameCase(DynamicOptic.root, "Red", "Crimson")
        ))
        val result = migration(dv)
        assertTrue(result.isRight) &&
        assert(result.toOption.get.caseName)(isSome(equalTo("Crimson")))
      },
      test("transform a variant case payload") {
        val dv = Schema[Color].toDynamicValue(Red(5))
        val migration = DynamicMigration(Vector(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            "Red",
            Vector(MigrationAction.RenameField(DynamicOptic.root, "shade", "intensity"))
          )
        ))
        val result = migration(dv)
        assertTrue(result.isRight) && {
          val payload = result.toOption.get.caseValue.get
          assertTrue(payload.get("intensity").one.isRight)
        }
      },
      test("non-matching case is left unchanged") {
        val dv = Schema[Color].toDynamicValue(Blue(3))
        val migration = DynamicMigration(Vector(
          MigrationAction.RenameCase(DynamicOptic.root, "Red", "Crimson")
        ))
        val result = migration(dv)
        assertTrue(result.isRight) &&
        assert(result.toOption.get.caseName)(isSome(equalTo("Blue")))
      },
      test("error on dropping non-existent field") {
        val dv = Schema[PersonV1].toDynamicValue(PersonV1("John", "Doe"))
        val migration = DynamicMigration(Vector(
          MigrationAction.DropField(DynamicOptic.root, "nonexistent", DynamicValue.Null)
        ))
        assert(migration(dv))(isLeft)
      },
      test("error on adding duplicate field") {
        val dv = Schema[PersonV1].toDynamicValue(PersonV1("John", "Doe"))
        val migration = DynamicMigration(Vector(
          MigrationAction.AddField(DynamicOptic.root, "firstName", DynamicValue.Null)
        ))
        assert(migration(dv))(isLeft)
      }
    ),
    suite("DynamicMigration composition")(
      test("sequential composition applies actions in order") {
        val m1 = DynamicMigration(Vector(
          MigrationAction.AddField(DynamicOptic.root, "age", DynamicValue.Primitive(new PrimitiveValue.Int(0)))
        ))
        val m2 = DynamicMigration(Vector(
          MigrationAction.DropField(DynamicOptic.root, "lastName", DynamicValue.Null)
        ))
        val combined = m1 ++ m2
        val dv       = Schema[PersonV1].toDynamicValue(PersonV1("John", "Doe"))
        val result   = combined(dv)
        assertTrue(
          result.isRight,
          result.toOption.get.fields.length == 2,
          result.toOption.get.get("age").one.isRight,
          result.toOption.get.get("lastName").toChunk.isEmpty
        )
      },
      test("associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
        val m1 = DynamicMigration(Vector(
          MigrationAction.AddField(DynamicOptic.root, "age", DynamicValue.Primitive(new PrimitiveValue.Int(25)))
        ))
        val m2 = DynamicMigration(Vector(
          MigrationAction.RenameField(DynamicOptic.root, "firstName", "name")
        ))
        val m3 = DynamicMigration(Vector(
          MigrationAction.DropField(DynamicOptic.root, "lastName", DynamicValue.Null)
        ))
        val dv  = Schema[PersonV1].toDynamicValue(PersonV1("John", "Doe"))
        val r1  = ((m1 ++ m2) ++ m3)(dv)
        val r2  = (m1 ++ (m2 ++ m3))(dv)
        assert(r1)(equalTo(r2))
      }
    ),
    suite("DynamicMigration reverse")(
      test("structural reverse: m.reverse.reverse == m") {
        val m = DynamicMigration(Vector(
          MigrationAction.AddField(DynamicOptic.root, "age", DynamicValue.Primitive(new PrimitiveValue.Int(0))),
          MigrationAction.RenameField(DynamicOptic.root, "firstName", "name"),
          MigrationAction.DropField(DynamicOptic.root, "lastName", DynamicValue.Primitive(new PrimitiveValue.String("")))
        ))
        assertTrue(m.reverse.reverse.actions == m.actions)
      },
      test("semantic reverse: add + reverse(add) == identity") {
        val dv = Schema[PersonV1].toDynamicValue(PersonV1("John", "Doe"))
        val m = DynamicMigration(Vector(
          MigrationAction.AddField(DynamicOptic.root, "age", DynamicValue.Primitive(new PrimitiveValue.Int(0)))
        ))
        val forward = m(dv)
        assertTrue(forward.isRight) && {
          val backward = m.reverse(forward.toOption.get)
          assert(backward)(isRight(equalTo(dv)))
        }
      },
      test("semantic reverse: rename roundtrip") {
        val dv = Schema[PersonV1].toDynamicValue(PersonV1("John", "Doe"))
        val m = DynamicMigration(Vector(
          MigrationAction.RenameField(DynamicOptic.root, "firstName", "name")
        ))
        val forward  = m(dv)
        val backward = m.reverse(forward.toOption.get)
        assert(backward)(isRight(equalTo(dv)))
      }
    ),
    suite("Migration[A, B] typed API")(
      test("end-to-end migration PersonV1 -> PersonV2") {
        val migration = Migration.newBuilder[PersonV1, PersonV2]
          .addField("age", 0)
          .build
        val result = migration(PersonV1("John", "Doe"))
        assert(result)(isRight(equalTo(PersonV2("John", "Doe", 0))))
      },
      test("identity migration") {
        val m = Migration.identity[PersonV1]
        assert(m(PersonV1("Jane", "Smith")))(isRight(equalTo(PersonV1("Jane", "Smith"))))
      },
      test("migration composition A -> B -> C") {
        val m1 = Migration.newBuilder[PersonV1, PersonV2]
          .addField("age", 0)
          .build
        val m2 = Migration.newBuilder[PersonV2, PersonV3]
          .dropField("lastName")
          .renameField("firstName", "fullName")
          .build
        val combined = m1 ++ m2
        val result   = combined(PersonV1("John", "Doe"))
        assert(result)(isRight(equalTo(PersonV3("John", 0))))
      },
      test("migration reverse") {
        val m = Migration.newBuilder[PersonV1, PersonV2]
          .addField("age", 0)
          .build
        val rev    = m.reverse
        val result = rev(PersonV2("John", "Doe", 42))
        assert(result)(isRight(equalTo(PersonV1("John", "Doe"))))
      }
    ),
    suite("MigrationBuilder")(
      test("renameField") {
        val m = Migration.newBuilder[PersonV1, PersonV1]
          .renameField("firstName", "givenName")
          .build
        val dv     = m.dynamicMigration(Schema[PersonV1].toDynamicValue(PersonV1("John", "Doe")))
        val record = dv.toOption.get
        assertTrue(record.get("givenName").one.isRight)
      },
      test("dropField with typed default for reverse") {
        val m = Migration.newBuilder[PersonV2, PersonV1]
          .dropField[Int]("age", 0)
          .build
        val result = m(PersonV2("John", "Doe", 30))
        assert(result)(isRight(equalTo(PersonV1("John", "Doe"))))
      },
      test("changeFieldType") {
        val m = Migration.newBuilder[Order, OrderV2]
          .changeFieldType("total", "Long", "Int")
          .build
        val result = m(Order("ord-1", 100))
        assert(result)(isRight(equalTo(OrderV2("ord-1", 100L))))
      },
      test("optionalizeField") {
        val m = Migration.newBuilder[WithRequired, WithOptional]
          .optionalizeField("nickname")
          .build
        val result = m(WithRequired("Alice", "Ali"))
        assert(result)(isRight(equalTo(WithOptional("Alice", Some("Ali")))))
      },
      test("mandateField") {
        val m = Migration.newBuilder[WithOptional, WithRequired]
          .mandateField("nickname", "default")
          .build
        val r1 = m(WithOptional("Alice", Some("Ali")))
        val r2 = m(WithOptional("Alice", None))
        assert(r1)(isRight(equalTo(WithRequired("Alice", "Ali")))) &&
        assert(r2)(isRight(equalTo(WithRequired("Alice", "default"))))
      },
      test("renameCase") {
        val m = Migration.newBuilder[Color, Color]
          .renameCase("Red", "Crimson")
          .build
        val dv     = m.dynamicMigration(Schema[Color].toDynamicValue(Red(5)))
        val result = dv.toOption.get
        assert(result.caseName)(isSome(equalTo("Crimson")))
      }
    ),
    suite("collection operations")(
      test("transformElements applies nested actions to each element") {
        val items = Chunk(
          ("name", DynamicValue.Primitive(new PrimitiveValue.String("Widget"))),
          ("price", DynamicValue.Primitive(new PrimitiveValue.Int(10)))
        )
        val seq = DynamicValue.Sequence(Chunk(DynamicValue.Record(items), DynamicValue.Record(items)))
        val record = DynamicValue.Record(Chunk(("items", seq)))
        val migration = DynamicMigration(Vector(
          MigrationAction.TransformElements(
            DynamicOptic.root.field("items"),
            Vector(MigrationAction.RenameField(DynamicOptic.root, "price", "cost"))
          )
        ))
        val result = migration(record)
        assertTrue(result.isRight) && {
          val elems = result.toOption.get.get("items").one.toOption.get.elements
          assertTrue(
            elems.length == 2,
            elems(0).get("cost").one.isRight,
            elems(0).get("price").toChunk.isEmpty
          )
        }
      }
    ),
    suite("error reporting")(
      test("error includes path information") {
        val dv = Schema[PersonV1].toDynamicValue(PersonV1("John", "Doe"))
        val migration = DynamicMigration(Vector(
          MigrationAction.DropField(DynamicOptic.root, "nonexistent", DynamicValue.Null)
        ))
        val result = migration(dv)
        assertTrue(result.isLeft) && {
          val err = result.swap.toOption.get
          assertTrue(err.message.contains("nonexistent"))
        }
      }
    )
  )
}
