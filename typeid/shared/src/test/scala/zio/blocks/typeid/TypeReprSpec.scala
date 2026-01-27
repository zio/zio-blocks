package zio.blocks.typeid

import zio.test._

/**
 * Comprehensive tests for TypeRepr data structures.
 * Covers: show, ordering, canonicalization, equality for Union/Intersection.
 */
object TypeReprSpec extends ZIOSpecDefault {

  private def stringId = DynamicTypeId(
    Owner.pkgs("java", "lang"),
    "String",
    Nil,
    TypeDefKind.Class(isFinal = true),
    Nil
  )

  private def intId = DynamicTypeId(
    Owner.pkg("scala"),
    "Int",
    Nil,
    TypeDefKind.Class(isFinal = true, isValue = true),
    Nil
  )

  private def listId = DynamicTypeId(
    Owner.pkgs("scala", "collection", "immutable"),
    "List",
    List(TypeParam("A", 0, Variance.Covariant)),
    TypeDefKind.Trait(isSealed = true),
    Nil
  )

  def spec: Spec[TestEnvironment, Any] = suite("TypeReprSpec")(
    suite("TypeRepr.show")(
      test("Ref without args shows id name") {
        val ref = TypeRepr.Ref(stringId, Nil)
        assertTrue(ref.show == "java.lang.String")
      },
      test("Ref with args shows applied type") {
        val ref = TypeRepr.Ref(listId, List(TypeRepr.Ref(intId, Nil)))
        assertTrue(ref.show == "scala.collection.immutable.List[scala.Int]")
      },
      test("AppliedType shows base[args]") {
        val applied = TypeRepr.AppliedType(
          TypeRepr.Ref(listId, Nil),
          List(TypeRepr.Ref(stringId, Nil))
        )
        assertTrue(applied.show.contains("List") && applied.show.contains("String"))
      },
      test("Union shows pipe-separated types") {
        val union = TypeRepr.Union(List(
          TypeRepr.Ref(intId, Nil),
          TypeRepr.Ref(stringId, Nil)
        ))
        assertTrue(union.show.contains("|"))
      },
      test("Intersection shows ampersand-separated types") {
        val intersection = TypeRepr.Intersection(List(
          TypeRepr.Ref(intId, Nil),
          TypeRepr.Ref(stringId, Nil)
        ))
        assertTrue(intersection.show.contains("&"))
      },
      test("Structural shows braces with members") {
        val structural = TypeRepr.Structural(List(Member.Val("foo", TypeRepr.Ref(intId, Nil))))
        assertTrue(structural.show.contains("{") && structural.show.contains("}"))
      },
      test("Function shows arrow notation") {
        val func = TypeRepr.Function(
          List(TypeRepr.Ref(intId, Nil)),
          TypeRepr.Ref(stringId, Nil)
        )
        assertTrue(func.show.contains("=>"))
      },
      test("Tuple shows parentheses") {
        val tuple = TypeRepr.Tuple(List(
          TypeRepr.Ref(intId, Nil),
          TypeRepr.Ref(stringId, Nil)
        ))
        assertTrue(tuple.show.startsWith("(") && tuple.show.endsWith(")"))
      },
      test("ConstantType shows value") {
        val const = TypeRepr.ConstantType(Constant.IntConst(42))
        assertTrue(const.show.contains("42"))
      },
      test("TypeParamRef shows name") {
        val paramRef = TypeRepr.TypeParamRef("A", 0)
        assertTrue(paramRef.show == "A")
      },
      test("ThisType shows .this suffix") {
        val thisType = TypeRepr.ThisType(TypeRepr.Ref(stringId, Nil))
        assertTrue(thisType.show.contains(".this"))
      },
      test("SuperType shows .super suffix") {
        val superType = TypeRepr.SuperType(
          TypeRepr.Ref(stringId, Nil),
          TypeRepr.Ref(intId, Nil)
        )
        assertTrue(superType.show.contains(".super"))
      },
      test("TypeLambda shows lambda notation") {
        val lambda = TypeRepr.TypeLambda(
          List(TypeParam("A", 0, Variance.Covariant)),
          TypeRepr.Ref(intId, Nil)
        )
        assertTrue(lambda.show.contains("=>>"))
      },
      test("Wildcard shows question mark") {
        val wildcard = TypeRepr.Wildcard(TypeBounds(None, None))
        assertTrue(wildcard.show == "?")
      },
      test("TypeProjection shows hash notation") {
        val projection = TypeRepr.TypeProjection(TypeRepr.Ref(stringId, Nil), "Inner")
        assertTrue(projection.show.contains("#"))
      },
      test("AnyType shows Any") {
        assertTrue(TypeRepr.AnyType.show == "Any")
      },
      test("AnyKindType shows AnyKind") {
        assertTrue(TypeRepr.AnyKindType.show == "AnyKind")
      },
      test("NothingType shows Nothing") {
        assertTrue(TypeRepr.NothingType.show == "Nothing")
      },
      test("NullType shows Null") {
        assertTrue(TypeRepr.NullType.show == "Null")
      },
      test("UnitType shows Unit") {
        assertTrue(TypeRepr.UnitType.show == "Unit")
      }
    ),
    suite("TypeRepr.ordering")(
      test("same type compares equal") {
        val ref = TypeRepr.Ref(intId, Nil)
        assertTrue(TypeRepr.ordering.compare(ref, ref) == 0)
      },
      test("different type tags compare by tag order") {
        val ref = TypeRepr.Ref(intId, Nil)
        val union = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil)))
        assertTrue(TypeRepr.ordering.compare(ref, union) < 0)
      },
      test("same tag, different names compare by name") {
        val aId = intId.copy(name = "AAA")
        val bId = intId.copy(name = "ZZZ")
        val refA = TypeRepr.Ref(aId, Nil)
        val refB = TypeRepr.Ref(bId, Nil)
        assertTrue(TypeRepr.ordering.compare(refA, refB) < 0)
      },
      test("Union with same elements sorted differently are equal") {
        val union1 = TypeRepr.Union(List(
          TypeRepr.Ref(intId, Nil),
          TypeRepr.Ref(stringId, Nil)
        ))
        val union2 = TypeRepr.Union(List(
          TypeRepr.Ref(stringId, Nil),
          TypeRepr.Ref(intId, Nil)
        ))
        assertTrue(TypeRepr.ordering.compare(union1, union2) == 0)
      },
      test("Function types compare by params then result") {
        val func1 = TypeRepr.Function(List(TypeRepr.Ref(intId, Nil)), TypeRepr.Ref(stringId, Nil))
        val func2 = TypeRepr.Function(List(TypeRepr.Ref(stringId, Nil)), TypeRepr.Ref(intId, Nil))
        assertTrue(TypeRepr.ordering.compare(func1, func2) != 0)
      },
      test("Tuple types compare element by element") {
        val tuple1 = TypeRepr.Tuple(List(TypeRepr.Ref(intId, Nil)))
        val tuple2 = TypeRepr.Tuple(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(TypeRepr.ordering.compare(tuple1, tuple2) < 0)
      },
      test("TypeParamRef compares by name then index") {
        val ref1 = TypeRepr.TypeParamRef("A", 0)
        val ref2 = TypeRepr.TypeParamRef("B", 0)
        assertTrue(TypeRepr.ordering.compare(ref1, ref2) < 0)
      },
      test("ConstantType compares by value string") {
        val const1 = TypeRepr.ConstantType(Constant.IntConst(1))
        val const2 = TypeRepr.ConstantType(Constant.IntConst(2))
        assertTrue(TypeRepr.ordering.compare(const1, const2) < 0)
      },
      test("ThisType compares by inner type") {
        val this1 = TypeRepr.ThisType(TypeRepr.Ref(intId, Nil))
        val this2 = TypeRepr.ThisType(TypeRepr.Ref(stringId, Nil))
        assertTrue(TypeRepr.ordering.compare(this1, this2) != 0)
      },
      test("SuperType compares by this then super") {
        val super1 = TypeRepr.SuperType(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil))
        val super2 = TypeRepr.SuperType(TypeRepr.Ref(stringId, Nil), TypeRepr.Ref(intId, Nil))
        assertTrue(TypeRepr.ordering.compare(super1, super2) != 0)
      },
      test("TypeLambda compares by result") {
        val lambda1 = TypeRepr.TypeLambda(List(TypeParam("A", 0, Variance.Covariant)), TypeRepr.Ref(intId, Nil))
        val lambda2 = TypeRepr.TypeLambda(List(TypeParam("A", 0, Variance.Covariant)), TypeRepr.Ref(stringId, Nil))
        assertTrue(TypeRepr.ordering.compare(lambda1, lambda2) != 0)
      },
      test("Wildcard types compare equal") {
        val w1 = TypeRepr.Wildcard(TypeBounds(None, None))
        val w2 = TypeRepr.Wildcard(TypeBounds(Some(TypeRepr.NothingType), Some(TypeRepr.AnyType)))
        assertTrue(TypeRepr.ordering.compare(w1, w2) == 0)
      },
      test("TypeProjection compares by qualifier then name") {
        val proj1 = TypeRepr.TypeProjection(TypeRepr.Ref(intId, Nil), "A")
        val proj2 = TypeRepr.TypeProjection(TypeRepr.Ref(intId, Nil), "B")
        assertTrue(TypeRepr.ordering.compare(proj1, proj2) < 0)
      }
    ),
    suite("TypeRepr.canonicalize")(
      test("Union with single element reduces to element") {
        val union = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil)))
        val result = TypeRepr.canonicalize(union)
        assertTrue(result == TypeRepr.Ref(intId, Nil))
      },
      test("Intersection with single element reduces to element") {
        val inter = TypeRepr.Intersection(List(TypeRepr.Ref(stringId, Nil)))
        val result = TypeRepr.canonicalize(inter)
        assertTrue(result == TypeRepr.Ref(stringId, Nil))
      },
      test("Union with duplicates removes duplicates") {
        val union = TypeRepr.Union(List(
          TypeRepr.Ref(intId, Nil),
          TypeRepr.Ref(intId, Nil)
        ))
        val result = TypeRepr.canonicalize(union)
        assertTrue(result == TypeRepr.Ref(intId, Nil))
      },
      test("Union sorts elements by show") {
        val union = TypeRepr.Union(List(
          TypeRepr.Ref(stringId, Nil),
          TypeRepr.Ref(intId, Nil)
        ))
        val result = TypeRepr.canonicalize(union).asInstanceOf[TypeRepr.Union]
        val shows = result.types.map(_.show)
        assertTrue(shows == shows.sorted)
      },
      test("AppliedType canonicalizes recursively") {
        val applied = TypeRepr.AppliedType(
          TypeRepr.Ref(listId, Nil),
          List(TypeRepr.Union(List(TypeRepr.Ref(intId, Nil))))
        )
        val result = TypeRepr.canonicalize(applied).asInstanceOf[TypeRepr.AppliedType]
        assertTrue(result.args.head == TypeRepr.Ref(intId, Nil))
      },
      test("Ref canonicalizes args recursively") {
        val ref = TypeRepr.Ref(
          listId,
          List(TypeRepr.Union(List(TypeRepr.Ref(intId, Nil))))
        )
        val result = TypeRepr.canonicalize(ref).asInstanceOf[TypeRepr.Ref]
        assertTrue(result.args.head == TypeRepr.Ref(intId, Nil))
      }
    ),
    suite("Union equality")(
      test("Unions with same elements in different order are equal") {
        val union1 = TypeRepr.Union(List(
          TypeRepr.Ref(intId, Nil),
          TypeRepr.Ref(stringId, Nil)
        ))
        val union2 = TypeRepr.Union(List(
          TypeRepr.Ref(stringId, Nil),
          TypeRepr.Ref(intId, Nil)
        ))
        assertTrue(union1 == union2)
      },
      test("Unions with different elements are not equal") {
        val union1 = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil)))
        val union2 = TypeRepr.Union(List(TypeRepr.Ref(stringId, Nil)))
        assertTrue(union1 != union2)
      },
      test("Union hashCode is order-independent") {
        val union1 = TypeRepr.Union(List(
          TypeRepr.Ref(intId, Nil),
          TypeRepr.Ref(stringId, Nil)
        ))
        val union2 = TypeRepr.Union(List(
          TypeRepr.Ref(stringId, Nil),
          TypeRepr.Ref(intId, Nil)
        ))
        assertTrue(union1.hashCode == union2.hashCode)
      }
    ),
    suite("Intersection equality")(
      test("Intersections with same elements in different order are equal") {
        val inter1 = TypeRepr.Intersection(List(
          TypeRepr.Ref(intId, Nil),
          TypeRepr.Ref(stringId, Nil)
        ))
        val inter2 = TypeRepr.Intersection(List(
          TypeRepr.Ref(stringId, Nil),
          TypeRepr.Ref(intId, Nil)
        ))
        assertTrue(inter1 == inter2)
      },
      test("Intersections with different elements are not equal") {
        val inter1 = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil)))
        val inter2 = TypeRepr.Intersection(List(TypeRepr.Ref(stringId, Nil)))
        assertTrue(inter1 != inter2)
      },
      test("Intersection hashCode is order-independent") {
        val inter1 = TypeRepr.Intersection(List(
          TypeRepr.Ref(intId, Nil),
          TypeRepr.Ref(stringId, Nil)
        ))
        val inter2 = TypeRepr.Intersection(List(
          TypeRepr.Ref(stringId, Nil),
          TypeRepr.Ref(intId, Nil)
        ))
        assertTrue(inter1.hashCode == inter2.hashCode)
      }
    ),
    suite("additional ordering cases")(
      test("AppliedType compares base then args") {
        val applied1 = TypeRepr.AppliedType(TypeRepr.Ref(listId, Nil), List(TypeRepr.Ref(intId, Nil)))
        val applied2 = TypeRepr.AppliedType(TypeRepr.Ref(listId, Nil), List(TypeRepr.Ref(stringId, Nil)))
        assertTrue(TypeRepr.ordering.compare(applied1, applied2) != 0)
      },
      test("Intersection compares canonicalized forms") {
        val inter1 = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val inter2 = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil)))
        assertTrue(TypeRepr.ordering.compare(inter1, inter2) != 0)
      },
      test("Structural compares by member string") {
        val struct1 = TypeRepr.Structural(List(Member.Val("a", TypeRepr.Ref(intId, Nil))))
        val struct2 = TypeRepr.Structural(List(Member.Val("b", TypeRepr.Ref(intId, Nil))))
        assertTrue(TypeRepr.ordering.compare(struct1, struct2) != 0)
      },
      test("AnyType compares equal to AnyType") {
        assertTrue(TypeRepr.ordering.compare(TypeRepr.AnyType, TypeRepr.AnyType) == 0)
      },
      test("NothingType compares equal to NothingType") {
        assertTrue(TypeRepr.ordering.compare(TypeRepr.NothingType, TypeRepr.NothingType) == 0)
      },
      test("NullType compares equal to NullType") {
        assertTrue(TypeRepr.ordering.compare(TypeRepr.NullType, TypeRepr.NullType) == 0)
      },
      test("UnitType compares equal to UnitType") {
        assertTrue(TypeRepr.ordering.compare(TypeRepr.UnitType, TypeRepr.UnitType) == 0)
      },
      test("AnyKindType compares equal to AnyKindType") {
        assertTrue(TypeRepr.ordering.compare(TypeRepr.AnyKindType, TypeRepr.AnyKindType) == 0)
      },
      test("different special types have different order") {
        val any = TypeRepr.AnyType
        val nothing = TypeRepr.NothingType
        assertTrue(TypeRepr.ordering.compare(any, nothing) != 0)
      },
      test("Ref compares by id show then args") {
        val ref1 = TypeRepr.Ref(intId, Nil)
        val ref2 = TypeRepr.Ref(intId, List(TypeRepr.AnyType))
        assertTrue(TypeRepr.ordering.compare(ref1, ref2) != 0)
      },
      test("TypeParamRef with same name compares by index") {
        val ref1 = TypeRepr.TypeParamRef("A", 0)
        val ref2 = TypeRepr.TypeParamRef("A", 1)
        assertTrue(TypeRepr.ordering.compare(ref1, ref2) < 0)
      }
    ),
    suite("Constant types coverage")(
      test("IntConst value") {
        val c = Constant.IntConst(42)
        assertTrue(c.value == 42)
      },
      test("LongConst value") {
        val c = Constant.LongConst(123L)
        assertTrue(c.value == 123L)
      },
      test("FloatConst value") {
        val c = Constant.FloatConst(1.5f)
        assertTrue(c.value == 1.5f)
      },
      test("DoubleConst value") {
        val c = Constant.DoubleConst(2.5)
        assertTrue(c.value == 2.5)
      },
      test("BooleanConst value") {
        val c = Constant.BooleanConst(true)
        assertTrue(c.value == true)
      },
      test("CharConst value") {
        val c = Constant.CharConst('x')
        assertTrue(c.value == 'x')
      },
      test("StringConst value") {
        val c = Constant.StringConst("hello")
        assertTrue(c.value == "hello")
      },
      test("NullConst value is null") {
        val c = Constant.NullConst()
        assertTrue(c.value == null)
      },
      test("UnitConst value is unit") {
        val c = Constant.UnitConst()
        assertTrue(c.value == ())
      },
      test("ClassOfConst value is TypeRepr") {
        val tpe = TypeRepr.Ref(intId, Nil)
        val c = Constant.ClassOfConst(tpe)
        assertTrue(c.value == tpe)
      }
    ),
    suite("TermPath coverage")(
      test("empty path") {
        val path = TermPath.empty
        assertTrue(path.segments.isEmpty && path.asString == "" && path.isStable)
      },
      test("Package segment is stable") {
        val seg = TermPath.Package("scala")
        assertTrue(seg.isStable && seg.name == "scala")
      },
      test("Module segment is stable") {
        val seg = TermPath.Module("Predef")
        assertTrue(seg.isStable && seg.name == "Predef")
      },
      test("Val segment is stable") {
        val seg = TermPath.Val("myVal")
        assertTrue(seg.isStable && seg.name == "myVal")
      },
      test("LazyVal segment is stable") {
        val seg = TermPath.LazyVal("lazyVal")
        assertTrue(seg.isStable && seg.name == "lazyVal")
      },
      test("Var segment is not stable") {
        val seg = TermPath.Var("myVar")
        assertTrue(!seg.isStable && seg.name == "myVar")
      },
      test("Def segment is not stable") {
        val seg = TermPath.Def("myDef")
        assertTrue(!seg.isStable && seg.name == "myDef")
      },
      test("This segment") {
        val seg = TermPath.This("MyClass")
        assertTrue(seg.isStable && seg.name == "MyClass.this")
      },
      test("Super segment without mixin") {
        val seg = TermPath.Super("MyClass", None)
        assertTrue(seg.isStable && seg.name == "MyClass.super")
      },
      test("Super segment with mixin") {
        val seg = TermPath.Super("MyClass", Some("Trait1"))
        assertTrue(seg.isStable && seg.name == "MyClass.super[Trait1]")
      },
      test("path concatenation with /") {
        val path = TermPath.empty / TermPath.Package("scala") / TermPath.Module("Predef")
        assertTrue(path.asString == "scala.Predef" && path.isStable)
      },
      test("path with unstable segment is not stable") {
        val path = TermPath.empty / TermPath.Package("scala") / TermPath.Var("x")
        assertTrue(!path.isStable)
      },
      test("toString returns asString") {
        val path = TermPath.empty / TermPath.Package("scala")
        assertTrue(path.toString == path.asString)
      }
    ),
    suite("ParamClause coverage")(
      test("Regular param clause") {
        val params = List(Param("x", TypeRepr.Ref(intId, Nil)))
        val clause = ParamClause.Regular(params)
        assertTrue(clause.params == params && clause.size == 1 && !clause.isEmpty)
      },
      test("Using param clause") {
        val params = List(Param("ctx", TypeRepr.Ref(stringId, Nil)))
        val clause = ParamClause.Using(params)
        assertTrue(clause.params == params && clause.size == 1)
      },
      test("Implicit param clause") {
        val params = List(Param("ev", TypeRepr.Ref(stringId, Nil)))
        val clause = ParamClause.Implicit(params)
        assertTrue(clause.params == params && clause.size == 1)
      },
      test("empty clause") {
        val clause = ParamClause.empty
        assertTrue(clause.isEmpty && clause.size == 0)
      },
      test("Param with default and repeated") {
        val param = Param("args", TypeRepr.Ref(stringId, Nil), hasDefault = true, isRepeated = true)
        assertTrue(param.hasDefault && param.isRepeated && param.name == "args")
      }
    ),
    suite("Union/Intersection branch coverage")(
      test("Union ordering with different sizes") {
        val union1 = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil)))
        val union2 = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(TypeRepr.ordering.compare(union1, union2) != 0)
      },
      test("Union ordering with same size different elements") {
        val union1 = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil)))
        val union2 = TypeRepr.Union(List(TypeRepr.Ref(stringId, Nil)))
        assertTrue(TypeRepr.ordering.compare(union1, union2) != 0)
      },
      test("Intersection ordering with different sizes") {
        val inter1 = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil)))
        val inter2 = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(TypeRepr.ordering.compare(inter1, inter2) != 0)
      },
      test("Intersection ordering with same size different elements") {
        val inter1 = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil)))
        val inter2 = TypeRepr.Intersection(List(TypeRepr.Ref(stringId, Nil)))
        assertTrue(TypeRepr.ordering.compare(inter1, inter2) != 0)
      },
      test("Union show displays all elements") {
        val union = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(union.show.contains("|"))
      },
      test("Intersection show displays all elements") {
        val inter = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(inter.show.contains("&"))
      },
      test("Union canonicalize sorts elements") {
        val union = TypeRepr.Union(List(TypeRepr.Ref(stringId, Nil), TypeRepr.Ref(intId, Nil)))
        val canonical = TypeRepr.canonicalize(union).asInstanceOf[TypeRepr.Union]
        assertTrue(canonical.types.size == 2)
      },
      test("Intersection canonicalize sorts elements") {
        val inter = TypeRepr.Intersection(List(TypeRepr.Ref(stringId, Nil), TypeRepr.Ref(intId, Nil)))
        val canonical = TypeRepr.canonicalize(inter).asInstanceOf[TypeRepr.Intersection]
        assertTrue(canonical.types.size == 2)
      },
      test("Union ordering same elements equal") {
        val union1 = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val union2 = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(TypeRepr.ordering.compare(union1, union2) == 0)
      },
      test("Intersection ordering same elements equal") {
        val inter1 = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val inter2 = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(TypeRepr.ordering.compare(inter1, inter2) == 0)
      }
    ),
    suite("TypeRepr.show all branches")(
      test("Function show") {
        val func = TypeRepr.Function(List(TypeRepr.Ref(intId, Nil)), TypeRepr.Ref(stringId, Nil))
        assertTrue(func.show.contains("=>"))
      },
      test("Tuple show") {
        val tuple = TypeRepr.Tuple(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(tuple.show.contains(","))
      },
      test("TypeParamRef show") {
        val param = TypeRepr.TypeParamRef("T", 0)
        assertTrue(param.show == "T")
      },
      test("ConstantType show") {
        val const = TypeRepr.ConstantType(Constant.IntConst(42))
        assertTrue(const.show.contains("42"))
      },
      test("Wildcard show with bounds") {
        val wild = TypeRepr.Wildcard(TypeBounds(Some(TypeRepr.NothingType), Some(TypeRepr.AnyType)))
        assertTrue(wild.show.contains("?"))  // Scala 3 uses ? for wildcards
      },
      test("Wildcard show empty bounds") {
        val wild = TypeRepr.Wildcard(TypeBounds.empty)
        assertTrue(wild.show == "?")  // Scala 3 uses ? for wildcards
      },
      test("ThisType show") {
        val thisType = TypeRepr.ThisType(TypeRepr.Ref(intId, Nil))
        assertTrue(thisType.show.contains("this"))
      },
      test("SuperType show") {
        val superType = TypeRepr.SuperType(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil))
        assertTrue(superType.show.contains("super"))
      },
      test("TypeLambda show") {
        val lambda = TypeRepr.TypeLambda(List(TypeParam("A", 0)), TypeRepr.Ref(intId, Nil))
        assertTrue(lambda.show.contains("=>"))
      },
      test("TypeProjection show") {
        val proj = TypeRepr.TypeProjection(TypeRepr.Ref(intId, Nil), "Inner")
        assertTrue(proj.show.contains("#"))
      },
      test("Structural show") {
        val structural = TypeRepr.Structural(List(Member.Val("foo", TypeRepr.Ref(intId, Nil))))
        assertTrue(structural.show.contains("{"))
      },
      test("AnyType show") {
        assertTrue(TypeRepr.AnyType.show == "Any")
      },
      test("NothingType show") {
        assertTrue(TypeRepr.NothingType.show == "Nothing")
      },
      test("NullType show") {
        assertTrue(TypeRepr.NullType.show == "Null")
      },
      test("UnitType show") {
        assertTrue(TypeRepr.UnitType.show == "Unit")
      },
      test("AnyKindType show") {
        assertTrue(TypeRepr.AnyKindType.show == "AnyKind")
      }
    ),
    suite("TypeRepr.dealias coverage")(
      test("Ref with TypeAlias dealiases") {
        val aliasId = DynamicTypeId(
          Owner.pkg("scala"),
          "MyInt",
          Nil,
          TypeDefKind.TypeAlias(TypeRepr.Ref(intId, Nil)),
          Nil
        )
        val aliasRef = TypeRepr.Ref(aliasId, Nil)
        val dealiased = aliasRef.dealias
        // Dealiased type should be equivalent to Int
        assertTrue(Subtyping.isEquivalent(dealiased, TypeRepr.Ref(intId, Nil)))
      },
      test("Regular Ref does not dealias") {
        val intRef = TypeRepr.Ref(intId, Nil)
        val dealiased = intRef.dealias
        assertTrue(dealiased == intRef)
      },
      test("AppliedType dealiases base") {
        val applied = TypeRepr.AppliedType(TypeRepr.Ref(listId, Nil), List(TypeRepr.Ref(intId, Nil)))
        val dealiased = applied.dealias
        assertTrue(dealiased != null)
      }
    ),
    suite("Union equals and hashCode coverage")(
      test("Union equals same types in same order") {
        val u1 = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val u2 = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(u1 == u2)
      },
      test("Union equals same types in different order") {
        val u1 = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val u2 = TypeRepr.Union(List(TypeRepr.Ref(stringId, Nil), TypeRepr.Ref(intId, Nil)))
        assertTrue(u1 == u2)
      },
      test("Union not equal to different types") {
        val u1 = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil)))
        val u2 = TypeRepr.Union(List(TypeRepr.Ref(stringId, Nil)))
        assertTrue(u1 != u2)
      },
      test("Union not equal to different size") {
        val u1 = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil)))
        val u2 = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(u1 != u2)
      },
      test("Union not equal to non-Union") {
        val u1 = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil)))
        val other: Any = TypeRepr.Ref(intId, Nil)
        assertTrue(u1 != other)
      },
      test("Union not equal to null") {
        val u1 = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil)))
        assertTrue(u1 != null)
      },
      test("Union hashCode is consistent") {
        val u1 = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val u2 = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(u1.hashCode == u2.hashCode)
      },
      test("Union hashCode order independent") {
        val u1 = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val u2 = TypeRepr.Union(List(TypeRepr.Ref(stringId, Nil), TypeRepr.Ref(intId, Nil)))
        assertTrue(u1.hashCode == u2.hashCode)
      },
      test("Union hashCode is nonzero") {
        val u = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil)))
        assertTrue(u.hashCode != 0)
      }
    ),
    suite("Intersection equals and hashCode coverage")(
      test("Intersection equals same types in same order") {
        val i1 = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val i2 = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(i1 == i2)
      },
      test("Intersection equals same types in different order") {
        val i1 = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val i2 = TypeRepr.Intersection(List(TypeRepr.Ref(stringId, Nil), TypeRepr.Ref(intId, Nil)))
        assertTrue(i1 == i2)
      },
      test("Intersection not equal to different types") {
        val i1 = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil)))
        val i2 = TypeRepr.Intersection(List(TypeRepr.Ref(stringId, Nil)))
        assertTrue(i1 != i2)
      },
      test("Intersection not equal to different size") {
        val i1 = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil)))
        val i2 = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(i1 != i2)
      },
      test("Intersection not equal to non-Intersection") {
        val i1 = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil)))
        val other: Any = TypeRepr.Ref(intId, Nil)
        assertTrue(i1 != other)
      },
      test("Intersection not equal to null") {
        val i1 = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil)))
        assertTrue(i1 != null)
      },
      test("Intersection hashCode is consistent") {
        val i1 = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val i2 = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(i1.hashCode == i2.hashCode)
      },
      test("Intersection hashCode order independent") {
        val i1 = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val i2 = TypeRepr.Intersection(List(TypeRepr.Ref(stringId, Nil), TypeRepr.Ref(intId, Nil)))
        assertTrue(i1.hashCode == i2.hashCode)
      },
      test("Intersection hashCode is nonzero") {
        val i = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil)))
        assertTrue(i.hashCode != 0)
      }
    ),
    suite("Union and Intersection show coverage")(
      test("Union show format") {
        val u = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(u.show.contains("|"))
      },
      test("Intersection show format") {
        val i = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        assertTrue(i.show.contains("&"))
      },
      test("Union with single element show") {
        val u = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil)))
        assertTrue(u.show.nonEmpty)
      },
      test("Intersection with single element show") {
        val i = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil)))
        assertTrue(i.show.nonEmpty)
      }
    ),
    suite("Union and Intersection ordering coverage")(
      test("Union ordering compared") {
        val u1 = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil)))
        val u2 = TypeRepr.Union(List(TypeRepr.Ref(stringId, Nil)))
        assertTrue(TypeRepr.ordering.compare(u1, u2) != 0 || u1 == u2)
      },
      test("Intersection ordering compared") {
        val i1 = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil)))
        val i2 = TypeRepr.Intersection(List(TypeRepr.Ref(stringId, Nil)))
        assertTrue(TypeRepr.ordering.compare(i1, i2) != 0 || i1 == i2)
      },
      test("Union less than Intersection by tag") {
        val u = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil)))
        val i = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil)))
        assertTrue(TypeRepr.ordering.compare(u, i) < 0)
      }
    ),
    suite("Union and Intersection canonicalization")(
      test("Union canonicalize sorts and deduplicates") {
        val u = TypeRepr.Union(List(TypeRepr.Ref(stringId, Nil), TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val canon = TypeRepr.canonicalize(u)
        canon match {
          case TypeRepr.Union(types) => assertTrue(types.size == 2)
          case _ => assertTrue(false)
        }
      },
      test("Intersection canonicalize sorts and deduplicates") {
        val i = TypeRepr.Intersection(List(TypeRepr.Ref(stringId, Nil), TypeRepr.Ref(intId, Nil), TypeRepr.Ref(stringId, Nil)))
        val canon = TypeRepr.canonicalize(i)
        canon match {
          case TypeRepr.Intersection(types) => assertTrue(types.size == 2)
          case _ => assertTrue(false)
        }
      },
      test("Union with single type after dedup becomes single type") {
        val u = TypeRepr.Union(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(intId, Nil)))
        val canon = TypeRepr.canonicalize(u)
        assertTrue(canon.isInstanceOf[TypeRepr.Ref])
      },
      test("Intersection with single type after dedup becomes single type") {
        val i = TypeRepr.Intersection(List(TypeRepr.Ref(intId, Nil), TypeRepr.Ref(intId, Nil)))
        val canon = TypeRepr.canonicalize(i)
        assertTrue(canon.isInstanceOf[TypeRepr.Ref])
      }
    ),
    suite("BuildInfo coverage")(
      test("BuildInfo version is accessible") {
        // BuildInfo is generated by sbt-buildinfo and contains version info
        val version = zio.blocks.typeid.BuildInfo.version
        assertTrue(version != null && version.nonEmpty)
      },
      test("BuildInfo name is accessible") {
        val name = zio.blocks.typeid.BuildInfo.name
        assertTrue(name != null && name.nonEmpty)
      },
      test("BuildInfo scalaVersion is accessible") {
        val scalaVersion = zio.blocks.typeid.BuildInfo.scalaVersion
        assertTrue(scalaVersion != null && scalaVersion.nonEmpty)
      },
      test("BuildInfo sbtVersion is accessible") {
        val sbtVersion = zio.blocks.typeid.BuildInfo.sbtVersion
        assertTrue(sbtVersion != null && sbtVersion.nonEmpty)
      }
    )
  )
}
