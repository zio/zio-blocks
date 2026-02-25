package golem

import zio.test._

object DatetimeSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment, Any] =
    suite("DatetimeSpec")(
      test("fromEpochMillis preserves millis") {
        val dt = Datetime.fromEpochMillis(1234.5)
        assertTrue(dt.epochMillis == 1234.5)
      },
      test("fromEpochSeconds scales to millis") {
        val dt = Datetime.fromEpochSeconds(1.5)
        assertTrue(dt.epochMillis == 1500.0)
      },
      test("afterMillis returns time after now") {
        val start = System.currentTimeMillis().toDouble
        val dt    = Datetime.afterMillis(250.0)
        assertTrue(dt.epochMillis >= start + 200.0)
      },
      test("afterSeconds delegates to afterMillis") {
        val start = System.currentTimeMillis().toDouble
        val dt    = Datetime.afterSeconds(0.5)
        assertTrue(dt.epochMillis >= start + 400.0)
      },
      test("now returns current-ish time") {
        val start = System.currentTimeMillis().toDouble
        val dt    = Datetime.now
        assertTrue(dt.epochMillis >= start - 5.0)
      }
    )
}
