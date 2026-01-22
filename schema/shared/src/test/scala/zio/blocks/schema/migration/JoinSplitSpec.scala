package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object JoinSplitSpec extends ZIOSpecDefault {

  // Helper to create a splitter that returns multiple values for testing
  def testSplitter(value: String, delimiter: String): SchemaExpr[DynamicValue, String] = {
    val literal = SchemaExpr.Literal[DynamicValue, String](value, Schema.string)
    SchemaExpr.StringSplit(literal, delimiter)
  }

  def spec = suite("Join and Split Migration Actions")(
    suite("Join Action")(
      test("should combine two string fields into one") {
        // PersonV1: firstName, lastName
        // PersonV2: fullName
        val firstName = DynamicValue.Primitive(PrimitiveValue.String("John"))
        val lastName  = DynamicValue.Primitive(PrimitiveValue.String("Doe"))
        val record    = DynamicValue.Record(Vector("firstName" -> firstName, "lastName" -> lastName))

        // Create Join action: combine firstName + " " + lastName -> fullName
        val firstNameOptic = DynamicOptic.root.field("firstName")
        val lastNameOptic  = DynamicOptic.root.field("lastName")

        // Combiner: For now, just return the expected result as a literal
        val combiner: SchemaExpr[DynamicValue, ?] = SchemaExpr.Literal[DynamicValue, String]("John Doe", Schema.string)

        val joinAction = MigrationAction.Join(
          at = DynamicOptic.root.field("fullName"),
          sourcePaths = Vector(firstNameOptic, lastNameOptic),
          combiner = combiner
        )

        val result = joinAction.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(result.exists {
          case DynamicValue.Record(fields) =>
            fields.exists {
              case ("fullName", DynamicValue.Primitive(PrimitiveValue.String(name))) =>
                name == "John Doe"
              case _ => false
            }
          case _ => false
        })
      },
      test("should combine numeric fields") {
        // Combine quantity + bonus -> totalQuantity
        val quantity = DynamicValue.Primitive(PrimitiveValue.Int(10))
        val bonus    = DynamicValue.Primitive(PrimitiveValue.Int(5))
        val record   = DynamicValue.Record(Vector("quantity" -> quantity, "bonus" -> bonus))

        val quantityOptic = DynamicOptic.root.field("quantity")
        val bonusOptic    = DynamicOptic.root.field("bonus")

        // Combiner: field0 + field1
        val combiner: SchemaExpr[DynamicValue, ?] = SchemaExpr.Literal[DynamicValue, Int](15, Schema.int)

        val joinAction = MigrationAction.Join(
          at = DynamicOptic.root.field("totalQuantity"),
          sourcePaths = Vector(quantityOptic, bonusOptic),
          combiner = combiner
        )

        val result = joinAction.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(result.exists {
          case DynamicValue.Record(fields) =>
            fields.exists {
              case ("totalQuantity", DynamicValue.Primitive(PrimitiveValue.Int(total))) =>
                total == 15
              case _ => false
            }
          case _ => false
        })
      },
      test("should fail if source field does not exist") {
        val record = DynamicValue.Record(Vector("firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John"))))

        val firstNameOptic = DynamicOptic.root.field("firstName")
        val lastNameOptic  = DynamicOptic.root.field("lastName") // Does not exist

        val combiner: SchemaExpr[DynamicValue, ?] = SchemaExpr.Literal[DynamicValue, String]("", Schema.string)

        val joinAction = MigrationAction.Join(
          at = DynamicOptic.root.field("fullName"),
          sourcePaths = Vector(firstNameOptic, lastNameOptic),
          combiner = combiner
        )

        val result = joinAction.execute(record)

        assertTrue(result.isLeft)
      },
      test("reverse should create Split action") {
        val joinAction = MigrationAction.Join(
          at = DynamicOptic.root.field("fullName"),
          sourcePaths = Vector(
            DynamicOptic.root.field("firstName"),
            DynamicOptic.root.field("lastName")
          ),
          combiner = SchemaExpr.Literal[DynamicValue, String]("", Schema.string)
        )

        val reversed = joinAction.reverse

        assertTrue(reversed.isInstanceOf[MigrationAction.Split])
      },
      test("should fail with CrossPathJoinNotSupported when paths don't share same parent") {
        // Create a nested record with different branches:
        // { meta: { id: "123" }, data: { name: "John" } }
        val metaRecord = DynamicValue.Record(Vector("id" -> DynamicValue.Primitive(PrimitiveValue.String("123"))))
        val dataRecord = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))))
        val record     = DynamicValue.Record(Vector("meta" -> metaRecord, "data" -> dataRecord))

        // Try to join _.meta.id + _.data.name -> _.result.combined
        // This should fail because meta.id and data.name have different parents (meta vs data)
        val joinAction = MigrationAction.Join(
          at = DynamicOptic.root.field("result").field("combined"),
          sourcePaths = Vector(
            DynamicOptic.root.field("meta").field("id"),
            DynamicOptic.root.field("data").field("name")
          ),
          combiner = SchemaExpr.Literal[DynamicValue, String]("combined", Schema.string)
        )

        val result = joinAction.execute(record)

        assertTrue(result.isLeft) &&
        assertTrue(result.swap.exists {
          case _: MigrationError.CrossPathJoinNotSupported => true
          case _                                           => false
        })
      },
      test("should succeed when nested paths share same parent") {
        // Create a nested record where fields share parent:
        // { address: { street: "Main St", city: "NYC" } }
        val addressRecord =
          DynamicValue.Record(
            Vector(
              "street" -> DynamicValue.Primitive(PrimitiveValue.String("Main St")),
              "city"   -> DynamicValue.Primitive(PrimitiveValue.String("NYC"))
            )
          )
        val record = DynamicValue.Record(Vector("address" -> addressRecord))

        // Join _.address.street + _.address.city -> _.address.fullAddress
        // This should succeed because all paths share parent _.address
        val joinAction = MigrationAction.Join(
          at = DynamicOptic.root.field("address").field("fullAddress"),
          sourcePaths = Vector(
            DynamicOptic.root.field("address").field("street"),
            DynamicOptic.root.field("address").field("city")
          ),
          combiner = SchemaExpr.Literal[DynamicValue, String]("Main St, NYC", Schema.string)
        )

        val result = joinAction.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(result.exists {
          case DynamicValue.Record(fields) =>
            fields.exists {
              case ("address", DynamicValue.Record(addressFields)) =>
                addressFields.exists {
                  case ("fullAddress", DynamicValue.Primitive(PrimitiveValue.String(addr))) =>
                    addr == "Main St, NYC"
                  case _ => false
                }
              case _ => false
            }
          case _ => false
        })
      }
    ),
    suite("Split Action")(
      test("should split fullName into firstName and lastName") {
        // PersonV1: fullName = "John Doe"
        // PersonV2: firstName, lastName
        val fullName = DynamicValue.Primitive(PrimitiveValue.String("John Doe"))
        val record   = DynamicValue.Record(Vector("fullName" -> fullName))

        // Create Split action: split fullName by " " -> [firstName, lastName]
        // Use testSplitter helper that splits "John Doe" into ["John", "Doe"]
        val splitter = testSplitter("John Doe", " ")

        val splitAction = MigrationAction.Split(
          at = DynamicOptic.root.field("fullName"),
          targetPaths = Vector(
            DynamicOptic.root.field("firstName"),
            DynamicOptic.root.field("lastName")
          ),
          splitter = splitter
        )

        val result = splitAction.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(result.exists {
          case DynamicValue.Record(fields) =>
            val firstNameExists = fields.exists {
              case ("firstName", DynamicValue.Primitive(PrimitiveValue.String(name))) =>
                name == "John"
              case _ => false
            }
            val lastNameExists = fields.exists {
              case ("lastName", DynamicValue.Primitive(PrimitiveValue.String(name))) =>
                name == "Doe"
              case _ => false
            }
            firstNameExists && lastNameExists
          case _ => false
        })
      },
      test("should fail if source field does not exist") {
        val record = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))))

        val splitter = testSplitter("John Doe", " ")

        val splitAction = MigrationAction.Split(
          at = DynamicOptic.root.field("fullName"), // Does not exist
          targetPaths = Vector(
            DynamicOptic.root.field("firstName"),
            DynamicOptic.root.field("lastName")
          ),
          splitter = splitter
        )

        val result = splitAction.execute(record)

        assertTrue(result.isLeft)
      },
      test("should fail if splitter returns wrong number of results") {
        val fullName = DynamicValue.Primitive(PrimitiveValue.String("John"))
        val record   = DynamicValue.Record(Vector("fullName" -> fullName))

        // Splitter returns only 1 value (no space to split), but we expect 2 target paths
        val splitter = testSplitter("John", " ")

        val splitAction = MigrationAction.Split(
          at = DynamicOptic.root.field("fullName"),
          targetPaths = Vector(
            DynamicOptic.root.field("firstName"),
            DynamicOptic.root.field("lastName")
          ),
          splitter = splitter
        )

        val result = splitAction.execute(record)

        assertTrue(result.isLeft)
      },
      test("reverse should create Join action") {
        val splitAction = MigrationAction.Split(
          at = DynamicOptic.root.field("fullName"),
          targetPaths = Vector(
            DynamicOptic.root.field("firstName"),
            DynamicOptic.root.field("lastName")
          ),
          splitter = testSplitter("John Doe", " ")
        )

        val reversed = splitAction.reverse

        assertTrue(reversed.isInstanceOf[MigrationAction.Join])
      },
      test("should fail with CrossPathSplitNotSupported when paths don't share same parent") {
        // Create a nested record:
        // { data: { fullName: "John Doe" } }
        val dataRecord =
          DynamicValue.Record(Vector("fullName" -> DynamicValue.Primitive(PrimitiveValue.String("John Doe"))))
        val record = DynamicValue.Record(Vector("data" -> dataRecord))

        // Try to split _.data.fullName -> _.meta.firstName + _.info.lastName
        // This should fail because meta.firstName and info.lastName have different parents
        val splitAction = MigrationAction.Split(
          at = DynamicOptic.root.field("data").field("fullName"),
          targetPaths = Vector(
            DynamicOptic.root.field("meta").field("firstName"),
            DynamicOptic.root.field("info").field("lastName")
          ),
          splitter = testSplitter("John Doe", " ")
        )

        val result = splitAction.execute(record)

        assertTrue(result.isLeft) &&
        assertTrue(result.swap.exists {
          case _: MigrationError.CrossPathSplitNotSupported => true
          case _                                            => false
        })
      },
      test("should succeed when nested split paths share same parent") {
        // Create a nested record:
        // { address: { fullAddress: "Main St, NYC" } }
        val addressRecord =
          DynamicValue.Record(Vector("fullAddress" -> DynamicValue.Primitive(PrimitiveValue.String("Main St, NYC"))))
        val record = DynamicValue.Record(Vector("address" -> addressRecord))

        // Split _.address.fullAddress -> _.address.street + _.address.city
        // This should succeed because all paths share parent _.address
        val splitAction = MigrationAction.Split(
          at = DynamicOptic.root.field("address").field("fullAddress"),
          targetPaths = Vector(
            DynamicOptic.root.field("address").field("street"),
            DynamicOptic.root.field("address").field("city")
          ),
          splitter = testSplitter("Main St, NYC", ", ")
        )

        val result = splitAction.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(result.exists {
          case DynamicValue.Record(fields) =>
            fields.exists {
              case ("address", DynamicValue.Record(addressFields)) =>
                val hasStreet = addressFields.exists {
                  case ("street", DynamicValue.Primitive(PrimitiveValue.String(s))) => s == "Main St"
                  case _                                                            => false
                }
                val hasCity = addressFields.exists {
                  case ("city", DynamicValue.Primitive(PrimitiveValue.String(c))) => c == "NYC"
                  case _                                                          => false
                }
                hasStreet && hasCity
              case _ => false
            }
          case _ => false
        })
      }
    ),
    suite("StringSplit SchemaExpr")(
      test("should split string by delimiter") {
        val input = DynamicValue.Primitive(PrimitiveValue.String("John Doe"))

        // StringSplit expression: split by " "
        val stringExpr = SchemaExpr.Literal[DynamicValue, String]("John Doe", Schema.string)
        val splitExpr  = SchemaExpr.StringSplit(stringExpr, " ")

        val result = splitExpr.evalDynamic(input)

        assertTrue(result.isRight) &&
        assertTrue(result.exists { seq =>
          seq.length == 2 &&
          seq.head == DynamicValue.Primitive(PrimitiveValue.String("John")) &&
          seq(1) == DynamicValue.Primitive(PrimitiveValue.String("Doe"))
        })
      },
      test("should handle empty string") {
        val input = DynamicValue.Primitive(PrimitiveValue.String(""))

        val stringExpr = SchemaExpr.Literal[DynamicValue, String]("", Schema.string)
        val splitExpr  = SchemaExpr.StringSplit(stringExpr, " ")

        val result = splitExpr.evalDynamic(input)

        assertTrue(result.isRight) &&
        assertTrue(result.exists(_.length == 1))
      },
      test("should handle string with no delimiter") {
        val input = DynamicValue.Primitive(PrimitiveValue.String("John"))

        val stringExpr = SchemaExpr.Literal[DynamicValue, String]("John", Schema.string)
        val splitExpr  = SchemaExpr.StringSplit(stringExpr, " ")

        val result = splitExpr.evalDynamic(input)

        assertTrue(result.isRight) &&
        assertTrue(result.exists { seq =>
          seq.length == 1 &&
          seq.head == DynamicValue.Primitive(PrimitiveValue.String("John"))
        })
      }
    )
  )
}
