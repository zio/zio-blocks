package zio.blocks.schema.comptime

import zio.blocks.schema.{Schema, SchemaBaseSpec}
import zio.blocks.schema.comptime.Allows
import Allows._
import zio.prelude.{Newtype, Subtype}
import zio.test._
import zio.test.Assertion._

/**
 * Tests for Allows[A, Wrapped[...]] — ZIO Prelude newtypes/subtypes.
 *
 * Also exercises native Scala 3 union type syntax and opaque type handling.
 *
 * Note on opaque types: the Allows macro resolves opaque types by looking
 * through to their underlying type (just as Schema.derived does). A Scala 3
 * opaque type alias such as `opaque type UserId = UUID` is therefore treated as
 * `Primitive` (since UUID is a primitive), not as `Wrapped[Primitive]`. The
 * `Wrapped` grammar node matches the ZIO Prelude Newtype/Subtype pattern, which
 * wraps the underlying schema via Schema.transform.
 *
 * Native Scala 3 union syntax `A | B` in grammar positions is equivalent to
 * writing `A | B` using the Allows.| type in infix notation.
 */
object AllowsWrappedSpec extends SchemaBaseSpec {

  // ---------------------------------------------------------------------------
  // ZIO Prelude newtypes
  // ---------------------------------------------------------------------------

  type ProductCode = ProductCode.Type
  object ProductCode extends Newtype[String] {
    implicit val schema: Schema[ProductCode] =
      Schema[String].transform(_.asInstanceOf[ProductCode], _.asInstanceOf[String])
    // ZIO Prelude Newtype IS matched as Wrapped by the macro
    val ev1: Allows[ProductCode, Wrapped[Primitive]]             = implicitly
    val ev2: Allows[ProductCode, Primitive | Wrapped[Primitive]] = implicitly
  }

  type Price = Price.Type
  object Price extends Subtype[BigDecimal] {
    implicit val schema: Schema[Price] =
      Schema[BigDecimal].transform(_.asInstanceOf[Price], _.asInstanceOf[BigDecimal])
    val ev: Allows[Price, Wrapped[Primitive]] = implicitly
  }

  case class Catalog(code: ProductCode, price: Price)
  object Catalog {
    implicit val schema: Schema[Catalog]                = Schema.derived
    val ev: Allows[Catalog, Record[Wrapped[Primitive]]] = implicitly
  }

  // ---------------------------------------------------------------------------
  // Scala 3 opaque types — treated as their underlying type by the macro
  // ---------------------------------------------------------------------------

  opaque type UserId = java.util.UUID
  object UserId {
    def apply(u: java.util.UUID): UserId = u
    // Opaque types use the schema of their underlying type
    implicit val schema: Schema[UserId] = Schema.uuid
    // Opaque type aliases are resolved to their underlying primitive type
    val ev: Allows[UserId, Primitive] = implicitly
  }

  opaque type Discount <: Double = Double
  object Discount {
    def apply(d: Double): Discount        = d
    implicit val schema: Schema[Discount] = Schema.double
    val ev: Allows[Discount, Primitive]   = implicitly
  }

  // Record containing opaque types: fields are Primitive (not Wrapped)
  case class Order(id: UserId, discount: Discount)
  object Order {
    implicit val schema: Schema[Order] = Schema.derived
    // Fields are seen as Primitive because opaque types are unwrapped
    val ev: Allows[Order, Record[Primitive]] = implicitly
  }

  // ---------------------------------------------------------------------------
  // Native Scala 3 union syntax in grammar positions
  // ---------------------------------------------------------------------------

  // Native `A | B` union is treated identically to `A | B` via Allows.|
  val nativeUnion1: Allows[ProductCode, Primitive | Wrapped[Primitive]]     = implicitly
  val nativeUnion2: Allows[Catalog, Record[Primitive | Wrapped[Primitive]]] = implicitly

  def spec: Spec[TestEnvironment, Any] = suite("AllowsWrappedSpec")(
    suite("ZIO Prelude newtypes")(
      test("ProductCode (Newtype[String]) satisfies Wrapped[Primitive]") {
        assertTrue(ProductCode.ev1.ne(null))
      },
      test("ProductCode satisfies Primitive | Wrapped[Primitive]") {
        assertTrue(ProductCode.ev2.ne(null))
      },
      test("Price (Subtype[BigDecimal]) satisfies Wrapped[Primitive]") {
        assertTrue(Price.ev.ne(null))
      },
      test("Catalog (Record[ProductCode, Price]) satisfies Record[Wrapped[Primitive]]") {
        assertTrue(Catalog.ev.ne(null))
      },
      test("ProductCode does NOT satisfy bare Primitive") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.{ Allows }
          import Allows._
          import zio.prelude.Newtype
          type PCode2 = PCode2.Type
          object PCode2 extends Newtype[String] {
            implicit val schema: Schema[PCode2] =
              Schema[String].transform(_.asInstanceOf[PCode2], _.asInstanceOf[String])
          }
          summon[Allows[PCode2, Primitive]]
        """).map(
          assert(_)(
            isLeft(containsString("Wrapped") || containsString("Primitive") || containsString("No given instance"))
          )
        )
      }
    ),
    suite("Scala 3 opaque types (resolved to underlying primitive)")(
      test("UserId (opaque UUID) satisfies Primitive (opaque aliases are transparent to Allows)") {
        assertTrue(UserId.ev.ne(null))
      },
      test("Discount (opaque Double) satisfies Primitive") {
        assertTrue(Discount.ev.ne(null))
      },
      test("Order (Record[UserId, Discount]) satisfies Record[Primitive] (fields unwrapped)") {
        assertTrue(Order.ev.ne(null))
      }
    ),
    suite("Native Scala 3 union syntax in grammar positions")(
      test("ProductCode satisfies Primitive | Wrapped[Primitive] (native union syntax)") {
        assertTrue(nativeUnion1.ne(null))
      },
      test("Catalog satisfies Record[Primitive | Wrapped[Primitive]] (native union syntax)") {
        assertTrue(nativeUnion2.ne(null))
      }
    )
  )
}
