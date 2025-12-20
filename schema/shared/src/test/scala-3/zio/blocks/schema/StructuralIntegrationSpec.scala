package zio.blocks.schema

import zio.test._
import zio.blocks.schema.binding.StructuralValue
import zio.blocks.schema.json.JsonBinaryCodecDeriver

object StructuralIntegrationSpec extends ZIOSpecDefault {

  case class Person(name: String, age: Int) derives ToStructural

  case class Company(name: String, ceo: Person) derives ToStructural

  case class Group(members: List[Person]) derives ToStructural

  case class User(email: Option[String]) derives ToStructural

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
    }
  )
}
