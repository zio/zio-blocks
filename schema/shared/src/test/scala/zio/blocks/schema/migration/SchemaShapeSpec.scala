package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object SchemaShapeSpec extends SchemaBaseSpec {

  // Extract shapes from real schemas to avoid constructing PrimitiveType instances directly
  private val stringShape = SchemaShape.fromReflect(Schema[String].reflect)
  private val intShape    = SchemaShape.fromReflect(Schema[Int].reflect)

  def spec: Spec[TestEnvironment, Any] = suite("SchemaShapeSpec")(
    fromReflectSuite,
    compareShapesSuite,
    applyActionSuite
  )

  private val fromReflectSuite = suite("fromReflect")(
    test("extracts primitive shape") {
      val shape = SchemaShape.fromReflect(Schema[Int].reflect)
      assertTrue(shape match {
        case SchemaShape.Prim(_) => true
        case _                   => false
      })
    },
    test("extracts record shape") {
      case class Person(name: String, age: Int)
      implicit val schema: Schema[Person] = Schema.derived[Person]
      val shape                           = SchemaShape.fromReflect(schema.reflect)
      assertTrue(shape match {
        case SchemaShape.Record(fields) =>
          fields.length == 2 &&
          fields.exists(_._1 == "name") &&
          fields.exists(_._1 == "age")
        case _ => false
      })
    },
    test("extracts Option as Opt") {
      val shape = SchemaShape.fromReflect(Schema[Option[String]].reflect)
      assertTrue(shape match {
        case SchemaShape.Opt(SchemaShape.Prim(_)) => true
        case _                                    => false
      })
    },
    test("extracts Seq as Sequence") {
      val shape = SchemaShape.fromReflect(Schema[Seq[Int]].reflect)
      assertTrue(shape match {
        case SchemaShape.Sequence(SchemaShape.Prim(_)) => true
        case _                                         => false
      })
    },
    test("extracts Map as MapShape") {
      val shape = SchemaShape.fromReflect(Schema[scala.collection.immutable.Map[String, Int]].reflect)
      assertTrue(shape match {
        case SchemaShape.MapShape(SchemaShape.Prim(_), SchemaShape.Prim(_)) => true
        case _                                                              => false
      })
    }
  )

  private val compareShapesSuite = suite("compareShapes")(
    test("identical shapes produce no errors") {
      val shape = SchemaShape.Record(
        Vector(
          ("name", stringShape),
          ("age", intShape)
        )
      )
      val errors = SchemaShape.compareShapes(shape, shape)
      assertTrue(errors.isEmpty)
    },
    test("missing field produces error") {
      val actual = SchemaShape.Record(
        Vector(
          ("name", stringShape)
        )
      )
      val expected = SchemaShape.Record(
        Vector(
          ("name", stringShape),
          ("age", intShape)
        )
      )
      val errors = SchemaShape.compareShapes(actual, expected)
      assertTrue(
        errors.size == 1,
        errors.head.message.contains("missing target field 'age'")
      )
    },
    test("extra field produces error") {
      val actual = SchemaShape.Record(
        Vector(
          ("name", stringShape),
          ("age", intShape)
        )
      )
      val expected = SchemaShape.Record(
        Vector(
          ("name", stringShape)
        )
      )
      val errors = SchemaShape.compareShapes(actual, expected)
      assertTrue(
        errors.size == 1,
        errors.head.message.contains("unaccounted source field 'age'")
      )
    },
    test("type mismatch produces error") {
      val actual = SchemaShape.Record(
        Vector(
          ("name", stringShape)
        )
      )
      val expected = SchemaShape.Record(
        Vector(
          ("name", intShape)
        )
      )
      val errors = SchemaShape.compareShapes(actual, expected)
      assertTrue(
        errors.size == 1,
        errors.head.message.contains("shape mismatch")
      )
    },
    test("Dyn matches anything") {
      val actual   = SchemaShape.Dyn
      val expected = SchemaShape.Record(
        Vector(
          ("name", stringShape)
        )
      )
      val errors = SchemaShape.compareShapes(actual, expected)
      assertTrue(errors.isEmpty)
    },
    test("Wrap is structural by default") {
      val actual   = SchemaShape.Wrap(stringShape)
      val expected = stringShape
      val errors   = SchemaShape.compareShapes(actual, expected)
      assertTrue(errors.nonEmpty) // Wrap(String) != String
    },
    test("Wrap is transparent when wrapTransparent = true") {
      val actual   = SchemaShape.Wrap(stringShape)
      val expected = stringShape
      val errors   = SchemaShape.compareShapes(actual, expected, wrapTransparent = true)
      assertTrue(errors.isEmpty)
    }
  )

  private val applyActionSuite = suite("applyAction (symbolic execution)")(
    test("AddField adds to record shape") {
      val shape = SchemaShape.Record(
        Vector(
          ("name", stringShape)
        )
      )
      val action = MigrationAction.AddField(
        DynamicOptic.root.field("age"),
        SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      )
      val result = SchemaShape.applyAction(shape, action)
      assertTrue(result match {
        case Right(SchemaShape.Record(fields)) =>
          fields.length == 2 && fields.exists(_._1 == "age")
        case _ => false
      })
    },
    test("AddField fails when field already exists") {
      val shape = SchemaShape.Record(
        Vector(
          ("name", stringShape),
          ("age", intShape)
        )
      )
      val action = MigrationAction.AddField(
        DynamicOptic.root.field("age"),
        SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      )
      val result = SchemaShape.applyAction(shape, action)
      assertTrue(result.isLeft)
    },
    test("DropField removes from record shape") {
      val shape = SchemaShape.Record(
        Vector(
          ("name", stringShape),
          ("age", intShape)
        )
      )
      val action = MigrationAction.DropField(DynamicOptic.root.field("age"), reverseDefault = None)
      val result = SchemaShape.applyAction(shape, action)
      assertTrue(result match {
        case Right(SchemaShape.Record(fields)) =>
          fields.length == 1 && !fields.exists(_._1 == "age")
        case _ => false
      })
    },
    test("Rename updates field name in shape") {
      val shape = SchemaShape.Record(
        Vector(
          ("firstName", stringShape)
        )
      )
      val action = MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName")
      val result = SchemaShape.applyAction(shape, action)
      assertTrue(result match {
        case Right(SchemaShape.Record(fields)) =>
          fields.exists(_._1 == "fullName") && !fields.exists(_._1 == "firstName")
        case _ => false
      })
    },
    test("Mandate unwraps Opt") {
      val shape = SchemaShape.Record(
        Vector(
          ("nickname", SchemaShape.Opt(stringShape))
        )
      )
      val defaultExpr = SchemaExpr.Literal[Any, Any]("", Schema[String].asInstanceOf[Schema[Any]])
      val action      = MigrationAction.Mandate(DynamicOptic.root.field("nickname"), defaultExpr)
      val result      = SchemaShape.applyAction(shape, action)
      assertTrue(result match {
        case Right(SchemaShape.Record(fields)) =>
          fields.exists(f => f._1 == "nickname" && f._2 == stringShape)
        case _ => false
      })
    },
    test("Optionalize wraps in Opt") {
      val shape = SchemaShape.Record(
        Vector(
          ("name", stringShape)
        )
      )
      val action = MigrationAction.Optionalize(DynamicOptic.root.field("name"))
      val result = SchemaShape.applyAction(shape, action)
      assertTrue(result match {
        case Right(SchemaShape.Record(fields)) =>
          fields.exists(f => f._1 == "name" && f._2 == SchemaShape.Opt(stringShape))
        case _ => false
      })
    },
    test("RenameCase renames case in variant shape") {
      val shape = SchemaShape.Variant(
        Vector(
          ("Active", SchemaShape.Record(Vector.empty)),
          ("Inactive", SchemaShape.Record(Vector.empty))
        )
      )
      val action = MigrationAction.RenameCase(DynamicOptic.root, "Active", "Enabled")
      val result = SchemaShape.applyAction(shape, action)
      assertTrue(result match {
        case Right(SchemaShape.Variant(cases)) =>
          cases.exists(_._1 == "Enabled") && !cases.exists(_._1 == "Active")
        case _ => false
      })
    },
    test("sequential actions compose correctly") {
      val shape = SchemaShape.Record(
        Vector(
          ("firstName", stringShape),
          ("obsolete", intShape)
        )
      )
      val actions = Vector(
        MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName"),
        MigrationAction.DropField(DynamicOptic.root.field("obsolete"), reverseDefault = None),
        MigrationAction.AddField(
          DynamicOptic.root.field("age"),
          SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
        )
      )
      val result = actions.foldLeft[Either[List[MigrationError], SchemaShape]](Right(shape)) {
        case (Right(s), a) => SchemaShape.applyAction(s, a)
        case (left, _)     => left
      }
      assertTrue(result match {
        case Right(SchemaShape.Record(fields)) =>
          fields.length == 2 &&
          fields.exists(_._1 == "fullName") &&
          fields.exists(_._1 == "age") &&
          !fields.exists(_._1 == "firstName") &&
          !fields.exists(_._1 == "obsolete")
        case _ => false
      })
    }
  )
}
