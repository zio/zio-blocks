package zio.blocks.typeid

import zio.test._

/**
 * Comprehensive tests for DynamicTypeId data structure.
 * Covers: fullName, arity, kind checks, aliasedTo, representation, enumCases, subtyping, equality.
 */
object DynamicTypeIdSpec extends ZIOSpecDefault {

  private val scalaOwner = Owner.pkg("scala")
  private val javaLang = Owner.pkgs("java", "lang")

  private def makeClass(
    owner: Owner,
    name: String,
    params: List[TypeParam] = Nil,
    isFinal: Boolean = false,
    isAbstract: Boolean = false,
    isCase: Boolean = false,
    isValue: Boolean = false
  ): DynamicTypeId =
    DynamicTypeId(owner, name, params, TypeDefKind.Class(isFinal, isAbstract, isCase, isValue), Nil)

  private def makeTrait(
    owner: Owner,
    name: String,
    params: List[TypeParam] = Nil,
    isSealed: Boolean = false
  ): DynamicTypeId =
    DynamicTypeId(owner, name, params, TypeDefKind.Trait(isSealed), Nil)

  private def makeObject(owner: Owner, name: String): DynamicTypeId =
    DynamicTypeId(owner, name, Nil, TypeDefKind.Object, Nil)

  private def makeEnum(owner: Owner, name: String, cases: List[EnumCaseInfo]): DynamicTypeId =
    DynamicTypeId(owner, name, Nil, TypeDefKind.Enum(cases), Nil)

  private def makeAlias(owner: Owner, name: String, alias: TypeRepr): DynamicTypeId =
    DynamicTypeId(owner, name, Nil, TypeDefKind.TypeAlias(alias), Nil)

  private def makeOpaque(owner: Owner, name: String, repr: TypeRepr): DynamicTypeId =
    DynamicTypeId(owner, name, Nil, TypeDefKind.OpaqueType(TypeBounds(None, Some(repr))), Nil)

  private def makeAbstract(owner: Owner, name: String): DynamicTypeId =
    DynamicTypeId(owner, name, Nil, TypeDefKind.AbstractType, Nil)

  def spec: Spec[TestEnvironment, Any] = suite("DynamicTypeIdSpec")(
    suite("fullName")(
      test("fullName with non-root owner") {
        val id = makeClass(javaLang, "String")
        assertTrue(id.fullName == "java.lang.String")
      },
      test("fullName with root owner") {
        val id = makeClass(Owner.Root, "Foo")
        assertTrue(id.fullName == "Foo")
      },
      test("fullName with deep nesting") {
        val owner = Owner.pkgs("com", "example", "app", "models")
        val id = makeClass(owner, "User")
        assertTrue(id.fullName == "com.example.app.models.User")
      }
    ),
    suite("arity")(
      test("arity with no type params is 0") {
        val id = makeClass(scalaOwner, "Int")
        assertTrue(id.arity == 0)
      },
      test("arity with one type param is 1") {
        val id = makeClass(scalaOwner, "Option", List(TypeParam("A", 0, Variance.Covariant)))
        assertTrue(id.arity == 1)
      },
      test("arity with multiple type params") {
        val id = makeClass(
          scalaOwner,
          "Map",
          List(
            TypeParam("K", 0, Variance.Invariant),
            TypeParam("V", 1, Variance.Covariant)
          )
        )
        assertTrue(id.arity == 2)
      }
    ),
    suite("isClass")(
      test("Class kind returns true") {
        val id = makeClass(scalaOwner, "Int")
        assertTrue(id.isClass)
      },
      test("Trait kind returns false") {
        val id = makeTrait(scalaOwner, "Ordered")
        assertTrue(!id.isClass)
      },
      test("Object kind returns false") {
        val id = makeObject(scalaOwner, "Nil")
        assertTrue(!id.isClass)
      }
    ),
    suite("isTrait")(
      test("Trait kind returns true") {
        val id = makeTrait(scalaOwner, "Ordered")
        assertTrue(id.isTrait)
      },
      test("Class kind returns false") {
        val id = makeClass(scalaOwner, "Int")
        assertTrue(!id.isTrait)
      }
    ),
    suite("isObject")(
      test("Object kind returns true") {
        val id = makeObject(scalaOwner, "Nil")
        assertTrue(id.isObject)
      },
      test("Class kind returns false") {
        val id = makeClass(scalaOwner, "Int")
        assertTrue(!id.isObject)
      }
    ),
    suite("isEnum")(
      test("Enum kind returns true") {
        val id = makeEnum(scalaOwner, "Color", List(EnumCaseInfo("Red", 0, Nil, true)))
        assertTrue(id.isEnum)
      },
      test("Class kind returns false") {
        val id = makeClass(scalaOwner, "Int")
        assertTrue(!id.isEnum)
      }
    ),
    suite("isAlias")(
      test("TypeAlias kind returns true") {
        val id = makeAlias(scalaOwner, "MyString", TypeRepr.Ref(
          DynamicTypeId(javaLang, "String", Nil, TypeDefKind.Class(isFinal = true), Nil),
          Nil
        ))
        assertTrue(id.isAlias)
      },
      test("Class kind returns false") {
        val id = makeClass(scalaOwner, "Int")
        assertTrue(!id.isAlias)
      }
    ),
    suite("isOpaque")(
      test("OpaqueType kind returns true") {
        val id = makeOpaque(scalaOwner, "UserId", TypeRepr.Ref(
          DynamicTypeId(javaLang, "String", Nil, TypeDefKind.Class(isFinal = true), Nil),
          Nil
        ))
        assertTrue(id.isOpaque)
      },
      test("Class kind returns false") {
        val id = makeClass(scalaOwner, "Int")
        assertTrue(!id.isOpaque)
      }
    ),
    suite("isAbstract")(
      test("AbstractType kind returns true") {
        val id = makeAbstract(scalaOwner, "T")
        assertTrue(id.isAbstract)
      },
      test("Class kind returns false") {
        val id = makeClass(scalaOwner, "Int")
        assertTrue(!id.isAbstract)
      }
    ),
    suite("isSealed")(
      test("sealed trait returns true") {
        val id = makeTrait(scalaOwner, "Option", isSealed = true)
        assertTrue(id.isSealed)
      },
      test("non-sealed trait returns false") {
        val id = makeTrait(scalaOwner, "Ordered", isSealed = false)
        assertTrue(!id.isSealed)
      },
      test("class returns false") {
        val id = makeClass(scalaOwner, "Int")
        assertTrue(!id.isSealed)
      }
    ),
    suite("isCaseClass")(
      test("case class returns true") {
        val id = makeClass(scalaOwner, "Some", isCase = true)
        assertTrue(id.isCaseClass)
      },
      test("non-case class returns false") {
        val id = makeClass(scalaOwner, "String")
        assertTrue(!id.isCaseClass)
      }
    ),
    suite("isValueClass")(
      test("value class returns true") {
        val id = makeClass(scalaOwner, "Int", isValue = true)
        assertTrue(id.isValueClass)
      },
      test("non-value class returns false") {
        val id = makeClass(scalaOwner, "String")
        assertTrue(!id.isValueClass)
      }
    ),
    suite("aliasedTo")(
      test("TypeAlias returns Some with alias") {
        val aliasTarget = TypeRepr.Ref(
          DynamicTypeId(javaLang, "String", Nil, TypeDefKind.Class(isFinal = true), Nil),
          Nil
        )
        val id = makeAlias(scalaOwner, "MyString", aliasTarget)
        assertTrue(id.aliasedTo.contains(aliasTarget))
      },
      test("Class returns None") {
        val id = makeClass(scalaOwner, "Int")
        assertTrue(id.aliasedTo.isEmpty)
      }
    ),
    suite("representation")(
      test("OpaqueType returns None (internal representation is hidden)") {
        val repr = TypeRepr.Ref(
          DynamicTypeId(javaLang, "String", Nil, TypeDefKind.Class(isFinal = true), Nil),
          Nil
        )
        val id = makeOpaque(scalaOwner, "UserId", repr)
        assertTrue(id.representation.isEmpty)
      },
      test("Class returns None") {
        val id = makeClass(scalaOwner, "Int")
        assertTrue(id.representation.isEmpty)
      }
    ),
    suite("enumCases")(
      test("Enum returns list of cases") {
        val cases = List(
          EnumCaseInfo("Red", 0, Nil, true),
          EnumCaseInfo("Green", 1, Nil, true),
          EnumCaseInfo("Blue", 2, Nil, true)
        )
        val id = makeEnum(scalaOwner, "Color", cases)
        assertTrue(id.enumCases == cases)
      },
      test("Class returns empty list") {
        val id = makeClass(scalaOwner, "Int")
        assertTrue(id.enumCases.isEmpty)
      }
    ),
    suite("show and toString")(
      test("show returns qualified name") {
        val id = makeClass(javaLang, "String")
        assertTrue(id.show == "java.lang.String")
      },
      test("show with root owner returns just name") {
        val id = makeClass(Owner.Root, "Foo")
        assertTrue(id.show == "Foo")
      },
      test("toString equals show") {
        val id = makeClass(javaLang, "String")
        assertTrue(id.toString == id.show)
      }
    ),
    suite("equals and hashCode")(
      test("same owner and name are equal") {
        val id1 = makeClass(scalaOwner, "Int")
        val id2 = makeClass(scalaOwner, "Int")
        assertTrue(id1 == id2)
      },
      test("different names are not equal") {
        val id1 = makeClass(scalaOwner, "Int")
        val id2 = makeClass(scalaOwner, "Long")
        assertTrue(id1 != id2)
      },
      test("different owners are not equal") {
        val id1 = makeClass(scalaOwner, "String")
        val id2 = makeClass(javaLang, "String")
        assertTrue(id1 != id2)
      },
      test("equal ids have same hashCode") {
        val id1 = makeClass(scalaOwner, "Int")
        val id2 = makeClass(scalaOwner, "Int")
        assertTrue(id1.hashCode == id2.hashCode)
      }
    ),
    suite("EnumCaseInfo")(
      test("arity with no params is 0") {
        val caseInfo = EnumCaseInfo("Red", 0, Nil, true)
        assertTrue(caseInfo.arity == 0)
      },
      test("arity with params equals param count") {
        val params = List(
          EnumCaseParam("value", TypeRepr.Ref(
            DynamicTypeId(scalaOwner, "Int", Nil, TypeDefKind.Class(isFinal = true, isValue = true), Nil),
            Nil
          ))
        )
        val caseInfo = EnumCaseInfo("Value", 0, params, false)
        assertTrue(caseInfo.arity == 1)
      },
      test("isObjectCase true for simple enum case") {
        val caseInfo = EnumCaseInfo("Red", 0, Nil, true)
        assertTrue(caseInfo.isObjectCase)
      },
      test("isObjectCase false for parameterized case") {
        val params = List(EnumCaseParam("n", TypeRepr.Ref(
          DynamicTypeId(scalaOwner, "Int", Nil, TypeDefKind.Class(), Nil),
          Nil
        )))
        val caseInfo = EnumCaseInfo("Num", 0, params, false)
        assertTrue(!caseInfo.isObjectCase)
      }
    ),
    suite("isSubtypeOf, isSupertypeOf, isEquivalentTo")(
      test("isSubtypeOf delegates to Subtyping") {
        val intId = makeClass(scalaOwner, "Int", isValue = true)
        val anyValId = makeClass(scalaOwner, "AnyVal")
        assertTrue(intId.isSubtypeOf(anyValId))
      },
      test("isSupertypeOf is inverse of isSubtypeOf") {
        val intId = makeClass(scalaOwner, "Int", isValue = true)
        val anyValId = makeClass(scalaOwner, "AnyVal")
        assertTrue(anyValId.isSupertypeOf(intId))
      },
      test("isEquivalentTo for same type returns true") {
        val intId1 = makeClass(scalaOwner, "Int")
        val intId2 = makeClass(scalaOwner, "Int")
        assertTrue(intId1.isEquivalentTo(intId2))
      },
      test("isEquivalentTo for different types returns false") {
        val intId = makeClass(scalaOwner, "Int")
        val stringId = makeClass(javaLang, "String")
        assertTrue(!intId.isEquivalentTo(stringId))
      }
    ),
    suite("equals and hashCode")(
      test("equals reflexive") {
        val intId = makeClass(scalaOwner, "Int")
        assertTrue(intId.equals(intId))
      },
      test("equals symmetric") {
        val intId1 = makeClass(scalaOwner, "Int")
        val intId2 = makeClass(scalaOwner, "Int")
        assertTrue(intId1.equals(intId2) && intId2.equals(intId1))
      },
      test("equals returns false for different types") {
        val intId = makeClass(scalaOwner, "Int")
        val stringId = makeClass(javaLang, "String")
        assertTrue(!intId.equals(stringId))
      },
      test("equals returns false for non-DynamicTypeId") {
        val intId = makeClass(scalaOwner, "Int")
        assertTrue(!intId.equals("not a DynamicTypeId"))
      },
      test("equals returns false for null") {
        val intId = makeClass(scalaOwner, "Int")
        assertTrue(!intId.equals(null))
      },
      test("hashCode consistent with equals") {
        val intId1 = makeClass(scalaOwner, "Int")
        val intId2 = makeClass(scalaOwner, "Int")
        assertTrue(intId1.hashCode == intId2.hashCode)
      },
      test("hashCode stable across invocations") {
        val intId = makeClass(scalaOwner, "Int")
        val hash1 = intId.hashCode
        val hash2 = intId.hashCode
        assertTrue(hash1 == hash2)
      }
    ),
    suite("toString and show")(
      test("toString returns meaningful representation") {
        val intId = makeClass(scalaOwner, "Int")
        assertTrue(intId.toString.nonEmpty)
      },
      test("show returns fullName") {
        val intId = makeClass(scalaOwner, "Int")
        assertTrue(intId.show == "scala.Int")
      },
      test("show with nested owner") {
        val nestedOwner = Owner.pkgs("com", "example", "mypackage")
        val myId = makeClass(nestedOwner, "MyClass")
        assertTrue(myId.show == "com.example.mypackage.MyClass")
      }
    ),
    suite("copy and modification")(
      test("copy creates new instance with same values") {
        val intId = makeClass(scalaOwner, "Int")
        val copied = intId.copy()
        assertTrue(intId.equals(copied))
      },
      test("copy with modified name") {
        val intId = makeClass(scalaOwner, "Int")
        val modified = intId.copy(name = "Long")
        assertTrue(modified.name == "Long")
      },
      test("copy with modified args") {
        val intId = makeClass(scalaOwner, "Int")
        val intRef = TypeRepr.Ref(intId, Nil)
        val modified = intId.copy(args = List(intRef))
        assertTrue(modified.args.size == 1)
      }
    ),
    suite("parents and annotations")(
      test("parents accessible") {
        val intId = makeClass(scalaOwner, "Int")
        assertTrue(intId.parents != null)
      },
      test("annotations accessible") {
        val deprecatedId = DynamicTypeId(scalaOwner, "deprecated", Nil, TypeDefKind.Class(), Nil)
        val annotation = Annotation(TypeRepr.Ref(deprecatedId, Nil), Nil)
        val intId = DynamicTypeId(
          scalaOwner,
          "Int",
          Nil,
          TypeDefKind.Class(),
          Nil,
          Nil,
          List(annotation)
        )
        assertTrue(intId.annotations.nonEmpty)
      },
      test("parents with hierarchy") {
        val intRef = TypeRepr.Ref(makeClass(scalaOwner, "Int"), Nil)
        val childId = DynamicTypeId(
          scalaOwner,
          "Child",
          Nil,
          TypeDefKind.Class(),
          List(intRef)
        )
        assertTrue(childId.parents.nonEmpty)
      }
    ),
    suite("TypeDefKind edge cases")(
      test("Trait with sealed flag") {
        val sealedTrait = makeTrait(scalaOwner, "MySealedTrait", isSealed = true)
        assertTrue(sealedTrait.isSealed)
      },
      test("AbstractType is abstract") {
        val abstractId = makeAbstract(scalaOwner, "T")
        assertTrue(abstractId.isAbstract)
      },
      test("OpaqueType is opaque") {
        val opaqueId = makeOpaque(scalaOwner, "MyOpaque", TypeRepr.Ref(makeClass(scalaOwner, "Int"), Nil))
        // Opaque types intentionally hide their representation (returns None)
        assertTrue(opaqueId.isOpaque && opaqueId.representation.isEmpty)
      },
      test("Enum with multiple cases") {
        val cases = List(
          EnumCaseInfo("A", 0, Nil, true),
          EnumCaseInfo("B", 1, List(EnumCaseParam("value", TypeRepr.Ref(makeClass(scalaOwner, "Int"), Nil))), false),
          EnumCaseInfo("C", 2, Nil, true)
        )
        val enumId = makeEnum(scalaOwner, "MyEnum", cases)
        assertTrue(enumId.enumCases.size == 3)
      }
    ),
    suite("DynamicTypeId representation and aliasedTo extended")(
      test("TypeAlias aliasedTo returns the alias") {
        val aliasId = DynamicTypeId(
          scalaOwner,
          "MyInt",
          Nil,
          TypeDefKind.TypeAlias(TypeRepr.Ref(makeClass(scalaOwner, "Int"), Nil)),
          Nil
        )
        assertTrue(aliasId.aliasedTo.isDefined)
      },
      test("Class aliasedTo returns None") {
        val classId = makeClass(scalaOwner, "MyClass")
        assertTrue(classId.aliasedTo.isEmpty)
      },
      test("OpaqueType representation always None") {
        val opaqueId = makeOpaque(scalaOwner, "MyOpaque", TypeRepr.Ref(makeClass(scalaOwner, "Int"), Nil))
        assertTrue(opaqueId.representation.isEmpty)
      },
      test("Class representation returns None") {
        val classId = makeClass(scalaOwner, "MyClass")
        assertTrue(classId.representation.isEmpty)
      },
      test("Trait representation returns None") {
        val traitId = makeTrait(scalaOwner, "MyTrait")
        assertTrue(traitId.representation.isEmpty)
      }
    ),
    suite("DynamicTypeId fullName edge cases")(
      test("fullName with root owner") {
        val rootId = DynamicTypeId(Owner.Root, "Foo", Nil, TypeDefKind.Class(), Nil)
        assertTrue(rootId.fullName == "Foo")
      },
      test("fullName with package owner") {
        val pkgId = DynamicTypeId(scalaOwner, "Int", Nil, TypeDefKind.Class(), Nil)
        assertTrue(pkgId.fullName == "scala.Int")
      },
      test("fullName with nested owner") {
        val nestedOwner = Owner.pkgs("scala", "collection", "immutable")
        val id = DynamicTypeId(nestedOwner, "List", Nil, TypeDefKind.Class(), Nil)
        assertTrue(id.fullName == "scala.collection.immutable.List")
      }
    ),
    suite("DynamicTypeId arity edge cases")(
      test("arity with no type params") {
        val id = makeClass(scalaOwner, "Simple")
        assertTrue(id.arity == 0)
      },
      test("arity with one type param") {
        val id = DynamicTypeId(
          scalaOwner,
          "Container",
          List(TypeParam("A", 0)),
          TypeDefKind.Class(),
          Nil
        )
        assertTrue(id.arity == 1)
      },
      test("arity with multiple type params") {
        val id = DynamicTypeId(
          scalaOwner,
          "Map",
          List(TypeParam("K", 0), TypeParam("V", 1)),
          TypeDefKind.Class(),
          Nil
        )
        assertTrue(id.arity == 2)
      }
    ),
    suite("TypeDefKind predicates extended")(
      test("isCaseClass returns false for non-case class") {
        val id = makeClass(scalaOwner, "Regular")
        assertTrue(!id.isCaseClass)
      },
      test("isValueClass returns false for non-value class") {
        val id = makeClass(scalaOwner, "Regular")
        assertTrue(!id.isValueClass)
      },
      test("isSealed returns false for non-sealed trait") {
        val id = makeTrait(scalaOwner, "OpenTrait")
        assertTrue(!id.isSealed)
      },
      test("isObject returns true for Object kind") {
        val id = makeObject(scalaOwner, "MyObject")
        assertTrue(id.isObject)
      },
      test("isClass returns true for Class kind") {
        val id = makeClass(scalaOwner, "MyClass")
        assertTrue(id.isClass)
      },
      test("isTrait returns true for Trait kind") {
        val id = makeTrait(scalaOwner, "MyTrait")
        assertTrue(id.isTrait)
      },
      test("isEnum returns true for Enum kind") {
        val id = makeEnum(scalaOwner, "MyEnum", Nil)
        assertTrue(id.isEnum)
      },
      test("isAlias returns true for TypeAlias kind") {
        val id = makeAlias(scalaOwner, "MyAlias", TypeRepr.Ref(makeClass(scalaOwner, "Int"), Nil))
        assertTrue(id.isAlias)
      },
      test("isOpaque returns true for OpaqueType kind") {
        val id = makeOpaque(scalaOwner, "MyOpaque", TypeRepr.Ref(makeClass(scalaOwner, "Int"), Nil))
        assertTrue(id.isOpaque)
      },
      test("isAbstract returns true for AbstractType kind") {
        val id = makeAbstract(scalaOwner, "T")
        assertTrue(id.isAbstract)
      }
    ),
    suite("DynamicTypeId subtyping methods")(
      test("isSubtypeOf reflexive") {
        val id = makeClass(scalaOwner, "Foo")
        assertTrue(id.isSubtypeOf(id))
      },
      test("isSupertypeOf reflexive") {
        val id = makeClass(scalaOwner, "Foo")
        assertTrue(id.isSupertypeOf(id))
      },
      test("isEquivalentTo reflexive") {
        val id = makeClass(scalaOwner, "Foo")
        assertTrue(id.isEquivalentTo(id))
      }
    ),
    suite("DynamicTypeId copy")(
      test("copy preserves owner") {
        val id = makeClass(scalaOwner, "Foo")
        val copied = id.copy(name = "Bar")
        assertTrue(copied.owner == scalaOwner && copied.name == "Bar")
      },
      test("copy can change kind") {
        val id = makeClass(scalaOwner, "Foo")
        val copied = id.copy(kind = TypeDefKind.Object)
        assertTrue(copied.isObject)
      }
    )
  )
}
