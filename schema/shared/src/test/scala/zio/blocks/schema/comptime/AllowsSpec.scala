package zio.blocks.schema.comptime

import zio.blocks.schema.{DynamicValue, Schema, SchemaBaseSpec}
import zio.blocks.schema.Schema._ // bring primitive and collection schemas into implicit scope
import zio.blocks.chunk.Chunk
import zio.test._
// Import grammar nodes individually to avoid Allows.Map shadowing scala.collection.immutable.Map
import Allows.{Primitive, Record, Variant, Sequence, Optional, Dynamic, Self, `|`}
import Allows.{Map => AMap} // Allows.Map grammar node, distinct from scala Map type

// ---------------------------------------------------------------------------
// Shared fixture types (cross-version)
// ---------------------------------------------------------------------------

object AllowsFixtures {

  // 4.1 All primitives
  case class AllPrimitives(
    unit: Unit,
    boolean: Boolean,
    byte: Byte,
    short: Short,
    int: Int,
    long: Long,
    float: Float,
    double: Double,
    char: Char,
    str: String,
    bigInt: BigInt,
    bigDecimal: BigDecimal,
    uuid: java.util.UUID,
    currency: java.util.Currency,
    instant: java.time.Instant,
    localDate: java.time.LocalDate,
    localDateTime: java.time.LocalDateTime,
    localTime: java.time.LocalTime,
    zonedDateTime: java.time.ZonedDateTime,
    offsetDateTime: java.time.OffsetDateTime,
    offsetTime: java.time.OffsetTime,
    duration: java.time.Duration,
    period: java.time.Period,
    year: java.time.Year,
    yearMonth: java.time.YearMonth,
    monthDay: java.time.MonthDay,
    month: java.time.Month,
    dayOfWeek: java.time.DayOfWeek,
    zoneId: java.time.ZoneId,
    zoneOffset: java.time.ZoneOffset
  )
  object AllPrimitives {
    implicit val schema: Schema[AllPrimitives] = Schema.derived
  }

  // Zero-field record (case object) — companion is auto-generated, put schema at outer level
  case object EmptyCaseObj
  implicit val emptyCaseObjSchema: Schema[EmptyCaseObj.type] = Schema.derived

  // 4.2 Optional fields
  case class WithOptionalPrimitive(id: Int, name: Option[String])
  object WithOptionalPrimitive { implicit val schema: Schema[WithOptionalPrimitive] = Schema.derived }

  case class WithOptionalRecord(id: Int, address: Option[Address])
  object WithOptionalRecord { implicit val schema: Schema[WithOptionalRecord] = Schema.derived }

  case class NestedOption(x: Option[Option[Int]])
  object NestedOption { implicit val schema: Schema[NestedOption] = Schema.derived }

  // 4.3 Sequence fields
  case class WithSeqPrimitive(ids: List[Int], names: Vector[String])
  object WithSeqPrimitive { implicit val schema: Schema[WithSeqPrimitive] = Schema.derived }

  case class WithChunk(data: Chunk[String])
  object WithChunk { implicit val schema: Schema[WithChunk] = Schema.derived }

  case class WithSeqSeq(matrix: List[List[Int]])
  object WithSeqSeq { implicit val schema: Schema[WithSeqSeq] = Schema.derived }

  // 4.4 Map fields
  case class WithStringMap(meta: scala.collection.immutable.Map[String, Int])
  object WithStringMap { implicit val schema: Schema[WithStringMap] = Schema.derived }

  case class WithStringMapRecord(meta: scala.collection.immutable.Map[String, Address])
  object WithStringMapRecord { implicit val schema: Schema[WithStringMapRecord] = Schema.derived }

  case class WithIntMap(counts: scala.collection.immutable.Map[Int, String])
  object WithIntMap { implicit val schema: Schema[WithIntMap] = Schema.derived }

  // 4.5 Nested records
  case class Address(street: String, city: String, zip: String)
  object Address { implicit val schema: Schema[Address] = Schema.derived }

  case class Person(name: String, age: Int, address: Address)
  object Person { implicit val schema: Schema[Person] = Schema.derived }

  // 4.6 Variant — flat cases
  sealed trait Shape
  case class Circle(radius: Double)                   extends Shape
  case class Rectangle(width: Double, height: Double) extends Shape
  case object Point                                   extends Shape
  object Shape { implicit val schema: Schema[Shape] = Schema.derived }

  // 4.7 Variant — cases with sequences
  sealed trait Event
  case class UserCreated(id: java.util.UUID, name: String)       extends Event
  case class TagsUpdated(id: java.util.UUID, tags: List[String]) extends Event
  object Event { implicit val schema: Schema[Event] = Schema.derived }

  // 4.8 Variant — nested variant cases
  sealed trait Outer
  sealed trait Inner            extends Outer
  case class InnerA(x: Int)     extends Inner
  case class InnerB(y: String)  extends Inner
  case class OuterC(z: Boolean) extends Outer
  object Outer { implicit val schema: Schema[Outer] = Schema.derived }

  // 4.10 Dynamic
  case class WithDynamic(name: String, payload: DynamicValue)
  object WithDynamic { implicit val schema: Schema[WithDynamic] = Schema.derived }

  // 4.11 Recursive
  case class TreeNode(value: Int, children: List[TreeNode])
  object TreeNode { implicit val schema: Schema[TreeNode] = Schema.derived }

  case class LinkedList(value: String, next: Option[LinkedList])
  object LinkedList { implicit val schema: Schema[LinkedList] = Schema.derived }

  case class Category(name: String, subcategories: List[Category])
  object Category { implicit val schema: Schema[Category] = Schema.derived }

  // 4.13 Realistic types
  case class UserRow(id: java.util.UUID, name: String, age: Int, email: Option[String])
  object UserRow { implicit val schema: Schema[UserRow] = Schema.derived }

  sealed trait DomainEvent
  case class AccountOpened(id: java.util.UUID, owner: String)              extends DomainEvent
  case class FundsDeposited(accountId: java.util.UUID, amount: BigDecimal) extends DomainEvent
  case class AccountClosed(id: java.util.UUID)                             extends DomainEvent
  object DomainEvent { implicit val schema: Schema[DomainEvent] = Schema.derived }

  case class JustARecord(id: java.util.UUID, value: Int)
  object JustARecord { implicit val schema: Schema[JustARecord] = Schema.derived }

  case class BadNode(name: String, extra: DynamicValue, children: List[BadNode])
  object BadNode { implicit val schema: Schema[BadNode] = Schema.derived }

  case class Order(id: java.util.UUID, amount: BigDecimal)
  object Order { implicit val schema: Schema[Order] = Schema.derived }

  case class WithSeqRecord(orders: List[Order])
  object WithSeqRecord { implicit val schema: Schema[WithSeqRecord] = Schema.derived }

  case class OrderRow(id: java.util.UUID, customer: Person, amount: BigDecimal)
  object OrderRow { implicit val schema: Schema[OrderRow] = Schema.derived }
}

// ---------------------------------------------------------------------------
// Positive tests (positive evidence derivation must compile)
// ---------------------------------------------------------------------------

object AllowsSpec extends SchemaBaseSpec {
  import AllowsFixtures._

  // 5.1 Primitive
  private val primInt: Allows[Int, Primitive]                   = implicitly
  private val primString: Allows[String, Primitive]             = implicitly
  private val primUUID: Allows[java.util.UUID, Primitive]       = implicitly
  private val primInstant: Allows[java.time.Instant, Primitive] = implicitly
  private val primBigDec: Allows[BigDecimal, Primitive]         = implicitly
  private val primBoolean: Allows[Boolean, Primitive]           = implicitly
  private val primByte: Allows[Byte, Primitive]                 = implicitly
  private val primShort: Allows[Short, Primitive]               = implicitly
  private val primLong: Allows[Long, Primitive]                 = implicitly
  private val primFloat: Allows[Float, Primitive]               = implicitly
  private val primDouble: Allows[Double, Primitive]             = implicitly
  private val primChar: Allows[Char, Primitive]                 = implicitly
  private val primUnit: Allows[Unit, Primitive]                 = implicitly
  private val primBigInt: Allows[BigInt, Primitive]             = implicitly
  private val primCurr: Allows[java.util.Currency, Primitive]   = implicitly

  // 5.2 Record
  private val recAll: Allows[AllPrimitives, Record[Primitive]]                                   = implicitly
  private val recSing: Allows[EmptyCaseObj.type, Record[Primitive]]                              = implicitly // vacuous
  private val recOpt: Allows[WithOptionalPrimitive, Record[`|`[Primitive, Optional[Primitive]]]] = implicitly
  private val recSeq: Allows[WithSeqPrimitive, Record[`|`[Primitive, Sequence[Primitive]]]]      = implicitly
  private val recMap: Allows[WithStringMap, Record[`|`[Primitive, AMap[Primitive, Primitive]]]]  = implicitly
  private val recUser: Allows[UserRow, Record[`|`[Primitive, Optional[Primitive]]]]              = implicitly
  private val recDyn2: Allows[WithDynamic, Record[`|`[Primitive, Dynamic]]]                      = implicitly

  // 5.3 Variant
  private val varShape1: Allows[Shape, Variant[Record[Primitive]]]                           = implicitly
  private val varEvent1: Allows[Event, Variant[Record[`|`[Primitive, Sequence[Primitive]]]]] = implicitly
  private val varDom: Allows[DomainEvent, Variant[Record[Primitive]]]                        = implicitly
  // Unused branch in union is fine
  private val varShape2: Allows[Shape, Variant[`|`[Record[Primitive], Primitive]]] = implicitly

  // 5.4 Sequence
  private val seqInt: Allows[List[Int], Sequence[Primitive]]                  = implicitly
  private val seqStr: Allows[Vector[String], Sequence[Primitive]]             = implicitly
  private val seqAddr: Allows[List[Address], Sequence[Record[Primitive]]]     = implicitly
  private val seqNest: Allows[List[List[Int]], Sequence[Sequence[Primitive]]] = implicitly
  private val seqChk: Allows[Chunk[String], Sequence[Primitive]]              = implicitly
  private val seqSet: Allows[Set[Int], Sequence[Primitive]]                   = implicitly

  // 5.5 Map
  private val mapSI: Allows[scala.collection.immutable.Map[String, Int], AMap[Primitive, Primitive]]             = implicitly
  private val mapSA: Allows[scala.collection.immutable.Map[String, Address], AMap[Primitive, Record[Primitive]]] =
    implicitly
  private val mapIS: Allows[scala.collection.immutable.Map[Int, List[String]], AMap[Primitive, Sequence[Primitive]]] =
    implicitly

  // 5.6 Optional
  private val optInt: Allows[Option[Int], Optional[Primitive]]                 = implicitly
  private val optAddr: Allows[Option[Address], Optional[Record[Primitive]]]    = implicitly
  private val optSeq: Allows[Option[List[Int]], Optional[Sequence[Primitive]]] = implicitly

  // 5.8 Dynamic
  private val dynDV: Allows[DynamicValue, Dynamic]                         = implicitly
  private val dynRec: Allows[WithDynamic, Record[`|`[Primitive, Dynamic]]] = implicitly

  // 5.9 Recursive
  private val recTree: Allows[TreeNode, Record[`|`[Primitive, Sequence[Self]]]]   = implicitly
  private val recList: Allows[LinkedList, Record[`|`[Primitive, Optional[Self]]]] = implicitly
  private val recCat: Allows[Category, Record[`|`[Primitive, Sequence[Self]]]]    = implicitly
  // Non-recursive types with no sequence fields still compile with Sequence[Self] grammar
  // (AllPrimitives has no Sequence fields, so Sequence[Self] branch is never needed)
  private val recAllVac: Allows[AllPrimitives, Record[`|`[Primitive, Sequence[Self]]]] = implicitly

  // 5.10 Union grammar — unused branches are fine
  private val unionAll: Allows[AllPrimitives, Record[`|`[Primitive, Optional[Primitive]]]] = implicitly
  private val unionShp: Allows[Shape, Variant[`|`[Record[Primitive], Primitive]]]          = implicitly
  private val unionSeq
    : Allows[WithSeqPrimitive, Record[`|`[Primitive, `|`[Sequence[Primitive], AMap[Primitive, Primitive]]]]] =
    implicitly

  def spec: Spec[TestEnvironment, Any] = suite("AllowsSpec")(
    suite("Primitive")(
      test("Int")(assertTrue(primInt.ne(null))),
      test("String")(assertTrue(primString.ne(null))),
      test("UUID")(assertTrue(primUUID.ne(null))),
      test("Instant")(assertTrue(primInstant.ne(null))),
      test("BigDecimal")(assertTrue(primBigDec.ne(null))),
      test("Boolean")(assertTrue(primBoolean.ne(null))),
      test("Byte")(assertTrue(primByte.ne(null))),
      test("Short")(assertTrue(primShort.ne(null))),
      test("Long")(assertTrue(primLong.ne(null))),
      test("Float")(assertTrue(primFloat.ne(null))),
      test("Double")(assertTrue(primDouble.ne(null))),
      test("Char")(assertTrue(primChar.ne(null))),
      test("Unit")(assertTrue(primUnit.ne(null))),
      test("BigInt")(assertTrue(primBigInt.ne(null))),
      test("Currency")(assertTrue(primCurr.ne(null)))
    ),
    suite("Record")(
      test("AllPrimitives satisfies Record[Primitive]")(assertTrue(recAll.ne(null))),
      test("EmptyCaseObj satisfies Record[Primitive] (vacuous)")(assertTrue(recSing.ne(null))),
      test("WithOptionalPrimitive satisfies Record[Primitive | Optional[Primitive]]") {
        assertTrue(recOpt.ne(null))
      },
      test("WithSeqPrimitive satisfies Record[Primitive | Sequence[Primitive]]") {
        assertTrue(recSeq.ne(null))
      },
      test("WithStringMap satisfies Record[Primitive | AMap[Primitive,Primitive]]") {
        assertTrue(recMap.ne(null))
      },
      test("UserRow satisfies Record[Primitive | Optional[Primitive]]")(assertTrue(recUser.ne(null))),
      test("WithDynamic satisfies Record[Primitive | Dynamic]")(assertTrue(recDyn2.ne(null)))
    ),
    suite("Variant")(
      test("Shape satisfies Variant[Record[Primitive]]")(assertTrue(varShape1.ne(null))),
      test("Event satisfies Variant[Record[Primitive | Sequence[Primitive]]]")(assertTrue(varEvent1.ne(null))),
      test("DomainEvent satisfies Variant[Record[Primitive]]")(assertTrue(varDom.ne(null))),
      test("Shape satisfies Variant[Record[Primitive] | Primitive] (unused branch ok)") {
        assertTrue(varShape2.ne(null))
      }
    ),
    suite("Sequence")(
      test("List[Int] satisfies Sequence[Primitive]")(assertTrue(seqInt.ne(null))),
      test("Vector[String] satisfies Sequence[Primitive]")(assertTrue(seqStr.ne(null))),
      test("List[Address] satisfies Sequence[Record[Primitive]]")(assertTrue(seqAddr.ne(null))),
      test("List[List[Int]] satisfies Sequence[Sequence[Primitive]]")(assertTrue(seqNest.ne(null))),
      test("Chunk[String] satisfies Sequence[Primitive]")(assertTrue(seqChk.ne(null))),
      test("Set[Int] satisfies Sequence[Primitive]")(assertTrue(seqSet.ne(null)))
    ),
    suite("Map")(
      test("Map[String,Int] satisfies Map[Primitive,Primitive]")(assertTrue(mapSI.ne(null))),
      test("Map[String,Address] satisfies Map[Primitive,Record[Primitive]]")(assertTrue(mapSA.ne(null))),
      test("Map[Int,List[String]] satisfies Map[Primitive,Sequence[Primitive]]")(assertTrue(mapIS.ne(null)))
    ),
    suite("Optional")(
      test("Option[Int] satisfies Optional[Primitive]")(assertTrue(optInt.ne(null))),
      test("Option[Address] satisfies Optional[Record[Primitive]]")(assertTrue(optAddr.ne(null))),
      test("Option[List[Int]] satisfies Optional[Sequence[Primitive]]")(assertTrue(optSeq.ne(null)))
    ),
    suite("Dynamic")(
      test("DynamicValue satisfies Dynamic")(assertTrue(dynDV.ne(null))),
      test("WithDynamic satisfies Record[Primitive | Dynamic]")(assertTrue(dynRec.ne(null)))
    ),
    suite("Recursive (Self)")(
      test("TreeNode satisfies Record[Primitive | Sequence[Self]]")(assertTrue(recTree.ne(null))),
      test("LinkedList satisfies Record[Primitive | Optional[Self]]")(assertTrue(recList.ne(null))),
      test("Category satisfies Record[Primitive | Sequence[Self]]")(assertTrue(recCat.ne(null))),
      test("AllPrimitives satisfies Record[Primitive | Sequence[Self]] (no seq fields — vacuous)") {
        assertTrue(recAllVac.ne(null))
      }
    ),
    suite("Union grammar (unused branches are fine)")(
      test("AllPrimitives satisfies Record[Primitive | Optional[Primitive]]")(assertTrue(unionAll.ne(null))),
      test("Shape satisfies Variant[Record[Primitive] | Primitive]")(assertTrue(unionShp.ne(null))),
      test("WithSeqPrimitive satisfies Record[Primitive | Sequence[...] | Map[...]]") {
        assertTrue(unionSeq.ne(null))
      }
    ),
    suite("Composition: realistic library API")(
      test("UserRow compiles in insert[Record[Primitive|Optional[Primitive]]] context") {
        def insert[A: Schema](v: A)(implicit c: Allows[A, Record[`|`[Primitive, Optional[Primitive]]]]): String = "ok"
        // Use a nil UUID to avoid java.security.SecureRandom (unavailable in Scala.js)
        val nilUUID = new java.util.UUID(0L, 0L)
        assertTrue(insert(UserRow(nilUUID, "Alice", 30, Some("a@b.com"))) == "ok")
      },
      test("DomainEvent compiles in publish[Variant[Record[Primitive]]] context") {
        def publish[A: Schema](e: A)(implicit c: Allows[A, Variant[Record[Primitive]]]): String = "ok"
        implicit val ds: Schema[DomainEvent]                                                    = DomainEvent.schema
        val nilUUID                                                                             = new java.util.UUID(0L, 0L)
        val event: DomainEvent                                                                  = AccountOpened(nilUUID, "Alice")
        assertTrue(publish(event) == "ok")
      }
    )
  )
}
