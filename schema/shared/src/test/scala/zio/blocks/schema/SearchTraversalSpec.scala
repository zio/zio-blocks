package zio.blocks.schema

import zio.test._
import zio.test.Assertion._
import zio.blocks.typeid.TypeId

object SearchTraversalSpec extends SchemaBaseSpec {

  // Test data types
  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  case class Company(name: String, ceo: Person, employees: List[Person])
  object Company {
    implicit val schema: Schema[Company] = Schema.derived
  }

  case class Document(title: String, author: Person, reviewers: Vector[Person], approver: Option[Person])
  object Document {
    implicit val schema: Schema[Document] = Schema.derived
  }

  // Nested structure for testing deep search
  case class Department(name: String, manager: Person, teams: List[Team])
  object Department {
    implicit val schema: Schema[Department] = Schema.derived
  }

  case class Team(name: String, lead: Person, members: List[Person])
  object Team {
    implicit val schema: Schema[Team] = Schema.derived
  }

  // Recursive structure for testing cycle handling
  case class TreeNode(value: String, children: List[TreeNode])
  object TreeNode {
    implicit val schema: Schema[TreeNode] = Schema.derived
  }

  // Structure with Map for testing map traversal
  case class Registry(entries: Map[String, Person])
  object Registry {
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
  object ContainerHolder {
    implicit val schema: Schema[ContainerHolder] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("SearchTraversalSpec")(
    suite("fold")(
      test("collects all matching values in flat structure") {
        val company  = Company("Acme", Person("CEO", 50), List(Person("Alice", 30), Person("Bob", 25)))
        val traversal = SearchTraversal[Company, Person]

        val names = traversal.fold[List[String]](company)(List.empty, (acc, p) => acc :+ p.name)

        assert(names.sorted)(equalTo(List("Alice", "Bob", "CEO")))
      },
      test("collects all matching values in nested structure") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(
            Team("Frontend", Person("FE Lead", 35), List(Person("Dev1", 28), Person("Dev2", 26))),
            Team("Backend", Person("BE Lead", 38), List(Person("Dev3", 30)))
          )
        )
        val traversal = SearchTraversal[Department, Person]

        val names = traversal.fold[List[String]](dept)(List.empty, (acc, p) => acc :+ p.name)

        // Should find: Director, FE Lead, Dev1, Dev2, BE Lead, Dev3
        assert(names.sorted)(equalTo(List("BE Lead", "Dev1", "Dev2", "Dev3", "Director", "FE Lead")))
      },
      test("returns zero for empty result") {
        val company  = Company("Acme", Person("CEO", 50), List.empty)
        val traversal = SearchTraversal[Company, Team] // Looking for Team in Company (won't find any)

        val result = traversal.fold[Int](company)(0, (acc, _) => acc + 1)

        assert(result)(equalTo(0))
      },
      test("finds root value if it matches") {
        val person   = Person("Alice", 30)
        val traversal = SearchTraversal[Person, Person]

        val names = traversal.fold[List[String]](person)(List.empty, (acc, p) => acc :+ p.name)

        assert(names)(equalTo(List("Alice")))
      },
      test("searches through Option values") {
        val doc = Document("Report", Person("Author", 35), Vector(Person("R1", 40)), Some(Person("Approver", 50)))
        val traversal = SearchTraversal[Document, Person]

        val count = traversal.fold[Int](doc)(0, (acc, _) => acc + 1)

        // Author, R1, Approver = 3 persons
        assert(count)(equalTo(3))
      },
      test("searches through Map values") {
        val registry = Registry(Map("a" -> Person("Alice", 30), "b" -> Person("Bob", 25)))
        val traversal = SearchTraversal[Registry, Person]

        val names = traversal.fold[List[String]](registry)(List.empty, (acc, p) => acc :+ p.name)

        assert(names.sorted)(equalTo(List("Alice", "Bob")))
      },
      test("searches through Variant payloads") {
        val holder = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val traversal = SearchTraversal[ContainerHolder, Person]

        val names = traversal.fold[List[String]](holder)(List.empty, (acc, p) => acc :+ p.name)

        assert(names)(equalTo(List("Alice")))
      },
      test("handles recursive types without infinite loop") {
        val tree = TreeNode(
          "root",
          List(
            TreeNode("child1", List(TreeNode("grandchild1", Nil))),
            TreeNode("child2", Nil)
          )
        )
        val traversal = SearchTraversal[TreeNode, TreeNode]

        val values = traversal.fold[List[String]](tree)(List.empty, (acc, n) => acc :+ n.value)

        // Should find: root, child1, grandchild1, child2
        assert(values.sorted)(equalTo(List("child1", "child2", "grandchild1", "root")))
      }
    ),
    suite("modify")(
      test("modifies all matching values") {
        val company  = Company("Acme", Person("CEO", 50), List(Person("Alice", 30), Person("Bob", 25)))
        val traversal = SearchTraversal[Company, Person]

        val modified = traversal.modify(company, p => p.copy(age = p.age + 1))

        assert(modified.ceo.age)(equalTo(51)) &&
        assert(modified.employees.map(_.age))(equalTo(List(31, 26)))
      },
      test("modifies nested values") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(Team("Frontend", Person("Lead", 35), List(Person("Dev", 28))))
        )
        val traversal = SearchTraversal[Department, Person]

        val modified = traversal.modify(dept, p => p.copy(name = p.name.toUpperCase))

        assert(modified.manager.name)(equalTo("DIRECTOR")) &&
        assert(modified.teams.head.lead.name)(equalTo("LEAD")) &&
        assert(modified.teams.head.members.head.name)(equalTo("DEV"))
      },
      test("returns original when no matches") {
        val company  = Company("Acme", Person("CEO", 50), List.empty)
        val traversal = SearchTraversal[Company, Team]

        val modified = traversal.modify(company, t => t.copy(name = "Modified"))

        assert(modified)(equalTo(company))
      },
      test("modifies values in Map") {
        val registry = Registry(Map("a" -> Person("Alice", 30), "b" -> Person("Bob", 25)))
        val traversal = SearchTraversal[Registry, Person]

        val modified = traversal.modify(registry, p => p.copy(age = 100))

        assert(modified.entries("a").age)(equalTo(100)) &&
        assert(modified.entries("b").age)(equalTo(100))
      }
    ),
    suite("modifyOption")(
      test("returns Some when modifications made") {
        val company  = Company("Acme", Person("CEO", 50), List(Person("Alice", 30)))
        val traversal = SearchTraversal[Company, Person]

        val result = traversal.modifyOption(company, p => p.copy(age = p.age + 1))

        assert(result)(isSome(hasField("ceo", (c: Company) => c.ceo.age, equalTo(51))))
      },
      test("returns None when no modifications") {
        val company  = Company("Acme", Person("CEO", 50), List.empty)
        val traversal = SearchTraversal[Company, Team]

        val result = traversal.modifyOption(company, t => t.copy(name = "X"))

        assert(result)(isNone)
      }
    ),
    suite("modifyOrFail")(
      test("returns Right when modifications made") {
        val company  = Company("Acme", Person("CEO", 50), List.empty)
        val traversal = SearchTraversal[Company, Person]

        val result = traversal.modifyOrFail(company, p => p.copy(age = p.age + 1))

        assert(result)(isRight(hasField("ceo", (c: Company) => c.ceo.age, equalTo(51))))
      },
      test("returns Left with OpticCheck when no matches") {
        val company  = Company("Acme", Person("CEO", 50), List.empty)
        val traversal = SearchTraversal[Company, Team]

        val result = traversal.modifyOrFail(company, t => t.copy(name = "X"))

        assert(result)(isLeft)
      }
    ),
    suite("check")(
      test("returns None when matches exist") {
        val company  = Company("Acme", Person("CEO", 50), List.empty)
        val traversal = SearchTraversal[Company, Person]

        assert(traversal.check(company))(isNone)
      },
      test("returns Some(OpticCheck) when no matches") {
        val company  = Company("Acme", Person("CEO", 50), List.empty)
        val traversal = SearchTraversal[Company, Team]

        assert(traversal.check(company))(isSome)
      }
    ),
    suite("source and focus")(
      test("returns correct source Reflect") {
        val traversal = SearchTraversal[Company, Person]
        assert(traversal.source.typeId)(equalTo(TypeId.of[Company]))
      },
      test("returns correct focus Reflect") {
        val traversal = SearchTraversal[Company, Person]
        assert(traversal.focus.typeId)(equalTo(TypeId.of[Person]))
      }
    ),
    suite("toDynamic")(
      test("produces correct DynamicOptic with TypeSearch node") {
        val traversal = SearchTraversal[Company, Person]
        val dynamic   = traversal.toDynamic

        assert(dynamic.nodes.length)(equalTo(1)) &&
        assert(dynamic.nodes.head)(isSubtype[DynamicOptic.Node.TypeSearch](anything))
      },
      test("TypeSearch node contains correct TypeId") {
        val traversal = SearchTraversal[Company, Person]
        val dynamic   = traversal.toDynamic

        dynamic.nodes.head match {
          case DynamicOptic.Node.TypeSearch(typeId) =>
            assert(typeId.name)(equalTo("Person"))
          case _ =>
            assert(false)(isTrue)
        }
      }
    ),
    suite("toString")(
      test("produces readable output") {
        val traversal = SearchTraversal[Company, Person]
        assert(traversal.toString)(equalTo("Traversal(_.search[Person])"))
      }
    ),
    suite("equals and hashCode")(
      test("equal traversals have same hashCode") {
        val t1 = SearchTraversal[Company, Person]
        val t2 = SearchTraversal[Company, Person]

        assert(t1.hashCode)(equalTo(t2.hashCode))
      },
      test("equal traversals are equal") {
        val t1 = SearchTraversal[Company, Person]
        val t2 = SearchTraversal[Company, Person]

        assert(t1)(equalTo(t2))
      },
      test("different traversals are not equal") {
        val t1 = SearchTraversal[Company, Person]
        val t2 = SearchTraversal[Company, Team]

        assert(t1: Any)(not(equalTo(t2)))
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
        val traversal = SearchTraversal[Department, Person]

        // Using age as visit order tracker
        val ages = traversal.fold[List[Int]](dept)(List.empty, (acc, p) => acc :+ p.age)

        // Expected DFS order: M(1) -> L1(2) -> D1(3) -> L2(4)
        assert(ages)(equalTo(List(1, 2, 3, 4)))
      }
    ),
    suite("composition")(
      test("composes SearchTraversal with Lens to access fields of found values") {
        val company  = Company("Acme", Person("CEO", 50), List(Person("Alice", 30), Person("Bob", 25)))
        val traversal = SearchTraversal[Company, Person]

        // Create lens using Reflect API
        val personRecord = Person.schema.reflect.asInstanceOf[Reflect.Record.Bound[Person]]
        val personNameLens = Lens(personRecord, personRecord.fields(0).asInstanceOf[Term.Bound[Person, String]])

        // Compose search with lens to get names
        val searchThenName = traversal(personNameLens)

        val names = searchThenName.fold[List[String]](company)(List.empty, (acc, name) => acc :+ name)

        assert(names.sorted)(equalTo(List("Alice", "Bob", "CEO")))
      },
      test("composed traversal modifies nested fields") {
        val company  = Company("Acme", Person("CEO", 50), List(Person("Alice", 30)))
        val traversal = SearchTraversal[Company, Person]

        val personRecord = Person.schema.reflect.asInstanceOf[Reflect.Record.Bound[Person]]
        val personNameLens = Lens(personRecord, personRecord.fields(0).asInstanceOf[Term.Bound[Person, String]])

        val searchThenName = traversal(personNameLens)
        val modified       = searchThenName.modify(company, (name: String) => name.toUpperCase)

        assert(modified.ceo.name)(equalTo("CEO")) &&
        assert(modified.employees.head.name)(equalTo("ALICE"))
      },
      test("composed traversal toDynamic includes both nodes") {
        val traversal = SearchTraversal[Company, Person]

        val personRecord = Person.schema.reflect.asInstanceOf[Reflect.Record.Bound[Person]]
        val personNameLens = Lens(personRecord, personRecord.fields(0).asInstanceOf[Term.Bound[Person, String]])

        val searchThenName = traversal(personNameLens)
        val dynamic        = searchThenName.toDynamic

        assert(dynamic.nodes.length)(equalTo(2)) &&
        assert(dynamic.nodes(0))(isSubtype[DynamicOptic.Node.TypeSearch](anything)) &&
        assert(dynamic.nodes(1))(isSubtype[DynamicOptic.Node.Field](anything))
      }
    )
  )
}
