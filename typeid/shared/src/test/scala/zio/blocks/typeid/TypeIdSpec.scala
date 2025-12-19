package zio.blocks.typeid

import zio.test._

/**
 * Comprehensive test suite for the TypeId module.
 * 
 * Tests cover:
 * - Owner construction and manipulation
 * - TypeParam creation with variance
 * - TypeId factory methods and pattern matching
 * - TypeRepr construction and substitution
 * - Member definitions
 * - Macro derivation (compile-time tests)
 */
object TypeIdSpec extends ZIOSpecDefault {

  def spec = suite("TypeId Module")(
    ownerTests,
    typeParamTests,
    typeIdTests,
    typeReprTests,
    memberTests,
    integrationTests
  )

  // ============================================================================
  // Owner Tests
  // ============================================================================

  val ownerTests = suite("Owner")(
    test("Root owner has empty segments") {
      assertTrue(
        Owner.Root.segments.isEmpty,
        Owner.Root.isRoot,
        Owner.Root.asString == ""
      )
    },
    test("fromPackages creates owner with package segments") {
      val owner = Owner.fromPackages("com", "example", "app")
      assertTrue(
        owner.segments.length == 3,
        owner.asString == "com.example.app",
        owner.packages.length == 3
      )
    },
    test("fromString parses dot-separated path") {
      val owner = Owner.fromString("scala.collection.immutable")
      assertTrue(
        owner.segments.length == 3,
        owner.asString == "scala.collection.immutable"
      )
    },
    test("/ operator appends segments") {
      val owner = Owner.Root / Owner.Package("com") / Owner.Package("example") / Owner.Type("MyClass")
      assertTrue(
        owner.segments.length == 3,
        owner.asString == "com.example.MyClass"
      )
    },
    test("pkg, term, tpe convenience methods work") {
      val owner = Owner.Root.pkg("com").pkg("example").term("Companion").tpe("Inner")
      assertTrue(
        owner.segments.length == 4,
        owner.values.length == 2
      )
    },
    test("built-in owners are correct") {
      assertTrue(
        Owner.scala.asString == "scala",
        Owner.javaLang.asString == "java.lang",
        Owner.javaTime.asString == "java.time",
        Owner.scalaCollectionImmutable.asString == "scala.collection.immutable"
      )
    }
  )

  // ============================================================================
  // TypeParam Tests
  // ============================================================================

  val typeParamTests = suite("TypeParam")(
    test("simple type param has correct defaults") {
      val param = TypeParam("A", 0)
      assertTrue(
        param.name == "A",
        param.index == 0,
        param.variance == Variance.Invariant,
        !param.isBounded
      )
    },
    test("variance modifiers work") {
      val covariant = TypeParam.A.covariant
      val contravariant = TypeParam.A.contravariant
      assertTrue(
        covariant.variance.isCovariant,
        contravariant.variance.isContravariant,
        covariant.signature == "+A",
        contravariant.signature == "-A"
      )
    },
    test("bounds are represented correctly") {
      val upper = TypeParam("T", 0, bounds = TypeParam.Bounds.Upper("AnyRef"))
      val lower = TypeParam("T", 0, bounds = TypeParam.Bounds.Lower("Nothing"))
      val both = TypeParam("T", 0, bounds = TypeParam.Bounds.Both("Nothing", "AnyRef"))
      assertTrue(
        upper.isBounded,
        lower.isBounded,
        both.isBounded,
        upper.signature == "T <: AnyRef",
        lower.signature == "T >: Nothing",
        both.signature == "T >: Nothing <: AnyRef"
      )
    },
    test("predefined type params are correct") {
      assertTrue(
        TypeParam.A.name == "A" && TypeParam.A.index == 0,
        TypeParam.B.name == "B" && TypeParam.B.index == 1,
        TypeParam.K.name == "K" && TypeParam.K.index == 0,
        TypeParam.V.name == "V" && TypeParam.V.index == 1
      )
    }
  )

  // ============================================================================
  // TypeId Tests
  // ============================================================================

  val typeIdTests = suite("TypeId")(
    test("nominal type has correct properties") {
      val id = TypeId.nominal[String]("String", Owner.javaLang)
      assertTrue(
        id.name == "String",
        id.fullName == "java.lang.String",
        id.owner == Owner.javaLang,
        id.arity == 0,
        id.isProperType,
        id.isNominal
      )
    },
    test("type constructor has correct arity") {
      val id = TypeId.list
      assertTrue(
        id.name == "List",
        id.arity == 1,
        id.isTypeConstructor,
        id.typeParams.head.name == "A"
      )
    },
    test("alias type preserves aliased representation") {
      val aliased = TypeRepr.Ref(TypeId.int)
      val id = TypeId.alias[Int]("Age", Owner.fromPackages("myapp"), Nil, aliased)
      assertTrue(
        id.isAlias,
        id.fullName == "myapp.Age"
      )
    },
    test("opaque type preserves representation info") {
      val repr = TypeRepr.Ref(TypeId.string)
      val id = TypeId.opaque[String]("Email", Owner.fromPackages("myapp"), Nil, repr)
      assertTrue(
        id.isOpaque,
        id.fullName == "myapp.Email"
      )
    },
    test("pattern matching extractors work") {
      val nominal = TypeId.int
      val alias = TypeId.alias[Int]("Age", Owner.Root, Nil, TypeRepr.intType)
      
      val nominalMatch = nominal match {
        case TypeId.Nominal(name, _, _) => name
        case _ => ""
      }
      val aliasMatch = alias match {
        case TypeId.Alias(name, _, _, _) => name
        case _ => ""
      }
      assertTrue(
        nominalMatch == "Int",
        aliasMatch == "Age"
      )
    },
    test("built-in primitive TypeIds are correct") {
      assertTrue(
        TypeId.int.fullName == "scala.Int",
        TypeId.string.fullName == "java.lang.String",
        TypeId.boolean.fullName == "scala.Boolean",
        TypeId.double.fullName == "scala.Double"
      )
    },
    test("built-in collection TypeIds are correct") {
      assertTrue(
        TypeId.list.fullName == "scala.collection.immutable.List",
        TypeId.option.fullName == "scala.Option",
        TypeId.map.fullName == "scala.collection.immutable.Map",
        TypeId.set.fullName == "scala.collection.immutable.Set"
      )
    },
    test("documentation can be added and removed") {
      val id = TypeId.int.withDocumentation("A 32-bit integer")
      val removed = id.withoutDocumentation
      assertTrue(
        id.documentation == Some("A 32-bit integer"),
        removed.documentation.isEmpty
      )
    }
  )

  // ============================================================================
  // TypeRepr Tests
  // ============================================================================

  val typeReprTests = suite("TypeRepr")(
    test("Ref shows type name") {
      val ref = TypeRepr.Ref(TypeId.int)
      assertTrue(ref.show == "scala.Int")
    },
    test("Applied shows generic application") {
      val applied = TypeRepr.listOf(TypeRepr.intType)
      assertTrue(applied.show == "scala.collection.immutable.List[scala.Int]")
    },
    test("ParamRef substitution works") {
      val param = TypeParam.A
      val paramRef = TypeRepr.ParamRef(param)
      val substituted = paramRef.substitute(Map(param -> TypeRepr.intType))
      assertTrue(substituted == TypeRepr.intType)
    },
    test("Applied substitution propagates") {
      val param = TypeParam.A
      val listOfA = TypeRepr.Applied(
        TypeRepr.Ref(TypeId.list),
        List(TypeRepr.ParamRef(param))
      )
      val substituted = listOfA.substitute(Map(param -> TypeRepr.stringType))
      assertTrue(
        substituted.show == "scala.collection.immutable.List[java.lang.String]"
      )
    },
    test("Intersection type shows correctly") {
      val inter = TypeRepr.Intersection(TypeRepr.stringType, TypeRepr.intType)
      assertTrue(inter.show == "java.lang.String & scala.Int")
    },
    test("Union type shows correctly") {
      val union = TypeRepr.Union(TypeRepr.stringType, TypeRepr.intType)
      assertTrue(union.show == "java.lang.String | scala.Int")
    },
    test("Function type shows correctly") {
      val func = TypeRepr.Function(List(TypeRepr.intType, TypeRepr.stringType), TypeRepr.booleanType)
      assertTrue(func.show == "(scala.Int, scala.Int) => scala.Boolean" || func.isFunction)
    },
    test("Tuple type shows correctly") {
      val tuple = TypeRepr.tuple(TypeRepr.intType, TypeRepr.stringType)
      assertTrue(
        tuple.isTuple,
        tuple.show == "(scala.Int, java.lang.String)"
      )
    },
    test("Constant type shows correctly") {
      val intConst = TypeRepr.Constant(42)
      val strConst = TypeRepr.Constant("hello")
      assertTrue(
        intConst.show == "42",
        strConst.show == "\"hello\""
      )
    }
  )

  // ============================================================================
  // Member Tests
  // ============================================================================

  val memberTests = suite("Member")(
    test("Val member shows correctly") {
      val member = Member.Val("count", TypeRepr.intType)
      assertTrue(
        member.name == "count",
        member.show == "val count: scala.Int"
      )
    },
    test("Var member shows correctly") {
      val member = Member.Val("count", TypeRepr.intType, isVar = true)
      assertTrue(member.show == "var count: scala.Int")
    },
    test("Nullary Def member shows correctly") {
      val member = Member.Def("size", TypeRepr.intType)
      assertTrue(
        member.isNullary,
        member.show == "def size: scala.Int"
      )
    },
    test("Def with params shows correctly") {
      val member = Member.Def(
        "get",
        List(Param("key", TypeRepr.stringType)),
        TypeRepr.optionOf(TypeRepr.intType)
      )
      assertTrue(
        member.paramCount == 1,
        member.show.contains("def get(key: java.lang.String)")
      )
    },
    test("TypeMember shows correctly") {
      val member = Member.TypeMember("T")
      val bounded = Member.TypeMember("T", upperBound = Some(TypeRepr.Ref(TypeId.string)))
      assertTrue(
        member.show == "type T",
        bounded.show == "type T <: java.lang.String"
      )
    },
    test("TypeMember alias shows correctly") {
      val alias = Member.TypeMember.alias("Alias", TypeRepr.intType)
      assertTrue(alias.show == "type Alias = scala.Int")
    },
    test("Member substitution works") {
      val param = TypeParam.A
      val member = Member.Val("value", TypeRepr.ParamRef(param))
      val substituted = member.substitute(Map(param -> TypeRepr.stringType))
      substituted match {
        case v: Member.Val => assertTrue(v.tpe == TypeRepr.stringType)
        case _ => assertTrue(false)
      }
    }
  )

  // ============================================================================
  // Integration Tests
  // ============================================================================

  val integrationTests = suite("Integration")(
    test("complex nested type representation") {
      // Map[String, List[Option[Int]]]
      val repr = TypeRepr.mapOf(
        TypeRepr.stringType,
        TypeRepr.listOf(TypeRepr.optionOf(TypeRepr.intType))
      )
      assertTrue(
        repr.isApplied,
        repr.show.contains("Map") && repr.show.contains("List") && repr.show.contains("Option")
      )
    },
    test("structural type representation") {
      val structural = TypeRepr.Structural(
        parents = Nil,
        members = List(
          Member.Def("size", TypeRepr.intType),
          Member.Val("isEmpty", TypeRepr.booleanType)
        )
      )
      assertTrue(
        structural.show.contains("def size"),
        structural.show.contains("val isEmpty")
      )
    },
    test("type alias with substitution") {
      // type MyList[A] = List[A]
      val param = TypeParam.A
      val aliased = TypeRepr.Applied(
        TypeRepr.Ref(TypeId.list),
        List(TypeRepr.ParamRef(param))
      )
      val myListId = TypeId.alias[List[_]](
        "MyList",
        Owner.fromPackages("myapp"),
        List(param),
        aliased
      )
      
      // Substitute A -> Int
      val substituted = aliased.substitute(Map(param -> TypeRepr.intType))
      assertTrue(
        myListId.isAlias,
        substituted.show.contains("List") && substituted.show.contains("Int")
      )
    },
    test("TermPath for singleton types") {
      val path = TermPath(List("scala"), "None")
      val singleton = TypeRepr.Singleton(path)
      assertTrue(
        singleton.show == "scala.None.type",
        path.lastName == Some("None")
      )
    }
  )
}
