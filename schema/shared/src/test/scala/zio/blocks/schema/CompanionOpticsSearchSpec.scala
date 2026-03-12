package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

object CompanionOpticsSearchSpec extends SchemaBaseSpec {

  // Test data types
  case class Person(name: String, age: Int)
  object Person extends CompanionOptics[Person] {
    implicit val schema: Schema[Person] = Schema.derived
  }

  case class Company(name: String, ceo: Person, employees: List[Person])
  object Company extends CompanionOptics[Company] {
    implicit val schema: Schema[Company] = Schema.derived
  }

  case class Document(title: String, author: Person, reviewers: Vector[Person], approver: Option[Person])
  object Document extends CompanionOptics[Document] {
    implicit val schema: Schema[Document] = Schema.derived
  }

  // Nested structure for testing deep search
  case class Department(name: String, manager: Person, teams: List[Team])
  object Department extends CompanionOptics[Department] {
    implicit val schema: Schema[Department] = Schema.derived
  }

  case class Team(name: String, lead: Person, members: List[Person])
  object Team extends CompanionOptics[Team] {
    implicit val schema: Schema[Team] = Schema.derived
  }

  // Structure with Map for testing map traversal
  case class Registry(entries: Map[String, Person])
  object Registry extends CompanionOptics[Registry] {
    implicit val schema: Schema[Registry] = Schema.derived
  }

  // Variant for testing variant traversal
  sealed trait Container
  case class PersonContainer(person: Person) extends Container
  case class StringContainer(value: String)  extends Container
  object Container {
    implicit val schema: Schema[Container] = Schema.derived
  }

  case class ContainerHolder(containers: List[Container])
  object ContainerHolder extends CompanionOptics[ContainerHolder] {
    implicit val schema: Schema[ContainerHolder] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("CompanionOpticsSearchSpec")(
    suite("optic(_.searchFor[T]) from root")(
      test("creates SearchTraversal that finds all matching values") {
        val company   = Company("Acme", Person("CEO", 50), List(Person("Alice", 30), Person("Bob", 25)))
        val traversal = Company.optic(_.searchFor[Person])

        val names = traversal.fold[List[String]](company)(List.empty, (acc, p) => acc :+ p.name)

        assert(names.sorted)(equalTo(List("Alice", "Bob", "CEO")))
      },
      test("finds values in nested structures") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(
            Team("Frontend", Person("FE Lead", 35), List(Person("Dev1", 28), Person("Dev2", 26))),
            Team("Backend", Person("BE Lead", 38), List(Person("Dev3", 30)))
          )
        )
        val traversal = Department.optic(_.searchFor[Person])

        val names = traversal.fold[List[String]](dept)(List.empty, (acc, p) => acc :+ p.name)

        // Should find: Director, FE Lead, Dev1, Dev2, BE Lead, Dev3
        assert(names.sorted)(equalTo(List("BE Lead", "Dev1", "Dev2", "Dev3", "Director", "FE Lead")))
      },
      test("returns empty result when no matches") {
        val company   = Company("Acme", Person("CEO", 50), List.empty)
        val traversal = Company.optic(_.searchFor[Team]) // Looking for Team in Company (won't find any)

        val result = traversal.fold[Int](company)(0, (acc, _) => acc + 1)

        assert(result)(equalTo(0))
      },
      test("finds root value if it matches") {
        val person    = Person("Alice", 30)
        val traversal = Person.optic(_.searchFor[Person])

        val names = traversal.fold[List[String]](person)(List.empty, (acc, p) => acc :+ p.name)

        assert(names)(equalTo(List("Alice")))
      }
    ),
    suite("optic(_.field.searchFor[T]) - search after lens")(
      test("searches within field value") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(
            Team("Frontend", Person("FE Lead", 35), List(Person("Dev1", 28)))
          )
        )
        val traversal = Department.optic(_.teams.searchFor[Person])

        val names = traversal.fold[List[String]](dept)(List.empty, (acc, p) => acc :+ p.name)

        // Should only find persons within teams, not the manager
        assert(names.sorted)(equalTo(List("Dev1", "FE Lead")))
      },
      test("modifies values within field") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(
            Team("Frontend", Person("FE Lead", 35), List(Person("Dev1", 28)))
          )
        )
        val traversal = Department.optic(_.teams.searchFor[Person])

        val modified = traversal.modify(dept, (p: Person) => p.copy(age = p.age + 10))

        // Manager should be unchanged
        assert(modified.manager.age)(equalTo(45)) &&
        // Persons within teams should be modified
        assert(modified.teams.head.lead.age)(equalTo(45)) &&
        assert(modified.teams.head.members.head.age)(equalTo(38))
      },
      test("toDynamic produces correct path for field.searchFor") {
        val traversal = Department.optic(_.teams.searchFor[Person])
        val dynamic   = traversal.toDynamic

        assert(dynamic.nodes.length)(equalTo(2)) &&
        assert(dynamic.nodes(0))(isSubtype[DynamicOptic.Node.Field](anything)) &&
        assert(dynamic.nodes(1))(isSubtype[DynamicOptic.Node.TypeSearch](anything))
      }
    ),
    suite("optic(_.field.searchFor[T].field) - nested composition")(
      test("accesses nested field on searched values within field") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(
            Team("Frontend", Person("FE Lead", 35), List(Person("Dev1", 28)))
          )
        )
        val traversal = Department.optic(_.teams.searchFor[Person].name)

        val names = traversal.fold[List[String]](dept)(List.empty, (acc, name) => acc :+ name)

        // Should get names of persons within teams, not the manager
        assert(names.sorted)(equalTo(List("Dev1", "FE Lead")))
      }
    ),
    suite("optic(_.searchFor[T].field) - lens after search")(
      test("accesses field on all found values") {
        val company   = Company("Acme", Person("CEO", 50), List(Person("Alice", 30), Person("Bob", 25)))
        val traversal = Company.optic(_.searchFor[Person].name)

        val names = traversal.fold[List[String]](company)(List.empty, (acc, name) => acc :+ name)

        assert(names.sorted)(equalTo(List("Alice", "Bob", "CEO")))
      },
      test("modifies field on all found values") {
        val company   = Company("Acme", Person("CEO", 50), List(Person("Alice", 30), Person("Bob", 25)))
        val traversal = Company.optic(_.searchFor[Person].name)

        val modified = traversal.modify(company, (name: String) => name.toUpperCase)

        assert(modified.ceo.name)(equalTo("CEO")) &&
        assert(modified.employees.map(_.name))(equalTo(List("ALICE", "BOB")))
      }
    ),
    suite("optic(_.each.searchFor[T]) - search after traversal") {
      import ContainerHolder._
      suite("with imported each extension")(
        test("searches within each collection element") {
          val holder    = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
          val traversal = ContainerHolder.optic(_.containers.each.searchFor[Person])

          val names = traversal.fold[List[String]](holder)(List.empty, (acc, p) => acc :+ p.name)

          assert(names)(equalTo(List("Alice")))
        },
        test("modifies searched values within traversal") {
          val holder    = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
          val traversal = ContainerHolder.optic(_.containers.each.searchFor[Person])

          val modified = traversal.modify(holder, (p: Person) => p.copy(age = p.age + 10))

          // Extract the modified person from the container
          val modifiedPerson = modified.containers.head.asInstanceOf[PersonContainer].person
          assert(modifiedPerson.age)(equalTo(40))
        },
        test("handles multiple matches across collection elements") {
          val holder = ContainerHolder(
            List(
              PersonContainer(Person("Alice", 30)),
              PersonContainer(Person("Bob", 25)),
              StringContainer("hello")
            )
          )
          val traversal = ContainerHolder.optic(_.containers.each.searchFor[Person])

          val names = traversal.fold[List[String]](holder)(List.empty, (acc, p) => acc :+ p.name)

          assert(names.sorted)(equalTo(List("Alice", "Bob")))
        }
      )
    },
    suite("optic(_.field.each.searchFor[T]) - lens then traversal then search") {
      import Department._
      suite("with imported each extension")(
        test("searches through field's collection elements") {
          val dept = Department(
            "Engineering",
            Person("Director", 45),
            List(
              Team("Frontend", Person("FE Lead", 35), List(Person("Dev1", 28), Person("Dev2", 26))),
              Team("Backend", Person("BE Lead", 38), List(Person("Dev3", 30)))
            )
          )
          // This goes: teams (Lens) -> each (Traversal) -> searchFor[Person] (SearchTraversal)
          val traversal = Department.optic(_.teams.each.searchFor[Person])

          val names = traversal.fold[List[String]](dept)(List.empty, (acc, p) => acc :+ p.name)

          // Should find all persons within all teams (leads and members), not the manager
          assert(names.sorted)(equalTo(List("BE Lead", "Dev1", "Dev2", "Dev3", "FE Lead")))
        },
        test("modifies searched values through field's collection") {
          val dept = Department(
            "Engineering",
            Person("Director", 45),
            List(
              Team("Frontend", Person("FE Lead", 35), List(Person("Dev1", 28)))
            )
          )
          val traversal = Department.optic(_.teams.each.searchFor[Person])

          val modified = traversal.modify(dept, (p: Person) => p.copy(name = p.name.toUpperCase))

          // Manager should be unchanged
          assert(modified.manager.name)(equalTo("Director")) &&
          // Persons within teams should be uppercased
          assert(modified.teams.head.lead.name)(equalTo("FE LEAD")) &&
          assert(modified.teams.head.members.head.name)(equalTo("DEV1"))
        }
      )
    },
    suite("optic(_.field.each.searchFor[T].field) - full composition chain") {
      import Department._
      suite("with imported each extension")(
        test("accesses field on searched values through collection") {
          val dept = Department(
            "Engineering",
            Person("Director", 45),
            List(
              Team("Frontend", Person("FE Lead", 35), List(Person("Dev1", 28))),
              Team("Backend", Person("BE Lead", 38), List())
            )
          )
          // This goes: teams -> each -> searchFor[Person] -> name
          val traversal = Department.optic(_.teams.each.searchFor[Person].name)

          val names = traversal.fold[List[String]](dept)(List.empty, (acc, name) => acc :+ name)

          assert(names.sorted)(equalTo(List("BE Lead", "Dev1", "FE Lead")))
        },
        test("modifies field on searched values through collection") {
          val dept = Department(
            "Engineering",
            Person("Director", 45),
            List(
              Team("Frontend", Person("FE Lead", 35), List(Person("Dev1", 28)))
            )
          )
          val traversal = Department.optic(_.teams.each.searchFor[Person].age)

          val modified = traversal.modify(dept, (age: Int) => age + 100)

          // Manager should be unchanged
          assert(modified.manager.age)(equalTo(45)) &&
          // Persons within teams should have age increased
          assert(modified.teams.head.lead.age)(equalTo(135)) &&
          assert(modified.teams.head.members.head.age)(equalTo(128))
        }
      )
    },
    suite("modify operations")(
      test("modifies all matching values from root") {
        val company   = Company("Acme", Person("CEO", 50), List(Person("Alice", 30), Person("Bob", 25)))
        val traversal = Company.optic(_.searchFor[Person])

        val modified = traversal.modify(company, (p: Person) => p.copy(age = p.age + 1))

        assert(modified.ceo.age)(equalTo(51)) &&
        assert(modified.employees.map(_.age))(equalTo(List(31, 26)))
      },
      test("modifies nested values") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(Team("Frontend", Person("Lead", 35), List(Person("Dev", 28))))
        )
        val traversal = Department.optic(_.searchFor[Person])

        val modified = traversal.modify(dept, (p: Person) => p.copy(name = p.name.toUpperCase))

        assert(modified.manager.name)(equalTo("DIRECTOR")) &&
        assert(modified.teams.head.lead.name)(equalTo("LEAD")) &&
        assert(modified.teams.head.members.head.name)(equalTo("DEV"))
      }
    ),
    suite("toDynamic")(
      test("produces correct DynamicOptic with TypeSearch node") {
        val traversal = Company.optic(_.searchFor[Person])
        val dynamic   = traversal.toDynamic

        assert(dynamic.nodes.length)(equalTo(1)) &&
        assert(dynamic.nodes.head)(isSubtype[DynamicOptic.Node.TypeSearch](anything))
      },
      test("composed search+field produces correct DynamicOptic") {
        val traversal = Company.optic(_.searchFor[Person].name)
        val dynamic   = traversal.toDynamic

        assert(dynamic.nodes.length)(equalTo(2)) &&
        assert(dynamic.nodes(0))(isSubtype[DynamicOptic.Node.TypeSearch](anything)) &&
        assert(dynamic.nodes(1))(isSubtype[DynamicOptic.Node.Field](anything))
      }
    ),
    suite("toString")(
      test("produces readable output for search from root") {
        val traversal = Company.optic(_.searchFor[Person])
        assert(traversal.toString)(equalTo("Traversal(_.searchFor[Person])"))
      }
    ),
    suite("searches in complex structures")(
      test("searches through Map values") {
        val registry  = Registry(Map("a" -> Person("Alice", 30), "b" -> Person("Bob", 25)))
        val traversal = Registry.optic(_.searchFor[Person])

        val names = traversal.fold[List[String]](registry)(List.empty, (acc, p) => acc :+ p.name)

        assert(names.sorted)(equalTo(List("Alice", "Bob")))
      },
      test("searches through Option values") {
        val doc       = Document("Report", Person("Author", 35), Vector(Person("R1", 40)), Some(Person("Approver", 50)))
        val traversal = Document.optic(_.searchFor[Person])

        val count = traversal.fold[Int](doc)(0, (acc, _) => acc + 1)

        // Author, R1, Approver = 3 persons
        assert(count)(equalTo(3))
      },
      test("searches through Variant payloads") {
        val holder    = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val traversal = ContainerHolder.optic(_.searchFor[Person])

        val names = traversal.fold[List[String]](holder)(List.empty, (acc, p) => acc :+ p.name)

        assert(names)(equalTo(List("Alice")))
      }
    ),
    suite("depth-first ordering")(
      test("visits values in depth-first left-to-right order") {
        val dept = Department(
          "Root",
          Person("M", 1),
          List(
            Team("T1", Person("L1", 2), List(Person("D1", 3))),
            Team("T2", Person("L2", 4), List())
          )
        )
        val traversal = Department.optic(_.searchFor[Person])

        // Using age as visit order tracker
        val ages = traversal.fold[List[Int]](dept)(List.empty, (acc, p) => acc :+ p.age)

        // Expected DFS order: M(1) -> L1(2) -> D1(3) -> L2(4)
        assert(ages)(equalTo(List(1, 2, 3, 4)))
      }
    )
  )
}
