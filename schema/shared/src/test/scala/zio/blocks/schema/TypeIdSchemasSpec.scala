package zio.blocks.schema

import zio.test._
import zio.blocks.typeid._

object TypeIdSchemasSpec extends SchemaBaseSpec {

  def roundtrip[A](value: A)(implicit schema: Schema[A]): Boolean = {
    val dynamic = schema.toDynamicValue(value)
    val result  = schema.fromDynamicValue(dynamic)
    result == Right(value)
  }

  def roundtripTypeId[A](typeId: TypeId[A]): Boolean = {
    val schema  = Schema.typeIdSchema.asInstanceOf[Schema[TypeId[A]]]
    val dynamic = schema.toDynamicValue(typeId)
    val result  = schema.fromDynamicValue(dynamic)
    result == Right(typeId)
  }

  def spec = suite("TypeIdSchemasSpec")(
    varianceSuite,
    kindSuite,
    ownerSegmentSuite,
    ownerSuite,
    termPathSegmentSuite,
    termPathSuite,
    typeBoundsSuite,
    typeParamSuite,
    paramSuite,
    memberSuite,
    tupleElementSuite,
    enumCaseParamSuite,
    enumCaseInfoSuite,
    annotationArgSuite,
    annotationSuite,
    typeReprSuite,
    typeDefKindSuite,
    typeIdSuite
  )

  lazy val varianceSuite = suite("Variance")(
    test("roundtrip Covariant") {
      assertTrue(roundtrip[Variance](Variance.Covariant))
    },
    test("roundtrip Contravariant") {
      assertTrue(roundtrip[Variance](Variance.Contravariant))
    },
    test("roundtrip Invariant") {
      assertTrue(roundtrip[Variance](Variance.Invariant))
    }
  )

  lazy val kindSuite = suite("Kind")(
    test("roundtrip Type") {
      assertTrue(roundtrip[Kind](Kind.Type))
    },
    test("roundtrip Arrow with single param") {
      assertTrue(roundtrip[Kind](Kind.Arrow(List(Kind.Type), Kind.Type)))
    },
    test("roundtrip Arrow with multiple params") {
      assertTrue(roundtrip[Kind](Kind.Arrow(List(Kind.Type, Kind.Type), Kind.Type)))
    },
    test("roundtrip nested Arrow") {
      val nested = Kind.Arrow(List(Kind.Arrow(List(Kind.Type), Kind.Type)), Kind.Type)
      assertTrue(roundtrip[Kind](nested))
    }
  )

  lazy val ownerSegmentSuite = suite("Owner.Segment")(
    test("roundtrip Package") {
      assertTrue(roundtrip[Owner.Segment](Owner.Package("zio")))
    },
    test("roundtrip Term") {
      assertTrue(roundtrip[Owner.Segment](Owner.Term("Schema")))
    },
    test("roundtrip Type") {
      assertTrue(roundtrip[Owner.Segment](Owner.Type("TypeId")))
    }
  )

  lazy val ownerSuite = suite("Owner")(
    test("roundtrip empty owner") {
      assertTrue(roundtrip(Owner(Nil)))
    },
    test("roundtrip single package") {
      assertTrue(roundtrip(Owner(List(Owner.Package("zio")))))
    },
    test("roundtrip package path") {
      assertTrue(roundtrip(Owner(List(Owner.Package("zio"), Owner.Package("blocks")))))
    },
    test("roundtrip mixed segments") {
      assertTrue(roundtrip(Owner(List(Owner.Package("zio"), Owner.Package("blocks"), Owner.Type("Schema")))))
    }
  )

  lazy val termPathSegmentSuite = suite("TermPath.Segment")(
    test("roundtrip Package") {
      assertTrue(roundtrip[TermPath.Segment](TermPath.Package("scala")))
    },
    test("roundtrip Term") {
      assertTrue(roundtrip[TermPath.Segment](TermPath.Term("Predef")))
    }
  )

  lazy val termPathSuite = suite("TermPath")(
    test("roundtrip empty path") {
      assertTrue(roundtrip(TermPath(Nil)))
    },
    test("roundtrip single segment") {
      assertTrue(roundtrip(TermPath(List(TermPath.Package("scala")))))
    },
    test("roundtrip multi-segment path") {
      assertTrue(roundtrip(TermPath(List(TermPath.Package("scala"), TermPath.Term("Predef")))))
    }
  )

  lazy val typeBoundsSuite = suite("TypeBounds")(
    test("roundtrip empty bounds") {
      assertTrue(roundtrip(TypeBounds(None, None)))
    },
    test("roundtrip lower bound only") {
      assertTrue(roundtrip(TypeBounds(Some(TypeRepr.NothingType), None)))
    },
    test("roundtrip upper bound only") {
      assertTrue(roundtrip(TypeBounds(None, Some(TypeRepr.AnyType))))
    },
    test("roundtrip both bounds") {
      assertTrue(roundtrip(TypeBounds(Some(TypeRepr.NothingType), Some(TypeRepr.AnyType))))
    }
  )

  lazy val typeParamSuite = suite("TypeParam")(
    test("roundtrip simple TypeParam") {
      val typeParam = TypeParam(
        name = "A",
        index = 0,
        variance = Variance.Covariant,
        bounds = TypeBounds(None, None),
        kind = Kind.Type
      )
      assertTrue(roundtrip(typeParam))
    },
    test("roundtrip TypeParam with bounds") {
      val typeParam = TypeParam(
        name = "T",
        index = 1,
        variance = Variance.Invariant,
        bounds = TypeBounds(Some(TypeRepr.NothingType), Some(TypeRepr.AnyType)),
        kind = Kind.Type
      )
      assertTrue(roundtrip(typeParam))
    },
    test("roundtrip TypeParam with Arrow kind") {
      val typeParam = TypeParam(
        name = "F",
        index = 0,
        variance = Variance.Invariant,
        bounds = TypeBounds(None, None),
        kind = Kind.Arrow(List(Kind.Type), Kind.Type)
      )
      assertTrue(roundtrip(typeParam))
    }
  )

  lazy val paramSuite = suite("Param")(
    test("roundtrip simple Param") {
      val param = Param(
        name = "value",
        tpe = TypeRepr.Ref(TypeId.of[String]),
        isImplicit = false,
        hasDefault = false
      )
      assertTrue(roundtrip(param))
    },
    test("roundtrip implicit Param") {
      val param = Param(
        name = "ord",
        tpe = TypeRepr.Ref(TypeId.of[Int]),
        isImplicit = true,
        hasDefault = false
      )
      assertTrue(roundtrip(param))
    },
    test("roundtrip Param with default") {
      val param = Param(
        name = "count",
        tpe = TypeRepr.Ref(TypeId.of[Int]),
        isImplicit = false,
        hasDefault = true
      )
      assertTrue(roundtrip(param))
    }
  )

  lazy val memberSuite = suite("Member")(
    test("roundtrip Def member") {
      val member: Member = Member.Def(
        name = "map",
        typeParams = Nil,
        paramLists = Nil,
        result = TypeRepr.Ref(TypeId.of[Unit])
      )
      assertTrue(roundtrip(member))
    },
    test("roundtrip Def member with params") {
      val param = Param(
        name = "f",
        tpe = TypeRepr.Ref(TypeId.of[Int]),
        isImplicit = false,
        hasDefault = false
      )
      val member: Member = Member.Def(
        name = "apply",
        typeParams = Nil,
        paramLists = List(List(param)),
        result = TypeRepr.Ref(TypeId.of[String])
      )
      assertTrue(roundtrip(member))
    },
    test("roundtrip Val member") {
      val member: Member = Member.Val(
        name = "value",
        tpe = TypeRepr.Ref(TypeId.of[Int]),
        isVar = false
      )
      assertTrue(roundtrip(member))
    },
    test("roundtrip var Val member") {
      val member: Member = Member.Val(
        name = "counter",
        tpe = TypeRepr.Ref(TypeId.of[Int]),
        isVar = true
      )
      assertTrue(roundtrip(member))
    },
    test("roundtrip TypeMember") {
      val member: Member = Member.TypeMember(
        name = "Elem",
        typeParams = Nil,
        lowerBound = None,
        upperBound = Some(TypeRepr.AnyType)
      )
      assertTrue(roundtrip(member))
    }
  )

  lazy val tupleElementSuite = suite("TupleElement")(
    test("roundtrip unnamed TupleElement") {
      val elem = TupleElement(None, TypeRepr.Ref(TypeId.of[Int]))
      assertTrue(roundtrip(elem))
    },
    test("roundtrip named TupleElement") {
      val elem = TupleElement(Some("x"), TypeRepr.Ref(TypeId.of[String]))
      assertTrue(roundtrip(elem))
    }
  )

  lazy val enumCaseParamSuite = suite("EnumCaseParam")(
    test("roundtrip EnumCaseParam") {
      val param = EnumCaseParam(
        name = "value",
        tpe = TypeRepr.Ref(TypeId.of[Int])
      )
      assertTrue(roundtrip(param))
    }
  )

  lazy val enumCaseInfoSuite = suite("EnumCaseInfo")(
    test("roundtrip simple EnumCaseInfo") {
      val info = EnumCaseInfo(
        name = "Red",
        ordinal = 0,
        params = Nil,
        isObjectCase = true
      )
      assertTrue(roundtrip(info))
    },
    test("roundtrip EnumCaseInfo with params") {
      val param = EnumCaseParam("value", TypeRepr.Ref(TypeId.of[Int]))
      val info  = EnumCaseInfo(
        name = "Some",
        ordinal = 1,
        params = List(param),
        isObjectCase = false
      )
      assertTrue(roundtrip(info))
    }
  )

  lazy val annotationArgSuite = suite("AnnotationArg")(
    test("roundtrip Const with DynamicValue") {
      val dv = DynamicValue.Primitive(PrimitiveValue.String("test"))
      assertTrue(roundtrip[AnnotationArg](AnnotationArg.Const(dv)))
    },
    test("roundtrip ClassOf") {
      assertTrue(roundtrip[AnnotationArg](AnnotationArg.ClassOf(TypeRepr.Ref(TypeId.of[String]))))
    },
    test("roundtrip EnumValue") {
      assertTrue(roundtrip[AnnotationArg](AnnotationArg.EnumValue(TypeId.of[Variance], "Covariant")))
    },
    test("roundtrip ArrayArg empty") {
      assertTrue(roundtrip[AnnotationArg](AnnotationArg.ArrayArg(Nil)))
    },
    test("roundtrip ArrayArg with ClassOf values") {
      val arr = AnnotationArg.ArrayArg(
        List(
          AnnotationArg.ClassOf(TypeRepr.Ref(TypeId.of[Int])),
          AnnotationArg.ClassOf(TypeRepr.Ref(TypeId.of[String]))
        )
      )
      assertTrue(roundtrip[AnnotationArg](arr))
    },
    test("roundtrip Named with ClassOf") {
      val named = AnnotationArg.Named("key", AnnotationArg.ClassOf(TypeRepr.Ref(TypeId.of[String])))
      assertTrue(roundtrip[AnnotationArg](named))
    },
    test("roundtrip Nested") {
      val ann    = Annotation(TypeId.of[Deprecated], Nil)
      val nested = AnnotationArg.Nested(ann)
      assertTrue(roundtrip[AnnotationArg](nested))
    }
  )

  lazy val annotationSuite = suite("Annotation")(
    test("roundtrip simple annotation") {
      val ann = Annotation(TypeId.of[Deprecated], Nil)
      assertTrue(roundtrip[Annotation](ann))
    },
    test("roundtrip annotation with ClassOf arg") {
      val ann = Annotation(
        TypeId.of[Deprecated],
        List(AnnotationArg.Named("target", AnnotationArg.ClassOf(TypeRepr.Ref(TypeId.of[String]))))
      )
      assertTrue(roundtrip[Annotation](ann))
    }
  )

  lazy val typeReprSuite = suite("TypeRepr")(
    test("roundtrip Ref") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.Ref(TypeId.of[String])))
    },
    test("roundtrip ParamRef") {
      val typeParam = TypeParam("A", 0, Variance.Invariant, TypeBounds(None, None), Kind.Type)
      assertTrue(roundtrip[TypeRepr](TypeRepr.ParamRef(typeParam, 0)))
    },
    test("roundtrip Applied") {
      val applied = TypeRepr.Applied(
        TypeRepr.Ref(TypeId.of[List[_]]),
        List(TypeRepr.Ref(TypeId.of[Int]))
      )
      assertTrue(roundtrip[TypeRepr](applied))
    },
    test("roundtrip Structural") {
      val member = Member.Val("x", TypeRepr.Ref(TypeId.of[Int]), isVar = false)
      assertTrue(roundtrip[TypeRepr](TypeRepr.Structural(Nil, List(member))))
    },
    test("roundtrip Intersection") {
      assertTrue(
        roundtrip[TypeRepr](
          TypeRepr.Intersection(List(TypeRepr.Ref(TypeId.of[Serializable]), TypeRepr.Ref(TypeId.of[Cloneable])))
        )
      )
    },
    test("roundtrip Union") {
      assertTrue(
        roundtrip[TypeRepr](TypeRepr.Union(List(TypeRepr.Ref(TypeId.of[Int]), TypeRepr.Ref(TypeId.of[String]))))
      )
    },
    test("roundtrip Tuple") {
      val elems =
        List(TupleElement(None, TypeRepr.Ref(TypeId.of[Int])), TupleElement(None, TypeRepr.Ref(TypeId.of[String])))
      assertTrue(roundtrip[TypeRepr](TypeRepr.Tuple(elems)))
    },
    test("roundtrip Function") {
      assertTrue(
        roundtrip[TypeRepr](TypeRepr.Function(List(TypeRepr.Ref(TypeId.of[Int])), TypeRepr.Ref(TypeId.of[String])))
      )
    },
    test("roundtrip ContextFunction") {
      assertTrue(
        roundtrip[TypeRepr](
          TypeRepr.ContextFunction(List(TypeRepr.Ref(TypeId.of[Int])), TypeRepr.Ref(TypeId.of[String]))
        )
      )
    },
    test("roundtrip TypeLambda") {
      val param = TypeParam("A", 0, Variance.Covariant, TypeBounds(None, None), Kind.Type)
      assertTrue(roundtrip[TypeRepr](TypeRepr.TypeLambda(List(param), TypeRepr.Ref(TypeId.of[List[_]]))))
    },
    test("roundtrip ByName") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.ByName(TypeRepr.Ref(TypeId.of[Int]))))
    },
    test("roundtrip Repeated") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.Repeated(TypeRepr.Ref(TypeId.of[String]))))
    },
    test("roundtrip Wildcard unbounded") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.Wildcard(TypeBounds(None, None))))
    },
    test("roundtrip Wildcard with bounds") {
      assertTrue(
        roundtrip[TypeRepr](TypeRepr.Wildcard(TypeBounds(Some(TypeRepr.NothingType), Some(TypeRepr.AnyType))))
      )
    },
    test("roundtrip Singleton") {
      assertTrue(
        roundtrip[TypeRepr](TypeRepr.Singleton(TermPath(List(TermPath.Package("scala"), TermPath.Term("None")))))
      )
    },
    test("roundtrip ThisType") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.ThisType(Owner(List(Owner.Package("zio"))))))
    },
    test("roundtrip TypeProjection") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.TypeProjection(TypeRepr.Ref(TypeId.of[List[_]]), "Elem")))
    },
    test("roundtrip TypeSelect") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.TypeSelect(TypeRepr.Ref(TypeId.of[scala.Predef.type]), "String")))
    },
    test("roundtrip Annotated") {
      val ann = Annotation(TypeId.of[Deprecated], Nil)
      assertTrue(roundtrip[TypeRepr](TypeRepr.Annotated(TypeRepr.Ref(TypeId.of[String]), List(ann))))
    },
    test("roundtrip Constant.IntConst") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.Constant.IntConst(42)))
    },
    test("roundtrip Constant.LongConst") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.Constant.LongConst(123456789L)))
    },
    test("roundtrip Constant.FloatConst") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.Constant.FloatConst(3.14f)))
    },
    test("roundtrip Constant.DoubleConst") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.Constant.DoubleConst(2.71828)))
    },
    test("roundtrip Constant.BooleanConst true") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.Constant.BooleanConst(true)))
    },
    test("roundtrip Constant.BooleanConst false") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.Constant.BooleanConst(false)))
    },
    test("roundtrip Constant.CharConst") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.Constant.CharConst('z')))
    },
    test("roundtrip Constant.StringConst") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.Constant.StringConst("literal")))
    },
    test("roundtrip Constant.NullConst") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.Constant.NullConst))
    },
    test("roundtrip Constant.UnitConst") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.Constant.UnitConst))
    },
    test("roundtrip Constant.ClassOfConst") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.Constant.ClassOfConst(TypeRepr.Ref(TypeId.of[String]))))
    },
    test("roundtrip AnyType") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.AnyType))
    },
    test("roundtrip NothingType") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.NothingType))
    },
    test("roundtrip NullType") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.NullType))
    },
    test("roundtrip UnitType") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.UnitType))
    },
    test("roundtrip AnyKindType") {
      assertTrue(roundtrip[TypeRepr](TypeRepr.AnyKindType))
    }
  )

  lazy val typeDefKindSuite = suite("TypeDefKind")(
    test("roundtrip Class simple") {
      val defKind: TypeDefKind = TypeDefKind.Class(
        isFinal = false,
        isAbstract = false,
        isCase = false,
        isValue = false,
        bases = Nil
      )
      assertTrue(roundtrip(defKind))
    },
    test("roundtrip Class case") {
      val defKind: TypeDefKind = TypeDefKind.Class(
        isFinal = true,
        isAbstract = false,
        isCase = true,
        isValue = false,
        bases = Nil
      )
      assertTrue(roundtrip(defKind))
    },
    test("roundtrip Class abstract") {
      val defKind: TypeDefKind = TypeDefKind.Class(
        isFinal = false,
        isAbstract = true,
        isCase = false,
        isValue = false,
        bases = List(TypeRepr.Ref(TypeId.of[Serializable]))
      )
      assertTrue(roundtrip(defKind))
    },
    test("roundtrip Trait simple") {
      val defKind: TypeDefKind = TypeDefKind.Trait(
        isSealed = false,
        knownSubtypes = Nil,
        bases = Nil
      )
      assertTrue(roundtrip(defKind))
    },
    test("roundtrip Trait sealed") {
      val defKind: TypeDefKind = TypeDefKind.Trait(
        isSealed = true,
        knownSubtypes = List(TypeRepr.Ref(TypeId.of[Some[_]]), TypeRepr.Ref(TypeId.of[None.type])),
        bases = Nil
      )
      assertTrue(roundtrip(defKind))
    },
    test("roundtrip Object simple") {
      val defKind: TypeDefKind = TypeDefKind.Object(bases = Nil)
      assertTrue(roundtrip(defKind))
    },
    test("roundtrip Object with bases") {
      val defKind: TypeDefKind = TypeDefKind.Object(bases = List(TypeRepr.Ref(TypeId.of[Serializable])))
      assertTrue(roundtrip(defKind))
    },
    test("roundtrip Enum") {
      val caseInfo             = EnumCaseInfo("Red", 0, Nil, isObjectCase = true)
      val defKind: TypeDefKind = TypeDefKind.Enum(
        cases = List(caseInfo),
        bases = Nil
      )
      assertTrue(roundtrip(defKind))
    },
    test("roundtrip EnumCase simple") {
      val defKind: TypeDefKind = TypeDefKind.EnumCase(
        parentEnum = TypeRepr.Ref(TypeId.of[Option[_]]),
        ordinal = 0,
        isObjectCase = true
      )
      assertTrue(roundtrip(defKind))
    },
    test("roundtrip EnumCase with params") {
      val defKind: TypeDefKind = TypeDefKind.EnumCase(
        parentEnum = TypeRepr.Ref(TypeId.of[Option[_]]),
        ordinal = 1,
        isObjectCase = false
      )
      assertTrue(roundtrip(defKind))
    },
    test("roundtrip OpaqueType") {
      val defKind: TypeDefKind = TypeDefKind.OpaqueType(
        publicBounds = TypeBounds(None, None)
      )
      assertTrue(roundtrip(defKind))
    },
    test("roundtrip TypeAlias") {
      assertTrue(roundtrip[TypeDefKind](TypeDefKind.TypeAlias))
    },
    test("roundtrip AbstractType") {
      assertTrue(roundtrip[TypeDefKind](TypeDefKind.AbstractType))
    },
    test("roundtrip Unknown") {
      assertTrue(roundtrip[TypeDefKind](TypeDefKind.Unknown))
    }
  )

  lazy val typeIdSuite = suite("TypeId")(
    test("roundtrip simple TypeId") {
      assertTrue(roundtripTypeId(TypeId.of[String]))
    },
    test("roundtrip generic TypeId") {
      assertTrue(roundtripTypeId(TypeId.of[List[Int]]))
    },
    test("roundtrip Option TypeId") {
      assertTrue(roundtripTypeId(TypeId.of[Option[String]]))
    },
    test("roundtrip Map TypeId") {
      assertTrue(roundtripTypeId(TypeId.of[Map[String, Int]]))
    },
    test("roundtrip Either TypeId") {
      assertTrue(roundtripTypeId(TypeId.of[Either[String, Int]]))
    },
    test("roundtrip nested generic TypeId") {
      assertTrue(roundtripTypeId(TypeId.of[List[Option[Int]]]))
    },
    test("roundtrip tuple TypeId") {
      assertTrue(roundtripTypeId(TypeId.of[(Int, String)]))
    },
    test("roundtrip function TypeId") {
      assertTrue(roundtripTypeId(TypeId.of[Int => String]))
    }
  )
}
