package zio.blocks.schema

import zio.test._

object TypeIdSpec extends ZIOSpecDefault {

  def spec = suite("TypeIdSpec")(
    suite("primitive TypeIds")(
      test("int has correct name") {
        assertTrue(TypeId.int.name == "Int")
      },
      test("int has correct fullName") {
        assertTrue(TypeId.int.fullName == "scala.Int")
      },
      test("string has correct name") {
        assertTrue(TypeId.string.name == "String")
      },
      test("boolean has correct fullName") {
        assertTrue(TypeId.boolean.fullName == "scala.Boolean")
      }
    ),
    suite("java.time TypeIds")(
      test("instant has correct fullName") {
        assertTrue(TypeId.instant.fullName == "java.time.Instant")
      },
      test("duration has correct fullName") {
        assertTrue(TypeId.duration.fullName == "java.time.Duration")
      },
      test("localDate has correct fullName") {
        assertTrue(TypeId.localDate.fullName == "java.time.LocalDate")
      }
    ),
    suite("collection TypeIds")(
      test("list[Int] has correct name") {
        val listInt = TypeId.list(TypeId.int)
        assertTrue(listInt.name == "List[Int]")
      },
      test("list[Int] has correct fullName") {
        val listInt = TypeId.list(TypeId.int)
        assertTrue(listInt.fullName == "scala.collection.immutable.List[scala.Int]")
      },
      test("map[String, Int] has correct name") {
        val mapStringInt = TypeId.map(TypeId.string, TypeId.int)
        assertTrue(mapStringInt.name == "Map[String, Int]")
      },
      test("option[String] has correct name") {
        val optString = TypeId.option(TypeId.string)
        assertTrue(optString.name == "Option[String]")
      },
      test("nested collections work") {
        val listListInt = TypeId.list(TypeId.list(TypeId.int))
        assertTrue(listListInt.name == "List[List[Int]]")
      }
    ),
    suite("TypeRepr types")(
      test("Nominal creates nominal type") {
        val personType = TypeRepr.Nominal(Owner(Seq("com", "example")), "Person")
        assertTrue(personType.fullName == "com.example.Person")
      },
      test("Applied creates applied type") {
        val listInt = TypeRepr.Applied(
          TypeRepr.Nominal(Owner.scalaCollectionImmutable, "List"),
          Seq(TypeRepr.Nominal(Owner.scala, "Int"))
        )
        assertTrue(listInt.name == "List[Int]")
      },
      test("Structural creates structural type") {
        val structType = TypeRepr.Structural(
          Seq(
            TypeMember("name", TypeRepr.Nominal(Owner.scala, "String")),
            TypeMember("age", TypeRepr.Nominal(Owner.scala, "Int"))
          )
        )
        assertTrue(structType.name.contains("name") && structType.name.contains("age"))
      },
      test("Intersection creates intersection type") {
        val intersect = TypeRepr.Intersection(
          Seq(
            TypeRepr.Nominal(Owner(Seq("example")), "A"),
            TypeRepr.Nominal(Owner(Seq("example")), "B")
          )
        )
        assertTrue(intersect.name == "A & B")
      },
      test("Union creates union type") {
        val union = TypeRepr.Union(
          Seq(
            TypeRepr.Nominal(Owner(Seq("example")), "A"),
            TypeRepr.Nominal(Owner(Seq("example")), "B")
          )
        )
        assertTrue(union.name == "A | B")
      },
      test("Function creates function type") {
        val func = TypeRepr.Function(
          Seq(TypeRepr.Nominal(Owner.scala, "Int")),
          TypeRepr.Nominal(Owner.scala, "String")
        )
        assertTrue(func.name == "Int => String")
      },
      test("Function with multiple params") {
        val func = TypeRepr.Function(
          Seq(
            TypeRepr.Nominal(Owner.scala, "Int"),
            TypeRepr.Nominal(Owner.scala, "String")
          ),
          TypeRepr.Nominal(Owner.scala, "Boolean")
        )
        assertTrue(func.name == "(Int, String) => Boolean")
      },
      test("Tuple creates tuple type") {
        val tuple = TypeRepr.Tuple(
          Seq(
            TypeRepr.Nominal(Owner.scala, "Int"),
            TypeRepr.Nominal(Owner.scala, "String")
          )
        )
        assertTrue(tuple.name == "(Int, String)")
      },
      test("Singleton creates singleton type") {
        val singleton = TypeRepr.Singleton(Owner(Seq("example")), "MyObject")
        assertTrue(singleton.fullName == "example.MyObject.type")
      },
      test("TypeParam creates type parameter") {
        val param = TypeRepr.TypeParam("T", 0)
        assertTrue(param.name == "T")
      }
    ),
    suite("structural equality")(
      test("same nominal types are equal") {
        val t1 = TypeId.int
        val t2 = TypeId.nominal[Int](Owner.scala, "Int")
        assertTrue(t1 == t2)
      },
      test("different nominal types are not equal") {
        assertTrue(TypeId.int != TypeId.string)
      },
      test("same applied types are equal") {
        val l1 = TypeId.list(TypeId.int)
        val l2 = TypeId.list(TypeId.int)
        assertTrue(l1 == l2)
      },
      test("different applied types are not equal") {
        val l1 = TypeId.list(TypeId.int)
        val l2 = TypeId.list(TypeId.string)
        assertTrue(l1 != l2)
      },
      test("alias expands to underlying for equality") {
        val alias =
          TypeId.Alias[Int](TypeRepr.Alias(Owner(Seq("example")), "Age", TypeRepr.Nominal(Owner.scala, "Int")))
        assertTrue(alias == TypeId.int)
      },
      test("opaque types are NOT equal to their representation") {
        val opaque =
          TypeId.Opaque[Int](TypeRepr.Opaque(Owner(Seq("example")), "UserId"))
        val nominal = TypeId.nominal[Int](Owner(Seq("example")), "UserId")
        assertTrue(opaque != nominal)
      }
    ),
    suite("hash code")(
      test("equal types have same hash code") {
        val t1 = TypeId.int
        val t2 = TypeId.nominal[Int](Owner.scala, "Int")
        assertTrue(t1.hashCode == t2.hashCode)
      },
      test("list[Int] has consistent hash") {
        val l1 = TypeId.list(TypeId.int)
        val l2 = TypeId.list(TypeId.int)
        assertTrue(l1.hashCode == l2.hashCode)
      }
    ),
    suite("normalization")(
      test("aliases are expanded") {
        val alias      = TypeRepr.Alias(Owner(Seq("example")), "Age", TypeRepr.Nominal(Owner.scala, "Int"))
        val normalized = TypeId.normalize(alias)
        assertTrue(normalized == TypeRepr.Nominal(Owner.scala, "Int"))
      },
      test("intersection types are sorted") {
        val i1 = TypeRepr.Intersection(
          Seq(
            TypeRepr.Nominal(Owner(Seq("example")), "B"),
            TypeRepr.Nominal(Owner(Seq("example")), "A")
          )
        )
        val normalized = TypeId.normalize(i1)
        normalized match {
          case TypeRepr.Intersection(types) =>
            assertTrue(types.head.name == "A" && types(1).name == "B")
          case _ => assertTrue(false)
        }
      },
      test("union types are sorted") {
        val u1 = TypeRepr.Union(
          Seq(
            TypeRepr.Nominal(Owner(Seq("example")), "B"),
            TypeRepr.Nominal(Owner(Seq("example")), "A")
          )
        )
        val normalized = TypeId.normalize(u1)
        normalized match {
          case TypeRepr.Union(types) =>
            assertTrue(types.head.name == "A" && types(1).name == "B")
          case _ => assertTrue(false)
        }
      }
    ),
    suite("subtype checking")(
      test("type is subtype of itself") {
        assertTrue(TypeId.isSubtypeOf(TypeRepr.Nominal(Owner.scala, "Int"), TypeRepr.Nominal(Owner.scala, "Int")))
      },
      test("type with parent is subtype of parent") {
        val child =
          TypeRepr.Nominal(Owner(Seq("example")), "Child", Seq(TypeRepr.Nominal(Owner(Seq("example")), "Parent")))
        val parent = TypeRepr.Nominal(Owner(Seq("example")), "Parent")
        assertTrue(TypeId.isSubtypeOf(child, parent))
      },
      test("intersection is subtype of its components") {
        val a         = TypeRepr.Nominal(Owner(Seq("example")), "A")
        val b         = TypeRepr.Nominal(Owner(Seq("example")), "B")
        val intersect = TypeRepr.Intersection(Seq(a, b))
        assertTrue(TypeId.isSubtypeOf(intersect, a)) &&
        assertTrue(TypeId.isSubtypeOf(intersect, b))
      },
      test("union is subtype only if all components are subtypes") {
        val a      = TypeRepr.Nominal(Owner(Seq("example")), "A", Seq(TypeRepr.Nominal(Owner(Seq("example")), "Parent")))
        val b      = TypeRepr.Nominal(Owner(Seq("example")), "B", Seq(TypeRepr.Nominal(Owner(Seq("example")), "Parent")))
        val union  = TypeRepr.Union(Seq(a, b))
        val parent = TypeRepr.Nominal(Owner(Seq("example")), "Parent")
        assertTrue(TypeId.isSubtypeOf(union, parent))
      }
    ),
    suite("TypeName conversion")(
      test("converts to TypeName and back") {
        val original  = TypeId.int
        val typeName  = original.toTypeName
        val converted = TypeId.fromTypeName(typeName)
        assertTrue(original == converted)
      },
      test("converts list[Int] to TypeName and back") {
        val original  = TypeId.list(TypeId.int)
        val typeName  = original.toTypeName
        val converted = TypeId.fromTypeName(typeName)
        assertTrue(original == converted)
      },
      test("converts map[String, Int] to TypeName and back") {
        val original  = TypeId.map(TypeId.string, TypeId.int)
        val typeName  = original.toTypeName
        val converted = TypeId.fromTypeName(typeName)
        assertTrue(original == converted)
      }
    ),
    suite("Owner")(
      test("fromNamespace creates correct Owner") {
        val ns    = new Namespace(List("com", "example"), List("Foo"))
        val owner = Owner.fromNamespace(ns)
        assertTrue(owner.packages == Seq("com", "example")) &&
        assertTrue(owner.values == Seq("Foo"))
      },
      test("fullPath concatenates correctly") {
        val owner = Owner(Seq("com", "example"), Seq("Foo", "Bar"))
        assertTrue(owner.fullPath == "com.example.Foo.Bar")
      }
    ),
    suite("TypeBounds")(
      test("empty bounds uses Nothing and Any") {
        val bounds = TypeBounds.empty
        assertTrue(bounds.lower == TypeRepr.Nothing) &&
        assertTrue(bounds.upper == TypeRepr.Any)
      }
    )
  )
}
