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
    wildcardSuite
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
}
