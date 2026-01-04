package zio.blocks.typeid

import zio.test._

/**
 * Scala 3-specific tests for TypeId derivation.
 *
 * Tests features only available in Scala 3:
 *   - Opaque types
 *   - Type aliases (with proper detection)
 *   - Union types
 *   - Intersection types
 */
object Scala3DerivationSpec extends ZIOSpecDefault {

  // Opaque type definitions
  object OpaqueTypes {
    opaque type Email = String
    object Email {
      def apply(s: String): Email = s
    }

    opaque type Age = Int
    object Age {
      def apply(i: Int): Age = i
    }

    opaque type UserId = Long
    object UserId {
      def apply(l: Long): UserId = l
    }

    opaque type SafeList[A] = List[A]
    object SafeList {
      def apply[A](list: List[A]): SafeList[A] = list
    }
  }

  // Type alias definitions
  object TypeAliases {
    type Age          = Int
    type Name         = String
    type MyList[A]    = List[A]
    type StringMap[V] = Map[String, V]
  }

  def spec = suite("Scala 3 TypeId Derivation")(
    suite("Opaque Types")(
      test("derives TypeId for simple opaque type (Email)") {
        val id = TypeId.derived[OpaqueTypes.Email]
        assertTrue(
          id.name == "Email",
          id.isOpaque,
          id.opaqueRepresentation.isDefined
        )
      },
      test("derives TypeId for numeric opaque type (Age)") {
        val id = TypeId.derived[OpaqueTypes.Age]
        assertTrue(
          id.name == "Age",
          id.isOpaque
        )
      },
      test("derives TypeId for Long opaque type (UserId)") {
        val id = TypeId.derived[OpaqueTypes.UserId]
        assertTrue(
          id.name == "UserId",
          id.isOpaque
        )
      },
      test("derives TypeId for generic opaque type (SafeList)") {
        val id = TypeId.derived[OpaqueTypes.SafeList[Any]]
        assertTrue(
          id.name == "SafeList",
          id.isOpaque,
          id.arity == 1
        )
      },
      test("opaque type has correct owner path") {
        val id = TypeId.derived[OpaqueTypes.Email]
        assertTrue(
          id.fullName.contains("OpaqueTypes")
        )
      }
    ),
    suite("Type Aliases")(
      test("derives TypeId for simple type alias (Age = Int)") {
        val id = TypeId.derived[TypeAliases.Age]
        assertTrue(
          id.name == "Age",
          id.isAlias,
          id.aliasedType.isDefined
        )
      },
      test("derives TypeId for String alias (Name)") {
        val id = TypeId.derived[TypeAliases.Name]
        assertTrue(
          id.name == "Name",
          id.isAlias
        )
      },
      // Note: Simple generic type aliases like `type MyList[A] = List[A]` are fully transparent
      // and the compiler reduces them to the underlying type before the macro runs.
      // Partially applied type aliases like StringMap work because they have fixed type args.
      test("derives TypeId for partially applied type alias (StringMap[V])") {
        val id = TypeId.derived[TypeAliases.StringMap[Int]]
        assertTrue(
          id.name == "StringMap",
          id.isAlias
        )
      }
    ),
    suite("Union Types")(
      test("derives TypeId for union type String | Int") {
        // Union types in Scala 3
        type StringOrInt = String | Int
        val id = TypeId.derived[StringOrInt]
        assertTrue(
          id.name.nonEmpty // Just verify it works
        )
      }
    ),
    suite("Intersection Types")(
      test("derives TypeId for intersection type") {
        trait A
        trait B
        type AandB = A & B
        val id = TypeId.derived[AandB]
        assertTrue(
          id.name.nonEmpty
        )
      }
    ),
    suite("Enum Types (Scala 3)")(
      test("derives TypeId for simple enum") {
        enum Color {
          case Red, Green, Blue
        }
        val id = TypeId.derived[Color]
        assertTrue(
          id.name == "Color",
          id.isNominal
        )
      },
      test("derives TypeId for enum with params") {
        enum Planet(val mass: Double) {
          case Earth extends Planet(5.97e24)
          case Mars  extends Planet(6.42e23)
        }
        val id = TypeId.derived[Planet]
        assertTrue(
          id.name == "Planet"
        )
      }
    ),
    suite("Higher-Kinded Types")(
      test("derives TypeId for Functor-like type constructor") {
        trait Functor[F[_]]
        val id = TypeId.derived[Functor[List]]
        assertTrue(
          id.name == "Functor"
        )
      },
      test("derives TypeId for Monad-like type constructor") {
        trait Monad[F[_]]
        val id = TypeId.derived[Monad[Option]]
        assertTrue(
          id.name == "Monad"
        )
      }
    ),
    suite("Opaque Type Representation")(
      test("Email opaque type has String representation") {
        val id = TypeId.derived[OpaqueTypes.Email]
        assertTrue(
          id.opaqueRepresentation match {
            case Some(TypeRepr.Ref(typeId)) => typeId.name == "String"
            case _                          => false
          }
        )
      },
      test("Age opaque type has Int representation") {
        val id = TypeId.derived[OpaqueTypes.Age]
        assertTrue(
          id.opaqueRepresentation match {
            case Some(TypeRepr.Ref(typeId)) => typeId.name == "Int"
            case _                          => false
          }
        )
      }
    ),
    suite("Type Alias Aliased Type")(
      test("Age alias references Int") {
        val id = TypeId.derived[TypeAliases.Age]
        assertTrue(
          id.aliasedType match {
            case Some(TypeRepr.Ref(typeId)) => typeId.name == "Int"
            case _                          => false
          }
        )
      },
      test("StringMap alias references Map") {
        val id = TypeId.derived[TypeAliases.StringMap[Int]]
        assertTrue(
          id.aliasedType match {
            case Some(TypeRepr.Ref(typeId)) => typeId.name == "Map"
            case _                          => false
          }
        )
      }
    )
  )
}
