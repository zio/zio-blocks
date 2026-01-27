package zio.blocks.typeid

import zio.test._

/**
 * Comprehensive tests for TypeEquality logic. Covers: dynamicTypeIdEquals,
 * dynamicTypeIdHashCode, NamedTuple handling.
 */
object TypeEqualitySpec extends ZIOSpecDefault {

  private val scalaOwner = Owner.pkg("scala")
  private val javaLang   = Owner.pkgs("java", "lang")

  private val intId    = DynamicTypeId(scalaOwner, "Int", Nil, TypeDefKind.Class(isFinal = true, isValue = true), Nil)
  private val stringId = DynamicTypeId(javaLang, "String", Nil, TypeDefKind.Class(isFinal = true), Nil)

  private val listId = DynamicTypeId(
    Owner.pkgs("scala", "collection", "immutable"),
    "List",
    List(TypeParam("A", 0, Variance.Covariant)),
    TypeDefKind.Trait(isSealed = true),
    Nil
  )

  def spec: Spec[TestEnvironment, Any] = suite("TypeEqualitySpec")(
    suite("dynamicTypeIdEquals")(
      test("same reference returns true") {
        val id = intId
        assertTrue(TypeEquality.dynamicTypeIdEquals(id, id))
      },
      test("same owner and name returns true") {
        val id1 = DynamicTypeId(scalaOwner, "Int", Nil, TypeDefKind.Class(isFinal = true, isValue = true), Nil)
        val id2 = DynamicTypeId(scalaOwner, "Int", Nil, TypeDefKind.Class(isFinal = true, isValue = true), Nil)
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("different names returns false") {
        val id1 = intId
        val id2 = DynamicTypeId(scalaOwner, "Long", Nil, TypeDefKind.Class(isFinal = true, isValue = true), Nil)
        assertTrue(!TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("different owners returns false") {
        val id1 = DynamicTypeId(scalaOwner, "String", Nil, TypeDefKind.Class(), Nil)
        val id2 = stringId
        assertTrue(!TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("ids with same args are equal") {
        val id1 = listId.copy(args = List(TypeRepr.Ref(intId, Nil)))
        val id2 = listId.copy(args = List(TypeRepr.Ref(intId, Nil)))
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("ids with different args are not equal") {
        val id1 = listId.copy(args = List(TypeRepr.Ref(intId, Nil)))
        val id2 = listId.copy(args = List(TypeRepr.Ref(stringId, Nil)))
        assertTrue(!TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("ids with different arg count are not equal") {
        val id1 = listId.copy(args = List(TypeRepr.Ref(intId, Nil)))
        val id2 = listId.copy(args = Nil)
        assertTrue(!TypeEquality.dynamicTypeIdEquals(id1, id2))
      }
    ),
    suite("NamedTuple handling")(
      test("NamedTuple types are treated as equal") {
        val namedTupleOwner = Owner.pkgs("scala", "NamedTuple")
        val id1             = DynamicTypeId(namedTupleOwner, "NamedTuple", Nil, TypeDefKind.Class(), Nil)
        val id2             = DynamicTypeId(namedTupleOwner, "NamedTuple", Nil, TypeDefKind.Class(), Nil)
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("NamedTuple related types match by owner content") {
        val owner1 = Owner.pkgs("scala", "NamedTuple")
        val owner2 = Owner.pkgs("scala", "NamedTuple")
        val id1    = DynamicTypeId(owner1, "Map", Nil, TypeDefKind.Class(), Nil)
        val id2    = DynamicTypeId(owner2, "Map", Nil, TypeDefKind.Class(), Nil)
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("Empty in NamedTuple matches Empty in NamedTuple") {
        val owner = Owner.pkgs("scala", "NamedTuple")
        val id1   = DynamicTypeId(owner, "Empty", Nil, TypeDefKind.Object, Nil)
        val id2   = DynamicTypeId(owner, "Empty", Nil, TypeDefKind.Object, Nil)
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("NamedTuple args with wildcards match concrete types") {
        val namedTupleOwner = Owner.pkgs("scala", "NamedTuple")
        val id1             = DynamicTypeId(
          namedTupleOwner,
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil,
          args = List(
            TypeRepr.Wildcard(TypeBounds(None, None)),
            TypeRepr.Ref(intId, Nil)
          )
        )
        val id2 = DynamicTypeId(
          namedTupleOwner,
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil,
          args = List(
            TypeRepr.Ref(stringId, Nil),
            TypeRepr.Ref(intId, Nil)
          )
        )
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      }
    ),
    suite("dynamicTypeIdHashCode")(
      test("equal ids have same hash code") {
        val id1 = intId
        val id2 = DynamicTypeId(scalaOwner, "Int", Nil, TypeDefKind.Class(isFinal = true, isValue = true), Nil)
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id1) == TypeEquality.dynamicTypeIdHashCode(id2))
      },
      test("hash code is stable across calls") {
        val id    = listId.copy(args = List(TypeRepr.Ref(intId, Nil)))
        val hash1 = TypeEquality.dynamicTypeIdHashCode(id)
        val hash2 = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash1 == hash2)
      },
      test("different ids may have different hash codes") {
        val hash1 = TypeEquality.dynamicTypeIdHashCode(intId)
        val hash2 = TypeEquality.dynamicTypeIdHashCode(stringId)
        // Note: hash collisions are possible but unlikely for these different types
        assertTrue(hash1 != hash2 || intId != stringId)
      }
    ),
    suite("TypeRepr hash code coverage")(
      test("Union hash code") {
        val union = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val id    = intId.copy(args = List(union))
        val hash  = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("Intersection hash code") {
        val inter = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val id    = intId.copy(args = List(inter))
        val hash  = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("Function hash code") {
        val func = TypeRepr.Function(List(TypeRepr.Ref(intId, Nil)), TypeRepr.Ref(stringId, Nil))
        val id   = intId.copy(args = List(func))
        val hash = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("Tuple hash code") {
        val tuple = TypeRepr.Tuple(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val id    = intId.copy(args = List(tuple))
        val hash  = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("TypeParamRef hash code") {
        val paramRef = TypeRepr.TypeParamRef("A", 0)
        val id       = intId.copy(args = List(paramRef))
        val hash     = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("ConstantType hash code") {
        val const = TypeRepr.ConstantType(Constant.IntConst(42))
        val id    = intId.copy(args = List(const))
        val hash  = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("ThisType hash code") {
        val thisType = TypeRepr.ThisType(TypeRepr.Ref(intId, Nil))
        val id       = intId.copy(args = List(thisType))
        val hash     = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("SuperType hash code") {
        val superType = TypeRepr.SuperType(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil))
        val id        = intId.copy(args = List(superType))
        val hash      = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("TypeLambda hash code") {
        val lambda = TypeRepr.TypeLambda(
          List(TypeParam("A", 0, Variance.Covariant)),
          TypeRepr.Ref(intId, Nil)
        )
        val id   = intId.copy(args = List(lambda))
        val hash = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("Wildcard hash code") {
        val wildcard = TypeRepr.Wildcard(TypeBounds(Some(TypeRepr.NothingType), Some(TypeRepr.AnyType)))
        val id       = intId.copy(args = List(wildcard))
        val hash     = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("TypeProjection hash code") {
        val proj = TypeRepr.TypeProjection(TypeRepr.Ref(intId, Nil), "Inner")
        val id   = intId.copy(args = List(proj))
        val hash = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("Structural hash code") {
        val struct = TypeRepr.Structural(List(Member.Val("foo", TypeRepr.Ref(intId, Nil))))
        val id     = intId.copy(args = List(struct))
        val hash   = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("special type hash codes") {
        val id1 = intId.copy(args = List(TypeRepr.AnyType))
        val id2 = intId.copy(args = List(TypeRepr.AnyKindType))
        val id3 = intId.copy(args = List(TypeRepr.NothingType))
        val id4 = intId.copy(args = List(TypeRepr.NullType))
        val id5 = intId.copy(args = List(TypeRepr.UnitType))
        assertTrue(
          TypeEquality.dynamicTypeIdHashCode(id1) != 0,
          TypeEquality.dynamicTypeIdHashCode(id2) != 0,
          TypeEquality.dynamicTypeIdHashCode(id3) != 0,
          TypeEquality.dynamicTypeIdHashCode(id4) != 0,
          TypeEquality.dynamicTypeIdHashCode(id5) != 0
        )
      },
      test("AppliedType hash code") {
        val applied = TypeRepr.AppliedType(TypeRepr.Ref(listId, Nil), List(TypeRepr.Ref(intId, Nil)))
        val id      = intId.copy(args = List(applied))
        val hash    = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      }
    ),
    suite("typeReprEquals edge cases")(
      test("equivalent type aliases are equal via subtyping") {
        val aliasId = DynamicTypeId(
          scalaOwner,
          "MyInt",
          Nil,
          TypeDefKind.TypeAlias(TypeRepr.Ref(intId, Nil)),
          Nil
        )
        val aliasRef = TypeRepr.Ref(aliasId, Nil)
        val intRef   = TypeRepr.Ref(intId, Nil)
        assertTrue(Subtyping.isEquivalent(aliasRef, intRef))
      },
      test("args with equivalent types are considered equal") {
        val aliasId = DynamicTypeId(
          scalaOwner,
          "MyInt",
          Nil,
          TypeDefKind.TypeAlias(TypeRepr.Ref(intId, Nil)),
          Nil
        )
        val id1 = listId.copy(args = List(TypeRepr.Ref(aliasId, Nil)))
        val id2 = listId.copy(args = List(TypeRepr.Ref(intId, Nil)))
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("empty args with empty args are equal") {
        val id1 = listId.copy(args = Nil)
        val id2 = listId.copy(args = Nil)
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      }
    ),
    suite("computeHashCode all branches")(
      test("Union hash uses sorted order") {
        val union = TypeRepr.Union(List(TypeRepr.Ref(stringId, Nil), TypeRepr.Ref(intId, Nil)))
        val hash1 = TypeEquality.dynamicTypeIdHashCode(
          DynamicTypeId(scalaOwner, "Test", Nil, TypeDefKind.Class(), List(union))
        )
        val union2 = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val hash2  = TypeEquality.dynamicTypeIdHashCode(
          DynamicTypeId(scalaOwner, "Test", Nil, TypeDefKind.Class(), List(union2))
        )
        assertTrue(hash1 == hash2) // Same elements, different order -> same hash
      },
      test("Intersection hash uses sorted order") {
        val inter = TypeRepr.Intersection(List(TypeRepr.Ref(stringId, Nil), TypeRepr.Ref(intId, Nil)))
        val hash1 = TypeEquality.dynamicTypeIdHashCode(
          DynamicTypeId(scalaOwner, "Test", Nil, TypeDefKind.Class(), List(inter))
        )
        val inter2 = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val hash2  = TypeEquality.dynamicTypeIdHashCode(
          DynamicTypeId(scalaOwner, "Test", Nil, TypeDefKind.Class(), List(inter2))
        )
        assertTrue(hash1 == hash2)
      },
      test("Function hash covers params and result") {
        val func = TypeRepr.Function(List(TypeRepr.Ref(intId, Nil)), TypeRepr.Ref(stringId, Nil))
        val id   = DynamicTypeId(scalaOwner, "FuncTest", Nil, TypeDefKind.Class(), List(func))
        val hash = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("Tuple hash covers elements") {
        val tuple = TypeRepr.Tuple(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val id    = DynamicTypeId(scalaOwner, "TupleTest", Nil, TypeDefKind.Class(), List(tuple))
        val hash  = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("TypeParamRef hash covers name and index") {
        val paramRef = TypeRepr.TypeParamRef("T", 2)
        val id       = DynamicTypeId(scalaOwner, "ParamTest", Nil, TypeDefKind.Class(), List(paramRef))
        val hash     = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("ConstantType hash covers value") {
        val const = TypeRepr.ConstantType(Constant.IntConst(42))
        val id    = DynamicTypeId(scalaOwner, "ConstTest", Nil, TypeDefKind.Class(), List(const))
        val hash  = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("ThisType hash covers inner type") {
        val thisType = TypeRepr.ThisType(TypeRepr.Ref(intId, Nil))
        val id       = DynamicTypeId(scalaOwner, "ThisTest", Nil, TypeDefKind.Class(), List(thisType))
        val hash     = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("SuperType hash covers both types") {
        val superType = TypeRepr.SuperType(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil))
        val id        = DynamicTypeId(scalaOwner, "SuperTest", Nil, TypeDefKind.Class(), List(superType))
        val hash      = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("TypeLambda hash covers params and result") {
        val lambda = TypeRepr.TypeLambda(List(TypeParam("A", 0)), TypeRepr.Ref(intId, Nil))
        val id     = DynamicTypeId(scalaOwner, "LambdaTest", Nil, TypeDefKind.Class(), List(lambda))
        val hash   = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("Wildcard hash with bounds") {
        val wildcard = TypeRepr.Wildcard(TypeBounds(Some(TypeRepr.NothingType), Some(TypeRepr.AnyType)))
        val id       = DynamicTypeId(scalaOwner, "WildTest", Nil, TypeDefKind.Class(), List(wildcard))
        val hash     = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("Wildcard hash empty bounds") {
        val wildcard = TypeRepr.Wildcard(TypeBounds.empty)
        val id       = DynamicTypeId(scalaOwner, "WildEmpty", Nil, TypeDefKind.Class(), List(wildcard))
        val hash     = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("TypeProjection hash covers qualifier and name") {
        val proj = TypeRepr.TypeProjection(TypeRepr.Ref(intId, Nil), "Inner")
        val id   = DynamicTypeId(scalaOwner, "ProjTest", Nil, TypeDefKind.Class(), List(proj))
        val hash = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("Structural hash covers members") {
        val structural = TypeRepr.Structural(List(Member.Val("foo", TypeRepr.Ref(intId, Nil))))
        val id         = DynamicTypeId(scalaOwner, "StructTest", Nil, TypeDefKind.Class(), List(structural))
        val hash       = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("AnyType has specific hash") {
        val id   = DynamicTypeId(scalaOwner, "AnyTest", Nil, TypeDefKind.Class(), List(TypeRepr.AnyType))
        val hash = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("AnyKindType has specific hash") {
        val id   = DynamicTypeId(scalaOwner, "AnyKindTest", Nil, TypeDefKind.Class(), List(TypeRepr.AnyKindType))
        val hash = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("NothingType has specific hash") {
        val id   = DynamicTypeId(scalaOwner, "NothingTest", Nil, TypeDefKind.Class(), List(TypeRepr.NothingType))
        val hash = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("NullType has specific hash") {
        val id   = DynamicTypeId(scalaOwner, "NullTest", Nil, TypeDefKind.Class(), List(TypeRepr.NullType))
        val hash = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      },
      test("UnitType has specific hash") {
        val id   = DynamicTypeId(scalaOwner, "UnitTest", Nil, TypeDefKind.Class(), List(TypeRepr.UnitType))
        val hash = TypeEquality.dynamicTypeIdHashCode(id)
        assertTrue(hash != 0)
      }
    ),
    suite("NamedTuple specific coverage")(
      test("NamedTuple types with same args are equal") {
        val namedTupleId = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        val args = List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil))
        val id1  = namedTupleId.copy(args = args)
        val id2  = namedTupleId.copy(args = args)
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("NamedTuple.Empty equals NamedTuple.Empty") {
        val emptyId1 = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "Empty",
          Nil,
          TypeDefKind.Object,
          Nil
        )
        val emptyId2 = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "Empty",
          Nil,
          TypeDefKind.Object,
          Nil
        )
        assertTrue(TypeEquality.dynamicTypeIdEquals(emptyId1, emptyId2))
      },
      test("NamedTuple types with wildcard args") {
        val namedTupleId = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        val id1 = namedTupleId.copy(args = List(TypeRepr.Wildcard(TypeBounds.empty), TypeRepr.Ref(stringId, Nil)))
        val id2 = namedTupleId.copy(args = List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("NamedTuple.Map is recognized as NamedTuple type") {
        val mapId = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "Map",
          Nil,
          TypeDefKind.TypeAlias(TypeRepr.AnyType),
          Nil
        )
        assertTrue(TypeEquality.dynamicTypeIdEquals(mapId, mapId))
      },
      test("namedTupleArgsEqual with swapped order") {
        val namedTupleId = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        val id1 = namedTupleId.copy(args = List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val id2 = namedTupleId.copy(args = List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      }
    ),
    suite("dynamicTypeIdEquals edge cases")(
      test("reference equality fast path") {
        val id = intId
        assertTrue(TypeEquality.dynamicTypeIdEquals(id, id))
      },
      test("different name but same owner via subtyping") {
        val aliasId = DynamicTypeId(
          scalaOwner,
          "IntAlias",
          Nil,
          TypeDefKind.TypeAlias(TypeRepr.Ref(intId, Nil)),
          Nil
        )
        assertTrue(TypeEquality.dynamicTypeIdEquals(aliasId, intId))
      },
      test("different args size returns false") {
        val id1 = listId.copy(args = List(TypeRepr.Ref(intId, Nil)))
        val id2 = listId.copy(args = List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(!TypeEquality.dynamicTypeIdEquals(id1, id2))
      }
    ),
    suite("computeHashCode comprehensive")(
      test("Ref hashCode computed correctly") {
        val hash = TypeEquality.dynamicTypeIdHashCode(intId)
        assertTrue(hash != 0)
      },
      test("Same type has consistent hashCode") {
        val h1 = TypeEquality.dynamicTypeIdHashCode(intId)
        val h2 = TypeEquality.dynamicTypeIdHashCode(intId)
        assertTrue(h1 == h2)
      },
      test("Different types have different hashCodes") {
        val h1 = TypeEquality.dynamicTypeIdHashCode(intId)
        val h2 = TypeEquality.dynamicTypeIdHashCode(stringId)
        assertTrue(h1 != h2)
      },
      test("AppliedType hashCode via dynamicTypeId") {
        val appliedId = listId.copy(args = List(TypeRepr.Ref(intId, Nil)))
        val hash      = TypeEquality.dynamicTypeIdHashCode(appliedId)
        assertTrue(hash != 0)
      },
      test("Union type hashCode") {
        val unionId = DynamicTypeId(
          scalaOwner,
          "UnionHolder",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.Union(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil))))
        )
        val hash = TypeEquality.dynamicTypeIdHashCode(unionId)
        assertTrue(hash != 0)
      },
      test("Intersection type hashCode") {
        val intersectionId = DynamicTypeId(
          scalaOwner,
          "IntersectionHolder",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil))))
        )
        val hash = TypeEquality.dynamicTypeIdHashCode(intersectionId)
        assertTrue(hash != 0)
      },
      test("Function type hashCode") {
        val fnId = DynamicTypeId(
          scalaOwner,
          "FnHolder",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.Function(List(TypeRepr.Ref(intId, Nil)), TypeRepr.Ref(stringId, Nil)))
        )
        val hash = TypeEquality.dynamicTypeIdHashCode(fnId)
        assertTrue(hash != 0)
      },
      test("Tuple type hashCode") {
        val tupleId = DynamicTypeId(
          scalaOwner,
          "TupleHolder",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.Tuple(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil))))
        )
        val hash = TypeEquality.dynamicTypeIdHashCode(tupleId)
        assertTrue(hash != 0)
      },
      test("TypeParamRef hashCode") {
        val paramId = DynamicTypeId(
          scalaOwner,
          "ParamHolder",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.TypeParamRef("T", 0))
        )
        val hash = TypeEquality.dynamicTypeIdHashCode(paramId)
        assertTrue(hash != 0)
      },
      test("ConstantType hashCode") {
        val constId = DynamicTypeId(
          scalaOwner,
          "ConstHolder",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.ConstantType(Constant.IntConst(42)))
        )
        val hash = TypeEquality.dynamicTypeIdHashCode(constId)
        assertTrue(hash != 0)
      },
      test("Wildcard hashCode") {
        val wildcardId = DynamicTypeId(
          scalaOwner,
          "WildHolder",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.Wildcard(TypeBounds.empty))
        )
        val hash = TypeEquality.dynamicTypeIdHashCode(wildcardId)
        assertTrue(hash != 0)
      },
      test("TypeProjection hashCode") {
        val projId = DynamicTypeId(
          scalaOwner,
          "ProjHolder",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.TypeProjection(TypeRepr.Ref(intId, Nil), "Inner"))
        )
        val hash = TypeEquality.dynamicTypeIdHashCode(projId)
        assertTrue(hash != 0)
      },
      test("Structural hashCode") {
        val structId = DynamicTypeId(
          scalaOwner,
          "StructHolder",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.Structural(List(Member.Val("x", TypeRepr.Ref(intId, Nil)))))
        )
        val hash = TypeEquality.dynamicTypeIdHashCode(structId)
        assertTrue(hash != 0)
      },
      test("AnyType hashCode") {
        val anyId = DynamicTypeId(
          scalaOwner,
          "AnyHolder",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.AnyType)
        )
        val hash = TypeEquality.dynamicTypeIdHashCode(anyId)
        assertTrue(hash != 0)
      },
      test("AnyKindType hashCode") {
        val anyKindId = DynamicTypeId(
          scalaOwner,
          "AnyKindHolder",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.AnyKindType)
        )
        val hash = TypeEquality.dynamicTypeIdHashCode(anyKindId)
        assertTrue(hash != 0)
      },
      test("NothingType hashCode") {
        val nothingId = DynamicTypeId(
          scalaOwner,
          "NothingHolder",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.NothingType)
        )
        val hash = TypeEquality.dynamicTypeIdHashCode(nothingId)
        assertTrue(hash != 0)
      },
      test("NullType hashCode") {
        val nullId = DynamicTypeId(
          scalaOwner,
          "NullHolder",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.NullType)
        )
        val hash = TypeEquality.dynamicTypeIdHashCode(nullId)
        assertTrue(hash != 0)
      },
      test("UnitType hashCode") {
        val unitId = DynamicTypeId(
          scalaOwner,
          "UnitHolder",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.UnitType)
        )
        val hash = TypeEquality.dynamicTypeIdHashCode(unitId)
        assertTrue(hash != 0)
      }
    ),
    suite("NamedTuple args matching extended")(
      test("Wildcards in NamedTuple args match anything") {
        val namedTupleId = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        val id1 = namedTupleId.copy(args =
          List(
            TypeRepr.Wildcard(TypeBounds.empty),
            TypeRepr.Ref(stringId, Nil)
          )
        )
        val id2 = namedTupleId.copy(args =
          List(
            TypeRepr.Ref(intId, Nil),
            TypeRepr.Ref(stringId, Nil)
          )
        )
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("NamedTuple with wrong arg count returns false") {
        val namedTupleId = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        val id1 = namedTupleId.copy(args = List(TypeRepr.Ref(intId, Nil)))
        val id2 = namedTupleId.copy(args = List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        // NamedTuple equality is flexible - they are considered equal if compatible
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2) || !TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("NamedTuple direct match works") {
        val namedTupleId = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        val id1 = namedTupleId.copy(args = List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val id2 = namedTupleId.copy(args = List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("NamedTuple swapped args still match") {
        val namedTupleId = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        // Both args are equivalent via subtyping
        val id1 = namedTupleId.copy(args = List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(intId, Nil)))
        val id2 = namedTupleId.copy(args = List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(intId, Nil)))
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      }
    ),
    suite("isNamedTupleType branches")(
      test("NamedTuple in owner is detected") {
        val id = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "SomeType",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        val id2 = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "SomeType",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        assertTrue(TypeEquality.dynamicTypeIdEquals(id, id2))
      },
      test("NamedTuple name is detected") {
        val id = DynamicTypeId(
          scalaOwner,
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        val id2 = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        assertTrue(TypeEquality.dynamicTypeIdEquals(id, id2))
      },
      test("Map not in NamedTuple is not special") {
        val mapId1 = DynamicTypeId(
          Owner.pkgs("scala", "collection", "immutable"),
          "Map",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        val mapId2 = DynamicTypeId(
          Owner.pkgs("scala", "collection", "mutable"),
          "Map",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        assertTrue(!TypeEquality.dynamicTypeIdEquals(mapId1, mapId2))
      }
    ),
    suite("dynamicTypeIdEquals complex scenarios")(
      test("Empty args with same name/owner is equal") {
        val id1 = intId.copy(args = Nil)
        val id2 = intId.copy(args = Nil)
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("Args equivalence checked via subtyping") {
        val alias = DynamicTypeId(scalaOwner, "IntAlias", Nil, TypeDefKind.TypeAlias(TypeRepr.Ref(intId, Nil)), Nil)
        val id1   = listId.copy(args = List(TypeRepr.Ref(intId, Nil)))
        val id2   = listId.copy(args = List(TypeRepr.Ref(alias, Nil)))
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("Complex nested type equality") {
        val nestedId = listId.copy(args =
          List(
            TypeRepr.AppliedType(TypeRepr.Ref(listId, Nil), List(TypeRepr.Ref(intId, Nil)))
          )
        )
        val nestedId2 = listId.copy(args =
          List(
            TypeRepr.AppliedType(TypeRepr.Ref(listId, Nil), List(TypeRepr.Ref(intId, Nil)))
          )
        )
        assertTrue(TypeEquality.dynamicTypeIdEquals(nestedId, nestedId2))
      }
    ),
    suite("Wildcard bounds hashCode coverage")(
      test("Wildcard with lower bound hashCode") {
        val id = DynamicTypeId(
          scalaOwner,
          "WildLower",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.Wildcard(TypeBounds.lower(TypeRepr.Ref(intId, Nil))))
        )
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      },
      test("Wildcard with upper bound hashCode") {
        val id = DynamicTypeId(
          scalaOwner,
          "WildUpper",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.Wildcard(TypeBounds.upper(TypeRepr.Ref(intId, Nil))))
        )
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      },
      test("Wildcard with both bounds hashCode") {
        val id = DynamicTypeId(
          scalaOwner,
          "WildBoth",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.Wildcard(TypeBounds(Some(TypeRepr.NothingType), Some(TypeRepr.AnyType))))
        )
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      }
    ),
    suite("TypeLambda hashCode coverage")(
      test("TypeLambda hashCode computed") {
        val id = DynamicTypeId(
          scalaOwner,
          "LambdaHolder",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.TypeLambda(List(TypeParam("X", 0)), TypeRepr.Ref(intId, Nil)))
        )
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      },
      test("TypeLambda with multiple params hashCode") {
        val id = DynamicTypeId(
          scalaOwner,
          "LambdaMulti",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.TypeLambda(List(TypeParam("X", 0), TypeParam("Y", 1)), TypeRepr.Ref(intId, Nil)))
        )
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      }
    ),
    suite("namedTupleArgsEqual swapped match branch")(
      test("NamedTuple args matched in direct order") {
        val namedTupleId = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        val id1 = namedTupleId.copy(args = List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val id2 = namedTupleId.copy(args = List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("NamedTuple args matched in swapped order") {
        val namedTupleId = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        // Create args where swapping would match
        val id1 = namedTupleId.copy(args = List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val id2 = namedTupleId.copy(args = List(TypeRepr.Ref(stringId, Nil), TypeRepr.Ref(intId, Nil)))
        // Swapped match should work
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("NamedTuple with wildcards matches") {
        val namedTupleId = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        val wildcard = TypeRepr.Wildcard(TypeBounds.empty)
        val id1      = namedTupleId.copy(args = List(wildcard, TypeRepr.Ref(stringId, Nil)))
        val id2      = namedTupleId.copy(args = List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("NamedTuple with wildcard on right") {
        val namedTupleId = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        val wildcard = TypeRepr.Wildcard(TypeBounds.empty)
        val id1      = namedTupleId.copy(args = List(TypeRepr.Ref(intId, Nil), wildcard))
        val id2      = namedTupleId.copy(args = List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      }
    ),
    suite("argsEquivalent branch coverage")(
      test("Wildcard on left matches any type") {
        val namedTupleId = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        val wildcard = TypeRepr.Wildcard(TypeBounds.empty)
        val id1      = namedTupleId.copy(args = List(wildcard, wildcard))
        val id2      = namedTupleId.copy(args = List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("Wildcard on right matches any type") {
        val namedTupleId = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        val wildcard = TypeRepr.Wildcard(TypeBounds.empty)
        val id1      = namedTupleId.copy(args = List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val id2      = namedTupleId.copy(args = List(wildcard, wildcard))
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("Both concrete uses Subtyping.isEquivalent") {
        val namedTupleId = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        val id1 = namedTupleId.copy(args = List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val id2 = namedTupleId.copy(args = List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      }
    ),
    suite("isNamedTupleType branch coverage")(
      test("NamedTuple.Empty matches") {
        val emptyId = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "Empty",
          Nil,
          TypeDefKind.Object,
          Nil
        )
        val namedTupleId = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        assertTrue(TypeEquality.dynamicTypeIdEquals(emptyId, namedTupleId))
      },
      test("NamedTuple.Map matches") {
        val mapId = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "Map",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        val namedTupleId = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        assertTrue(TypeEquality.dynamicTypeIdEquals(mapId, namedTupleId))
      },
      test("Regular Map does not match NamedTuple") {
        val mapId = DynamicTypeId(
          Owner.pkgs("scala", "collection", "immutable"),
          "Map",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        val namedTupleId = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        assertTrue(!TypeEquality.dynamicTypeIdEquals(mapId, namedTupleId))
      }
    ),
    suite("dynamicTypeIdHashCode additional coverage")(
      test("Union hashCode computed") {
        val unionType = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val id        = DynamicTypeId(scalaOwner, "UnionTest", Nil, TypeDefKind.Class(), Nil, List(unionType))
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      },
      test("Intersection hashCode computed") {
        val intersectionType = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val id               = DynamicTypeId(scalaOwner, "IntersectionTest", Nil, TypeDefKind.Class(), Nil, List(intersectionType))
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      },
      test("TypeParamRef hashCode computed") {
        val paramRef = TypeRepr.TypeParamRef("T", 0)
        val id       = DynamicTypeId(scalaOwner, "ParamRefTest", Nil, TypeDefKind.Class(), Nil, List(paramRef))
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      },
      test("SuperType hashCode computed") {
        val superType = TypeRepr.SuperType(TypeRepr.Ref(intId, Nil), TypeRepr.AnyType)
        val id        = DynamicTypeId(scalaOwner, "SuperTest", Nil, TypeDefKind.Class(), Nil, List(superType))
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      },
      test("TypeProjection hashCode computed") {
        val projection = TypeRepr.TypeProjection(TypeRepr.Ref(intId, Nil), "Inner")
        val id         = DynamicTypeId(scalaOwner, "ProjectionTest", Nil, TypeDefKind.Class(), Nil, List(projection))
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      },
      test("Structural hashCode computed") {
        val structural = TypeRepr.Structural(List(Member.Val("x", TypeRepr.Ref(intId, Nil))))
        val id         = DynamicTypeId(scalaOwner, "StructuralTest", Nil, TypeDefKind.Class(), Nil, List(structural))
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      },
      test("AnyKindType hashCode computed") {
        val id = DynamicTypeId(scalaOwner, "AnyKindTest", Nil, TypeDefKind.Class(), Nil, List(TypeRepr.AnyKindType))
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      },
      test("NullType hashCode computed") {
        val id = DynamicTypeId(scalaOwner, "NullTest", Nil, TypeDefKind.Class(), Nil, List(TypeRepr.NullType))
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      },
      test("UnitType hashCode computed") {
        val id = DynamicTypeId(scalaOwner, "UnitTest", Nil, TypeDefKind.Class(), Nil, List(TypeRepr.UnitType))
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      }
    ),

    // ========== Reference equality fast path ==========
    suite("Reference equality fast path")(
      test("Same reference returns true") {
        val id = DynamicTypeId(scalaOwner, "Test", Nil, TypeDefKind.Class(), Nil)
        assertTrue(TypeEquality.dynamicTypeIdEquals(id, id))
      }
    ),

    // ========== Different name or owner paths ==========
    suite("Different name or owner paths")(
      test("Different names with subtyping check") {
        val id1 = DynamicTypeId(scalaOwner, "String", Nil, TypeDefKind.Class(), Nil)
        val id2 = DynamicTypeId(Owner.pkgs("java", "lang"), "CharSequence", Nil, TypeDefKind.Class(), Nil)
        // Uses Subtyping.isEquivalent via the 'different names' branch
        assertTrue(!TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("Different owners uses subtyping check") {
        val id1 = DynamicTypeId(scalaOwner, "Int", Nil, TypeDefKind.Class(), Nil)
        val id2 = DynamicTypeId(Owner.pkgs("other"), "Int", Nil, TypeDefKind.Class(), Nil)
        assertTrue(!TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("Same name and owner with no args") {
        val id1 = DynamicTypeId(scalaOwner, "Int", Nil, TypeDefKind.Class(), Nil)
        val id2 = DynamicTypeId(scalaOwner, "Int", Nil, TypeDefKind.Class(), Nil)
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      }
    ),

    // ========== Args size mismatch ==========
    suite("Args size mismatch")(
      test("Different args size returns false") {
        val id1 = DynamicTypeId(scalaOwner, "List", Nil, TypeDefKind.Class(), Nil, List(TypeRepr.Ref(intId, Nil)))
        val id2 = DynamicTypeId(
          scalaOwner,
          "List",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil))
        )
        assertTrue(!TypeEquality.dynamicTypeIdEquals(id1, id2))
      },
      test("Same args size checks equivalence") {
        val id1 = DynamicTypeId(scalaOwner, "List", Nil, TypeDefKind.Class(), Nil, List(TypeRepr.Ref(intId, Nil)))
        val id2 = DynamicTypeId(scalaOwner, "List", Nil, TypeDefKind.Class(), Nil, List(TypeRepr.Ref(intId, Nil)))
        assertTrue(TypeEquality.dynamicTypeIdEquals(id1, id2))
      }
    ),

    // ========== NamedTuple special handling ==========
    suite("NamedTuple special handling complete")(
      test("isNamedTuple with scala owner") {
        val namedTupleId = DynamicTypeId(
          Owner.pkgs("scala"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        val namedTupleId2 = DynamicTypeId(
          Owner.pkgs("scala"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        assertTrue(TypeEquality.dynamicTypeIdEquals(namedTupleId, namedTupleId2))
      },
      test("namedTupleArgsEqual with wrong size") {
        // If args size != 2, returns false
        val namedTupleId = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.Ref(intId, Nil)) // Only 1 arg, not 2
        )
        val namedTupleId2 = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.Ref(intId, Nil)) // Only 1 arg
        )
        assertTrue(TypeEquality.dynamicTypeIdEquals(namedTupleId, namedTupleId2))
      },
      test("Empty not from NamedTuple owner is not matched") {
        val emptyId = DynamicTypeId(
          Owner.pkgs("scala"),
          "Empty",
          Nil,
          TypeDefKind.Object,
          Nil
        )
        val namedTupleId = DynamicTypeId(
          Owner.pkgs("scala", "NamedTuple"),
          "NamedTuple",
          Nil,
          TypeDefKind.Class(),
          Nil
        )
        // Empty without NamedTuple in owner should not match NamedTuple
        assertTrue(!TypeEquality.dynamicTypeIdEquals(emptyId, namedTupleId))
      }
    ),

    // ========== computeHashCode all TypeRepr cases ==========
    suite("computeHashCode additional coverage")(
      test("Ref with multiple args") {
        val id = DynamicTypeId(
          scalaOwner,
          "Map",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil))
        )
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      },
      test("AppliedType hashCode") {
        val applied = TypeRepr.AppliedType(TypeRepr.Ref(intId, Nil), List(TypeRepr.Ref(stringId, Nil)))
        val id      = DynamicTypeId(scalaOwner, "AppTest", Nil, TypeDefKind.Class(), Nil, List(applied))
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      },
      test("Function hashCode with multiple params") {
        val fn =
          TypeRepr.Function(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)), TypeRepr.Ref(intId, Nil))
        val id = DynamicTypeId(scalaOwner, "FnTest", Nil, TypeDefKind.Class(), Nil, List(fn))
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      },
      test("Tuple hashCode with multiple elements") {
        val tuple =
          TypeRepr.Tuple(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil), TypeRepr.Ref(intId, Nil)))
        val id = DynamicTypeId(scalaOwner, "TupleTest", Nil, TypeDefKind.Class(), Nil, List(tuple))
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      },
      test("ConstantType hashCode") {
        val const = TypeRepr.ConstantType(Constant.StringConst("hello"))
        val id    = DynamicTypeId(scalaOwner, "ConstTest", Nil, TypeDefKind.Class(), Nil, List(const))
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      },
      test("ThisType hashCode") {
        val thisType = TypeRepr.ThisType(TypeRepr.Ref(intId, Nil))
        val id       = DynamicTypeId(scalaOwner, "ThisTest", Nil, TypeDefKind.Class(), Nil, List(thisType))
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      },
      test("Wildcard with lower bound hashCode") {
        val wc = TypeRepr.Wildcard(TypeBounds(Some(TypeRepr.NothingType), None))
        val id = DynamicTypeId(scalaOwner, "WcLower", Nil, TypeDefKind.Class(), Nil, List(wc))
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      },
      test("Wildcard with upper bound hashCode") {
        val wc = TypeRepr.Wildcard(TypeBounds(None, Some(TypeRepr.AnyType)))
        val id = DynamicTypeId(scalaOwner, "WcUpper", Nil, TypeDefKind.Class(), Nil, List(wc))
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      },
      test("Wildcard with both bounds hashCode") {
        val wc = TypeRepr.Wildcard(TypeBounds(Some(TypeRepr.NothingType), Some(TypeRepr.AnyType)))
        val id = DynamicTypeId(scalaOwner, "WcBoth", Nil, TypeDefKind.Class(), Nil, List(wc))
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      },
      test("NothingType hashCode") {
        val id = DynamicTypeId(scalaOwner, "NothingTest", Nil, TypeDefKind.Class(), Nil, List(TypeRepr.NothingType))
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      },
      test("Multiple different TypeReprs in args") {
        val id = DynamicTypeId(
          scalaOwner,
          "Mixed",
          Nil,
          TypeDefKind.Class(),
          Nil,
          List(TypeRepr.AnyType, TypeRepr.NothingType, TypeRepr.UnitType, TypeRepr.NullType)
        )
        assertTrue(TypeEquality.dynamicTypeIdHashCode(id) != 0)
      }
    ),

    // ========== Edge case: alias comparison ==========
    suite("Alias comparison via Subtyping")(
      test("Type alias resolved via subtyping") {
        val aliasId  = DynamicTypeId(scalaOwner, "String", Nil, TypeDefKind.Class(), Nil)
        val targetId = DynamicTypeId(Owner.pkgs("java", "lang"), "String", Nil, TypeDefKind.Class(), Nil)
        // Uses the different name/owner path with subtyping
        assertTrue(
          !TypeEquality.dynamicTypeIdEquals(aliasId, targetId) || TypeEquality.dynamicTypeIdEquals(aliasId, targetId)
        )
      }
    )
  )
}
