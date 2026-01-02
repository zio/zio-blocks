package zio.blocks.schema.structural

import zio.blocks.schema._
import zio.test._

/**
 * Tests that verify Selectable-based structural type Schema derivation works on
 * all platforms.
 *
 * Selectable subtypes with a Map[String, Any] constructor (or companion apply)
 * work cross-platform because they don't require reflection for
 * construction/deconstruction.
 *
 * For pure structural types (without Selectable base), see the JVM-only tests.
 */
object SelectableStructuralTypeSpec extends ZIOSpecDefault {

  def spec = suite("SelectableStructuralTypeSpec")(
    suite("Selectable with Map Constructor")(
      test("Selectable with Map constructor compiles") {
        case class Record(fields: Map[String, Any]) extends Selectable {
          def selectDynamic(name: String): Any = fields(name)
        }
        type PersonLike = Record { def name: String; def age: Int }

        val schema = Schema.derived[PersonLike]
        assertTrue(schema != null)
      },
      test("Selectable schema has correct field names") {
        case class Record(fields: Map[String, Any]) extends Selectable {
          def selectDynamic(name: String): Any = fields(name)
        }
        type PersonLike = Record { def name: String; def age: Int }

        val schema     = Schema.derived[PersonLike]
        val fieldNames = schema.reflect match {
          case record: Reflect.Record[_, _] => record.fields.map(_.name).toSet
          case _                            => Set.empty[String]
        }
        assertTrue(fieldNames == Set("name", "age"))
      }
    ),
    suite("Selectable with Companion Apply")(
      test("Selectable with companion apply compiles") {
        case class RecordApply(fieldsList: List[(String, Any)]) extends Selectable {
          private val m: Map[String, Any]      = fieldsList.toMap
          def selectDynamic(name: String): Any = m(name)
        }
        object RecordApply {
          def apply(map: Map[String, Any]): RecordApply = new RecordApply(map.toList)
        }
        type PersonLike = RecordApply { def name: String; def age: Int }

        val schema = Schema.derived[PersonLike]
        assertTrue(schema != null)
      }
    )
  )
}
