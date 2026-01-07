package golem.runtime.rpc.jvm.internal

import scala.util.Try

/** Extremely small WAVE-ish codec for the CLI-backed JVM testing client. */
private[rpc] object WaveTextCodec {

  def encodeArg(value: Any): Either[String, String] =
    value match {
      case null           => Right("null")
      case s: String      => Right(renderString(s))
      case i: Int         => Right(i.toString)
      case l: Long        => Right(l.toString)
      case d: Double      => Right(d.toString)
      case b: Boolean     => Right(if (b) "true" else "false")
      case _: Unit        => Right("()")
      case opt: Option[?] =>
        opt match {
          case None        => Right("none")
          case Some(inner) => encodeArg(inner).map(v => s"some($v)")
        }
      case p2: Product if p2.productArity == 2 =>
        for {
          a0 <- encodeArg(p2.productElement(0))
          a1 <- encodeArg(p2.productElement(1))
        } yield s"$a0, $a1"
      case p3: Product if p3.productArity == 3 =>
        for {
          a0 <- encodeArg(p3.productElement(0))
          a1 <- encodeArg(p3.productElement(1))
          a2 <- encodeArg(p3.productElement(2))
        } yield s"$a0, $a1, $a2"
      case other =>
        Left(s"Unsupported CLI argument type: ${other.getClass.getName}")
    }

  def parseLastWaveResult(stdout: String): Option[String] = {
    // golem-cli prints:
    // Invocation results in WAVE format:
    //   - <value>
    if (stdout.contains("Empty result.")) return Some("()")
    stdout.linesIterator.toVector.reverse.collectFirst {
      case line if line.trim.startsWith("- ")  => line.trim.stripPrefix("- ").trim
      case line if line.trim.startsWith(" - ") => line.trim.stripPrefix("- ").trim
      case line if line.trim.startsWith(" -")  => line.trim.stripPrefix("-").trim
      case line if line.trim.startsWith("-")   => line.trim.stripPrefix("-").trim
    }
  }

  def decodeInt(wave: String): Either[String, Int] =
    Try(wave.trim.toInt).toEither.left.map(_ => s"Expected int, got: $wave")

  def decodeString(wave: String): Either[String, String] = {
    val t = wave.trim
    if (t.startsWith("\"") && t.endsWith("\"") && t.length >= 2) {
      // Minimal unescape for \" and \\ (good enough for quickstart).
      val inner = t.substring(1, t.length - 1)
      Right(
        inner
          .replace("\\\\", "\\")
          .replace("\\\"", "\"")
      )
    } else Left(s"Expected quoted string, got: $wave")
  }

  def decodeUnit(wave: String): Either[String, Unit] =
    if (wave.trim == "()" || wave.trim == "unit" || wave.trim == "") Right(()) else Left(s"Expected unit, got: $wave")

  def decodeOptionString(wave: String): Either[String, Option[String]] = {
    val t = wave.trim
    if (t == "none") Right(None)
    else if (t.startsWith("some(") && t.endsWith(")")) {
      val inner = t.stripPrefix("some(").stripSuffix(")")
      decodeString(inner).map(Some(_))
    } else {
      // Some golem-cli outputs option-as-null or option-as-string; accept quoted string as Some.
      decodeString(t).map(Some(_))
    }
  }

  /**
   * Best-effort decoding for the CLI-backed JVM testing client.
   *
   * This deliberately avoids depending on JVM generic return types (which can
   * be erased to `Object`), and instead decodes based on the WAVE literal
   * itself.
   */
  def decodeWaveAny(wave: String): Either[String, Any] = {
    val t = wave.trim
    if (t == "none") Right(None)
    else if (t.startsWith("some(") && t.endsWith(")")) {
      val inner = t.stripPrefix("some(").stripSuffix(")")
      decodeWaveAny(inner).map(v => Some(v))
    } else if (t == "()" || t == "unit" || t.isEmpty) Right(())
    else if (t == "true") Right(true)
    else if (t == "false") Right(false)
    else {
      // Try string
      decodeString(t) match {
        case Right(s) => Right(s)
        case Left(_)  =>
          // Try number
          Try(t.toDouble).toEither.left.map(_ => s"Unsupported WAVE result: $wave").map { d =>
            if (d.isWhole && d >= Int.MinValue && d <= Int.MaxValue) d.toInt
            else d
          }
      }
    }
  }

  private def renderString(value: String): String = {
    val escaped =
      value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    "\"" + escaped + "\""
  }
}
