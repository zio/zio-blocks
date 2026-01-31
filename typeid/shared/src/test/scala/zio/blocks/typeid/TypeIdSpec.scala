package zio.blocks.typeid

import zio.test._

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
    )
  )
}
