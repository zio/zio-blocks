package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationBuilderSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("MigrationBuilder")(
    suite("String-based API")(
      test("addField adds a field using path string") {
        val builder = MigrationBuilder[PersonV1, PersonV2]
          .addField("age", DynamicValue.int(0))
        val migration = builder.buildPartial
        val original  = PersonV1("Alice")
        val result    = migration(original)

        assertTrue(result == Right(PersonV2("Alice", 0)))
      },
      test("dropField removes a field using path string") {
        val builder = MigrationBuilder[PersonV2, PersonV1]
          .dropField("age", DynamicValue.int(0))
        val migration = builder.buildPartial
        val original  = PersonV2("Alice", 30)
        val result    = migration(original)

        assertTrue(result == Right(PersonV1("Alice")))
      },
      test("renameField renames a field using path strings") {
        val builder = MigrationBuilder[PersonV1, PersonRenamed]
          .renameField("name", "fullName")
        val migration = builder.buildPartial
        val original  = PersonV1("Alice")
        val result    = migration(original)

        assertTrue(result == Right(PersonRenamed("Alice")))
      },
      test("changeFieldType converts field type") {
        val builder = MigrationBuilder[PersonWithIntId, PersonWithLongId]
          .changeFieldType("id", PrimitiveConversion.IntToLong, PrimitiveConversion.LongToInt)
        val migration = builder.buildPartial
        val original  = PersonWithIntId("Alice", 42)
        val result    = migration(original)

        assertTrue(result == Right(PersonWithLongId("Alice", 42L)))
      },
      test("nested path string works for deeply nested fields") {
        val builder = MigrationBuilder[NestedV1, NestedV2]
          .addField("person.contact.phone", DynamicValue.string("555-0000"))
        val migration = builder.buildPartial
        val original  = NestedV1(PersonWithContact("Alice", Contact("alice@example.com")))
        val result    = migration(original)

        assertTrue(
          result == Right(NestedV2(PersonWithContactV2("Alice", ContactV2("alice@example.com", "555-0000"))))
        )
      },
      test("multiple field operations chain correctly") {
        val builder = MigrationBuilder[PersonV1, PersonV3]
          .addField("age", DynamicValue.int(0))
          .addField("active", DynamicValue.boolean(true))
        val migration = builder.buildPartial
        val original  = PersonV1("Alice")
        val result    = migration(original)

        assertTrue(result == Right(PersonV3("Alice", 0, true)))
      },
      test("optionalizeField with simple path wraps field in Some") {
        val builder = MigrationBuilder[PersonV1, PersonV1]
          .optionalizeField("name", DynamicValue.string("default"))
        val step = builder.step
        assertTrue(step.fieldActions.nonEmpty)
      },
      test("mandateField with simple path unwraps optional field") {
        val builder = MigrationBuilder[PersonV1, PersonV1]
          .mandateField("name", DynamicValue.string("default"))
        val step = builder.step
        assertTrue(step.fieldActions.nonEmpty)
      }
    ),
    suite("Builder chaining")(
      test("builder is immutable and chainable") {
        val base      = MigrationBuilder[PersonV1, PersonV2]
        val withAge   = base.addField("age", DynamicValue.int(25))
        val migration = withAge.buildPartial
        val original  = PersonV1("Alice")
        val result    = migration(original)

        assertTrue(result == Right(PersonV2("Alice", 25)))
      },
      test("fromBuilder provides fluent API") {
        val migration = MigrationBuilder
          .from[PersonV1]
          .to[PersonV2]
          .addField("age", DynamicValue.int(18))
          .buildPartial
        val result = migration(PersonV1("Bob"))

        assertTrue(result == Right(PersonV2("Bob", 18)))
      },
      test("exprToDynamicValue throws for non-Literal expressions") {
        val fieldExpr = MigrationExpr.field[PersonV1, String](DynamicOptic.root.field("name"))

        val caught = try {
          val builder = MigrationBuilder[PersonV1, PersonV2]
          builder.addFieldExpr(DynamicOptic.root.field("age"), fieldExpr)
          None
        } catch {
          case e: UnsupportedOperationException => Some(e.getMessage)
          case _: Throwable                     => None
        }

        assertTrue(
          caught.isDefined,
          caught.exists(_.contains("Only MigrationExpr.literal()"))
        )
      }
    ),
    suite("Variant operations")(
      test("renameCase renames enum case") {
        val builder = MigrationBuilder[StatusV1, StatusV2]
          .renameCase("Active", "Enabled")
        val migration = builder.buildPartial
        val original  = StatusV1.Active
        val result    = migration(original)

        assertTrue(result == Right(StatusV2.Enabled))
      }
    ),
    suite("Dynamic migration extraction")(
      test("toDynamicMigration returns serializable migration") {
        val builder = MigrationBuilder[PersonV1, PersonV2]
          .addField("age", DynamicValue.int(0))
        val dynamic = builder.toDynamicMigration

        val original = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val result   = dynamic(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "name" -> DynamicValue.string("Alice"),
              "age"  -> DynamicValue.int(0)
            )
          )
        )
      }
    ),
    suite("nested path operations")(
      test("optionalizeField on nested path wraps field in Some") {
        val original = DynamicValue.Record(
          "outer" -> DynamicValue.Record(
            "inner" -> DynamicValue.int(42)
          )
        )
        val step = MigrationStep.Record.empty
          .nested("outer")(_.makeFieldOptional("inner", DynamicValue.Null))
        val migration = DynamicMigration(step)
        val result    = migration(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap   = fields.toVector.toMap
            val outer      = fieldMap("outer").asInstanceOf[DynamicValue.Record]
            val innerMap   = outer.fields.toVector.toMap
            val innerField = innerMap.get("inner")
            innerField match {
              case Some(DynamicValue.Variant("Some", _)) => assertTrue(true)
              case _                                     => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      },
      test("mandateField on nested path unwraps Some") {
        val original = DynamicValue.Record(
          "outer" -> DynamicValue.Record(
            "inner" -> DynamicValue.Variant("Some", DynamicValue.Record("value" -> DynamicValue.string("test")))
          )
        )
        val step = MigrationStep.Record.empty
          .nested("outer")(_.makeFieldRequired("inner", DynamicValue.string("default")))
        val migration = DynamicMigration(step)
        val result    = migration(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap   = fields.toVector.toMap
            val outer      = fieldMap("outer").asInstanceOf[DynamicValue.Record]
            val innerMap   = outer.fields.toVector.toMap
            val innerField = innerMap.get("inner")
            assertTrue(innerField == Some(DynamicValue.string("test")))
          case _ => assertTrue(false)
        }
      },
      test("transformCase applies nested record step to variant case") {
        val original = DynamicValue.Variant(
          "MyCase",
          DynamicValue.Record("value" -> DynamicValue.int(1))
        )
        val step = MigrationStep.Variant.empty
          .transformCase("MyCase")(_.addField("extra", DynamicValue.string("added")))
        val migration = DynamicMigration(step)
        val result    = migration(original)

        result match {
          case Right(DynamicValue.Variant(caseName, payload)) =>
            val fieldMap = payload.asInstanceOf[DynamicValue.Record].fields.toVector.toMap
            assertTrue(
              caseName == "MyCase",
              fieldMap.get("value") == Some(DynamicValue.int(1)),
              fieldMap.get("extra") == Some(DynamicValue.string("added"))
            )
          case _ => assertTrue(false)
        }
      },
      test("deeply nested transformElements") {
        val original = DynamicValue.Record(
          "level1" -> DynamicValue.Record(
            "level2" -> DynamicValue.Record(
              "items" -> DynamicValue.Sequence(
                DynamicValue.Record("name" -> DynamicValue.string("item1"))
              )
            )
          )
        )
        val step = MigrationStep.Record.empty
          .nested("level1") { l1 =>
            l1.nested("level2") { l2 =>
              l2.copy(nestedFields =
                l2.nestedFields + ("items" -> MigrationStep.Sequence(
                  MigrationStep.Record.empty.addField("count", DynamicValue.int(0))
                ))
              )
            }
          }
        val migration = DynamicMigration(step)
        val result    = migration(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap = fields.toVector.toMap
            val l1       = fieldMap("level1").asInstanceOf[DynamicValue.Record]
            val l1Map    = l1.fields.toVector.toMap
            val l2       = l1Map("level2").asInstanceOf[DynamicValue.Record]
            val l2Map    = l2.fields.toVector.toMap
            val items    = l2Map("items").asInstanceOf[DynamicValue.Sequence]
            val itemMap  = items.elements.head.asInstanceOf[DynamicValue.Record].fields.toVector.toMap
            assertTrue(
              itemMap.get("name") == Some(DynamicValue.string("item1")),
              itemMap.get("count") == Some(DynamicValue.int(0))
            )
          case _ => assertTrue(false)
        }
      },
      test("transformKeys on map field") {
        val original = DynamicValue.Record(
          "data" -> DynamicValue.Map(
            DynamicValue.Record("id" -> DynamicValue.string("k1")) -> DynamicValue.int(1)
          )
        )
        val step = MigrationStep.Record.empty
          .copy(nestedFields =
            Map(
              "data" -> MigrationStep.MapEntries(
                MigrationStep.Record.empty.renameField("id", "key"),
                MigrationStep.NoOp
              )
            )
          )
        val migration = DynamicMigration(step)
        val result    = migration(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap  = fields.toVector.toMap
            val dataMap   = fieldMap("data").asInstanceOf[DynamicValue.Map]
            val (key, _)  = dataMap.entries.head
            val keyFields = key.asInstanceOf[DynamicValue.Record].fields.toVector.toMap
            assertTrue(keyFields.contains("key") && !keyFields.contains("id"))
          case _ => assertTrue(false)
        }
      },
      test("transformValues on map field") {
        val original = DynamicValue.Record(
          "data" -> DynamicValue.Map(
            DynamicValue.string("k1") -> DynamicValue.Record("value" -> DynamicValue.int(1))
          )
        )
        val step = MigrationStep.Record.empty
          .copy(nestedFields =
            Map(
              "data" -> MigrationStep.MapEntries(
                MigrationStep.NoOp,
                MigrationStep.Record.empty.addField("extra", DynamicValue.string("added"))
              )
            )
          )
        val migration = DynamicMigration(step)
        val result    = migration(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap    = fields.toVector.toMap
            val dataMap     = fieldMap("data").asInstanceOf[DynamicValue.Map]
            val (_, value)  = dataMap.entries.head
            val valueFields = value.asInstanceOf[DynamicValue.Record].fields.toVector.toMap
            assertTrue(
              valueFields.get("value") == Some(DynamicValue.int(1)),
              valueFields.get("extra") == Some(DynamicValue.string("added"))
            )
          case _ => assertTrue(false)
        }
      }
    ),
    suite("field name extraction")(
      test("addedFieldNames extracts direct added fields") {
        val step = MigrationStep.Record.empty
          .addField("topLevel", DynamicValue.int(1))
          .joinFields("combined", Vector("a", "b"), DynamicValueTransform.identity, DynamicValueTransform.identity)
          .splitField("source", Vector("x", "y"), DynamicValueTransform.identity, DynamicValueTransform.identity)
        val builder = MigrationBuilder(Schema[Unit], Schema[Unit], step, MigrationStep.Variant.empty)

        val added = builder.addedFieldNames
        assertTrue(
          added.contains("topLevel"),
          added.contains("combined"),
          added.contains("x"),
          added.contains("y")
        )
      },
      test("addedFieldNames extracts nested added fields") {
        val step = MigrationStep.Record.empty
          .nested("outer")(_.addField("inner", DynamicValue.int(2)))
        val builder = MigrationBuilder(Schema[Unit], Schema[Unit], step, MigrationStep.Variant.empty)

        val added = builder.addedFieldNames
        assertTrue(added.contains("outer.inner"))
      },
      test("removedFieldNames extracts direct removed fields") {
        val step = MigrationStep.Record.empty
          .removeField("removed", DynamicValue.Null)
          .joinFields("combined", Vector("a", "b"), DynamicValueTransform.identity, DynamicValueTransform.identity)
          .splitField("source", Vector("x", "y"), DynamicValueTransform.identity, DynamicValueTransform.identity)
        val builder = MigrationBuilder(Schema[Unit], Schema[Unit], step, MigrationStep.Variant.empty)

        val removed = builder.removedFieldNames
        assertTrue(
          removed.contains("removed"),
          removed.contains("a"),
          removed.contains("b"),
          removed.contains("source")
        )
      },
      test("renamedFromNames and renamedToNames track renames") {
        val step = MigrationStep.Record.empty
          .renameField("oldName", "newName")
          .nested("parent")(_.renameField("oldInner", "newInner"))
        val builder = MigrationBuilder(Schema[Unit], Schema[Unit], step, MigrationStep.Variant.empty)

        assertTrue(
          builder.renamedFromNames.contains("oldName"),
          builder.renamedFromNames.contains("parent.oldInner"),
          builder.renamedToNames.contains("newName"),
          builder.renamedToNames.contains("parent.newInner")
        )
      }
    ),
    suite("error cases")(
      test("renameField with different parent paths via DynamicMigration") {
        val migration = DynamicMigration.record(_.renameField("field1", "field2"))
        val original  = DynamicValue.Record("field1" -> DynamicValue.int(1))
        val result    = migration(original)
        assertTrue(result == Right(DynamicValue.Record("field2" -> DynamicValue.int(1))))
      },
      test("renameField throws for different parent paths") {
        val caught = try {
          val builder = MigrationBuilder[PersonV1, PersonRenamed]
          builder.renameField("parent.child", "other.child")
          None
        } catch {
          case e: IllegalArgumentException => Some(e.getMessage)
          case _: Throwable                => None
        }
        assertTrue(
          caught.isDefined,
          caught.exists(_.contains("Cannot rename across different parent paths"))
        )
      },
      test("extractFieldNameFromOptic throws for non-field optic") {
        val nonFieldOptic = DynamicOptic.root.at(0)
        val caught        = try {
          val builder = MigrationBuilder[PersonV1, PersonV2]
          builder.addFieldExpr(nonFieldOptic, MigrationExpr.literal(0))
          None
        } catch {
          case e: IllegalArgumentException => Some(e.getMessage)
          case _: Throwable                => None
        }
        assertTrue(
          caught.isDefined,
          caught.exists(_.contains("Expected field optic"))
        )
      },
      test("parsePath throws for empty path") {
        val caught = try {
          val builder = MigrationBuilder[PersonV1, PersonV2]
          builder.addField("", DynamicValue.int(0))
          None
        } catch {
          case e: IllegalArgumentException => Some(e.getMessage)
          case _: Throwable                => None
        }
        assertTrue(
          caught.isDefined,
          caught.exists(_.contains("Invalid empty path"))
        )
      },
      test("combineSteps throws when both record and variant actions are defined") {
        val caught = try {
          val builder = MigrationBuilder(
            Schema[StatusV1],
            Schema[StatusV2],
            MigrationStep.Record.empty.addField("extra", DynamicValue.int(1)),
            MigrationStep.Variant.empty.renameCase("A", "B")
          )
          builder.buildPartial
          None
        } catch {
          case e: IllegalStateException => Some(e.getMessage)
          case _: Throwable             => None
        }
        assertTrue(
          caught.isDefined,
          caught.exists(_.contains("both record field actions and variant case actions"))
        )
      }
    ),
    suite("nested string path API - head :: tail branches")(
      test("renameField with nested path renames field in nested record") {
        val builder = MigrationBuilder[NestedV1, NestedV1]
          .renameField("person.name", "person.fullName")
        val step = builder.step
        assertTrue(step.nestedFields.contains("person"))
      },
      test("transformFieldValue with nested path transforms nested field") {
        val builder = MigrationBuilder[NestedV1, NestedV1]
          .transformFieldValue(
            "person.name",
            DynamicValueTransform.stringAppend("!"),
            DynamicValueTransform.stringReplace("!", "")
          )
        val step = builder.step
        assertTrue(step.nestedFields.contains("person"))
      },
      test("optionalizeField with nested path wraps nested field in Some") {
        val builder = MigrationBuilder[NestedV1, NestedV1]
          .optionalizeField("person.name", DynamicValue.Null)
        val step = builder.step
        assertTrue(step.nestedFields.contains("person"))
      },
      test("mandateField with nested path unwraps nested optional field") {
        val builder = MigrationBuilder[NestedV1, NestedV1]
          .mandateField("person.name", DynamicValue.string("default"))
        val step = builder.step
        assertTrue(step.nestedFields.contains("person"))
      },
      test("changeFieldType with nested path changes nested field type") {
        val builder = MigrationBuilder[NestedV1, NestedV1]
          .changeFieldType("person.age", PrimitiveConversion.IntToLong, PrimitiveConversion.LongToInt)
        val step = builder.step
        assertTrue(step.nestedFields.contains("person"))
      },
      test("joinFields with nested target path joins nested fields") {
        val builder = MigrationBuilder[NestedV1, NestedV1]
          .joinFields(
            "person.fullName",
            Vector("firstName", "lastName"),
            DynamicValueTransform.stringJoinFields(Vector("firstName", "lastName"), " "),
            DynamicValueTransform.stringSplitToFields(Vector("firstName", "lastName"), " ")
          )
        val step = builder.step
        assertTrue(step.nestedFields.contains("person"))
      },
      test("splitField with nested source path splits nested field") {
        val builder = MigrationBuilder[NestedV1, NestedV1]
          .splitField(
            "person.fullName",
            Vector("firstName", "lastName"),
            DynamicValueTransform.stringSplitToFields(Vector("firstName", "lastName"), " "),
            DynamicValueTransform.stringJoinFields(Vector("firstName", "lastName"), " ")
          )
        val step = builder.step
        assertTrue(step.nestedFields.contains("person"))
      },
      test("transformElements with nested path transforms nested sequence elements") {
        val builder = MigrationBuilder[NestedV1, NestedV1]
          .transformElements("person.items")(_.addField("extra", DynamicValue.int(0)))
        val step = builder.step
        assertTrue(step.nestedFields.contains("person"))
      },
      test("transformKeys with nested path transforms nested map keys") {
        val builder = MigrationBuilder[NestedV1, NestedV1]
          .transformKeys("person.data")(_.renameField("id", "key"))
        val step = builder.step
        assertTrue(step.nestedFields.contains("person"))
      },
      test("transformValues with nested path transforms nested map values") {
        val builder = MigrationBuilder[NestedV1, NestedV1]
          .transformValues("person.data")(_.addField("extra", DynamicValue.int(0)))
        val step = builder.step
        assertTrue(step.nestedFields.contains("person"))
      },
      test("addNestedStepAtPath with nested path adds step at correct location") {
        val builder = MigrationBuilder[NestedV1, NestedV1]
          .transformElements("outer.inner.items")(_.addField("new", DynamicValue.boolean(true)))
        val step = builder.step
        assertTrue(step.nestedFields.contains("outer"))
      },
      test("deeply nested path with 3 levels") {
        val builder = MigrationBuilder[NestedV1, NestedV2]
          .addField("person.contact.phone", DynamicValue.string("555-1234"))
        val migration = builder.buildPartial
        val original  = NestedV1(PersonWithContact("Alice", Contact("alice@example.com")))
        val result    = migration(original)
        assertTrue(
          result == Right(NestedV2(PersonWithContactV2("Alice", ContactV2("alice@example.com", "555-1234"))))
        )
      }
    ),
    suite("field extraction with non-Record nested steps")(
      test("addedFieldNames handles non-Record nested step") {
        val step = MigrationStep.Record.empty
          .addField("seqField", DynamicValue.int(1))
          .copy(nestedFields = Map("items" -> MigrationStep.Sequence(MigrationStep.Record.empty)))
        val builder = MigrationBuilder(Schema[Unit], Schema[Unit], step, MigrationStep.Variant.empty)
        val added   = builder.addedFieldNames
        assertTrue(added.contains("seqField"))
      },
      test("removedFieldNames handles non-Record nested step") {
        val step = MigrationStep.Record.empty
          .removeField("old", DynamicValue.Null)
          .copy(nestedFields = Map("items" -> MigrationStep.NoOp))
        val builder = MigrationBuilder(Schema[Unit], Schema[Unit], step, MigrationStep.Variant.empty)
        val removed = builder.removedFieldNames
        assertTrue(removed.contains("old"))
      },
      test("renamedFromNames handles non-Record nested step") {
        val step = MigrationStep.Record.empty
          .renameField("oldName", "newName")
          .copy(nestedFields = Map("items" -> MigrationStep.Sequence(MigrationStep.Record.empty)))
        val builder = MigrationBuilder(Schema[Unit], Schema[Unit], step, MigrationStep.Variant.empty)
        val renamed = builder.renamedFromNames
        assertTrue(renamed.contains("oldName"))
      },
      test("renamedToNames handles non-Record nested step") {
        val step = MigrationStep.Record.empty
          .renameField("oldName", "newName")
          .copy(nestedFields = Map("data" -> MigrationStep.MapEntries(MigrationStep.NoOp, MigrationStep.NoOp)))
        val builder = MigrationBuilder(Schema[Unit], Schema[Unit], step, MigrationStep.Variant.empty)
        val renamed = builder.renamedToNames
        assertTrue(renamed.contains("newName"))
      }
    )
  )
}
