package zio.blocks.schema

import zio.Scope
import zio.blocks.schema.DynamicOpticSpec.suite
import zio.blocks.schema.example.Person
import zio.blocks.schema.example.Person.field
import zio.test._
import zio.test.Assertion._

object PatchSpec extends ZIOSpecDefault {

  case class Person(
    id: Long,
    name: String,
    age: Int,
    address: String,
    childrenAges: List[Int],
    schools: Map[String, Int]
  )
  object Person extends CompanionOptics[Person] {
    implicit val schema: Schema[Person]      = Schema.derived
    val id: Lens[Person, Long]               = field(_.id)
    val name: Lens[Person, String]           = field(_.name)
    val age: Lens[Person, Int]               = field(_.age)
    val address: Lens[Person, String]        = field(_.address)
    val childrenAges: Traversal[Person, Int] = field(_.childrenAges).listValues

  }

  def spec: Spec[TestEnvironment with Scope, Any] = suite("PatchSpec")(
    suite("Patch")(
      test("replace a field with a new value") {
        val person           = Person(12345678901L, "John", 30, "123 Main St", List(5, 7, 9), Map())
        val replaceNamePatch = Patch.replace(Person.name, "Piero")
        assert(replaceNamePatch(person))(equalTo(Some(person.copy(name = "Piero"))))
      },
      test("combine two patches") {
        val person           = Person(12345678901L, "John", 30, "123 Main St", List(5, 7, 9), Map())
        val replaceNamePatch = Patch.replace(Person.name, "Piero")
        val replaceAgePatch  = Patch.replace(Person.age, 40)
        val combined         = replaceAgePatch ++ replaceNamePatch
        assert(combined(person))(equalTo(Some(person.copy(name = "Piero", age = 40))))
      },
      test("patch by focusing on one element of a list") {
        val person             = Person(12345678901L, "John", 30, "123 Main St", List(5, 7, 9), Map())
        val replace2ndChildAge = Patch.replace(Person.childrenAges, 15) // need to find a way to specify the index
        assert(replace2ndChildAge(person))(equalTo(Some(person.copy(childrenAges = List(5, 15, 9)))))
      } @@ TestAspect.ignore,
      test("patch by adding one element to the children list") {
        val person            = Person(12345678901L, "John", 30, "123 Main St", List(5, 7, 9), Map())
        val appendNewChildAge = Patch.replace(Person.childrenAges, 11) // + way to add elements to traversable list?
        assert(appendNewChildAge(person))(equalTo(Some(person.copy(childrenAges = List(5, 7, 9, 11)))))
      } @@ TestAspect.ignore
    )
  )

}
