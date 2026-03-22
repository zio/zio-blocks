package golem.wasi

import zio.test._

object ConfigCompileSpec extends ZIOSpecDefault {
  import Config._

  private val errors: List[ConfigError] = List(
    ConfigError.Upstream("upstream error"),
    ConfigError.Io("io error")
  )

  private def describeError(e: ConfigError): String = e match {
    case ConfigError.Upstream(msg) => s"upstream($msg)"
    case ConfigError.Io(msg)       => s"io($msg)"
  }

  def spec = suite("ConfigCompileSpec")(
    test("ConfigError exhaustive match") {
      errors.foreach(e => assert(describeError(e).nonEmpty))
      assertTrue(true)
    },
    test("ConfigError field access") {
      assertTrue(
        errors.head.asInstanceOf[ConfigError.Upstream].message == "upstream error",
        errors(1).asInstanceOf[ConfigError.Io].message == "io error"
      )
    },
    test("Either result type usage") {
      val result: Either[ConfigError, Option[String]]         = Right(Some("value"))
      val allResult: Either[ConfigError, Map[String, String]] = Right(Map("k" -> "v"))

      result match {
        case Right(Some(v)) =>
          allResult match {
            case Right(m) =>
              assertTrue(
                v == "value",
                m.size == 1
              )
            case Left(_) => assertTrue(false)
          }
        case Right(None)                     => assertTrue(false)
        case Left(ConfigError.Upstream(msg)) => assertTrue(false)
        case Left(ConfigError.Io(msg))       => assertTrue(false)
      }
    }
  )
}
