package zio.blocks.typeid

import zio.test._

object TypeIdSpec extends ZIOSpecDefault {

  sealed trait TestSealedTrait
  case class TestCaseClass1(value: Int)                     extends TestSealedTrait
  case class TestCaseClass2(name: String, items: List[Int]) extends TestSealedTrait
  case object TestCaseObject                                extends TestSealedTrait

  class OuterClass {
    class InnerClass
    object InnerObject {
      class DeepInnerClass
    }
  }

  trait TestTrait[A, B] {
    def combine(a: A, b: B): (A, B)
  }

  case class GenericRecord[A, B, C](first: A, second: B, third: C)

  case class Person(name: String, age: Int)
  case class Box[A](value: A)
  case class Pair[A, B](first: A, second: B)

  def spec: Spec[TestEnvironment, Any] = suite("TypeIdSpec")(
    ownerSuite,
    typeParamSuite,
    typeIdCoreSuite,
    typeReprSuite,
    memberSuite,
    termPathSuite,
    builtInConstantsSuite,
    macroDerivedSuite,
    nestedTypesSuite,
    complexGenericsSuite,
    patternMatchingSuite
  )

  val ownerSuite: Spec[Any, Nothing] = suite("Owner")(
    test("Root owner has empty segments") {
      assertTrue(Owner.Root.segments.isEmpty) &&
      assertTrue(Owner.Root.asString == "")
    },
    test("asString concatenates segment names with dots") {
      val owner = Owner(List(Owner.Package("zio"), Owner.Package("blocks"), Owner.Type("Foo")))
      assertTrue(owner.asString == "zio.blocks.Foo")
    },
    test("single package segment") {
      val owner = Owner(List(Owner.Package("scala")))
      assertTrue(owner.asString == "scala")
    },
    test("mixed segment types") {
      val owner = Owner(
        List(
          Owner.Package("com"),
          Owner.Package("example"),
          Owner.Term("MyObject"),
          Owner.Type("MyClass")
        )
      )
      assertTrue(owner.asString == "com.example.MyObject.MyClass")
    },
    test("deeply nested packages") {
      val owner = Owner(
        List(
          Owner.Package("org"),
          Owner.Package("apache"),
          Owner.Package("spark"),
          Owner.Package("sql"),
          Owner.Package("catalyst")
        )
      )
      assertTrue(owner.asString == "org.apache.spark.sql.catalyst")
    },
    test("Owner.Package stores name correctly") {
      val segment = Owner.Package("mypackage")
      assertTrue(segment.name == "mypackage")
    },
    test("Owner.Term stores name correctly") {
      val segment = Owner.Term("MyObject")
      assertTrue(segment.name == "MyObject")
    },
    test("Owner.Type stores name correctly") {
      val segment = Owner.Type("MyClass")
      assertTrue(segment.name == "MyClass")
    }
  )

  val typeParamSuite: Spec[Any, Nothing] = suite("TypeParam")(
    test("stores name and index") {
      val param = TypeParam("A", 0)
      assertTrue(param.name == "A") &&
      assertTrue(param.index == 0)
    },
    test("multiple type params with sequential indices") {
      val params = List(TypeParam("K", 0), TypeParam("V", 1))
      assertTrue(params(0).name == "K") &&
      assertTrue(params(0).index == 0) &&
      assertTrue(params(1).name == "V") &&
      assertTrue(params(1).index == 1)
    },
    test("type param with higher-kinded name") {
      val param = TypeParam("F", 0)
      assertTrue(param.name == "F")
    }
  )

  val typeIdCoreSuite: Spec[Any, Nothing] = suite("TypeId core")(
    test("nominal creates nominal TypeId") {
      val id = TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil)
      assertTrue(id.name == "Int") &&
      assertTrue(id.owner.asString == "scala") &&
      assertTrue(id.typeParams.isEmpty) &&
      assertTrue(id.arity == 0) &&
      assertTrue(id.fullName == "scala.Int")
    },
    test("nominal with single type param") {
      val params = List(TypeParam("A", 0))
      val id     = TypeId.nominal[List[_]](
        "List",
        Owner(List(Owner.Package("scala"), Owner.Package("collection"), Owner.Package("immutable"))),
        params
      )
      assertTrue(id.name == "List") &&
      assertTrue(id.typeParams.size == 1) &&
      assertTrue(id.arity == 1) &&
      assertTrue(id.fullName == "scala.collection.immutable.List")
    },
    test("nominal with multiple type params") {
      val params = List(TypeParam("K", 0), TypeParam("V", 1))
      val id     = TypeId.nominal[Map[_, _]](
        "Map",
        Owner(List(Owner.Package("scala"), Owner.Package("collection"), Owner.Package("immutable"))),
        params
      )
      assertTrue(id.name == "Map") &&
      assertTrue(id.typeParams.size == 2) &&
      assertTrue(id.arity == 2) &&
      assertTrue(id.typeParams(0).name == "K") &&
      assertTrue(id.typeParams(1).name == "V")
    },
    test("alias creates alias TypeId") {
      val intRef = TypeRepr.Ref(TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil))
      val id     = TypeId.alias[Int]("Age", Owner(List(Owner.Package("myapp"))), Nil, intRef)
      assertTrue(id.name == "Age") &&
      assertTrue(id.fullName == "myapp.Age") &&
      assertTrue(id.arity == 0)
    },
    test("alias with type params") {
      val listId = TypeId.nominal[List[_]](
        "List",
        Owner(List(Owner.Package("scala"), Owner.Package("collection"), Owner.Package("immutable"))),
        List(TypeParam("A", 0))
      )
      val param   = TypeParam("A", 0)
      val aliased = TypeRepr.Applied(TypeRepr.Ref(listId), List(TypeRepr.ParamRef(param)))
      val id      = TypeId.alias[List[_]]("MyList", Owner(List(Owner.Package("myapp"))), List(param), aliased)
      assertTrue(id.name == "MyList") &&
      assertTrue(id.arity == 1) &&
      assertTrue(id.typeParams.head.name == "A")
    },
    test("opaque creates opaque TypeId") {
      val stringRef = TypeRepr.Ref(
        TypeId.nominal[String]("String", Owner(List(Owner.Package("java"), Owner.Package("lang"))), Nil)
      )
      val id = TypeId.opaque[String]("Email", Owner(List(Owner.Package("myapp"))), Nil, stringRef)
      assertTrue(id.name == "Email") &&
      assertTrue(id.fullName == "myapp.Email") &&
      assertTrue(id.arity == 0)
    },
    test("fullName with root owner returns just name") {
      val id = TypeId.nominal[Int]("Int", Owner.Root, Nil)
      assertTrue(id.fullName == "Int")
    },
    test("fullName with deep nesting") {
      val owner = Owner(
        List(
          Owner.Package("com"),
          Owner.Package("example"),
          Owner.Term("Outer"),
          Owner.Type("Inner")
        )
      )
      val id = TypeId.nominal[Int]("Nested", owner, Nil)
      assertTrue(id.fullName == "com.example.Outer.Inner.Nested")
    },
    test("arity returns zero for non-generic types") {
      val id = TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil)
      assertTrue(id.arity == 0)
    },
    test("arity returns correct count for generic types") {
      val params = List(TypeParam("A", 0), TypeParam("B", 1), TypeParam("C", 2))
      val id     = TypeId.nominal[Nothing]("Triple", Owner.Root, params)
      assertTrue(id.arity == 3)
    }
  )

  val typeReprSuite: Spec[Any, Nothing] = suite("TypeRepr")(
    test("Ref holds TypeId") {
      val id  = TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil)
      val ref = TypeRepr.Ref(id)
      assertTrue(ref.id == id)
    },
    test("ParamRef holds TypeParam") {
      val param = TypeParam("A", 0)
      val ref   = TypeRepr.ParamRef(param)
      assertTrue(ref.param == param)
    },
    test("Applied holds tycon and args") {
      val listId = TypeId.nominal[List[_]](
        "List",
        Owner(List(Owner.Package("scala"), Owner.Package("collection"), Owner.Package("immutable"))),
        List(TypeParam("A", 0))
      )
      val intId   = TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil)
      val applied = TypeRepr.Applied(TypeRepr.Ref(listId), List(TypeRepr.Ref(intId)))
      assertTrue(applied.tycon == TypeRepr.Ref(listId)) &&
      assertTrue(applied.args == List(TypeRepr.Ref(intId)))
    },
    test("Applied with multiple args") {
      val mapId = TypeId.nominal[Map[_, _]](
        "Map",
        Owner(List(Owner.Package("scala"), Owner.Package("collection"), Owner.Package("immutable"))),
        List(TypeParam("K", 0), TypeParam("V", 1))
      )
      val stringId = TypeId.nominal[String]("String", Owner(List(Owner.Package("java"), Owner.Package("lang"))), Nil)
      val intId    = TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil)
      val applied  = TypeRepr.Applied(
        TypeRepr.Ref(mapId),
        List(TypeRepr.Ref(stringId), TypeRepr.Ref(intId))
      )
      assertTrue(applied.args.size == 2)
    },
    test("nested Applied for complex types") {
      val listId = TypeId.nominal[List[_]](
        "List",
        Owner(List(Owner.Package("scala"), Owner.Package("collection"), Owner.Package("immutable"))),
        List(TypeParam("A", 0))
      )
      val optionId      = TypeId.nominal[Option[_]]("Option", Owner(List(Owner.Package("scala"))), List(TypeParam("A", 0)))
      val intId         = TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil)
      val listInt       = TypeRepr.Applied(TypeRepr.Ref(listId), List(TypeRepr.Ref(intId)))
      val optionListInt = TypeRepr.Applied(TypeRepr.Ref(optionId), List(listInt))
      assertTrue(optionListInt.tycon == TypeRepr.Ref(optionId)) &&
      assertTrue(optionListInt.args.head == listInt)
    },
    test("Intersection holds left and right") {
      val aRef         = TypeRepr.Ref(TypeId.nominal[Int]("A", Owner.Root, Nil))
      val bRef         = TypeRepr.Ref(TypeId.nominal[String]("B", Owner.Root, Nil))
      val intersection = TypeRepr.Intersection(aRef, bRef)
      assertTrue(intersection.left == aRef) &&
      assertTrue(intersection.right == bRef)
    },
    test("Union holds left and right") {
      val aRef  = TypeRepr.Ref(TypeId.nominal[Int]("A", Owner.Root, Nil))
      val bRef  = TypeRepr.Ref(TypeId.nominal[String]("B", Owner.Root, Nil))
      val union = TypeRepr.Union(aRef, bRef)
      assertTrue(union.left == aRef) &&
      assertTrue(union.right == bRef)
    },
    test("Tuple holds elements") {
      val intRef    = TypeRepr.Ref(TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil))
      val stringRef = TypeRepr.Ref(
        TypeId.nominal[String]("String", Owner(List(Owner.Package("java"), Owner.Package("lang"))), Nil)
      )
      val boolRef = TypeRepr.Ref(TypeId.nominal[Boolean]("Boolean", Owner(List(Owner.Package("scala"))), Nil))
      val tuple   = TypeRepr.Tuple(List(intRef, stringRef, boolRef))
      assertTrue(tuple.elems.size == 3) &&
      assertTrue(tuple.elems == List(intRef, stringRef, boolRef))
    },
    test("Function holds params and result") {
      val intRef    = TypeRepr.Ref(TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil))
      val stringRef = TypeRepr.Ref(
        TypeId.nominal[String]("String", Owner(List(Owner.Package("java"), Owner.Package("lang"))), Nil)
      )
      val boolRef  = TypeRepr.Ref(TypeId.nominal[Boolean]("Boolean", Owner(List(Owner.Package("scala"))), Nil))
      val function = TypeRepr.Function(List(intRef, stringRef), boolRef)
      assertTrue(function.params == List(intRef, stringRef)) &&
      assertTrue(function.result == boolRef)
    },
    test("Function with no params") {
      val intRef   = TypeRepr.Ref(TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil))
      val function = TypeRepr.Function(Nil, intRef)
      assertTrue(function.params.isEmpty) &&
      assertTrue(function.result == intRef)
    },
    test("Constant holds various value types") {
      assertTrue(TypeRepr.Constant(42).value == 42) &&
      assertTrue(TypeRepr.Constant("hello").value == "hello") &&
      assertTrue(TypeRepr.Constant(true).value == true) &&
      assertTrue(TypeRepr.Constant(3.14).value == 3.14)
    },
    test("Singleton holds path") {
      val path      = TermPath(List(TermPath.Package("myapp"), TermPath.Term("myObject")))
      val singleton = TypeRepr.Singleton(path)
      assertTrue(singleton.path == path)
    },
    test("AnyType is a singleton object") {
      assertTrue(TypeRepr.AnyType == TypeRepr.AnyType)
    },
    test("NothingType is a singleton object") {
      assertTrue(TypeRepr.NothingType == TypeRepr.NothingType)
    },
    test("Structural with parents and members") {
      val intRef     = TypeRepr.Ref(TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil))
      val boolRef    = TypeRepr.Ref(TypeId.nominal[Boolean]("Boolean", Owner(List(Owner.Package("scala"))), Nil))
      val structural = TypeRepr.Structural(
        parents = List(intRef),
        members = List(
          Member.Val("size", intRef, isVar = false),
          Member.Def("isEmpty", Nil, boolRef)
        )
      )
      assertTrue(structural.parents.size == 1) &&
      assertTrue(structural.members.size == 2)
    }
  )

  val memberSuite: Spec[Any, Nothing] = suite("Member")(
    test("Val member with isVar false") {
      val intRef = TypeRepr.Ref(TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil))
      val member = Member.Val("x", intRef, isVar = false)
      assertTrue(member.name == "x") &&
      assertTrue(member.tpe == intRef) &&
      assertTrue(!member.isVar)
    },
    test("Val member with isVar true") {
      val intRef = TypeRepr.Ref(TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil))
      val member = Member.Val("counter", intRef, isVar = true)
      assertTrue(member.name == "counter") &&
      assertTrue(member.isVar)
    },
    test("Def member with single param list") {
      val intRef    = TypeRepr.Ref(TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil))
      val stringRef = TypeRepr.Ref(
        TypeId.nominal[String]("String", Owner(List(Owner.Package("java"), Owner.Package("lang"))), Nil)
      )
      val member = Member.Def("foo", List(List(Param("x", intRef))), stringRef)
      assertTrue(member.name == "foo") &&
      assertTrue(member.paramLists.size == 1) &&
      assertTrue(member.paramLists.head.size == 1) &&
      assertTrue(member.result == stringRef)
    },
    test("Def member with multiple param lists") {
      val intRef    = TypeRepr.Ref(TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil))
      val stringRef = TypeRepr.Ref(
        TypeId.nominal[String]("String", Owner(List(Owner.Package("java"), Owner.Package("lang"))), Nil)
      )
      val member = Member.Def(
        "curried",
        List(List(Param("x", intRef)), List(Param("y", stringRef))),
        intRef
      )
      assertTrue(member.paramLists.size == 2)
    },
    test("Def member with no params") {
      val intRef = TypeRepr.Ref(TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil))
      val member = Member.Def("getValue", Nil, intRef)
      assertTrue(member.paramLists.isEmpty) &&
      assertTrue(member.result == intRef)
    },
    test("TypeMember with no bounds") {
      val member = Member.TypeMember("T", Nil, None, None)
      assertTrue(member.name == "T") &&
      assertTrue(member.typeParams.isEmpty) &&
      assertTrue(member.lowerBound.isEmpty) &&
      assertTrue(member.upperBound.isEmpty)
    },
    test("TypeMember with upper bound") {
      val anyRef = TypeRepr.AnyType
      val member = Member.TypeMember("T", Nil, None, Some(anyRef))
      assertTrue(member.upperBound.contains(anyRef))
    },
    test("TypeMember with lower bound") {
      val nothingRef = TypeRepr.NothingType
      val member     = Member.TypeMember("T", Nil, Some(nothingRef), None)
      assertTrue(member.lowerBound.contains(nothingRef))
    },
    test("TypeMember with type params") {
      val params = List(TypeParam("A", 0))
      val member = Member.TypeMember("F", params, None, None)
      assertTrue(member.typeParams.size == 1) &&
      assertTrue(member.typeParams.head.name == "A")
    }
  )

  val termPathSuite: Spec[Any, Nothing] = suite("TermPath")(
    test("segments are stored correctly") {
      val path = TermPath(List(TermPath.Package("myapp"), TermPath.Term("obj")))
      assertTrue(path.segments.size == 2) &&
      assertTrue(path.segments.head == TermPath.Package("myapp")) &&
      assertTrue(path.segments(1) == TermPath.Term("obj"))
    },
    test("empty path") {
      val path = TermPath(Nil)
      assertTrue(path.segments.isEmpty)
    },
    test("deeply nested path") {
      val path = TermPath(
        List(
          TermPath.Package("com"),
          TermPath.Package("example"),
          TermPath.Term("Outer"),
          TermPath.Term("inner"),
          TermPath.Term("value")
        )
      )
      assertTrue(path.segments.size == 5)
    },
    test("TermPath.Package stores name") {
      val segment = TermPath.Package("mypackage")
      assertTrue(segment.name == "mypackage")
    },
    test("TermPath.Term stores name") {
      val segment = TermPath.Term("myTerm")
      assertTrue(segment.name == "myTerm")
    }
  )

  val builtInConstantsSuite: Spec[Any, Nothing] = suite("built-in TypeId constants")(
    test("primitive types") {
      assertTrue(TypeId.unit.name == "Unit") &&
      assertTrue(TypeId.boolean.name == "Boolean") &&
      assertTrue(TypeId.byte.name == "Byte") &&
      assertTrue(TypeId.short.name == "Short") &&
      assertTrue(TypeId.int.name == "Int") &&
      assertTrue(TypeId.long.name == "Long") &&
      assertTrue(TypeId.float.name == "Float") &&
      assertTrue(TypeId.double.name == "Double") &&
      assertTrue(TypeId.char.name == "Char")
    },
    test("primitive types have scala owner") {
      assertTrue(TypeId.int.owner.asString == "scala") &&
      assertTrue(TypeId.boolean.owner.asString == "scala") &&
      assertTrue(TypeId.unit.owner.asString == "scala")
    },
    test("string has java.lang owner") {
      assertTrue(TypeId.string.name == "String") &&
      assertTrue(TypeId.string.owner.asString == "java.lang")
    },
    test("numeric types") {
      assertTrue(TypeId.bigInt.name == "BigInt") &&
      assertTrue(TypeId.bigInt.owner.asString == "scala") &&
      assertTrue(TypeId.bigDecimal.name == "BigDecimal") &&
      assertTrue(TypeId.bigDecimal.owner.asString == "scala")
    },
    test("java.time types") {
      assertTrue(TypeId.dayOfWeek.name == "DayOfWeek") &&
      assertTrue(TypeId.dayOfWeek.owner.asString == "java.time") &&
      assertTrue(TypeId.duration.name == "Duration") &&
      assertTrue(TypeId.instant.name == "Instant") &&
      assertTrue(TypeId.localDate.name == "LocalDate") &&
      assertTrue(TypeId.localDateTime.name == "LocalDateTime") &&
      assertTrue(TypeId.localTime.name == "LocalTime") &&
      assertTrue(TypeId.month.name == "Month") &&
      assertTrue(TypeId.monthDay.name == "MonthDay") &&
      assertTrue(TypeId.offsetDateTime.name == "OffsetDateTime") &&
      assertTrue(TypeId.offsetTime.name == "OffsetTime") &&
      assertTrue(TypeId.period.name == "Period") &&
      assertTrue(TypeId.year.name == "Year") &&
      assertTrue(TypeId.yearMonth.name == "YearMonth") &&
      assertTrue(TypeId.zoneId.name == "ZoneId") &&
      assertTrue(TypeId.zoneOffset.name == "ZoneOffset") &&
      assertTrue(TypeId.zonedDateTime.name == "ZonedDateTime")
    },
    test("java.util types") {
      assertTrue(TypeId.currency.name == "Currency") &&
      assertTrue(TypeId.currency.owner.asString == "java.util") &&
      assertTrue(TypeId.uuid.name == "UUID") &&
      assertTrue(TypeId.uuid.owner.asString == "java.util")
    },
    test("option types") {
      assertTrue(TypeId.none.name == "None") &&
      assertTrue(TypeId.none.arity == 0) &&
      assertTrue(TypeId.some.name == "Some") &&
      assertTrue(TypeId.some.arity == 1) &&
      assertTrue(TypeId.option.name == "Option") &&
      assertTrue(TypeId.option.arity == 1)
    },
    test("collection types") {
      assertTrue(TypeId.list.name == "List") &&
      assertTrue(TypeId.list.arity == 1) &&
      assertTrue(TypeId.list.owner.asString == "scala.collection.immutable") &&
      assertTrue(TypeId.map.name == "Map") &&
      assertTrue(TypeId.map.arity == 2) &&
      assertTrue(TypeId.set.name == "Set") &&
      assertTrue(TypeId.set.arity == 1) &&
      assertTrue(TypeId.vector.name == "Vector") &&
      assertTrue(TypeId.vector.arity == 1) &&
      assertTrue(TypeId.seq.name == "Seq") &&
      assertTrue(TypeId.seq.arity == 1) &&
      assertTrue(TypeId.indexedSeq.name == "IndexedSeq") &&
      assertTrue(TypeId.arraySeq.name == "ArraySeq")
    },
    test("collection type params have correct names") {
      assertTrue(TypeId.list.typeParams.head.name == "A") &&
      assertTrue(TypeId.map.typeParams(0).name == "K") &&
      assertTrue(TypeId.map.typeParams(1).name == "V")
    }
  )

  val macroDerivedSuite: Spec[Any, Nothing] = suite("TypeId.derive macro")(
    test("derives TypeId for primitive types") {
      assertTrue(TypeId.derive[Int].name == "Int") &&
      assertTrue(TypeId.derive[String].name == "String") &&
      assertTrue(TypeId.derive[Boolean].name == "Boolean") &&
      assertTrue(TypeId.derive[Long].name == "Long") &&
      assertTrue(TypeId.derive[Double].name == "Double") &&
      assertTrue(TypeId.derive[Float].name == "Float") &&
      assertTrue(TypeId.derive[Char].name == "Char") &&
      assertTrue(TypeId.derive[Byte].name == "Byte") &&
      assertTrue(TypeId.derive[Short].name == "Short") &&
      assertTrue(TypeId.derive[Unit].name == "Unit")
    },
    test("derives TypeId for BigInt and BigDecimal") {
      assertTrue(TypeId.derive[BigInt].name == "BigInt") &&
      assertTrue(TypeId.derive[BigDecimal].name == "BigDecimal")
    },
    test("derives TypeId for java.time types") {
      assertTrue(TypeId.derive[java.time.LocalDate].name == "LocalDate") &&
      assertTrue(TypeId.derive[java.time.LocalDateTime].name == "LocalDateTime") &&
      assertTrue(TypeId.derive[java.time.Instant].name == "Instant") &&
      assertTrue(TypeId.derive[java.time.Duration].name == "Duration") &&
      assertTrue(TypeId.derive[java.time.ZonedDateTime].name == "ZonedDateTime")
    },
    test("derives TypeId for java.util types") {
      assertTrue(TypeId.derive[java.util.UUID].name == "UUID") &&
      assertTrue(TypeId.derive[java.util.Currency].name == "Currency")
    },
    test("derives TypeId for Option with correct arity") {
      val id = TypeId.derive[Option[_]]
      assertTrue(id.name == "Option") &&
      assertTrue(id.arity == 1)
    },
    test("derives TypeId for List with correct arity") {
      val id = TypeId.derive[List[_]]
      assertTrue(id.name == "List") &&
      assertTrue(id.arity == 1)
    },
    test("derives TypeId for Map with correct arity") {
      val id = TypeId.derive[Map[_, _]]
      assertTrue(id.name == "Map") &&
      assertTrue(id.arity == 2)
    },
    test("derives TypeId for Either with correct arity") {
      val id = TypeId.derive[Either[_, _]]
      assertTrue(id.name == "Either") &&
      assertTrue(id.arity == 2)
    },
    test("derives TypeId for Vector") {
      val id = TypeId.derive[Vector[_]]
      assertTrue(id.name == "Vector") &&
      assertTrue(id.arity == 1)
    },
    test("derives TypeId for Set") {
      val id = TypeId.derive[Set[_]]
      assertTrue(id.name == "Set") &&
      assertTrue(id.arity == 1)
    },
    test("derives TypeId for custom case class") {
      val id = TypeId.derive[Person]
      assertTrue(id.name == "Person")
    },
    test("derives TypeId for case class with type params") {
      val id = TypeId.derive[Box[_]]
      assertTrue(id.name == "Box") &&
      assertTrue(id.arity == 1)
    },
    test("derives TypeId for case class with multiple type params") {
      val id = TypeId.derive[Pair[_, _]]
      assertTrue(id.name == "Pair") &&
      assertTrue(id.arity == 2)
    },
    test("derives TypeId for sealed trait") {
      val id = TypeId.derive[TestSealedTrait]
      assertTrue(id.name == "TestSealedTrait")
    },
    test("derives TypeId for case class extending sealed trait") {
      val id = TypeId.derive[TestCaseClass1]
      assertTrue(id.name == "TestCaseClass1")
    },
    test("derives TypeId for case object") {
      val id = TypeId.derive[TestCaseObject.type]
      assertTrue(id.name == "TestCaseObject" || id.name == "TestCaseObject$")
    },
    test("derives TypeId for trait with type params") {
      val id = TypeId.derive[TestTrait[_, _]]
      assertTrue(id.name == "TestTrait") &&
      assertTrue(id.arity == 2)
    },
    test("derives TypeId for generic record with three type params") {
      val id = TypeId.derive[GenericRecord[_, _, _]]
      assertTrue(id.name == "GenericRecord") &&
      assertTrue(id.arity == 3)
    },
    test("derived TypeId has correct name for standard library types") {
      val intId    = TypeId.derive[Int]
      val stringId = TypeId.derive[String]
      val listId   = TypeId.derive[List[_]]
      assertTrue(intId.name == "Int") &&
      assertTrue(stringId.name == "String") &&
      assertTrue(listId.name == "List")
    }
  )

  val nestedTypesSuite: Spec[Any, Nothing] = suite("nested types")(
    test("derives TypeId for inner class") {
      val id = TypeId.derive[OuterClass#InnerClass]
      assertTrue(id.name == "InnerClass")
    },
    test("owner contains enclosing class for inner types") {
      val id = TypeId.derive[OuterClass#InnerClass]
      assertTrue(id.owner.asString.contains("OuterClass"))
    },
    test("derives TypeId for class in companion object") {
      val id = TypeId.derive[TestSealedTrait]
      assertTrue(id.name == "TestSealedTrait")
    }
  )

  val complexGenericsSuite: Spec[Any, Nothing] = suite("complex generic types")(
    test("derives TypeId for deeply nested generic - Option[List[Int]]") {
      val id = TypeId.derive[Option[List[Int]]]
      assertTrue(id.name == "Option")
    },
    test("derives TypeId for Map with complex value type") {
      val id = TypeId.derive[Map[String, List[Int]]]
      assertTrue(id.name == "Map")
    },
    test("derives TypeId for nested Maps") {
      val id = TypeId.derive[Map[String, Map[Int, Boolean]]]
      assertTrue(id.name == "Map")
    },
    test("derives TypeId for List of tuples") {
      val id = TypeId.derive[List[(Int, String)]]
      assertTrue(id.name == "List")
    },
    test("derives TypeId for Either with complex types") {
      val id = TypeId.derive[Either[List[Int], Option[String]]]
      assertTrue(id.name == "Either")
    },
    test("derives TypeId for Function types") {
      val id = TypeId.derive[Int => String]
      assertTrue(id.name == "Function1")
    },
    test("derives TypeId for Function2") {
      val id = TypeId.derive[(Int, String) => Boolean]
      assertTrue(id.name == "Function2")
    },
    test("derives TypeId for Tuple2") {
      val id = TypeId.derive[(Int, String)]
      assertTrue(id.name == "Tuple2")
    },
    test("derives TypeId for Tuple3") {
      val id = TypeId.derive[(Int, String, Boolean)]
      assertTrue(id.name == "Tuple3")
    },
    test("derives TypeId for Array") {
      val id = TypeId.derive[Array[Int]]
      assertTrue(id.name == "Array")
    }
  )

  val patternMatchingSuite: Spec[Any, Nothing] = suite("pattern matching extractors")(
    test("Nominal extractor matches nominal TypeId") {
      val id = TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil)
      id match {
        case TypeId.Nominal(name, owner, params) =>
          assertTrue(name == "Int") &&
          assertTrue(owner.asString == "scala") &&
          assertTrue(params.isEmpty)
        case _ =>
          assertTrue(false)
      }
    },
    test("Nominal extractor does not match alias TypeId") {
      val intRef = TypeRepr.Ref(TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil))
      val id     = TypeId.alias[Int]("Age", Owner(List(Owner.Package("myapp"))), Nil, intRef)
      id match {
        case TypeId.Nominal(_, _, _) => assertTrue(false)
        case _                       => assertTrue(true)
      }
    },
    test("Alias extractor matches alias TypeId") {
      val intRef = TypeRepr.Ref(TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil))
      val id     = TypeId.alias[Int]("Age", Owner(List(Owner.Package("myapp"))), Nil, intRef)
      id match {
        case TypeId.Alias(name, owner, params, aliased) =>
          assertTrue(name == "Age") &&
          assertTrue(owner.asString == "myapp") &&
          assertTrue(params.isEmpty) &&
          assertTrue(aliased == intRef)
        case _ =>
          assertTrue(false)
      }
    },
    test("Alias extractor does not match nominal TypeId") {
      val id = TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil)
      id match {
        case TypeId.Alias(_, _, _, _) => assertTrue(false)
        case _                        => assertTrue(true)
      }
    },
    test("Opaque extractor matches opaque TypeId") {
      val stringRef = TypeRepr.Ref(
        TypeId.nominal[String]("String", Owner(List(Owner.Package("java"), Owner.Package("lang"))), Nil)
      )
      val id = TypeId.opaque[String]("Email", Owner(List(Owner.Package("myapp"))), Nil, stringRef)
      id match {
        case TypeId.Opaque(name, owner, params, representation) =>
          assertTrue(name == "Email") &&
          assertTrue(owner.asString == "myapp") &&
          assertTrue(params.isEmpty) &&
          assertTrue(representation == stringRef)
        case _ =>
          assertTrue(false)
      }
    },
    test("Opaque extractor does not match nominal TypeId") {
      val id = TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil)
      id match {
        case TypeId.Opaque(_, _, _, _) => assertTrue(false)
        case _                         => assertTrue(true)
      }
    },
    test("Opaque extractor does not match alias TypeId") {
      val intRef = TypeRepr.Ref(TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil))
      val id     = TypeId.alias[Int]("Age", Owner(List(Owner.Package("myapp"))), Nil, intRef)
      id match {
        case TypeId.Opaque(_, _, _, _) => assertTrue(false)
        case _                         => assertTrue(true)
      }
    },
    test("exhaustive pattern matching on TypeId variants") {
      val nominal   = TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil)
      val intRef    = TypeRepr.Ref(nominal)
      val alias     = TypeId.alias[Int]("Age", Owner(List(Owner.Package("myapp"))), Nil, intRef)
      val stringRef = TypeRepr.Ref(
        TypeId.nominal[String]("String", Owner(List(Owner.Package("java"), Owner.Package("lang"))), Nil)
      )
      val opaque = TypeId.opaque[String]("Email", Owner(List(Owner.Package("myapp"))), Nil, stringRef)

      def classify(id: TypeId[_]): String = id match {
        case TypeId.Nominal(_, _, _)   => "nominal"
        case TypeId.Alias(_, _, _, _)  => "alias"
        case TypeId.Opaque(_, _, _, _) => "opaque"
        case _                         => "unknown"
      }

      assertTrue(classify(nominal) == "nominal") &&
      assertTrue(classify(alias) == "alias") &&
      assertTrue(classify(opaque) == "opaque")
    }
  )
}
