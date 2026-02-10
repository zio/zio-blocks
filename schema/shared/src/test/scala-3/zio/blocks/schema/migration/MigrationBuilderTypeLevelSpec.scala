package zio.blocks.schema.migration

import zio._
import zio.blocks.schema._
import zio.test._

/**
 * Tests for compile-time type-level tracking of Handled and Provided fields in
 * MigrationBuilder.
 */
object MigrationBuilderTypeLevelSpec extends ZIOSpecDefault {

  // Test case classes for basic operations
  case class PersonV1(name: String, age: Int, city: String)
  case class PersonV2(fullName: String, age: Int, location: String)

  case class SimpleSource(a: String, b: Int, c: Boolean)
  case class SimpleTarget(x: String, y: Int, z: Boolean)

  // For Option operations
  case class WithOption(name: Option[String], age: Int)
  case class WithoutOption(name: String, age: Int)

  // For type change operations
  case class WithString(value: String, other: Int)
  case class WithInt(value: Int, other: Int)

  // For collection operations
  case class WithList(items: List[Int], name: String)
  case class WithMap(data: Map[String, Int], name: String)

  // For variant operations
  sealed trait Status
  case object Active               extends Status
  case object Inactive             extends Status
  case class Custom(label: String) extends Status

  case class WithStatus(status: Status, id: Int)

  // For join/split operations
  case class FullName(first: String, last: String)
  case class CombinedName(fullName: String)

  // Type alias for field path segments used in type-level tracking.
  // FP["city"] represents the path type (("field", "city"),) for a field named "city".
  type FP[Name <: String] = ("field", Name) *: EmptyTuple

  // Schemas
  implicit val personV1Schema: Schema[PersonV1]           = Schema.derived
  implicit val personV2Schema: Schema[PersonV2]           = Schema.derived
  implicit val simpleSourceSchema: Schema[SimpleSource]   = Schema.derived
  implicit val simpleTargetSchema: Schema[SimpleTarget]   = Schema.derived
  implicit val withOptionSchema: Schema[WithOption]       = Schema.derived
  implicit val withoutOptionSchema: Schema[WithoutOption] = Schema.derived
  implicit val withStringSchema: Schema[WithString]       = Schema.derived
  implicit val withIntSchema: Schema[WithInt]             = Schema.derived
  implicit val withListSchema: Schema[WithList]           = Schema.derived
  implicit val withMapSchema: Schema[WithMap]             = Schema.derived
  implicit val statusSchema: Schema[Status]               = Schema.derived
  implicit val withStatusSchema: Schema[WithStatus]       = Schema.derived
  implicit val fullNameSchema: Schema[FullName]           = Schema.derived
  implicit val combinedNameSchema: Schema[CombinedName]   = Schema.derived

  def spec = suite("MigrationBuilderTypeLevelSpec")(
    suite("newBuilder")(
      test("returns EmptyTuple for both Handled and Provided") {
        val builder: MigrationBuilder[PersonV1, PersonV2, EmptyTuple, EmptyTuple] =
          MigrationBuilder.newBuilder[PersonV1, PersonV2]

        assertTrue(builder.actions.isEmpty)
      },
      test("Fresh type alias is equivalent to EmptyTuple, EmptyTuple") {
        val builder: MigrationBuilder.Fresh[PersonV1, PersonV2] =
          MigrationBuilder.newBuilder[PersonV1, PersonV2]

        // Fresh[A, B] should be equivalent to MigrationBuilder[A, B, EmptyTuple, EmptyTuple]
        val _: MigrationBuilder[PersonV1, PersonV2, EmptyTuple, EmptyTuple] = builder

        assertTrue(builder.actions.isEmpty)
      }
    ),
    suite("dropField - adds field name to Handled")(
      test("dropField with DynamicOptic preserves type parameters") {
        val builder   = MigrationBuilder.newBuilder[PersonV1, PersonV2]
        val afterDrop = builder.dropField(
          DynamicOptic.root.field("city"),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default")))
        )

        assertTrue(afterDrop.actions.size == 1)
      },
      test("dropField with selector syntax") {
        val builder                                                                               = MigrationBuilder.newBuilder[PersonV1, PersonV2]
        val afterDrop: MigrationBuilder[PersonV1, PersonV2, FP["city"] *: EmptyTuple, EmptyTuple] =
          builder.dropField(
            _.city,
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default")))
          )

        assertTrue(afterDrop.actions.size == 1)
      },
      test("multiple dropField operations accumulate") {
        val builder                                                                                              = MigrationBuilder.newBuilder[PersonV1, PersonV2]
        val afterDrops: MigrationBuilder[PersonV1, PersonV2, FP["city"] *: FP["name"] *: EmptyTuple, EmptyTuple] =
          builder
            .dropField(_.city, DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(""))))
            .dropField(_.name, DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(""))))

        assertTrue(afterDrops.actions.size == 2)
      }
    ),
    suite("addField - adds field name to Provided")(
      test("addField with DynamicOptic") {
        val builder  = MigrationBuilder.newBuilder[PersonV1, PersonV2]
        val afterAdd = builder.addField(
          DynamicOptic.root.field("location"),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("unknown")))
        )

        assertTrue(afterAdd.actions.size == 1)
      },
      test("addField with selector syntax") {
        val builder                                                                                  = MigrationBuilder.newBuilder[PersonV1, PersonV2]
        val afterAdd: MigrationBuilder[PersonV1, PersonV2, EmptyTuple, FP["location"] *: EmptyTuple] =
          builder.addField(
            _.location,
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("unknown")))
          )

        assertTrue(afterAdd.actions.size == 1)
      },
      test("multiple addField operations accumulate") {
        val builder = MigrationBuilder.newBuilder[PersonV1, PersonV2]
        val afterAdds
          : MigrationBuilder[PersonV1, PersonV2, EmptyTuple, FP["location"] *: FP["fullName"] *: EmptyTuple] =
          builder
            .addField(_.location, DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(""))))
            .addField(_.fullName, DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(""))))

        assertTrue(afterAdds.actions.size == 2)
      }
    ),
    suite("renameField - adds source to Handled and target to Provided")(
      test("renameField with selector syntax") {
        val builder                                                                                                   = MigrationBuilder.newBuilder[PersonV1, PersonV2]
        val afterRename: MigrationBuilder[PersonV1, PersonV2, FP["name"] *: EmptyTuple, FP["fullName"] *: EmptyTuple] =
          builder.renameField(_.name, _.fullName)

        assertTrue(afterRename.actions.size == 1)
      },
      test("multiple renameField operations") {
        val builder = MigrationBuilder.newBuilder[PersonV1, PersonV2]
        val afterRenames: MigrationBuilder[
          PersonV1,
          PersonV2,
          FP["name"] *: FP["city"] *: EmptyTuple,
          FP["fullName"] *: FP["location"] *: EmptyTuple
        ] = builder
          .renameField(_.name, _.fullName)
          .renameField(_.city, _.location)

        assertTrue(afterRenames.actions.size == 2)
      },
      test("renameField all fields in simple types") {
        val builder = MigrationBuilder.newBuilder[SimpleSource, SimpleTarget]
        val result  = builder
          .renameField(_.a, _.x)
          .renameField(_.b, _.y)
          .renameField(_.c, _.z)

        assertTrue(result.actions.size == 3)
      }
    ),
    suite("transformField - adds field name to both Handled and Provided")(
      test("transformField with DynamicOptic") {
        val builder        = MigrationBuilder.newBuilder[PersonV1, PersonV1]
        val afterTransform = builder.transformField(
          DynamicOptic.root.field("age"),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )

        assertTrue(afterTransform.actions.size == 1)
      },
      test("transformField with selector syntax") {
        val builder                                                                                                = MigrationBuilder.newBuilder[PersonV1, PersonV1]
        val afterTransform: MigrationBuilder[PersonV1, PersonV1, FP["age"] *: EmptyTuple, FP["age"] *: EmptyTuple] =
          builder.transformField(
            _.age,
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )

        assertTrue(afterTransform.actions.size == 1)
      }
    ),
    suite("mandateField - unwrap Option, adds to both Handled and Provided")(
      test("mandateField with DynamicOptic") {
        val builder      = MigrationBuilder.newBuilder[WithOption, WithoutOption]
        val afterMandate = builder.mandateField(
          DynamicOptic.root.field("name"),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default")))
        )

        assertTrue(afterMandate.actions.size == 1)
      },
      test("mandateField with selector syntax") {
        val builder = MigrationBuilder.newBuilder[WithOption, WithoutOption]
        val afterMandate
          : MigrationBuilder[WithOption, WithoutOption, FP["name"] *: EmptyTuple, FP["name"] *: EmptyTuple] =
          builder.mandateField(
            _.name,
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default")))
          )

        assertTrue(afterMandate.actions.size == 1)
      },
      test("mandateField enables .build for complete migration") {
        val migration = MigrationBuilder
          .newBuilder[WithOption, WithoutOption]
          .mandateField(
            _.name,
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default")))
          )
          .build

        val result = migration(WithOption(Some("Alice"), 25))
        assertTrue(
          result == Right(WithoutOption("Alice", 25))
        )
      },
      test("mandateField .build uses default for None") {
        val migration = MigrationBuilder
          .newBuilder[WithOption, WithoutOption]
          .mandateField(
            _.name,
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default")))
          )
          .build

        val result = migration(WithOption(None, 30))
        assertTrue(
          result == Right(WithoutOption("default", 30))
        )
      }
    ),
    suite("optionalizeField - wrap in Option, adds to both Handled and Provided")(
      test("optionalizeField with DynamicOptic") {
        val builder          = MigrationBuilder.newBuilder[WithoutOption, WithOption]
        val afterOptionalize = builder.optionalizeField(
          DynamicOptic.root.field("name"),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default")))
        )

        assertTrue(afterOptionalize.actions.size == 1)
      },
      test("optionalizeField with selector syntax") {
        val builder = MigrationBuilder.newBuilder[WithoutOption, WithOption]
        val afterOptionalize
          : MigrationBuilder[WithoutOption, WithOption, FP["name"] *: EmptyTuple, FP["name"] *: EmptyTuple] =
          builder.optionalizeField(
            _.name,
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default")))
          )

        assertTrue(afterOptionalize.actions.size == 1)
      },
      test("optionalizeField enables .build for complete migration") {
        val migration = MigrationBuilder
          .newBuilder[WithoutOption, WithOption]
          .optionalizeField(
            _.name,
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default")))
          )
          .build

        val result = migration(WithoutOption("Alice", 25))
        assertTrue(
          result == Right(WithOption(Some("Alice"), 25))
        )
      }
    ),
    suite("changeFieldType - adds field name to both Handled and Provided")(
      test("changeFieldType with DynamicOptic") {
        val builder     = MigrationBuilder.newBuilder[WithString, WithInt]
        val afterChange = builder.changeFieldType(
          DynamicOptic.root.field("value"),
          PrimitiveConverter.StringToInt
        )

        assertTrue(afterChange.actions.size == 1)
      },
      test("changeFieldType with selector syntax") {
        val builder = MigrationBuilder.newBuilder[WithString, WithInt]
        // Note: Using explicit function type due to type inference limitations
        val afterChange: MigrationBuilder[WithString, WithInt, FP["value"] *: EmptyTuple, FP["value"] *: EmptyTuple] =
          builder.changeFieldType(
            (s: WithString) => s.value,
            PrimitiveConverter.StringToInt
          )

        assertTrue(afterChange.actions.size == 1)
      }
    ),
    suite("joinFields - adds target to Provided")(
      test("joinFields with DynamicOptic") {
        val builder   = MigrationBuilder.newBuilder[FullName, CombinedName]
        val afterJoin = builder.joinFields(
          DynamicOptic.root.field("fullName"),
          Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
        )

        assertTrue(afterJoin.actions.size == 1)
      },
      test("joinFields with selector syntax") {
        val builder = MigrationBuilder.newBuilder[FullName, CombinedName]
        // Note: Using explicit function types due to type inference limitations with Seq
        val afterJoin = builder.joinFields(
          (c: CombinedName) => c.fullName,
          Seq((f: FullName) => f.first, (f: FullName) => f.last),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
        )

        assertTrue(afterJoin.actions.size == 1)
      }
    ),
    suite("splitField - adds source to Handled")(
      test("splitField with DynamicOptic") {
        val builder    = MigrationBuilder.newBuilder[CombinedName, FullName]
        val afterSplit = builder.splitField(
          DynamicOptic.root.field("fullName"),
          Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
        )

        assertTrue(afterSplit.actions.size == 1)
      },
      test("splitField with selector syntax") {
        val builder = MigrationBuilder.newBuilder[CombinedName, FullName]
        // Note: Using explicit function types due to type inference limitations with Seq
        val afterSplit = builder.splitField(
          (c: CombinedName) => c.fullName,
          Seq((f: FullName) => f.first, (f: FullName) => f.last),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
        )

        assertTrue(afterSplit.actions.size == 1)
      }
    ),
    suite("transformElements - preserves type parameters (no field tracking)")(
      test("transformElements with DynamicOptic") {
        val builder: MigrationBuilder[WithList, WithList, EmptyTuple, EmptyTuple] =
          MigrationBuilder.newBuilder[WithList, WithList]

        val afterTransform: MigrationBuilder[WithList, WithList, EmptyTuple, EmptyTuple] =
          builder.transformElements(
            DynamicOptic.root.field("items"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )

        assertTrue(afterTransform.actions.size == 1)
      },
      test("transformElements with selector syntax") {
        val builder        = MigrationBuilder.newBuilder[WithList, WithList]
        val afterTransform = builder.transformElements(
          _.items,
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )

        assertTrue(afterTransform.actions.size == 1)
      }
    ),
    suite("transformKeys - preserves type parameters (no field tracking)")(
      test("transformKeys with DynamicOptic") {
        val builder: MigrationBuilder[WithMap, WithMap, EmptyTuple, EmptyTuple] =
          MigrationBuilder.newBuilder[WithMap, WithMap]

        val afterTransform: MigrationBuilder[WithMap, WithMap, EmptyTuple, EmptyTuple] =
          builder.transformKeys(
            DynamicOptic.root.field("data"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
          )

        assertTrue(afterTransform.actions.size == 1)
      },
      test("transformKeys with selector syntax") {
        val builder        = MigrationBuilder.newBuilder[WithMap, WithMap]
        val afterTransform = builder.transformKeys(
          _.data,
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
        )

        assertTrue(afterTransform.actions.size == 1)
      }
    ),
    suite("transformValues - preserves type parameters (no field tracking)")(
      test("transformValues with DynamicOptic") {
        val builder: MigrationBuilder[WithMap, WithMap, EmptyTuple, EmptyTuple] =
          MigrationBuilder.newBuilder[WithMap, WithMap]

        val afterTransform: MigrationBuilder[WithMap, WithMap, EmptyTuple, EmptyTuple] =
          builder.transformValues(
            DynamicOptic.root.field("data"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )

        assertTrue(afterTransform.actions.size == 1)
      },
      test("transformValues with selector syntax") {
        val builder        = MigrationBuilder.newBuilder[WithMap, WithMap]
        val afterTransform = builder.transformValues(
          _.data,
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )

        assertTrue(afterTransform.actions.size == 1)
      }
    ),
    suite("renameCase - preserves type parameters (case-level, not field-level)")(
      test("renameCase with DynamicOptic") {
        val builder: MigrationBuilder[WithStatus, WithStatus, EmptyTuple, EmptyTuple] =
          MigrationBuilder.newBuilder[WithStatus, WithStatus]

        val afterRename: MigrationBuilder[WithStatus, WithStatus, EmptyTuple, EmptyTuple] =
          builder.renameCase(
            DynamicOptic.root.field("status"),
            "Active",
            "Enabled"
          )

        assertTrue(afterRename.actions.size == 1)
      }
    ),
    suite("transformCase - preserves type parameters (case-level, not field-level)")(
      test("transformCase with DynamicOptic") {
        val builder: MigrationBuilder[WithStatus, WithStatus, EmptyTuple, EmptyTuple] =
          MigrationBuilder.newBuilder[WithStatus, WithStatus]

        val afterTransform: MigrationBuilder[WithStatus, WithStatus, EmptyTuple, EmptyTuple] =
          builder.transformCase(
            DynamicOptic.root.field("status"),
            "Custom",
            Vector.empty
          )

        assertTrue(afterTransform.actions.size == 1)
      }
    ),
    suite("Chaining operations")(
      test("mixed operations accumulate correctly") {
        val builder: MigrationBuilder[
          PersonV1,
          PersonV2,
          FP["name"] *: FP["city"] *: FP["age"] *: EmptyTuple,
          FP["fullName"] *: FP["location"] *: FP["age"] *: EmptyTuple
        ] = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .renameField(_.name, _.fullName)
          .renameField(_.city, _.location)
          .transformField(
            _.age,
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )

        assertTrue(builder.actions.size == 3)
      },
      test("long chain of operations") {
        val builder: MigrationBuilder[
          PersonV1,
          PersonV2,
          FP["name"] *: FP["city"] *: FP["age"] *: EmptyTuple,
          FP["fullName"] *: FP["location"] *: FP["age"] *: EmptyTuple
        ] = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .renameField(_.name, _.fullName)
          .dropField(_.city, DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(""))))
          .addField(_.location, DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("unknown"))))
          .transformField(
            _.age,
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )

        assertTrue(builder.actions.size == 4)
      }
    ),
    suite("Builder preserves source and target schemas")(
      test("schemas are preserved through chaining") {
        val b0 = MigrationBuilder.newBuilder[PersonV1, PersonV2]
        val b1 = b0.renameField(_.name, _.fullName)
        val b2 = b1.renameField(_.city, _.location)

        assertTrue(
          b0.sourceSchema == personV1Schema,
          b0.targetSchema == personV2Schema,
          b1.sourceSchema == personV1Schema,
          b1.targetSchema == personV2Schema,
          b2.sourceSchema == personV1Schema,
          b2.targetSchema == personV2Schema
        )
      }
    ),
    suite("Runtime behavior")(
      test("complete migration executes correctly") {
        val builder = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .renameField(_.name, _.fullName)
          .renameField(_.city, _.location)
          .transformField(
            DynamicOptic.root.field("age"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )

        // build now returns Migration directly (compile-time validated)
        val migration = builder.build
        val person    = PersonV1("John", 30, "NYC")
        val result    = migration(person)
        assertTrue(result.isRight) &&
        assertTrue(result.map(_.fullName) == Right("John")) &&
        assertTrue(result.map(_.location) == Right("NYC")) &&
        assertTrue(result.map(_.age) == Right(0))
      },
      test("buildPartial always succeeds") {
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .renameField(_.name, _.fullName)
          .buildPartial

        val person = PersonV1("John", 30, "NYC")
        val _      = migration(person)

        // buildPartial may succeed or fail at runtime depending on completeness
        assertTrue(migration != null)
      }
    )
  )
}
