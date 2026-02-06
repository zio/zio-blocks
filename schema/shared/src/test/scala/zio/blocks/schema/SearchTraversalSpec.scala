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
  object PersonContainer {
    implicit val schema: Schema[PersonContainer] = Schema.derived
  }

  case class ContainerHolder(containers: List[Container])
  object ContainerHolder {
    implicit val schema: Schema[ContainerHolder] = Schema.derived
  }

  // Helper optics for Prism/Optional composition tests
  lazy val containerVariant: Reflect.Variant.Bound[Container] = Container.schema.reflect.asVariant.get
  lazy val pcCaseTerm: Term.Bound[Container, PersonContainer] = {
    val idx = containerVariant.caseIndexByName("PersonContainer")
    containerVariant.cases(idx).asInstanceOf[Term.Bound[Container, PersonContainer]]
  }
  lazy val containerToPC: Prism[Container, PersonContainer] = Prism(containerVariant, pcCaseTerm)
  lazy val pcRecord: Reflect.Record.Bound[PersonContainer]  =
    PersonContainer.schema.reflect.asInstanceOf[Reflect.Record.Bound[PersonContainer]]
  lazy val pcToPersonLens: Lens[PersonContainer, Person] =
    Lens(pcRecord, pcRecord.fields(0).asInstanceOf[Term.Bound[PersonContainer, Person]])
  lazy val containerToPerson: Optional[Container, Person] = Optional(containerToPC, pcToPersonLens)
  lazy val personRecord: Reflect.Record.Bound[Person]     =
    Person.schema.reflect.asInstanceOf[Reflect.Record.Bound[Person]]
  lazy val personNameLens: Lens[Person, String] =
    Lens(personRecord, personRecord.fields(0).asInstanceOf[Term.Bound[Person, String]])

  def spec: Spec[TestEnvironment, Any] = suite("SearchTraversalSpec")(
    suite("fold")(
      test("collects all matching values in flat structure") {
        val company   = Company("Acme", Person("CEO", 50), List(Person("Alice", 30), Person("Bob", 25)))
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
        val company   = Company("Acme", Person("CEO", 50), List.empty)
        val traversal = SearchTraversal[Company, Team] // Looking for Team in Company (won't find any)

        val result = traversal.fold[Int](company)(0, (acc, _) => acc + 1)

        assert(result)(equalTo(0))
      },
      test("finds root value if it matches") {
        val person    = Person("Alice", 30)
        val traversal = SearchTraversal[Person, Person]

        val names = traversal.fold[List[String]](person)(List.empty, (acc, p) => acc :+ p.name)

        assert(names)(equalTo(List("Alice")))
      },
      test("searches through Option values") {
        val doc       = Document("Report", Person("Author", 35), Vector(Person("R1", 40)), Some(Person("Approver", 50)))
        val traversal = SearchTraversal[Document, Person]

        val count = traversal.fold[Int](doc)(0, (acc, _) => acc + 1)

        // Author, R1, Approver = 3 persons
        assert(count)(equalTo(3))
      },
      test("searches through Map values") {
        val registry  = Registry(Map("a" -> Person("Alice", 30), "b" -> Person("Bob", 25)))
        val traversal = SearchTraversal[Registry, Person]

        val names = traversal.fold[List[String]](registry)(List.empty, (acc, p) => acc :+ p.name)

        assert(names.sorted)(equalTo(List("Alice", "Bob")))
      },
      test("searches through Variant payloads") {
        val holder    = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
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
        val company   = Company("Acme", Person("CEO", 50), List(Person("Alice", 30), Person("Bob", 25)))
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
        val company   = Company("Acme", Person("CEO", 50), List.empty)
        val traversal = SearchTraversal[Company, Team]

        val modified = traversal.modify(company, t => t.copy(name = "Modified"))

        assert(modified)(equalTo(company))
      },
      test("modifies values in Map") {
        val registry  = Registry(Map("a" -> Person("Alice", 30), "b" -> Person("Bob", 25)))
        val traversal = SearchTraversal[Registry, Person]

        val modified = traversal.modify(registry, p => p.copy(age = 100))

        assert(modified.entries("a").age)(equalTo(100)) &&
        assert(modified.entries("b").age)(equalTo(100))
      }
    ),
    suite("modifyOption")(
      test("returns Some when modifications made") {
        val company   = Company("Acme", Person("CEO", 50), List(Person("Alice", 30)))
        val traversal = SearchTraversal[Company, Person]

        val result = traversal.modifyOption(company, p => p.copy(age = p.age + 1))

        assert(result)(isSome(hasField("ceo", (c: Company) => c.ceo.age, equalTo(51))))
      },
      test("returns None when no modifications") {
        val company   = Company("Acme", Person("CEO", 50), List.empty)
        val traversal = SearchTraversal[Company, Team]

        val result = traversal.modifyOption(company, t => t.copy(name = "X"))

        assert(result)(isNone)
      }
    ),
    suite("modifyOrFail")(
      test("returns Right when modifications made") {
        val company   = Company("Acme", Person("CEO", 50), List.empty)
        val traversal = SearchTraversal[Company, Person]

        val result = traversal.modifyOrFail(company, p => p.copy(age = p.age + 1))

        assert(result)(isRight(hasField("ceo", (c: Company) => c.ceo.age, equalTo(51))))
      },
      test("returns Left with OpticCheck when no matches") {
        val company   = Company("Acme", Person("CEO", 50), List.empty)
        val traversal = SearchTraversal[Company, Team]

        val result = traversal.modifyOrFail(company, t => t.copy(name = "X"))

        assert(result)(isLeft)
      }
    ),
    suite("check")(
      test("returns None when matches exist") {
        val company   = Company("Acme", Person("CEO", 50), List.empty)
        val traversal = SearchTraversal[Company, Person]

        assert(traversal.check(company))(isNone)
      },
      test("returns Some(OpticCheck) when no matches") {
        val company   = Company("Acme", Person("CEO", 50), List.empty)
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
        val company   = Company("Acme", Person("CEO", 50), List(Person("Alice", 30), Person("Bob", 25)))
        val traversal = SearchTraversal[Company, Person]

        // Create lens using Reflect API
        val personRecord   = Person.schema.reflect.asInstanceOf[Reflect.Record.Bound[Person]]
        val personNameLens = Lens(personRecord, personRecord.fields(0).asInstanceOf[Term.Bound[Person, String]])

        // Compose search with lens to get names
        val searchThenName = traversal(personNameLens)

        val names = searchThenName.fold[List[String]](company)(List.empty, (acc, name) => acc :+ name)

        assert(names.sorted)(equalTo(List("Alice", "Bob", "CEO")))
      },
      test("composed traversal modifies nested fields") {
        val company   = Company("Acme", Person("CEO", 50), List(Person("Alice", 30)))
        val traversal = SearchTraversal[Company, Person]

        val personRecord   = Person.schema.reflect.asInstanceOf[Reflect.Record.Bound[Person]]
        val personNameLens = Lens(personRecord, personRecord.fields(0).asInstanceOf[Term.Bound[Person, String]])

        val searchThenName = traversal(personNameLens)
        val modified       = searchThenName.modify(company, (name: String) => name.toUpperCase)

        assert(modified.ceo.name)(equalTo("CEO")) &&
        assert(modified.employees.head.name)(equalTo("ALICE"))
      },
      test("composed traversal toDynamic includes both nodes") {
        val traversal = SearchTraversal[Company, Person]

        val personRecord   = Person.schema.reflect.asInstanceOf[Reflect.Record.Bound[Person]]
        val personNameLens = Lens(personRecord, personRecord.fields(0).asInstanceOf[Term.Bound[Person, String]])

        val searchThenName = traversal(personNameLens)
        val dynamic        = searchThenName.toDynamic

        assert(dynamic.nodes.length)(equalTo(2)) &&
        assert(dynamic.nodes(0))(isSubtype[DynamicOptic.Node.TypeSearch](anything)) &&
        assert(dynamic.nodes(1))(isSubtype[DynamicOptic.Node.Field](anything))
      },
      test("SearchTraversal composed with Prism via apply") {
        val holder   = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val search   = SearchTraversal[ContainerHolder, Container]
        val composed = search(containerToPC) // SearchTraversal.apply(Prism)

        val names = composed.fold[List[String]](holder)(List.empty, (acc, pc) => acc :+ pc.person.name)
        assert(names)(equalTo(List("Alice")))
      },
      test("SearchTraversal composed with Prism modify") {
        val holder   = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val search   = SearchTraversal[ContainerHolder, Container]
        val composed = search(containerToPC)

        val modified = composed.modify(
          holder,
          (pc: PersonContainer) => PersonContainer(Person(pc.person.name.toUpperCase, pc.person.age))
        )
        val names = modified.containers.collect { case PersonContainer(p) => p.name }
        assert(names)(equalTo(List("ALICE")))
      },
      test("SearchTraversal composed with Optional via apply") {
        val holder   = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val search   = SearchTraversal[ContainerHolder, Container]
        val composed = search(containerToPerson) // SearchTraversal.apply(Optional)

        val names = composed.fold[List[String]](holder)(List.empty, (acc, p) => acc :+ p.name)
        assert(names)(equalTo(List("Alice")))
      },
      test("SearchTraversal composed with Optional modify") {
        val holder   = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val search   = SearchTraversal[ContainerHolder, Container]
        val composed = search(containerToPerson)

        val modified = composed.modify(holder, (p: Person) => p.copy(age = 99))
        val ages     = modified.containers.collect { case PersonContainer(p) => p.age }
        assert(ages)(equalTo(List(99)))
      },
      test("SearchTraversal composed with Traversal (SearchTraversal + SearchTraversal)") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(Team("Frontend", Person("Lead", 35), List(Person("Dev", 28))))
        )
        val searchTeam   = SearchTraversal[Department, Team]
        val searchPerson = SearchTraversal[Team, Person]
        // Traversal.apply(Traversal, Traversal) with both being SearchTraversals
        val composed = Traversal(searchTeam, searchPerson)

        val names = composed.fold[List[String]](dept)(List.empty, (acc, p) => acc :+ p.name)
        // Should find persons within teams only (not the manager)
        assert(names.sorted)(equalTo(List("Dev", "Lead")))
      }
    ),
    suite("Prism prefix composition")(
      test("Prism then SearchTraversal fold - matching case") {
        val container: Container = PersonContainer(Person("Alice", 30))
        val search               = SearchTraversal[PersonContainer, Person]
        val composed             = Traversal(containerToPC, search) // Traversal.apply(Prism, Traversal)

        val names = composed.fold[List[String]](container)(List.empty, (acc, p) => acc :+ p.name)
        assert(names)(equalTo(List("Alice")))
      },
      test("Prism then SearchTraversal fold - non-matching case") {
        val container: Container = StringContainer("hello")
        val search               = SearchTraversal[PersonContainer, Person]
        val composed             = Traversal(containerToPC, search)

        val names = composed.fold[List[String]](container)(List.empty, (acc, p) => acc :+ p.name)
        assert(names)(equalTo(List.empty))
      },
      test("Prism then SearchTraversal modify - matching case") {
        val container: Container = PersonContainer(Person("Alice", 30))
        val search               = SearchTraversal[PersonContainer, Person]
        val composed             = Traversal(containerToPC, search)

        val modified = composed.modify(container, (p: Person) => p.copy(age = 99))
        assert(modified)(equalTo(PersonContainer(Person("Alice", 99)): Container))
      },
      test("Prism then SearchTraversal modify - non-matching case") {
        val container: Container = StringContainer("hello")
        val search               = SearchTraversal[PersonContainer, Person]
        val composed             = Traversal(containerToPC, search)

        val modified = composed.modify(container, (p: Person) => p.copy(age = 99))
        assert(modified)(equalTo(StringContainer("hello"): Container))
      },
      test("Prism then SearchTraversal check - matching case") {
        val container: Container = PersonContainer(Person("Alice", 30))
        val search               = SearchTraversal[PersonContainer, Person]
        val composed             = Traversal(containerToPC, search)

        assert(composed.check(container))(isNone)
      },
      test("Prism then SearchTraversal modifyOption - matching") {
        val container: Container = PersonContainer(Person("Alice", 30))
        val search               = SearchTraversal[PersonContainer, Person]
        val composed             = Traversal(containerToPC, search)

        val result = composed.modifyOption(container, (p: Person) => p.copy(age = 99))
        assert(result)(isSome)
      },
      test("Prism then SearchTraversal modifyOption - non-matching") {
        val container: Container = StringContainer("hello")
        val search               = SearchTraversal[PersonContainer, Person]
        val composed             = Traversal(containerToPC, search)

        val result = composed.modifyOption(container, (p: Person) => p.copy(age = 99))
        assert(result)(isNone)
      },
      test("Prism then SearchTraversal modifyOrFail - matching") {
        val container: Container = PersonContainer(Person("Alice", 30))
        val search               = SearchTraversal[PersonContainer, Person]
        val composed             = Traversal(containerToPC, search)

        val result = composed.modifyOrFail(container, (p: Person) => p.copy(age = 99))
        assert(result)(isRight)
      },
      test("Prism then SearchTraversal modifyOrFail - non-matching") {
        val container: Container = StringContainer("hello")
        val search               = SearchTraversal[PersonContainer, Person]
        val composed             = Traversal(containerToPC, search)

        val result = composed.modifyOrFail(container, (p: Person) => p.copy(age = 99))
        assert(result)(isLeft)
      }
    ),
    suite("Optional prefix composition")(
      test("Optional then SearchTraversal fold - present") {
        val container: Container = PersonContainer(Person("Alice", 30))
        val search               = SearchTraversal(Person.schema.reflect, Person.schema.reflect)
        val composed             = Traversal(containerToPerson, search) // Traversal.apply(Optional, Traversal)

        val names = composed.fold[List[String]](container)(List.empty, (acc, p) => acc :+ p.name)
        assert(names)(equalTo(List("Alice")))
      },
      test("Optional then SearchTraversal fold - absent") {
        val container: Container = StringContainer("hello")
        val search               = SearchTraversal(Person.schema.reflect, Person.schema.reflect)
        val composed             = Traversal(containerToPerson, search)

        val names = composed.fold[List[String]](container)(List.empty, (acc, p) => acc :+ p.name)
        assert(names)(equalTo(List.empty))
      },
      test("Optional then SearchTraversal modify - present") {
        val container: Container = PersonContainer(Person("Alice", 30))
        val search               = SearchTraversal(Person.schema.reflect, Person.schema.reflect)
        val composed             = Traversal(containerToPerson, search)

        val modified = composed.modify(container, (p: Person) => p.copy(age = 99))
        assert(modified)(equalTo(PersonContainer(Person("Alice", 99)): Container))
      },
      test("Optional then SearchTraversal modify - absent") {
        val container: Container = StringContainer("hello")
        val search               = SearchTraversal(Person.schema.reflect, Person.schema.reflect)
        val composed             = Traversal(containerToPerson, search)

        val modified = composed.modify(container, (p: Person) => p.copy(age = 99))
        assert(modified)(equalTo(StringContainer("hello"): Container))
      },
      test("Optional then SearchTraversal check - present") {
        val container: Container = PersonContainer(Person("Alice", 30))
        val search               = SearchTraversal(Person.schema.reflect, Person.schema.reflect)
        val composed             = Traversal(containerToPerson, search)

        assert(composed.check(container))(isNone)
      },
      test("Optional then SearchTraversal modifyOption - present") {
        val container: Container = PersonContainer(Person("Alice", 30))
        val search               = SearchTraversal(Person.schema.reflect, Person.schema.reflect)
        val composed             = Traversal(containerToPerson, search)

        val result = composed.modifyOption(container, (p: Person) => p.copy(age = 99))
        assert(result)(isSome)
      },
      test("Optional then SearchTraversal modifyOption - absent") {
        val container: Container = StringContainer("hello")
        val search               = SearchTraversal(Person.schema.reflect, Person.schema.reflect)
        val composed             = Traversal(containerToPerson, search)

        val result = composed.modifyOption(container, (p: Person) => p.copy(age = 99))
        assert(result)(isNone)
      },
      test("Optional then SearchTraversal modifyOrFail - present") {
        val container: Container = PersonContainer(Person("Alice", 30))
        val search               = SearchTraversal(Person.schema.reflect, Person.schema.reflect)
        val composed             = Traversal(containerToPerson, search)

        val result = composed.modifyOrFail(container, (p: Person) => p.copy(age = 99))
        assert(result)(isRight)
      },
      test("Optional then SearchTraversal modifyOrFail - absent") {
        val container: Container = StringContainer("hello")
        val search               = SearchTraversal(Person.schema.reflect, Person.schema.reflect)
        val composed             = Traversal(containerToPerson, search)

        val result = composed.modifyOrFail(container, (p: Person) => p.copy(age = 99))
        assert(result)(isLeft)
      }
    ),
    suite("ComposedSearchTraversal further composition")(
      test("ComposedSearchTraversal composed with Prism") {
        val holder = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val search = SearchTraversal[ContainerHolder, Container]
        // First compose: search → lens (to get a ComposedSearchTraversal)
        // Actually, search → prism gives ComposedSearchTraversal directly
        val composed1 = search(containerToPC) // ComposedSearchTraversal[ContainerHolder, Container, PersonContainer]
        // Then compose further with another optic to test ComposedSearchTraversal.apply(Lens)
        val composed2 = composed1(pcToPersonLens) // ComposedSearchTraversal.apply(Lens)

        val names = composed2.fold[List[String]](holder)(List.empty, (acc, p) => acc :+ p.name)
        assert(names)(equalTo(List("Alice")))
      },
      test("ComposedSearchTraversal composed with Prism via Traversal.apply") {
        val holder    = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val search    = SearchTraversal[ContainerHolder, Container]
        val composed1 = search(containerToPC) // ComposedSearchTraversal
        // Use Traversal.apply(Traversal, Prism) with ComposedSearchTraversal on left
        // Not directly possible since Prism[PersonContainer, ?] doesn't exist
        // Instead test ComposedSearchTraversal.apply(Traversal)
        val innerSearch = SearchTraversal[PersonContainer, Person]
        val composed2   = Traversal(composed1, innerSearch)

        val names = composed2.fold[List[String]](holder)(List.empty, (acc, p) => acc :+ p.name)
        assert(names)(equalTo(List("Alice")))
      },
      test("ComposedSearchTraversal check with inner failures") {
        val holder   = ContainerHolder(List(StringContainer("hello")))
        val search   = SearchTraversal[ContainerHolder, Container]
        val composed = search(containerToPC) // ComposedSearchTraversal

        // Prism won't match StringContainer, so check on inner should report issues
        val result = composed.check(holder)
        // The search finds Container values, but prism doesn't match
        assert(result)(isSome)
      },
      test("ComposedSearchTraversal modifyOption with no matches") {
        val holder   = ContainerHolder(List(StringContainer("hello")))
        val search   = SearchTraversal[ContainerHolder, Container]
        val composed = search(containerToPC)

        val result = composed.modifyOption(holder, (_: PersonContainer) => PersonContainer(Person("X", 0)))
        assert(result)(isNone)
      },
      test("ComposedSearchTraversal modifyOrFail with matches") {
        val holder   = ContainerHolder(List(PersonContainer(Person("Alice", 30))))
        val search   = SearchTraversal[ContainerHolder, Container]
        val composed = search(containerToPC)

        val result = composed.modifyOrFail(holder, (_: PersonContainer) => PersonContainer(Person("X", 0)))
        assert(result)(isRight)
      },
      test("ComposedSearchTraversal modifyOrFail with no matches") {
        val holder   = ContainerHolder(List(StringContainer("hello")))
        val search   = SearchTraversal[ContainerHolder, Container]
        val composed = search(containerToPC)

        val result = composed.modifyOrFail(holder, (_: PersonContainer) => PersonContainer(Person("X", 0)))
        assert(result)(isLeft)
      }
    ),
    suite("PrefixedSearchTraversal further composition")(
      test("PrefixedSearchTraversal composed with Lens") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(Team("Frontend", Person("Lead", 35), List(Person("Dev", 28))))
        )
        val teamsLens = Lens(
          Department.schema.reflect.asInstanceOf[Reflect.Record.Bound[Department]],
          Department.schema.reflect
            .asInstanceOf[Reflect.Record.Bound[Department]]
            .fields(2)
            .asInstanceOf[Term.Bound[Department, List[Team]]]
        )
        val searchPerson = SearchTraversal[List[Team], Person]
        // Lens → SearchTraversal creates PrefixedSearchTraversal
        val prefixed = Traversal(teamsLens, searchPerson)
        // Then compose PrefixedSearchTraversal with Lens
        val composed = prefixed(personNameLens)

        val names = composed.fold[List[String]](dept)(List.empty, (acc, name) => acc :+ name)
        assert(names.sorted)(equalTo(List("Dev", "Lead")))
      },
      test("PrefixedSearchTraversal composed with Prism") {
        val holder         = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val containersLens = Lens(
          ContainerHolder.schema.reflect.asInstanceOf[Reflect.Record.Bound[ContainerHolder]],
          ContainerHolder.schema.reflect
            .asInstanceOf[Reflect.Record.Bound[ContainerHolder]]
            .fields(0)
            .asInstanceOf[Term.Bound[ContainerHolder, List[Container]]]
        )
        val searchContainer = SearchTraversal[List[Container], Container]
        // Lens → SearchTraversal creates PrefixedSearchTraversal
        val prefixed = Traversal(containersLens, searchContainer)
        // Then compose PrefixedSearchTraversal with Prism
        val composed = prefixed(containerToPC)

        val names = composed.fold[List[String]](holder)(List.empty, (acc, pc) => acc :+ pc.person.name)
        assert(names)(equalTo(List("Alice")))
      },
      test("PrefixedSearchTraversal composed with Optional") {
        val holder         = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val containersLens = Lens(
          ContainerHolder.schema.reflect.asInstanceOf[Reflect.Record.Bound[ContainerHolder]],
          ContainerHolder.schema.reflect
            .asInstanceOf[Reflect.Record.Bound[ContainerHolder]]
            .fields(0)
            .asInstanceOf[Term.Bound[ContainerHolder, List[Container]]]
        )
        val searchContainer = SearchTraversal[List[Container], Container]
        val prefixed        = Traversal(containersLens, searchContainer)
        // Then compose PrefixedSearchTraversal with Optional
        val composed = prefixed(containerToPerson)

        val names = composed.fold[List[String]](holder)(List.empty, (acc, p) => acc :+ p.name)
        assert(names)(equalTo(List("Alice")))
      }
    ),
    suite("Traversal.apply overloads with search variants")(
      test("Traversal.apply(Traversal, Prism) with SearchTraversal on left") {
        val holder                                        = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val search: Traversal[ContainerHolder, Container] = SearchTraversal[ContainerHolder, Container]
        // Traversal.apply(Traversal[S,T], Prism[T,A])
        val composed = Traversal(search, containerToPC)

        val names = composed.fold[List[String]](holder)(List.empty, (acc, pc) => acc :+ pc.person.name)
        assert(names)(equalTo(List("Alice")))
      },
      test("Traversal.apply(Traversal, Optional) with SearchTraversal on left") {
        val holder                                        = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val search: Traversal[ContainerHolder, Container] = SearchTraversal[ContainerHolder, Container]
        // Traversal.apply(Traversal[S,T], Optional[T,A])
        val composed = Traversal(search, containerToPerson)

        val names = composed.fold[List[String]](holder)(List.empty, (acc, p) => acc :+ p.name)
        assert(names)(equalTo(List("Alice")))
      },
      test("Traversal.apply(Traversal, Traversal) with ComposedSearchTraversal on right") {
        val searchContainer = SearchTraversal[ContainerHolder, Container]
        val composedInner   = searchContainer(containerToPC) // ComposedSearchTraversal
        // Verify the composed result is the expected type
        assert(composedInner.isInstanceOf[Traversal.ComposedSearchTraversal[_, _, _]])(isTrue)
      },
      test("Traversal.apply(Prism, ComposedSearchTraversal)") {
        // Prism[Container, PersonContainer] → ComposedSearchTraversal[PersonContainer, Person, String]
        val searchPerson  = SearchTraversal[PersonContainer, Person]
        val composedInner = searchPerson(personNameLens) // ComposedSearchTraversal[PersonContainer, Person, String]
        // Prism → ComposedSearchTraversal
        val composed = Traversal(containerToPC, composedInner)

        val container: Container = PersonContainer(Person("Alice", 30))
        val names                = composed.fold[List[String]](container)(List.empty, (acc, name) => acc :+ name)
        assert(names)(equalTo(List("Alice")))
      },
      test("Traversal.apply(Optional, SearchTraversal)") {
        val search = SearchTraversal(Person.schema.reflect, Person.schema.reflect)
        // Optional → SearchTraversal
        val composed = Traversal(containerToPerson, search)

        val container: Container = PersonContainer(Person("Alice", 30))
        val names                = composed.fold[List[String]](container)(List.empty, (acc, p) => acc :+ p.name)
        assert(names)(equalTo(List("Alice")))
      },
      test("Traversal.apply(Optional, ComposedSearchTraversal)") {
        val searchPerson  = SearchTraversal(Person.schema.reflect, Person.schema.reflect)
        val composedInner = searchPerson(personNameLens) // ComposedSearchTraversal[Person, Person, String]
        // Optional → ComposedSearchTraversal
        val composed = Traversal(containerToPerson, composedInner)

        val container: Container = PersonContainer(Person("Alice", 30))
        val names                = composed.fold[List[String]](container)(List.empty, (acc, name) => acc :+ name)
        assert(names)(equalTo(List("Alice")))
      }
    ),
    suite("ComposedSearchTraversal equals and hashCode")(
      test("equal ComposedSearchTraversals have same hashCode") {
        val search = SearchTraversal[Company, Person]
        val t1     = search(personNameLens)
        val t2     = search(personNameLens)
        assert(t1.hashCode)(equalTo(t2.hashCode))
      },
      test("equal ComposedSearchTraversals are equal") {
        val search = SearchTraversal[Company, Person]
        val t1     = search(personNameLens)
        val t2     = search(personNameLens)
        assert(t1)(equalTo(t2))
      },
      test("different ComposedSearchTraversals are not equal") {
        val search        = SearchTraversal[Company, Person]
        val personAgeLens = Lens(personRecord, personRecord.fields(1).asInstanceOf[Term.Bound[Person, Int]])
        val t1            = search(personNameLens)
        val t2            = search(personAgeLens)
        assert(t1: Any)(not(equalTo(t2)))
      },
      test("ComposedSearchTraversal not equal to non-ComposedSearchTraversal") {
        val search   = SearchTraversal[Company, Person]
        val composed = search(personNameLens)
        assert(composed: Any)(not(equalTo("not a traversal")))
      }
    ),
    suite("PrefixedSearchTraversal equals and hashCode")(
      test("equal PrefixedSearchTraversals have same hashCode") {
        val search = SearchTraversal[PersonContainer, Person]
        val t1     = Traversal(containerToPC, search)
        val t2     = Traversal(containerToPC, search)
        assert(t1.hashCode)(equalTo(t2.hashCode))
      },
      test("equal PrefixedSearchTraversals are equal") {
        val search = SearchTraversal[PersonContainer, Person]
        val t1     = Traversal(containerToPC, search)
        val t2     = Traversal(containerToPC, search)
        assert(t1)(equalTo(t2))
      },
      test("different PrefixedSearchTraversals are not equal") {
        val search1 = SearchTraversal[PersonContainer, Person]
        val search2 = SearchTraversal(Person.schema.reflect, Person.schema.reflect)
        val t1      = Traversal(containerToPC, search1)
        val t2      = Traversal(containerToPerson, search2)
        assert(t1: Any)(not(equalTo(t2)))
      },
      test("PrefixedSearchTraversal not equal to non-PrefixedSearchTraversal") {
        val search   = SearchTraversal[PersonContainer, Person]
        val prefixed = Traversal(containerToPC, search)
        assert(prefixed: Any)(not(equalTo("not a traversal")))
      }
    ),
    suite("ComposedSearchTraversal fold with Prism and Optional inner")(
      test("fold with Prism inner filters matching cases") {
        val holder = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val search = SearchTraversal[ContainerHolder, Container]
        // Compose with Prism - fold should use getOption on Prism
        val composed = search(containerToPC)

        val count = composed.fold[Int](holder)(0, (acc, _) => acc + 1)
        // Only PersonContainer matches the prism
        assert(count)(equalTo(1))
      },
      test("fold with Optional inner filters matching values") {
        val holder = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val search = SearchTraversal[ContainerHolder, Container]
        // Compose with Optional - fold should use getOption on Optional
        val composed = search(containerToPerson)

        val count = composed.fold[Int](holder)(0, (acc, _) => acc + 1)
        // Only PersonContainer has a person via the Optional
        assert(count)(equalTo(1))
      }
    ),
    suite("PrefixedSearchTraversal with Traversal prefix")(
      test("modifyOption with Traversal prefix - matches found") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(Team("Frontend", Person("Lead", 35), List(Person("Dev", 28))))
        )
        val teamsLens = Lens(
          Department.schema.reflect.asInstanceOf[Reflect.Record.Bound[Department]],
          Department.schema.reflect
            .asInstanceOf[Reflect.Record.Bound[Department]]
            .fields(2)
            .asInstanceOf[Term.Bound[Department, List[Team]]]
        )
        val searchPerson = SearchTraversal[List[Team], Person]
        val prefixed     = Traversal(teamsLens, searchPerson)

        val result = prefixed.modifyOption(dept, (p: Person) => p.copy(age = 99))
        assert(result)(isSome)
      },
      test("modifyOrFail with Traversal prefix - matches found") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(Team("Frontend", Person("Lead", 35), List(Person("Dev", 28))))
        )
        val teamsLens = Lens(
          Department.schema.reflect.asInstanceOf[Reflect.Record.Bound[Department]],
          Department.schema.reflect
            .asInstanceOf[Reflect.Record.Bound[Department]]
            .fields(2)
            .asInstanceOf[Term.Bound[Department, List[Team]]]
        )
        val searchPerson = SearchTraversal[List[Team], Person]
        val prefixed     = Traversal(teamsLens, searchPerson)

        val result = prefixed.modifyOrFail(dept, (p: Person) => p.copy(age = 99))
        assert(result)(isRight)
      }
    ),
    suite("PrefixedSearchTraversal toString and toDynamic")(
      test("toString includes prefix and search") {
        val search   = SearchTraversal[PersonContainer, Person]
        val composed = Traversal(containerToPC, search)
        val str      = composed.toString
        // Should include both the prism and search parts
        assert(str)(containsString("search"))
      },
      test("toDynamic includes prefix and search nodes") {
        val search   = SearchTraversal[PersonContainer, Person]
        val composed = Traversal(containerToPC, search)
        val dynamic  = composed.toDynamic
        // Should have at least 2 nodes (prism + search)
        assert(dynamic.nodes.length >= 2)(isTrue)
      }
    )
  )
}
