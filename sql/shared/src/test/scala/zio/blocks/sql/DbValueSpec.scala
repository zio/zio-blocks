package zio.blocks.sql

import zio.test.*
import java.time.{Duration, Instant, LocalDate, LocalDateTime, LocalTime}
import java.util.UUID

object DbValueSpec extends ZIOSpecDefault {
  def spec = suite("DbValueSpec")(
    test("DbNull is a case object") {
      assertTrue(DbValue.DbNull == DbValue.DbNull)
    },
    test("DbInt creation and extraction") {
      val v = DbValue.DbInt(42)
      assertTrue(v.value == 42)
    },
    test("DbLong creation and extraction") {
      val v = DbValue.DbLong(9999999999L)
      assertTrue(v.value == 9999999999L)
    },
    test("DbDouble creation and extraction") {
      val v = DbValue.DbDouble(3.14)
      assertTrue(v.value == 3.14)
    },
    test("DbFloat creation and extraction") {
      val v = DbValue.DbFloat(2.71f)
      assertTrue(v.value == 2.71f)
    },
    test("DbBoolean creation and extraction") {
      val v = DbValue.DbBoolean(true)
      assertTrue(v.value == true)
    },
    test("DbString creation and extraction") {
      val v = DbValue.DbString("hello")
      assertTrue(v.value == "hello")
    },
    test("DbBigDecimal creation and extraction") {
      val bd = scala.BigDecimal("123.45")
      val v  = DbValue.DbBigDecimal(bd)
      assertTrue(v.value == bd)
    },
    test("DbBytes creation and extraction") {
      val bytes = Array[Byte](1, 2, 3)
      val v     = DbValue.DbBytes(bytes)
      assertTrue(v.value.sameElements(bytes))
    },
    test("DbShort creation and extraction") {
      val v = DbValue.DbShort(100.toShort)
      assertTrue(v.value == 100.toShort)
    },
    test("DbByte creation and extraction") {
      val v = DbValue.DbByte(50.toByte)
      assertTrue(v.value == 50.toByte)
    },
    test("DbChar creation and extraction") {
      val v = DbValue.DbChar('A')
      assertTrue(v.value == 'A')
    },
    test("DbLocalDate creation and extraction") {
      val ld = LocalDate.of(2024, 3, 14)
      val v  = DbValue.DbLocalDate(ld)
      assertTrue(v.value == ld)
    },
    test("DbLocalDateTime creation and extraction") {
      val ldt = LocalDateTime.of(2024, 3, 14, 12, 0)
      val v   = DbValue.DbLocalDateTime(ldt)
      assertTrue(v.value == ldt)
    },
    test("DbLocalTime creation and extraction") {
      val lt = LocalTime.of(12, 30, 45)
      val v  = DbValue.DbLocalTime(lt)
      assertTrue(v.value == lt)
    },
    test("DbInstant creation and extraction") {
      val inst = Instant.now()
      val v    = DbValue.DbInstant(inst)
      assertTrue(v.value == inst)
    },
    test("DbDuration creation and extraction") {
      val dur = Duration.ofHours(2)
      val v   = DbValue.DbDuration(dur)
      assertTrue(v.value == dur)
    },
    test("DbUUID creation and extraction") {
      val uuid = UUID.randomUUID()
      val v    = DbValue.DbUUID(uuid)
      assertTrue(v.value == uuid)
    },
    test("all DbValue types are case classes or case objects") {
      assertTrue(
        DbValue.DbNull.isInstanceOf[DbValue] &&
          DbValue.DbInt(1).isInstanceOf[DbValue] &&
          DbValue.DbString("x").isInstanceOf[DbValue]
      )
    }
  )
}
