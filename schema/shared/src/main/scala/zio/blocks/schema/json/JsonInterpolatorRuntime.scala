package zio.blocks.schema.json

import zio.blocks.schema.json.Json

object JsonInterpolatorRuntime {
  def interpolate(parts: Seq[String], args: Seq[Any]): Json = {
    val sb = new StringBuilder
    val pi = parts.iterator
    val ai = args.iterator

    if (pi.hasNext) {
      sb.append(pi.next())
      while (ai.hasNext) {
        val arg     = ai.next()
        val jsonArg = arg match {
          case j: Json       => j
          case s: String     => Json.String(s)
          case i: Int        => Json.Number(i.toString)
          case l: Long       => Json.Number(l.toString)
          case d: Double     => Json.Number(d.toString)
          case f: Float      => Json.Number(f.toString)
          case b: Boolean    => Json.Boolean(b)
          case b: BigInt     => Json.Number(b.toString)
          case b: BigDecimal => Json.Number(b.toString)
          case null          => Json.Null
          case x             => Json.String(x.toString)
        }
        sb.append(Json.encode(jsonArg))
        if (pi.hasNext) sb.append(pi.next())
      }
    }

    Json.parse(sb.toString()) match {
      case Right(json) => json
      case Left(err)   => throw new IllegalArgumentException(s"Invalid JSON: ${err.message}")
    }
  }
}
