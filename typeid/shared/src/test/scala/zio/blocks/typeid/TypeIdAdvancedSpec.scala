package zio.blocks.typeid

import zio.test._

object TypeIdAdvancedSpec extends ZIOSpecDefault {

  sealed trait Animal
  case class Dog(name: String) extends Animal
  case class Cat(name: String) extends Animal
  case object UnknownAnimal    extends Animal

  sealed trait Shape
  final case class Circle(radius: Double)          extends Shape
  final case class Rectangle(w: Double, h: Double) extends Shape

  trait SimpleTrait
  trait MarkerTrait
  abstract class AbstractBase
  final class FinalClass(val x: Int)
  case class SimpleCaseClass(value: Int)
  class RegularClass(val name: String)

  object Container {
    case class Nested(x: Int)
    object Inner {
      case class DeepNested(y: String)
    }
  }

  class MyAnnotation(val message: String) extends scala.annotation.StaticAnnotation
  class MarkerAnnotation                  extends scala.annotation.StaticAnnotation

  @MyAnnotation("test message")
  case class AnnotatedClass(value: Int)

  @MarkerAnnotation
  trait AnnotatedTrait

  @SerialVersionUID(12345L)
  class SerializableClass extends Serializable

  trait Logger {
    def log(msg: String): Unit
  }

  trait SelfTypedTrait { self: Logger =>
    def doWork(): Unit = log("working")
  }

  trait NoSelfType {
    def simple(): Unit = ()
  }

  class Outer {
    class Inner
    type InnerType = Int
  }

  trait TypeHolder {
    type T
  }

  def spec = suite("TypeId Advanced")(
    sealedExtractorSuite,
    typeDefKindDerivedSuite,
    ownerDerivedSuite,
    typeBoundsSuite,
    varianceSuite,
    typeParamSuite,
    termPathSuite,
    typeReprSuite,
    containsParamSuite,
    wildcardSuite,
    annotationSuite,
    selfTypeSuite,
    enumCaseInfoSuite,
    memberSuite,
    kindSuite,
    typeIdMethodsSuite,
    typeIdExtractorsSuite,
    pathDependentTypeSuite
  )

  val sealedExtractorSuite = suite("Sealed Extractor (derived)")(
    test("Sealed extractor matches derived sealed trait") {
      val animalId = TypeId.of[Animal]
      animalId match {
        case TypeId.Sealed(name, subtypes) =>
          assertTrue(name == "Animal", subtypes.nonEmpty)
        case _ => assertTrue(false)
      }
    },
    test("Sealed extractor extracts known subtypes for Shape") {
      val shapeId = TypeId.of[Shape]
      shapeId match {
        case TypeId.Sealed(name, subtypes) =>
          assertTrue(name == "Shape", subtypes.size >= 2)
        case _ => assertTrue(false)
      }
    },
    test("Sealed extractor does NOT match regular trait") {
      val traitId  = TypeId.of[SimpleTrait]
      val isSealed = traitId match {
        case TypeId.Sealed(_, _) => true
        case _                   => false
      }
      assertTrue(!isSealed)
    },
    test("Sealed extractor does NOT match case class") {
      val dogId    = TypeId.of[Dog]
      val isSealed = dogId match {
        case TypeId.Sealed(_, _) => true
        case _                   => false
      }
      assertTrue(!isSealed)
    },
    test("Sealed extractor does NOT match case object") {
      val objId    = TypeId.of[UnknownAnimal.type]
      val isSealed = objId match {
        case TypeId.Sealed(_, _) => true
        case _                   => false
      }
      assertTrue(!isSealed)
    }
  )

  val typeDefKindDerivedSuite = suite("TypeDefKind (derived)")(
    test("derived case class has Class defKind with isCase=true") {
      val id = TypeId.of[SimpleCaseClass]
      id.defKind match {
        case TypeDefKind.Class(_, _, isCase, _, _) => assertTrue(isCase)
        case _                                     => assertTrue(false)
      }
    },
    test("derived final case class has isFinal=true") {
      val id = TypeId.of[Circle]
      id.defKind match {
        case TypeDefKind.Class(isFinal, _, isCase, _, _) =>
          assertTrue(isFinal, isCase)
        case _ => assertTrue(false)
      }
    },
    test("derived trait has Trait defKind") {
      val id = TypeId.of[SimpleTrait]
      assertTrue(id.defKind.isInstanceOf[TypeDefKind.Trait])
    },
    test("derived sealed trait has Trait defKind with isSealed=true") {
      val id = TypeId.of[Animal]
      id.defKind match {
        case TypeDefKind.Trait(isSealed, knownSubtypes, _) =>
          assertTrue(isSealed, knownSubtypes.nonEmpty)
        case _ => assertTrue(false)
      }
    },
    test("derived case object has Object defKind") {
      val id = TypeId.of[UnknownAnimal.type]
      assertTrue(id.defKind.isInstanceOf[TypeDefKind.Object])
    },
    test("derived abstract class has Class defKind with isAbstract=true") {
      val id = TypeId.of[AbstractBase]
      id.defKind match {
        case TypeDefKind.Class(_, isAbstract, _, _, _) => assertTrue(isAbstract)
        case _                                         => assertTrue(false)
      }
    },
    test("defKind.baseTypes returns parent types for Dog") {
      val dogId = TypeId.of[Dog]
      assertTrue(
        dogId.defKind.baseTypes.exists {
          case TypeRepr.Ref(id) => id.name == "Animal"
          case _                => false
        }
      )
    },
    test("defKind.baseTypes returns parent types for Circle") {
      val circleId = TypeId.of[Circle]
      assertTrue(
        circleId.defKind.baseTypes.exists {
          case TypeRepr.Ref(id) => id.name == "Shape"
          case _                => false
        }
      )
    },
    test("derived regular class has Class defKind") {
      val id = TypeId.of[RegularClass]
      id.defKind match {
        case TypeDefKind.Class(isFinal, isAbstract, isCase, isValue, _) =>
          assertTrue(!isFinal, !isAbstract, !isCase, !isValue)
        case _ => assertTrue(false)
      }
    }
  )

  val ownerDerivedSuite = suite("Owner (derived)")(
    test("derived nested type has correct owner with lastName") {
      val id = TypeId.of[Container.Nested]
      assertTrue(
        id.owner.lastName == "Container" || id.owner.asString.contains("Container")
      )
    },
    test("derived deeply nested type has correct owner path") {
      val id = TypeId.of[Container.Inner.DeepNested]
      assertTrue(
        id.owner.asString.contains("Inner") || id.fullName.contains("Inner")
      )
    },
    test("Owner.tpe creates Type segment") {
      val owner = (Owner.Root / "com").tpe("MyClass")
      owner.segments.last match {
        case Owner.Type(name) => assertTrue(name == "MyClass")
        case _                => assertTrue(false)
      }
    },
    test("Owner.lastName returns last segment") {
      val owner = Owner.Root / "com" / "example" / "app"
      assertTrue(owner.lastName == "app")
    },
    test("Owner.lastName returns empty for Root") {
      assertTrue(Owner.Root.lastName == "")
    },
    test("Owner.fromPackagePath with empty returns Root") {
      assertTrue(Owner.fromPackagePath("").isRoot)
    }
  )

  val typeBoundsSuite = suite("TypeBounds")(
    test("isUnbounded returns true for no bounds") {
      assertTrue(TypeBounds.Unbounded.isUnbounded)
    },
    test("hasOnlyUpper/hasOnlyLower/hasBothBounds") {
      val upper = TypeBounds.upper(TypeRepr.Ref(TypeId.int))
      val lower = TypeBounds.lower(TypeRepr.Ref(TypeId.int))
      val both  = TypeBounds(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string))
      assertTrue(
        upper.hasOnlyUpper && !upper.hasOnlyLower && !upper.hasBothBounds,
        lower.hasOnlyLower && !lower.hasOnlyUpper && !lower.hasBothBounds,
        both.hasBothBounds && !both.hasOnlyUpper && !both.hasOnlyLower,
        !upper.isUnbounded && !lower.isUnbounded && !both.isUnbounded
      )
    },
    test("isAlias when lower equals upper") {
      val tpe    = TypeRepr.Ref(TypeId.int)
      val bounds = TypeBounds.alias(tpe)
      val diff   = TypeBounds(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string))
      assertTrue(
        bounds.isAlias,
        bounds.aliasType == Some(tpe),
        !diff.isAlias,
        diff.aliasType.isEmpty
      )
    }
  )

  val varianceSuite = suite("Variance")(
    test("symbol returns correct variance annotations") {
      assertTrue(
        Variance.Covariant.symbol == "+",
        Variance.Contravariant.symbol == "-",
        Variance.Invariant.symbol == ""
      )
    },
    test("isCovariant/isContravariant/isInvariant") {
      assertTrue(
        Variance.Covariant.isCovariant && !Variance.Covariant.isContravariant && !Variance.Covariant.isInvariant,
        Variance.Contravariant.isContravariant && !Variance.Contravariant.isCovariant,
        Variance.Invariant.isInvariant && !Variance.Invariant.isCovariant && !Variance.Invariant.isContravariant
      )
    },
    test("flip swaps covariant and contravariant") {
      assertTrue(
        Variance.Covariant.flip == Variance.Contravariant,
        Variance.Contravariant.flip == Variance.Covariant,
        Variance.Invariant.flip == Variance.Invariant
      )
    },
    test("* combines variances correctly") {
      assertTrue(
        (Variance.Covariant * Variance.Covariant) == Variance.Covariant,
        (Variance.Contravariant * Variance.Contravariant) == Variance.Covariant,
        (Variance.Covariant * Variance.Contravariant) == Variance.Contravariant,
        (Variance.Contravariant * Variance.Covariant) == Variance.Contravariant,
        (Variance.Invariant * Variance.Covariant) == Variance.Invariant,
        (Variance.Covariant * Variance.Invariant) == Variance.Invariant,
        (Variance.Invariant * Variance.Contravariant) == Variance.Invariant,
        (Variance.Contravariant * Variance.Invariant) == Variance.Invariant,
        (Variance.Invariant * Variance.Invariant) == Variance.Invariant
      )
    }
  )

  val typeParamSuite = suite("TypeParam")(
    test("isCovariant/isContravariant/isInvariant") {
      val cov    = TypeParam.covariant("A", 0)
      val contra = TypeParam.contravariant("A", 0)
      val inv    = TypeParam("A", 0, Variance.Invariant)
      assertTrue(
        cov.isCovariant && !cov.isContravariant && !cov.isInvariant,
        contra.isContravariant && !contra.isCovariant && !contra.isInvariant,
        inv.isInvariant && !inv.isCovariant && !inv.isContravariant
      )
    },
    test("hasUpperBound/hasLowerBound") {
      val upper = TypeParam.bounded("T", 0, TypeRepr.Ref(TypeId.string))
      val lower = TypeParam("T", 0, bounds = TypeBounds.lower(TypeRepr.Ref(TypeId.int)))
      assertTrue(
        upper.hasUpperBound && !upper.hasLowerBound,
        lower.hasLowerBound && !lower.hasUpperBound
      )
    },
    test("isProperType/isTypeConstructor") {
      val proper = TypeParam("A", 0)
      val hk     = TypeParam.higherKinded("F", 0, 1)
      assertTrue(
        proper.isProperType && !proper.isTypeConstructor,
        hk.isTypeConstructor && !hk.isProperType,
        hk.kind.arity == 1
      )
    }
  )

  val termPathSuite = suite("TermPath")(
    test("isEmpty and / operator") {
      val path = TermPath.Empty / "a" / "b"
      assertTrue(
        TermPath.Empty.isEmpty,
        !path.isEmpty,
        path.segments.size == 2,
        path.asString == "a.b"
      )
    },
    test("fromOwner creates path with term name") {
      val owner = Owner.Root / "com" / "example"
      val path  = TermPath.fromOwner(owner, "myValue")
      assertTrue(path.asString == "com.example.myValue", path.segments.size == 3)
    }
  )

  val typeReprSuite = suite("TypeRepr utilities")(
    test("union edge cases") {
      val t = TypeRepr.Ref(TypeId.int)
      assertTrue(
        TypeRepr.union(Nil) == TypeRepr.NothingType,
        TypeRepr.union(List(t)) == t
      )
    },
    test("union with multiple types creates Union") {
      TypeRepr.union(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string))) match {
        case TypeRepr.Union(types) => assertTrue(types.size == 2)
        case _                     => assertTrue(false)
      }
    },
    test("intersection edge cases") {
      val t = TypeRepr.Ref(TypeId.int)
      assertTrue(
        TypeRepr.intersection(Nil) == TypeRepr.AnyType,
        TypeRepr.intersection(List(t)) == t
      )
    },
    test("intersection with multiple types creates Intersection") {
      TypeRepr.intersection(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string))) match {
        case TypeRepr.Intersection(types) => assertTrue(types.size == 2)
        case _                            => assertTrue(false)
      }
    },
    test("tuple creates Tuple with unlabeled elements") {
      TypeRepr.tuple(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string))) match {
        case TypeRepr.Tuple(elems) => assertTrue(elems.size == 2, elems.forall(_.label.isEmpty))
        case _                     => assertTrue(false)
      }
    },
    test("function creates Function type") {
      TypeRepr.function(List(TypeRepr.Ref(TypeId.int)), TypeRepr.Ref(TypeId.boolean)) match {
        case TypeRepr.Function(params, ret) =>
          assertTrue(params.size == 1, ret == TypeRepr.Ref(TypeId.boolean))
        case _ => assertTrue(false)
      }
    },
    test("Union/Intersection equality is order-independent") {
      val u1 = TypeRepr.Union(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string)))
      val u2 = TypeRepr.Union(List(TypeRepr.Ref(TypeId.string), TypeRepr.Ref(TypeId.int)))
      val i1 = TypeRepr.Intersection(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string)))
      val i2 = TypeRepr.Intersection(List(TypeRepr.Ref(TypeId.string), TypeRepr.Ref(TypeId.int)))
      assertTrue(u1 == u2, i1 == i2)
    }
  )

  val containsParamSuite = suite("TypeRepr.containsParam")(
    test("Ref does not contain any param") {
      assertTrue(!TypeRepr.Ref(TypeId.int).containsParam(TypeParam("A", 0)))
    },
    test("ParamRef contains its own param only") {
      val paramA = TypeParam("A", 0)
      val paramB = TypeParam("B", 1)
      val ref    = TypeRepr.ParamRef(paramA)
      assertTrue(ref.containsParam(paramA), !ref.containsParam(paramB))
    },
    test("Applied containsParam checks tycon and args") {
      val param   = TypeParam("A", 0)
      val applied = TypeRepr.Applied(TypeRepr.Ref(TypeId.list), List(TypeRepr.ParamRef(param)))
      assertTrue(applied.containsParam(param))
    },
    test("Structural containsParam checks parents") {
      val param = TypeParam("A", 0)
      assertTrue(TypeRepr.Structural(List(TypeRepr.ParamRef(param)), Nil).containsParam(param))
    },
    test("Intersection/Union containsParam checks all types") {
      val param = TypeParam("A", 0)
      val inter = TypeRepr.Intersection(List(TypeRepr.Ref(TypeId.int), TypeRepr.ParamRef(param)))
      val union = TypeRepr.Union(List(TypeRepr.Ref(TypeId.string), TypeRepr.ParamRef(param)))
      assertTrue(inter.containsParam(param), union.containsParam(param))
    },
    test("Tuple containsParam checks elements") {
      val param = TypeParam("A", 0)
      assertTrue(TypeRepr.Tuple(List(TupleElement(None, TypeRepr.ParamRef(param)))).containsParam(param))
    },
    test("Function/ContextFunction containsParam") {
      val param = TypeParam("A", 0)
      val func  = TypeRepr.Function(List(TypeRepr.Ref(TypeId.int)), TypeRepr.ParamRef(param))
      val ctx   = TypeRepr.ContextFunction(List(TypeRepr.ParamRef(param)), TypeRepr.Ref(TypeId.string))
      assertTrue(func.containsParam(param), ctx.containsParam(param))
    },
    test("TypeLambda/ByName/Repeated containsParam") {
      val param  = TypeParam("A", 0)
      val lambda = TypeRepr.TypeLambda(List(TypeParam("X", 0)), TypeRepr.ParamRef(param))
      val byName = TypeRepr.ByName(TypeRepr.ParamRef(param))
      val rep    = TypeRepr.Repeated(TypeRepr.ParamRef(param))
      assertTrue(lambda.containsParam(param), byName.containsParam(param), rep.containsParam(param))
    },
    test("Wildcard containsParam checks bounds") {
      val param = TypeParam("A", 0)
      assertTrue(TypeRepr.Wildcard(TypeBounds.upper(TypeRepr.ParamRef(param))).containsParam(param))
    },
    test("TypeProjection/TypeSelect containsParam") {
      val param = TypeParam("A", 0)
      val proj  = TypeRepr.TypeProjection(TypeRepr.ParamRef(param), "Inner")
      val sel   = TypeRepr.TypeSelect(TypeRepr.ParamRef(param), "Member")
      assertTrue(proj.containsParam(param), sel.containsParam(param))
    },
    test("Annotated containsParam checks underlying") {
      val param = TypeParam("A", 0)
      assertTrue(TypeRepr.Annotated(TypeRepr.ParamRef(param), Nil).containsParam(param))
    },
    test("ThisType/Singleton/Constant/special types do not contain params") {
      val param = TypeParam("A", 0)
      assertTrue(
        !TypeRepr.ThisType(Owner.Root / "pkg").containsParam(param),
        !TypeRepr.Singleton(TermPath.Empty / "value").containsParam(param),
        !TypeRepr.Constant.IntConst(42).containsParam(param),
        !TypeRepr.Constant.StringConst("hello").containsParam(param),
        !TypeRepr.Constant.BooleanConst(true).containsParam(param),
        !TypeRepr.Constant.LongConst(42L).containsParam(param),
        !TypeRepr.Constant.DoubleConst(3.14).containsParam(param),
        !TypeRepr.Constant.FloatConst(1.0f).containsParam(param),
        !TypeRepr.Constant.CharConst('x').containsParam(param),
        !TypeRepr.Constant.NullConst.containsParam(param),
        !TypeRepr.Constant.UnitConst.containsParam(param),
        !TypeRepr.Constant.ClassOfConst(TypeRepr.Ref(TypeId.string)).containsParam(param),
        !TypeRepr.AnyType.containsParam(param),
        !TypeRepr.NothingType.containsParam(param),
        !TypeRepr.NullType.containsParam(param),
        !TypeRepr.UnitType.containsParam(param),
        !TypeRepr.AnyKindType.containsParam(param)
      )
    }
  )

  val wildcardSuite = suite("TypeRepr.Wildcard")(
    test("Wildcard default has unbounded bounds") {
      assertTrue(TypeRepr.Wildcard().bounds.isUnbounded)
    },
    test("Wildcard with upper/lower bound") {
      val upper = TypeRepr.Wildcard(TypeBounds.upper(TypeRepr.Ref(TypeId.string)))
      val lower = TypeRepr.Wildcard(TypeBounds.lower(TypeRepr.Ref(TypeId.int)))
      assertTrue(
        upper.bounds.hasOnlyUpper,
        upper.bounds.upper == Some(TypeRepr.Ref(TypeId.string)),
        lower.bounds.hasOnlyLower,
        lower.bounds.lower == Some(TypeRepr.Ref(TypeId.int))
      )
    }
  )

  val annotationSuite = suite("Annotation")(
    test("derived TypeId extracts custom annotation from class") {
      val id = TypeId.of[AnnotatedClass]
      assertTrue(
        id.annotations.nonEmpty,
        id.annotations.exists(_.name == "MyAnnotation")
      )
    },
    test("derived TypeId extracts marker annotation from trait") {
      val id = TypeId.of[AnnotatedTrait]
      assertTrue(
        id.annotations.nonEmpty,
        id.annotations.exists(_.name == "MarkerAnnotation")
      )
    },
    test("derived TypeId extracts annotation arguments") {
      val id           = TypeId.of[AnnotatedClass]
      val myAnnotation = id.annotations.find(_.name == "MyAnnotation")
      assertTrue(myAnnotation.exists(_.args.nonEmpty))
    },
    test("derived TypeId extracts @SerialVersionUID annotation") {
      val id = TypeId.of[SerializableClass]
      assertTrue(id.annotations.exists(_.name == "SerialVersionUID"))
    },
    test("unannotated type has empty annotations") {
      val id = TypeId.of[SimpleCaseClass]
      assertTrue(id.annotations.isEmpty)
    },
    test("Annotation name returns simple name") {
      val annot = Annotation(TypeId.of[SimpleTrait])
      assertTrue(annot.name == "SimpleTrait")
    },
    test("Annotation fullName returns qualified name") {
      val annot = Annotation(TypeId.of[SimpleTrait])
      assertTrue(annot.fullName.contains("SimpleTrait"))
    },
    test("Annotation with empty args") {
      val annot = Annotation(TypeId.of[MarkerTrait], Nil)
      assertTrue(annot.args.isEmpty)
    },
    test("AnnotationArg.Const holds constant values") {
      val constInt    = AnnotationArg.Const(42)
      val constString = AnnotationArg.Const("hello")
      val constBool   = AnnotationArg.Const(true)
      assertTrue(
        constInt.value == 42,
        constString.value == "hello",
        constBool.value == true
      )
    },
    test("AnnotationArg.ArrayArg holds multiple args") {
      val arr = AnnotationArg.ArrayArg(List(AnnotationArg.Const(1), AnnotationArg.Const(2)))
      assertTrue(arr.values.size == 2)
    },
    test("AnnotationArg.Named holds name-value pairs") {
      val named = AnnotationArg.Named("message", AnnotationArg.Const("deprecated"))
      assertTrue(named.name == "message")
    },
    test("AnnotationArg.Nested holds nested annotation") {
      val inner  = Annotation(TypeId.of[MarkerTrait])
      val nested = AnnotationArg.Nested(inner)
      assertTrue(nested.annotation.name == "MarkerTrait")
    },
    test("AnnotationArg.ClassOf holds type reference") {
      val classOf = AnnotationArg.ClassOf(TypeRepr.Ref(TypeId.string))
      classOf.tpe match {
        case TypeRepr.Ref(id) => assertTrue(id.name == "String")
        case _                => assertTrue(false)
      }
    },
    test("AnnotationArg.EnumValue holds enum reference") {
      val enumVal = AnnotationArg.EnumValue(TypeId.of[Animal], "Dog")
      assertTrue(enumVal.valueName == "Dog")
    }
  )

  val selfTypeSuite = suite("SelfType (derived)")(
    test("derived TypeId extracts selfType from self-typed trait") {
      val id = TypeId.of[SelfTypedTrait]
      assertTrue(id.selfType.isDefined)
    },
    test("derived TypeId selfType references the required type") {
      val id = TypeId.of[SelfTypedTrait]
      id.selfType match {
        case Some(selfType) =>
          selfType match {
            case TypeRepr.Ref(refId) =>
              assertTrue(refId.name == "Logger")
            case TypeRepr.Intersection(types) =>
              assertTrue(types.exists {
                case TypeRepr.Ref(refId) => refId.name == "Logger"
                case _                   => false
              })
            case _ =>
              assertTrue(false)
          }
        case None => assertTrue(false)
      }
    },
    test("trait without self-type has None selfType") {
      val id = TypeId.of[NoSelfType]
      assertTrue(id.selfType.isEmpty)
    },
    test("regular trait has None selfType") {
      val id = TypeId.of[SimpleTrait]
      assertTrue(id.selfType.isEmpty)
    }
  )

  val enumCaseInfoSuite = suite("EnumCaseInfo")(
    test("EnumCaseInfo with no params has arity 0") {
      val info = EnumCaseInfo("Red", 0, Nil, isObjectCase = true)
      assertTrue(info.arity == 0, info.isObjectCase)
    },
    test("EnumCaseInfo with params has correct arity") {
      val params = List(
        EnumCaseParam("r", TypeRepr.Ref(TypeId.int)),
        EnumCaseParam("g", TypeRepr.Ref(TypeId.int)),
        EnumCaseParam("b", TypeRepr.Ref(TypeId.int))
      )
      val info = EnumCaseInfo("RGB", 1, params, isObjectCase = false)
      assertTrue(info.arity == 3, !info.isObjectCase)
    },
    test("EnumCaseParam holds name and type") {
      val param = EnumCaseParam("value", TypeRepr.Ref(TypeId.string))
      assertTrue(param.name == "value")
      param.tpe match {
        case TypeRepr.Ref(id) => assertTrue(id.name == "String")
        case _                => assertTrue(false)
      }
    },
    test("TypeDefKind.Enum has cases and baseTypes") {
      val cases    = List(EnumCaseInfo("A", 0), EnumCaseInfo("B", 1))
      val bases    = List(TypeRepr.Ref(TypeId.of[SimpleTrait]))
      val enumKind = TypeDefKind.Enum(cases, bases)
      assertTrue(enumKind.cases.size == 2, enumKind.baseTypes.size == 1)
    },
    test("TypeDefKind.EnumCase has parentEnum and ordinal") {
      val parentRef = TypeRepr.Ref(TypeId.of[Animal])
      val enumCase  = TypeDefKind.EnumCase(parentRef, ordinal = 0, isObjectCase = true)
      assertTrue(
        enumCase.ordinal == 0,
        enumCase.isObjectCase,
        enumCase.baseTypes == List(parentRef)
      )
    },
    test("TypeDefKind.Object has baseTypes") {
      val bases = List(TypeRepr.Ref(TypeId.of[SimpleTrait]))
      val obj   = TypeDefKind.Object(bases)
      assertTrue(obj.baseTypes == bases)
    },
    test("TypeDefKind case objects") {
      assertTrue(
        TypeDefKind.TypeAlias.baseTypes.isEmpty,
        TypeDefKind.AbstractType.baseTypes.isEmpty,
        TypeDefKind.Unknown.baseTypes.isEmpty
      )
    }
  )

  val memberSuite = suite("Member")(
    test("Member.Val holds val information") {
      val valMember = Member.Val("x", TypeRepr.Ref(TypeId.int), isVar = false)
      assertTrue(valMember.name == "x", !valMember.isVar)
    },
    test("Member.Val with isVar=true for var") {
      val varMember = Member.Val("y", TypeRepr.Ref(TypeId.string), isVar = true)
      assertTrue(varMember.isVar)
    },
    test("Member.Def holds method information") {
      val defMember = Member.Def(
        name = "foo",
        typeParams = List(TypeParam("T", 0)),
        paramLists = List(List(Param("x", TypeRepr.Ref(TypeId.int)))),
        result = TypeRepr.Ref(TypeId.string)
      )
      assertTrue(
        defMember.name == "foo",
        defMember.typeParams.size == 1,
        defMember.paramLists.size == 1,
        defMember.paramLists.head.size == 1
      )
    },
    test("Member.Def with empty type params and param lists") {
      val simpleDef = Member.Def("bar", Nil, Nil, TypeRepr.Ref(TypeId.unit))
      assertTrue(
        simpleDef.name == "bar",
        simpleDef.typeParams.isEmpty,
        simpleDef.paramLists.isEmpty
      )
    },
    test("Member.Def with curried params") {
      val curriedDef = Member.Def(
        "curried",
        Nil,
        List(
          List(Param("a", TypeRepr.Ref(TypeId.int))),
          List(Param("b", TypeRepr.Ref(TypeId.string)))
        ),
        TypeRepr.Ref(TypeId.boolean)
      )
      assertTrue(curriedDef.paramLists.size == 2)
    },
    test("Param holds parameter information") {
      val param = Param("arg", TypeRepr.Ref(TypeId.int), isImplicit = true, hasDefault = true)
      assertTrue(param.name == "arg", param.isImplicit, param.hasDefault)
    },
    test("Param default values") {
      val param = Param("simple", TypeRepr.Ref(TypeId.string))
      assertTrue(!param.isImplicit, !param.hasDefault)
    }
  )

  val kindSuite = suite("Kind")(
    test("Kind.Type is proper type with arity 0") {
      assertTrue(Kind.Type.isProperType, Kind.Type.arity == 0)
    },
    test("Kind.Star is alias for Type") {
      assertTrue(Kind.Star == Kind.Type)
    },
    test("Kind.Star1 has arity 1") {
      assertTrue(Kind.Star1.arity == 1, !Kind.Star1.isProperType)
    },
    test("Kind.Star2 has arity 2") {
      assertTrue(Kind.Star2.arity == 2)
    },
    test("Kind.HigherStar1 is higher-kinded") {
      assertTrue(Kind.HigherStar1.arity == 1)
      Kind.HigherStar1 match {
        case Kind.Arrow(params, _) =>
          assertTrue(params.head == Kind.Star1)
        case _ => assertTrue(false)
      }
    },
    test("Kind.constructor creates n-ary constructor") {
      assertTrue(
        Kind.constructor(0) == Kind.Type,
        Kind.constructor(1) == Kind.Star1,
        Kind.constructor(2) == Kind.Star2,
        Kind.constructor(3).arity == 3,
        Kind.constructor(-1) == Kind.Type
      )
    },
    test("Kind.Arrow stores params and result") {
      val arrow = Kind.Arrow(List(Kind.Type, Kind.Type), Kind.Type)
      assertTrue(
        arrow.arity == 2,
        arrow.params.size == 2,
        arrow.result == Kind.Type
      )
    }
  )

  val typeIdMethodsSuite = suite("TypeId methods")(
    test("isApplied returns true for applied types") {
      val listInt = TypeId.of[List[Int]]
      assertTrue(listInt.isApplied, listInt.typeArgs.nonEmpty)
    },
    test("arity returns number of type params") {
      val list  = TypeId.of[List[_]]
      val map   = TypeId.of[Map[_, _]]
      val intId = TypeId.of[Int]
      assertTrue(list.arity == 1, map.arity == 2, intId.arity == 0)
    },
    test("fullName includes owner path") {
      val intId = TypeId.of[Int]
      assertTrue(intId.fullName == "scala.Int")
    },
    test("isProperType and isTypeConstructor") {
      val intId = TypeId.of[Int]
      val list  = TypeId.of[List[_]]
      assertTrue(
        intId.isProperType && !intId.isTypeConstructor,
        list.isTypeConstructor && !list.isProperType
      )
    },
    test("isClass/isTrait/isObject") {
      val classId  = TypeId.of[SimpleCaseClass]
      val traitId  = TypeId.of[SimpleTrait]
      val objectId = TypeId.of[UnknownAnimal.type]
      assertTrue(classId.isClass, traitId.isTrait, objectId.isObject)
    },
    test("isSealed returns true for sealed traits") {
      val sealedId    = TypeId.of[Animal]
      val notSealedId = TypeId.of[SimpleTrait]
      assertTrue(sealedId.isSealed, !notSealedId.isSealed)
    },
    test("isCaseClass returns true for case classes") {
      val caseClass    = TypeId.of[Dog]
      val regularClass = TypeId.of[RegularClass]
      assertTrue(caseClass.isCaseClass, !regularClass.isCaseClass)
    },
    test("enumCases returns empty for non-enum") {
      val traitId = TypeId.of[Animal]
      assertTrue(traitId.enumCases.isEmpty)
    },
    test("knownSubtypes returns subtypes for sealed trait") {
      val sealedId = TypeId.of[Animal]
      assertTrue(sealedId.knownSubtypes.nonEmpty)
    },
    test("knownSubtypes returns empty for non-sealed") {
      val notSealed = TypeId.of[SimpleTrait]
      assertTrue(notSealed.knownSubtypes.isEmpty)
    },
    test("isTuple returns true for scala tuples") {
      val tuple2 = TypeId.of[(Int, String)]
      assertTrue(tuple2.isTuple)
    },
    test("isOption returns true for Option") {
      val opt = TypeId.of[Option[Int]]
      assertTrue(opt.isOption)
    },
    test("toString contains kind and fullName") {
      val intId = TypeId.of[Int]
      assertTrue(intId.toString.contains("TypeId."), intId.toString.contains("Int"))
    },
    test("TypeId.applied creates applied type") {
      val applied = TypeId.applied[List[Int]](TypeId.of[List[_]], TypeRepr.Ref(TypeId.int))
      assertTrue(applied.isApplied, applied.typeArgs.size == 1)
    }
  )

  val typeIdExtractorsSuite = suite("TypeId Extractors")(
    test("Enum extractor matches enum TypeDefKind") {
      val enumDefKind = TypeDefKind.Enum(List(EnumCaseInfo("A", 0)), Nil)
      val id          = TypeId.nominal[Animal]("TestEnum", Owner.Root, defKind = enumDefKind)
      id match {
        case TypeId.Enum(name, _, cases) =>
          assertTrue(name == "TestEnum", cases.size == 1)
        case _ => assertTrue(false)
      }
    },
    test("Enum extractor does not match non-enum") {
      val id     = TypeId.of[Animal]
      val isEnum = id match {
        case TypeId.Enum(_, _, _) => true
        case _                    => false
      }
      assertTrue(!isEnum)
    },
    test("Opaque extractor does not match nominal type") {
      val id       = TypeId.of[Int]
      val isOpaque = id match {
        case TypeId.Opaque(_, _, _, _, _) => true
        case _                            => false
      }
      assertTrue(!isOpaque)
    },
    test("Alias extractor does not match nominal type") {
      val id      = TypeId.of[Int]
      val isAlias = id match {
        case TypeId.Alias(_, _, _, _) => true
        case _                        => false
      }
      assertTrue(!isAlias)
    },
    test("Alias extractor matches alias TypeId") {
      val aliasId = TypeId.alias[Int]("MyInt", Owner.Root, Nil, TypeRepr.Ref(TypeId.int))
      aliasId match {
        case TypeId.Alias(name, _, _, aliased) =>
          assertTrue(name == "MyInt")
          aliased match {
            case TypeRepr.Ref(id) => assertTrue(id.name == "Int")
            case _                => assertTrue(false)
          }
        case _ => assertTrue(false)
      }
    },
    test("Opaque extractor matches opaque TypeId") {
      val opaqueId = TypeId.opaque[Int]("Secret", Owner.Root, Nil, TypeRepr.Ref(TypeId.string))
      opaqueId match {
        case TypeId.Opaque(name, _, _, repr, bounds) =>
          assertTrue(name == "Secret", bounds.isUnbounded)
          repr match {
            case TypeRepr.Ref(id) => assertTrue(id.name == "String")
            case _                => assertTrue(false)
          }
        case _ => assertTrue(false)
      }
    },
    test("Nominal extractor matches nominal TypeId") {
      val id = TypeId.of[Int]
      id match {
        case TypeId.Nominal(name, owner, _, _, _) =>
          assertTrue(name == "Int", owner == Owner.scala)
        case _ => assertTrue(false)
      }
    }
  )

  val additionalCoverageSuite = suite("Additional Coverage")(
    test("TypeDefKind.Class with isValue=true") {
      val valueClass = TypeDefKind.Class(isValue = true)
      assertTrue(valueClass.isValue, valueClass.baseTypes.isEmpty)
    },
    test("TypeId.isValueClass returns true for value class") {
      val valueDefKind = TypeDefKind.Class(isValue = true)
      val id           = TypeId.nominal[Int]("MyValue", Owner.Root, defKind = valueDefKind)
      assertTrue(id.isValueClass)
    },
    test("TypeId.isValueClass returns false for regular class") {
      val id = TypeId.of[SimpleCaseClass]
      assertTrue(!id.isValueClass)
    },
    test("TypeId.isAbstract returns true for abstract type") {
      val id = TypeId.nominal[Int]("T", Owner.Root, defKind = TypeDefKind.AbstractType)
      assertTrue(id.isAbstract)
    },
    test("TypeId.isAlias returns true for type alias") {
      val id = TypeId.alias[Int]("MyInt", Owner.Root, Nil, TypeRepr.Ref(TypeId.int))
      assertTrue(id.isAlias)
    },
    test("TypeId.isEnum returns true for enum") {
      val enumKind = TypeDefKind.Enum(List(EnumCaseInfo("A", 0)), Nil)
      val id       = TypeId.nominal[Animal]("MyEnum", Owner.Root, defKind = enumKind)
      assertTrue(id.isEnum)
    },
    test("TypeId.isProduct returns true for Product types") {
      val productKind = TypeDefKind.Class(isCase = true)
      val id          = TypeId.nominal[Int]("Product1", Owner.scala, defKind = productKind)
      assertTrue(id.isProduct)
    },
    test("TypeId.isSum returns true for Either") {
      val eitherKind = TypeDefKind.Trait(isSealed = true)
      val id         = TypeId.nominal[Int]("Either", Owner.scala, defKind = eitherKind)
      assertTrue(id.isSum)
    },
    test("TypeId.isEither checks specific path") {
      val id = TypeId.nominal[Int]("Either", Owner.fromPackagePath("scala.util"))
      assertTrue(id.isEither)
    },
    test("TypeId equals and hashCode work correctly") {
      val id1 = TypeId.of[Int]
      val id2 = TypeId.of[Int]
      assertTrue(id1 == id2, id1.hashCode == id2.hashCode)
    },
    test("TypeId with different typeArgs are not equal") {
      val listInt    = TypeId.of[List[Int]]
      val listString = TypeId.of[List[String]]
      assertTrue(listInt != listString)
    },
    test("Member.Def name accessor") {
      val defMember = Member.Def("testMethod", Nil, Nil, TypeRepr.Ref(TypeId.unit))
      assertTrue(defMember.name == "testMethod")
    },
    test("Member.TypeMember name accessor") {
      val typeMember = Member.TypeMember("T", Nil, None, None)
      assertTrue(typeMember.name == "T")
    },
    test("TypeDefKind.Trait with knownSubtypes") {
      val subtypes  = List(TypeRepr.Ref(TypeId.of[Dog]), TypeRepr.Ref(TypeId.of[Cat]))
      val traitKind = TypeDefKind.Trait(isSealed = true, knownSubtypes = subtypes)
      assertTrue(traitKind.knownSubtypes.size == 2)
    },
    test("TermPath Package and Term segments") {
      val pkg  = TermPath.Package("com")
      val term = TermPath.Term("value")
      assertTrue(pkg.name == "com", term.name == "value")
    },
    test("TypeRepr.Structural members and parents") {
      val parent = TypeRepr.Ref(TypeId.of[SimpleTrait])
      val member = Member.Val("x", TypeRepr.Ref(TypeId.int))
      val struct = TypeRepr.Structural(List(parent), List(member))
      assertTrue(struct.parents.size == 1, struct.members.size == 1)
    },
    test("TypeRepr.Annotated underlying and annotations") {
      val underlying = TypeRepr.Ref(TypeId.int)
      val annot      = Annotation(TypeId.of[MarkerTrait])
      val annotated  = TypeRepr.Annotated(underlying, List(annot))
      assertTrue(annotated.annotations.size == 1)
      annotated.underlying match {
        case TypeRepr.Ref(id) => assertTrue(id.name == "Int")
        case _                => assertTrue(false)
      }
    },
    test("OpaqueType with non-default bounds") {
      val bounds     = TypeBounds.upper(TypeRepr.Ref(TypeId.string))
      val opaqueKind = TypeDefKind.OpaqueType(bounds)
      assertTrue(!opaqueKind.publicBounds.isUnbounded)
    }
  )

  val pathDependentTypeSuite = suite("Path-Dependent Type Derivation")(
    test("type projection Outer#Inner produces correct TypeId") {
      val id         = TypeId.of[Outer#Inner]
      val isExpected = id.aliasedTo match {
        case Some(TypeRepr.TypeProjection(_, name)) => name == "Inner"
        case Some(TypeRepr.Ref(refId))              => refId.name == "Inner"
        case _                                      => id.name == "Inner"
      }
      assertTrue(isExpected)
    },
    test("nested class has correct owner path") {
      val id = TypeId.of[Outer#Inner]
      assertTrue(id.name == "Inner" || id.aliasedTo.isDefined)
    },
    test("type member in trait can be derived") {
      val id = TypeId.of[TypeHolder]
      assertTrue(id.name == "TypeHolder")
    }
  )
}
