package zio.blocks.typeid

import zio.test._

/**
 * Comprehensive tests for TypeNormalization logic.
 * Covers: dealias, substitute.
 */
object TypeNormalizationSpec extends ZIOSpecDefault {

  private val scalaOwner = Owner.pkg("scala")
  private val javaLang = Owner.pkgs("java", "lang")

  private val intId = DynamicTypeId(scalaOwner, "Int", Nil, TypeDefKind.Class(isFinal = true, isValue = true), Nil)
  private val stringId = DynamicTypeId(javaLang, "String", Nil, TypeDefKind.Class(isFinal = true), Nil)

  private def makeAlias(name: String, alias: TypeRepr, params: List[TypeParam] = Nil): DynamicTypeId =
    DynamicTypeId(scalaOwner, name, params, TypeDefKind.TypeAlias(alias), Nil)

  def spec: Spec[TestEnvironment, Any] = suite("TypeNormalizationSpec")(
    suite("dealias")(
      test("non-alias type returns unchanged") {
        val tpe = TypeRepr.Ref(intId, Nil)
        val result = TypeNormalization.dealias(tpe)
        assertTrue(result == tpe)
      },
      test("simple alias is expanded") {
        val aliasId = makeAlias("MyInt", TypeRepr.Ref(intId, Nil))
        val tpe = TypeRepr.Ref(aliasId, Nil)
        val result = TypeNormalization.dealias(tpe)
        assertTrue(result == TypeRepr.Ref(intId, Nil))
      },
      test("chained aliases are fully expanded") {
        val alias1 = makeAlias("Alias1", TypeRepr.Ref(intId, Nil))
        val alias2 = makeAlias("Alias2", TypeRepr.Ref(alias1, Nil))
        val tpe = TypeRepr.Ref(alias2, Nil)
        val result = TypeNormalization.dealias(tpe)
        assertTrue(result == TypeRepr.Ref(intId, Nil))
      },
      test("parameterized alias substitutes type args") {
        val paramA = TypeParam("A", 0, Variance.Invariant)
        val aliasId = makeAlias(
          "Identity",
          TypeRepr.TypeParamRef("A", 0),
          List(paramA)
        )
        val tpe = TypeRepr.Ref(aliasId, List(TypeRepr.Ref(stringId, Nil)))
        val result = TypeNormalization.dealias(tpe)
        assertTrue(result == TypeRepr.Ref(stringId, Nil))
      },
      test("alias with mismatched arg count returns unchanged") {
        val paramA = TypeParam("A", 0, Variance.Invariant)
        val aliasId = makeAlias(
          "Identity",
          TypeRepr.TypeParamRef("A", 0),
          List(paramA)
        )
        // No args provided, but alias expects 1
        val tpe = TypeRepr.Ref(aliasId, Nil)
        val result = TypeNormalization.dealias(tpe)
        assertTrue(result == tpe)
      },
      test("AppliedType dealiases base and args") {
        val aliasId = makeAlias("MyInt", TypeRepr.Ref(intId, Nil))
        val applied = TypeRepr.AppliedType(
          TypeRepr.Ref(aliasId, Nil),
          List(TypeRepr.Ref(aliasId, Nil))
        )
        val result = TypeNormalization.dealias(applied).asInstanceOf[TypeRepr.AppliedType]
        assertTrue(
          result.tpe == TypeRepr.Ref(intId, Nil),
          result.args.head == TypeRepr.Ref(intId, Nil)
        )
      },
      test("other TypeRepr variants return unchanged") {
        val union = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil)))
        val result = TypeNormalization.dealias(union)
        assertTrue(result == union)
      }
    ),
    suite("substitute")(
      test("empty params returns unchanged") {
        val tpe = TypeRepr.TypeParamRef("A", 0)
        val result = TypeNormalization.substitute(tpe, Nil, Nil)
        assertTrue(result == tpe)
      },
      test("empty args returns unchanged") {
        val tpe = TypeRepr.TypeParamRef("A", 0)
        val params = List(TypeParam("A", 0, Variance.Invariant))
        val result = TypeNormalization.substitute(tpe, params, Nil)
        assertTrue(result == tpe)
      },
      test("TypeParamRef is substituted") {
        val tpe = TypeRepr.TypeParamRef("A", 0)
        val params = List(TypeParam("A", 0, Variance.Invariant))
        val args = List(TypeRepr.Ref(intId, Nil))
        val result = TypeNormalization.substitute(tpe, params, args)
        assertTrue(result == TypeRepr.Ref(intId, Nil))
      },
      test("unmatched TypeParamRef returns unchanged") {
        val tpe = TypeRepr.TypeParamRef("B", 1)
        val params = List(TypeParam("A", 0, Variance.Invariant))
        val args = List(TypeRepr.Ref(intId, Nil))
        val result = TypeNormalization.substitute(tpe, params, args)
        assertTrue(result == tpe)
      },
      test("Ref with args substitutes recursively") {
        val listId = DynamicTypeId(
          Owner.pkgs("scala", "collection", "immutable"),
          "List",
          List(TypeParam("A", 0, Variance.Covariant)),
          TypeDefKind.Trait(isSealed = true),
          Nil
        )
        val tpe = TypeRepr.Ref(listId, List(TypeRepr.TypeParamRef("X", 0)))
        val params = List(TypeParam("X", 0, Variance.Invariant))
        val args = List(TypeRepr.Ref(stringId, Nil))
        val result = TypeNormalization.substitute(tpe, params, args).asInstanceOf[TypeRepr.Ref]
        assertTrue(result.args.head == TypeRepr.Ref(stringId, Nil))
      },
      test("AppliedType substitutes recursively") {
        val applied = TypeRepr.AppliedType(
          TypeRepr.TypeParamRef("F", 0),
          List(TypeRepr.TypeParamRef("A", 1))
        )
        val params = List(
          TypeParam("F", 0, Variance.Invariant),
          TypeParam("A", 1, Variance.Invariant)
        )
        val args = List(
          TypeRepr.Ref(intId, Nil),
          TypeRepr.Ref(stringId, Nil)
        )
        val result = TypeNormalization.substitute(applied, params, args).asInstanceOf[TypeRepr.AppliedType]
        assertTrue(
          result.tpe == TypeRepr.Ref(intId, Nil),
          result.args.head == TypeRepr.Ref(stringId, Nil)
        )
      },
      test("Union substitutes all members") {
        val union = TypeRepr.Union(List(
          TypeRepr.TypeParamRef("A", 0),
          TypeRepr.TypeParamRef("B", 1)
        ))
        val params = List(
          TypeParam("A", 0, Variance.Invariant),
          TypeParam("B", 1, Variance.Invariant)
        )
        val args = List(
          TypeRepr.Ref(intId, Nil),
          TypeRepr.Ref(stringId, Nil)
        )
        val result = TypeNormalization.substitute(union, params, args).asInstanceOf[TypeRepr.Union]
        assertTrue(
          result.types.contains(TypeRepr.Ref(intId, Nil)),
          result.types.contains(TypeRepr.Ref(stringId, Nil))
        )
      },
      test("Intersection substitutes all members") {
        val inter = TypeRepr.Intersection(List(
          TypeRepr.TypeParamRef("A", 0),
          TypeRepr.Ref(intId, Nil)
        ))
        val params = List(TypeParam("A", 0, Variance.Invariant))
        val args = List(TypeRepr.Ref(stringId, Nil))
        val result = TypeNormalization.substitute(inter, params, args).asInstanceOf[TypeRepr.Intersection]
        assertTrue(result.types.contains(TypeRepr.Ref(stringId, Nil)))
      },
      test("Function substitutes params and result") {
        val func = TypeRepr.Function(
          List(TypeRepr.TypeParamRef("A", 0)),
          TypeRepr.TypeParamRef("B", 1)
        )
        val params = List(
          TypeParam("A", 0, Variance.Contravariant),
          TypeParam("B", 1, Variance.Covariant)
        )
        val args = List(
          TypeRepr.Ref(intId, Nil),
          TypeRepr.Ref(stringId, Nil)
        )
        val result = TypeNormalization.substitute(func, params, args).asInstanceOf[TypeRepr.Function]
        assertTrue(
          result.params.head == TypeRepr.Ref(intId, Nil),
          result.result == TypeRepr.Ref(stringId, Nil)
        )
      },
      test("Tuple substitutes all elements") {
        val tuple = TypeRepr.Tuple(List(
          TypeRepr.TypeParamRef("A", 0),
          TypeRepr.TypeParamRef("B", 1)
        ))
        val params = List(
          TypeParam("A", 0, Variance.Invariant),
          TypeParam("B", 1, Variance.Invariant)
        )
        val args = List(
          TypeRepr.Ref(intId, Nil),
          TypeRepr.Ref(stringId, Nil)
        )
        val result = TypeNormalization.substitute(tuple, params, args).asInstanceOf[TypeRepr.Tuple]
        assertTrue(
          result.elements.head == TypeRepr.Ref(intId, Nil),
          result.elements(1) == TypeRepr.Ref(stringId, Nil)
        )
      },
      test("non-substitutable types return unchanged") {
        val const = TypeRepr.ConstantType(Constant.IntConst(42))
        val params = List(TypeParam("A", 0, Variance.Invariant))
        val args = List(TypeRepr.Ref(intId, Nil))
        val result = TypeNormalization.substitute(const, params, args)
        assertTrue(result == const)
      }
    )
  )
}
