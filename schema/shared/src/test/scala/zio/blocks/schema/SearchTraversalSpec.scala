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
  lazy val teamRecord: Reflect.Record.Bound[Team] =
    Team.schema.reflect.asInstanceOf[Reflect.Record.Bound[Team]]
  lazy val teamNameLens: Lens[Team, String] =
    Lens(teamRecord, teamRecord.fields(0).asInstanceOf[Term.Bound[Team, String]])

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

        assert(result)(isSome(hasField("ceo", (c: Company) => c.ceo.age, equalTo(51)))) &&
        assert(result.get.employees.map(_.age))(equalTo(List(31)))
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
        val company   = Company("Acme", Person("CEO", 50), List(Person("Alice", 30)))
        val traversal = SearchTraversal[Company, Person]

        val result = traversal.modifyOrFail(company, p => p.copy(age = p.age + 1))

        assert(result)(isRight(hasField("ceo", (c: Company) => c.ceo.age, equalTo(51)))) &&
        assert(result.toOption.get.employees.map(_.age))(equalTo(List(31)))
      },
      test("returns Left with OpticCheck when no matches") {
        val company   = Company("Acme", Person("CEO", 50), List.empty)
        val traversal = SearchTraversal[Company, Team]

        val result = traversal.modifyOrFail(company, t => t.copy(name = "X"))

        assert(result)(
          isLeft(hasField("errors", (c: OpticCheck) => c.errors.head, isSubtype[OpticCheck.EmptySequence](anything)))
        )
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

        assert(dynamic.nodes.head)(
          isSubtype[DynamicOptic.Node.TypeSearch](
            hasField("typeId", (ts: DynamicOptic.Node.TypeSearch) => ts.typeId.name, equalTo("Person"))
          )
        )
      }
    ),
    suite("toString")(
      test("produces readable output") {
        val traversal = SearchTraversal[Company, Person]
        assert(traversal.toString)(equalTo("Traversal(_.searchFor[Person])"))
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
        assert(result)(isSome(equalTo(PersonContainer(Person("Alice", 99)): Container)))
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
        assert(result)(isRight(equalTo(PersonContainer(Person("Alice", 99)): Container)))
      },
      test("Prism then SearchTraversal modifyOrFail - non-matching") {
        val container: Container = StringContainer("hello")
        val search               = SearchTraversal[PersonContainer, Person]
        val composed             = Traversal(containerToPC, search)

        val result = composed.modifyOrFail(container, (p: Person) => p.copy(age = 99))
        assert(result)(
          isLeft(hasField("errors", (c: OpticCheck) => c.errors.head, isSubtype[OpticCheck.EmptySequence](anything)))
        )
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
        assert(result)(isSome(equalTo(PersonContainer(Person("Alice", 99)): Container)))
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
        assert(result)(isRight(equalTo(PersonContainer(Person("Alice", 99)): Container)))
      },
      test("Optional then SearchTraversal modifyOrFail - absent") {
        val container: Container = StringContainer("hello")
        val search               = SearchTraversal(Person.schema.reflect, Person.schema.reflect)
        val composed             = Traversal(containerToPerson, search)

        val result = composed.modifyOrFail(container, (p: Person) => p.copy(age = 99))
        assert(result)(
          isLeft(hasField("errors", (c: OpticCheck) => c.errors.head, isSubtype[OpticCheck.EmptySequence](anything)))
        )
      }
    ),
    suite("ComposedSearchTraversal further composition")(
      test("ComposedSearchTraversal composed with Prism") {
        val holder    = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val search    = SearchTraversal[ContainerHolder, Container]
        val composed1 = search(containerToPC)     // ComposedSearchTraversal[ContainerHolder, Container, PersonContainer]
        val composed2 = composed1(pcToPersonLens) // ComposedSearchTraversal.apply(Lens)

        val names = composed2.fold[List[String]](holder)(List.empty, (acc, p) => acc :+ p.name)
        assert(names)(equalTo(List("Alice")))
      },
      test("ComposedSearchTraversal composed with Prism via Traversal.apply") {
        val holder      = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val search      = SearchTraversal[ContainerHolder, Container]
        val composed1   = search(containerToPC) // ComposedSearchTraversal
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
        // The search finds Container values, but prism doesn't match — errors should contain inner check failure
        assert(result)(isSome(hasField("errors", (c: OpticCheck) => c.errors.nonEmpty, isTrue)))
      },
      test("ComposedSearchTraversal modifyOption with no matches") {
        val holder   = ContainerHolder(List(StringContainer("hello")))
        val search   = SearchTraversal[ContainerHolder, Container]
        val composed = search(containerToPC)

        val result = composed.modifyOption(holder, (_: PersonContainer) => PersonContainer(Person("X", 0)))
        // StringContainer doesn't match prism, so anyModified stays false → None
        assert(result)(isNone)
      },
      test("ComposedSearchTraversal modifyOrFail with matches") {
        val holder   = ContainerHolder(List(PersonContainer(Person("Alice", 30))))
        val search   = SearchTraversal[ContainerHolder, Container]
        val composed = search(containerToPC)

        val result = composed.modifyOrFail(holder, (_: PersonContainer) => PersonContainer(Person("X", 0)))
        assert(result)(
          isRight(
            hasField(
              "containers",
              (h: ContainerHolder) => h.containers,
              equalTo(List[Container](PersonContainer(Person("X", 0))))
            )
          )
        )
      },
      test("ComposedSearchTraversal modifyOrFail with no matches") {
        val holder   = ContainerHolder(List(StringContainer("hello")))
        val search   = SearchTraversal[ContainerHolder, Container]
        val composed = search(containerToPC)

        val result = composed.modifyOrFail(holder, (_: PersonContainer) => PersonContainer(Person("X", 0)))
        assert(result)(
          isLeft(hasField("errors", (c: OpticCheck) => c.errors.head, isSubtype[OpticCheck.UnexpectedCase](anything)))
        )
      },
      test("check returns Some when outer search has no matches") {
        val company  = Company("Acme", Person("CEO", 50), List.empty)
        val search   = SearchTraversal[Company, Team]
        val composed = search(teamNameLens) // ComposedSearchTraversal[Company, Team, String]

        val result = composed.check(company)
        // search.check(company) returns Some because no Team found in Company
        assert(result)(isSome)
      },
      test("modifyOption returns Some when inner optic matches") {
        val holder   = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val search   = SearchTraversal[ContainerHolder, Container]
        val composed = search(containerToPC)

        val result = composed.modifyOption(
          holder,
          (pc: PersonContainer) => PersonContainer(Person(pc.person.name.toUpperCase, pc.person.age))
        )

        assert(result)(isSome) &&
        assert(result.get.containers.collect { case PersonContainer(p) => p.name })(equalTo(List("ALICE"))) &&
        assert(result.get.containers.collect { case s: StringContainer => s.value })(equalTo(List("hello")))
      },
      test("modifyOrFail returns Left when inner optic fails and short-circuits remaining") {
        // Two containers: StringContainer fails the prism, PersonContainer is skipped via early termination
        val holder   = ContainerHolder(List(StringContainer("hello"), PersonContainer(Person("Alice", 30))))
        val search   = SearchTraversal[ContainerHolder, Container]
        val composed = search(containerToPC)

        val result = composed.modifyOrFail(
          holder,
          (_: PersonContainer) => PersonContainer(Person("X", 0))
        )

        // StringContainer found first → containerToPC.modifyOrFail fails → firstError set
        // PersonContainer found second → firstError.isLeft guard triggers early return
        assert(result)(isLeft(hasField("errors", (c: OpticCheck) => c.errors.nonEmpty, isTrue)))
      },
      test("ComposedSearchTraversal apply(Prism) extends inner optic chain") {
        val holder          = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val searchContainer = SearchTraversal[ContainerHolder, Container]
        val identitySearch  = SearchTraversal[Container, Container]
        val composed1       = searchContainer(identitySearch) // ComposedSearchTraversal with focus=Container
        val composed2       = composed1(containerToPC)        // ComposedSearchTraversal.apply(Prism)

        val names = composed2.fold[List[String]](holder)(List.empty, (acc, pc) => acc :+ pc.person.name)
        assert(names)(equalTo(List("Alice")))
      },
      test("ComposedSearchTraversal apply(Optional) extends inner optic chain") {
        val holder          = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val searchContainer = SearchTraversal[ContainerHolder, Container]
        val identitySearch  = SearchTraversal[Container, Container]
        val composed1       = searchContainer(identitySearch) // ComposedSearchTraversal with focus=Container
        val composed3       = composed1(containerToPerson)    // ComposedSearchTraversal.apply(Optional)

        val names = composed3.fold[List[String]](holder)(List.empty, (acc, p) => acc :+ p.name)
        assert(names)(equalTo(List("Alice")))
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
      test("Traversal.apply(Traversal, Traversal) with ComposedSearchTraversal on right") {
        val holder          = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val searchContainer = SearchTraversal[ContainerHolder, Container]
        val composedInner   = searchContainer(containerToPC) // ComposedSearchTraversal
        // Verify the composed result is the expected type and works correctly
        assert(composedInner.isInstanceOf[Traversal.ComposedSearchTraversal[_, _, _]])(isTrue) &&
        assert(composedInner.fold[List[String]](holder)(List.empty, (acc, pc) => acc :+ pc.person.name))(
          equalTo(List("Alice"))
        )
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
        assert(result)(isSome(hasField("teams", (d: Department) => d.teams.head.lead.age, equalTo(99)))) &&
        assert(result.get.teams.head.members.head.age)(equalTo(99)) &&
        assert(result.get.manager.age)(equalTo(45)) // Director not touched (not in teams)
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
        assert(result)(isRight(hasField("teams", (d: Department) => d.teams.head.lead.age, equalTo(99)))) &&
        assert(result.toOption.get.teams.head.members.head.age)(equalTo(99)) &&
        assert(result.toOption.get.manager.age)(equalTo(45)) // Director not touched
      }
    ),
    suite("PrefixedSearchTraversal with non-Lens Traversal prefix")(
      test("fold with Traversal prefix collects values from all traversed items") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(
            Team("Frontend", Person("Lead", 35), List(Person("Dev1", 28))),
            Team("Backend", Person("BE Lead", 38), List(Person("Dev2", 30)))
          )
        )
        // SearchTraversal extends Traversal but NOT Lens/Prism/Optional
        val searchTeam: Traversal[Department, Team] = SearchTraversal[Department, Team]
        val searchPerson                            = SearchTraversal[Team, Person]
        val prefixed                                =
          Traversal.PrefixedSearchTraversal[Department, Team, Person](searchTeam, searchPerson)

        val names = prefixed.fold[List[String]](dept)(List.empty, (acc, p) => acc :+ p.name)
        // Director is NOT inside a Team, so not found through this path
        assert(names.sorted)(equalTo(List("BE Lead", "Dev1", "Dev2", "Lead")))
      },
      test("modify with Traversal prefix modifies values in all traversed items") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(
            Team("Frontend", Person("Lead", 35), List(Person("Dev1", 28))),
            Team("Backend", Person("BE Lead", 38), List(Person("Dev2", 30)))
          )
        )
        val searchTeam: Traversal[Department, Team] = SearchTraversal[Department, Team]
        val searchPerson                            = SearchTraversal[Team, Person]
        val prefixed                                =
          Traversal.PrefixedSearchTraversal[Department, Team, Person](searchTeam, searchPerson)

        val modified = prefixed.modify(dept, (p: Person) => p.copy(age = p.age + 100))

        // Director is NOT in any Team, so unchanged
        assert(modified.manager.age)(equalTo(45)) &&
        assert(modified.teams(0).lead.age)(equalTo(135)) &&
        assert(modified.teams(0).members(0).age)(equalTo(128)) &&
        assert(modified.teams(1).lead.age)(equalTo(138)) &&
        assert(modified.teams(1).members(0).age)(equalTo(130))
      },
      test("modifyOption with Traversal prefix returns Some when matches found") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(Team("Frontend", Person("Lead", 35), List(Person("Dev", 28))))
        )
        val searchTeam: Traversal[Department, Team] = SearchTraversal[Department, Team]
        val searchPerson                            = SearchTraversal[Team, Person]
        val prefixed                                =
          Traversal.PrefixedSearchTraversal[Department, Team, Person](searchTeam, searchPerson)

        val result = prefixed.modifyOption(dept, (p: Person) => p.copy(age = 99))

        assert(result)(isSome) &&
        assert(result.get.teams.head.lead.age)(equalTo(99)) &&
        assert(result.get.teams.head.members.head.age)(equalTo(99))
      },
      test("modifyOption with Traversal prefix returns None when no inner matches") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(Team("Frontend", Person("Lead", 35), List(Person("Dev", 28))))
        )
        val searchTeam: Traversal[Department, Team] = SearchTraversal[Department, Team]
        val searchTreeNode                          = SearchTraversal[Team, TreeNode]
        val prefixed                                =
          Traversal.PrefixedSearchTraversal[Department, Team, TreeNode](searchTeam, searchTreeNode)

        val result = prefixed.modifyOption(dept, (t: TreeNode) => t.copy(value = "X"))

        assert(result)(isNone)
      },
      test("modifyOrFail with Traversal prefix returns Right on success") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(Team("Frontend", Person("Lead", 35), List(Person("Dev", 28))))
        )
        val searchTeam: Traversal[Department, Team] = SearchTraversal[Department, Team]
        val searchPerson                            = SearchTraversal[Team, Person]
        val prefixed                                =
          Traversal.PrefixedSearchTraversal[Department, Team, Person](searchTeam, searchPerson)

        val result = prefixed.modifyOrFail(dept, (p: Person) => p.copy(age = 99))

        assert(result.isRight)(isTrue) &&
        assert(result.toOption.get.teams.head.lead.age)(equalTo(99)) &&
        assert(result.toOption.get.teams.head.members.head.age)(equalTo(99))
      },
      test("modifyOrFail with Traversal prefix returns Left when inner fails") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(
            Team("Team1", Person("Lead1", 35), List()),
            Team("Team2", Person("Lead2", 38), List())
          )
        )
        val searchTeam: Traversal[Department, Team] = SearchTraversal[Department, Team]
        val searchTreeNode                          = SearchTraversal[Team, TreeNode]
        val prefixed                                =
          Traversal.PrefixedSearchTraversal[Department, Team, TreeNode](searchTeam, searchTreeNode)

        val result = prefixed.modifyOrFail(dept, (t: TreeNode) => t.copy(value = "X"))

        assert(result)(isLeft)
      },
      test("modifyOrFail with Traversal prefix returns Left(EmptySequence) when prefix finds no items") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(Team("Frontend", Person("Lead", 35), List(Person("Dev", 28))))
        )
        val searchTreeNode: Traversal[Department, TreeNode] = SearchTraversal[Department, TreeNode]
        val searchPerson                                    = SearchTraversal[TreeNode, Person]
        val prefixed                                        =
          Traversal.PrefixedSearchTraversal[Department, TreeNode, Person](searchTreeNode, searchPerson)

        val result = prefixed.modifyOrFail(dept, (p: Person) => p.copy(age = 99))

        assert(result)(isLeft) &&
        assert(result.swap.toOption.get.errors.head)(isSubtype[OpticCheck.EmptySequence](anything))
      },
      test("check with Traversal prefix returns None when inner checks pass") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(Team("Frontend", Person("Lead", 35), List(Person("Dev", 28))))
        )
        val searchTeam: Traversal[Department, Team] = SearchTraversal[Department, Team]
        val searchPerson                            = SearchTraversal[Team, Person]
        val prefixed                                =
          Traversal.PrefixedSearchTraversal[Department, Team, Person](searchTeam, searchPerson)

        assert(prefixed.check(dept))(isNone)
      },
      test("check with Traversal prefix returns Some when inner checks fail") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(Team("Frontend", Person("Lead", 35), List(Person("Dev", 28))))
        )
        val searchTeam: Traversal[Department, Team] = SearchTraversal[Department, Team]
        val searchTreeNode                          = SearchTraversal[Team, TreeNode]
        val prefixed                                =
          Traversal.PrefixedSearchTraversal[Department, Team, TreeNode](searchTeam, searchTreeNode)

        val result = prefixed.check(dept)
        assert(result)(isSome)
      },
      test("apply(Traversal) further composes PrefixedSearchTraversal with Traversal prefix") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(
            Team("Frontend", Person("Lead", 35), List(Person("Dev1", 28))),
            Team("Backend", Person("BE Lead", 38), List(Person("Dev2", 30)))
          )
        )
        val searchTeam: Traversal[Department, Team] = SearchTraversal[Department, Team]
        val searchPerson                            = SearchTraversal[Team, Person]
        val prefixed                                =
          Traversal.PrefixedSearchTraversal[Department, Team, Person](searchTeam, searchPerson)
        // apply(Traversal) — compose with another SearchTraversal (a Traversal, not Lens/Prism/Optional)
        val searchPersonIdentity = SearchTraversal[Person, Person]
        val composed             = prefixed.apply(searchPersonIdentity)

        val names = composed.fold[List[String]](dept)(List.empty, (acc, p) => acc :+ p.name)
        assert(names.sorted)(equalTo(List("BE Lead", "Dev1", "Dev2", "Lead")))
      }
    ),
    suite("error recovery paths")(
      test("modify returns original when fromDynamicValue fails") {
        // Create a validated source schema that rejects negative ages
        val validatedSchema = Company.schema.transform[Company](
          c => {
            if (c.ceo.age < 0 || c.employees.exists(_.age < 0))
              throw new Exception("Ages must be non-negative")
            c
          },
          identity
        )
        val traversal = SearchTraversal(validatedSchema.reflect, Person.schema.reflect)
        val company   = Company("Acme", Person("CEO", 50), List(Person("Alice", 30)))

        // Modify ages to -1 — searchModify succeeds but sourceReflect.fromDynamicValue fails
        val result = traversal.modify(company, p => p.copy(age = -1))

        // modify returns the original value when fromDynamicValue returns Left
        assert(result)(equalTo(company))
      },
      test("modifyOption returns None when anyModified but fromDynamicValue fails") {
        val validatedSchema = Company.schema.transform[Company](
          c => {
            if (c.ceo.age < 0 || c.employees.exists(_.age < 0))
              throw new Exception("Ages must be non-negative")
            c
          },
          identity
        )
        val traversal = SearchTraversal(validatedSchema.reflect, Person.schema.reflect)
        val company   = Company("Acme", Person("CEO", 50), List(Person("Alice", 30)))

        val result = traversal.modifyOption(company, p => p.copy(age = -1))

        // modifyOption returns None when anyModified=true but fromDynamicValue returns Left
        assert(result)(isNone)
      },
      test("modifyOrFail returns Left with WrappingError when anyModified but fromDynamicValue fails") {
        val validatedSchema = Company.schema.transform[Company](
          c => {
            if (c.ceo.age < 0 || c.employees.exists(_.age < 0))
              throw new Exception("Ages must be non-negative")
            c
          },
          identity
        )
        val traversal = SearchTraversal(validatedSchema.reflect, Person.schema.reflect)
        val company   = Company("Acme", Person("CEO", 50), List(Person("Alice", 30)))

        val result = traversal.modifyOrFail(company, p => p.copy(age = -1))

        // modifyOrFail returns Left with WrappingError wrapping the SchemaError
        assert(result)(isLeft) &&
        assert(result.swap.toOption.get.errors.head)(isSubtype[OpticCheck.WrappingError](anything))
      }
    ),
    suite("PrefixedSearchTraversal toString and toDynamic")(
      test("toString includes prefix and search") {
        val search   = SearchTraversal[PersonContainer, Person]
        val composed = Traversal(containerToPC, search)
        val str      = composed.toString
        // Should include both the prism and search parts
        assert(str)(containsString("search")) &&
        assert(str)(containsString("PersonContainer"))
      },
      test("toDynamic includes prefix and search nodes") {
        val search   = SearchTraversal[PersonContainer, Person]
        val composed = Traversal(containerToPC, search)
        val dynamic  = composed.toDynamic
        // Should have at least 2 nodes (prism + search)
        assert(dynamic.nodes.length >= 2)(isTrue) &&
        assert(dynamic.nodes.exists(_.isInstanceOf[DynamicOptic.Node.TypeSearch]))(isTrue) &&
        assert(dynamic.nodes.exists(_.isInstanceOf[DynamicOptic.Node.Case]))(isTrue)
      }
    ),
    suite("Traversal.apply companion routing - untested branches")(
      // Traversal.apply(Traversal, Traversal): case (search: SearchTraversal, _) with non-SearchTraversal right (line 1202)
      test("Traversal(SearchTraversal, non-SearchTraversal Traversal) creates ComposedSearchTraversal") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(Team("Frontend", Person("Lead", 35), List(Person("Dev", 28))))
        )
        val searchTeam                                = SearchTraversal[Department, Team]
        val searchPersonName: Traversal[Team, String] = SearchTraversal[Team, Person].apply(personNameLens)
        // searchPersonName is a ComposedSearchTraversal (not SearchTraversal), so hits case 2
        val composed = Traversal(searchTeam: Traversal[Department, Team], searchPersonName)

        val names = composed.fold[List[String]](dept)(List.empty, (acc, name) => acc :+ name)
        assert(names.sorted)(equalTo(List("Dev", "Lead")))
      },
      // Traversal.apply(Traversal, Traversal): case (prefixed: PrefixedSearchTraversal, _) (line 1208)
      test("Traversal(PrefixedSearchTraversal, SearchTraversal) via companion apply") {
        val dept = Department(
          "Engineering",
          Person("Director", 45),
          List(
            Team("Frontend", Person("Lead", 35), List(Person("Dev1", 28))),
            Team("Backend", Person("BE Lead", 38), List(Person("Dev2", 30)))
          )
        )
        val searchTeam: Traversal[Department, Team] = SearchTraversal[Department, Team]
        val searchPerson                            = SearchTraversal[Team, Person]
        val prefixed: Traversal[Department, Person] =
          Traversal.PrefixedSearchTraversal[Department, Team, Person](searchTeam, searchPerson)
        val searchString = SearchTraversal[Person, String]
        // Call companion's apply(Traversal, Traversal) where first is PrefixedSearchTraversal
        val composed = Traversal(prefixed, searchString)

        val names = composed.fold[List[String]](dept)(List.empty, (acc, name) => acc :+ name)
        assert(names.sorted)(equalTo(List("BE Lead", "Dev1", "Dev2", "Lead")))
      },
      // Traversal.apply(Traversal, Traversal): case (_, search: SearchTraversal) with TraversalImpl left (line 1211)
      test("Traversal(regular TraversalImpl, SearchTraversal) creates PrefixedSearchTraversal") {
        val containers: List[Container] =
          List(PersonContainer(Person("Alice", 30)), StringContainer("hello"))
        val listTraversal = Traversal.listValues(Container.schema.reflect)
        val searchPerson  = SearchTraversal[Container, Person]
        val composed      = Traversal(listTraversal, searchPerson)

        val names = composed.fold[List[String]](containers)(List.empty, (acc, p) => acc :+ p.name)
        assert(names)(equalTo(List("Alice")))
      },
      // Traversal.apply(Traversal, Traversal): case (_, composed: ComposedSearchTraversal) with TraversalImpl left (line 1214)
      test("Traversal(regular TraversalImpl, ComposedSearchTraversal) creates PrefixedSearchTraversal") {
        val containers: List[Container] =
          List(PersonContainer(Person("Alice", 30)), StringContainer("hello"))
        val listTraversal                                = Traversal.listValues(Container.schema.reflect)
        val composedSearch: Traversal[Container, String] =
          SearchTraversal[Container, Person].apply(personNameLens)
        val composed = Traversal(listTraversal, composedSearch)

        val names = composed.fold[List[String]](containers)(List.empty, (acc, name) => acc :+ name)
        assert(names)(equalTo(List("Alice")))
      },
      // Traversal.apply(Traversal, Traversal): case (_, prefixed: PrefixedSearchTraversal) with TraversalImpl left (line 1217)
      test("Traversal(regular TraversalImpl, PrefixedSearchTraversal) creates PrefixedSearchTraversal") {
        val containers: List[Container] =
          List(PersonContainer(Person("Alice", 30)), StringContainer("hello"))
        val listTraversal                                = Traversal.listValues(Container.schema.reflect)
        val searchPerson                                 = SearchTraversal[Container, Person]
        val searchString                                 = SearchTraversal[Person, String]
        val prefixedSearch: Traversal[Container, String] =
          Traversal.PrefixedSearchTraversal[Container, Person, String](searchPerson, searchString)
        val composed = Traversal(listTraversal, prefixedSearch)

        val names = composed.fold[List[String]](containers)(List.empty, (acc, name) => acc :+ name)
        assert(names)(equalTo(List("Alice")))
      },
      // Traversal.apply(Traversal, Prism): case composed: ComposedSearchTraversal (line 1255)
      test("Traversal(ComposedSearchTraversal, Prism) delegates to composed.apply") {
        val holder                                              = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val searchContainer                                     = SearchTraversal[ContainerHolder, Container]
        val identitySearch                                      = SearchTraversal[Container, Container]
        val composedLeft: Traversal[ContainerHolder, Container] = searchContainer(identitySearch)
        val result                                              = Traversal(composedLeft, containerToPC)

        val names = result.fold[List[String]](holder)(List.empty, (acc, pc) => acc :+ pc.person.name)
        assert(names)(equalTo(List("Alice")))
      },
      // Traversal.apply(Traversal, Optional): case composed: ComposedSearchTraversal (line 1274)
      test("Traversal(ComposedSearchTraversal, Optional) delegates to composed.apply") {
        val holder                                              = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val searchContainer                                     = SearchTraversal[ContainerHolder, Container]
        val identitySearch                                      = SearchTraversal[Container, Container]
        val composedLeft: Traversal[ContainerHolder, Container] = searchContainer(identitySearch)
        val result                                              = Traversal(composedLeft, containerToPerson)

        val names = result.fold[List[String]](holder)(List.empty, (acc, p) => acc :+ p.name)
        assert(names)(equalTo(List("Alice")))
      },
      // Traversal.apply(Lens, Traversal): case composed: ComposedSearchTraversal (line 1293)
      test("Traversal(Lens, ComposedSearchTraversal) creates PrefixedSearchTraversal") {
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
        val composedSearch: Traversal[List[Team], String] =
          SearchTraversal[List[Team], Person].apply(personNameLens)
        val result = Traversal(teamsLens, composedSearch)

        val names = result.fold[List[String]](dept)(List.empty, (acc, name) => acc :+ name)
        assert(names.sorted)(equalTo(List("Dev", "Lead")))
      },
      // Traversal.apply(Lens, Traversal): case prefixed: PrefixedSearchTraversal (line 1295)
      test("Traversal(Lens, PrefixedSearchTraversal) creates PrefixedSearchTraversal") {
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
        val searchTeam                                    = SearchTraversal[List[Team], Team]
        val searchPerson                                  = SearchTraversal[Team, Person]
        val prefixedSearch: Traversal[List[Team], Person] =
          Traversal.PrefixedSearchTraversal[List[Team], Team, Person](searchTeam, searchPerson)
        val result = Traversal(teamsLens, prefixedSearch)

        val names = result.fold[List[String]](dept)(List.empty, (acc, p) => acc :+ p.name)
        assert(names.sorted)(equalTo(List("Dev", "Lead")))
      },
      // Traversal.apply(Prism, Traversal): case prefixed: PrefixedSearchTraversal (line 1314)
      test("Traversal(Prism, PrefixedSearchTraversal) creates PrefixedSearchTraversal") {
        val container: Container                               = PersonContainer(Person("Alice", 30))
        val searchPerson                                       = SearchTraversal[PersonContainer, Person]
        val searchString                                       = SearchTraversal[Person, String]
        val prefixedSearch: Traversal[PersonContainer, String] =
          Traversal.PrefixedSearchTraversal[PersonContainer, Person, String](searchPerson, searchString)
        val result = Traversal(containerToPC, prefixedSearch)

        val names = result.fold[List[String]](container)(List.empty, (acc, name) => acc :+ name)
        assert(names)(equalTo(List("Alice")))
      },
      // Traversal.apply(Optional, Traversal): case prefixed: PrefixedSearchTraversal (line 1333)
      test("Traversal(Optional, PrefixedSearchTraversal) creates PrefixedSearchTraversal") {
        val container: Container                      = PersonContainer(Person("Alice", 30))
        val searchPerson                              = SearchTraversal[Person, Person]
        val searchString                              = SearchTraversal[Person, String]
        val prefixedSearch: Traversal[Person, String] =
          Traversal.PrefixedSearchTraversal[Person, Person, String](searchPerson, searchString)
        val result = Traversal(containerToPerson, prefixedSearch)

        val names = result.fold[List[String]](container)(List.empty, (acc, name) => acc :+ name)
        assert(names)(equalTo(List("Alice")))
      }
    ),
    suite("searchModify short-circuit optimizations")(
      test("Record no-change short-circuit returns original when identity modify applied") {
        // When modify function returns the same value, the Record short-circuit should
        // detect no change and return the original structure unchanged
        val company   = Company("Acme", Person("CEO", 50), List(Person("Alice", 30)))
        val traversal = SearchTraversal[Company, Person]

        // Identity modification — all persons are "modified" to same values
        val modified = traversal.modify(company, identity)

        // Should be equal to original (short-circuit should preserve structure)
        assert(modified)(equalTo(company))
      },
      test("Variant no-change short-circuit returns original when no inner change") {
        // Test that Variant payloads that don't change are short-circuited
        val holder    = ContainerHolder(List(PersonContainer(Person("Alice", 30)), StringContainer("hello")))
        val traversal = SearchTraversal[ContainerHolder, Person]

        // Identity modification
        val modified = traversal.modify(holder, identity)

        assert(modified)(equalTo(holder))
      },
      test("Sequence no-change short-circuit returns original when elements unchanged") {
        // Test that Sequence elements that don't change are short-circuited
        val company   = Company("Acme", Person("CEO", 50), List(Person("Alice", 30), Person("Bob", 25)))
        val traversal = SearchTraversal[Company, Person]

        // Identity modification
        val modified = traversal.modify(company, identity)

        assert(modified)(equalTo(company))
      },
      test("Map no-change short-circuit returns original when entries unchanged") {
        // Test that Map entries that don't change are short-circuited
        val registry  = Registry(Map("a" -> Person("Alice", 30), "b" -> Person("Bob", 25)))
        val traversal = SearchTraversal[Registry, Person]

        // Identity modification
        val modified = traversal.modify(registry, identity)

        assert(modified)(equalTo(registry))
      }
    )
  )
}
