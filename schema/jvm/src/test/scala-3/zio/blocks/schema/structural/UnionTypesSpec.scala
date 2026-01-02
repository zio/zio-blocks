package zio.blocks.schema.structural

import zio.blocks.schema._
import zio.test._

/**
 * Tests for sealed trait to structural union type conversion (Scala 3 only).
 *
 * When calling `.structural` on a sealed trait schema, it produces a union type
 * of structural representations where each case becomes a structural type with
 * a Tag type member for discrimination:
 *
 * {{{
 * sealed trait Result
 * case class Success(value: Int) extends Result
 * case class Failure(error: String) extends Result
 *
 * val structuralSchema = Schema.derived[Result].structural
 * // Type: Schema[
 * //   { type Tag = "Success"; def value(): Int } | { type Tag = "Failure"; def error(): String }
 * // ]
 *
 * structuralSchema.reflect.typeName.name
 * // => "{Tag:"Failure",error:String}|{Tag:"Success",value:Int}"
 * }}}
 *
 * This is NOT the same as deriving a Schema for an explicit union type (A | B).
 * This tests the CONVERSION of a sealed trait schema to a structural union.
 *
 * Note: Sum type to structural conversion is only supported in Scala 3 because
 * it requires union types. In Scala 2, `.structural` on a sealed trait produces
 * a compile-time error.
 */
object UnionTypesSpec extends ZIOSpecDefault {

  sealed trait Result
  object Result {
    case class Success(value: Int)    extends Result
    case class Failure(error: String) extends Result
  }

  sealed trait Status
  object Status {
    case object Active   extends Status
    case object Inactive extends Status
  }

  sealed trait Animal
  object Animal {
    case class Dog(name: String, breed: String)    extends Animal
    case class Cat(name: String, indoor: Boolean)  extends Animal
    case class Bird(name: String, canFly: Boolean) extends Animal
  }

  enum Color {
    case Red
    case Green
    case Blue
  }

  enum Shape {
    case Circle(radius: Double)
    case Rectangle(width: Double, height: Double)
  }

  def spec: Spec[Any, Nothing] = suite("UnionTypesSpec")(
    suite("Sealed Trait to Structural Conversion")(
      test("sealed trait with case classes converts to structural") {
        val schema     = Schema.derived[Result]
        val structural = schema.structural
        assertTrue(structural != null)
      },
      test("sealed trait with case objects converts to structural") {
        val schema     = Schema.derived[Status]
        val structural = schema.structural
        assertTrue(structural != null)
      },
      test("sealed trait with three variants converts to structural") {
        val schema     = Schema.derived[Animal]
        val structural = schema.structural
        assertTrue(structural != null)
      }
    ),
    suite("Structural Type Name")(
      test("structural type name is a pipe-separated union of case types") {
        val schema     = Schema.derived[Result]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name
        val caseTypes  = typeName.split('|').map(_.trim).toSet

        assertTrue(
          caseTypes.size == 2,
          caseTypes.exists(t => t.contains("Tag:\"Failure\"") && t.contains("error:String")),
          caseTypes.exists(t => t.contains("Tag:\"Success\"") && t.contains("value:Int"))
        )
      },
      test("each case in union has correct Tag field with case name as value") {
        val schema     = Schema.derived[Result]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name
        val caseTypes  = typeName.split('|').map(_.trim)

        val hasSuccessTag = caseTypes.exists(_.contains("Tag:\"Success\""))
        val hasFailureTag = caseTypes.exists(_.contains("Tag:\"Failure\""))

        assertTrue(
          hasSuccessTag,
          hasFailureTag
        )
      },
      test("Success case structural type has correct structure") {
        val schema     = Schema.derived[Result]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name

        val caseTypes   = typeName.split('|').map(_.trim)
        val successCase = caseTypes.find(_.contains("Tag:\"Success\""))

        assertTrue(
          successCase.isDefined,
          successCase.get.contains("value:Int"),
          successCase.get.count(_ == ',') == 1 || successCase.get.count(_ == ':') == 2
        )
      },
      test("Failure case structural type has correct structure") {
        val schema     = Schema.derived[Result]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name

        val caseTypes   = typeName.split('|').map(_.trim)
        val failureCase = caseTypes.find(_.contains("Tag:\"Failure\""))

        assertTrue(
          failureCase.isDefined,
          failureCase.get.contains("error:String"),
          failureCase.get.count(_ == ',') == 1 || failureCase.get.count(_ == ':') == 2
        )
      },
      test("three-case sealed trait produces union with three types") {
        val schema     = Schema.derived[Animal]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name

        val caseTypes = typeName.split('|').map(_.trim).toSet

        assertTrue(
          caseTypes.size == 3,
          caseTypes.exists(_.contains("Tag:\"Dog\"")),
          caseTypes.exists(_.contains("Tag:\"Cat\"")),
          caseTypes.exists(_.contains("Tag:\"Bird\""))
        )
      },
      test("each Animal case has correct fields") {
        val schema     = Schema.derived[Animal]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name

        val caseTypes = typeName.split('|').map(_.trim)

        val dogCase  = caseTypes.find(_.contains("Tag:\"Dog\""))
        val catCase  = caseTypes.find(_.contains("Tag:\"Cat\""))
        val birdCase = caseTypes.find(_.contains("Tag:\"Bird\""))

        assertTrue(
          dogCase.isDefined,
          dogCase.get.contains("name:String"),
          dogCase.get.contains("breed:String"),
          catCase.isDefined,
          catCase.get.contains("name:String"),
          catCase.get.contains("indoor:Boolean"),
          birdCase.isDefined,
          birdCase.get.contains("name:String"),
          birdCase.get.contains("canFly:Boolean")
        )
      },
      test("case object structural type has only Tag field") {
        val schema     = Schema.derived[Status]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name

        val caseTypes = typeName.split('|').map(_.trim)

        val activeCase   = caseTypes.find(_.contains("Tag:\"Active\""))
        val inactiveCase = caseTypes.find(_.contains("Tag:\"Inactive\""))

        assertTrue(
          activeCase.isDefined,
          inactiveCase.isDefined,
          activeCase.get.count(_ == ':') == 1,
          inactiveCase.get.count(_ == ':') == 1
        )
      }
    ),
    suite("Structural Schema Structure")(
      test("structural schema is a Variant reflecting sum type") {
        val schema                           = Schema.derived[Result]
        val structural                       = schema.structural
        val (isVariant, hasCorrectCaseCount) = structural.reflect match {
          case v: Reflect.Variant[_, _] => (true, v.cases.size == 2)
          case _                        => (false, false)
        }
        assertTrue(isVariant, hasCorrectCaseCount)
      },
      test("structural variant cases have correct names") {
        val schema     = Schema.derived[Result]
        val structural = schema.structural
        val caseNames  = structural.reflect match {
          case v: Reflect.Variant[_, _] => v.cases.map(_.name).toSet
          case _                        => Set.empty[String]
        }
        assertTrue(
          caseNames.contains("Success"),
          caseNames.contains("Failure"),
          caseNames.size == 2
        )
      },
      test("three-variant Animal has correct case count and names") {
        val schema                 = Schema.derived[Animal]
        val structural             = schema.structural
        val (caseCount, caseNames) = structural.reflect match {
          case v: Reflect.Variant[_, _] => (v.cases.size, v.cases.map(_.name).toSet)
          case _                        => (-1, Set.empty[String])
        }
        assertTrue(
          caseCount == 3,
          caseNames.contains("Dog"),
          caseNames.contains("Cat"),
          caseNames.contains("Bird")
        )
      },
      test("each variant case is a Record with correct fields") {
        val schema     = Schema.derived[Result]
        val structural = schema.structural

        val caseRecords = structural.reflect match {
          case v: Reflect.Variant[_, _] =>
            v.cases.map { c =>
              val fieldNames = c.value match {
                case r: Reflect.Record[_, _] => r.fields.map(_.name).toSet
                case _                       => Set.empty[String]
              }
              (c.name, fieldNames)
            }.toMap
          case _ => Map.empty[String, Set[String]]
        }

        assertTrue(
          caseRecords.get("Success").exists(_.contains("value")),
          caseRecords.get("Failure").exists(_.contains("error"))
        )
      }
    ),
    suite("Round-Trip Through Structural")(
      test("Success case round-trips through structural with correct value") {
        val schema          = Schema.derived[Result]
        val nominal: Result = Result.Success(42)

        val dynamic       = schema.toDynamicValue(nominal)
        val structural    = schema.structural
        val reconstructed = structural.fromDynamicValue(dynamic)

        val isVariant = dynamic match {
          case DynamicValue.Variant(tag, value) =>
            tag == "Success" && (value match {
              case DynamicValue.Record(fields) =>
                fields.exists { case (k, v) =>
                  k == "value" && v == DynamicValue.Primitive(PrimitiveValue.Int(42))
                }
              case _ => false
            })
          case _ => false
        }

        assertTrue(
          reconstructed.isRight,
          isVariant
        )
      },
      test("Failure case round-trips through structural with correct error message") {
        val schema          = Schema.derived[Result]
        val nominal: Result = Result.Failure("error message")

        val dynamic       = schema.toDynamicValue(nominal)
        val structural    = schema.structural
        val reconstructed = structural.fromDynamicValue(dynamic)

        val isVariant = dynamic match {
          case DynamicValue.Variant(tag, value) =>
            tag == "Failure" && (value match {
              case DynamicValue.Record(fields) =>
                fields.exists { case (k, v) =>
                  k == "error" && v == DynamicValue.Primitive(PrimitiveValue.String("error message"))
                }
              case _ => false
            })
          case _ => false
        }

        assertTrue(
          reconstructed.isRight,
          isVariant
        )
      },
      test("case object round-trips with correct tag") {
        val schema          = Schema.derived[Status]
        val nominal: Status = Status.Active

        val dynamic       = schema.toDynamicValue(nominal)
        val structural    = schema.structural
        val reconstructed = structural.fromDynamicValue(dynamic)

        val isActiveVariant = dynamic match {
          case DynamicValue.Variant(tag, _) => tag == "Active"
          case _                            => false
        }

        assertTrue(
          reconstructed.isRight,
          isActiveVariant
        )
      },
      test("Inactive case object round-trips correctly") {
        val schema          = Schema.derived[Status]
        val nominal: Status = Status.Inactive

        val dynamic       = schema.toDynamicValue(nominal)
        val structural    = schema.structural
        val reconstructed = structural.fromDynamicValue(dynamic)

        val isInactiveVariant = dynamic match {
          case DynamicValue.Variant(tag, _) => tag == "Inactive"
          case _                            => false
        }

        assertTrue(
          reconstructed.isRight,
          isInactiveVariant
        )
      },
      test("all Animal variants round-trip with correct structure") {
        val schema     = Schema.derived[Animal]
        val structural = schema.structural

        val dog: Animal  = Animal.Dog("Rex", "German Shepherd")
        val cat: Animal  = Animal.Cat("Whiskers", true)
        val bird: Animal = Animal.Bird("Tweety", true)

        val dogDynamic  = schema.toDynamicValue(dog)
        val catDynamic  = schema.toDynamicValue(cat)
        val birdDynamic = schema.toDynamicValue(bird)

        val dogIsCorrect = dogDynamic match {
          case DynamicValue.Variant("Dog", DynamicValue.Record(fields)) =>
            val fieldMap = fields.toMap
            fieldMap.get("name").contains(DynamicValue.Primitive(PrimitiveValue.String("Rex"))) &&
            fieldMap.get("breed").contains(DynamicValue.Primitive(PrimitiveValue.String("German Shepherd")))
          case _ => false
        }

        val catIsCorrect = catDynamic match {
          case DynamicValue.Variant("Cat", DynamicValue.Record(fields)) =>
            val fieldMap = fields.toMap
            fieldMap.get("name").contains(DynamicValue.Primitive(PrimitiveValue.String("Whiskers"))) &&
            fieldMap.get("indoor").contains(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
          case _ => false
        }

        val birdIsCorrect = birdDynamic match {
          case DynamicValue.Variant("Bird", DynamicValue.Record(fields)) =>
            val fieldMap = fields.toMap
            fieldMap.get("name").contains(DynamicValue.Primitive(PrimitiveValue.String("Tweety"))) &&
            fieldMap.get("canFly").contains(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
          case _ => false
        }

        assertTrue(
          dogIsCorrect,
          catIsCorrect,
          birdIsCorrect,
          structural.fromDynamicValue(dogDynamic).isRight,
          structural.fromDynamicValue(catDynamic).isRight,
          structural.fromDynamicValue(birdDynamic).isRight
        )
      }
    ),
    suite("Enum to Structural Conversion")(
      test("simple enum converts to structural") {
        val schema     = Schema.derived[Color]
        val structural = schema.structural
        assertTrue(structural != null)
      },
      test("parameterized enum converts to structural") {
        val schema     = Schema.derived[Shape]
        val structural = schema.structural
        assertTrue(structural != null)
      },
      test("simple enum structural produces three-way union") {
        val schema     = Schema.derived[Color]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name

        val caseTypes = typeName.split('|').map(_.trim).toSet

        assertTrue(
          caseTypes.size == 3,
          caseTypes.exists(_.contains("Tag:\"Red\"")),
          caseTypes.exists(_.contains("Tag:\"Green\"")),
          caseTypes.exists(_.contains("Tag:\"Blue\""))
        )
      },
      test("simple enum cases have only Tag field") {
        val schema     = Schema.derived[Color]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name

        val caseTypes = typeName.split('|').map(_.trim)

        assertTrue(
          caseTypes.forall(_.count(_ == ':') == 1)
        )
      },
      test("parameterized enum structural contains field types") {
        val schema     = Schema.derived[Shape]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name

        val caseTypes = typeName.split('|').map(_.trim)

        val circleCase    = caseTypes.find(_.contains("Tag:\"Circle\""))
        val rectangleCase = caseTypes.find(_.contains("Tag:\"Rectangle\""))

        assertTrue(
          circleCase.isDefined,
          circleCase.get.contains("radius:Double"),
          rectangleCase.isDefined,
          rectangleCase.get.contains("width:Double"),
          rectangleCase.get.contains("height:Double")
        )
      },
      test("Circle case has exactly two fields (Tag and radius)") {
        val schema     = Schema.derived[Shape]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name

        val caseTypes  = typeName.split('|').map(_.trim)
        val circleCase = caseTypes.find(_.contains("Tag:\"Circle\""))

        assertTrue(
          circleCase.isDefined,
          circleCase.get.count(_ == ':') == 2
        )
      },
      test("Rectangle case has exactly three fields (Tag, width, height)") {
        val schema     = Schema.derived[Shape]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name

        val caseTypes     = typeName.split('|').map(_.trim)
        val rectangleCase = caseTypes.find(_.contains("Tag:\"Rectangle\""))

        assertTrue(
          rectangleCase.isDefined,
          rectangleCase.get.count(_ == ':') == 3
        )
      }
    ),
    suite("Type Name Determinism")(
      test("same sealed trait produces identical structural type name") {
        val schema1 = Schema.derived[Result]
        val schema2 = Schema.derived[Result]

        val name1 = schema1.structural.reflect.typeName.name
        val name2 = schema2.structural.reflect.typeName.name

        assertTrue(
          name1 == name2,
          name1.nonEmpty,
          name2.nonEmpty
        )
      },
      test("structural union contains all case variants") {
        val schema   = Schema.derived[Result]
        val typeName = schema.structural.reflect.typeName.name

        val failureIdx = typeName.indexOf("Failure")
        val successIdx = typeName.indexOf("Success")

        assertTrue(
          failureIdx >= 0,
          successIdx >= 0
        )
      },
      test("Animal structural union contains all three cases") {
        val schema   = Schema.derived[Animal]
        val typeName = schema.structural.reflect.typeName.name

        val birdIdx = typeName.indexOf("Bird")
        val catIdx  = typeName.indexOf("Cat")
        val dogIdx  = typeName.indexOf("Dog")

        assertTrue(
          birdIdx >= 0,
          catIdx >= 0,
          dogIdx >= 0
        )
      },
      test("enum structural type name contains all variants") {
        val schema   = Schema.derived[Color]
        val typeName = schema.structural.reflect.typeName.name

        val blueIdx  = typeName.indexOf("Blue")
        val greenIdx = typeName.indexOf("Green")
        val redIdx   = typeName.indexOf("Red")

        assertTrue(
          blueIdx >= 0,
          greenIdx >= 0,
          redIdx >= 0
        )
      },
      test("structural type name is deterministic across schema instances") {
        val schema1 = Schema.derived[Animal]
        val schema2 = Schema.derived[Animal]
        val schema3 = Schema.derived[Animal]

        val name1 = schema1.structural.reflect.typeName.name
        val name2 = schema2.structural.reflect.typeName.name
        val name3 = schema3.structural.reflect.typeName.name

        assertTrue(
          name1 == name2,
          name2 == name3,
          name1.contains("Dog"),
          name1.contains("Cat"),
          name1.contains("Bird")
        )
      }
    )
  )
}
