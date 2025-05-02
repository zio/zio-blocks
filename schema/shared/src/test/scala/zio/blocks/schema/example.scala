package zio.blocks.schema

object example {
  case class Person(id: Long, name: String, age: Int, address: String, childrenAges: List[Int])

  object Person extends CompanionOptics[Person] {
    implicit val schema: Schema[Person]      = Schema.derived
    val id: Lens[Person, Long]               = field(_.id)
    val name: Lens[Person, String]           = field(_.name)
    val age: Lens[Person, Int]               = field(_.age)
    val address: Lens[Person, String]        = field(_.address)
    val childrenAges: Traversal[Person, Int] = field(_.childrenAges).listValues
  }

  def main(args: Array[String]): Unit = {
    val person = Person(12345678901L, "John", 30, "123 Main St", List(5, 7, 9))

    println("id:         " + Person.id.get(person))
    println("name:       " + Person.name.get(person))
    println("age:        " + Person.age.get(person))
    println("address:    " + Person.address.get(person))
    println("newPerson:  " + Person.name.replace(person, "Jane"))
    println("newPerson2: " + Person.childrenAges.modify(person, _ + 1))
  }
}
