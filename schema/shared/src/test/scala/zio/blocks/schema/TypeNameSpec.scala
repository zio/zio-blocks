package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

object TypeNameSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("TypeNameSpec")(
    suite("TypeName.derived")(
      test("derives TypeName for simple type") {
        val typeName = TypeName.derived[SimpleRecord]
        assert(typeName.name)(equalTo("SimpleRecord")) &&
        assert(typeName.namespace.packages)(equalTo(Seq("zio", "blocks", "schema"))) &&
        assert(typeName.namespace.values)(equalTo(Seq("TypeNameSpec")))
      },
      test("derives TypeName for generic type") {
        val typeName = TypeName.derived[List[String]]
        assert(typeName.name)(equalTo("List")) &&
        assert(typeName.params.map(_.name))(equalTo(Seq("String")))
      },
      test("derives TypeName for nested generic types") {
        val typeName = TypeName.derived[Map[String, List[Int]]]
        assert(typeName.name)(equalTo("Map")) &&
        assert(typeName.params.size)(equalTo(2)) &&
        assert(typeName.params.head.name)(equalTo("String")) &&
        assert(typeName.params(1).name)(equalTo("List"))
      },
      test("derives TypeName for case object") {
        val typeName = TypeName.derived[SingletonObject.type]
        assert(typeName.name)(equalTo("SingletonObject"))
      },
      test("derives TypeName for tuple types") {
        val typeName = TypeName.derived[(Int, String)]
        assert(typeName.name)(equalTo("Tuple2")) &&
        assert(typeName.params.map(_.name))(equalTo(Seq("Int", "String")))
      },
      test("derives TypeName for Option type") {
        val typeName = TypeName.derived[Option[Double]]
        assert(typeName.name)(equalTo("Option")) &&
        assert(typeName.params.map(_.name))(equalTo(Seq("Double")))
      },
      test("derives TypeName for Either type") {
        val typeName = TypeName.derived[Either[String, Int]]
        assert(typeName.name)(equalTo("Either")) &&
        assert(typeName.params.map(_.name))(equalTo(Seq("String", "Int")))
      },
      test("derives TypeName for AnyVal wrapper") {
        val typeName = TypeName.derived[AnyValWrapper]
        assert(typeName.name)(equalTo("AnyValWrapper"))
      },
      test("derives TypeName for Boolean") {
        val typeName = TypeName.derived[Boolean]
        assert(typeName.name)(equalTo("Boolean"))
      },
      test("derives TypeName for Char") {
        val typeName = TypeName.derived[Char]
        assert(typeName.name)(equalTo("Char"))
      },
      test("derives TypeName for Byte") {
        val typeName = TypeName.derived[Byte]
        assert(typeName.name)(equalTo("Byte"))
      },
      test("derives TypeName for Short") {
        val typeName = TypeName.derived[Short]
        assert(typeName.name)(equalTo("Short"))
      },
      test("derives TypeName for Float") {
        val typeName = TypeName.derived[Float]
        assert(typeName.name)(equalTo("Float"))
      },
      test("derives TypeName for Int") {
        val typeName = TypeName.derived[Int]
        assert(typeName.name)(equalTo("Int"))
      },
      test("derives TypeName for Long") {
        val typeName = TypeName.derived[Long]
        assert(typeName.name)(equalTo("Long"))
      },
      test("derives TypeName for Double") {
        val typeName = TypeName.derived[Double]
        assert(typeName.name)(equalTo("Double"))
      },
      test("derives TypeName for String") {
        val typeName = TypeName.derived[String]
        assert(typeName.name)(equalTo("String"))
      },
      test("derives TypeName for Vector") {
        val typeName = TypeName.derived[Vector[Int]]
        assert(typeName.name)(equalTo("Vector")) &&
        assert(typeName.params.map(_.name))(equalTo(Seq("Int")))
      },
      test("derives TypeName for Set") {
        val typeName = TypeName.derived[Set[String]]
        assert(typeName.name)(equalTo("Set")) &&
        assert(typeName.params.map(_.name))(equalTo(Seq("String")))
      },
      test("derives TypeName for java.lang.String") {
        val typeName = TypeName.derived[java.lang.String]
        assert(typeName.name)(equalTo("String"))
      },
      test("derives TypeName for nested class") {
        val typeName = TypeName.derived[OuterClass.InnerClass]
        assert(typeName.name)(equalTo("InnerClass")) &&
        assert(typeName.namespace.values)(contains("OuterClass"))
      },
      test("derives TypeName for generic nested class") {
        val typeName = TypeName.derived[OuterClass.GenericInner[Int]]
        assert(typeName.name)(equalTo("GenericInner")) &&
        assert(typeName.params.map(_.name))(equalTo(Seq("Int")))
      },
      test("derives TypeName for deeply nested generic types") {
        val typeName = TypeName.derived[Option[List[Map[String, Set[Int]]]]]
        assert(typeName.name)(equalTo("Option")) &&
        assert(typeName.params.head.name)(equalTo("List")) &&
        assert(typeName.params.head.params.head.name)(equalTo("Map")) &&
        assert(typeName.params.head.params.head.params.head.name)(equalTo("String")) &&
        assert(typeName.params.head.params.head.params(1).name)(equalTo("Set"))
      },
      test("derives TypeName for Seq") {
        val typeName = TypeName.derived[Seq[Long]]
        assert(typeName.name)(equalTo("Seq")) &&
        assert(typeName.params.map(_.name))(equalTo(Seq("Long")))
      },
      test("derives TypeName for IndexedSeq") {
        val typeName = TypeName.derived[IndexedSeq[Double]]
        assert(typeName.name)(equalTo("IndexedSeq")) &&
        assert(typeName.params.map(_.name))(equalTo(Seq("Double")))
      },
      test("derives TypeName for Unit") {
        val typeName = TypeName.derived[Unit]
        assert(typeName.name)(equalTo("Unit"))
      },
      test("derives TypeName for BigInt") {
        val typeName = TypeName.derived[BigInt]
        assert(typeName.name)(equalTo("BigInt"))
      },
      test("derives TypeName for BigDecimal") {
        val typeName = TypeName.derived[BigDecimal]
        assert(typeName.name)(equalTo("BigDecimal"))
      },
      test("derives TypeName for java.time types") {
        val instantTypeName   = TypeName.derived[java.time.Instant]
        val durationTypeName  = TypeName.derived[java.time.Duration]
        val localDateTypeName = TypeName.derived[java.time.LocalDate]
        assert(instantTypeName.name)(equalTo("Instant")) &&
        assert(durationTypeName.name)(equalTo("Duration")) &&
        assert(localDateTypeName.name)(equalTo("LocalDate"))
      },
      test("derives TypeName for java.util.UUID") {
        val typeName = TypeName.derived[java.util.UUID]
        assert(typeName.name)(equalTo("UUID"))
      },
      test("derives TypeName for Tuple3") {
        val typeName = TypeName.derived[(Int, String, Boolean)]
        assert(typeName.name)(equalTo("Tuple3")) &&
        assert(typeName.params.map(_.name))(equalTo(Seq("Int", "String", "Boolean")))
      },
      test("derives TypeName for type alias") {
        type MyList[A] = List[A]
        val typeName = TypeName.derived[MyList[Int]]
        assert(typeName.name)(equalTo("List")) &&
        assert(typeName.params.map(_.name))(equalTo(Seq("Int")))
      },
      test("derives TypeName for sealed trait") {
        val typeName = TypeName.derived[SealedVariant]
        assert(typeName.name)(equalTo("SealedVariant"))
      },
      test("derives TypeName for generic sealed trait") {
        val typeName = TypeName.derived[GenericSealedVariant[String]]
        assert(typeName.name)(equalTo("GenericSealedVariant")) &&
        assert(typeName.params.map(_.name))(equalTo(Seq("String")))
      }
    )
  )

  case class SimpleRecord(a: Int, b: String)

  case class AnyValWrapper(value: Long) extends AnyVal

  case object SingletonObject

  object OuterClass {
    case class InnerClass(x: Int)
    case class GenericInner[A](a: A)
  }

  sealed trait SealedVariant
  case class SealedCase1(i: Int)    extends SealedVariant
  case class SealedCase2(s: String) extends SealedVariant

  sealed trait GenericSealedVariant[A]
  case class GenericCase1[A](a: A)       extends GenericSealedVariant[A]
  case class GenericCase2[A](a: A, b: A) extends GenericSealedVariant[A]
}
