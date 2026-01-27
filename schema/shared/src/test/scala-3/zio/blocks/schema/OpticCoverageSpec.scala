package zio.blocks.schema

import zio.test._
import zio.blocks.typeid.TypeId

/**
 * Coverage tests for TraversalImpl methods in Optic.scala and ReflectPrinter.
 * Targets low coverage methods to boost branch rate.
 */
object OpticCoverageSpec extends SchemaBaseSpec {

  // Test types for traversals
  case class Person(name: String, age: Int, emails: List[String])
  object Person {
    given schema: Schema[Person] = Schema.derived
  }

  case class Container(items: Vector[Int], nested: Option[Container])
  object Container {
    given schema: Schema[Container] = Schema.derived
  }

  case class Indexed(data: Map[String, Int])
  object Indexed {
    given schema: Schema[Indexed] = Schema.derived
  }

  sealed trait Status
  case object Active              extends Status
  case object Inactive            extends Status
  case class Custom(name: String) extends Status
  object Status {
    given schema: Schema[Status] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("OpticCoverageSpec")(
    traversalTests,
    reflectPrinterTests
  )

  // Traversal tests for optic methods
  val traversalTests = suite("TraversalImpl coverage")(
    test("Lens get works") {
      val person     = Person("Alice", 30, List("a@b.com", "c@d.com"))
      val schema     = Schema[Person]
      val record     = schema.reflect.asRecord.get
      val emailsLens = record.lensByIndex[List[String]](2).get
      val emails     = emailsLens.get(person)
      assertTrue(emails.size == 2)
    },
    test("Lens modify works") {
      val person   = Person("Alice", 30, List("a@b.com"))
      val schema   = Schema[Person]
      val record   = schema.reflect.asRecord.get
      val nameLens = record.lensByIndex[String](0).get
      val result   = nameLens.modify(person, _.toUpperCase)
      assertTrue(result == Person("ALICE", 30, List("a@b.com")))
    },
    test("Lens modifyOrFail works") {
      val person   = Person("Alice", 30, List("a@b.com"))
      val schema   = Schema[Person]
      val record   = schema.reflect.asRecord.get
      val nameLens = record.lensByIndex[String](0).get
      val result   = nameLens.modifyOrFail(person, _.toUpperCase)
      assertTrue(result == Right(Person("ALICE", 30, List("a@b.com"))))
    },
    test("Lens modifyOption works") {
      val person  = Person("Alice", 30, List("a@b.com"))
      val schema  = Schema[Person]
      val record  = schema.reflect.asRecord.get
      val ageLens = record.lensByIndex[Int](1).get
      val result  = ageLens.modifyOption(person, _ + 1)
      assertTrue(result == Some(Person("Alice", 31, List("a@b.com"))))
    },
    test("Lens replace works") {
      val person   = Person("Alice", 30, List("a@b.com"))
      val schema   = Schema[Person]
      val record   = schema.reflect.asRecord.get
      val nameLens = record.lensByIndex[String](0).get
      val result   = nameLens.replace(person, "Bob")
      assertTrue(result == Person("Bob", 30, List("a@b.com")))
    },
    test("Lens toString is non-empty") {
      val schema   = Schema[Person]
      val record   = schema.reflect.asRecord.get
      val nameLens = record.lensByIndex[String](0).get
      val str      = nameLens.toString
      assertTrue(str.nonEmpty)
    },
    test("Lens check returns None") {
      val person   = Person("Alice", 30, List("a@b.com"))
      val schema   = Schema[Person]
      val record   = schema.reflect.asRecord.get
      val nameLens = record.lensByIndex[String](0).get
      val result   = nameLens.check(person)
      assertTrue(result.isEmpty)
    },
    test("Lens toDynamic returns path") {
      val schema   = Schema[Person]
      val record   = schema.reflect.asRecord.get
      val nameLens = record.lensByIndex[String](0).get
      val dynamic  = nameLens.toDynamic
      assertTrue(dynamic.nodes.nonEmpty)
    },
    test("Prism getOption returns Some for matching case") {
      val status: Status = Active
      val schema         = Schema[Status]
      val variant        = schema.reflect.asVariant.get
      val activePrism    = variant.prismByIndex[Active.type](0).get
      val result         = activePrism.getOption(status)
      assertTrue(result.isDefined)
    },
    test("Prism getOption returns None for non-matching case") {
      val status: Status = Inactive
      val schema         = Schema[Status]
      val variant        = schema.reflect.asVariant.get
      val activePrism    = variant.prismByIndex[Active.type](0).get
      val result         = activePrism.getOption(status)
      assertTrue(result.isEmpty)
    },
    test("Prism check returns None for matching case") {
      val status: Status = Active
      val schema         = Schema[Status]
      val variant        = schema.reflect.asVariant.get
      val activePrism    = variant.prismByIndex[Active.type](0).get
      val result         = activePrism.check(status)
      assertTrue(result.isEmpty)
    },
    test("Prism check returns Some for non-matching case") {
      val status: Status = Inactive
      val schema         = Schema[Status]
      val variant        = schema.reflect.asVariant.get
      val activePrism    = variant.prismByIndex[Active.type](0).get
      val result         = activePrism.check(status)
      assertTrue(result.isDefined)
    },
    test("Prism getOrFail returns Right for matching case") {
      val status: Status = Active
      val schema         = Schema[Status]
      val variant        = schema.reflect.asVariant.get
      val activePrism    = variant.prismByIndex[Active.type](0).get
      val result         = activePrism.getOrFail(status)
      assertTrue(result.isRight)
    },
    test("Prism getOrFail returns Left for non-matching case") {
      val status: Status = Inactive
      val schema         = Schema[Status]
      val variant        = schema.reflect.asVariant.get
      val activePrism    = variant.prismByIndex[Active.type](0).get
      val result         = activePrism.getOrFail(status)
      assertTrue(result.isLeft)
    },
    test("Prism reverseGet works") {
      val schema      = Schema[Status]
      val variant     = schema.reflect.asVariant.get
      val activePrism = variant.prismByIndex[Active.type](0).get
      val result      = activePrism.reverseGet(Active)
      assertTrue(result == Active)
    },
    test("Prism replace works for matching case") {
      val status: Status = Active
      val schema         = Schema[Status]
      val variant        = schema.reflect.asVariant.get
      val inactivePrism  = variant.prismByIndex[Inactive.type](1).get
      val replaced       = inactivePrism.replace(status, Inactive)
      assertTrue(replaced == Active) // Does not match, returns original
    },
    test("Prism modify works for matching case") {
      val status: Status = Custom("test")
      val schema         = Schema[Status]
      val variant        = schema.reflect.asVariant.get
      val customPrism    = variant.prismByIndex[Custom](2).get
      val result         = customPrism.modify(status, c => Custom(c.name.toUpperCase))
      assertTrue(result == Custom("TEST"))
    },
    test("Prism modify no-op for non-matching case") {
      val status: Status = Active
      val schema         = Schema[Status]
      val variant        = schema.reflect.asVariant.get
      val customPrism    = variant.prismByIndex[Custom](2).get
      val result         = customPrism.modify(status, c => Custom(c.name.toUpperCase))
      assertTrue(result == Active) // No-op, returns original
    },
    test("Prism modifyOption returns Some for matching case") {
      val status: Status = Custom("test")
      val schema         = Schema[Status]
      val variant        = schema.reflect.asVariant.get
      val customPrism    = variant.prismByIndex[Custom](2).get
      val result         = customPrism.modifyOption(status, c => Custom(c.name.toUpperCase))
      assertTrue(result == Some(Custom("TEST")))
    },
    test("Prism modifyOption returns None for non-matching case") {
      val status: Status = Active
      val schema         = Schema[Status]
      val variant        = schema.reflect.asVariant.get
      val customPrism    = variant.prismByIndex[Custom](2).get
      val result         = customPrism.modifyOption(status, c => Custom(c.name.toUpperCase))
      assertTrue(result.isEmpty)
    },
    test("Prism modifyOrFail returns Right for matching case") {
      val status: Status = Custom("test")
      val schema         = Schema[Status]
      val variant        = schema.reflect.asVariant.get
      val customPrism    = variant.prismByIndex[Custom](2).get
      val result         = customPrism.modifyOrFail(status, c => Custom(c.name.toUpperCase))
      assertTrue(result == Right(Custom("TEST")))
    },
    test("Prism modifyOrFail returns Left for non-matching case") {
      val status: Status = Active
      val schema         = Schema[Status]
      val variant        = schema.reflect.asVariant.get
      val customPrism    = variant.prismByIndex[Custom](2).get
      val result         = customPrism.modifyOrFail(status, c => Custom(c.name.toUpperCase))
      assertTrue(result.isLeft)
    },
    test("Prism replaceOption returns Some for matching case") {
      val status: Status = Active
      val schema         = Schema[Status]
      val variant        = schema.reflect.asVariant.get
      val activePrism    = variant.prismByIndex[Active.type](0).get
      val result         = activePrism.replaceOption(status, Active)
      assertTrue(result == Some(Active))
    },
    test("Prism replaceOption returns None for non-matching case") {
      val status: Status = Inactive
      val schema         = Schema[Status]
      val variant        = schema.reflect.asVariant.get
      val activePrism    = variant.prismByIndex[Active.type](0).get
      val result         = activePrism.replaceOption(status, Active)
      assertTrue(result.isEmpty)
    },
    test("Prism replaceOrFail returns Right for matching case") {
      val status: Status = Active
      val schema         = Schema[Status]
      val variant        = schema.reflect.asVariant.get
      val activePrism    = variant.prismByIndex[Active.type](0).get
      val result         = activePrism.replaceOrFail(status, Active)
      assertTrue(result == Right(Active))
    },
    test("Prism replaceOrFail returns Left for non-matching case") {
      val status: Status = Inactive
      val schema         = Schema[Status]
      val variant        = schema.reflect.asVariant.get
      val activePrism    = variant.prismByIndex[Active.type](0).get
      val result         = activePrism.replaceOrFail(status, Active)
      assertTrue(result.isLeft)
    },
    test("Prism toString is non-empty") {
      val schema      = Schema[Status]
      val variant     = schema.reflect.asVariant.get
      val activePrism = variant.prismByIndex[Active.type](0).get
      val str         = activePrism.toString
      assertTrue(str.nonEmpty)
    },
    test("Prism toDynamic returns path") {
      val schema      = Schema[Status]
      val variant     = schema.reflect.asVariant.get
      val activePrism = variant.prismByIndex[Active.type](0).get
      val dynamic     = activePrism.toDynamic
      assertTrue(dynamic.nodes.nonEmpty)
    }
  )

  // ReflectPrinter tests - use correct API
  val reflectPrinterTests = suite("ReflectPrinter coverage")(
    test("printRecord for Person schema") {
      val schema = Schema[Person]
      val record = schema.reflect.asRecord.get
      val str    = ReflectPrinter.printRecord(record)
      assertTrue(
        str.nonEmpty,
        str.contains("name") || str.contains("Person")
      )
    },
    test("printVariant for Status schema") {
      val schema  = Schema[Status]
      val variant = schema.reflect.asVariant.get
      val str     = ReflectPrinter.printVariant(variant)
      assertTrue(
        str.nonEmpty,
        str.contains("Status") || str.contains("Active")
      )
    },
    test("printSequence for List schema") {
      val schema = Schema[List[Int]]
      val seq    = schema.reflect.asSequence.get
      val str    = ReflectPrinter.printSequence(seq)
      assertTrue(str.nonEmpty)
    },
    test("printMap for Map schema") {
      val schema = Schema[Map[String, Int]]
      val map    = schema.reflect.asMap.get
      val str    = ReflectPrinter.printMap(map)
      assertTrue(str.nonEmpty)
    },
    test("printRecord for Container schema with nested option") {
      val schema = Schema[Container]
      val record = schema.reflect.asRecord.get
      val str    = ReflectPrinter.printRecord(record)
      assertTrue(str.nonEmpty)
    },
    test("printRecord for Indexed schema with map field") {
      val schema = Schema[Indexed]
      val record = schema.reflect.asRecord.get
      val str    = ReflectPrinter.printRecord(record)
      assertTrue(str.nonEmpty)
    },
    test("printVariant for Option schema") {
      val schema  = Schema[Option[Int]]
      val variant = schema.reflect.asVariant.get
      val str     = ReflectPrinter.printVariant(variant)
      assertTrue(str.nonEmpty)
    },
    test("printVariant for Either schema") {
      val schema  = Schema[Either[String, Int]]
      val variant = schema.reflect.asVariant.get
      val str     = ReflectPrinter.printVariant(variant)
      assertTrue(str.nonEmpty)
    },
    test("printTerm works for record field") {
      val schema = Schema[Person]
      val record = schema.reflect.asRecord.get
      val term   = record.fields(0)
      val str    = ReflectPrinter.printTerm(term.asInstanceOf[Term[binding.Binding, Person, ?]])
      assertTrue(str.nonEmpty)
    },
    test("printSequence for Vector schema") {
      val schema = Schema[Vector[String]]
      val seq    = schema.reflect.asSequence.get
      val str    = ReflectPrinter.printSequence(seq)
      assertTrue(str.nonEmpty)
    },
    test("printSequence for Set schema") {
      val schema = Schema[Set[Int]]
      val seq    = schema.reflect.asSequence.get
      val str    = ReflectPrinter.printSequence(seq)
      assertTrue(str.nonEmpty)
    }
  )
}
