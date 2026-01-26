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
          TypeId.either.fullName == "scala.Either",
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
    )
  )
}
