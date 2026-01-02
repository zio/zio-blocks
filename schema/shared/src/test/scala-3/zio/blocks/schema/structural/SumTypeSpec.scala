package zio.blocks.schema.structural

import scala.annotation.nowarn
import zio.test._
import zio.blocks.schema._

object SumTypeSpec extends ZIOSpecDefault {

  // Test sealed traits
  sealed trait Status
  case class Active(since: String)    extends Status
  case class Inactive(reason: String) extends Status
  case object Unknown                 extends Status

  sealed trait Result
  case class Success(value: Int)    extends Result
  case class Failure(error: String) extends Result

  // Nested sealed trait
  sealed trait Outer
  case class OuterA(inner: Result) extends Outer
  case class OuterB(name: String)  extends Outer

  // Scala 3 enum
  enum Color {
    case Red, Green, Blue
  }

  enum ColorWithValue {
    case RGB(r: Int, g: Int, b: Int)
    case Named(name: String)
  }

  def spec = suite("SumTypeSpec")(
    suite("Sealed Traits with Case Classes")(
      test("sealed trait with case class variants") {
        val ts: ToStructural[Result] = ToStructural.derived[Result]

        val success = ts.toStructural(Success(42)).asInstanceOf[StructuralRecord]
        assertTrue(
          success.selectDynamic("Tag") == "Success",
          success.selectDynamic("value") == 42
        )

        val failure = ts.toStructural(Failure("oops")).asInstanceOf[StructuralRecord]
        assertTrue(
          failure.selectDynamic("Tag") == "Failure",
          failure.selectDynamic("error") == "oops"
        )
      },
      test("sealed trait with nested case class fields") {
        case class Address(city: String)
        sealed trait Person
        case class Employee(name: String, address: Address) extends Person
        case class Contractor(company: String)              extends Person

        val ts: ToStructural[Person] = ToStructural.derived[Person]

        val emp = ts.toStructural(Employee("Alice", Address("NYC"))).asInstanceOf[StructuralRecord]
        assertTrue(
          emp.selectDynamic("Tag") == "Employee",
          emp.selectDynamic("name") == "Alice"
        )
        val addr = emp.selectDynamic("address").asInstanceOf[StructuralRecord]
        assertTrue(addr.selectDynamic("city") == "NYC")

        val contractor = ts.toStructural(Contractor("Acme Corp")).asInstanceOf[StructuralRecord]
        assertTrue(
          contractor.selectDynamic("Tag") == "Contractor",
          contractor.selectDynamic("company") == "Acme Corp"
        )
      }
    ),
    suite("Sealed Traits with Case Objects")(
      test("sealed trait with case objects") {
        val ts: ToStructural[Status] = ToStructural.derived[Status]

        val active = ts.toStructural(Active("2024")).asInstanceOf[StructuralRecord]
        assertTrue(
          active.selectDynamic("Tag") == "Active",
          active.selectDynamic("since") == "2024"
        )

        val unknown = ts.toStructural(Unknown).asInstanceOf[StructuralRecord]
        assertTrue(unknown.selectDynamic("Tag") == "Unknown")
      },
      test("mixed case classes and case objects") {
        sealed trait Event
        case class Click(x: Int, y: Int) extends Event
        case object Scroll               extends Event
        case class KeyPress(key: String) extends Event

        val ts: ToStructural[Event] = ToStructural.derived[Event]

        val click = ts.toStructural(Click(10, 20)).asInstanceOf[StructuralRecord]
        assertTrue(
          click.selectDynamic("Tag") == "Click",
          click.selectDynamic("x") == 10
        )

        val scroll = ts.toStructural(Scroll).asInstanceOf[StructuralRecord]
        assertTrue(scroll.selectDynamic("Tag") == "Scroll")

        val keyPress = ts.toStructural(KeyPress("Enter")).asInstanceOf[StructuralRecord]
        assertTrue(
          keyPress.selectDynamic("Tag") == "KeyPress",
          keyPress.selectDynamic("key") == "Enter"
        )
      }
    ),
    suite("Scala 3 Enums")(
      test("simple enum (case objects only) - converted to StructuralRecord with Tag") {
        // All enums are now converted to StructuralRecord with Tag field
        val ts: ToStructural[Color] = ToStructural.derived[Color]

        val red = ts.toStructural(Color.Red).asInstanceOf[StructuralRecord]
        assertTrue(red.selectDynamic("Tag") == "Red")

        val green = ts.toStructural(Color.Green).asInstanceOf[StructuralRecord]
        assertTrue(green.selectDynamic("Tag") == "Green")

        val blue = ts.toStructural(Color.Blue).asInstanceOf[StructuralRecord]
        assertTrue(blue.selectDynamic("Tag") == "Blue")
      },
      test("enum with case class variants") {
        val ts: ToStructural[ColorWithValue] = ToStructural.derived[ColorWithValue]

        val rgb = ts.toStructural(ColorWithValue.RGB(255, 128, 0)).asInstanceOf[StructuralRecord]
        assertTrue(
          rgb.selectDynamic("Tag") == "RGB",
          rgb.selectDynamic("r") == 255,
          rgb.selectDynamic("g") == 128,
          rgb.selectDynamic("b") == 0
        )

        val named = ts.toStructural(ColorWithValue.Named("coral")).asInstanceOf[StructuralRecord]
        assertTrue(
          named.selectDynamic("Tag") == "Named",
          named.selectDynamic("name") == "coral"
        )
      },
      test("case class containing enum field") {
        enum Gender { case Male, Female, Other }
        case class Person(name: String, age: Int, gender: Gender)

        val ts: ToStructural[Person] = ToStructural.derived[Person]
        val s                        = ts.toStructural(Person("Alice", 30, Gender.Female)).asInstanceOf[StructuralRecord]

        assertTrue(
          s.selectDynamic("name") == "Alice",
          s.selectDynamic("age") == 30,
          s.selectDynamic("gender").asInstanceOf[StructuralRecord].selectDynamic("Tag") == "Female"
        )
      }
    ),
    suite("Either as Sum Type")(
      test("Either[String, Int] - Left") {
        case class WithEither(value: Either[String, Int])
        val ts: ToStructural[WithEither] = ToStructural.derived[WithEither]
        val s                            = ts.toStructural(WithEither(Left("error"))).asInstanceOf[StructuralRecord]

        val either = s.selectDynamic("value").asInstanceOf[StructuralRecord]
        assertTrue(
          either.selectDynamic("Tag") == "Left",
          either.selectDynamic("value") == "error"
        )
      },
      test("Either[String, Int] - Right") {
        case class WithEither(value: Either[String, Int])
        val ts: ToStructural[WithEither] = ToStructural.derived[WithEither]
        val s                            = ts.toStructural(WithEither(Right(42))).asInstanceOf[StructuralRecord]

        val either = s.selectDynamic("value").asInstanceOf[StructuralRecord]
        assertTrue(
          either.selectDynamic("Tag") == "Right",
          either.selectDynamic("value") == 42
        )
      },
      test("Either with nested case class") {
        case class Error(code: Int, message: String)
        case class Data(name: String)
        case class WithComplexEither(result: Either[Error, Data])

        val ts: ToStructural[WithComplexEither] = ToStructural.derived[WithComplexEither]

        val left       = ts.toStructural(WithComplexEither(Left(Error(404, "Not Found")))).asInstanceOf[StructuralRecord]
        val leftRecord = left.selectDynamic("result").asInstanceOf[StructuralRecord]
        assertTrue(leftRecord.selectDynamic("Tag") == "Left")
        val error = leftRecord.selectDynamic("value").asInstanceOf[StructuralRecord]
        assertTrue(
          error.selectDynamic("code") == 404,
          error.selectDynamic("message") == "Not Found"
        )

        val right       = ts.toStructural(WithComplexEither(Right(Data("test")))).asInstanceOf[StructuralRecord]
        val rightRecord = right.selectDynamic("result").asInstanceOf[StructuralRecord]
        assertTrue(rightRecord.selectDynamic("Tag") == "Right")
        val data = rightRecord.selectDynamic("value").asInstanceOf[StructuralRecord]
        assertTrue(data.selectDynamic("name") == "test")
      }
    ),
    suite("Nested Sum Types")(
      test("sealed trait containing another sealed trait") {
        val ts: ToStructural[Outer] = ToStructural.derived[Outer]

        val outerA = ts.toStructural(OuterA(Success(100))).asInstanceOf[StructuralRecord]
        assertTrue(outerA.selectDynamic("Tag") == "OuterA")
        val inner = outerA.selectDynamic("inner").asInstanceOf[StructuralRecord]
        assertTrue(
          inner.selectDynamic("Tag") == "Success",
          inner.selectDynamic("value") == 100
        )

        val outerB = ts.toStructural(OuterB("test")).asInstanceOf[StructuralRecord]
        assertTrue(
          outerB.selectDynamic("Tag") == "OuterB",
          outerB.selectDynamic("name") == "test"
        )
      },
      test("List of sealed trait") {
        case class Events(list: List[Result])
        val ts: ToStructural[Events] = ToStructural.derived[Events]
        val s                        = ts.toStructural(Events(List(Success(1), Failure("x"), Success(2)))).asInstanceOf[StructuralRecord]

        val list = s.selectDynamic("list").asInstanceOf[List[StructuralRecord]]
        assertTrue(
          list.size == 3,
          list(0).selectDynamic("Tag") == "Success",
          list(1).selectDynamic("Tag") == "Failure",
          list(2).selectDynamic("value") == 2
        )
      },
      test("Option of sealed trait") {
        case class MaybeResult(result: Option[Result])
        val ts: ToStructural[MaybeResult] = ToStructural.derived[MaybeResult]

        val some  = ts.toStructural(MaybeResult(Some(Success(42)))).asInstanceOf[StructuralRecord]
        val inner = some.selectDynamic("result").asInstanceOf[Option[StructuralRecord]].get
        assertTrue(
          inner.selectDynamic("Tag") == "Success",
          inner.selectDynamic("value") == 42
        )

        val none = ts.toStructural(MaybeResult(None)).asInstanceOf[StructuralRecord]
        assertTrue(none.selectDynamic("result") == None)
      }
    ),
    suite("StructuralSchema TypeName for Sum Types")(
      test("sealed trait has correct TypeName") {
        val ts               = ToStructural.derived[Result]
        given Schema[Result] = Schema.derived[Result]
        val structSchema     = ts.structuralSchema

        assertTrue(structSchema.reflect.typeName.name == """({Tag:"Failure",error:String}|{Tag:"Success",value:Int})""")
      },
      test("sealed trait with case object has correct TypeName") {
        val ts               = ToStructural.derived[Status]
        given Schema[Status] = Schema.derived[Status]
        val structSchema     = ts.structuralSchema

        assertTrue(
          structSchema.reflect.typeName.name == """({Tag:"Active",since:String}|{Tag:"Inactive",reason:String}|{Tag:"Unknown"})"""
        )
      },
      test("simple enum has correct TypeName") {
        val ts              = ToStructural.derived[Color]
        given Schema[Color] = Schema.derived[Color]
        val structSchema    = ts.structuralSchema

        assertTrue(structSchema.reflect.typeName.name == """({Tag:"Blue"}|{Tag:"Green"}|{Tag:"Red"})""")
      },
      test("complex enum has correct TypeName") {
        val ts                       = ToStructural.derived[ColorWithValue]
        given Schema[ColorWithValue] = Schema.derived[ColorWithValue]
        val structSchema             = ts.structuralSchema

        assertTrue(
          structSchema.reflect.typeName.name == """({Tag:"Named",name:String}|{Tag:"RGB",b:Int,g:Int,r:Int})"""
        )
      },
      test("case class containing sealed trait has correct TypeName") {
        @nowarn("msg=unused") case class Wrapper(id: Int, result: Result)
        val ts                = ToStructural.derived[Wrapper]
        given Schema[Wrapper] = Schema.derived[Wrapper]
        val structSchema      = ts.structuralSchema

        assertTrue(
          structSchema.reflect.typeName.name == """{id:Int,result:({Tag:"Failure",error:String}|{Tag:"Success",value:Int})}"""
        )
      }
    )
  )
}
