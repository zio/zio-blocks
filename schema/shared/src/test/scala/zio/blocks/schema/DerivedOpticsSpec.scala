package zio.blocks.schema

import zio.test._
import zio.blocks.schema.binding.Binding
import zio.blocks.typeid.TypeId
import scala.language.reflectiveCalls

object DerivedOpticsSpec extends SchemaBaseSpec {
  case class Person(name: String, age: Int)

  object Person extends DerivedOptics {
    implicit val schema: Schema[Person] = Schema.derived
  }

  sealed trait Shape

  case class Circle(radius: Double) extends Shape

  case class Rectangle(width: Double, height: Double) extends Shape

  case object Point extends Shape

  object Shape extends DerivedOptics {
    implicit val schema: Schema[Shape] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("DerivedOpticsSpec")(
    suite("Lens generation for case classes")(
      test("lens has correct types (compile-time check)") {
        val nameLens: Lens[Person, String] = Person.optics.name
        val ageLens: Lens[Person, Int]     = Person.optics.age
        val person                         = Person("Test", 25)
        assertTrue(
          Person.optics eq Person.optics,
          nameLens eq Person.optics.name,
          nameLens.get(person) == "Test",
          ageLens.get(person) == 25
        )
      },
      test("lens for case class extending trait") {
        trait HasId {
          def id: Int
        }

        case class Entity(id: Int, name: String) extends HasId

        object Entity extends DerivedOptics.Of[Entity] {
          implicit val schema: Schema[Entity] = Schema.derived
        }

        val e = Entity(1, "test")
        assertTrue(
          Entity.optics.id.get(e) == 1,
          Entity.optics.name.get(e) == "test"
        )
      },
      test("lens for fields with keyword names") {
        case class Keywords(`type`: String, `class`: Int, `val`: Boolean)

        object Keywords extends DerivedOptics.Of[Keywords] {
          implicit val schema: Schema[Keywords] = Schema.derived
        }

        val kw = Keywords("test", 42, true)
        assertTrue(Keywords.optics.`type`.get(kw) == "test") &&
        assertTrue(Keywords.optics.`class`.get(kw) == 42) &&
        assertTrue(Keywords.optics.`val`.get(kw) == true)
      },
      test("lens for generic case class") {
        case class Box[A](value: A)

        object BoxInt extends DerivedOptics.Of[Box[Int]] {
          implicit val boxIntSchema: Schema[Box[Int]] = Schema.derived
        }

        object BoxString extends DerivedOptics.Of[Box[String]] {
          implicit val boxStringSchema: Schema[Box[String]] = Schema.derived
        }

        import BoxInt.boxIntSchema
        import BoxString.boxStringSchema

        val boxInt    = Box(42)
        val boxString = Box("hello")
        assertTrue(
          BoxInt.optics.value.get(boxInt).asInstanceOf[Int] == 42,
          BoxString.optics.value.get(boxString).asInstanceOf[String] == "hello",
          !(BoxInt.optics eq BoxString.optics),
          BoxInt.optics.value eq BoxInt.optics.value
        )
      },
      test("lens works when companion uses type alias of its own type (case class)") {
        type AP = Person

        object AliasedPerson extends DerivedOptics.Of[AP] {
          implicit val schema: Schema[AP] = Schema.derived
        }

        val person: AP = Person("Alice", 30)
        assertTrue(
          AliasedPerson.optics.name.get(person) == "Alice",
          AliasedPerson.optics.age.get(person) == 30,
          AliasedPerson.optics.name.replace(person, "Bob") == Person("Bob", 30)
        )
      },
      test("lens works with nested type aliases") {
        type AA = Person

        type AAA = AA

        object TripleAliasedPerson extends DerivedOptics.Of[AAA] {
          implicit val schema: Schema[Person] = Schema.derived
        }

        val person: AAA = Person("Alice", 30)
        assertTrue(TripleAliasedPerson.optics.name.get(person) == "Alice")
      },
      test("empty case class produces empty optics") {
        case class Empty()

        object Empty extends DerivedOptics.Of[Empty] {
          implicit val schema: Schema[Empty] = Schema.derived
        }

        assertTrue(Empty.optics ne null)
      },
      test("case class with private constructor") {
        case class Private private (value: Int)

        object Private extends DerivedOptics.Of[Private] {
          implicit val schema: Schema[Private] = Schema.derived

          def create(v: Int): Private = Private(v)
        }

        val p = Private.create(42)
        assertTrue(
          Private.optics.value.get(p) == 42,
          Private.optics.value.replace(p, 100).value == 100
        )
      },
      test("recursive type (Tree) derives optics") {
        case class Tree(value: Int, children: List[Tree])

        object Tree extends DerivedOptics.Of[Tree] {
          implicit val schema: Schema[Tree] = Schema.derived
        }

        val tree = Tree(1, List(Tree(2, Nil)))
        assertTrue(
          Tree.optics.value.get(tree) == 1,
          Tree.optics.children.get(tree).size == 1
        )
      },
      test("mutually recursive types derive optics") {
        case class NodeA(value: String, next: Option[NodeB])

        case class NodeB(value: Int, next: Option[NodeA])

        object NodeA extends DerivedOptics.Of[NodeA] {
          implicit val schema: Schema[NodeA] = Schema.derived
        }

        object NodeB extends DerivedOptics.Of[NodeB] {
          implicit val schema: Schema[NodeB] = Schema.derived
        }

        val a = NodeA("start", Some(NodeB(42, None)))
        assertTrue(
          NodeA.optics.value.get(a) == "start",
          NodeA.optics.next.get(a).map(_.value) == Some(42)
        )
      },
      test("macro derivation succeeds for case class with backtick-escaped field names") {
        case class SpecialFields(`my funny name`: String, `field-with-dashes`: Int)

        object SpecialFields extends DerivedOptics.Of[SpecialFields] {
          implicit val schema: Schema[SpecialFields] = Schema.derived
        }

        val fields = SpecialFields("hello", 42)
        assertTrue(
          SpecialFields.optics.`my funny name`.get(fields) == "hello",
          SpecialFields.optics.`field-with-dashes`.get(fields) == 42
        )
      }
    ),
    suite("Prism generation for sealed traits")(
      test("prism has correct types (compile-time check)") {
        val circlePrism: Prism[Shape, Circle]       = Shape.optics.circle
        val rectanglePrism: Prism[Shape, Rectangle] = Shape.optics.rectangle
        val s1: Shape                               = Circle(1.0)
        val s2: Shape                               = Rectangle(2.0, 3.0)
        assertTrue(
          circlePrism.getOption(s1) == Some(Circle(1.0)),
          rectanglePrism.getOption(s2) == Some(Rectangle(2.0, 3.0))
        )
      },
      test("stress test: fixed type arguments") {
        sealed trait Stress[A]

        case class FixedStress(value: Int) extends Stress[Int]

        case class VarStress[B](value: B) extends Stress[B]

        object StressInt extends DerivedOptics.Of[Stress[Int]] {
          implicit val schema: Schema[Stress[Int]] = Schema.derived
        }

        import StressInt.schema

        val fixed: Stress[Int]   = FixedStress(100)
        val variant: Stress[Int] = VarStress(200)
        assertTrue(
          StressInt.optics.fixedStress.getOption(fixed) == Some(FixedStress(100)),
          StressInt.optics.fixedStress.getOption(variant) == None,
          StressInt.optics.varStress.getOption(variant) == Some(VarStress(200))
        )
      },
      test("stress test: multiple type parameters") {
        sealed trait MultiParam[A, B]

        case class MultiChild[X, Y](x: X, y: Y) extends MultiParam[X, Y]

        case class PartialChild[Z](z: Z) extends MultiParam[Z, String] // Partial fix

        object MultiParamIntString extends DerivedOptics.Of[MultiParam[Int, String]] {
          implicit val schema: Schema[MultiParam[Int, String]] = Schema.derived
        }

        import MultiParamIntString.schema

        val multi: MultiParam[Int, String]   = MultiChild(42, "hello")
        val partial: MultiParam[Int, String] = PartialChild(99)
        assertTrue(
          MultiParamIntString.optics.multiChild.getOption(multi) == Some(MultiChild(42, "hello")),
          MultiParamIntString.optics.partialChild.getOption(partial) == Some(PartialChild(99)),
          MultiParamIntString.optics.multiChild.getOption(partial) == None
        )
      },
      test("stress test: shadowed type parameter names") {
        sealed trait Parent[A]

        case class ChildWithDifferentName[X](value: X) extends Parent[X]

        object ParentString extends DerivedOptics.Of[Parent[String]] {
          implicit val schema: Schema[Parent[String]] = Schema.derived
        }

        import ParentString.schema

        val child: Parent[String] = ChildWithDifferentName("test")
        assertTrue(
          ParentString.optics.childWithDifferentName.getOption(child) == Some(ChildWithDifferentName("test"))
        )
      },
      /* FIXME: Compilation error with Scala 3
      test("stress test: triple type parameters with partial application") {
        sealed trait Triple[A, B, C]
        case class TripleChild[P, Q, R](p: P, q: Q, r: R) extends Triple[P, Q, R]
        case class DoubleFixed[T](t: T) extends Triple[Int, T, Boolean]

        object TripleTest extends DerivedOptics.Of[Triple[Int, String, Boolean]] {
          implicit val schema: Schema[Triple[Int, String, Boolean]] = Schema.derived
        }

        import TripleTest.schema

        val triple: Triple[Int, String, Boolean]      = TripleChild(1, "two", true)
        val doubleFixed: Triple[Int, String, Boolean] = DoubleFixed("middle")
        assertTrue(
          TripleTest.optics.tripleChild.getOption(triple) == Some(TripleChild(1, "two", true)),
          TripleTest.optics.doubleFixed.getOption(doubleFixed) == Some(DoubleFixed("middle")),
          TripleTest.optics.tripleChild.getOption(doubleFixed) == None
        )
      },
       */
      test("prism for generic sealed trait") {
        sealed trait GenericResult[T]

        case class Success[T](value: T) extends GenericResult[T]

        case class Failure[T](msg: String) extends GenericResult[T]

        object ResultInt extends DerivedOptics.Of[GenericResult[Int]] {
          implicit val schema: Schema[GenericResult[Int]] = Schema.derived
        }

        object ResultString extends DerivedOptics.Of[GenericResult[String]] {
          implicit val schema: Schema[GenericResult[String]] = Schema.derived
        }

        implicit val intSchema: Schema[GenericResult[Int]]    = ResultInt.schema
        implicit val strSchema: Schema[GenericResult[String]] = ResultString.schema

        val s1: GenericResult[Int]    = Success(42)
        val s2: GenericResult[String] = Success("hello")
        val f1: GenericResult[String] = Failure[String]("ops")
        assertTrue(
          ResultInt.optics.success.getOption(s1) == Some(Success(42)),
          ResultString.optics.success.getOption(s2) == Some(Success("hello")),
          ResultString.optics.failure.getOption(f1) == Some(Failure[String]("ops")),
          !(ResultInt.optics eq ResultString.optics),
          ResultInt.optics.success eq ResultInt.optics.success
        )
      },
      test("prism works when companion uses type alias of its own type (sealed trait)") {
        type AS = Shape

        object AliasedShape extends DerivedOptics.Of[AS] {
          implicit val schema: Schema[Shape] = Schema.derived
        }

        val circle: AS = Circle(5.0)
        val rect: AS   = Rectangle(3.0, 4.0)
        assertTrue(
          AliasedShape.optics.circle.getOption(circle) == Some(Circle(5.0)),
          AliasedShape.optics.rectangle.getOption(rect) == Some(Rectangle(3.0, 4.0)),
          AliasedShape.optics.circle.getOption(rect) == None
        )
      },
      test("macro derivation succeeds for sealed trait with backtick-escaped case names") {
        sealed trait SpecialCases

        case class `my-special-case`(value: Int) extends SpecialCases

        case class `another special case`(msg: String) extends SpecialCases

        case object `weird case name` extends SpecialCases

        object SpecialCases extends DerivedOptics.Of[SpecialCases] {
          implicit val schema: Schema[SpecialCases] = Schema.derived
        }

        val case1: SpecialCases = `my-special-case`(5)
        val case2: SpecialCases = `another special case`("VVV")
        assertTrue(
          SpecialCases.optics.`my-special-case`.getOption(case1) == Some(`my-special-case`(5)),
          SpecialCases.optics.`another special case`.getOption(case2) == Some(`another special case`("VVV"))
        )
      },
      test("prism for case object has correct singleton type") {
        // Case objects should have singleton type in the prism:
        // Prism[Shape, Point.type] not Prism[Shape, Shape]
        val pointPrism: Prism[Shape, Point.type] = Shape.optics.point

        val s1: Shape = Point
        val s2: Shape = Circle(1.0)
        assertTrue(
          pointPrism.getOption(s1) == Some(Point),
          pointPrism.getOption(s2) == None
        )
      }
    ),
    suite("Lens generation for wrappers")(
      test("robustness: wrapperAsRecord strategy works for custom wrapper types") {
        case class CustomWrapper(value: String)

        object CustomWrapper extends DerivedOptics.Of[CustomWrapper] {
          implicit val schema: Schema[CustomWrapper] = new Schema(
            new Reflect.Wrapper[Binding, CustomWrapper, String](
              Schema[String].reflect,
              TypeId.of[CustomWrapper],
              None,
              new Binding.Wrapper[CustomWrapper, String](
                (s: String) => Right(CustomWrapper(s)),
                (w: CustomWrapper) => w.value
              )
            )
          )
        }

        val wrapped = CustomWrapper("wrapped value")
        val lens    = CustomWrapper.optics.value
        assertTrue(
          lens.get(wrapped) == "wrapped value",
          lens.replace(wrapped, "new value") == CustomWrapper("new value")
        )
      }
    )
  )
}
