package zio.blocks.schema

import zio.test._
import zio.blocks.schema.binding.StructuralValue
import zio.blocks.schema.json.JsonBinaryCodecDeriver

object StructuralIntegrationSpec extends ZIOSpecDefault {

  case class Person(name: String, age: Int) derives ToStructural

  case class Company(name: String, ceo: Person) derives ToStructural

  case class Group(members: List[Person]) derives ToStructural

  case class User(email: Option[String]) derives ToStructural

  case class VectorGroup(items: Vector[Person]) derives ToStructural

  case class SetHolder(tags: Set[String]) derives ToStructural

  case class MapHolder(data: Map[String, Int]) derives ToStructural
  case class EitherHolder(result: Either[String, Int]) derives ToStructural

  def spec: Spec[TestEnvironment, Any] = suite("StructuralIntegrationSpec")(
    test("Schema.structural API returns correct schema") {
      val schema           = Schema.derived[Person]
      val structuralSchema = schema.structural

      assertTrue(structuralSchema.reflect.typeName.name == "{age:Int,name:String}")
    },
    test("Schema.structural works with ToStructural conversion") {
      val person = Person("Alice", 30)
      val ts     = implicitly[ToStructural[Person]]

      val sValue  = ts.toStructural(person)
      val sSchema = Schema.derived[Person].structural(ts)

      val dv      = sSchema.toDynamicValue(sValue)
      val decoded = sSchema.fromDynamicValue(dv)

      decoded match {
        case Right(v) =>
          val sv = v.asInstanceOf[StructuralValue]
          assertTrue(sv.selectDynamic("name") == "Alice") &&
          assertTrue(sv.selectDynamic("age") == 30)
        case Left(_) =>
          assertTrue(false)
      }
    },
    test("Nested structures are converted deeply") {
      val ceo     = Person("Boss", 50)
      val company = Company("TechCorp", ceo)
      val ts      = implicitly[ToStructural[Company]]

      val sValue   = ts.toStructural(company)
      val sv       = sValue.asInstanceOf[StructuralValue]
      val ceoValue = sv.selectDynamic("ceo").asInstanceOf[StructuralValue]

      assertTrue(sv.selectDynamic("name") == "TechCorp") &&
      assertTrue(ceoValue.selectDynamic("name") == "Boss") &&
      assertTrue(ceoValue.selectDynamic("age") == 50)
    },
    test("Collections in structural types") {
      val p1    = Person("A", 1)
      val p2    = Person("B", 2)
      val group = Group(List(p1, p2))
      val ts    = implicitly[ToStructural[Group]]

      val sValue  = ts.toStructural(group)
      val sv      = sValue.asInstanceOf[StructuralValue]
      val members = sv.selectDynamic("members").asInstanceOf[List[StructuralValue]]

      assertTrue(members.size == 2) &&
      assertTrue(members(0).selectDynamic("name") == "A") &&
      assertTrue(members(1).selectDynamic("name") == "B")
    },
    test("Option fields in structural types") {
      val u1 = User(Some("foo@bar.com"))
      val u2 = User(None)
      val ts = implicitly[ToStructural[User]]

      val s1 = ts.toStructural(u1).asInstanceOf[StructuralValue]
      val s2 = ts.toStructural(u2).asInstanceOf[StructuralValue]

      assertTrue(s1.selectDynamic("email") == Some("foo@bar.com")) &&
      assertTrue(s2.selectDynamic("email") == None)
    },
    test("JSON Interop: Structural Type -> JSON -> Structural Type") {
      val person  = Person("Charlie", 25)
      val ts      = implicitly[ToStructural[Person]]
      val sValue  = ts.toStructural(person)
      val sSchema = Schema.derived[Person].structural(ts)

      val jsonCodec = sSchema.derive(JsonBinaryCodecDeriver)
      val json      = jsonCodec.encode(sValue)
      val decoded   = jsonCodec.decode(json)

      decoded match {
        case Right(v) =>
          val sv = v.asInstanceOf[StructuralValue]
          assertTrue(sv.selectDynamic("name") == "Charlie") &&
          assertTrue(sv.selectDynamic("age") == 25)
        case Left(_) =>
          assertTrue(false)
      }
    },
    test("Tuples convert to structural types") {
      val tuple = ("foo", 123)
      val ts    = ToStructural.derived[(String, Int)]

      val sValue = ts.toStructural(tuple)
      val sv     = sValue.asInstanceOf[StructuralValue]

      assertTrue(sv.selectDynamic("_1") == "foo") &&
      assertTrue(sv.selectDynamic("_2") == 123)
    },
    test("Vector in structural types") {
      val p1    = Person("X", 10)
      val p2    = Person("Y", 20)
      val group = VectorGroup(Vector(p1, p2))
      val ts    = implicitly[ToStructural[VectorGroup]]

      val sValue = ts.toStructural(group)
      val sv     = sValue.asInstanceOf[StructuralValue]
      val items  = sv.selectDynamic("items").asInstanceOf[Vector[StructuralValue]]

      assertTrue(items.size == 2) &&
      assertTrue(items(0).selectDynamic("name") == "X") &&
      assertTrue(items(1).selectDynamic("name") == "Y")
    },
    test("Set in structural types") {
      val holder = SetHolder(Set("a", "b", "c"))
      val ts     = implicitly[ToStructural[SetHolder]]

      val sValue = ts.toStructural(holder)
      val sv     = sValue.asInstanceOf[StructuralValue]
      val tags   = sv.selectDynamic("tags").asInstanceOf[Set[String]]

      assertTrue(tags == Set("a", "b", "c"))
    },
    test("Map in structural types") {
      val holder = MapHolder(Map("x" -> 1, "y" -> 2))
      val ts     = implicitly[ToStructural[MapHolder]]

      val sValue = ts.toStructural(holder)
      val sv     = sValue.asInstanceOf[StructuralValue]
      val data   = sv.selectDynamic("data").asInstanceOf[Map[String, Int]]

      assertTrue(data == Map("x" -> 1, "y" -> 2))
    },
    test("Either in structural types - Right") {
      val holder = EitherHolder(Right(42))
      val ts     = implicitly[ToStructural[EitherHolder]]

      val sValue = ts.toStructural(holder)
      val sv     = sValue.asInstanceOf[StructuralValue]
      val result = sv.selectDynamic("result").asInstanceOf[Either[String, Int]]

      assertTrue(result == Right(42))
    },
    test("Either in structural types - Left") {
      val holder = EitherHolder(Left("error"))
      val ts     = implicitly[ToStructural[EitherHolder]]

      val sValue = ts.toStructural(holder)
      val sv     = sValue.asInstanceOf[StructuralValue]
      val result = sv.selectDynamic("result").asInstanceOf[Either[String, Int]]

      assertTrue(result == Left("error"))
    },
    test("3-tuple converts to structural types") {
      val tuple = ("a", 1, true)
      val ts    = ToStructural.derived[(String, Int, Boolean)]

      val sValue = ts.toStructural(tuple)
      val sv     = sValue.asInstanceOf[StructuralValue]

      assertTrue(sv.selectDynamic("_1") == "a") &&
      assertTrue(sv.selectDynamic("_2") == 1) &&
      assertTrue(sv.selectDynamic("_3") == true)
    },
    test("5-tuple converts to structural types") {
      val tuple = ("a", 1, true, 2.5, 'x')
      val ts    = ToStructural.derived[(String, Int, Boolean, Double, Char)]

      val sValue = ts.toStructural(tuple)
      val sv     = sValue.asInstanceOf[StructuralValue]

      assertTrue(sv.selectDynamic("_1") == "a") &&
      assertTrue(sv.selectDynamic("_2") == 1) &&
      assertTrue(sv.selectDynamic("_3") == true) &&
      assertTrue(sv.selectDynamic("_4") == 2.5) &&
      assertTrue(sv.selectDynamic("_5") == 'x')
    },
    test("fully applied generic type to structural") {
      case class Container[T](value: T, label: String)
      val ts = ToStructural.derived[Container[Int]]

      val container = Container(42, "answer")
      val sValue    = ts.toStructural(container)
      val sv        = sValue.asInstanceOf[StructuralValue]

      assertTrue(sv.selectDynamic("value") == 42) &&
      assertTrue(sv.selectDynamic("label") == "answer")
    },
    test("generic with nested case class fields") {
      case class Inner(x: Int, y: Int)
      case class Wrapper[T](inner: T, name: String)
      val ts = ToStructural.derived[Wrapper[Inner]]

      val wrapper = Wrapper(Inner(1, 2), "point")
      val sValue  = ts.toStructural(wrapper)
      val sv      = sValue.asInstanceOf[StructuralValue]

      val innerSv = sv.selectDynamic("inner").asInstanceOf[StructuralValue]

      assertTrue(sv.selectDynamic("name") == "point") &&
      assertTrue(innerSv.selectDynamic("x") == 1) &&
      assertTrue(innerSv.selectDynamic("y") == 2)
    },
    test("generic with List of case class") {
      case class Item(id: Int, name: String)
      case class Container[T](items: List[T])
      val ts = ToStructural.derived[Container[Item]]

      val container = Container(List(Item(1, "a"), Item(2, "b")))
      val sValue    = ts.toStructural(container)
      val sv        = sValue.asInstanceOf[StructuralValue]

      val items = sv.selectDynamic("items").asInstanceOf[List[StructuralValue]]

      assertTrue(items.size == 2) &&
      assertTrue(items(0).selectDynamic("id") == 1) &&
      assertTrue(items(1).selectDynamic("name") == "b")
    }
  )
}
