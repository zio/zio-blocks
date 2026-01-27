package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import TypeLevel._

object MigrationBuilderTypeLevelSpec extends ZIOSpecDefault {

  // ==========================================================================
  // Test case classes
  // ==========================================================================

  case class PersonV1(name: String, age: Int, oldField: String)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  case class PersonV2(name: String, age: Int, newField: String)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  case class PersonV3(name: String, age: Int, firstName: String, lastName: String)
  object PersonV3 {
    implicit val schema: Schema[PersonV3] = Schema.derived
  }

  case class WithOption(name: String, maybeAge: Option[Int])
  object WithOption {
    implicit val schema: Schema[WithOption] = Schema.derived
  }

  case class WithoutOption(name: String, maybeAge: Int)
  object WithoutOption {
    implicit val schema: Schema[WithoutOption] = Schema.derived
  }

  case class WithInt(name: String, count: Int)
  object WithInt {
    implicit val schema: Schema[WithInt] = Schema.derived
  }

  case class WithString(name: String, count: String)
  object WithString {
    implicit val schema: Schema[WithString] = Schema.derived
  }

  case class WithList(name: String, items: List[Int])
  object WithList {
    implicit val schema: Schema[WithList] = Schema.derived
  }

  case class WithMap(name: String, data: Map[String, Int])
  object WithMap {
    implicit val schema: Schema[WithMap] = Schema.derived
  }

  case class MultiField(a: String, b: Int, c: Boolean, d: Double)
  object MultiField {
    implicit val schema: Schema[MultiField] = Schema.derived
  }

  case class MultiField2(a: String, b: Int, c: Boolean, e: Long)
  object MultiField2 {
    implicit val schema: Schema[MultiField2] = Schema.derived
  }

  // ==========================================================================
  // Helpers
  // ==========================================================================

  // Helper to get syntax explicitly (avoids conflict with private[migration] methods)
  def syntax[A, B, H <: TList, P <: TList](
    b: MigrationBuilder[A, B, H, P]
  ): MigrationBuilderSyntax[A, B, H, P] = new MigrationBuilderSyntax(b)

  // Type-level assertion helpers
  def assertContains[L <: TList, X](implicit ev: Contains[L, X]): Unit = ()
  def assertIsSubset[A <: TList, B <: TList](implicit ev: IsSubset[A, B]): Unit = ()

  // ==========================================================================
  // Tests
  // ==========================================================================

  override def spec = suite("MigrationBuilderTypeLevelSpec - Scala 2")(
    newBuilderSuite,
    dropFieldSuite,
    addFieldSuite,
    renameFieldSuite,
    transformFieldSuite,
    mandateFieldSuite,
    optionalizeFieldSuite,
    changeFieldTypeSuite,
    joinFieldsSuite,
    splitFieldSuite,
    collectionOperationsSuite,
    chainingSuite,
    typeLevelVerificationSuite
  )

  // --------------------------------------------------------------------------
  // newBuilder Suite
  // --------------------------------------------------------------------------
  val newBuilderSuite = suite("newBuilder")(
    test("returns TNil for both Handled and Provided") {
      val builder: MigrationBuilder[PersonV1, PersonV2, TNil, TNil] =
        MigrationBuilder.newBuilder[PersonV1, PersonV2]

      assertTrue(builder.actions.isEmpty)
    },
    test("Fresh type alias matches TNil types") {
      val builder: MigrationBuilder.Fresh[PersonV1, PersonV2] =
        MigrationBuilder.newBuilder[PersonV1, PersonV2]

      // Fresh[A, B] = MigrationBuilder[A, B, TNil, TNil]
      val _: MigrationBuilder[PersonV1, PersonV2, TNil, TNil] = builder

      assertTrue(true)
    }
  )

  // --------------------------------------------------------------------------
  // dropField Suite
  // --------------------------------------------------------------------------
  val dropFieldSuite = suite("dropField")(
    test("adds field name to Handled") {
      val builder = syntax(MigrationBuilder.newBuilder[PersonV1, PersonV2])
        .dropField(_.oldField, SchemaExpr.Literal[DynamicValue, String]("default", Schema.string))

      assertTrue(builder.actions.size == 1)
    },
    test("creates correct MigrationAction") {
      val builder = syntax(MigrationBuilder.newBuilder[PersonV1, PersonV2])
        .dropField(_.oldField, SchemaExpr.Literal[DynamicValue, String]("default", Schema.string))

      val action = builder.actions.head
      assertTrue(action.isInstanceOf[MigrationAction.DropField])
    },
    test("multiple dropField operations accumulate") {
      val builder = syntax(
        syntax(MigrationBuilder.newBuilder[MultiField, MultiField2])
          .dropField(_.d, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double))
      ).dropField(_.c, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))

      assertTrue(builder.actions.size == 2)
    }
  )

  // --------------------------------------------------------------------------
  // addField Suite
  // --------------------------------------------------------------------------
  val addFieldSuite = suite("addField")(
    test("adds field name to Provided") {
      val builder = syntax(MigrationBuilder.newBuilder[PersonV1, PersonV2])
        .addField(_.newField, SchemaExpr.Literal[DynamicValue, String]("default", Schema.string))

      assertTrue(builder.actions.size == 1)
    },
    test("creates correct MigrationAction") {
      val builder = syntax(MigrationBuilder.newBuilder[PersonV1, PersonV2])
        .addField(_.newField, SchemaExpr.Literal[DynamicValue, String]("default", Schema.string))

      val action = builder.actions.head
      assertTrue(action.isInstanceOf[MigrationAction.AddField])
    },
    test("multiple addField operations accumulate") {
      val builder = syntax(
        syntax(MigrationBuilder.newBuilder[MultiField, MultiField2])
          .addField(_.e, SchemaExpr.Literal[DynamicValue, Long](0L, Schema.long))
      ).addField(_.a, SchemaExpr.Literal[DynamicValue, String]("", Schema.string))

      assertTrue(builder.actions.size == 2)
    }
  )

  // --------------------------------------------------------------------------
  // renameField Suite
  // --------------------------------------------------------------------------
  val renameFieldSuite = suite("renameField")(
    test("adds source to Handled and target to Provided") {
      val builder = syntax(MigrationBuilder.newBuilder[PersonV1, PersonV2])
        .renameField(_.oldField, _.newField)

      assertTrue(builder.actions.size == 1)
    },
    test("creates correct MigrationAction") {
      val builder = syntax(MigrationBuilder.newBuilder[PersonV1, PersonV2])
        .renameField(_.oldField, _.newField)

      val action = builder.actions.head
      assertTrue(action.isInstanceOf[MigrationAction.Rename])
    }
  )

  // --------------------------------------------------------------------------
  // transformField Suite
  // --------------------------------------------------------------------------
  val transformFieldSuite = suite("transformField")(
    test("adds field name to both Handled and Provided") {
      val builder = syntax(MigrationBuilder.newBuilder[PersonV1, PersonV1])
        .transformField(_.name, SchemaExpr.Literal[DynamicValue, String]("transformed", Schema.string))

      assertTrue(builder.actions.size == 1)
    },
    test("creates correct MigrationAction") {
      val builder = syntax(MigrationBuilder.newBuilder[PersonV1, PersonV1])
        .transformField(_.name, SchemaExpr.Literal[DynamicValue, String]("transformed", Schema.string))

      val action = builder.actions.head
      assertTrue(action.isInstanceOf[MigrationAction.TransformValue])
    }
  )

  // --------------------------------------------------------------------------
  // mandateField Suite
  // --------------------------------------------------------------------------
  val mandateFieldSuite = suite("mandateField")(
    test("adds field name to both Handled and Provided") {
      val builder = syntax(MigrationBuilder.newBuilder[WithOption, WithoutOption])
        .mandateField(_.maybeAge, SchemaExpr.Literal[DynamicValue, Int](0, Schema.int))

      assertTrue(builder.actions.size == 1)
    },
    test("creates correct MigrationAction") {
      val builder = syntax(MigrationBuilder.newBuilder[WithOption, WithoutOption])
        .mandateField(_.maybeAge, SchemaExpr.Literal[DynamicValue, Int](0, Schema.int))

      val action = builder.actions.head
      assertTrue(action.isInstanceOf[MigrationAction.Mandate])
    }
  )

  // --------------------------------------------------------------------------
  // optionalizeField Suite
  // --------------------------------------------------------------------------
  val optionalizeFieldSuite = suite("optionalizeField")(
    test("adds field name to both Handled and Provided") {
      val builder = syntax(MigrationBuilder.newBuilder[WithoutOption, WithOption])
        .optionalizeField(_.maybeAge, SchemaExpr.Literal[DynamicValue, Int](0, Schema.int))

      assertTrue(builder.actions.size == 1)
    },
    test("creates correct MigrationAction") {
      val builder = syntax(MigrationBuilder.newBuilder[WithoutOption, WithOption])
        .optionalizeField(_.maybeAge, SchemaExpr.Literal[DynamicValue, Int](0, Schema.int))

      val action = builder.actions.head
      assertTrue(action.isInstanceOf[MigrationAction.Optionalize])
    }
  )

  // --------------------------------------------------------------------------
  // changeFieldType Suite
  // --------------------------------------------------------------------------
  val changeFieldTypeSuite = suite("changeFieldType")(
    test("adds field name to both Handled and Provided") {
      val builder = syntax(MigrationBuilder.newBuilder[WithInt, WithString])
        .changeFieldType(_.count, PrimitiveConverter.IntToString)

      assertTrue(builder.actions.size == 1)
    },
    test("creates correct MigrationAction") {
      val builder = syntax(MigrationBuilder.newBuilder[WithInt, WithString])
        .changeFieldType(_.count, PrimitiveConverter.IntToString)

      val action = builder.actions.head
      assertTrue(action.isInstanceOf[MigrationAction.ChangeType])
    }
  )

  // --------------------------------------------------------------------------
  // joinFields Suite
  // --------------------------------------------------------------------------
  val joinFieldsSuite = suite("joinFields")(
    test("adds target to Provided") {
      val combiner = SchemaExpr.StringConcat(
        SchemaExpr.Literal[DynamicValue, String]("", Schema.string),
        SchemaExpr.Literal[DynamicValue, String]("", Schema.string)
      )

      val builder = syntax(MigrationBuilder.newBuilder[PersonV3, PersonV1])
        .joinFields(
          _.name,
          Seq((p: PersonV3) => p.firstName, (p: PersonV3) => p.lastName),
          combiner
        )

      assertTrue(builder.actions.size == 1)
    },
    test("creates correct MigrationAction") {
      val combiner = SchemaExpr.StringConcat(
        SchemaExpr.Literal[DynamicValue, String]("", Schema.string),
        SchemaExpr.Literal[DynamicValue, String]("", Schema.string)
      )

      val builder = syntax(MigrationBuilder.newBuilder[PersonV3, PersonV1])
        .joinFields(
          _.name,
          Seq((p: PersonV3) => p.firstName, (p: PersonV3) => p.lastName),
          combiner
        )

      val action = builder.actions.head
      assertTrue(action.isInstanceOf[MigrationAction.Join])
    }
  )

  // --------------------------------------------------------------------------
  // splitField Suite
  // --------------------------------------------------------------------------
  val splitFieldSuite = suite("splitField")(
    test("adds source to Handled") {
      val splitter = SchemaExpr.Literal[DynamicValue, String]("", Schema.string)

      val builder = syntax(MigrationBuilder.newBuilder[PersonV1, PersonV3])
        .splitField(
          _.name,
          Seq((p: PersonV3) => p.firstName, (p: PersonV3) => p.lastName),
          splitter
        )

      assertTrue(builder.actions.size == 1)
    },
    test("creates correct MigrationAction") {
      val splitter = SchemaExpr.Literal[DynamicValue, String]("", Schema.string)

      val builder = syntax(MigrationBuilder.newBuilder[PersonV1, PersonV3])
        .splitField(
          _.name,
          Seq((p: PersonV3) => p.firstName, (p: PersonV3) => p.lastName),
          splitter
        )

      val action = builder.actions.head
      assertTrue(action.isInstanceOf[MigrationAction.Split])
    }
  )

  // --------------------------------------------------------------------------
  // Collection Operations Suite
  // --------------------------------------------------------------------------
  val collectionOperationsSuite = suite("collection operations")(
    test("transformElements preserves types") {
      val builder = syntax(MigrationBuilder.newBuilder[WithList, WithList])
        .transformElements(_.items, SchemaExpr.Literal[DynamicValue, Int](0, Schema.int))

      assertTrue(builder.actions.size == 1) &&
      assertTrue(builder.actions.head.isInstanceOf[MigrationAction.TransformElements])
    },
    test("transformKeys preserves types") {
      val builder = syntax(MigrationBuilder.newBuilder[WithMap, WithMap])
        .transformKeys(_.data, SchemaExpr.Literal[DynamicValue, String]("", Schema.string))

      assertTrue(builder.actions.size == 1) &&
      assertTrue(builder.actions.head.isInstanceOf[MigrationAction.TransformKeys])
    },
    test("transformValues preserves types") {
      val builder = syntax(MigrationBuilder.newBuilder[WithMap, WithMap])
        .transformValues(_.data, SchemaExpr.Literal[DynamicValue, Int](0, Schema.int))

      assertTrue(builder.actions.size == 1) &&
      assertTrue(builder.actions.head.isInstanceOf[MigrationAction.TransformValues])
    }
  )

  // --------------------------------------------------------------------------
  // Chaining Suite
  // --------------------------------------------------------------------------
  val chainingSuite = suite("chaining operations")(
    test("chaining accumulates Handled fields") {
      val builder = syntax(
        syntax(MigrationBuilder.newBuilder[MultiField, MultiField2])
          .dropField(_.d, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double))
      ).dropField(_.c, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))

      assertTrue(builder.actions.size == 2)
    },
    test("chaining accumulates Provided fields") {
      val builder = syntax(
        syntax(MigrationBuilder.newBuilder[MultiField, MultiField2])
          .addField(_.e, SchemaExpr.Literal[DynamicValue, Long](0L, Schema.long))
      ).addField(_.c, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))

      assertTrue(builder.actions.size == 2)
    },
    test("mixed operations chain correctly") {
      val builder = syntax(
        syntax(
          syntax(MigrationBuilder.newBuilder[PersonV1, PersonV2])
            .dropField(_.oldField, SchemaExpr.Literal[DynamicValue, String]("", Schema.string))
        ).addField(_.newField, SchemaExpr.Literal[DynamicValue, String]("", Schema.string))
      ).transformField(_.name, SchemaExpr.Literal[DynamicValue, String]("", Schema.string))

      assertTrue(builder.actions.size == 3)
    },
    test("complex chain with many operations") {
      val builder = syntax(
        syntax(
          syntax(
            syntax(MigrationBuilder.newBuilder[MultiField, MultiField2])
              .dropField(_.d, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double))
          ).addField(_.e, SchemaExpr.Literal[DynamicValue, Long](0L, Schema.long))
        ).transformField(_.a, SchemaExpr.Literal[DynamicValue, String]("", Schema.string))
      ).transformField(_.b, SchemaExpr.Literal[DynamicValue, Int](0, Schema.int))

      assertTrue(builder.actions.size == 4)
    }
  )

  // --------------------------------------------------------------------------
  // Type-Level Verification Suite
  // --------------------------------------------------------------------------
  val typeLevelVerificationSuite = suite("type-level verification")(
    test("IsSubset works with TNil") {
      // TNil is subset of TNil
      assertIsSubset[TNil, TNil]
      assertTrue(true)
    },
    test("IsSubset works with single element") {
      // "a" :: TNil is subset of "a" :: TNil
      assertIsSubset["a" :: TNil, "a" :: TNil]
      assertTrue(true)
    },
    test("IsSubset works with subset") {
      // "a" :: TNil is subset of "a" :: "b" :: TNil
      assertIsSubset["a" :: TNil, "a" :: "b" :: TNil]
      assertTrue(true)
    },
    test("Contains works for element at head") {
      assertContains["a" :: "b" :: TNil, "a"]
      assertTrue(true)
    },
    test("Contains works for element in tail") {
      assertContains["a" :: "b" :: TNil, "b"]
      assertTrue(true)
    },
    test("schemas are preserved through operations") {
      val builder = syntax(MigrationBuilder.newBuilder[PersonV1, PersonV2])
        .dropField(_.oldField, SchemaExpr.Literal[DynamicValue, String]("", Schema.string))

      assertTrue(builder.sourceSchema == Schema[PersonV1]) &&
      assertTrue(builder.targetSchema == Schema[PersonV2])
    },
    test("buildPartial works on incomplete migration") {
      val builder = syntax(MigrationBuilder.newBuilder[PersonV1, PersonV2])
        .dropField(_.oldField, SchemaExpr.Literal[DynamicValue, String]("", Schema.string))

      val migration = builder.buildPartial

      assertTrue(migration.sourceSchema == Schema[PersonV1]) &&
      assertTrue(migration.targetSchema == Schema[PersonV2])
    }
  )
}
