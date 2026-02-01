package zio.blocks.typeid

import zio.blocks.chunk.Chunk
import zio.test._
import zio.test.TestAspect.jvmOnly

case class Person(name: String, age: Int)
case class Address(street: String, city: String)

object Outer {
  case class Inner(value: Int)
}

case class Box[A](value: A)

sealed trait Animal
class Dog extends Animal

object TestObject

object TypeIdSpec extends ZIOSpecDefault {

  def spec = suite("TypeId")(
    suite("Owner")(
      test("fromPackagePath creates correct owner") {
        val owner = Owner.fromPackagePath("com.example.app")
        assertTrue(
          owner.segments == List(
            Owner.Package("com"),
            Owner.Package("example"),
            Owner.Package("app")
          ),
          owner.asString == "com.example.app"
        )
      },
      test("Root owner is empty") {
        assertTrue(
          Owner.Root.segments.isEmpty,
          Owner.Root.isRoot,
          Owner.Root.asString == ""
        )
      },
      test("/ operator appends package segment") {
        val owner = Owner.Root / "com" / "example"
        assertTrue(
          owner.segments == List(Owner.Package("com"), Owner.Package("example")),
          owner.asString == "com.example"
        )
      },
      test("term appends term segment") {
        val owner = (Owner.Root / "com" / "example").term("MyObject")
        assertTrue(
          owner.segments == List(
            Owner.Package("com"),
            Owner.Package("example"),
            Owner.Term("MyObject")
          )
        )
      },
      test("parent returns parent owner") {
        val owner = Owner.Root / "com" / "example"
        assertTrue(
          owner.parent.asString == "com",
          owner.parent.parent.asString == "",
          owner.parent.parent.parent.isRoot
        )
      }
    ),
    suite("TypeParam")(
      test("toString includes name and index") {
        // Basic type param without variance shows just name@index
        assertTrue(
          TypeParam("X", 5).toString == "X@5"
        )
      },
      test("variance is reflected in toString") {
        val covariant     = TypeParam("A", 0, Variance.Covariant)
        val contravariant = TypeParam("A", 0, Variance.Contravariant)
        val invariant     = TypeParam("A", 0, Variance.Invariant)
        assertTrue(
          covariant.toString == "+A@0",
          contravariant.toString == "-A@0",
          invariant.toString == "A@0"
        )
      },
      test("higher-kinded params show arity") {
        val hk1 = TypeParam.higherKinded("F", 0, 1)
        val hk2 = TypeParam.higherKinded("G", 0, 2)
        assertTrue(
          hk1.toString == "F[1]@0",
          hk2.toString == "G[2]@0"
        )
      }
    ),
    suite("TypeId construction")(
      test("nominal creates nominal TypeId") {
        val id = TypeId.nominal[String]("String", Owner.javaLang)
        assertTrue(
          id.name == "String",
          id.owner == Owner.javaLang,
          id.typeParams.isEmpty,
          id.arity == 0,
          !id.isAlias && !id.isOpaque,
          !id.isAlias,
          !id.isOpaque,
          id.fullName == "java.lang.String"
        )
      },
      test("alias creates alias TypeId") {
        val id = TypeId.alias[Int](
          name = "Age",
          owner = Owner.Root / "myapp",
          aliased = TypeRepr.Ref(TypeId.int)
        )
        assertTrue(
          id.name == "Age",
          id.isAlias,
          id.aliasedTo.isDefined
        )
      },
      test("opaque creates opaque TypeId") {
        val id = TypeId.opaque[String](
          name = "Email",
          owner = Owner.Root / "myapp",
          representation = TypeRepr.Ref(TypeId.string)
        )
        assertTrue(
          id.name == "Email",
          id.isOpaque,
          id.representation.isDefined
        )
      },
      test("type constructors have correct arity") {
        assertTrue(
          TypeId.list.arity == 1,
          TypeId.option.arity == 1,
          TypeId.map.arity == 2,
          TypeId.either.arity == 2,
          TypeId.int.arity == 0
        )
      }
    ),
    suite("TypeId extractors")(
      test("Nominal extractor works") {
        TypeId.int match {
          case TypeId.Nominal(name, owner, params, _, _) =>
            assertTrue(name == "Int", owner == Owner.scala, params.isEmpty)
          case _ =>
            assertTrue(false)
        }
      },
      test("Alias extractor works") {
        val aliasId = TypeId.alias[Int]("Age", Owner.Root / "myapp", Nil, TypeRepr.Ref(TypeId.int))
        aliasId match {
          case TypeId.Alias(name, _, _, aliased) =>
            assertTrue(name == "Age", aliased == TypeRepr.Ref(TypeId.int))
          case _ =>
            assertTrue(false)
        }
      },
      test("Opaque extractor works") {
        val opaqueId = TypeId.opaque[String]("Email", Owner.Root / "myapp", Nil, TypeRepr.Ref(TypeId.string))
        opaqueId match {
          case TypeId.Opaque(name, _, _, repr, _) =>
            assertTrue(name == "Email", repr == TypeRepr.Ref(TypeId.string))
          case _ =>
            assertTrue(false)
        }
      }
    ),
    suite("TypeRepr")(
      test("Ref creates type reference") {
        val ref = TypeRepr.Ref(TypeId.int)
        assertTrue(ref.id == TypeId.int)
      },
      test("Applied creates applied type") {
        val listInt = TypeRepr.Applied(
          TypeRepr.Ref(TypeId.list),
          List(TypeRepr.Ref(TypeId.int))
        )
        assertTrue(
          listInt.tycon == TypeRepr.Ref(TypeId.list),
          listInt.args.size == 1
        )
      },
      test("ParamRef references type parameter") {
        val ref = TypeRepr.ParamRef(TypeParam.A)
        assertTrue(ref.param == TypeParam.A, ref.binderDepth == 0)
      },
      test("containsParam correctly identifies type parameters") {
        val paramRef = TypeRepr.ParamRef(TypeParam.A)
        val applied  = TypeRepr.Applied(
          TypeRepr.Ref(TypeId.list),
          List(TypeRepr.ParamRef(TypeParam.A))
        )
        assertTrue(
          paramRef.containsParam(TypeParam.A),
          !paramRef.containsParam(TypeParam.B),
          applied.containsParam(TypeParam.A),
          !applied.containsParam(TypeParam.B)
        )
      },
      test("intersection combines types") {
        val types  = List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string))
        val result = TypeRepr.intersection(types)
        assertTrue(
          result == TypeRepr.Intersection(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string)))
        )
      },
      test("union combines types") {
        val types  = List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string))
        val result = TypeRepr.union(types)
        assertTrue(
          result == TypeRepr.Union(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string)))
        )
      }
    ),
    suite("TermPath")(
      test("fromOwner creates correct path") {
        val owner = Owner.Root / "com" / "example"
        val path  = TermPath.fromOwner(owner, "myValue")
        assertTrue(
          path.asString == "com.example.myValue",
          path.segments.size == 3
        )
      }
    ),
    suite("Member")(
      test("Val member creation") {
        val valMember = Member.Val("x", TypeRepr.Ref(TypeId.int))
        assertTrue(
          valMember.name == "x",
          !valMember.isVar
        )
      },
      test("Def member creation") {
        val defMember = Member.Def(
          name = "foo",
          paramLists = List(List(Param("x", TypeRepr.Ref(TypeId.int)))),
          result = TypeRepr.Ref(TypeId.string)
        )
        assertTrue(
          defMember.name == "foo",
          defMember.paramLists.size == 1
        )
      },
      test("TypeMember isAlias") {
        val aliasMember = Member.TypeMember(
          name = "T",
          lowerBound = Some(TypeRepr.Ref(TypeId.int)),
          upperBound = Some(TypeRepr.Ref(TypeId.int))
        )
        assertTrue(aliasMember.isAlias, !aliasMember.isAbstract)
      },
      test("TypeMember isAbstract") {
        val abstractMember = Member.TypeMember(name = "T")
        assertTrue(abstractMember.isAbstract, !abstractMember.isAlias)
      }
    ),
    suite("Predefined TypeIds")(
      test("primitive types are defined correctly") {
        assertTrue(
          TypeId.int.fullName == "scala.Int",
          TypeId.string.fullName == "java.lang.String",
          TypeId.boolean.fullName == "scala.Boolean",
          TypeId.unit.fullName == "scala.Unit"
        )
      },
      test("collection types are defined correctly") {
        assertTrue(
          TypeId.list.fullName == "scala.collection.immutable.List",
          TypeId.map.fullName == "scala.collection.immutable.Map",
          TypeId.option.fullName == "scala.Option"
        )
      },
      test("java.time types are defined correctly") {
        assertTrue(
          TypeId.instant.fullName == "java.time.Instant",
          TypeId.duration.fullName == "java.time.Duration",
          TypeId.localDate.fullName == "java.time.LocalDate"
        )
      },
      test("java.util types are defined correctly") {
        assertTrue(
          TypeId.uuid.fullName == "java.util.UUID",
          TypeId.currency.fullName == "java.util.Currency"
        )
      },
      test("additional java.time types are defined correctly") {
        assertTrue(
          TypeId.dayOfWeek.fullName == "java.time.DayOfWeek",
          TypeId.localTime.fullName == "java.time.LocalTime",
          TypeId.localDateTime.fullName == "java.time.LocalDateTime",
          TypeId.month.fullName == "java.time.Month",
          TypeId.monthDay.fullName == "java.time.MonthDay",
          TypeId.offsetDateTime.fullName == "java.time.OffsetDateTime",
          TypeId.offsetTime.fullName == "java.time.OffsetTime",
          TypeId.period.fullName == "java.time.Period",
          TypeId.year.fullName == "java.time.Year",
          TypeId.yearMonth.fullName == "java.time.YearMonth",
          TypeId.zoneId.fullName == "java.time.ZoneId",
          TypeId.zoneOffset.fullName == "java.time.ZoneOffset",
          TypeId.zonedDateTime.fullName == "java.time.ZonedDateTime"
        )
      },
      test("additional collection types are defined correctly") {
        assertTrue(
          TypeId.vector.fullName == "scala.collection.immutable.Vector",
          TypeId.set.fullName == "scala.collection.immutable.Set",
          TypeId.seq.fullName == "scala.collection.immutable.Seq",
          TypeId.indexedSeq.fullName == "scala.collection.immutable.IndexedSeq",
          TypeId.either.fullName == "scala.util.Either",
          TypeId.some.fullName == "scala.Some",
          TypeId.none.fullName == "scala.None"
        )
      },
      test("numeric types are defined correctly") {
        assertTrue(
          TypeId.byte.fullName == "scala.Byte",
          TypeId.short.fullName == "scala.Short",
          TypeId.long.fullName == "scala.Long",
          TypeId.float.fullName == "scala.Float",
          TypeId.double.fullName == "scala.Double",
          TypeId.char.fullName == "scala.Char",
          TypeId.bigInt.fullName == "scala.BigInt",
          TypeId.bigDecimal.fullName == "scala.BigDecimal"
        )
      },
      test("java interface types are defined correctly") {
        assertTrue(
          TypeId.charSequence.fullName == "java.lang.CharSequence",
          TypeId.comparable.fullName == "java.lang.Comparable",
          TypeId.serializable.fullName == "java.io.Serializable"
        )
      }
    ),
    suite("TypeIdOps coverage")(
      suite("isTypeReprSubtypeOf")(
        test("returns false for Applied types with different type constructors") {
          val listInt   = TypeRepr.Applied(TypeRepr.Ref(TypeId.list), List(TypeRepr.Ref(TypeId.int)))
          val optionInt = TypeRepr.Applied(TypeRepr.Ref(TypeId.option), List(TypeRepr.Ref(TypeId.int)))
          assertTrue(!TypeIdOps.isTypeReprSubtypeOf(listInt, optionInt))
        },
        test("returns false for Applied types with different size of type args") {
          val listInt   = TypeRepr.Applied(TypeRepr.Ref(TypeId.list), List(TypeRepr.Ref(TypeId.int)))
          val mapIntInt =
            TypeRepr.Applied(TypeRepr.Ref(TypeId.map), List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.int)))
          assertTrue(!TypeIdOps.isTypeReprSubtypeOf(listInt, mapIntInt))
        },
        test("returns false when typeParams size doesn't match args size") {
          val intRef     = TypeRepr.Ref(TypeId.int)
          val appliedInt = TypeRepr.Applied(intRef, List(TypeRepr.Ref(TypeId.string)))
          val listString = TypeRepr.Applied(TypeRepr.Ref(TypeId.list), List(TypeRepr.Ref(TypeId.string)))
          assertTrue(!TypeIdOps.isTypeReprSubtypeOf(appliedInt, listString))
        },
        test("handles Contravariant variance correctly") {
          val function1 = TypeId.nominal[Any => Any](
            "Function1",
            Owner.scala,
            List(
              TypeParam("T", 0, Variance.Contravariant),
              TypeParam("R", 1, Variance.Covariant)
            )
          )
          val stringToString = TypeRepr.Applied(
            TypeRepr.Ref(function1),
            List(TypeRepr.Ref(TypeId.string), TypeRepr.Ref(TypeId.string))
          )
          val intToString = TypeRepr.Applied(
            TypeRepr.Ref(function1),
            List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string))
          )
          assertTrue(!TypeIdOps.isTypeReprSubtypeOf(stringToString, intToString))
        },
        test("handles Invariant variance correctly") {
          val invariantType = TypeId.nominal[Any](
            "InvariantType",
            Owner.Root / "test",
            List(TypeParam("A", 0, Variance.Invariant))
          )
          val invariantInt    = TypeRepr.Applied(TypeRepr.Ref(invariantType), List(TypeRepr.Ref(TypeId.int)))
          val invariantString = TypeRepr.Applied(TypeRepr.Ref(invariantType), List(TypeRepr.Ref(TypeId.string)))
          assertTrue(!TypeIdOps.isTypeReprSubtypeOf(invariantInt, invariantString))
        }
      ),
      suite("checkParents")(
        test("handles Applied type in parents") {
          val listInt     = TypeRepr.Applied(TypeRepr.Ref(TypeId.list), List(TypeRepr.Ref(TypeId.int)))
          val childTypeId = TypeId.nominal[Any](
            "ChildType",
            Owner.Root / "test",
            Nil,
            Nil,
            TypeDefKind.Trait(isSealed = false, knownSubtypes = Nil, bases = List(listInt))
          )
          assertTrue(TypeIdOps.checkParents(childTypeId.defKind.baseTypes, TypeId.list, Set.empty))
        },
        test("returns false for non-matching parent") {
          val stringParent = TypeRepr.Ref(TypeId.string)
          val childTypeId  = TypeId.nominal[Any](
            "ChildType",
            Owner.Root / "test",
            Nil,
            Nil,
            TypeDefKind.Trait(isSealed = false, knownSubtypes = Nil, bases = List(stringParent))
          )
          assertTrue(!TypeIdOps.checkParents(childTypeId.defKind.baseTypes, TypeId.int, Set.empty))
        }
      ),
      suite("checkAppliedSubtyping")(
        test("returns false for different fullNames") {
          assertTrue(!TypeIdOps.checkAppliedSubtyping(TypeId.list, TypeId.option))
        },
        test("returns false for different typeArgs sizes") {
          val listWithOneArg = TypeId.nominal[Any](
            "SomeType",
            Owner.Root / "test",
            List(TypeParam.A),
            List(TypeRepr.Ref(TypeId.int))
          )
          val listWithTwoArgs = TypeId.nominal[Any](
            "SomeType",
            Owner.Root / "test",
            List(TypeParam.A, TypeParam.B),
            List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string))
          )
          assertTrue(!TypeIdOps.checkAppliedSubtyping(listWithOneArg, listWithTwoArgs))
        },
        test("returns false when typeParams size doesn't match typeArgs size") {
          val mismatchedTypeId = TypeId.nominal[Any](
            "MismatchedType",
            Owner.Root / "test",
            List(TypeParam.A, TypeParam.B),
            List(TypeRepr.Ref(TypeId.int))
          )
          val matchedTypeId = TypeId.nominal[Any](
            "MismatchedType",
            Owner.Root / "test",
            List(TypeParam.A, TypeParam.B),
            List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string))
          )
          assertTrue(!TypeIdOps.checkAppliedSubtyping(mismatchedTypeId, matchedTypeId))
        }
      ),
      suite("typeReprEqual for Union and Intersection")(
        test("Union types with same members are equal") {
          val union1 = TypeRepr.Union(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string)))
          val union2 = TypeRepr.Union(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string)))
          assertTrue(TypeIdOps.typeReprEqual(union1, union2))
        },
        test("Union types with different members are not equal") {
          val union1 = TypeRepr.Union(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string)))
          val union2 = TypeRepr.Union(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.boolean)))
          assertTrue(!TypeIdOps.typeReprEqual(union1, union2))
        },
        test("Intersection types with same members are equal") {
          val intersection1 = TypeRepr.Intersection(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string)))
          val intersection2 = TypeRepr.Intersection(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string)))
          assertTrue(TypeIdOps.typeReprEqual(intersection1, intersection2))
        },
        test("Intersection types with different members are not equal") {
          val intersection1 = TypeRepr.Intersection(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string)))
          val intersection2 = TypeRepr.Intersection(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.boolean)))
          assertTrue(!TypeIdOps.typeReprEqual(intersection1, intersection2))
        }
      ),
      suite("typeReprHash for Union and Intersection")(
        test("Union types produce hash") {
          val union = TypeRepr.Union(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string)))
          val hash  = TypeIdOps.typeReprHash(union)
          assertTrue(hash != 0)
        },
        test("Intersection types produce hash") {
          val intersection = TypeRepr.Intersection(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string)))
          val hash         = TypeIdOps.typeReprHash(intersection)
          assertTrue(hash != 0)
        },
        test("Same Union types produce same hash") {
          val union1 = TypeRepr.Union(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string)))
          val union2 = TypeRepr.Union(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string)))
          assertTrue(TypeIdOps.typeReprHash(union1) == TypeIdOps.typeReprHash(union2))
        },
        test("Same Intersection types produce same hash") {
          val intersection1 = TypeRepr.Intersection(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string)))
          val intersection2 = TypeRepr.Intersection(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string)))
          assertTrue(TypeIdOps.typeReprHash(intersection1) == TypeIdOps.typeReprHash(intersection2))
        }
      )
    ),
    suite("TypeId.clazz (JVM reflection)")(
      suite("Scala primitive types")(
        test("Int returns classOf[Int]") {
          assertTrue(TypeId.int.clazz == Some(classOf[Int]))
        },
        test("Long returns classOf[Long]") {
          assertTrue(TypeId.long.clazz == Some(classOf[Long]))
        },
        test("Double returns classOf[Double]") {
          assertTrue(TypeId.double.clazz == Some(classOf[Double]))
        },
        test("Float returns classOf[Float]") {
          assertTrue(TypeId.float.clazz == Some(classOf[Float]))
        },
        test("Short returns classOf[Short]") {
          assertTrue(TypeId.short.clazz == Some(classOf[Short]))
        },
        test("Byte returns classOf[Byte]") {
          assertTrue(TypeId.byte.clazz == Some(classOf[Byte]))
        },
        test("Char returns classOf[Char]") {
          assertTrue(TypeId.char.clazz == Some(classOf[Char]))
        },
        test("Boolean returns classOf[Boolean]") {
          assertTrue(TypeId.boolean.clazz == Some(classOf[Boolean]))
        },
        test("Unit returns classOf[Unit]") {
          assertTrue(TypeId.unit.clazz == Some(classOf[Unit]))
        }
      ),
      suite("Java/Scala standard library types")(
        test("String returns classOf[String]") {
          assertTrue(TypeId.string.clazz == Some(classOf[String]))
        },
        test("BigInt returns classOf[BigInt]") {
          assertTrue(TypeId.bigInt.clazz == Some(classOf[BigInt]))
        },
        test("BigDecimal returns classOf[BigDecimal]") {
          assertTrue(TypeId.bigDecimal.clazz == Some(classOf[BigDecimal]))
        }
      ),
      suite("Collection types")(
        test("List returns classOf[List[_]]") {
          assertTrue(TypeId.list.clazz == Some(classOf[List[_]]))
        },
        test("Vector returns classOf[Vector[_]]") {
          assertTrue(TypeId.vector.clazz == Some(classOf[Vector[_]]))
        },
        test("Set returns classOf[Set[_]]") {
          assertTrue(TypeId.set.clazz == Some(classOf[Set[_]]))
        },
        test("Map returns classOf[Map[_, _]]") {
          assertTrue(TypeId.map.clazz == Some(classOf[Map[_, _]]))
        },
        test("Option returns classOf[Option[_]]") {
          assertTrue(TypeId.option.clazz == Some(classOf[Option[_]]))
        },
        test("Some returns classOf[Some[_]]") {
          assertTrue(TypeId.some.clazz == Some(classOf[Some[_]]))
        },
        test("Either returns classOf[Either[_, _]]") {
          assertTrue(TypeId.either.clazz == Some(classOf[Either[_, _]]))
        },
        test("Seq returns classOf[Seq[_]]") {
          assertTrue(TypeId.seq.clazz == Some(classOf[Seq[_]]))
        },
        test("IndexedSeq returns classOf[IndexedSeq[_]]") {
          assertTrue(TypeId.indexedSeq.clazz == Some(classOf[IndexedSeq[_]]))
        },
        test("Array returns classOf for scala.Array") {
          val clazz: Option[Class[_]] = TypeId.array.clazz
          val name: String            = clazz.map(_.getName).getOrElse("")
          assertTrue(name == "scala.Array")
        }
      ),
      suite("java.time types")(
        test("Instant returns classOf[java.time.Instant]") {
          assertTrue(TypeId.instant.clazz == Some(classOf[java.time.Instant]))
        },
        test("Duration returns classOf[java.time.Duration]") {
          assertTrue(TypeId.duration.clazz == Some(classOf[java.time.Duration]))
        },
        test("LocalDate returns classOf[java.time.LocalDate]") {
          assertTrue(TypeId.localDate.clazz == Some(classOf[java.time.LocalDate]))
        },
        test("LocalTime returns classOf[java.time.LocalTime]") {
          assertTrue(TypeId.localTime.clazz == Some(classOf[java.time.LocalTime]))
        },
        test("LocalDateTime returns classOf[java.time.LocalDateTime]") {
          assertTrue(TypeId.localDateTime.clazz == Some(classOf[java.time.LocalDateTime]))
        },
        test("ZonedDateTime returns classOf[java.time.ZonedDateTime]") {
          assertTrue(TypeId.zonedDateTime.clazz == Some(classOf[java.time.ZonedDateTime]))
        },
        test("OffsetDateTime returns classOf[java.time.OffsetDateTime]") {
          assertTrue(TypeId.offsetDateTime.clazz == Some(classOf[java.time.OffsetDateTime]))
        },
        test("OffsetTime returns classOf[java.time.OffsetTime]") {
          assertTrue(TypeId.offsetTime.clazz == Some(classOf[java.time.OffsetTime]))
        },
        test("ZoneId returns classOf[java.time.ZoneId]") {
          assertTrue(TypeId.zoneId.clazz == Some(classOf[java.time.ZoneId]))
        },
        test("ZoneOffset returns classOf[java.time.ZoneOffset]") {
          assertTrue(TypeId.zoneOffset.clazz == Some(classOf[java.time.ZoneOffset]))
        },
        test("Period returns classOf[java.time.Period]") {
          assertTrue(TypeId.period.clazz == Some(classOf[java.time.Period]))
        },
        test("Year returns classOf[java.time.Year]") {
          assertTrue(TypeId.year.clazz == Some(classOf[java.time.Year]))
        },
        test("YearMonth returns classOf[java.time.YearMonth]") {
          assertTrue(TypeId.yearMonth.clazz == Some(classOf[java.time.YearMonth]))
        },
        test("Month returns classOf[java.time.Month]") {
          assertTrue(TypeId.month.clazz == Some(classOf[java.time.Month]))
        },
        test("MonthDay returns classOf[java.time.MonthDay]") {
          assertTrue(TypeId.monthDay.clazz == Some(classOf[java.time.MonthDay]))
        },
        test("DayOfWeek returns classOf[java.time.DayOfWeek]") {
          assertTrue(TypeId.dayOfWeek.clazz == Some(classOf[java.time.DayOfWeek]))
        }
      ),
      suite("java.util types")(
        test("UUID returns classOf[java.util.UUID]") {
          assertTrue(TypeId.uuid.clazz == Some(classOf[java.util.UUID]))
        },
        test("Currency returns classOf[java.util.Currency]") {
          assertTrue(TypeId.currency.clazz == Some(classOf[java.util.Currency]))
        }
      ),
      suite("Java interface types")(
        test("CharSequence returns classOf[CharSequence]") {
          assertTrue(TypeId.charSequence.clazz == Some(classOf[CharSequence]))
        },
        test("Comparable returns classOf[Comparable[_]]") {
          assertTrue(TypeId.comparable.clazz == Some(classOf[Comparable[_]]))
        },
        test("Serializable returns classOf[java.io.Serializable]") {
          assertTrue(TypeId.serializable.clazz == Some(classOf[java.io.Serializable]))
        }
      ),
      suite("Applied types return erased class")(
        test("List[Int] returns classOf[List[_]]") {
          val listInt = TypeId.of[List[Int]]
          assertTrue(listInt.clazz == Some(classOf[List[_]]))
        },
        test("Option[String] returns classOf[Option[_]]") {
          val optionString = TypeId.of[Option[String]]
          assertTrue(optionString.clazz == Some(classOf[Option[_]]))
        },
        test("Map[String, Int] returns classOf[Map[_, _]]") {
          val mapStringInt = TypeId.of[Map[String, Int]]
          assertTrue(mapStringInt.clazz == Some(classOf[Map[_, _]]))
        }
      ),
      suite("Type aliases return None")(
        test("alias TypeId returns None") {
          val aliasId = TypeId.alias[Int]("Age", Owner.Root / "myapp", Nil, TypeRepr.Ref(TypeId.int))
          assertTrue(aliasId.clazz.isEmpty)
        }
      ),
      suite("Opaque types return None")(
        test("opaque TypeId returns None") {
          val opaqueId = TypeId.opaque[String]("Email", Owner.Root / "myapp", Nil, TypeRepr.Ref(TypeId.string))
          assertTrue(opaqueId.clazz.isEmpty)
        }
      ),
      suite("User-defined types")(
        test("Chunk returns classOf[Chunk[_]]") {
          assertTrue(TypeId.chunk.clazz == Some(classOf[zio.blocks.chunk.Chunk[_]]))
        },
        test("ArraySeq returns classOf[ArraySeq[_]]") {
          assertTrue(TypeId.arraySeq.clazz == Some(classOf[scala.collection.immutable.ArraySeq[_]]))
        }
      ),
      suite("Singleton objects")(
        test("None returns classOf[None.type]") {
          assertTrue(TypeId.none.clazz == Some(None.getClass))
        },
        test("object TypeId works via isObject branch") {
          val objId = TypeId.nominal[TestObject.type](
            "TestObject",
            Owner.fromPackagePath("zio.blocks.typeid"),
            defKind = TypeDefKind.Object(Nil)
          )
          assertTrue(objId.clazz == Some(TestObject.getClass))
        }
      ),
      suite("Derived user-defined case classes")(
        test("case class Person returns its Class") {
          val personId = TypeId.of[Person]
          assertTrue(personId.clazz == Some(classOf[Person]))
        },
        test("case class Address returns its Class") {
          val addressId = TypeId.of[Address]
          assertTrue(addressId.clazz == Some(classOf[Address]))
        },
        test("nested case class Outer.Inner returns its Class") {
          val innerId = TypeId.of[Outer.Inner]
          assertTrue(innerId.clazz == Some(classOf[Outer.Inner]))
        },
        test("generic case class Box[Int] returns erased Class") {
          val boxIntId = TypeId.of[Box[Int]]
          assertTrue(boxIntId.clazz == Some(classOf[Box[_]]))
        },
        test("sealed trait Animal returns its Class") {
          val animalId = TypeId.of[Animal]
          assertTrue(animalId.clazz == Some(classOf[Animal]))
        },
        test("class extending trait returns its Class") {
          val dogId = TypeId.of[Dog]
          assertTrue(dogId.clazz == Some(classOf[Dog]))
        }
      )
    ) @@ jvmOnly,
    suite("TypeId.construct (JVM reflection)")(
      suite("Collection types")(
        test("construct List from elements") {
          val result = TypeId.list.construct(Chunk("a", "b", "c"))
          assertTrue(result == Right(List("a", "b", "c")))
        },
        test("construct empty List") {
          val result = TypeId.list.construct(Chunk.empty)
          assertTrue(result == Right(List.empty))
        },
        test("construct Vector from elements") {
          val result = TypeId.vector.construct(Chunk("x", "y"))
          assertTrue(result == Right(Vector("x", "y")))
        },
        test("construct Set from elements") {
          val result = TypeId.set.construct(Chunk("a", "b", "a"))
          assertTrue(result == Right(Set("a", "b")))
        },
        test("construct Seq from elements") {
          val result = TypeId.seq.construct(Chunk(1: Integer, 2: Integer, 3: Integer))
          assertTrue(result == Right(Seq(1, 2, 3)))
        },
        test("construct IndexedSeq from elements") {
          val result = TypeId.indexedSeq.construct(Chunk("a", "b"))
          assertTrue(result == Right(IndexedSeq("a", "b")))
        },
        test("construct Map from key-value pairs") {
          val result = TypeId.map.construct(Chunk("a", 1: Integer, "b", 2: Integer))
          assertTrue(result == Right(Map("a" -> 1, "b" -> 2)))
        },
        test("construct Map fails with odd number of args") {
          val result = TypeId.map.construct(Chunk("a", 1: Integer, "b"))
          assertTrue(result.isLeft)
        }
      ),
      suite("Option types")(
        test("construct Option with one element") {
          val result = TypeId.option.construct(Chunk("value"))
          assertTrue(result == Right(Some("value")))
        },
        test("construct Option with no elements") {
          val result = TypeId.option.construct(Chunk.empty)
          assertTrue(result == Right(None))
        },
        test("construct Option fails with too many elements") {
          val result = TypeId.option.construct(Chunk("a", "b"))
          assertTrue(result.isLeft)
        },
        test("construct Some with one element") {
          val result = TypeId.some.construct(Chunk("value"))
          assertTrue(result == Right(Some("value")))
        },
        test("construct Some fails with wrong arity") {
          val result = TypeId.some.construct(Chunk.empty)
          assertTrue(result.isLeft)
        },
        test("construct None with no elements") {
          val result = TypeId.none.construct(Chunk.empty)
          assertTrue(result == Right(None))
        },
        test("construct None fails with elements") {
          val result = TypeId.none.construct(Chunk("x"))
          assertTrue(result.isLeft)
        }
      ),
      suite("Either type")(
        test("construct Right") {
          val result = TypeId.either.construct(Chunk(true: java.lang.Boolean, "value"))
          assertTrue(result == Right(scala.util.Right("value")))
        },
        test("construct Left") {
          val result = TypeId.either.construct(Chunk(false: java.lang.Boolean, "error"))
          assertTrue(result == Right(scala.util.Left("error")))
        },
        test("construct Either fails with wrong arity") {
          val result = TypeId.either.construct(Chunk("value"))
          assertTrue(result.isLeft)
        },
        test("construct Either fails with non-boolean first arg") {
          val result = TypeId.either.construct(Chunk("notBoolean", "value"))
          assertTrue(result.isLeft)
        }
      ),
      suite("Tuple types")(
        test("construct Tuple2") {
          val tuple2Id = TypeId.nominal[(Any, Any)]("Tuple2", Owner.scala, List(TypeParam.A, TypeParam.B))
          val result   = tuple2Id.construct(Chunk("a", 1: Integer))
          assertTrue(result == Right(("a", 1)))
        },
        test("construct Tuple2 fails with wrong arity") {
          val tuple2Id = TypeId.nominal[(Any, Any)]("Tuple2", Owner.scala, List(TypeParam.A, TypeParam.B))
          val result   = tuple2Id.construct(Chunk("a"))
          assertTrue(result.isLeft)
        },
        test("construct Tuple3") {
          val tuple3Id =
            TypeId.nominal[(Any, Any, Any)]("Tuple3", Owner.scala, List(TypeParam.A, TypeParam.B, TypeParam("C", 2)))
          val result = tuple3Id.construct(Chunk("a", 1: Integer, true: java.lang.Boolean))
          assertTrue(result == Right(("a", 1, true)))
        }
      ),
      suite("java.time types with factory methods")(
        test("construct java.time.LocalDate") {
          val result = TypeId.localDate.construct(Chunk(2024: Integer, 6: Integer, 15: Integer))
          assertTrue(result == Right(java.time.LocalDate.of(2024, 6, 15)))
        },
        test("construct java.time.LocalTime") {
          val result = TypeId.localTime.construct(Chunk(14: Integer, 30: Integer, 0: Integer))
          assertTrue(result == Right(java.time.LocalTime.of(14, 30, 0)))
        },
        test("construct java.time.LocalDateTime") {
          val result = TypeId.localDateTime.construct(
            Chunk(2024: Integer, 6: Integer, 15: Integer, 14: Integer, 30: Integer, 0: Integer)
          )
          assertTrue(result == Right(java.time.LocalDateTime.of(2024, 6, 15, 14, 30, 0)))
        },
        test("construct java.time.Year") {
          val result = TypeId.year.construct(Chunk(2024: Integer))
          assertTrue(result == Right(java.time.Year.of(2024)))
        },
        test("construct java.time.YearMonth") {
          val result = TypeId.yearMonth.construct(Chunk(2024: Integer, 6: Integer))
          assertTrue(result == Right(java.time.YearMonth.of(2024, 6)))
        },
        test("construct java.time.MonthDay") {
          val result = TypeId.monthDay.construct(Chunk(6: Integer, 15: Integer))
          assertTrue(result == Right(java.time.MonthDay.of(6, 15)))
        },
        test("construct java.time.Duration from seconds") {
          val result = TypeId.duration.construct(Chunk(3600L: java.lang.Long))
          assertTrue(result == Right(java.time.Duration.ofSeconds(3600L)))
        },
        test("construct java.time.Period") {
          val result = TypeId.period.construct(Chunk(1: Integer, 2: Integer, 3: Integer))
          assertTrue(result == Right(java.time.Period.of(1, 2, 3)))
        },
        test("construct java.time.Instant from epoch seconds") {
          val result = TypeId.instant.construct(Chunk(1718450400L: java.lang.Long))
          assertTrue(result == Right(java.time.Instant.ofEpochSecond(1718450400L)))
        },
        test("construct java.time.ZoneOffset from total seconds") {
          val result = TypeId.zoneOffset.construct(Chunk(3600: Integer))
          assertTrue(result == Right(java.time.ZoneOffset.ofTotalSeconds(3600)))
        }
      ),
      suite("User-defined types via reflection")(
        test("construct Person case class") {
          val personId = TypeId.of[Person]
          val result   = personId.construct(Chunk("Alice", 30: Integer))
          assertTrue(result == Right(Person("Alice", 30)))
        },
        test("construct Address case class") {
          val addressId = TypeId.of[Address]
          val result    = addressId.construct(Chunk("123 Main St", "Springfield"))
          assertTrue(result == Right(Address("123 Main St", "Springfield")))
        },
        test("construct nested case class Outer.Inner") {
          val innerId = TypeId.of[Outer.Inner]
          val result  = innerId.construct(Chunk(42: Integer))
          assertTrue(result == Right(Outer.Inner(42)))
        },
        test("construct generic case class Box[String]") {
          val boxId  = TypeId.of[Box[String]]
          val result = boxId.construct(Chunk("content"))
          assertTrue(result == Right(Box("content")))
        },
        test("construct fails with wrong number of arguments") {
          val personId = TypeId.of[Person]
          val result   = personId.construct(Chunk("Alice"))
          assertTrue(result.isLeft)
        },
        test("construct fails with wrong argument types") {
          val personId = TypeId.of[Person]
          val result   = personId.construct(Chunk(123: Integer, "notAnInt"))
          assertTrue(result.isLeft)
        }
      ),
      suite("Error cases")(
        test("construct returns error for alias types") {
          val aliasId = TypeId.alias[Int]("Age", Owner.Root / "myapp", Nil, TypeRepr.Ref(TypeId.int))
          val result  = aliasId.construct(Chunk(42: Integer))
          assertTrue(result.isLeft)
        },
        test("construct returns error for opaque types") {
          val opaqueId = TypeId.opaque[String]("Email", Owner.Root / "myapp", Nil, TypeRepr.Ref(TypeId.string))
          val result   = opaqueId.construct(Chunk("test@example.com"))
          assertTrue(result.isLeft)
        },
        test("LocalDate with wrong arity") {
          val result = TypeId.localDate.construct(Chunk(2024: Integer))
          assertTrue(result.isLeft)
        },
        test("LocalTime with 2 arguments (hour, minute)") {
          val result = TypeId.localTime.construct(Chunk(14: Integer, 30: Integer))
          assertTrue(result == Right(java.time.LocalTime.of(14, 30)))
        },
        test("LocalTime with wrong arity") {
          val result = TypeId.localTime.construct(Chunk(14: Integer))
          assertTrue(result.isLeft)
        },
        test("LocalDateTime with wrong arity") {
          val result = TypeId.localDateTime.construct(Chunk(2024: Integer))
          assertTrue(result.isLeft)
        },
        test("Year with wrong arity") {
          val result = TypeId.year.construct(Chunk.empty)
          assertTrue(result.isLeft)
        },
        test("YearMonth with wrong arity") {
          val result = TypeId.yearMonth.construct(Chunk(2024: Integer))
          assertTrue(result.isLeft)
        },
        test("MonthDay with wrong arity") {
          val result = TypeId.monthDay.construct(Chunk(6: Integer))
          assertTrue(result.isLeft)
        },
        test("Duration with wrong arity") {
          val result = TypeId.duration.construct(Chunk.empty)
          assertTrue(result.isLeft)
        },
        test("Period with wrong arity") {
          val result = TypeId.period.construct(Chunk(1: Integer))
          assertTrue(result.isLeft)
        },
        test("Instant with wrong arity") {
          val result = TypeId.instant.construct(Chunk.empty)
          assertTrue(result.isLeft)
        },
        test("ZoneOffset with wrong arity") {
          val result = TypeId.zoneOffset.construct(Chunk.empty)
          assertTrue(result.isLeft)
        },
        test("reflective construct with null argument") {
          val personId = TypeId.of[Person]
          val result   = personId.construct(Chunk(null, 30: Integer))
          assertTrue(result.isRight)
        },
        test("reflective construct with primitive int param (boxed)") {
          val innerId = TypeId.of[Outer.Inner]
          val result  = innerId.construct(Chunk(99: Integer))
          assertTrue(result == Right(Outer.Inner(99)))
        }
      )
    ) @@ jvmOnly
  )
}
