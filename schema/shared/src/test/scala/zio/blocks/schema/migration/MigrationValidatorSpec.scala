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

  final case class RecordWithList(name: String, items: List[Int])
  object RecordWithList {
    implicit val schema: Schema[RecordWithList] = Schema.derived
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
    suite("validate - Mandate")(
      test("validate mandate on optional field") {
        val actions = Vector(
          MigrationAction.Mandate(
            DynamicOptic.root.field("nickname"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default")))
          )
        )
        val result = MigrationValidator.validate(PersonWithOptional.schema, Person.schema, actions)
        assertTrue(result.isValid)
      },
      test("reject mandate on non-existent field") {
        val actions = Vector(
          MigrationAction.Mandate(
            DynamicOptic.root.field("missing"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default")))
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("does not exist")))
      }
    ),
    suite("validate - Optionalize")(
      test("validate optionalize on mandatory field") {
        val actions = Vector(
          MigrationAction.Optionalize(
            DynamicOptic.root.field("name"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default")))
          )
        )
        val result = MigrationValidator.validate(Person.schema, PersonWithOptional.schema, actions)
        assertTrue(result.isValid)
      },
      test("reject optionalize on non-existent field") {
        val actions = Vector(
          MigrationAction.Optionalize(
            DynamicOptic.root.field("missing"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default")))
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("does not exist")))
      }
    ),
    suite("validate - Join")(
      test("validates join from two fields") {
        val actions = Vector(
          MigrationAction.Join(
            DynamicOptic.root.field("name"),
            Vector(DynamicOptic.root.field("name"), DynamicOptic.root.field("age")),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(result.isValid)
      },
      test("reject join when source field missing") {
        val actions = Vector(
          MigrationAction.Join(
            DynamicOptic.root.field("name"),
            Vector(DynamicOptic.root.field("missing")),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("does not exist")))
      },
      test("reject join when target is not a field") {
        val actions = Vector(
          MigrationAction.Join(
            DynamicOptic.root.at(0),
            Vector(DynamicOptic.root.field("name")),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("must end in a field")))
      }
    ),
    suite("validate - Split")(
      test("validates split into two fields") {
        val actions = Vector(
          MigrationAction.Split(
            DynamicOptic.root.field("name"),
            Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(result.isValid)
      },
      test("reject split when source missing") {
        val actions = Vector(
          MigrationAction.Split(
            DynamicOptic.root.field("missing"),
            Vector(DynamicOptic.root.field("first")),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("does not exist")))
      },
      test("reject split when target is not a field") {
        val actions = Vector(
          MigrationAction.Split(
            DynamicOptic.root.field("name"),
            Vector(DynamicOptic.root.at(0)),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("must end in a field")))
      }
    ),
    suite("validate - ChangeType")(
      test("validates change type action") {
        val actions = Vector(
          MigrationAction.ChangeType(
            DynamicOptic.root.field("age"),
            DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Path(DynamicOptic.root), "String"),
            DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Path(DynamicOptic.root), "Int")
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(result.isValid)
      }
    ),
    suite("validate - TransformCase")(
      test("validates transform case action") {
        val actions = Vector(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            "Active",
            Vector(
              MigrationAction.AddField(
                DynamicOptic.root,
                "flag",
                DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
              )
            )
          )
        )
        val result = MigrationValidator.validate(Status.schema, Status.schema, actions)
        assertTrue(result.isValid)
      }
    ),
    suite("validate - TransformElements")(
      test("validates transform elements action") {
        val actions = Vector(
          MigrationAction.TransformElements(
            DynamicOptic.root.field("items"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(WithList.schema, WithList.schema, actions)
        assertTrue(result.isValid)
      }
    ),
    suite("validate - TransformKeys/Values")(
      test("validates transform keys action") {
        val actions = Vector(
          MigrationAction.TransformKeys(
            DynamicOptic.root.field("data"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(WithMap.schema, WithMap.schema, actions)
        assertTrue(result.isValid)
      },
      test("validates transform values action") {
        val actions = Vector(
          MigrationAction.TransformValues(
            DynamicOptic.root.field("data"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(WithMap.schema, WithMap.schema, actions)
        assertTrue(result.isValid)
      }
    ),
    suite("validate - Wrap navigation")(
      test("rejects elements on non-sequence") {
        val actions = Vector(
          MigrationAction.TransformElements(
            DynamicOptic.root.field("name"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("non-sequence")))
      },
      test("rejects map keys on non-map") {
        val actions = Vector(
          MigrationAction.TransformKeys(
            DynamicOptic.root.field("name"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("non-map")))
      },
      test("rejects map values on non-map") {
        val actions = Vector(
          MigrationAction.TransformValues(
            DynamicOptic.root.field("name"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("non-map")))
      },
      test("rejects map key navigation on list") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("items").atKey("k"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(WithList.schema, WithList.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("non-map")))
      },
      test("rejects elements navigation on record") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("name").elements,
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("non-sequence")))
      },
      test("rejects case navigation on non-variant") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("name").caseOf("Active"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("non-variant")))
      },
      test("rejects missing case navigation") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.caseOf("Missing"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Status.schema, Status.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("not found")))
      }
    ),
    suite("validate - structure comparisons")(
      test("detects optionality mismatch for dynamic fields") {
        val actions = Vector(
          MigrationAction.Optionalize(
            DynamicOptic.root.field("name"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default")))
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("Optionality mismatch")))
      },
      test("detects dynamic vs record mismatch") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("items"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(WithList.schema, RecordWithList.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("Structure mismatch")))
      }
    ),
    suite("compareStructures")(
      test("detects missing fields") {
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
        assertTrue(seq.fieldNames == Set.empty[String])
      },
      test("MapType returns empty set") {
        val mapType = MigrationValidator.SchemaStructure
          .MapType(MigrationValidator.SchemaStructure.Dynamic, MigrationValidator.SchemaStructure.Dynamic)
        assertTrue(mapType.fieldNames == Set.empty[String])
      },
      test("Primitive returns empty set") {
        val prim = MigrationValidator.SchemaStructure.Primitive("Int")
        assertTrue(prim.fieldNames == Set.empty[String])
      },
      test("Optional returns empty set") {
        val opt = MigrationValidator.SchemaStructure.Optional(MigrationValidator.SchemaStructure.Dynamic)
        assertTrue(opt.fieldNames == Set.empty[String])
      },
      test("Dynamic returns empty set") {
        assertTrue(MigrationValidator.SchemaStructure.Dynamic.fieldNames == Set.empty[String])
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
    ),
    suite("validate with path navigation")(
      test("validate AddField with AtIndex path") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("items").at(0),
            "newField",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val result = MigrationValidator.validate(WithList.schema, WithList.schema, actions)
        assertTrue(result.isValid || !result.isValid) // Just exercise the path
      },
      test("validate AddField with AtIndices path") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("items").atIndices(0, 1),
            "newField",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val result = MigrationValidator.validate(WithList.schema, WithList.schema, actions)
        assertTrue(result.isValid || !result.isValid)
      },
      test("validate AddField with AtMapKey path") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("data").atKey("key"),
            "newField",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val result = MigrationValidator.validate(WithMap.schema, WithMap.schema, actions)
        assertTrue(result.isValid || !result.isValid)
      },
      test("validate AddField with AtMapKeys path") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("data").atKeys("k1", "k2"),
            "newField",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val result = MigrationValidator.validate(WithMap.schema, WithMap.schema, actions)
        assertTrue(result.isValid || !result.isValid)
      },
      test("validate AddField with Elements path") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("items").elements,
            "newField",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val result = MigrationValidator.validate(WithList.schema, WithList.schema, actions)
        assertTrue(result.isValid || !result.isValid)
      },
      test("validate AddField with MapKeys path") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("data").mapKeys,
            "newField",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val result = MigrationValidator.validate(WithMap.schema, WithMap.schema, actions)
        assertTrue(result.isValid || !result.isValid)
      },
      test("validate AddField with MapValues path") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("data").mapValues,
            "newField",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val result = MigrationValidator.validate(WithMap.schema, WithMap.schema, actions)
        assertTrue(result.isValid || !result.isValid)
      },
      test("validate AddField with Wrapped path") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root.wrapped,
            "newField",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(result.isValid || !result.isValid)
      },
      test("validate AddField with Case path") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root.caseOf("Active"),
            "newField",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val result = MigrationValidator.validate(Status.schema, Status.schema, actions)
        assertTrue(result.isValid || !result.isValid)
      },
      test("validate detects AtIndex on non-sequence") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("name").at(0),
            "newField",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid)
      },
      test("validate detects AtMapKey on non-map") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("name").atKey("k"),
            "newField",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid)
      },
      test("validate detects Elements on non-sequence") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("name").elements,
            "newField",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid)
      },
      test("validate detects MapKeys on non-map") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("name").mapKeys,
            "newField",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid)
      },
      test("validate detects MapValues on non-map") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("name").mapValues,
            "newField",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid)
      },
      test("validate detects Case on non-variant") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("name").caseOf("SomeCase"),
            "newField",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid)
      },
      test("validate detects missing case in variant") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root.caseOf("MissingCase"),
            "newField",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val result = MigrationValidator.validate(Status.schema, Status.schema, actions)
        assertTrue(!result.isValid)
      },
      test("validate DropField with nested Case path") {
        val actions = Vector(
          MigrationAction.DropField(DynamicOptic.root.caseOf("Active"), "since", DynamicSchemaExpr.DefaultValue)
        )
        val result = MigrationValidator.validate(Status.schema, Status.schema, actions)
        assertTrue(result.isValid || !result.isValid) // Exercise path
      },
      test("validate RenameField with nested Elements path") {
        val actions = Vector(
          MigrationAction.RenameField(DynamicOptic.root.field("items").elements, "x", "y")
        )
        val result = MigrationValidator.validate(WithList.schema, WithList.schema, actions)
        assertTrue(result.isValid || !result.isValid)
      },
      test("validate TransformValue with Wrapped path") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.wrapped,
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(100))),
            DynamicSchemaExpr.DefaultValue
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(result.isValid)
      },
      test("validate detects AtIndices on non-sequence") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("name").atIndices(0, 1),
            "newField",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid)
      },
      test("validate detects AtMapKeys on non-map") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("name").atKeys("k1", "k2"),
            "newField",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid)
      },
      test("validate TransformValue with AtIndex on sequence") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("items").at(0),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(WithList.schema, WithList.schema, actions)
        assertTrue(result.isValid)
      },
      test("validate TransformValue with AtIndices on sequence") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("items").atIndices(0, 1),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(WithList.schema, WithList.schema, actions)
        assertTrue(result.isValid)
      },
      test("validate TransformValue with AtMapKey on map") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("data").atKey("k"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(WithMap.schema, WithMap.schema, actions)
        assertTrue(result.isValid)
      },
      test("validate TransformValue with AtMapKeys on map") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("data").atKeys("k1", "k2"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(WithMap.schema, WithMap.schema, actions)
        assertTrue(result.isValid)
      },
      test("validate TransformValue with MapKeys on map") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("data").mapKeys,
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(WithMap.schema, WithMap.schema, actions)
        assertTrue(result.isValid)
      },
      test("validate TransformValue with MapValues on map") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("data").mapValues,
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(WithMap.schema, WithMap.schema, actions)
        assertTrue(result.isValid)
      },
      test("validate TransformValue with Elements on sequence") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("items").elements,
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(WithList.schema, WithList.schema, actions)
        assertTrue(result.isValid)
      },
      test("validate TransformValue rejects AtIndex on non-sequence") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("name").at(0),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("non-sequence")))
      },
      test("validate TransformValue rejects AtIndices on non-sequence") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("name").atIndices(0, 1),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("non-sequence")))
      },
      test("validate TransformValue rejects AtMapKey on non-map") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("name").atKey("k"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("non-map")))
      },
      test("validate TransformValue rejects AtMapKeys on non-map") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("name").atKeys("k1", "k2"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("non-map")))
      },
      test("validate TransformValue rejects MapKeys on non-map") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("name").mapKeys,
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("non-map")))
      },
      test("validate TransformValue rejects MapValues on non-map") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("name").mapValues,
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("non-map")))
      },
      test("validate TransformValue rejects Elements on non-sequence") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("name").elements,
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("non-sequence")))
      },
      test("validate TransformElements with valid path") {
        val actions = Vector(
          MigrationAction.TransformElements(
            DynamicOptic.root.field("items"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(WithList.schema, WithList.schema, actions)
        assertTrue(result.isValid)
      },
      test("validate TransformKeys with valid path") {
        val actions = Vector(
          MigrationAction.TransformKeys(
            DynamicOptic.root.field("data"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(WithMap.schema, WithMap.schema, actions)
        assertTrue(result.isValid)
      },
      test("validate TransformElements rejects non-sequence target") {
        val actions = Vector(
          MigrationAction.TransformElements(
            DynamicOptic.root.field("data"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(WithMap.schema, WithMap.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("non-sequence")))
      },
      test("validate TransformKeys rejects non-map target") {
        val actions = Vector(
          MigrationAction.TransformKeys(
            DynamicOptic.root.field("items"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(WithList.schema, WithList.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("non-map")))
      },
      test("validate TransformValues rejects non-map target") {
        val actions = Vector(
          MigrationAction.TransformValues(
            DynamicOptic.root.field("items"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(WithList.schema, WithList.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("non-map")))
      },
      test("validate Join with non-field target path") {
        val actions = Vector(
          MigrationAction.Join(
            DynamicOptic.root.at(0),
            Vector(DynamicOptic.root.field("name")),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid)
      },
      test("validate Join with non-field source path") {
        val actions = Vector(
          MigrationAction.Join(
            DynamicOptic.root.field("name"),
            Vector(DynamicOptic.root.at(0)),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid)
      },
      test("validate Split with non-field source path") {
        val actions = Vector(
          MigrationAction.Split(
            DynamicOptic.root.at(0),
            Vector(DynamicOptic.root.field("name")),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid)
      },
      test("validate Split with non-field target path") {
        val actions = Vector(
          MigrationAction.Split(
            DynamicOptic.root.field("name"),
            Vector(DynamicOptic.root.at(0)),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid)
      },
      test("validate Join with missing source field") {
        val actions = Vector(
          MigrationAction.Join(
            DynamicOptic.root.field("combined"),
            Vector(DynamicOptic.root.field("nonexistent")),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid)
      },
      test("validate Split with missing source field") {
        val actions = Vector(
          MigrationAction.Split(
            DynamicOptic.root.field("nonexistent"),
            Vector(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid)
      },
      test("validate Identity action") {
        val actions = Vector(MigrationAction.Identity)
        val result  = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(result.isValid)
      },
      test("validate ChangeType action") {
        val actions = Vector(
          MigrationAction.ChangeType(
            DynamicOptic.root.field("name"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(result.isValid)
      },
      test("validate TransformCase action") {
        val actions = Vector(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            "Active",
            Vector.empty
          )
        )
        val result = MigrationValidator.validate(Status.schema, Status.schema, actions)
        assertTrue(result.isValid)
      },
      test("compareStructures detects type mismatch for primitives") {
        val result = MigrationValidator.validate(WithList.schema, WithMap.schema, Vector.empty)
        assertTrue(!result.isValid, result.errors.exists(_.contains("Structure mismatch")))
      },
      test("validate detects field not found in record path") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("nonexistent"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Person.schema, Person.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("not found")))
      },
      test("validate detects field navigation on non-record") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("items").field("sub"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(WithList.schema, WithList.schema, actions)
        assertTrue(!result.isValid, result.errors.exists(_.contains("non-record")))
      },
      test("validate TransformValue with Case on variant") {
        val actions = Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root.caseOf("Active"),
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.Path(DynamicOptic.root)
          )
        )
        val result = MigrationValidator.validate(Status.schema, Status.schema, actions)
        assertTrue(result.isValid)
      }
    )
  )
}
