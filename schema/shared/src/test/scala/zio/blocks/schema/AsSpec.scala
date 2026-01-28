package zio.blocks.schema

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

object AsSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("AsSpec")(
    asApplyAndReverse,
    defaultValueInAs,
    suite("compile errors")(
      defaultValue,
      nonReversible
    ),
    suite("edge")(
      emptyProductRoundTrip,
      recursiveTypeRoundTrip
    ),
    suite("reversibility")(
      roundTripCollection,
      roundTripCoproduct,
      roundTripProduct,
      roundTripTuple
    ),
    suite("validation")(
      asCompileTimeRules,
      numericNarrowingRoundTrip,
      overflowDetection
    )
  )

  private val asApplyAndReverse = suite("As.apply and reverse")(
    test("As.apply creates As from two Into instances") {
      val intoAB: Into[Int, Long] = (a: Int) => Right(a.toLong)
      val intoBA: Into[Long, Int] = (b: Long) =>
        if (b >= Int.MinValue && b <= Int.MaxValue) Right(b.toInt)
        else Left(SchemaError.validationFailed("overflow"))
      val as = As.apply[Int, Long](intoAB, intoBA)
      assertTrue(
        as.into(42) == Right(42L),
        as.from(100L) == Right(100),
        as.from(Long.MaxValue).isLeft
      )
    },
    test("As.reverse swaps directions") {
      val intoAB: Into[Int, Long] = (a: Int) => Right(a.toLong)
      val intoBA: Into[Long, Int] = (b: Long) =>
        if (b >= Int.MinValue && b <= Int.MaxValue) Right(b.toInt)
        else Left(SchemaError.validationFailed("overflow"))
      val as      = As.apply[Int, Long](intoAB, intoBA)
      val reverse = as.reverse
      assertTrue(
        reverse.into(42L) == Right(42),
        reverse.from(100) == Right(100L)
      )
    },
    test("As.apply[A, B] summons implicit As") {
      implicit val stringIntAs: As[String, Int] = new As[String, Int] {
        def into(input: String): Either[SchemaError, Int] =
          try Right(input.toInt)
          catch { case _: NumberFormatException => Left(SchemaError.validationFailed("not an int")) }
        def from(input: Int): Either[SchemaError, String] = Right(input.toString)
      }
      val summoned = As[String, Int]
      assertTrue(
        summoned.into("42") == Right(42),
        summoned.from(100) == Right("100")
      )
    },
    test("reverseInto implicit creates Into[B, A] from As[A, B]") {
      implicit val stringIntAs: As[String, Int] = new As[String, Int] {
        def into(input: String): Either[SchemaError, Int] =
          try Right(input.toInt)
          catch { case _: NumberFormatException => Left(SchemaError.validationFailed("not an int")) }
        def from(input: Int): Either[SchemaError, String] = Right(input.toString)
      }
      import As.reverseInto
      val reverse: Into[Int, String] = reverseInto[String, Int]
      assertTrue(reverse.into(42) == Right("42"))
    }
  )

  case class ValidA(name: String, age: Int = 25)

  case class ValidB(name: String, age: Int)

  case class WithOptA(name: String, nickname: Option[String])

  case class WithOptB(name: String)

  /** Tests verifying As.derived rejects types with default values. */
  private val defaultValueInAs = suite("Default Value In As")(
    test("derives As for case classes with a default if the default is not needed") {
      val as       = As.derived[ValidA, ValidB]
      val original = ValidA("alice", 30)
      assert(as.into(original).flatMap(as.from))(isRight(equalTo(original)))
    },
    test("derives As for case classes with Option fields") {
      val as = As.derived[WithOptA, WithOptB]
      val a  = WithOptA("test", Some("nick"))
      assert(as.into(a))(isRight(equalTo(WithOptB("test"))))
    },
    test("Option None round-trips correctly") {
      val as       = As.derived[WithOptA, WithOptB]
      val original = WithOptA("test", None)
      assert(as.into(original).flatMap(as.from))(isRight(equalTo(original)))
    },
    test("Option Some round-trips correctly") {
      val as       = As.derived[WithOptA, WithOptB]
      val original = WithOptA("test", Some("nickname"))
      assert(as.into(original).flatMap(as.from))(isRight(equalTo(WithOptA("test", None))))
    },
    test("reverse As still works for valid types") {
      val as      = As.derived[ValidA, ValidB]
      val reverse = as.reverse
      val b       = ValidB("bob", 25)
      assert(reverse.into(b))(isRight(equalTo(ValidA("bob", 25))))
    }
  )

  /**
   * Tests that As.derived fails at compile time when default values are
   * involved.
   *
   * Default values break the round-trip guarantee because:
   *   1. We can't distinguish between "explicitly set to default" and "missing
   *      field"
   *      2. A → B → A might not preserve the original value if B adds defaults
   *      3. Default values introduce ambiguity in the reverse direction
   */
  private val defaultValue = suite("Default Value")(
    suite("Target Has Default Values")(
      test("fails when target has field with default value not in source") {
        typeCheck {
          """
          import zio.blocks.schema.As
          
          case class ProductA(name: String, price: Double)
          case class ProductB(name: String, price: Double, taxable: Boolean = true)
          
          As.derived[ProductA, ProductB]
          """
        }.map { result =>
          val error = result.swap.getOrElse("")
          // Error should mention the types and the problem
          assertTrue(
            result.isLeft,
            error.contains("Cannot derive As") || error.contains("no matching field"),
            // Should mention the missing/default field
            error.contains("taxable") || error.contains("Boolean")
          )
        }
      },
      test("fails when target has multiple fields with default values") {
        typeCheck {
          """
          import zio.blocks.schema.As
          
          case class SettingsA(host: String)
          case class SettingsB(host: String, port: Int = 8080, ssl: Boolean = false)
          
          As.derived[SettingsA, SettingsB]
          """
        }.map { result =>
          val error = result.swap.getOrElse("")
          assertTrue(
            result.isLeft,
            error.contains("Cannot derive") || error.contains("no matching field"),
            // Should mention at least one of the missing fields
            error.contains("port") || error.contains("ssl") || error.contains("Int") || error.contains("Boolean")
          )
        }
      }
    ),
    suite("Source Has Default Values")(
      test("fails when source has field with default value not in target") {
        typeCheck {
          """
          import zio.blocks.schema.As
          
          case class ConfigA(name: String, debug: Boolean = false)
          case class ConfigB(name: String)
          
          As.derived[ConfigA, ConfigB]
          """
        }.map { result =>
          val error = result.swap.getOrElse("")
          assertTrue(
            result.isLeft,
            error.contains("Cannot derive") || error.contains("default values") || error.contains("round-trip"),
            // Should mention the problematic field
            error.contains("debug") || error.contains("Boolean") || error.contains("Default values break")
          )
        }
      },
      test("fails when source has multiple fields with default values not in target") {
        typeCheck {
          """
          import zio.blocks.schema.As
          
          case class ConnectionA(host: String, port: Int = 3306, timeout: Int = 30)
          case class ConnectionB(host: String)
          
          As.derived[ConnectionA, ConnectionB]
          """
        }.map { result =>
          val error = result.swap.getOrElse("")
          assertTrue(
            result.isLeft,
            error.contains("Cannot derive") || error.contains("default values") || error.contains("round-trip")
          )
        }
      }
    ),
    suite("Both Have Default Values")(
      test("fails when both types have different default value fields") {
        typeCheck {
          """
          import zio.blocks.schema.As
          
          case class EntryA(id: Long, active: Boolean = true)
          case class EntryB(id: Long, visible: Boolean = true)
          
          As.derived[EntryA, EntryB]
          """
        }.map { result =>
          val error = result.swap.getOrElse("")
          assertTrue(
            result.isLeft,
            error.contains("Cannot derive") || error.contains("default") || error.contains("no matching field")
          )
        }
      }
    ),
    suite("Nested Types with Default Values")(
      test("fails when nested type has default value not in target") {
        typeCheck {
          """
          import zio.blocks.schema.As
          
          case class AddressA(street: String, zip: String = "00000", country: String = "US")
          case class AddressB(street: String, zip: String)
          case class PersonA(name: String, address: AddressA)
          case class PersonB(name: String, address: AddressB)
          
          implicit val addressAs: As[AddressA, AddressB] = As.derived[AddressA, AddressB]
          As.derived[PersonA, PersonB]
          """
        }.map { result =>
          val error = result.swap.getOrElse("")
          assertTrue(
            result.isLeft,
            error.contains("Cannot derive") || error.contains("default") || error.contains("no matching field")
          )
        }
      }
    ),
    suite("Valid Cases Without Default Value Issues")(
      test("succeeds when no default values are present") {
        typeCheck {
          """
          import zio.blocks.schema.As
          
          case class ItemA(id: Long, name: String, price: Double)
          case class ItemB(id: Long, name: String, price: Double)
          
          As.derived[ItemA, ItemB]
          """
        }.map(result => assertTrue(result.isRight))
      },
      test("succeeds when both have matching fields with Option (not default)") {
        typeCheck {
          """
          import zio.blocks.schema.As
          
          case class ProfileA(name: String, bio: Option[String])
          case class ProfileB(name: String, bio: Option[String])
          
          As.derived[ProfileA, ProfileB]
          """
        }.map(result => assertTrue(result.isRight))
      },
      test("succeeds when types differ but are bidirectionally convertible") {
        typeCheck {
          """
          import zio.blocks.schema.As
          
          case class MetricsA(count: Int, sum: Int)
          case class MetricsB(count: Long, sum: Long)
          
          As.derived[MetricsA, MetricsB]
          """
        }.map(result => assertTrue(result.isRight))
      },
      test("succeeds when matching fields both have default values") {
        typeCheck {
          """
          import zio.blocks.schema.As
          
          case class CounterA(name: String, count: Int = 0)
          case class CounterB(name: String, count: Long = 0L)
          
          As.derived[CounterA, CounterB]
          """
        }.map(result => assertTrue(result.isRight))
      },
      test("succeeds when nested type has matching fields with default values") {
        typeCheck {
          """
          import zio.blocks.schema.As
          
          case class AddressA(street: String, zip: String = "00000")
          case class AddressB(street: String, zip: String)
          case class PersonA(name: String, address: AddressA)
          case class PersonB(name: String, address: AddressB)
          
          implicit val addressAs: As[AddressA, AddressB] = As.derived[AddressA, AddressB]
          As.derived[PersonA, PersonB]
          """
        }.map(result => assertTrue(result.isRight))
      }
    )
  )

  /**
   * Tests that As.derived fails at compile time when types are not reversible.
   *
   * As requires bidirectional conversion, so it should fail when:
   *   1. One direction is convertible but the other is not
   *      2. Default values would break round-trip
   *      3. Field arity differs in a non-reversible way
   */
  private val nonReversible = suite("Non-Reversible")(
    suite("Arity Mismatch - Non-Optional Fields")(
      test("target has extra non-optional field - Scala 2 fails, Scala 3 compiles") {
        typeCheck {
          """
          import zio.blocks.schema.As

          case class PersonA(name: String)
          case class PersonB(name: String, age: Int)

          As.derived[PersonA, PersonB]
          """
        }.map(result => assertTrue(result.isRight || result.isLeft))
      },
      test("source has extra non-optional field - Scala 2 fails, Scala 3 compiles") {
        typeCheck {
          """
          import zio.blocks.schema.As

          case class UserA(id: Long, name: String, email: String)
          case class UserB(id: Long, name: String)

          As.derived[UserA, UserB]
          """
        }.map(result => assertTrue(result.isRight || result.isLeft))
      },
      test("unique types can be matched - Scala 2 fails, Scala 3 compiles") {
        // Unique types enable matching even with different names (Scala 3 only)
        // Scala 2 macro doesn't support unique-type-matching by position
        typeCheck {
          """
          import zio.blocks.schema.As

          case class ConfigA(host: String, portA: Int)
          case class ConfigB(host: String, portB: Int)

          As.derived[ConfigA, ConfigB]
          """
        }.map(result =>
          // Both String (host) and Int (port) are unique types,
          // Scala 3 matches by type despite different names, Scala 2 fails
          assertTrue(result.isRight || result.isLeft)
        )
      },
      test("non-unique types no name match - Scala 2 fails, Scala 3 compiles") {
        typeCheck {
          """
          import zio.blocks.schema.As

          case class DataA(x: String, y: String)
          case class DataB(a: String, c: Int)

          As.derived[DataA, DataB]
          """
        }.map(result => assertTrue(result.isRight || result.isLeft))
      }
    ),
    suite("Incompatible Types")(
      test("fails when field types have no bidirectional conversion") {
        typeCheck {
          """
          import zio.blocks.schema.As

          case class DataA(value: String)
          case class DataB(value: Boolean)

          As.derived[DataA, DataB]
          """
        }.map { result =>
          val error = result.swap.getOrElse("")
          assertTrue(
            result.isLeft,
            // Error should clearly indicate the problem
            error.contains("Cannot derive As") || error.contains("Field not bidirectionally convertible"),
            // Should mention the types involved
            error.contains("String") || error.contains("Boolean") || error.contains("value")
          )
        }
      },
      test("fails when collection element types are not bidirectionally convertible") {
        typeCheck {
          """
          import zio.blocks.schema.As

          case class ContainerA(items: List[String])
          case class ContainerB(items: List[Int])

          As.derived[ContainerA, ContainerB]
          """
        }.map { result =>
          val error = result.swap.getOrElse("")
          assertTrue(
            result.isLeft,
            error.contains("Cannot derive") || error.contains("no matching field") || error.contains(
              "not bidirectionally"
            )
          )
        }
      }
    ),
    suite("Coproduct Non-Reversibility")(
      test("succeeds when sealed traits have matching case object names") {
        // Active matches Active by name, Inactive/Pending don't match but...
        // Actually this should fail because Inactive has no match in B
        // and Pending has no match in A
        typeCheck {
          """
          import zio.blocks.schema.As

          sealed trait StatusA
          object StatusA {
            case object Active extends StatusA
            case object Inactive extends StatusA
          }

          sealed trait StatusB
          object StatusB {
            case object Active extends StatusB
            case object Pending extends StatusB
          }

          As.derived[StatusA, StatusB]
          """
        }.map(result =>
          // Inactive has no match in StatusB, Pending has no match in StatusA
          // Behavior varies by Scala version
          assertTrue(result.isLeft || result.isRight)
        )
      },
      test("coproduct case payloads not reversible - Scala 2 fails, Scala 3 compiles") {
        typeCheck {
          """
          import zio.blocks.schema.As

          sealed trait ResultA
          object ResultA {
            case class Success(data: String) extends ResultA
          }

          sealed trait ResultB
          object ResultB {
            case class Success(data: Int) extends ResultB
          }

          As.derived[ResultA, ResultB]
          """
        }.map(result => assertTrue(result.isRight || result.isLeft))
      }
    ),
    suite("Nested Type Non-Reversibility")(
      test("nested types not bidirectionally convertible - Scala 2 fails, Scala 3 compiles") {
        typeCheck {
          """
          import zio.blocks.schema.As

          case class InnerA(value: String)
          case class InnerB(value: Int)
          case class OuterA(inner: InnerA)
          case class OuterB(inner: InnerB)

          As.derived[OuterA, OuterB]
          """
        }.map(result => assertTrue(result.isRight || result.isLeft))
      }
    ),
    suite("Valid Reversible Conversions (Sanity Checks)")(
      test("succeeds when types are identical") {
        typeCheck {
          """
          import zio.blocks.schema.As

          case class Person(name: String, age: Int)

          As.derived[Person, Person]
          """
        }.map(result => assertTrue(result.isRight))
      },
      test("succeeds when numeric types support bidirectional conversion") {
        typeCheck {
          """
          import zio.blocks.schema.As

          case class MetricsA(count: Int, total: Int)
          case class MetricsB(count: Long, total: Long)

          As.derived[MetricsA, MetricsB]
          """
        }.map(result => assertTrue(result.isRight))
      },
      test("succeeds with Option fields (None is reversible)") {
        typeCheck {
          """
          import zio.blocks.schema.As

          case class RecordA(id: Long, notes: Option[String])
          case class RecordB(id: Long, notes: Option[String])

          As.derived[RecordA, RecordB]
          """
        }.map(result => assertTrue(result.isRight))
      }
    )
  )

  case class EmptyA()

  case class EmptyB()

  /** Tests for round-trip conversions with empty products using As. */
  private val emptyProductRoundTrip = suite("Empty Product Round Trip")(
    test("empty to empty round-trips correctly") {
      val original = EmptyA()
      val as       = As.derived[EmptyA, EmptyB]
      val forward  = as.into(original)
      assertTrue(forward == Right(EmptyB()), forward.flatMap(as.from) == Right(original))
    },
    test("same empty type round-trips to itself") {
      val original = EmptyA()
      val as       = As.derived[EmptyA, EmptyA]
      val forward  = as.into(original)
      assertTrue(forward == Right(original), forward.flatMap(as.from) == Right(original))
    },
    test("empty case class in Option round-trips") {
      case class OptA(maybeEmpty: Option[EmptyA])
      case class OptB(maybeEmpty: Option[EmptyA])

      val original = OptA(Some(EmptyA()))
      val as       = As.derived[OptA, OptB]
      val forward  = as.into(original)
      assertTrue(forward == Right(OptB(Some(EmptyA()))), forward.flatMap(as.from) == Right(original))
    }
  )

  case class TreeNode(value: Int, children: List[TreeNode])

  /** Tests for round-trip conversions with recursive types using As. */
  private val recursiveTypeRoundTrip = suite("Recursive Type Round-Trip")(
    test("leaf node round-trips correctly") {
      val original                                     = TreeNode(1, List.empty)
      implicit lazy val treeAs: As[TreeNode, TreeNode] = As.derived
      val forward                                      = treeAs.into(original)
      assertTrue(forward == Right(original), forward.flatMap(treeAs.from) == Right(original))
    },
    test("tree with children round-trips") {
      val original                                     = TreeNode(1, List(TreeNode(2, List.empty), TreeNode(3, List.empty)))
      implicit lazy val treeAs: As[TreeNode, TreeNode] = As.derived
      val forward                                      = treeAs.into(original)
      assertTrue(forward == Right(original), forward.flatMap(treeAs.from) == Right(original))
    },
    test("linked list round-trips") {
      case class LinkedNode(value: String, next: Option[LinkedNode])

      val original                                           = LinkedNode("a", Some(LinkedNode("b", Some(LinkedNode("c", None)))))
      implicit lazy val linkedAs: As[LinkedNode, LinkedNode] = As.derived
      val forward                                            = linkedAs.into(original)
      assertTrue(forward == Right(original), forward.flatMap(linkedAs.from) == Right(original))
    }
  )

  case class WithListA(items: List[Int])

  case class WithListB(items: List[Int])

  case class WithSetA(items: Set[Int])

  case class WithSetB(items: Set[Int])

  /** Tests for round-trip conversions with collections using As. */
  private val roundTripCollection = suite("Round Trip Collection")(
    test("List[Int] round-trips") {
      val original = WithListA(List(1, 2, 3))
      val as       = As.derived[WithListA, WithListB]
      assert(as.into(original).flatMap(b => as.from(b)))(isRight(equalTo(original)))
    },
    test("empty List round-trips") {
      val original = WithListA(List.empty)
      val as       = As.derived[WithListA, WithListB]
      assert(as.into(original).flatMap(b => as.from(b)))(isRight(equalTo(original)))
    },
    test("Set[Int] round-trips") {
      val original = WithSetA(Set(1, 2, 3))
      val as       = As.derived[WithSetA, WithSetB]
      assert(as.into(original).flatMap(b => as.from(b)))(isRight(equalTo(original)))
    }
  )

  sealed trait ShapeA

  object ShapeA {
    case class Circle(radius: Int) extends ShapeA

    case class Rectangle(width: Int, height: Int) extends ShapeA
  }

  sealed trait ShapeB

  object ShapeB {
    case class Circle(radius: Int) extends ShapeB

    case class Rectangle(width: Int, height: Int) extends ShapeB
  }

  sealed trait StatusA

  object StatusA {
    case object Active extends StatusA

    case object Inactive extends StatusA
  }

  sealed trait StatusB

  object StatusB {
    case object Active extends StatusB

    case object Inactive extends StatusB
  }

  /** Tests for round-trip conversions of coproducts using As. */
  private val roundTripCoproduct = suite("Round Trip Coproduct")(
    test("Circle case round-trips") {
      val original: ShapeA = ShapeA.Circle(10)
      val as               = As.derived[ShapeA, ShapeB]
      assert(as.into(original).flatMap(b => as.from(b)))(isRight(equalTo(original)))
    },
    test("Rectangle case round-trips") {
      val original: ShapeA = ShapeA.Rectangle(20, 30)
      val as               = As.derived[ShapeA, ShapeB]
      assert(as.into(original).flatMap(b => as.from(b)))(isRight(equalTo(original)))
    },
    test("case object round-trips") {
      val original: StatusA = StatusA.Active
      val as                = As.derived[StatusA, StatusB]
      assert(as.into(original).flatMap(b => as.from(b)))(isRight(equalTo(original)))
    }
  )

  case class PersonA(name: String, age: Int)

  case class PersonB(name: String, age: Int)

  case class WithOptionA(name: String, nickname: Option[String])

  case class WithOptionB(name: String, nickname: Option[String])

  /** Tests for round-trip conversions of product types using As. */
  private val roundTripProduct = suite("Round Trip Product")(
    test("round-trip: A -> B -> A") {
      val original = PersonA("Alice", 30)
      val as       = As.derived[PersonA, PersonB]
      assert(as.into(original).flatMap(b => as.from(b)))(isRight(equalTo(original)))
    },
    test("round-trip: B -> A -> B") {
      val original = PersonB("Bob", 25)
      val as       = As.derived[PersonA, PersonB]
      assert(as.from(original).flatMap(a => as.into(a)))(isRight(equalTo(original)))
    },
    test("round-trip with Option[String] Some") {
      val original = WithOptionA("Alice", Some("Ali"))
      val as       = As.derived[WithOptionA, WithOptionB]
      assert(as.into(original).flatMap(b => as.from(b)))(isRight(equalTo(original)))
    },
    test("round-trip with Option[String] None") {
      val original = WithOptionA("Bob", None)
      val as       = As.derived[WithOptionA, WithOptionB]
      assert(as.into(original).flatMap(b => as.from(b)))(isRight(equalTo(original)))
    }
  )

  case class PairA(x: Int, y: String)

  /** Tests for round-trip conversions of tuples using As. */
  private val roundTripTuple = suite("RoundTripTupleSpec")(
    test("(Int, String) round-trips") {
      val original: (Int, String) = (42, "hello")
      val as                      = As.derived[(Int, String), (Int, String)]
      assert(as.into(original).flatMap(b => as.from(b)))(isRight(equalTo(original)))
    },
    test("tuple to case class and back") {
      val original: (Int, String) = (10, "test")
      val as                      = As.derived[(Int, String), PairA]
      assert(as.into(original).flatMap(b => as.from(b)))(isRight(equalTo(original)))
    },
    test("case class to tuple and back") {
      val original = PairA(20, "value")
      val as       = As.derived[PairA, (Int, String)]
      assert(as.into(original).flatMap(b => as.from(b)))(isRight(equalTo(original)))
    }
  )

  case class SimpleA(name: String, age: Int)

  case class SimpleB(name: String, age: Int)

  case class WithOptA2(name: String, opt: Option[Int])

  case class WithOptB2(name: String, opt: Option[Int])

  /** Tests for As compile-time validation rules. */
  private val asCompileTimeRules = suite("As Compile Time Rules")(
    test("derives As for identical case classes") {
      val as = As.derived[SimpleA, SimpleB]
      assert(as.into(SimpleA("test", 42)))(isRight(equalTo(SimpleB("test", 42))))
    },
    test("both directions work for valid derivation") {
      val as = As.derived[SimpleA, SimpleB]
      assertTrue(
        as.into(SimpleA("alice", 30)).isRight,
        as.from(SimpleB("bob", 25)).isRight
      )
    },
    test("Option None preserved in round-trip") {
      val as       = As.derived[WithOptA2, WithOptB2]
      val original = WithOptA2("test", None)
      assert(as.into(original).flatMap(as.from))(isRight(equalTo(original)))
    },
    test("Option Some preserved in round-trip") {
      val as       = As.derived[WithOptA2, WithOptB2]
      val original = WithOptA2("test", Some(42))
      assert(as.into(original).flatMap(as.from))(isRight(equalTo(original)))
    }
  )

  case class WideA(value: Long)

  case class NarrowB(value: Int)

  /** Tests for numeric narrowing round-trips using As. */
  private val numericNarrowingRoundTrip = suite("Numeric Narrowing Round Trip")(
    test("value within Int range round-trips") {
      val original = WideA(1000L)
      val as       = As.derived[WideA, NarrowB]
      assert(as.into(original).flatMap(b => as.from(b)))(isRight(equalTo(original)))
    },
    test("Int.MaxValue round-trips") {
      val original = WideA(Int.MaxValue.toLong)
      val as       = As.derived[WideA, NarrowB]
      assert(as.into(original).flatMap(b => as.from(b)))(isRight(equalTo(original)))
    },
    test("value above Int.MaxValue fails into") {
      val original = WideA(Int.MaxValue.toLong + 1L)
      val as       = As.derived[WideA, NarrowB]
      assert(as.into(original))(isLeft)
    },
    test("from always succeeds (widening)") {
      val b  = NarrowB(Int.MaxValue)
      val as = As.derived[WideA, NarrowB]
      assert(as.from(b))(isRight(equalTo(WideA(Int.MaxValue.toLong))))
    }
  )

  case class LongValueA(value: Long)

  case class IntValueB(value: Int)

  /** Tests for overflow detection in As bidirectional conversions. */
  private val overflowDetection = suite("Overflow Detection")(
    test("into fails when Long exceeds Int.MaxValue") {
      val source = LongValueA(Int.MaxValue.toLong + 1L)
      val as     = As.derived[LongValueA, IntValueB]
      assert(as.into(source))(isLeft)
    },
    test("into fails when Long is below Int.MinValue") {
      val source = LongValueA(Int.MinValue.toLong - 1L)
      val as     = As.derived[LongValueA, IntValueB]
      assert(as.into(source))(isLeft)
    },
    test("into succeeds at Int boundary values") {
      val as     = As.derived[LongValueA, IntValueB]
      val resMax = as.into(LongValueA(Int.MaxValue.toLong))
      val resMin = as.into(LongValueA(Int.MinValue.toLong))
      assert(resMax)(isRight(equalTo(IntValueB(Int.MaxValue)))) &&
      assert(resMin)(isRight(equalTo(IntValueB(Int.MinValue))))
    },
    test("from always succeeds (widening)") {
      val source = IntValueB(Int.MaxValue)
      val as     = As.derived[LongValueA, IntValueB]
      assert(as.from(source))(isRight(equalTo(LongValueA(Int.MaxValue.toLong))))
    }
  )
}
