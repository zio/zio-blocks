package zio.blocks.schema.migration

import zio.test._
import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, Schema}

/**
 * Tests for MigrationBuilderMacros.extractPath to exercise branches of
 * collectNodes. This improves coverage of the compile-time macro code.
 */
object MigrationBuilderMacrosSpec extends ZIOSpecDefault {

  // Test types for various selector scenarios
  case class Simple(name: String, age: Int)
  object Simple {
    implicit val schema: Schema[Simple] = Schema.derived
  }

  case class Nested(inner: Simple)
  object Nested {
    implicit val schema: Schema[Nested] = Schema.derived
  }

  case class DeepNested(outer: Nested)
  object DeepNested {
    implicit val schema: Schema[DeepNested] = Schema.derived
  }

  case class NameParts(first: String, last: String)
  object NameParts {
    implicit val schema: Schema[NameParts] = Schema.derived
  }

  case class FullName(full: String)
  object FullName {
    implicit val schema: Schema[FullName] = Schema.derived
  }

  case class PersonSource(person: NameParts, city: String)
  object PersonSource {
    implicit val schema: Schema[PersonSource] = Schema.derived
  }

  case class PersonTarget(person: FullName, city: String)
  object PersonTarget {
    implicit val schema: Schema[PersonTarget] = Schema.derived
  }

  case class PersonSource2(person: FullName, city: String)
  object PersonSource2 {
    implicit val schema: Schema[PersonSource2] = Schema.derived
  }

  case class PersonTarget2(person: NameParts, city: String)
  object PersonTarget2 {
    implicit val schema: Schema[PersonTarget2] = Schema.derived
  }

  sealed trait StatusV1
  object StatusV1 {
    case object Active  extends StatusV1
    case object Pending extends StatusV1
  }

  sealed trait StatusV2
  object StatusV2 {
    case object Active   extends StatusV2
    case object Approved extends StatusV2
  }

  case class WithStatusV1(status: StatusV1)
  object WithStatusV1 {
    implicit val schema: Schema[WithStatusV1] = Schema.derived
  }

  case class WithStatusV2(status: StatusV2)
  object WithStatusV2 {
    implicit val schema: Schema[WithStatusV2] = Schema.derived
  }

  def spec = suite("MigrationBuilderMacrosSpec")(
    suite("extractPath")(
      test("extracts simple field access") {
        val path = MigrationBuilderMacros.extractPath[Simple, String](_.name)
        assertTrue(
          path.nodes.size == 1,
          path.nodes.head == DynamicOptic.Node.Field("name")
        )
      },
      test("extracts another simple field") {
        val path = MigrationBuilderMacros.extractPath[Simple, Int](_.age)
        assertTrue(
          path.nodes.size == 1,
          path.nodes.head == DynamicOptic.Node.Field("age")
        )
      },
      test("extracts nested field access") {
        val path = MigrationBuilderMacros.extractPath[Nested, String](_.inner.name)
        assertTrue(
          path.nodes.size == 2,
          path.nodes(0) == DynamicOptic.Node.Field("inner"),
          path.nodes(1) == DynamicOptic.Node.Field("name")
        )
      },
      test("extracts deeply nested paths") {
        val path = MigrationBuilderMacros.extractPath[DeepNested, String](_.outer.inner.name)
        assertTrue(
          path.nodes.size == 3,
          path.nodes(0) == DynamicOptic.Node.Field("outer"),
          path.nodes(1) == DynamicOptic.Node.Field("inner"),
          path.nodes(2) == DynamicOptic.Node.Field("name")
        )
      }
    ),
    suite("extractCaseName")(
      test("extracts simple case name from case object") {
        sealed trait Status
        object Status {
          case object Active extends Status
        }
        val name = MigrationBuilderMacros.extractCaseName[Status.Active.type]
        // Case objects have a $ suffix in their symbol name
        assertTrue(name == "Active$")
      },
      test("extracts case class name") {
        sealed trait Event
        object Event {
          case class Created(at: Long) extends Event
        }
        val name = MigrationBuilderMacros.extractCaseName[Event.Created]
        assertTrue(name == "Created")
      }
    ),
    suite("validateSelector")(
      test("validates valid field selector") {
        MigrationBuilderMacros.validateSelector[Simple, String](_.name)
        assertTrue(true)
      },
      test("validates valid nested selector") {
        MigrationBuilderMacros.validateSelector[Nested, String](_.inner.name)
        assertTrue(true)
      },
      test("validates deeply nested selector") {
        MigrationBuilderMacros.validateSelector[DeepNested, String](_.outer.inner.name)
        assertTrue(true)
      }
    ),
    suite("integration with MigrationBuilder")(
      test("addField using selector invokes macro") {
        import MigrationBuilderSyntax._
        val builder = MigrationBuilder[Simple, Nested]
          .addField(_.inner, Simple("", 0))
        assertTrue(builder.currentActions.nonEmpty)
      },
      test("renameField using selector invokes macro") {
        import MigrationBuilderSyntax._
        case class V1(oldName: String)
        case class V2(newName: String)
        object V1 { implicit val s: Schema[V1] = Schema.derived }
        object V2 { implicit val s: Schema[V2] = Schema.derived }

        val builder = MigrationBuilder[V1, V2]
          .renameField(_.oldName, _.newName)
        assertTrue(builder.currentActions.nonEmpty)
      },
      test("dropField using selector invokes macro") {
        import MigrationBuilderSyntax._
        case class V1(name: String, extra: String)
        case class V2(name: String)
        object V1 { implicit val s: Schema[V1] = Schema.derived }
        object V2 { implicit val s: Schema[V2] = Schema.derived }

        val builder = MigrationBuilder[V1, V2]
          .dropField(_.extra, "default")
        assertTrue(builder.currentActions.nonEmpty)
      }
    ),
    suite("validateAndBuild")(
      test("joinFields covers nested handled/provided fields") {
        val builder = MigrationBuilder[PersonSource, PersonTarget]
          .joinFields(
            DynamicOptic.root.field("person"),
            "full",
            Chunk(
              DynamicOptic.root.field("person").field("first"),
              DynamicOptic.root.field("person").field("last")
            ),
            Resolved.Concat(
              Vector(
                Resolved.FieldAccess("first", Resolved.Identity),
                Resolved.Literal.string(" "),
                Resolved.FieldAccess("last", Resolved.Identity)
              ),
              ""
            ),
            Resolved.SplitString(Resolved.Identity, " ", 0)
          )

        val result = scala.util.Try(MigrationBuilderMacros.buildValidated(builder))
        assertTrue(result.isSuccess)
      },
      test("splitField covers nested provided fields") {
        val builder = MigrationBuilder[PersonSource2, PersonTarget2]
          .splitField(
            DynamicOptic.root.field("person"),
            "full",
            Chunk(
              DynamicOptic.root.field("person").field("first"),
              DynamicOptic.root.field("person").field("last")
            ),
            Resolved.SplitString(Resolved.Identity, " ", 0),
            Resolved.Concat(
              Vector(
                Resolved.FieldAccess("first", Resolved.Identity),
                Resolved.Literal.string(" "),
                Resolved.FieldAccess("last", Resolved.Identity)
              ),
              ""
            )
          )

        val result = scala.util.Try(MigrationBuilderMacros.buildValidated(builder))
        assertTrue(result.isSuccess)
      },
      test("missing provided fields reports error") {
        case class V1(name: String)
        case class V2(name: String, email: String)
        object V1 { implicit val schema: Schema[V1] = Schema.derived }
        object V2 { implicit val schema: Schema[V2] = Schema.derived }

        val builder = MigrationBuilder[V1, V2]
        val result  = scala.util.Try(MigrationBuilderMacros.buildValidated(builder))
        assertTrue(result.isFailure)
      },
      test("renameCase covers handled/provided cases") {
        val builder = MigrationBuilder[WithStatusV1, WithStatusV2]
          .renameCaseAt(DynamicOptic.root.field("status"), "Pending", "Approved")

        val result = scala.util.Try(MigrationBuilderMacros.buildValidated(builder))
        assertTrue(result.isSuccess)
      },
      test("missing case migration reports error") {
        val builder = MigrationBuilder[WithStatusV1, WithStatusV2]
        val result  = scala.util.Try(MigrationBuilderMacros.buildValidated(builder))
        assertTrue(result.isFailure)
      }
    )
  )
}
