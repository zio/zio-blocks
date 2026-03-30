package zio.blocks.template

/**
 * Typeclass for values that can be safely interpolated into JavaScript via
 * `js"..."` .
 *
 * Escaping rules:
 *   - `String` is quoted with `"` and JS-escaped (including `</script>`
 *     protection)
 *   - Numeric types render as unquoted literals
 *   - `Boolean` renders as `true`/`false`
 *   - `Option` renders as the value or `null`
 *   - `List`/`Map` render as JSON arrays/objects
 *   - Types with a [[zio.blocks.schema.Schema]] auto-derive via JSON
 *     serialization
 */

trait ToJs[-A] {
  def toJs(a: A): String
}

object ToJs extends LowPriorityToJs {

  def apply[A](implicit ev: ToJs[A]): ToJs[A] = ev

  implicit val stringToJs: ToJs[String] = new ToJs[String] {
    def toJs(a: String): String = "\"" + Escape.jsString(a) + "\""
  }

  implicit val intToJs: ToJs[Int] = new ToJs[Int] {
    def toJs(a: Int): String = a.toString
  }

  implicit val longToJs: ToJs[Long] = new ToJs[Long] {
    def toJs(a: Long): String = a.toString
  }

  implicit val doubleToJs: ToJs[Double] = new ToJs[Double] {
    def toJs(a: Double): String =
      if (a.isNaN) "NaN"
      else if (a.isInfinity) {
        if (a > 0) "Infinity" else "-Infinity"
      } else a.toString
  }

  implicit val floatToJs: ToJs[Float] = new ToJs[Float] {
    def toJs(a: Float): String =
      if (a.isNaN) "NaN"
      else if (a.isInfinity) {
        if (a > 0) "Infinity" else "-Infinity"
      } else a.toString
  }

  implicit val booleanToJs: ToJs[Boolean] = new ToJs[Boolean] {
    def toJs(a: Boolean): String = if (a) "true" else "false"
  }

  implicit val jsToJs: ToJs[Js] = new ToJs[Js] {
    def toJs(a: Js): String = a.value
  }

  implicit val unitToJs: ToJs[Unit] = new ToJs[Unit] {
    def toJs(a: Unit): String = "undefined"
  }

  implicit def optionToJs[A](implicit ev: ToJs[A]): ToJs[Option[A]] = new ToJs[Option[A]] {
    def toJs(a: Option[A]): String = a match {
      case Some(v) => ev.toJs(v)
      case None    => "null"
    }
  }

  implicit def listToJs[A](implicit ev: ToJs[A]): ToJs[List[A]] = new ToJs[List[A]] {
    def toJs(a: List[A]): String = {
      val sb = new java.lang.StringBuilder
      sb.append('[')
      var rem = a
      var sep = false
      while (rem.nonEmpty) {
        if (sep) sb.append(',')
        sb.append(ev.toJs(rem.head))
        rem = rem.tail
        sep = true
      }
      sb.append(']')
      sb.toString
    }
  }

  implicit def mapToJs[V](implicit ev: ToJs[V]): ToJs[Map[String, V]] = new ToJs[Map[String, V]] {
    def toJs(a: Map[String, V]): String = {
      val sb = new java.lang.StringBuilder
      sb.append('{')
      val it  = a.iterator
      var sep = false
      while (it.hasNext) {
        val (k, v) = it.next()
        if (sep) sb.append(',')
        sb.append('"')
        sb.append(Escape.jsString(k))
        sb.append("\":")
        sb.append(ev.toJs(v))
        sep = true
      }
      sb.append('}')
      sb.toString
    }
  }
}

trait LowPriorityToJs {
  implicit def fromSchema[A](implicit schema: zio.blocks.schema.Schema[A]): ToJs[A] = new ToJs[A] {
    private[this] var _codec: zio.blocks.schema.json.JsonCodec[A] = _
    private[this] def codec: zio.blocks.schema.json.JsonCodec[A]  = {
      if (_codec == null) _codec = schema.derive(zio.blocks.schema.json.JsonFormat)
      _codec
    }
    def toJs(a: A): String = codec.encodeToString(a)
  }
}
