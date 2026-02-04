package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationValidatorSpec extends SchemaBaseSpec {

  // Test schemas for various scenarios
  final case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  final case class PersonWithEmail(name: String, age: Int, email: String)
  object PersonWithEmail {
    implicit val schema: Schema[PersonWithEmail] = Schema.derived
  }

  final case class PersonRenamed(fullName: String, age: Int)
  object PersonRenamed {
    implicit val schema: Schema[PersonRenamed] = Schema.derived
  }

  final case class PersonWithOptional(name: String, age: Int, nickname: Option[String])
  object PersonWithOptional {
    implicit val schema: Schema[PersonWithOptional] = Schema.derived
  }

  final case class Nested(person: Person)
  object Nested {
    implicit val schema: Schema[Nested] = Schema.derived
  }

  final case class NestedWithEmail(person: PersonWithEmail)
  object NestedWithEmail {
    implicit val schema: Schema[NestedWithEmail] = Schema.derived
  }

  sealed trait Status
  object Status {
    case class Active(since: Int)       extends Status
    case class Inactive(reason: String) extends Status
    implicit val schema: Schema[Status] = Schema.derived
  }

  sealed trait StatusRenamed
  object StatusRenamed {
    case class Enabled(since: Int)      extends StatusRenamed
    case class Inactive(reason: String) extends StatusRenamed
    implicit val schema: Schema[StatusRenamed] = Schema.derived
  }

  final case class WithList(items: List[Int])
  object WithList {
    implicit val schema: Schema[WithList] = Schema.derived
  }

  final case class WithMap(data: Map[String, Int])
  object WithMap {
    implicit val schema: Schema[WithMap] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("MigrationValidatorSpec")(
    suite("extractStructure")(
      test("extracts record structure") {
        val structure = MigrationValidator.extractStructure(Person.schema)
        structure match {
          case MigrationValidator.SchemaStructure.Record(name, fields, _) =>
            assertTrue(
              name == "Person",
              fields.contains("name"),
              fields.contains("age")
            )
          case _ =>
            assertTrue(false)
        }
      },
      test("extracts nested record structure") {
        val structure = MigrationValidator.extractStructure(Nested.schema)
        structure match {
          case MigrationValidator.SchemaStructure.Record(_, fields, _) =>
            fields.get("person") match {
              case Some(MigrationValidator.SchemaStructure.Record(name, innerFields, _)) =>
                assertTrue(
                  name == "Person",
                  innerFields.contains("name")
                )
              case _ => assertTrue(false)
            }
          case _ =>
            assertTrue(false)
        }
      },
      test("extracts variant structure") {
        val structure = MigrationValidator.extractStructure(Status.schema)
        structure match {
          case MigrationValidator.SchemaStructure.Variant(_, cases) =>
            assertTrue(
              cases.contains("Active"),
              cases.contains("Inactive")
            )
          case _ =>
            assertTrue(false)
        }
      },
      test("extracts sequence structure") {
        val structure = MigrationValidator.extractStructure(WithList.schema)
        structure match {
          case MigrationValidator.SchemaStructure.Record(_, fields, _) =>
            fields.get("items") match {
              case Some(_: MigrationValidator.SchemaStructure.Sequence) =>
                assertTrue(true)
              case _ => assertTrue(false)
            }
          case _ =>
            assertTrue(false)
        }
      },
      test("extracts map structure") {
        val structure = MigrationValidator.extractStructure(WithMap.schema)
        structure match {
          case MigrationValidator.SchemaStructure.Record(_, fields, _) =>
            fields.get("data") match {
              case Some(_: MigrationValidator.SchemaStructure.MapType) =>
                assertTrue(true)
              case _ => assertTrue(false)
            }
          case _ =>
            assertTrue(false)
        }
      },
      test("extracts optional structure") {
        val structure = MigrationValidator.extractStructure(PersonWithOptional.schema)
        structure match {
          case MigrationValidator.SchemaStructure.Record(_, fields, isOptional) =>
            assertTrue(
              fields.contains("nickname"),
              isOptional.getOrElse("nickname", false) == true
            )
          case _ =>
            assertTrue(false)
        }
      }
    ),
    suite("validate - AddField")(
      test("validates adding a new field") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root,
            "email",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
          )
        )
        val result = MigrationValidator.validate(Person.schema, PersonWithEmail.schema, actions)
        assertTrue(result.isValid)
      },
      test("rejects adding field that already exists") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root,
            "name",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("already exists")))
      },
      test("rejects adding field to non-record") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("name"),
            "foo",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("non-record")))
      }
    ),
    suite("validate - DropField")(
      test("validates dropping a field") {
        val actions = Vector(
          MigrationAction.DropField(
            DynamicOptic.root,
            "email",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
          )
        )
        val result = MigrationValidator.validate(PersonWithEmail.schema, Person.schema, actions)
        assertTrue(result.isValid)
      },
      test("rejects dropping non-existent field") {
        val actions = Vector(
          MigrationAction.DropField(
            DynamicOptic.root,
            "missing",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("does not exist")))
      },
      test("rejects dropping from non-record") {
        val actions = Vector(
          MigrationAction.DropField(
            DynamicOptic.root.field("name"),
            "foo",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("non-record")))
      }
    ),
    suite("validate - RenameField")(
      test("validates renaming a field") {
        val actions = Vector(
          MigrationAction.RenameField(DynamicOptic.root, "name", "fullName")
        )
        val result = MigrationValidator.validate(Person.schema, PersonRenamed.schema, actions)
        assertTrue(result.isValid)
      },
      test("rejects renaming non-existent field") {
        val actions = Vector(
          MigrationAction.RenameField(DynamicOptic.root, "missing", "newName")
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("does not exist")))
      },
      test("rejects renaming to existing field") {
        val actions = Vector(
          MigrationAction.RenameField(DynamicOptic.root, "name", "age")
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("already exists")))
      },
      test("rejects renaming in non-record") {
        val actions = Vector(
          MigrationAction.RenameField(DynamicOptic.root.field("name"), "a", "b")
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("non-record")))
      }
    ),
    suite("validate - RenameCase")(
      test("validates renaming a case") {
        val actions = Vector(
          MigrationAction.RenameCase(DynamicOptic.root, "Active", "Enabled")
        )
        val result = MigrationValidator.validate(Status.schema, StatusRenamed.schema, actions)
        assertTrue(result.isValid)
      },
      test("rejects renaming non-existent case") {
        val actions = Vector(
          MigrationAction.RenameCase(DynamicOptic.root, "Missing", "NewCase")
        )
        val result = MigrationValidator.validate(Status.schema, Status.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("does not exist")))
      },
      test("rejects renaming to existing case") {
        val actions = Vector(
          MigrationAction.RenameCase(DynamicOptic.root, "Active", "Inactive")
        )
        val result = MigrationValidator.validate(Status.schema, Status.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("already exists")))
      },
      test("rejects renaming case in non-variant") {
        val actions = Vector(
          MigrationAction.RenameCase(DynamicOptic.root, "a", "b")
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("non-variant")))
      }
    ),
    suite("validate - nested paths")(
      test("validates adding field in nested record") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("person"),
            "email",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
          )
        )
        val result = MigrationValidator.validate(Nested.schema, NestedWithEmail.schema, actions)
        assertTrue(result.isValid)
      },
      test("rejects path through non-existent field") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("missing").field("person"),
            "email",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
          )
        )
        val result = MigrationValidator.validate(Nested.schema, Nested.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("not found")))
      }
    ),
    suite("validate - Identity")(
      test("identity action is always valid") {
        val actions = Vector(MigrationAction.Identity)
        val result  = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(result.isValid)
      }
    ),
    suite("validate - TransformValue")(
      test("transform value action is valid") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("age"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(result.isValid)
      }
    ),
    suite("compareStructures")(
      test("detects missing fields") {
        val source = MigrationValidator.extractStructure(Person.schema)
        val target = MigrationValidator.extractStructure(PersonWithEmail.schema)
        val result = MigrationValidator.validate(Person.schema, PersonWithEmail.schema, Vector.empty)
        assertTrue(!result.isValid, result.errors.exists(_.contains("Missing fields")))
      },
      test("detects extra fields") {
        val result = MigrationValidator.validate(PersonWithEmail.schema, Person.schema, Vector.empty)
        assertTrue(!result.isValid, result.errors.exists(_.contains("Unexpected fields")))
      },
      test("matches identical structures") {
        val result = MigrationValidator.validate(Person.schema, Person.schema, Vector.empty)
        assertTrue(result.isValid)
      }
    ),
    suite("ValidationResult")(
      test("++ combines Valid with Valid") {
        val result = MigrationValidator.Valid ++ MigrationValidator.Valid
        assertTrue(result.isValid)
      },
      test("++ combines Valid with Invalid") {
        val result = MigrationValidator.Valid ++ MigrationValidator.Invalid("error")
        assertTrue(!result.isValid, result.errors == List("error"))
      },
      test("++ combines Invalid with Valid") {
        val result = MigrationValidator.Invalid("error") ++ MigrationValidator.Valid
        assertTrue(!result.isValid, result.errors == List("error"))
      },
      test("++ combines Invalid with Invalid") {
        val result = MigrationValidator.Invalid("error1") ++ MigrationValidator.Invalid("error2")
        assertTrue(!result.isValid, result.errors == List("error1", "error2"))
      }
    ),
    suite("SchemaStructure.fieldNames")(
      test("Record returns field names") {
        val record = MigrationValidator.SchemaStructure.Record(
          "Test",
          Map("a" -> MigrationValidator.SchemaStructure.Dynamic, "b" -> MigrationValidator.SchemaStructure.Dynamic),
          Map.empty
        )
        assertTrue(record.fieldNames == Set("a", "b"))
      },
      test("Variant returns case names") {
        val variant = MigrationValidator.SchemaStructure.Variant(
          "Test",
          Map("A" -> MigrationValidator.SchemaStructure.Dynamic, "B" -> MigrationValidator.SchemaStructure.Dynamic)
        )
        assertTrue(variant.fieldNames == Set("A", "B"))
      },
      test("Sequence returns empty set") {
        val seq = MigrationValidator.SchemaStructure.Sequence(MigrationValidator.SchemaStructure.Dynamic)
        assertTrue(seq.fieldNames == Set.empty)
      },
      test("MapType returns empty set") {
        val mapType = MigrationValidator.SchemaStructure
          .MapType(MigrationValidator.SchemaStructure.Dynamic, MigrationValidator.SchemaStructure.Dynamic)
        assertTrue(mapType.fieldNames == Set.empty)
      },
      test("Primitive returns empty set") {
        val prim = MigrationValidator.SchemaStructure.Primitive("Int")
        assertTrue(prim.fieldNames == Set.empty)
      },
      test("Optional returns empty set") {
        val opt = MigrationValidator.SchemaStructure.Optional(MigrationValidator.SchemaStructure.Dynamic)
        assertTrue(opt.fieldNames == Set.empty)
      },
      test("Dynamic returns empty set") {
        assertTrue(MigrationValidator.SchemaStructure.Dynamic.fieldNames == Set.empty)
      }
    ),
    suite("DynamicOpticOps")(
      test("dropLastField on empty path returns None") {
        val optic                  = DynamicOptic.root
        val (remaining, fieldName) = new MigrationValidator.DynamicOpticOps(optic).dropLastField
        assertTrue(remaining.nodes.isEmpty, fieldName.isEmpty)
      },
      test("dropLastField on field path returns field name") {
        val optic                  = DynamicOptic.root.field("test")
        val (remaining, fieldName) = new MigrationValidator.DynamicOpticOps(optic).dropLastField
        assertTrue(remaining.nodes.isEmpty, fieldName.contains("test"))
      },
      test("dropLastField on non-field path returns None") {
        val optic                  = DynamicOptic.root.at(0)
        val (remaining, fieldName) = new MigrationValidator.DynamicOpticOps(optic).dropLastField
        assertTrue(remaining.nodes.nonEmpty, fieldName.isEmpty)
      },
      test("lastFieldName on empty path returns None") {
        val optic = DynamicOptic.root
        assertTrue(new MigrationValidator.DynamicOpticOps(optic).lastFieldName.isEmpty)
      },
      test("lastFieldName on field path returns field name") {
        val optic = DynamicOptic.root.field("test")
        assertTrue(new MigrationValidator.DynamicOpticOps(optic).lastFieldName.contains("test"))
      },
      test("lastFieldName on non-field path returns None") {
        val optic = DynamicOptic.root.at(0)
        assertTrue(new MigrationValidator.DynamicOpticOps(optic).lastFieldName.isEmpty)
      }
    )
  )
}
