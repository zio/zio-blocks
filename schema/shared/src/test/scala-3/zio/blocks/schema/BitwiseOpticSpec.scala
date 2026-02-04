package zio.blocks.schema

import zio.test._

object BitwiseOpticSpec extends ZIOSpecDefault {

  case class ByteRecord(value: Byte, mask: Byte)
  object ByteRecord extends CompanionOptics[ByteRecord] {
    implicit val schema: Schema[ByteRecord] = Schema.derived
    val value: Lens[ByteRecord, Byte]       = $(_.value)
    val mask: Lens[ByteRecord, Byte]        = $(_.mask)
  }

  case class ShortRecord(value: Short, mask: Short)
  object ShortRecord extends CompanionOptics[ShortRecord] {
    implicit val schema: Schema[ShortRecord] = Schema.derived
    val value: Lens[ShortRecord, Short]      = $(_.value)
    val mask: Lens[ShortRecord, Short]       = $(_.mask)
  }

  case class IntRecord(value: Int, mask: Int, shift: Int)
  object IntRecord extends CompanionOptics[IntRecord] {
    implicit val schema: Schema[IntRecord] = Schema.derived
    val value: Lens[IntRecord, Int]        = $(_.value)
    val mask: Lens[IntRecord, Int]         = $(_.mask)
    val shift: Lens[IntRecord, Int]        = $(_.shift)
  }

  case class LongRecord(value: Long, mask: Long, shift: Long)
  object LongRecord extends CompanionOptics[LongRecord] {
    implicit val schema: Schema[LongRecord] = Schema.derived
    val value: Lens[LongRecord, Long]       = $(_.value)
    val mask: Lens[LongRecord, Long]        = $(_.mask)
    val shift: Lens[LongRecord, Long]       = $(_.shift)
  }

  case class MixedIntegrals(b: Byte, s: Short, i: Int, l: Long)
  object MixedIntegrals extends CompanionOptics[MixedIntegrals] {
    implicit val schema: Schema[MixedIntegrals] = Schema.derived
    val b: Lens[MixedIntegrals, Byte]           = $(_.b)
    val s: Lens[MixedIntegrals, Short]          = $(_.s)
    val i: Lens[MixedIntegrals, Int]            = $(_.i)
    val l: Lens[MixedIntegrals, Long]           = $(_.l)
  }

  type PersonV0 = { val firstName: String; val lastName: String; val age: Int }
  type PersonV1 = { val fullName: String; val age: Int; val score: Long }

  type IntOrLong    = Int | Long
  type ByteOrInt    = Byte | Int
  type AllIntegrals = Byte | Short | Int | Long

  case class UnionHolder(intOrLong: IntOrLong, byteOrInt: ByteOrInt)
  object UnionHolder extends CompanionOptics[UnionHolder] {
    implicit val schema: Schema[UnionHolder]        = Schema.derived
    val intOrLong: Lens[UnionHolder, IntOrLong]     = $(_.intOrLong)
    val byteOrInt: Lens[UnionHolder, ByteOrInt]     = $(_.byteOrInt)
    val intOrLong_int: Optional[UnionHolder, Int]   = $(_.intOrLong.when[Int])
    val intOrLong_long: Optional[UnionHolder, Long] = $(_.intOrLong.when[Long])
  }

  type OldCreditCard    = { type Tag = "CreditCard"; def number: String; def exp: String; def cvv: Int }
  type OldWireTransfer  = { type Tag = "WireTransfer"; def account: String; def routing: Int }
  type OldPaymentMethod = OldCreditCard | OldWireTransfer

  case class Nested(inner: IntRecord)
  object Nested extends CompanionOptics[Nested] {
    implicit val schema: Schema[Nested] = Schema.derived
    val inner: Lens[Nested, IntRecord]  = $(_.inner)
    val innerValue: Lens[Nested, Int]   = $(_.inner.value)
    val innerMask: Lens[Nested, Int]    = $(_.inner.mask)
  }

  def spec = suite("BitwiseOpticSpec")(
    suite("Case class with Byte fields")(
      test("& (AND) with literal") {
        val record = ByteRecord(0x0f.toByte, 0xf0.toByte)
        val expr   = ByteRecord.value & 0x03.toByte
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test("| (OR) with literal") {
        val record = ByteRecord(0x0f.toByte, 0xf0.toByte)
        val expr   = ByteRecord.value | 0xf0.toByte
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test("^ (XOR) with literal") {
        val record = ByteRecord(0xff.toByte, 0x0f.toByte)
        val expr   = ByteRecord.value ^ 0x0f.toByte
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test("<< (left shift) with literal") {
        val record = ByteRecord(0x01.toByte, 0x00.toByte)
        val expr   = ByteRecord.value << 4.toByte
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test(">> (right shift) with literal") {
        val record = ByteRecord(0x10.toByte, 0x00.toByte)
        val expr   = ByteRecord.value >> 2.toByte
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test(">>> (unsigned right shift) with literal") {
        val record = ByteRecord(0x80.toByte, 0x00.toByte)
        val expr   = ByteRecord.value >>> 1.toByte
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      }
    ),
    suite("Case class with Short fields")(
      test("& (AND) with literal") {
        val record = ShortRecord(0x00ff.toShort, 0xff00.toShort)
        val expr   = ShortRecord.value & 0x000f.toShort
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test("| (OR) with literal") {
        val record = ShortRecord(0x00ff.toShort, 0xff00.toShort)
        val expr   = ShortRecord.value | 0xff00.toShort
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test("<< (left shift) with literal") {
        val record = ShortRecord(0x0001.toShort, 0x0000.toShort)
        val expr   = ShortRecord.value << 8.toShort
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      }
    ),
    suite("Case class with Int fields")(
      test("& (AND) with literal") {
        val record = IntRecord(0x0000ffff, 0xffff0000, 4)
        val expr   = IntRecord.value & 0x000000ff
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test("| (OR) with literal") {
        val record = IntRecord(0x0000ffff, 0xffff0000, 4)
        val expr   = IntRecord.value | 0xffff0000
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test("^ (XOR) with literal") {
        val record = IntRecord(0xffffffff, 0x00000000, 4)
        val expr   = IntRecord.value ^ 0x0f0f0f0f
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test("<< (left shift) with literal") {
        val record = IntRecord(1, 0, 4)
        val expr   = IntRecord.value << 16
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test(">> (right shift) with literal") {
        val record = IntRecord(0x00010000, 0, 4)
        val expr   = IntRecord.value >> 8
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test(">>> (unsigned right shift) with literal") {
        val record = IntRecord(0x80000000, 0, 4)
        val expr   = IntRecord.value >>> 1
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      }
    ),
    suite("Case class with Long fields")(
      test("& (AND) with literal") {
        val record = LongRecord(0x00000000ffffffffL, 0xffffffff00000000L, 4L)
        val expr   = LongRecord.value & 0x00000000000000ffL
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test("| (OR) with literal") {
        val record = LongRecord(0x00000000ffffffffL, 0xffffffff00000000L, 4L)
        val expr   = LongRecord.value | 0xffffffff00000000L
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test("^ (XOR) with literal") {
        val record = LongRecord(0xffffffffffffffffL, 0L, 4L)
        val expr   = LongRecord.value ^ 0x0f0f0f0f0f0f0f0fL
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test("<< (left shift) with literal") {
        val record = LongRecord(1L, 0L, 4L)
        val expr   = LongRecord.value << 32L
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test(">> (right shift) with literal") {
        val record = LongRecord(0x0001000000000000L, 0L, 4L)
        val expr   = LongRecord.value >> 16L
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test(">>> (unsigned right shift) with literal") {
        val record = LongRecord(0x8000000000000000L, 0L, 4L)
        val expr   = LongRecord.value >>> 1L
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      }
    ),
    suite("Mixed integral types in one record")(
      test("Byte & with Byte literal") {
        val record = MixedIntegrals(0x0f.toByte, 0x00ff.toShort, 0x0000ffff, 0xffffffffL)
        val expr   = MixedIntegrals.b & 0x03.toByte
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test("Short | with Short literal") {
        val record = MixedIntegrals(0x0f.toByte, 0x00ff.toShort, 0x0000ffff, 0xffffffffL)
        val expr   = MixedIntegrals.s | 0xff00.toShort
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test("Int ^ with Int literal") {
        val record = MixedIntegrals(0x0f.toByte, 0x00ff.toShort, 0x0000ffff, 0xffffffffL)
        val expr   = MixedIntegrals.i ^ 0xffff0000
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test("Long << with Long literal") {
        val record = MixedIntegrals(0x0f.toByte, 0x00ff.toShort, 0x0000ffff, 1L)
        val expr   = MixedIntegrals.l << 32L
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      }
    ),
    suite("Nested structures")(
      test("& on nested Int field") {
        val record = Nested(IntRecord(0x0000ffff, 0xffff0000, 4))
        val expr   = Nested.innerValue & 0x000000ff
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test("| on nested Int field") {
        val record = Nested(IntRecord(0x0000ffff, 0xffff0000, 4))
        val expr   = Nested.innerMask | 0x0000ffff
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test("<< on nested Int field") {
        val record = Nested(IntRecord(1, 0, 4))
        val expr   = Nested.innerValue << 8
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      }
    ),
    suite("Union types with integrals")(
      test("Union Int | Long - access Int via when[Int]") {
        val record = UnionHolder(42, 0xff.toByte)
        val expr   = UnionHolder.intOrLong_int & 0x0f
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight)
      },
      test("Union Int | Long - access Long via when[Long]") {
        val record = UnionHolder(100L, 0x0f.toByte)
        val expr   = UnionHolder.intOrLong_long | 0xffL
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight)
      }
    ),
    suite("All bitwise operators with case class optics")(
      test("unary_~ (bitwise NOT)") {
        val record = IntRecord(0x0f0f0f0f, 0, 0)
        val expr   = ~IntRecord.value
        val result = expr.evalDynamic(record)
        assertTrue(result.isRight && result.exists(_.nonEmpty))
      },
      test("multiple bitwise operations") {
        val record  = IntRecord(0xff, 0x0f, 4)
        val expr1   = IntRecord.value & 0x0f
        val expr2   = IntRecord.mask | 0xf0
        val result1 = expr1.evalDynamic(record)
        val result2 = expr2.evalDynamic(record)
        assertTrue(result1.isRight && result2.isRight)
      }
    )
  )
}
