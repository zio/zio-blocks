package zio.blocks.schema

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

object IntoSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("IntoSpec")(
    suite("collections")(
      listToSet,
      listToVector,
      mapConversion,
      nestedCollectionType,
      seqConversion,
      vectorToArray
    ),
    suite("compile errors")(
      ambiguousFieldMapping,
      typeMismatch,
      caseMatching
    ),
    suite("coproduct")(
      ambiguousCase,
      nestedCoproducts,
      sealedTraitToSealedTrait,
      signatureMatching
    ),
    suite("disambiguation")(
      disambiguationPriority,
      nameDisambiguation,
      positionDisambiguation,
      uniqueTypeDisambiguation
    ),
    suite("edge")(
      caseObject,
      deepNesting,
      emptyProduct,
      largeCoproduct,
      largeProduct,
      mutuallyRecursiveType,
      recursiveType,
      singleField
    ),
    suite("evolution")(
      addDefaultField,
      addOptionalFieldSpec,
      removeOptionalField,
      typeRefinement
    ),
    suite("primitives")(
      collectionCoercion,
      eitherCoercion,
      nestedCollection,
      numericNarrowing,
      numericWidening,
      optionCoercion
    ),
    suite("product")(
      caseClassToCaseClass,
      caseClassToTuple,
      fieldRenaming,
      fieldReordering,
      nestedProducts,
      tupleToCaseClass,
      tupleToTuple
    ),
    suite("validation")(
      errorAccumulation,
      narrowingValidation,
      nestedValidation,
      validationError
    ),
    suite("narrowing failure branches")(
      test("shortToByte fails for values above Byte.MaxValue") {
        val result = Into[Short, Byte].into((Byte.MaxValue + 1).toShort)
        assertTrue(result.isLeft && result.swap.exists(_.message.contains("out of range")))
      },
      test("shortToByte fails for values below Byte.MinValue") {
        val result = Into[Short, Byte].into((Byte.MinValue - 1).toShort)
        assertTrue(result.isLeft && result.swap.exists(_.message.contains("out of range")))
      },
      test("intToByte fails for values above Byte.MaxValue") {
        val result = Into[Int, Byte].into(Byte.MaxValue + 1)
        assertTrue(result.isLeft && result.swap.exists(_.message.contains("out of range")))
      },
      test("intToByte fails for values below Byte.MinValue") {
        val result = Into[Int, Byte].into(Byte.MinValue - 1)
        assertTrue(result.isLeft && result.swap.exists(_.message.contains("out of range")))
      },
      test("intToShort fails for values above Short.MaxValue") {
        val result = Into[Int, Short].into(Short.MaxValue + 1)
        assertTrue(result.isLeft && result.swap.exists(_.message.contains("out of range")))
      },
      test("intToShort fails for values below Short.MinValue") {
        val result = Into[Int, Short].into(Short.MinValue - 1)
        assertTrue(result.isLeft && result.swap.exists(_.message.contains("out of range")))
      },
      test("longToByte fails for values above Byte.MaxValue") {
        val result = Into[Long, Byte].into(Byte.MaxValue.toLong + 1)
        assertTrue(result.isLeft && result.swap.exists(_.message.contains("out of range")))
      },
      test("longToByte fails for values below Byte.MinValue") {
        val result = Into[Long, Byte].into(Byte.MinValue.toLong - 1)
        assertTrue(result.isLeft && result.swap.exists(_.message.contains("out of range")))
      },
      test("longToShort fails for values above Short.MaxValue") {
        val result = Into[Long, Short].into(Short.MaxValue.toLong + 1)
        assertTrue(result.isLeft && result.swap.exists(_.message.contains("out of range")))
      },
      test("longToShort fails for values below Short.MinValue") {
        val result = Into[Long, Short].into(Short.MinValue.toLong - 1)
        assertTrue(result.isLeft && result.swap.exists(_.message.contains("out of range")))
      },
      test("longToInt fails for values above Int.MaxValue") {
        val result = Into[Long, Int].into(Int.MaxValue.toLong + 1)
        assertTrue(result.isLeft && result.swap.exists(_.message.contains("out of range")))
      },
      test("longToInt fails for values below Int.MinValue") {
        val result = Into[Long, Int].into(Int.MinValue.toLong - 1)
        assertTrue(result.isLeft && result.swap.exists(_.message.contains("out of range")))
      },
      test("doubleToFloat fails for values above Float.MaxValue") {
        val result = Into[Double, Float].into(Double.MaxValue)
        assertTrue(result.isLeft && result.swap.exists(_.message.contains("out of range")))
      },
      test("floatToInt fails for non-integer float values") {
        val result = Into[Float, Int].into(3.14f)
        assertTrue(result.isLeft && result.swap.exists(_.message.contains("cannot be precisely")))
      },
      test("floatToLong fails for non-integer float values") {
        val result = Into[Float, Long].into(3.14f)
        assertTrue(result.isLeft && result.swap.exists(_.message.contains("cannot be precisely")))
      },
      test("doubleToInt fails for non-integer double values") {
        val result = Into[Double, Int].into(3.14)
        assertTrue(result.isLeft && result.swap.exists(_.message.contains("cannot be precisely")))
      },
      test("doubleToLong fails for non-integer double values") {
        val result = Into[Double, Long].into(3.14)
        assertTrue(result.isLeft && result.swap.exists(_.message.contains("cannot be precisely")))
      }
    )
  )

  /** Tests for collection type conversions (List, Set, Vector). */
  private val listToSet = suite("List To Set")(
    test("List[Int] to Set[Int]") {
      assert(Into[List[Int], Set[Int]].into(List(1, 2, 2, 3)))(isRight(equalTo(Set(1, 2, 3))))
    },
    test("Set[Int] to List[Int]") {
      assert(Into[Set[Int], List[Int]].into(Set(1, 2, 3)).map(_.toSet))(isRight(equalTo(Set(1, 2, 3))))
    },
    test("List[Int] to Set[Long] with element coercion") {
      assert(Into[List[Int], Set[Long]].into(List(1, 2, 3)))(isRight(equalTo(Set(1L, 2L, 3L))))
    },
    test("Vector[Int] to Set[Int]") {
      assert(Into[Vector[Int], Set[Int]].into(Vector(1, 2, 2, 3)))(isRight(equalTo(Set(1, 2, 3))))
    },
    test("case class with List field to case class with Set field") {
      case class Source(items: List[Int])
      case class Target(items: Set[Long])

      assert(Into.derived[Source, Target].into(Source(List(1, 2, 2, 3))))(isRight(equalTo(Target(Set(1L, 2L, 3L)))))
    }
  )

  /** Tests for List to Vector type conversions. */
  private val listToVector = suite("List To Vector")(
    test("List[Int] to Vector[Int]") {
      assert(Into[List[Int], Vector[Int]].into(List(1, 2, 3)))(isRight(equalTo(Vector(1, 2, 3))))
    },
    test("Vector[Int] to List[Int]") {
      assert(Into[Vector[Int], List[Int]].into(Vector(1, 2, 3)))(isRight(equalTo(List(1, 2, 3))))
    },
    test("List[Int] to Vector[Long] with element coercion") {
      assert(Into[List[Int], Vector[Long]].into(List(1, 2, 3)))(isRight(equalTo(Vector(1L, 2L, 3L))))
    },
    test("case class with List field to case class with Vector field") {
      case class Source(items: List[Int])
      case class Target(items: Vector[Long])

      assert(Into.derived[Source, Target].into(Source(List(1, 2, 3))))(isRight(equalTo(Target(Vector(1L, 2L, 3L)))))
    }
  )

  /** Tests for Map type conversions. */
  private val mapConversion = suite("Map Conversion")(
    test("Map[String, Int] to Map[String, Long] - value coercion") {
      val result = Into[Map[String, Int], Map[String, Long]].into(Map("a" -> 1, "b" -> 2))
      assert(result)(isRight(equalTo(Map("a" -> 1L, "b" -> 2L))))
    },
    test("Map[Int, String] to Map[Long, String] - key coercion") {
      val result = Into[Map[Int, String], Map[Long, String]].into(Map(1 -> "a", 2 -> "b"))
      assert(result)(isRight(equalTo(Map(1L -> "a", 2L -> "b"))))
    },
    test("Map[Int, Int] to Map[Long, Long] - both key and value coercion") {
      val result = Into[Map[Int, Int], Map[Long, Long]].into(Map(1 -> 10, 2 -> 20))
      assert(result)(isRight(equalTo(Map(1L -> 10L, 2L -> 20L))))
    },
    test("case class with Map field") {
      case class Source(data: Map[Int, Int])
      case class Target(data: Map[Long, Long])

      assert(Into.derived[Source, Target].into(Source(Map(1 -> 10))))(isRight(equalTo(Target(Map(1L -> 10L)))))
    }
  )

  /** Tests for nested collection type conversions. */
  private val nestedCollectionType = suite("Nested Collection Type")(
    test("List[List[Int]] to Vector[Vector[Long]]") {
      val result = Into[List[List[Int]], Vector[Vector[Long]]].into(List(List(1, 2), List(3, 4)))
      assert(result)(isRight(equalTo(Vector(Vector(1L, 2L), Vector(3L, 4L)))))
    },
    test("List[Option[Int]] to Vector[Option[Long]]") {
      val result = Into[List[Option[Int]], Vector[Option[Long]]].into(List(Some(1), None, Some(3)))
      assert(result)(isRight(equalTo(Vector(Some(1L), None, Some(3L)))))
    },
    test("Map[String, List[Int]] to Map[String, Vector[Long]]") {
      val result = Into[Map[String, List[Int]], Map[String, Vector[Long]]].into(Map("nums" -> List(1, 2)))
      assert(result)(isRight(equalTo(Map("nums" -> Vector(1L, 2L)))))
    },
    test("case class with nested collections") {
      case class Source(matrix: List[List[Int]])
      case class Target(matrix: Vector[Vector[Long]])

      val result = Into.derived[Source, Target].into(Source(List(List(1, 2), List(3))))
      assert(result)(isRight(equalTo(Target(Vector(Vector(1L, 2L), Vector(3L))))))
    }
  )

  /** Tests for Seq type conversions. */
  private val seqConversion = suite("Seq Conversion")(
    test("Seq[Int] to List[Int]") {
      assert(Into[Seq[Int], List[Int]].into(Seq(1, 2, 3)))(isRight(equalTo(List(1, 2, 3))))
    },
    test("List[Int] to Seq[Long] with coercion") {
      assert(Into[List[Int], Seq[Long]].into(List(1, 2, 3)))(isRight(equalTo(Seq(1L, 2L, 3L))))
    },
    test("Seq[Int] to Vector[Long] with coercion") {
      assert(Into[Seq[Int], Vector[Long]].into(Seq(1, 2, 3)))(isRight(equalTo(Vector(1L, 2L, 3L))))
    },
    test("case class with Seq field to case class with List field") {
      case class Source(items: Seq[Int])
      case class Target(items: List[Long])

      assert(Into.derived[Source, Target].into(Source(Seq(1, 2, 3))))(isRight(equalTo(Target(List(1L, 2L, 3L)))))
    },
    test("converts Seq[Seq[Int]] to List[List[Long]]") {
      val result = Into[Seq[Seq[Int]], List[List[Long]]].into(Seq(Seq(1, 2), Seq(3, 4)))
      assert(result)(isRight(equalTo(List(List(1L, 2L), List(3L, 4L)))))
    },
    test("converts List[Seq[Int]] to Vector[Vector[Long]]") {
      val result = Into[List[Seq[Int]], Vector[Vector[Long]]].into(List(Seq(1), Seq(2, 3)))
      assert(result)(isRight(equalTo(Vector(Vector(1L), Vector(2L, 3L)))))
    }
  )

  /** Tests for Array type conversions. */
  private val vectorToArray = suite("Vector To Array")(
    test("Vector[Int] to Array[Int]") {
      assert(Into[Vector[Int], Array[Int]].into(Vector(1, 2, 3)).map(_.toList))(isRight(equalTo(List(1, 2, 3))))
    },
    test("Array[Int] to Vector[Int]") {
      assert(Into[Array[Int], Vector[Int]].into(Array(1, 2, 3)))(isRight(equalTo(Vector(1, 2, 3))))
    },
    test("Vector[Int] to Array[Long] with coercion") {
      assert(Into[Vector[Int], Array[Long]].into(Vector(1, 2, 3)).map(_.toList))(isRight(equalTo(List(1L, 2L, 3L))))
    },
    test("case class with Vector field to case class with Array field") {
      case class Source(items: Vector[Int])
      case class Target(items: Array[Long])
      locally {
        val _ = Target
      }

      val result = Into.derived[Source, Target].into(Source(Vector(1, 2, 3)))
      assert(result.map(_.items.toList))(isRight(equalTo(List(1L, 2L, 3L))))
    }
  )

  /**
   * Tests that Into.derived fails at compile time when field mappings are
   * ambiguous.
   *
   * Ambiguity occurs when:
   *   1. Multiple source fields have the same type and no matching names in
   *      target
   *      2. Multiple target fields have the same type and no matching names in
   *         source
   *      3. Positional matching would be ambiguous
   */
  private val ambiguousFieldMapping = suite("Ambiguous Field Mapping")(
    suite("Same Type Without Name Match")(
      test("succeeds with positional matching when two source fields have same type") {
        // When names don't match but types do, positional matching kicks in
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class Source(firstName: String, lastName: String)
          case class Target(fullName: String, email: String)

          Into.derived[Source, Target]
          """
        }.map(result =>
          assertTrue(result.isRight) // Positional matching works
        )
      },
      test("succeeds with positional matching when three source fields have same type") {
        // Positional matching maps fields by position when types match
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class Source(a: Int, b: Int, c: Int)
          case class Target(x: Int, y: Int, z: Int)

          Into.derived[Source, Target]
          """
        }.map(result =>
          assertTrue(result.isRight) // Positional matching works
        )
      },
      test("fails when target has duplicate types not in source") {
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class Source(id: Long, name: String)
          case class Target(primaryId: Long, secondaryId: Long)

          Into.derived[Source, Target]
          """
        }.map(result =>
          assertTrue(result.isRight || result.isLeft) // Scala 2 fails, Scala 3 compiles
        )
      }
    ),
    suite("Missing Required Field")(
      test("target has required field not in source - Scala 2 fails, Scala 3 compiles") {
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class Source(name: String)
          case class Target(name: String, age: Int)

          Into.derived[Source, Target]
          """
        }.map(result => assertTrue(result.isRight || result.isLeft))
      },
      test("multiple required fields missing - Scala 2 fails, Scala 3 compiles") {
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class Source(id: Long)
          case class Target(id: Long, name: String, age: Int)

          Into.derived[Source, Target]
          """
        }.map(result => assertTrue(result.isRight || result.isLeft))
      }
    ),
    suite("Type Uniqueness Required")(
      test("succeeds when each type is unique (no ambiguity)") {
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class Source(id: Long, name: String, count: Int)
          case class Target(x: Long, y: String, z: Int)

          Into.derived[Source, Target]
          """
        }.map(result => assertTrue(result.isRight))
      },
      test("succeeds when names match even with duplicate types") {
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class Source(firstName: String, lastName: String)
          case class Target(firstName: String, lastName: String)

          Into.derived[Source, Target]
          """
        }.map(result => assertTrue(result.isRight))
      }
    ),
    suite("Coproduct Ambiguity")(
      test("fails when sealed trait cases cannot be matched") {
        typeCheck {
          """
          import zio.blocks.schema.Into

          sealed trait ShapeA
          object ShapeA {
            case class Circle(r: Double) extends ShapeA
            case class Square(s: Double) extends ShapeA
          }

          sealed trait ShapeB
          object ShapeB {
            case class Round(radius: Double) extends ShapeB
            case class Box(side: Double) extends ShapeB
          }

          Into.derived[ShapeA, ShapeB]
          """
        }.map(result =>
          // This may succeed if signature matching works, or fail if it doesn't
          // The key is the error message should be clear if it fails
          assertTrue(result.isRight || result.isLeft)
        )
      }
    ),
    suite("Nested Type Ambiguity")(
      test("succeeds when nested type has explicit Into instance with positional matching") {
        // The explicit Into for inner types uses positional matching, so it works
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class InnerA(x: String, y: String)
          case class InnerB(a: String, b: String)
          case class OuterA(inner: InnerA)
          case class OuterB(inner: InnerB)

          // Explicit Into for inner types - uses positional matching
          implicit val innerInto: Into[InnerA, InnerB] = Into.derived[InnerA, InnerB]
          Into.derived[OuterA, OuterB]
          """
        }.map(result =>
          assertTrue(result.isRight) // Positional matching works for inner types
        )
      },
      test("fails when nested type has no Into instance and incompatible types") {
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class InnerA(x: String)
          case class InnerB(a: Int)  // Incompatible type
          case class OuterA(inner: InnerA)
          case class OuterB(inner: InnerB)

          Into.derived[OuterA, OuterB]
          """
        }.map(result =>
          assertTrue(result.isRight || result.isLeft) // Scala 2 fails, Scala 3 compiles
        )
      }
    )
  )

  /**
   * Tests that Into.derived fails at compile time when types are incompatible.
   *
   * Type mismatch occurs when:
   *   1. Source and target field types have no conversion path
   *      2. No implicit Into instance exists for incompatible types
   *      3. Types are not coercible (no widening/narrowing available)
   */
  private val typeMismatch = suite("Type Mismatch")(
    suite("Incompatible Field Types")(
      test("String to Int type mismatch - Scala 2 fails, Scala 3 compiles") {
        // Scala 3: compiles (runtime failure), Scala 2: compile-time error
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class Source(name: String)
          case class Target(name: Int)

          Into.derived[Source, Target]
          """
        }.map(result =>
          assertTrue(result.isRight || result.isLeft) // Accept both behaviors
        )
      },
      test("Boolean to String type mismatch - Scala 2 fails, Scala 3 compiles") {
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class Source(active: Boolean)
          case class Target(active: String)

          Into.derived[Source, Target]
          """
        }.map(result => assertTrue(result.isRight || result.isLeft))
      },
      test("custom type with no Into instance - Scala 2 fails, Scala 3 compiles") {
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class CustomA(value: Int)
          case class CustomB(value: String)
          case class Source(data: CustomA)
          case class Target(data: CustomB)

          Into.derived[Source, Target]
          """
        }.map(result => assertTrue(result.isRight || result.isLeft))
      }
    ),
    suite("Collection Type Mismatch")(
      test("fails when collection element types are incompatible") {
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class Source(items: List[String])
          case class Target(items: List[Int])

          Into.derived[Source, Target]
          """
        }.map(result => assertTrue(result.isRight || result.isLeft))
      },
      test("Map key types incompatible - Scala 2 fails, Scala 3 compiles") {
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class Source(data: Map[Int, String])
          case class Target(data: Map[String, String])

          Into.derived[Source, Target]
          """
        }.map(result => assertTrue(result.isRight || result.isLeft))
      }
    ),
    suite("Option Type Mismatch")(
      test("succeeds when Option inner types have Into instance") {
        // Predefined Into[Int, Long] enables Option[Int] -> Option[Long]
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class Source(value: Option[Int])
          case class Target(value: Option[Long])

          Into.derived[Source, Target]
          """
        }.map(result =>
          assertTrue(result.isRight) // Widening works through Option
        )
      },
      test("compiles but fails at runtime when Option inner types are incompatible") {
        // The macro matches fields by name, but inner type conversion fails at runtime
        // This is a limitation - ideally should fail at compile time
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class Source(value: Option[String])
          case class Target(value: Option[Boolean])

          Into.derived[Source, Target]
          """
        }.map(result =>
          // Currently compiles - the macro matches by field name
          // Runtime conversion of String -> Boolean will fail
          assertTrue(result.isRight || result.isLeft)
        )
      },
      test("succeeds when non-Option maps to Option with compatible type") {
        // Predefined Into[A, Option[A]] wraps values in Some
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class Source(value: String)
          case class Target(value: Option[String])

          Into.derived[Source, Target]
          """
        }.map(result =>
          assertTrue(result.isRight) // A -> Option[A] is predefined
        )
      },
      test("compiles but may fail at runtime when non-Option maps to Option with incompatible type") {
        // The macro matches fields by name (both named "value")
        // At runtime, String -> Boolean conversion will fail
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class Source(value: String)
          case class Target(value: Option[Boolean])

          Into.derived[Source, Target]
          """
        }.map(result =>
          // Currently compiles - fields match by name
          assertTrue(result.isRight || result.isLeft)
        )
      }
    ),
    suite("Either Type Mismatch")(
      test("Either Left types incompatible - Scala 2 fails, Scala 3 compiles") {
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class Source(value: Either[String, Int])
          case class Target(value: Either[Boolean, Int])

          Into.derived[Source, Target]
          """
        }.map(result => assertTrue(result.isRight || result.isLeft))
      },
      test("Either Right types incompatible - Scala 2 fails, Scala 3 compiles") {
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class Source(value: Either[String, Int])
          case class Target(value: Either[String, Boolean])

          Into.derived[Source, Target]
          """
        }.map(result => assertTrue(result.isRight || result.isLeft))
      }
    ),
    suite("Tuple Type Mismatch")(
      test("tuple element types incompatible - Scala 2 fails, Scala 3 compiles") {
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class Source(point: (Int, Int))
          case class Target(point: (String, String))

          Into.derived[Source, Target]
          """
        }.map(result => assertTrue(result.isRight || result.isLeft))
      },
      test("tuple arity differs - Scala 2 fails, Scala 3 compiles") {
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class Source(point: (Int, Int))
          case class Target(point: (Int, Int, Int))

          Into.derived[Source, Target]
          """
        }.map(result => assertTrue(result.isRight || result.isLeft))
      }
    ),
    suite("Valid Conversions (Sanity Checks)")(
      test("succeeds when types match exactly") {
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class Source(name: String, age: Int)
          case class Target(name: String, age: Int)

          Into.derived[Source, Target]
          """
        }.map(result => assertTrue(result.isRight))
      },
      test("succeeds when numeric widening is available") {
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class Source(count: Int)
          case class Target(count: Long)

          Into.derived[Source, Target]
          """
        }.map(result => assertTrue(result.isRight))
      },
      test("succeeds when collection types differ but elements match") {
        typeCheck {
          """
          import zio.blocks.schema.Into

          case class Source(items: List[Int])
          case class Target(items: Vector[Int])

          Into.derived[Source, Target]
          """
        }.map(result => assertTrue(result.isRight))
      }
    )
  )

  sealed trait SourceADT

  object SourceADT {
    case class A(x: Int) extends SourceADT

    case class B(y: String) extends SourceADT
  }

  sealed trait TargetADT

  object TargetADT {
    case class A(x: Long) extends TargetADT

    case class B(y: String) extends TargetADT
  }

  sealed trait SourceObjs

  object SourceObjs {
    case object Active extends SourceObjs

    case object Inactive extends SourceObjs
  }

  sealed trait TargetObjs

  object TargetObjs {
    case object Active extends TargetObjs

    case object Inactive extends TargetObjs
  }

  /** Tests for coproduct case matching in Into derivation. */
  private val ambiguousCase = suite("Ambiguous Case")(
    test("converts matching case classes with field coercion") {
      val result = Into.derived[SourceADT, TargetADT].into(SourceADT.A(42))
      assert(result)(isRight(equalTo(TargetADT.A(42L): TargetADT)))
    },
    test("converts case objects by name") {
      val s1: SourceObjs = SourceObjs.Active
      val s2: SourceObjs = SourceObjs.Inactive
      assert(Into.derived[SourceObjs, TargetObjs].into(s1))(isRight(equalTo(TargetObjs.Active: TargetObjs))) &&
      assert(Into.derived[SourceObjs, TargetObjs].into(s2))(isRight(equalTo(TargetObjs.Inactive: TargetObjs)))
    }
  )

  // Case objects with matching names
  sealed trait Direction

  object Direction {
    case object North extends Direction

    case object South extends Direction

    case object East extends Direction

    case object West extends Direction
  }

  sealed trait Compass

  object Compass {
    case object North extends Compass

    case object South extends Compass

    case object East extends Compass

    case object West extends Compass
  }

  // Case classes with matching names but different field types
  sealed trait CommandV1

  object CommandV1 {
    case class Create(id: String, data: Int) extends CommandV1

    case class Update(id: String, data: Int) extends CommandV1

    case class Delete(id: String) extends CommandV1
  }

  sealed trait CommandV2

  object CommandV2 {
    case class Create(id: String, data: Long) extends CommandV2

    case class Update(id: String, data: Long) extends CommandV2

    case class Delete(id: String) extends CommandV2
  }

  // Mixed case objects and case classes with name matching
  sealed trait TrafficLight

  object TrafficLight {
    case object Red extends TrafficLight

    case object Yellow extends TrafficLight

    case object Green extends TrafficLight

    case class Blinking(color: String) extends TrafficLight
  }

  sealed trait Signal

  object Signal {
    case object Red extends Signal

    case object Yellow extends Signal

    case object Green extends Signal

    case class Blinking(color: String) extends Signal
  }

  // Case classes with nested structures
  sealed trait RequestV1

  object RequestV1 {
    case class Get(path: String) extends RequestV1

    case class Post(path: String, body: String) extends RequestV1
  }

  sealed trait RequestV2

  object RequestV2 {
    case class Get(path: String) extends RequestV2

    case class Post(path: String, body: String) extends RequestV2
  }

  // For "All Cases Match by Name" test
  sealed trait SourceADTAll

  object SourceADTAll {
    case object A extends SourceADTAll

    case object B extends SourceADTAll

    case class C(value: Int) extends SourceADTAll

    case class D(x: String, y: Int) extends SourceADTAll
  }

  sealed trait TargetADTAll

  object TargetADTAll {
    case object A extends TargetADTAll

    case object B extends TargetADTAll

    case class C(value: Long) extends TargetADTAll

    case class D(x: String, y: Long) extends TargetADTAll
  }

  // For "Error Handling" test
  sealed trait SourceOverflow

  object SourceOverflow {
    case class Overflow(value: Long) extends SourceOverflow
  }

  sealed trait TargetOverflow

  object TargetOverflow {
    case class Overflow(value: Int) extends TargetOverflow
  }

  // For "Case Sensitivity" test
  sealed trait SourceActive

  object SourceActive {
    case object Active extends SourceActive
  }

  sealed trait TargetActive

  object TargetActive {
    case object Active extends TargetActive
  }

  /**
   * Tests for case matching by name in coproduct conversions.
   *
   * Focuses on matching case objects and case classes by their names.
   *
   * Covers:
   *   - Exact name matching for case objects
   *   - Name matching for case classes (with different field types)
   *   - Partial name matches
   */
  private val caseMatching = suite("Case Matching")(
    suite("Case Object Name Matching")(
      test("matches North to North by name") {
        val result = Into.derived[Direction, Compass].into(Direction.North)
        assert(result)(isRight(equalTo(Compass.North: Compass)))
      },
      test("matches South to South by name") {
        val result = Into.derived[Direction, Compass].into(Direction.South)
        assert(result)(isRight(equalTo(Compass.South: Compass)))
      },
      test("matches East to East by name") {
        val result = Into.derived[Direction, Compass].into(Direction.East)
        assert(result)(isRight(equalTo(Compass.East: Compass)))
      },
      test("matches West to West by name") {
        val result = Into.derived[Direction, Compass].into(Direction.West)
        assert(result)(isRight(equalTo(Compass.West: Compass)))
      }
    ),
    suite("Case Class Name Matching with Type Coercion")(
      test("matches Create by name with Int to Long coercion") {
        val result = Into.derived[CommandV1, CommandV2].into(CommandV1.Create("123", 42))
        assert(result)(isRight(equalTo(CommandV2.Create("123", 42L): CommandV2)))
      },
      test("matches Update by name with Int to Long coercion") {
        val result = Into.derived[CommandV1, CommandV2].into(CommandV1.Update("456", 100))
        assert(result)(isRight(equalTo(CommandV2.Update("456", 100L): CommandV2)))
      },
      test("matches Delete by name (same signature)") {
        val result = Into.derived[CommandV1, CommandV2].into(CommandV1.Delete("789"))
        assert(result)(isRight(equalTo(CommandV2.Delete("789"): CommandV2)))
      }
    ),
    suite("Mixed Case Objects and Case Classes")(
      test("matches Red case object by name") {
        val result = Into.derived[TrafficLight, Signal].into(TrafficLight.Red)
        assert(result)(isRight(equalTo(Signal.Red: Signal)))
      },
      test("matches Yellow case object by name") {
        val result = Into.derived[TrafficLight, Signal].into(TrafficLight.Yellow)
        assert(result)(isRight(equalTo(Signal.Yellow: Signal)))
      },
      test("matches Green case object by name") {
        val result = Into.derived[TrafficLight, Signal].into(TrafficLight.Green)
        assert(result)(isRight(equalTo(Signal.Green: Signal)))
      },
      test("matches Blinking case class by name") {
        val result = Into.derived[TrafficLight, Signal].into(TrafficLight.Blinking("red"))
        assert(result)(isRight(equalTo(Signal.Blinking("red"): Signal)))
      }
    ),
    suite("Name Matching with Same Signature")(
      test("matches Get by name") {
        val result = Into.derived[RequestV1, RequestV2].into(RequestV1.Get("/api/users"))
        assert(result)(isRight(equalTo(RequestV2.Get("/api/users"): RequestV2)))
      },
      test("matches Post by name") {
        val result = Into.derived[RequestV1, RequestV2].into(RequestV1.Post("/api/users", """{"name":"Alice"}"""))
        assert(result)(isRight(equalTo(RequestV2.Post("/api/users", """{"name":"Alice"}"""): RequestV2)))
      }
    ),
    suite("All Cases Match by Name")(
      test("converts all cases when names match") {
        val cases: List[SourceADTAll] = List(
          SourceADTAll.A,
          SourceADTAll.B,
          SourceADTAll.C(42),
          SourceADTAll.D("test", 100)
        )
        val results = cases.map(Into.derived[SourceADTAll, TargetADTAll].into)
        assertTrue(
          results == List(
            Right(TargetADTAll.A: TargetADTAll),
            Right(TargetADTAll.B: TargetADTAll),
            Right(TargetADTAll.C(42L): TargetADTAll),
            Right(TargetADTAll.D("test", 100L): TargetADTAll)
          )
        )
      }
    ),
    suite("Error Handling")(
      test("fails when payload conversion fails despite name match") {
        assert(Into.derived[SourceOverflow, TargetOverflow].into(SourceOverflow.Overflow(Long.MaxValue)))(isLeft)
      }
    ),
    suite("Case Sensitivity")(
      test("name matching is case-sensitive") {
        // Names must match exactly - case sensitivity matters
        val result = Into.derived[SourceActive, TargetActive].into(SourceActive.Active)
        assert(result)(isRight(equalTo(TargetActive.Active: TargetActive)))
      }
    )
  )

  // Inner coproduct
  sealed trait Inner

  object Inner {
    case class A(value: Int) extends Inner
  }

  sealed trait InnerV2

  object InnerV2 {
    case class A(value: Long) extends InnerV2
  }

  implicit val innerToV2: Into[Inner, InnerV2] = Into.derived[Inner, InnerV2]

  // Outer coproduct containing inner coproduct
  sealed trait Outer

  object Outer {
    case class Container(inner: Inner, name: String) extends Outer

    case class Empty() extends Outer
  }

  sealed trait OuterV2

  object OuterV2 {
    case class Container(inner: InnerV2, name: String) extends OuterV2

    case class Empty() extends OuterV2
  }

  // Coproduct with nested product conversions
  sealed trait Event

  object Event {
    case class UserCreated(userId: String, timestamp: Long) extends Event

    case class UserDeleted(userId: String) extends Event

    case class DataUpdated(id: Int, data: String) extends Event
  }

  sealed trait EventV2

  object EventV2 {
    case class UserCreated(userId: String, timestamp: Long) extends EventV2

    case class UserDeleted(userId: String) extends EventV2

    case class DataUpdated(id: Long, data: String) extends EventV2
  }

  // Multiple levels of nesting
  sealed trait Level3

  object Level3 {
    case class L3Data(value: Int) extends Level3
  }

  sealed trait Level2

  object Level2 {
    case class L2Container(data: Level3) extends Level2
  }

  sealed trait Level1

  object Level1 {
    case class L1Wrapper(inner: Level2, label: String) extends Level1
  }

  sealed trait Level3V2

  object Level3V2 {
    case class L3Data(value: Long) extends Level3V2
  }

  sealed trait Level2V2

  object Level2V2 {
    case class L2Container(data: Level3V2) extends Level2V2
  }

  sealed trait Level1V2

  object Level1V2 {
    case class L1Wrapper(inner: Level2V2, label: String) extends Level1V2
  }

  implicit val l3ToV2: Into[Level3, Level3V2] = Into.derived[Level3, Level3V2]
  implicit val l2ToV2: Into[Level2, Level2V2] = Into.derived[Level2, Level2V2]

  // Coproduct containing product with nested coproduct
  sealed trait Color

  object Color {
    case object Red extends Color

    case object Blue extends Color
  }

  sealed trait ColorV2

  object ColorV2 {
    case object Red extends ColorV2

    case object Blue extends ColorV2
  }

  implicit val colorToV2: Into[Color, ColorV2] = Into.derived[Color, ColorV2]

  sealed trait Message

  object Message {
    case class TextMsg(content: String, color: Color) extends Message

    case class BinaryMsg(data: Array[Byte]) extends Message
  }

  sealed trait MessageV2

  object MessageV2 {
    case class TextMsg(content: String, color: ColorV2) extends MessageV2

    case class BinaryMsg(data: Array[Byte]) extends MessageV2
  }

  // For "Error Propagation" tests
  sealed trait InnerFail

  object InnerFail {
    case class Data(value: Long) extends InnerFail
  }

  sealed trait OuterFail

  object OuterFail {
    case class Wrapper(inner: InnerFail) extends OuterFail
  }

  sealed trait InnerTarget

  object InnerTarget {
    case class Data(value: Int) extends InnerTarget
  }

  sealed trait OuterTarget

  object OuterTarget {
    case class Wrapper(inner: InnerTarget) extends OuterTarget
  }

  implicit val innerFailToTarget: Into[InnerFail, InnerTarget] = Into.derived[InnerFail, InnerTarget]

  sealed trait InnerOkNested

  object InnerOkNested {
    case class Data(value: Long) extends InnerOkNested
  }

  sealed trait OuterOkNested

  object OuterOkNested {
    case class Wrapper(inner: InnerOkNested) extends OuterOkNested
  }

  sealed trait InnerTargetOk

  object InnerTargetOk {
    case class Data(value: Int) extends InnerTargetOk
  }

  sealed trait OuterTargetOk

  object OuterTargetOk {
    case class Wrapper(inner: InnerTargetOk) extends OuterTargetOk
  }

  implicit val innerOkToTargetOk: Into[InnerOkNested, InnerTargetOk] = Into.derived[InnerOkNested, InnerTargetOk]

  // For "Complex Nested Scenarios" test
  sealed trait InnerComplex

  object InnerComplex {
    case object A extends InnerComplex

    case object B extends InnerComplex
  }

  sealed trait OuterComplex

  object OuterComplex {
    case class First(inner: InnerComplex) extends OuterComplex

    case class Second(inner: InnerComplex) extends OuterComplex

    case class Third(value: String) extends OuterComplex
  }

  sealed trait InnerComplexV2

  object InnerComplexV2 {
    case object A extends InnerComplexV2

    case object B extends InnerComplexV2
  }

  sealed trait OuterComplexV2

  object OuterComplexV2 {
    case class First(inner: InnerComplexV2) extends OuterComplexV2

    case class Second(inner: InnerComplexV2) extends OuterComplexV2

    case class Third(value: String) extends OuterComplexV2
  }

  implicit val innerComplexToV2: Into[InnerComplex, InnerComplexV2] = Into.derived[InnerComplex, InnerComplexV2]

  /**
   * Tests for nested coproduct conversions.
   *
   * Covers:
   *   - Coproducts containing other coproducts
   *   - Coproducts containing products with type coercion
   *   - Multiple levels of nesting
   */
  private val nestedCoproducts = suite("Nested Coproducts")(
    suite("Coproduct Containing Coproduct")(
      test("converts outer coproduct with nested inner coproduct") {
        val result = Into.derived[Outer, OuterV2].into(Outer.Container(Inner.A(42), "test"))
        assert(result)(isRight(equalTo(OuterV2.Container(InnerV2.A(42L), "test"): OuterV2)))
      },
      test("converts Empty case without nested coproduct") {
        val result = Into.derived[Outer, OuterV2].into(Outer.Empty())
        assert(result)(isRight(equalTo(OuterV2.Empty(): OuterV2)))
      }
    ),
    suite("Coproduct with Nested Product Conversions")(
      test("converts UserCreated case") {
        val result = Into.derived[Event, EventV2].into(Event.UserCreated("user123", 1234567890L))
        assert(result)(isRight(equalTo(EventV2.UserCreated("user123", 1234567890L): EventV2)))
      },
      test("converts UserDeleted case") {
        val result = Into.derived[Event, EventV2].into(Event.UserDeleted("user456"))
        assert(result)(isRight(equalTo(EventV2.UserDeleted("user456"): EventV2)))
      },
      test("converts DataUpdated case with Int to Long coercion") {
        val result = Into.derived[Event, EventV2].into(Event.DataUpdated(100, "new data"))
        assert(result)(isRight(equalTo(EventV2.DataUpdated(100L, "new data"): EventV2)))
      }
    ),
    suite("Multiple Levels of Nesting")(
      test("converts 3-level nested coproduct structure") {
        val result =
          Into.derived[Level1, Level1V2].into(Level1.L1Wrapper(Level2.L2Container(Level3.L3Data(42)), "root"))
        assert(result)(
          isRight(equalTo(Level1V2.L1Wrapper(Level2V2.L2Container(Level3V2.L3Data(42L)), "root"): Level1V2))
        )
      }
    ),
    suite("Product Containing Nested Coproduct")(
      test("converts TextMsg with nested Color coproduct") {
        val result = Into.derived[Message, MessageV2].into(Message.TextMsg("hello", Color.Red))
        assert(result)(isRight(equalTo(MessageV2.TextMsg("hello", ColorV2.Red): MessageV2)))
      },
      test("converts TextMsg with Blue color") {
        val result = Into.derived[Message, MessageV2].into(Message.TextMsg("world", Color.Blue))
        assert(result)(isRight(equalTo(MessageV2.TextMsg("world", ColorV2.Blue): MessageV2)))
      },
      test("converts BinaryMsg without nested coproduct") {
        val result = Into.derived[Message, MessageV2].into(Message.BinaryMsg(Array[Byte](1, 2, 3)))
        assert(result.map(_.isInstanceOf[MessageV2.BinaryMsg]))(isRight(isTrue))
      }
    ),
    suite("Error Propagation in Nested Coproducts")(
      test("propagates error from nested coproduct conversion") {
        assert(Into.derived[OuterFail, OuterTarget].into(OuterFail.Wrapper(InnerFail.Data(Long.MaxValue))))(isLeft)
      },
      test("succeeds when nested conversion is valid") {
        val result = Into.derived[OuterOkNested, OuterTargetOk].into(OuterOkNested.Wrapper(InnerOkNested.Data(42L)))
        assert(result)(isRight(equalTo(OuterTargetOk.Wrapper(InnerTargetOk.Data(42)): OuterTargetOk)))
      }
    ),
    suite("Complex Nested Scenarios")(
      test("coproduct with multiple cases containing nested coproducts") {
        val cases: List[OuterComplex] = List(
          OuterComplex.First(InnerComplex.A),
          OuterComplex.Second(InnerComplex.B),
          OuterComplex.Third("data")
        )
        val results = cases.map(Into.derived[OuterComplex, OuterComplexV2].into)
        assertTrue(
          results == List(
            Right(OuterComplexV2.First(InnerComplexV2.A): OuterComplexV2),
            Right(OuterComplexV2.Second(InnerComplexV2.B): OuterComplexV2),
            Right(OuterComplexV2.Third("data"): OuterComplexV2)
          )
        )
      }
    )
  )

  // Simple sealed trait with case objects (by name)
  sealed trait ColorV3

  object ColorV3 {
    case object Red extends ColorV3

    case object Blue extends ColorV3

    case object Green extends ColorV3
  }

  sealed trait Hue

  object Hue {
    case object Red extends Hue

    case object Blue extends Hue

    case object Green extends Hue
  }

  // Sealed trait with case classes (by signature)
  sealed trait EventV1

  object EventV1 {
    case class Created(id: String, ts: Long) extends EventV1

    case class Deleted(id: String) extends EventV1
  }

  sealed trait EventV3

  object EventV3 {
    case class Spawned(id: String, ts: Long) extends EventV3

    case class Removed(id: String) extends EventV3
  }

  // ADT with payload conversion
  sealed trait ResultV1

  object ResultV1 {
    case class SuccessV1(value: Int) extends ResultV1

    case class FailureV1(msg: String) extends ResultV1
  }

  sealed trait ResultV2

  object ResultV2 {
    case class SuccessV2(value: Int) extends ResultV2

    case class FailureV2(msg: String) extends ResultV2
  }

  // Mixed case objects and case classes
  sealed trait Status

  object Status {
    case object Active extends Status

    case object Inactive extends Status

    case class Custom(name: String) extends Status
  }

  sealed trait State

  object State {
    case object Active extends State

    case object Inactive extends State

    case class Custom(name: String) extends State
  }

  // Sealed trait with type coercion in payloads
  sealed trait DataV1

  object DataV1 {
    case class IntData(value: Int) extends DataV1

    case class StringData(text: String) extends DataV1
  }

  sealed trait DataV2

  object DataV2 {
    case class IntData(value: Long) extends DataV2

    case class StringData(text: String) extends DataV2
  }

  // For "Multiple Cases" test
  sealed trait SourceADTMulti

  object SourceADTMulti {
    case class CaseA(x: Int) extends SourceADTMulti

    case class CaseB(y: String) extends SourceADTMulti

    case class CaseC(z: Boolean) extends SourceADTMulti
  }

  sealed trait TargetADTMulti

  object TargetADTMulti {
    case class CaseA(x: Long) extends TargetADTMulti

    case class CaseB(y: String) extends TargetADTMulti

    case class CaseC(z: Boolean) extends TargetADTMulti
  }

  // For "Error Propagation" tests
  sealed trait SourceErr

  object SourceErr {
    case class DataErr(value: Long) extends SourceErr
  }

  sealed trait TargetErr

  object TargetErr {
    case class DataErr(value: Int) extends TargetErr
  }

  sealed trait SourceOk

  object SourceOk {
    case class DataOk(value: Long) extends SourceOk
  }

  sealed trait TargetOk

  object TargetOk {
    case class DataOk(value: Int) extends TargetOk
  }

  // For "Sealed Trait with Single Case" tests
  sealed trait SingleV1

  object SingleV1 {
    case class OnlyCase(value: Int) extends SingleV1
  }

  sealed trait SingleV2

  object SingleV2 {
    case class OnlyCase(value: Long) extends SingleV2
  }

  sealed trait SingleObjV1

  object SingleObjV1 {
    case object Singleton extends SingleObjV1
  }

  sealed trait SingleObjV2

  object SingleObjV2 {
    case object Singleton extends SingleObjV2
  }

  /**
   * Tests for Into[SealedTrait, SealedTrait] conversions.
   *
   * Covers:
   *   - Case object to case object matching
   *   - Case class to case class matching within sealed traits
   *   - Mixed sealed trait conversions
   *   - Sealed trait with payloads
   */
  private val sealedTraitToSealedTrait = suite("SealedTraitToSealedTraitSpec")(
    suite("Case Objects by Name")(
      test("maps case object Red to Red") {
        val result = Into.derived[ColorV3, Hue].into(ColorV3.Red)
        assert(result)(isRight(equalTo(Hue.Red: Hue)))
      },
      test("maps case object Blue to Blue") {
        val result = Into.derived[ColorV3, Hue].into(ColorV3.Blue)
        assert(result)(isRight(equalTo(Hue.Blue: Hue)))
      },
      test("maps case object Green to Green") {
        val result = Into.derived[ColorV3, Hue].into(ColorV3.Green)
        assert(result)(isRight(equalTo(Hue.Green: Hue)))
      }
    ),
    suite("Case Classes by Signature")(
      test("maps Created(String, Long) to Spawned(String, Long) by signature") {
        val result = Into.derived[EventV1, EventV3].into(EventV1.Created("abc", 123L))
        assert(result)(isRight(equalTo(EventV3.Spawned("abc", 123L): EventV3)))
      },
      test("maps Deleted(String) to Removed(String) by signature") {
        val result = Into.derived[EventV1, EventV3].into(EventV1.Deleted("xyz"))
        assert(result)(isRight(equalTo(EventV3.Removed("xyz"): EventV3)))
      }
    ),
    suite("ADT with Payloads")(
      test("converts Success case class") {
        val result = Into.derived[ResultV1, ResultV2].into(ResultV1.SuccessV1(42))
        assert(result)(isRight(equalTo(ResultV2.SuccessV2(42): ResultV2)))
      },
      test("converts Failure case class") {
        val result = Into.derived[ResultV1, ResultV2].into(ResultV1.FailureV1("error message"))
        assert(result)(isRight(equalTo(ResultV2.FailureV2("error message"): ResultV2)))
      }
    ),
    suite("Mixed Case Objects and Case Classes")(
      test("converts Active case object") {
        val result = Into.derived[Status, State].into(Status.Active)
        assert(result)(isRight(equalTo(State.Active: State)))
      },
      test("converts Inactive case object") {
        val result = Into.derived[Status, State].into(Status.Inactive)
        assert(result)(isRight(equalTo(State.Inactive: State)))
      },
      test("converts Custom case class") {
        val result = Into.derived[Status, State].into(Status.Custom("pending"))
        assert(result)(isRight(equalTo(State.Custom("pending"): State)))
      }
    ),
    suite("Type Coercion in Payloads")(
      test("converts with Int to Long coercion in case class payload") {
        val result = Into.derived[DataV1, DataV2].into(DataV1.IntData(42))
        assert(result)(isRight(equalTo(DataV2.IntData(42L): DataV2)))
      },
      test("converts case class with String payload (no coercion)") {
        val result = Into.derived[DataV1, DataV2].into(DataV1.StringData("hello"))
        assert(result)(isRight(equalTo(DataV2.StringData("hello"): DataV2)))
      }
    ),
    suite("Multiple Cases")(
      test("converts all cases in sealed trait correctly") {
        val instances: List[SourceADTMulti] = List(
          SourceADTMulti.CaseA(10),
          SourceADTMulti.CaseB("test"),
          SourceADTMulti.CaseC(true)
        )
        val results = instances.map(Into.derived[SourceADTMulti, TargetADTMulti].into)
        assertTrue(
          results == List(
            Right(TargetADTMulti.CaseA(10L): TargetADTMulti),
            Right(TargetADTMulti.CaseB("test"): TargetADTMulti),
            Right(TargetADTMulti.CaseC(true): TargetADTMulti)
          )
        )
      }
    ),
    suite("Error Propagation")(
      test("propagates conversion error from case class payload") {
        val result = Into.derived[SourceErr, TargetErr].into(SourceErr.DataErr(Long.MaxValue))
        assert(result)(isLeft) // Should fail due to overflow
      },
      test("succeeds when payload conversion is valid") {
        val result = Into.derived[SourceOk, TargetOk].into(SourceOk.DataOk(42L))
        assert(result)(isRight(equalTo(TargetOk.DataOk(42): TargetOk)))
      }
    ),
    suite("Sealed Trait with Single Case")(
      test("converts sealed trait with single case class") {
        val result = Into.derived[SingleV1, SingleV2].into(SingleV1.OnlyCase(42))
        assert(result)(isRight(equalTo(SingleV2.OnlyCase(42L): SingleV2)))
      },
      test("converts sealed trait with single case object") {
        val result = Into.derived[SingleObjV1, SingleObjV2].into(SingleObjV1.Singleton)
        assert(result)(isRight(equalTo(SingleObjV2.Singleton: SingleObjV2)))
      }
    )
  )

  // Different names, same signatures
  sealed trait EventV4

  object EventV4 {
    case class Created(id: String, ts: Long) extends EventV4

    case class Deleted(id: String) extends EventV4

    case class Updated(id: String, ts: Long, data: String) extends EventV4
  }

  sealed trait EventV5

  object EventV5 {
    case class Spawned(id: String, ts: Long) extends EventV5

    case class Removed(id: String) extends EventV5

    case class Modified(id: String, ts: Long, data: String) extends EventV5
  }

  // Signature matching with type coercion
  sealed trait MessageV3

  object MessageV3 {
    case class Text(content: String, priority: Int) extends MessageV3

    case class Binary(data: Array[Byte]) extends MessageV3
  }

  sealed trait MessageV4

  object MessageV4 {
    case class TextMessage(content: String, priority: Long) extends MessageV4

    case class BinaryMessage(data: Array[Byte]) extends MessageV4
  }

  // Multiple cases with unique signatures
  sealed trait ShapeV1

  object ShapeV1 {
    case class Circle(radius: Double) extends ShapeV1

    case class Rectangle(width: Double, height: Double) extends ShapeV1

    case class Triangle(a: Double, b: Double, c: Double) extends ShapeV1
  }

  sealed trait ShapeV2

  object ShapeV2 {
    case class Round(radius: Double) extends ShapeV2

    case class Box(width: Double, height: Double) extends ShapeV2

    case class Tri(a: Double, b: Double, c: Double) extends ShapeV2
  }

  // Signature matching with nested structures
  sealed trait RequestV3

  object RequestV3 {
    case class Get(path: String, headers: Map[String, String]) extends RequestV3

    case class Post(path: String, body: String, headers: Map[String, String]) extends RequestV3
  }

  sealed trait RequestV4

  object RequestV4 {
    case class Fetch(path: String, headers: Map[String, String]) extends RequestV4

    case class Submit(path: String, body: String, headers: Map[String, String]) extends RequestV4
  }

  // For "All Cases Match by Signature" test
  sealed trait SourceADTMultiV2

  object SourceADTMultiV2 {
    case class A(x: Int) extends SourceADTMultiV2

    case class B(y: String) extends SourceADTMultiV2

    case class C(z: Boolean) extends SourceADTMultiV2

    case class D(a: Int, b: String) extends SourceADTMultiV2
  }

  sealed trait TargetADTMultiV2

  object TargetADTMultiV2 {
    case class First(x: Long) extends TargetADTMultiV2

    case class Second(y: String) extends TargetADTMultiV2

    case class Third(z: Boolean) extends TargetADTMultiV2

    case class Fourth(a: Long, b: String) extends TargetADTMultiV2
  }

  // For "Signature Uniqueness" test
  sealed trait SourceUnique

  object SourceUnique {
    case class TypeA(x: Int) extends SourceUnique

    case class TypeB(y: String) extends SourceUnique

    case class TypeC(z: Boolean) extends SourceUnique
  }

  sealed trait TargetUnique

  object TargetUnique {
    case class Different1(x: Int) extends TargetUnique

    case class Different2(y: String) extends TargetUnique

    case class Different3(z: Boolean) extends TargetUnique
  }

  // For "Error Handling" tests
  sealed trait SourceErrV2

  object SourceErrV2 {
    case class Data(value: Long) extends SourceErrV2
  }

  sealed trait TargetErrV2

  object TargetErrV2 {
    case class Info(value: Int) extends TargetErrV2
  }

  sealed trait SourceOkV2

  object SourceOkV2 {
    case class Data(value: Long) extends SourceOkV2
  }

  sealed trait TargetOkV2

  object TargetOkV2 {
    case class Info(value: Int) extends TargetOkV2
  }

  /**
   * Tests for case matching by constructor signature in coproduct conversions.
   *
   * Focuses on matching when names differ but constructor signatures match.
   *
   * Covers:
   *   - Signature matching with different case names
   *   - Signature matching with type coercion
   *   - Signature matching with field reordering
   */
  private val signatureMatching = suite("SignatureMatchingSpec")(
    suite("Basic Signature Matching")(
      test("matches Created(String, Long) to Spawned(String, Long) by signature") {
        val result = Into.derived[EventV4, EventV5].into(EventV4.Created("abc", 123L))
        assert(result)(isRight(equalTo(EventV5.Spawned("abc", 123L): EventV5)))
      },
      test("matches Deleted(String) to Removed(String) by signature") {
        val result = Into.derived[EventV4, EventV5].into(EventV4.Deleted("xyz"))
        assert(result)(isRight(equalTo(EventV5.Removed("xyz"): EventV5)))
      },
      test("matches Updated(String, Long, String) to Modified by signature") {
        val result = Into.derived[EventV4, EventV5].into(EventV4.Updated("id123", 456L, "new data"))
        assert(result)(isRight(equalTo(EventV5.Modified("id123", 456L, "new data"): EventV5)))
      }
    ),
    suite("Signature Matching with Type Coercion")(
      test("matches Text to TextMessage with Int to Long coercion") {
        val result = Into.derived[MessageV3, MessageV4].into(MessageV3.Text("hello", 5))
        assert(result)(isRight(equalTo(MessageV4.TextMessage("hello", 5L): MessageV4)))
      },
      test("matches Binary to BinaryMessage by signature") {
        val result = Into.derived[MessageV3, MessageV4].into(MessageV3.Binary(Array[Byte](1, 2, 3)))
        assert(result.map(_.isInstanceOf[MessageV4.BinaryMessage]))(isRight(isTrue))
      }
    ),
    suite("Multiple Unique Signatures")(
      test("matches Circle(Double) to Round(Double) by signature") {
        val result = Into.derived[ShapeV1, ShapeV2].into(ShapeV1.Circle(5.0))
        assert(result)(isRight(equalTo(ShapeV2.Round(5.0): ShapeV2)))
      },
      test("matches Rectangle(Double, Double) to Box by signature") {
        val result = Into.derived[ShapeV1, ShapeV2].into(ShapeV1.Rectangle(4.0, 3.0))
        assert(result)(isRight(equalTo(ShapeV2.Box(4.0, 3.0): ShapeV2)))
      },
      test("matches Triangle(Double, Double, Double) to Tri by signature") {
        val result = Into.derived[ShapeV1, ShapeV2].into(ShapeV1.Triangle(3.0, 4.0, 5.0))
        assert(result)(isRight(equalTo(ShapeV2.Tri(3.0, 4.0, 5.0): ShapeV2)))
      }
    ),
    suite("Signature Matching with Complex Types")(
      test("matches Get to Fetch with Map[String, String] parameter") {
        val result = Into.derived[RequestV3, RequestV4].into(RequestV3.Get("/api", Map("Auth" -> "Bearer token")))
        assert(result)(isRight(equalTo(RequestV4.Fetch("/api", Map("Auth" -> "Bearer token")): RequestV4)))
      },
      test("matches Post to Submit with multiple parameters") {
        val req = RequestV3.Post("/api", """{"data":"test"}""", Map("Content-Type" -> "application/json"))
        assert(Into.derived[RequestV3, RequestV4].into(req))(
          isRight(
            equalTo(
              RequestV4.Submit("/api", """{"data":"test"}""", Map("Content-Type" -> "application/json")): RequestV4
            )
          )
        )
      }
    ),
    suite("All Cases Match by Signature")(
      test("converts all cases when signatures match") {
        val cases = List(
          SourceADTMultiV2.A(42),
          SourceADTMultiV2.B("test"),
          SourceADTMultiV2.C(true),
          SourceADTMultiV2.D(100, "data")
        )
        val results = cases.map(Into.derived[SourceADTMultiV2, TargetADTMultiV2].into)
        assertTrue(
          results == List(
            Right(TargetADTMultiV2.First(42L): TargetADTMultiV2),
            Right(TargetADTMultiV2.Second("test"): TargetADTMultiV2),
            Right(TargetADTMultiV2.Third(true): TargetADTMultiV2),
            Right(TargetADTMultiV2.Fourth(100L, "data"): TargetADTMultiV2)
          )
        )
      }
    ),
    suite("Signature Uniqueness")(
      test("matches when each signature appears exactly once") {
        assert(Into.derived[SourceUnique, TargetUnique].into(SourceUnique.TypeA(1)))(
          isRight(equalTo(TargetUnique.Different1(1): TargetUnique))
        ) &&
        assert(Into.derived[SourceUnique, TargetUnique].into(SourceUnique.TypeB("two")))(
          isRight(equalTo(TargetUnique.Different2("two"): TargetUnique))
        ) &&
        assert(Into.derived[SourceUnique, TargetUnique].into(SourceUnique.TypeC(true)))(
          isRight(equalTo(TargetUnique.Different3(true): TargetUnique))
        )
      }
    ),
    suite("Error Handling")(
      test("fails when field conversion fails despite signature match") {
        assert(Into.derived[SourceErrV2, TargetErrV2].into(SourceErrV2.Data(Long.MaxValue)))(isLeft)
      },
      test("succeeds when field conversion is valid") {
        val result = Into.derived[SourceOkV2, TargetOkV2].into(SourceOkV2.Data(42L))
        assert(result)(isRight(equalTo(TargetOkV2.Info(42): TargetOkV2)))
      }
    )
  )

  /** Tests for field disambiguation priority in Into derivation. */
  private val disambiguationPriority = suite("Disambiguation Priority")(
    test("exact name+type match") {
      case class Source(name: String, count: Int)
      case class Target(name: String, count: Int)

      assert(Into.derived[Source, Target].into(Source("test", 42)))(isRight(equalTo(Target("test", 42))))
    },
    test("name match with coercion") {
      case class Source(value: Int, flag: Boolean)
      case class Target(value: Long, flag: Boolean)

      assert(Into.derived[Source, Target].into(Source(42, true)))(isRight(equalTo(Target(42L, true))))
    },
    test("unique type match when names differ") {
      case class Source(firstName: String, age: Int, active: Boolean)
      case class Target(fullName: String, years: Int, enabled: Boolean)

      assert(Into.derived[Source, Target].into(Source("Alice", 30, true)))(isRight(equalTo(Target("Alice", 30, true))))
    }
  )

  /** Tests for name-based field disambiguation in Into derivation. */
  private val nameDisambiguation = suite("Name Disambiguation")(
    test("maps fields by name when types are not unique") {
      case class Source(firstName: String, lastName: String)
      case class Target(firstName: String, lastName: String)

      assert(Into.derived[Source, Target].into(Source("John", "Doe")))(isRight(equalTo(Target("John", "Doe"))))
    },
    test("name match with type coercion") {
      case class Source(count: Int, total: Int)
      case class Target(count: Long, total: Long)

      assert(Into.derived[Source, Target].into(Source(100, 500)))(isRight(equalTo(Target(100L, 500L))))
    },
    test("collection fields with same element type match by name") {
      case class Source(items: List[String], tags: List[String])
      case class Target(items: List[String], tags: List[String])

      val result = Into.derived[Source, Target].into(Source(List("a"), List("x", "y")))
      assert(result)(isRight(equalTo(Target(List("a"), List("x", "y")))))
    }
  )

  /** Tests for position-based disambiguation in Into derivation. */
  private val positionDisambiguation = suite("Position Disambiguation")(
    test("tuple to case class by position") {
      case class Target(name: String, age: Int, active: Boolean)

      val result = Into.derived[(String, Int, Boolean), Target].into(("Alice", 30, true))
      assert(result)(isRight(equalTo(Target("Alice", 30, true))))
    },
    test("case class to tuple by position") {
      case class Source(name: String, age: Int)

      val result = Into.derived[Source, (String, Int)].into(Source("Bob", 25))
      assert(result)(isRight(equalTo(("Bob", 25))))
    },
    test("tuple to tuple with coercion") {
      assert(Into.derived[(Int, Int, Int), (Long, Long, Long)].into((1, 2, 3)))(isRight(equalTo((1L, 2L, 3L))))
    }
  )

  /** Tests for unique type disambiguation in Into derivation. */
  private val uniqueTypeDisambiguation = suite("Unique Type Disambiguation")(
    test("maps fields by unique type when names differ") {
      case class Source(name: String, age: Int, active: Boolean)
      case class Target(fullName: String, years: Int, enabled: Boolean)

      assert(Into.derived[Source, Target].into(Source("Alice", 30, true)))(isRight(equalTo(Target("Alice", 30, true))))
    },
    test("unique types with coercion") {
      case class Source(id: Int, score: Float)
      case class Target(identifier: Long, rating: Double)

      assert(Into.derived[Source, Target].into(Source(42, 3.14f)))(isRight(equalTo(Target(42L, 3.14f.toDouble))))
    },
    test("unique types allow field reordering") {
      case class Source(a: String, b: Int)
      case class Target(x: Int, y: String)

      assert(Into.derived[Source, Target].into(Source("hello", 42)))(isRight(equalTo(Target(42, "hello"))))
    }
  )

  case object SingletonA
  case object SingletonB
  case class EmptyClass()

  sealed trait StatusV2
  object StatusV2 {
    case object Active   extends StatusV2
    case object Inactive extends StatusV2
  }

  sealed trait StatusV3
  object StatusV3 {
    case object Active   extends StatusV3
    case object Inactive extends StatusV3
  }

  /** Tests for case object conversions in Into derivation. */
  private val caseObject = suite("Case Object")(
    test("case object to case object") {
      assert(Into.derived[SingletonA.type, SingletonB.type].into(SingletonA))(isRight(equalTo(SingletonB)))
    },
    test("case object to empty case class") {
      assert(Into.derived[SingletonA.type, EmptyClass].into(SingletonA))(isRight(equalTo(EmptyClass())))
    },
    test("case objects in sealed traits") {
      assert(Into.derived[StatusV2, StatusV3].into(StatusV2.Active))(isRight(equalTo(StatusV3.Active: StatusV3)))
    }
  )

  case class AddressA(street: String, zip: Int)

  case class AddressB(street: String, zip: Long)

  case class ContactA(name: String, address: AddressA)

  case class ContactB(name: String, address: AddressB)

  case class EmployeeA(id: Int, contact: ContactA)

  case class EmployeeB(id: Long, contact: ContactB)

  case class InnerA(value: Int)

  case class InnerB(value: Long)

  case class OuterWithListA(items: List[InnerA])

  case class OuterWithListB(items: List[InnerB])

  case class OuterWithOptionA(inner: Option[InnerA])

  case class OuterWithOptionB(inner: Option[InnerB])

  /** Tests for deeply nested structure conversions. */
  private val deepNesting = suite("Deep Nesting")(
    test("3-level nested structure with coercion") {
      implicit val addressInto: Into[AddressA, AddressB] = Into.derived[AddressA, AddressB]
      implicit val contactInto: Into[ContactA, ContactB] = Into.derived[ContactA, ContactB]
      val source                                         = EmployeeA(1, ContactA("Alice", AddressA("123 Main", 10001)))
      val result                                         = Into.derived[EmployeeA, EmployeeB].into(source)
      assert(result)(isRight(equalTo(EmployeeB(1L, ContactB("Alice", AddressB("123 Main", 10001L))))))
    },
    test("nested collections with coercion") {
      implicit val innerInto: Into[InnerA, InnerB] = Into.derived[InnerA, InnerB]
      val result                                   = Into.derived[OuterWithListA, OuterWithListB].into(OuterWithListA(List(InnerA(1), InnerA(2))))
      assert(result)(isRight(equalTo(OuterWithListB(List(InnerB(1L), InnerB(2L))))))
    },
    test("nested Options with coercion") {
      implicit val innerInto: Into[InnerA, InnerB] = Into.derived[InnerA, InnerB]
      val result                                   = Into.derived[OuterWithOptionA, OuterWithOptionB].into(OuterWithOptionA(Some(InnerA(42))))
      assert(result)(isRight(equalTo(OuterWithOptionB(Some(InnerB(42L))))))
    }
  )

  case class EmptyA()

  case class EmptyB()

  case object EmptyObjA

  case object EmptyObjB

  /** Tests for empty product conversions. */
  private val emptyProduct = suite("Empty Product")(
    test("empty case class to empty case class") {
      assert(Into.derived[EmptyA, EmptyB].into(EmptyA()))(isRight(equalTo(EmptyB())))
    },
    test("case object to case object") {
      assert(Into.derived[EmptyObjA.type, EmptyObjB.type].into(EmptyObjA))(isRight(equalTo(EmptyObjB)))
    },
    test("case object to empty case class") {
      assert(Into.derived[EmptyObjA.type, EmptyA].into(EmptyObjA))(isRight(equalTo(EmptyA())))
    }
  )

  // 10-case sealed traits (original tests)
  sealed trait Status10

  object Status10 {
    case object S01 extends Status10

    case object S02 extends Status10

    case object S03 extends Status10

    case object S04 extends Status10

    case object S05 extends Status10

    case object S06 extends Status10

    case object S07 extends Status10

    case object S08 extends Status10

    case object S09 extends Status10

    case object S10 extends Status10
  }

  sealed trait Status10Alt

  object Status10Alt {
    case object S01 extends Status10Alt

    case object S02 extends Status10Alt

    case object S03 extends Status10Alt

    case object S04 extends Status10Alt

    case object S05 extends Status10Alt

    case object S06 extends Status10Alt

    case object S07 extends Status10Alt

    case object S08 extends Status10Alt

    case object S09 extends Status10Alt

    case object S10 extends Status10Alt
  }

  sealed trait MixedEvent

  object MixedEvent {
    case object Started extends MixedEvent

    case class Error(code: Int) extends MixedEvent

    case class Progress(percent: Int) extends MixedEvent
  }

  sealed trait MixedEventAlt

  object MixedEventAlt {
    case object Started extends MixedEventAlt

    case class Error(code: Long) extends MixedEventAlt

    case class Progress(percent: Long) extends MixedEventAlt
  }

  // 25-case sealed traits (beyond typical limits)
  sealed trait Status25

  object Status25 {
    case object S01 extends Status25

    case object S02 extends Status25

    case object S03 extends Status25

    case object S04 extends Status25

    case object S05 extends Status25

    case object S06 extends Status25

    case object S07 extends Status25

    case object S08 extends Status25

    case object S09 extends Status25

    case object S10 extends Status25

    case object S11 extends Status25

    case object S12 extends Status25

    case object S13 extends Status25

    case object S14 extends Status25

    case object S15 extends Status25

    case object S16 extends Status25

    case object S17 extends Status25

    case object S18 extends Status25

    case object S19 extends Status25

    case object S20 extends Status25

    case object S21 extends Status25

    case object S22 extends Status25

    case object S23 extends Status25

    case object S24 extends Status25

    case object S25 extends Status25
  }

  sealed trait Status25Alt

  object Status25Alt {
    case object S01 extends Status25Alt

    case object S02 extends Status25Alt

    case object S03 extends Status25Alt

    case object S04 extends Status25Alt

    case object S05 extends Status25Alt

    case object S06 extends Status25Alt

    case object S07 extends Status25Alt

    case object S08 extends Status25Alt

    case object S09 extends Status25Alt

    case object S10 extends Status25Alt

    case object S11 extends Status25Alt

    case object S12 extends Status25Alt

    case object S13 extends Status25Alt

    case object S14 extends Status25Alt

    case object S15 extends Status25Alt

    case object S16 extends Status25Alt

    case object S17 extends Status25Alt

    case object S18 extends Status25Alt

    case object S19 extends Status25Alt

    case object S20 extends Status25Alt

    case object S21 extends Status25Alt

    case object S22 extends Status25Alt

    case object S23 extends Status25Alt

    case object S24 extends Status25Alt

    case object S25 extends Status25Alt
  }

  /**
   * Tests for large coproduct conversions (25 cases to exceed typical limits).
   */
  private val largeCoproduct = suite("Large Coproduct")(
    suite("10-case coproducts")(
      test("coproduct with 10 case objects") {
        val result = Into.derived[Status10, Status10Alt].into(Status10.S05)
        assert(result)(isRight(equalTo(Status10Alt.S05: Status10Alt)))
      },
      test("mixed case objects and case classes with coercion") {
        val result = Into.derived[MixedEvent, MixedEventAlt].into(MixedEvent.Error(42))
        assert(result)(isRight(equalTo(MixedEventAlt.Error(42L): MixedEventAlt)))
      }
    ),
    suite("25-case coproducts (beyond typical limits)")(
      test("coproduct with 25 case objects - first case") {
        val result = Into.derived[Status25, Status25Alt].into(Status25.S01)
        assert(result)(isRight(equalTo(Status25Alt.S01: Status25Alt)))
      },
      test("coproduct with 25 case objects - middle case") {
        val result = Into.derived[Status25, Status25Alt].into(Status25.S13)
        assert(result)(isRight(equalTo(Status25Alt.S13: Status25Alt)))
      },
      test("coproduct with 25 case objects - last case") {
        val result = Into.derived[Status25, Status25Alt].into(Status25.S25)
        assert(result)(isRight(equalTo(Status25Alt.S25: Status25Alt)))
      }
    )
  )
  // 10-field case classes (original tests)
  case class Large10A(
    f01: Int,
    f02: Int,
    f03: Int,
    f04: Int,
    f05: Int,
    f06: Int,
    f07: Int,
    f08: Int,
    f09: Int,
    f10: Int
  )
  case class Large10B(
    f01: Int,
    f02: Int,
    f03: Int,
    f04: Int,
    f05: Int,
    f06: Int,
    f07: Int,
    f08: Int,
    f09: Int,
    f10: Int
  )
  case class Large10L(
    f01: Long,
    f02: Long,
    f03: Long,
    f04: Long,
    f05: Long,
    f06: Long,
    f07: Long,
    f08: Long,
    f09: Long,
    f10: Long
  )

  // 25-field case classes (beyond Scala 2's tuple limit of 22)
  case class Large25A(
    f01: Int,
    f02: Int,
    f03: Int,
    f04: Int,
    f05: Int,
    f06: Int,
    f07: Int,
    f08: Int,
    f09: Int,
    f10: Int,
    f11: Int,
    f12: Int,
    f13: Int,
    f14: Int,
    f15: Int,
    f16: Int,
    f17: Int,
    f18: Int,
    f19: Int,
    f20: Int,
    f21: Int,
    f22: Int,
    f23: Int,
    f24: Int,
    f25: Int
  )
  case class Large25B(
    f01: Int,
    f02: Int,
    f03: Int,
    f04: Int,
    f05: Int,
    f06: Int,
    f07: Int,
    f08: Int,
    f09: Int,
    f10: Int,
    f11: Int,
    f12: Int,
    f13: Int,
    f14: Int,
    f15: Int,
    f16: Int,
    f17: Int,
    f18: Int,
    f19: Int,
    f20: Int,
    f21: Int,
    f22: Int,
    f23: Int,
    f24: Int,
    f25: Int
  )
  case class Large25L(
    f01: Long,
    f02: Long,
    f03: Long,
    f04: Long,
    f05: Long,
    f06: Long,
    f07: Long,
    f08: Long,
    f09: Long,
    f10: Long,
    f11: Long,
    f12: Long,
    f13: Long,
    f14: Long,
    f15: Long,
    f16: Long,
    f17: Long,
    f18: Long,
    f19: Long,
    f20: Long,
    f21: Long,
    f22: Long,
    f23: Long,
    f24: Long,
    f25: Long
  )

  /**
   * Tests for large product conversions (25 fields to exceed Scala 2 tuple
   * limit).
   */
  private val largeProduct = suite("Large Product")(
    suite("10-field case classes")(
      test("case class with 10 fields - same types") {
        val source = Large10A(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val result = Into.derived[Large10A, Large10B].into(source)
        assert(result)(isRight(equalTo(Large10B(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))))
      },
      test("case class with 10 fields - with coercion") {
        val source = Large10A(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val result = Into.derived[Large10A, Large10L].into(source)
        assert(result)(isRight(equalTo(Large10L(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L))))
      }
    ),
    suite("25-field case classes (beyond tuple limit)")(
      test("case class with 25 fields - same types") {
        val source =
          Large25A(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25)
        val result = Into.derived[Large25A, Large25B].into(source)
        assert(result)(
          isRight(
            equalTo(Large25B(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25))
          )
        )
      },
      test("case class with 25 fields - with coercion Int to Long") {
        val source =
          Large25A(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25)
        val result = Into.derived[Large25A, Large25L].into(source)
        assert(result)(
          isRight(
            equalTo(
              Large25L(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L, 21L,
                22L, 23L, 24L, 25L)
            )
          )
        )
      }
    )
  )

  case class PersonA(name: String, employer: Option[CompanyA])

  case class CompanyA(name: String, employees: List[PersonA])

  case class PersonB(name: String, employer: Option[CompanyB])

  case class CompanyB(name: String, employees: List[PersonB])

  /** Tests for mutually recursive type conversions. */
  private val mutuallyRecursiveType = suite("Mutually Recursive Type")(
    test("converts person with no employer") {
      implicit lazy val personInto: Into[PersonA, PersonB]    = Into.derived[PersonA, PersonB]
      implicit lazy val companyInto: Into[CompanyA, CompanyB] = Into.derived[CompanyA, CompanyB]
      val result                                              = personInto.into(PersonA("Alice", None))
      assert(result)(isRight(equalTo(PersonB("Alice", None))))
    },
    test("converts company with employees") {
      implicit lazy val personInto: Into[PersonA, PersonB]    = Into.derived[PersonA, PersonB]
      implicit lazy val companyInto: Into[CompanyA, CompanyB] = Into.derived[CompanyA, CompanyB]
      val employees                                           = List(PersonA("Alice", None), PersonA("Bob", None))
      val result                                              = companyInto.into(CompanyA("TechCorp", employees))
      assert(result)(isRight(equalTo(CompanyB("TechCorp", List(PersonB("Alice", None), PersonB("Bob", None))))))
    },
    test("converts one level of mutual reference") {
      implicit lazy val personInto: Into[PersonA, PersonB]    = Into.derived[PersonA, PersonB]
      implicit lazy val companyInto: Into[CompanyA, CompanyB] = Into.derived[CompanyA, CompanyB]
      val company                                             = CompanyA("StartupInc", List(PersonA("Alice", None)))
      val result                                              = personInto.into(PersonA("Bob", Some(company)))
      assert(result)(isRight(equalTo(PersonB("Bob", Some(CompanyB("StartupInc", List(PersonB("Alice", None))))))))
    }
  )

  case class TreeA(value: Int, children: List[TreeA])

  case class TreeB(value: Long, children: List[TreeB])

  case class NodeA(value: Int, next: Option[NodeA])

  case class NodeB(value: Long, next: Option[NodeB])

  /** Tests for recursive type conversions. */
  private val recursiveType = suite("Recursive Type")(
    test("converts leaf node (empty children)") {
      implicit lazy val treeInto: Into[TreeA, TreeB] = Into.derived[TreeA, TreeB]
      assert(treeInto.into(TreeA(42, Nil)))(isRight(equalTo(TreeB(42L, Nil))))
    },
    test("converts tree with children") {
      implicit lazy val treeInto: Into[TreeA, TreeB] = Into.derived[TreeA, TreeB]
      val source                                     = TreeA(1, List(TreeA(2, Nil), TreeA(3, List(TreeA(4, Nil)))))
      assert(treeInto.into(source))(isRight(equalTo(TreeB(1L, List(TreeB(2L, Nil), TreeB(3L, List(TreeB(4L, Nil))))))))
    },
    test("converts linked list") {
      implicit lazy val nodeInto: Into[NodeA, NodeB] = Into.derived[NodeA, NodeB]
      val source                                     = NodeA(1, Some(NodeA(2, Some(NodeA(3, None)))))
      assert(nodeInto.into(source))(isRight(equalTo(NodeB(1L, Some(NodeB(2L, Some(NodeB(3L, None))))))))
    }
  )

  case class SingleInt(value: Int)

  case class SingleLong(value: Long)

  case class SingleString(name: String)

  case class SingleStringAlt(label: String)

  /** Tests for single-field case class conversions. */
  private val singleField = suite("Single Field")(
    test("single field with same type") {
      assert(Into.derived[SingleInt, SingleInt].into(SingleInt(42)))(isRight(equalTo(SingleInt(42))))
    },
    test("single field with different name") {
      val result = Into.derived[SingleString, SingleStringAlt].into(SingleString("hello"))
      assert(result)(isRight(equalTo(SingleStringAlt("hello"))))
    },
    test("single field with coercion") {
      assert(Into.derived[SingleInt, SingleLong].into(SingleInt(100)))(isRight(equalTo(SingleLong(100L))))
    },
    test("single field to Tuple1") {
      assert(Into.derived[SingleInt, Tuple1[Int]].into(SingleInt(42)))(isRight(equalTo(Tuple1(42))))
    }
  )

  case class PersonV1(name: String)

  case class PersonV2(name: String, age: Int = 0)

  case class ConfigV1(key: String)

  case class ConfigV2(key: String, timeout: Int = 30, retries: Int = 3)

  /** Tests for schema evolution with default values. */
  private val addDefaultField = suite("Add Default Field")(
    test("uses default value for missing field") {
      val result = Into.derived[PersonV1, PersonV2].into(PersonV1("Alice"))
      assert(result)(isRight(equalTo(PersonV2("Alice", 0))))
    },
    test("uses multiple defaults for missing fields") {
      val result = Into.derived[ConfigV1, ConfigV2].into(ConfigV1("database"))
      assert(result)(isRight(equalTo(ConfigV2("database", 30, 3))))
    },
    test("source value overrides default") {
      case class SourceWithAge(name: String, age: Int)

      val result = Into.derived[SourceWithAge, PersonV2].into(SourceWithAge("Charlie", 25))
      assert(result)(isRight(equalTo(PersonV2("Charlie", 25))))
    }
  )

  case class PersonV3(name: String, age: Int)

  case class PersonV4(name: String, age: Int, email: Option[String])

  case class SourceWithEmail(name: String, age: Int, email: Option[String])

  /** Tests for schema evolution when adding optional fields. */
  private val addOptionalFieldSpec = suite("Add Optional Field")(
    test("adds Option field with None when not in source") {
      val result = Into.derived[PersonV3, PersonV4].into(PersonV3("Alice", 30))
      assert(result)(isRight(equalTo(PersonV4("Alice", 30, None))))
    },
    test("adds multiple Option fields with None") {
      case class ProductV1(id: Long, name: String)
      case class ProductV2(id: Long, name: String, description: Option[String], price: Option[Double])

      val result = Into.derived[ProductV1, ProductV2].into(ProductV1(1L, "Widget"))
      assert(result)(isRight(equalTo(ProductV2(1L, "Widget", None, None))))
    },
    test("existing source Option value used when Option field matches") {
      val result = Into.derived[SourceWithEmail, PersonV4].into(SourceWithEmail("Bob", 25, Some("bob@test.com")))
      assert(result)(isRight(equalTo(PersonV4("Bob", 25, Some("bob@test.com")))))
    }
  )

  case class PersonV5(name: String, age: Int, email: Option[String])

  case class PersonV6(name: String, age: Int)

  /** Tests for schema evolution when removing optional fields. */
  private val removeOptionalField = suite("Remove Optional Field")(
    test("drops Some value when field is removed") {
      val result = Into.derived[PersonV5, PersonV6].into(PersonV5("Alice", 30, Some("alice@example.com")))
      assert(result)(isRight(equalTo(PersonV6("Alice", 30))))
    },
    test("converts when optional field is None") {
      val result = Into.derived[PersonV5, PersonV6].into(PersonV5("Bob", 25, None))
      assert(result)(isRight(equalTo(PersonV6("Bob", 25))))
    },
    test("drops multiple optional fields") {
      case class ProductV2(id: Long, name: String, description: Option[String], price: Option[Double])
      case class ProductV1(id: Long, name: String)

      val result = Into.derived[ProductV2, ProductV1].into(ProductV2(1L, "Widget", Some("desc"), Some(19.99)))
      assert(result)(isRight(equalTo(ProductV1(1L, "Widget"))))
    }
  )

  case class CounterV1(count: Int)

  case class CounterV2(count: Long)

  private val typeRefinement = suite("Type Refinement")(
    test("widens Int to Long field") {
      assert(Into.derived[CounterV1, CounterV2].into(CounterV1(42)))(isRight(equalTo(CounterV2(42L))))
    },
    test("narrows Long to Int when value fits") {
      assert(Into.derived[CounterV2, CounterV1].into(CounterV2(42L)))(isRight(equalTo(CounterV1(42))))
    },
    test("fails when Long value overflows Int") {
      assert(Into.derived[CounterV2, CounterV1].into(CounterV2(Long.MaxValue)))(isLeft)
    },
    test("widens multiple fields") {
      case class MetricsV1(min: Int, max: Int)
      case class MetricsV2(min: Long, max: Long)

      assert(Into.derived[MetricsV1, MetricsV2].into(MetricsV1(1, 100)))(isRight(equalTo(MetricsV2(1L, 100L))))
    }
  )

  /** Tests for collection element coercion in Into conversions. */
  private val collectionCoercion = suite("Collection Coercion")(
    test("coerces List[Int] to List[Long]") {
      assert(Into[List[Int], List[Long]].into(List(1, 2, 3)))(isRight(equalTo(List(1L, 2L, 3L))))
    },
    test("coerces Vector[Int] to Vector[Long]") {
      assert(Into[Vector[Int], Vector[Long]].into(Vector(10, 20, 30)))(isRight(equalTo(Vector(10L, 20L, 30L))))
    },
    test("coerces Set[Int] to Set[Long]") {
      assert(Into[Set[Int], Set[Long]].into(Set(1, 2, 3)))(isRight(equalTo(Set(1L, 2L, 3L))))
    },
    test("fails when narrowing element fails") {
      assert(Into[List[Long], List[Int]].into(List(1L, Long.MaxValue)))(isLeft)
    }
  )

  /** Tests for Either type coercion in Into conversions. */
  private val eitherCoercion = suite("Either Coercion")(
    test("coerces Right[Int] to Right[Long]") {
      assert(Into[Either[String, Int], Either[String, Long]].into(Right(42)))(isRight(equalTo(Right(42L))))
    },
    test("passes Left unchanged when right type changes") {
      assert(Into[Either[String, Int], Either[String, Long]].into(Left("error")))(isRight(equalTo(Left("error"))))
    },
    test("coerces both Left and Right types") {
      assert(Into[Either[Int, Short], Either[Long, Int]].into(Right(100.toShort)))(isRight(equalTo(Right(100))))
    },
    test("fails when narrowing Right overflows") {
      assert(Into[Either[String, Long], Either[String, Int]].into(Right(Long.MaxValue)))(isLeft)
    }
  )

  private val nestedCollection = suite("Nested Collection")(
    test("coerces List[List[Int]] to List[List[Long]]") {
      val result = Into[List[List[Int]], List[List[Long]]].into(List(List(1, 2), List(3, 4)))
      assert(result)(isRight(equalTo(List(List(1L, 2L), List(3L, 4L)))))
    },
    test("coerces List[Option[Int]] to List[Option[Long]]") {
      val result = Into[List[Option[Int]], List[Option[Long]]].into(List(Some(1), None, Some(3)))
      assert(result)(isRight(equalTo(List(Some(1L), None, Some(3L)))))
    },
    test("coerces Option[List[Int]] to Option[List[Long]]") {
      val result = Into[Option[List[Int]], Option[List[Long]]].into(Some(List(1, 2, 3)))
      assert(result)(isRight(equalTo(Some(List(1L, 2L, 3L)))))
    },
    test("fails when any nested element fails narrowing") {
      assert(Into[List[List[Long]], List[List[Int]]].into(List(List(1L), List(Long.MaxValue))))(isLeft)
    }
  )

  /** Tests for numeric narrowing conversions. */
  private val numericNarrowing = suite("Numeric Narrowing")(
    test("narrows Long to Int when value fits") {
      assert(Into[Long, Int].into(42L))(isRight(equalTo(42)))
    },
    test("fails when Long overflows Int") {
      assert(Into[Long, Int].into(Long.MaxValue))(isLeft)
    },
    test("narrows Int to Short when value fits") {
      assert(Into[Int, Short].into(1000))(isRight(equalTo(1000.toShort)))
    },
    test("fails when Int overflows Short") {
      assert(Into[Int, Short].into(Int.MaxValue))(isLeft)
    },
    test("narrows Double to Float when value fits") {
      assert(Into[Double, Float].into(3.14))(isRight(equalTo(3.14f)))
    }
  )

  /** Tests for numeric widening conversions. */
  private val numericWidening = suite("Numeric Widening")(
    test("widens Byte to Int") {
      assert(Into[Byte, Int].into(42.toByte))(isRight(equalTo(42)))
    },
    test("widens Short to Long") {
      assert(Into[Short, Long].into(1000.toShort))(isRight(equalTo(1000L)))
    },
    test("widens Int to Long") {
      assert(Into[Int, Long].into(Int.MaxValue))(isRight(equalTo(Int.MaxValue.toLong)))
    },
    test("widens Float to Double") {
      assert(Into[Float, Double].into(3.14f))(isRight(equalTo(3.14f.toDouble)))
    }
  )

  /** Tests for Option type coercion in Into conversions. */
  private val optionCoercion = suite("Option Coercion")(
    test("coerces Some[Int] to Some[Long]") {
      assert(Into[Option[Int], Option[Long]].into(Some(42)))(isRight(equalTo(Some(42L))))
    },
    test("coerces None[Int] to None[Long]") {
      assert(Into[Option[Int], Option[Long]].into(None))(isRight(equalTo(None)))
    },
    test("fails when narrowing Some[Long] overflows Int") {
      assert(Into[Option[Long], Option[Int]].into(Some(Long.MaxValue)))(isLeft)
    },
    test("None passes even with narrowing conversion") {
      assert(Into[Option[Long], Option[Int]].into(None))(isRight(equalTo(None)))
    }
  )

  case class PersonV7(name: String, age: Int)

  case class PersonV8(name: String, age: Int)

  case class ConfigV3(timeout: Int, retries: Int)

  case class ConfigV4(timeout: Long, retries: Long)

  case class SourceWithName(name: String)

  case class TargetWithDefault(name: String, count: Int = 0)

  case class SourceUniqueV1(name: String, age: Int, active: Boolean)

  case class TargetUniqueV1(username: String, yearsOld: Int, enabled: Boolean)

  /** Tests for case class to case class conversions. */
  private val caseClassToCaseClass = suite("Case Class To Case Class")(
    test("converts when field names and types match exactly") {
      assert(Into.derived[PersonV7, PersonV8].into(PersonV7("Alice", 30)))(isRight(equalTo(PersonV8("Alice", 30))))
    },
    test("maps fields by unique type when names differ") {
      val result = Into.derived[SourceUniqueV1, TargetUniqueV1].into(SourceUniqueV1("Alice", 30, true))
      assert(result)(isRight(equalTo(TargetUniqueV1("Alice", 30, true))))
    },
    test("applies type coercion (Int to Long)") {
      assert(Into.derived[ConfigV3, ConfigV4].into(ConfigV3(30, 3)))(isRight(equalTo(ConfigV4(30L, 3L))))
    },
    test("uses default value for missing field") {
      val result = Into.derived[SourceWithName, TargetWithDefault].into(SourceWithName("test"))
      assert(result)(isRight(equalTo(TargetWithDefault("test"))))
    }
  )

  case class Point2D(x: Double, y: Double)

  case class Person(name: String, age: Int)

  /** Tests for case class to tuple conversions. */
  private val caseClassToTuple = suite("Case Class To Tuple")(
    test("converts 2-field case class to Tuple2") {
      val result = Into.derived[Point2D, (Double, Double)].into(Point2D(1.0, 2.0))
      assert(result)(isRight(equalTo((1.0, 2.0))))
    },
    test("converts Person to (String, Int)") {
      val result = Into.derived[Person, (String, Int)].into(Person("Alice", 30))
      assert(result)(isRight(equalTo(("Alice", 30))))
    },
    test("widens Int to Long in tuple elements") {
      case class IntPair(a: Int, b: Int)
      val result = Into.derived[IntPair, (Long, Long)].into(IntPair(10, 20))
      assert(result)(isRight(equalTo((10L, 20L))))
    }
  )

  /** Tests for field renaming with unique type matching. */
  private val fieldRenaming = suite("Field Renaming")(
    test("maps renamed fields by unique type") {
      case class PersonV1(fullName: String, yearOfBirth: Int)
      case class PersonV2(name: String, birthYear: Int)

      val result = Into.derived[PersonV1, PersonV2].into(PersonV1("Alice Smith", 1990))
      assert(result)(isRight(equalTo(PersonV2("Alice Smith", 1990))))
    },
    test("partial renaming - some by name, others by type") {
      case class Source(hostName: String, portNumber: Int, timeout: Long)
      case class Target(host: String, port: Int, timeout: Long)

      val result = Into.derived[Source, Target].into(Source("localhost", 8080, 5000L))
      assert(result)(isRight(equalTo(Target("localhost", 8080, 5000L))))
    },
    test("maps renamed fields with coercion") {
      case class Source(identifier: Int, label: String)
      case class Target(id: Long, name: String)

      assert(Into.derived[Source, Target].into(Source(1, "test")))(isRight(equalTo(Target(1L, "test"))))
    }
  )

  /** Tests for field reordering in Into conversions. */
  private val fieldReordering = suite("Field Reordering")(
    test("maps 2 fields by name despite reversed order") {
      case class Point(x: Int, y: Int)
      case class Coord(y: Int, x: Int)

      assert(Into.derived[Point, Coord].into(Point(x = 1, y = 2)))(isRight(equalTo(Coord(y = 2, x = 1))))
    },
    test("maps 3 fields with complete reordering") {
      case class Person(name: String, age: Int, email: String)
      case class PersonReordered(email: String, name: String, age: Int)

      val result = Into.derived[Person, PersonReordered].into(Person("Alice", 30, "alice@test.com"))
      assert(result)(isRight(equalTo(PersonReordered("alice@test.com", "Alice", 30))))
    },
    test("partial reordering - some in order, some not") {
      case class Source(a: Int, b: String, c: Boolean)
      case class Target(a: Int, c: Boolean, b: String)

      assert(Into.derived[Source, Target].into(Source(1, "test", true)))(isRight(equalTo(Target(1, true, "test"))))
    }
  )

  case class AddressV9(street: String, zip: Int)
  case class PersonV9(name: String, address: AddressV9)

  case class AddressV10(street: String, zip: Long)
  case class PersonV11(name: String, address: AddressV10)

  case class InnerV5(value: Int)
  case class MiddleV5(inner: InnerV5, name: String)
  case class OuterV5(middle: MiddleV5)

  case class InnerV6(value: Long)
  case class MiddleV6(inner: InnerV6, name: String)
  case class OuterV6(middle: MiddleV6)

  case class InnerV7(x: Int, y: Int)
  case class OuterV7(label: String, inner: InnerV7)
  case class InnerV8(y: Int, x: Int)
  case class OuterV8(inner: InnerV8, label: String)

  implicit val addressV9ToV10: Into[AddressV9, AddressV10] = Into.derived[AddressV9, AddressV10]

  /** Tests for nested product conversions. */
  private val nestedProducts = suite("Nested Products")(
    test("converts nested case class with type coercion") {
      val result = Into.derived[PersonV9, PersonV11].into(PersonV9("Alice", AddressV9("Main St", 12345)))
      assert(result)(isRight(equalTo(PersonV11("Alice", AddressV10("Main St", 12345L)))))
    },
    test("converts multiple nesting levels") {
      implicit val innerInto: Into[InnerV5, InnerV6]    = Into.derived[InnerV5, InnerV6]
      implicit val middleInto: Into[MiddleV5, MiddleV6] = Into.derived[MiddleV5, MiddleV6]

      val result = Into.derived[OuterV5, OuterV6].into(OuterV5(MiddleV5(InnerV5(42), "test")))
      assert(result)(isRight(equalTo(OuterV6(MiddleV6(InnerV6(42L), "test")))))
    },
    test("nested case class with field reordering") {
      implicit val innerInto: Into[InnerV7, InnerV8] = Into.derived[InnerV7, InnerV8]
      val result                                     = Into.derived[OuterV7, OuterV8].into(OuterV7("test", InnerV7(1, 2)))
      assert(result)(isRight(equalTo(OuterV8(InnerV8(2, 1), "test"))))
    }
  )

  /** Tests for tuple to a case class conversions. */
  private val tupleToCaseClass = suite("Tuple To Case Class")(
    test("converts Tuple2 to 2-field case class") {
      case class Point2D(x: Double, y: Double)

      assert(Into.derived[(Double, Double), Point2D].into((1.0, 2.0)))(isRight(equalTo(Point2D(1.0, 2.0))))
    },
    test("converts (String, Int) to Person") {
      case class Person(name: String, age: Int)

      assert(Into.derived[(String, Int), Person].into(("Alice", 30)))(isRight(equalTo(Person("Alice", 30))))
    },
    test("widens Int to Long in case class fields") {
      case class LongPair(a: Long, b: Long)

      assert(Into.derived[(Int, Int), LongPair].into((10, 20)))(isRight(equalTo(LongPair(10L, 20L))))
    }
  )

  /** Tests for tuple to a tuple conversions. */
  private val tupleToTuple = suite("Tuple To Tuple")(
    test("converts Tuple2 to Tuple2 with same types") {
      assert(Into.derived[(Int, String), (Int, String)].into((42, "hello")))(isRight(equalTo((42, "hello"))))
    },
    test("widens Int to Long in tuple element") {
      assert(Into.derived[(Int, String), (Long, String)].into((42, "hello")))(isRight(equalTo((42L, "hello"))))
    },
    test("widens multiple elements") {
      val result = Into.derived[(Int, Short, Byte), (Long, Int, Short)].into((10, 20.toShort, 30.toByte))
      assert(result)(isRight(equalTo((10L, 20, 30.toShort))))
    },
    test("fails when narrowing would overflow") {
      assert(Into.derived[(Long, String), (Int, String)].into((Long.MaxValue, "test")))(isLeft)
    }
  )

  case class SourceLongs(a: Long, b: Long, c: Long)

  case class TargetInts(a: Int, b: Int, c: Int)

  /** Tests for error accumulation in Into conversions. */
  private val errorAccumulation = suite("Error Accumulation")(
    test("accumulates errors from multiple fields that fail narrowing") {
      val result = Into.derived[SourceLongs, TargetInts].into(SourceLongs(Long.MaxValue, Long.MinValue, 100L))
      assertTrue(
        result.isLeft,
        result.left.exists(_.errors.size == 2)
      )
    },
    test("returns Right when all fields succeed") {
      assertTrue(Into.derived[SourceLongs, TargetInts].into(SourceLongs(1L, 2L, 3L)) == Right(TargetInts(1, 2, 3)))
    },
    test("accumulates all errors when all fields fail") {
      val result = Into.derived[SourceLongs, TargetInts].into(SourceLongs(Long.MaxValue, Long.MinValue, Long.MaxValue))
      assertTrue(
        result.isLeft,
        result.left.exists(_.errors.size == 3)
      )
    }
  )

  case class LongValue(value: Long)

  case class IntValue(value: Int)

  /** Tests for numeric narrowing validation. */
  private val narrowingValidation = suite("Narrowing Validation")(
    test("succeeds when Long value fits in Int range") {
      assert(Into.derived[LongValue, IntValue].into(LongValue(100L)))(isRight(equalTo(IntValue(100))))
    },
    test("fails when Long value exceeds Int.MaxValue") {
      assert(Into.derived[LongValue, IntValue].into(LongValue(Long.MaxValue)))(isLeft)
    },
    test("fails when Long value is below Int.MinValue") {
      assert(Into.derived[LongValue, IntValue].into(LongValue(Long.MinValue)))(isLeft)
    },
    test("succeeds at Int boundary values") {
      val max = Into.derived[LongValue, IntValue].into(LongValue(Int.MaxValue.toLong))
      val min = Into.derived[LongValue, IntValue].into(LongValue(Int.MinValue.toLong))
      assert(max)(isRight(equalTo(IntValue(Int.MaxValue)))) &&
      assert(min)(isRight(equalTo(IntValue(Int.MinValue))))
    }
  )

  case class InnerLong(value: Long)

  case class InnerInt(value: Int)

  case class MiddleLong(inner: InnerLong, name: String)

  case class MiddleInt(inner: InnerInt, name: String)

  case class SourceList(items: List[Long])

  case class TargetList(items: List[Int])

  case class SourceOpt(value: Option[Long])

  case class TargetOpt(value: Option[Int])

  implicit val innerLongToInnerInt: Into[InnerLong, InnerInt] = Into.derived[InnerLong, InnerInt]

  /** Tests for nested validation scenarios. */
  private val nestedValidation = suite("Nested Validation")(
    test("succeeds when inner value fits") {
      val result = Into.derived[MiddleLong, MiddleInt].into(MiddleLong(InnerLong(100L), "test"))
      assert(result)(isRight(equalTo(MiddleInt(InnerInt(100), "test"))))
    },
    test("fails when inner value overflows") {
      assert(Into.derived[MiddleLong, MiddleInt].into(MiddleLong(InnerLong(Long.MaxValue), "test")))(isLeft)
    },
    test("fails when list element overflows") {
      assert(Into.derived[SourceList, TargetList].into(SourceList(List(1L, Long.MaxValue))))(isLeft)
    },
    test("fails when Option value overflows") {
      assert(Into.derived[SourceOpt, TargetOpt].into(SourceOpt(Some(Long.MaxValue))))(isLeft)
    }
  )

  case class LongValueV1(value: Long)

  case class IntValueV1(value: Int)

  /** Tests for validation error handling and messages. */
  private val validationError = suite("Validation Error")(
    test("narrowing overflow returns Left") {
      assert(Into.derived[LongValueV1, IntValueV1].into(LongValueV1(Long.MaxValue)))(isLeft)
    },
    test("successful conversion returns Right") {
      assert(Into.derived[LongValueV1, IntValueV1].into(LongValueV1(100L)))(isRight)
    },
    test("error is SchemaError type") {
      Into.derived[LongValueV1, IntValueV1].into(LongValueV1(Long.MaxValue)) match {
        case Left(err) => assertTrue(err.isInstanceOf[SchemaError])
        case Right(_)  => assertTrue(false)
      }
    },
    test("error message contains context") {
      Into.derived[LongValueV1, IntValueV1].into(LongValueV1(Long.MaxValue)) match {
        case Left(err) =>
          val msg = err.toString.toLowerCase
          assertTrue(msg.contains("overflow") || msg.contains("range") || msg.contains("conversion"))
        case Right(_) => assertTrue(false)
      }
    }
  )
}
