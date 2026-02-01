package golem.runtime.rpc.jvm.internal

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class WaveTextCodecSpec extends AnyFunSuite with Matchers {
  test("encodeArg renders supported primitive types") {
    WaveTextCodec.encodeArg(null).shouldBe(Right("null"))
    WaveTextCodec.encodeArg("hi").shouldBe(Right("\"hi\""))
    WaveTextCodec.encodeArg(1).shouldBe(Right("1"))
    WaveTextCodec.encodeArg(2L).shouldBe(Right("2"))
    WaveTextCodec.encodeArg(3.5).shouldBe(Right("3.5"))
    WaveTextCodec.encodeArg(true).shouldBe(Right("true"))
    WaveTextCodec.encodeArg(()).shouldBe(Right("()"))
  }

  test("encodeArg handles options and tuples") {
    WaveTextCodec.encodeArg(Option.empty[String]).shouldBe(Right("none"))
    WaveTextCodec.encodeArg(Some("ok")).shouldBe(Right("some(\"ok\")"))
    WaveTextCodec.encodeArg((1, "a")).shouldBe(Right("1, \"a\""))
    WaveTextCodec.encodeArg((1, "a", false)).shouldBe(Right("1, \"a\", false"))
  }

  test("encodeArg rejects unsupported values") {
    WaveTextCodec.encodeArg(BigInt(1)).isLeft.shouldBe(true)
  }

  test("parseLastWaveResult extracts last wave value") {
    val out =
      """Invocation results in WAVE format:
        | - "ok"
        |""".stripMargin

    WaveTextCodec.parseLastWaveResult(out).shouldBe(Some("\"ok\""))
    WaveTextCodec.parseLastWaveResult("Empty result.").shouldBe(Some("()"))
  }

  test("parseLastWaveResult handles alternate dash formats") {
    val out1 =
      """Invocation results in WAVE format:
        |  - ok
        |""".stripMargin
    val out2 =
      """Invocation results in WAVE format:
        | -ok
        |""".stripMargin

    WaveTextCodec.parseLastWaveResult(out1).shouldBe(Some("ok"))
    WaveTextCodec.parseLastWaveResult(out2).shouldBe(Some("ok"))
  }

  test("decodeString and decodeUnit handle expected formats") {
    WaveTextCodec.decodeString("\"hi\"").shouldBe(Right("hi"))
    WaveTextCodec.decodeString("hi").isLeft.shouldBe(true)

    WaveTextCodec.decodeUnit("()").shouldBe(Right(()))
    WaveTextCodec.decodeUnit("unit").shouldBe(Right(()))
    WaveTextCodec.decodeUnit("").shouldBe(Right(()))
    WaveTextCodec.decodeUnit("nope").isLeft.shouldBe(true)
  }

  test("decodeInt handles valid and invalid values") {
    WaveTextCodec.decodeInt("42").shouldBe(Right(42))
    WaveTextCodec.decodeInt("nope").isLeft.shouldBe(true)
  }

  test("decodeOptionString accepts none, some, and quoted values") {
    WaveTextCodec.decodeOptionString("none").shouldBe(Right(None))
    WaveTextCodec.decodeOptionString("some(\"x\")").shouldBe(Right(Some("x")))
    WaveTextCodec.decodeOptionString("\"y\"").shouldBe(Right(Some("y")))
  }

  test("decodeWaveAny handles common literals") {
    WaveTextCodec.decodeWaveAny("none").shouldBe(Right(None))
    WaveTextCodec.decodeWaveAny("some(1)").shouldBe(Right(Some(1)))
    WaveTextCodec.decodeWaveAny("()").shouldBe(Right(()))
    WaveTextCodec.decodeWaveAny("true").shouldBe(Right(true))
    WaveTextCodec.decodeWaveAny("false").shouldBe(Right(false))
    WaveTextCodec.decodeWaveAny("\"ok\"").shouldBe(Right("ok"))
    WaveTextCodec.decodeWaveAny("12").shouldBe(Right(12))
    WaveTextCodec.decodeWaveAny("12.5").shouldBe(Right(12.5))
  }

  test("decodeWaveAny reports unsupported values") {
    val result = WaveTextCodec.decodeWaveAny("not-a-number")
    result.isLeft.shouldBe(true)
  }
}
