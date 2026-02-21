package golem

import org.scalatest.funsuite.AnyFunSuite

final class DatetimeCompileSpec extends AnyFunSuite {

  test("Datetime.fromEpochMillis constructs value") {
    val dt: Datetime = Datetime.fromEpochMillis(1700000000000.0)
    assert(dt.epochMillis == 1700000000000.0)
  }

  test("Datetime.fromEpochSeconds converts correctly") {
    val dt = Datetime.fromEpochSeconds(1700000000.0)
    assert(dt.epochMillis == 1700000000000.0)
  }

  test("Datetime.now returns non-zero value") {
    val dt = Datetime.now
    assert(dt.epochMillis > 0.0)
  }

  test("Datetime.afterMillis is in the future") {
    val before = Datetime.now.epochMillis
    val dt     = Datetime.afterMillis(10000.0)
    assert(dt.epochMillis >= before)
  }

  test("Datetime.afterSeconds is in the future") {
    val before = Datetime.now.epochMillis
    val dt     = Datetime.afterSeconds(10.0)
    assert(dt.epochMillis >= before)
  }

  test("DatetimeJs.fromTs creates Datetime") {
    val dt: Datetime = DatetimeJs.fromTs(1700000000000.0)
    assert(dt.epochMillis == 1700000000000.0)
  }

  test("Datetime is a value type (AnyVal)") {
    val _: AnyVal = Datetime.fromEpochMillis(0.0)
    assert(true)
  }

  test("Uuid construction and field access") {
    val u = Uuid(BigInt(123456789L), BigInt(987654321L))
    assert(u.highBits == BigInt(123456789L))
    assert(u.lowBits == BigInt(987654321L))
  }

  test("Uuid equality") {
    val a = Uuid(BigInt(1), BigInt(2))
    val b = Uuid(BigInt(1), BigInt(2))
    val c = Uuid(BigInt(1), BigInt(3))
    assert(a == b)
    assert(a != c)
  }
}
