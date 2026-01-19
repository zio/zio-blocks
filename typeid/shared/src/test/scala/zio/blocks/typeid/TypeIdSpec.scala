package zio.blocks.typeid

import zio.test._

object TypeIdSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("TypeIdSpec")(
    suite("Owner")(
      test("Root owner has empty segments") {
        assertTrue(Owner.Root.segments.isEmpty) &&
        assertTrue(Owner.Root.asString == "")
      },
      test("asString concatenates segment names") {
        val owner = Owner(List(Owner.Package("zio"), Owner.Package("blocks"), Owner.Type("Foo")))
        assertTrue(owner.asString == "zio.blocks.Foo")
      }
    ),
    suite("TypeParam")(
      test("stores name and index") {
        val param = TypeParam("A", 0)
        assertTrue(param.name == "A") &&
        assertTrue(param.index == 0)
      }
    ),
    suite("TypeId")(
      test("nominal creates nominal TypeId") {
        val id = TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil)
        assertTrue(id.name == "Int") &&
        assertTrue(id.owner.asString == "scala") &&
        assertTrue(id.typeParams.isEmpty) &&
        assertTrue(id.arity == 0) &&
        assertTrue(id.fullName == "scala.Int")
      },
      test("nominal with type params") {
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
      test("alias creates alias TypeId") {
        val intRef = TypeRepr.Ref(TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil))
        val id     = TypeId.alias[Int]("Age", Owner(List(Owner.Package("myapp"))), Nil, intRef)
        assertTrue(id.name == "Age") &&
        assertTrue(id.fullName == "myapp.Age")
      },
      test("opaque creates opaque TypeId") {
        val stringRef =
          TypeRepr.Ref(TypeId.nominal[String]("String", Owner(List(Owner.Package("java"), Owner.Package("lang"))), Nil))
        val id = TypeId.opaque[String]("Email", Owner(List(Owner.Package("myapp"))), Nil, stringRef)
        assertTrue(id.name == "Email") &&
        assertTrue(id.fullName == "myapp.Email")
      },
      test("Nominal extractor works") {
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
      test("Alias extractor works") {
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
      test("Opaque extractor works") {
        val stringRef =
          TypeRepr.Ref(TypeId.nominal[String]("String", Owner(List(Owner.Package("java"), Owner.Package("lang"))), Nil))
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
      test("fullName with root owner") {
        val id = TypeId.nominal[Int]("Int", Owner.Root, Nil)
        assertTrue(id.fullName == "Int")
      }
    ),
    suite("TypeRepr")(
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
      test("Intersection and Union") {
        val aRef         = TypeRepr.Ref(TypeId.nominal[Int]("A", Owner.Root, Nil))
        val bRef         = TypeRepr.Ref(TypeId.nominal[String]("B", Owner.Root, Nil))
        val intersection = TypeRepr.Intersection(aRef, bRef)
        val union        = TypeRepr.Union(aRef, bRef)
        assertTrue(intersection.left == aRef) &&
        assertTrue(intersection.right == bRef) &&
        assertTrue(union.left == aRef) &&
        assertTrue(union.right == bRef)
      },
      test("Tuple holds elements") {
        val intRef    = TypeRepr.Ref(TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil))
        val stringRef =
          TypeRepr.Ref(TypeId.nominal[String]("String", Owner(List(Owner.Package("java"), Owner.Package("lang"))), Nil))
        val tuple = TypeRepr.Tuple(List(intRef, stringRef))
        assertTrue(tuple.elems == List(intRef, stringRef))
      },
      test("Function holds params and result") {
        val intRef    = TypeRepr.Ref(TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil))
        val stringRef =
          TypeRepr.Ref(TypeId.nominal[String]("String", Owner(List(Owner.Package("java"), Owner.Package("lang"))), Nil))
        val function = TypeRepr.Function(List(intRef), stringRef)
        assertTrue(function.params == List(intRef)) &&
        assertTrue(function.result == stringRef)
      },
      test("Constant holds value") {
        val constant = TypeRepr.Constant(42)
        assertTrue(constant.value == 42)
      },
      test("Singleton holds path") {
        val path      = TermPath(List(TermPath.Package("myapp"), TermPath.Term("myObject")))
        val singleton = TypeRepr.Singleton(path)
        assertTrue(singleton.path == path)
      },
      test("AnyType and NothingType are objects") {
        assertTrue(TypeRepr.AnyType == TypeRepr.AnyType) &&
        assertTrue(TypeRepr.NothingType == TypeRepr.NothingType)
      }
    ),
    suite("Member")(
      test("Val member") {
        val intRef = TypeRepr.Ref(TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil))
        val member = Member.Val("x", intRef, isVar = false)
        assertTrue(member.name == "x") &&
        assertTrue(member.tpe == intRef) &&
        assertTrue(member.isVar == false)
      },
      test("Def member") {
        val intRef    = TypeRepr.Ref(TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil))
        val stringRef =
          TypeRepr.Ref(TypeId.nominal[String]("String", Owner(List(Owner.Package("java"), Owner.Package("lang"))), Nil))
        val member = Member.Def("foo", List(List(Param("x", intRef))), stringRef)
        assertTrue(member.name == "foo") &&
        assertTrue(member.paramLists == List(List(Param("x", intRef)))) &&
        assertTrue(member.result == stringRef)
      },
      test("TypeMember") {
        val intRef = TypeRepr.Ref(TypeId.nominal[Int]("Int", Owner(List(Owner.Package("scala"))), Nil))
        val member = Member.TypeMember("T", Nil, None, Some(intRef))
        assertTrue(member.name == "T") &&
        assertTrue(member.typeParams.isEmpty) &&
        assertTrue(member.lowerBound.isEmpty) &&
        assertTrue(member.upperBound.contains(intRef))
      }
    ),
    suite("TermPath")(
      test("segments are stored correctly") {
        val path = TermPath(List(TermPath.Package("myapp"), TermPath.Term("obj")))
        assertTrue(path.segments.size == 2) &&
        assertTrue(path.segments.head == TermPath.Package("myapp")) &&
        assertTrue(path.segments(1) == TermPath.Term("obj"))
      }
    ),
    suite("TypeId.derive macro")(
      test("derives TypeId for Int") {
        val id = TypeId.derive[Int]
        assertTrue(id.name == "Int")
      },
      test("derives TypeId for String") {
        val id = TypeId.derive[String]
        assertTrue(id.name == "String")
      },
      test("derives TypeId for List") {
        val id = TypeId.derive[List[_]]
        assertTrue(id.name == "List") &&
        assertTrue(id.arity == 1)
      },
      test("derives TypeId for custom case class") {
        case class Person(name: String, age: Int)
        val id = TypeId.derive[Person]
        assertTrue(id.name == "Person")
      },
      test("derives TypeId for Option") {
        val id = TypeId.derive[Option[_]]
        assertTrue(id.name == "Option") &&
        assertTrue(id.arity == 1)
      },
      test("derives TypeId for Map") {
        val id = TypeId.derive[Map[_, _]]
        assertTrue(id.name == "Map") &&
        assertTrue(id.arity == 2)
      }
    )
  )
}
