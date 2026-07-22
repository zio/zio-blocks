/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.html

/**
 * Typeclass for values that can be safely interpolated into JavaScript via
 * `js"..."`.
 *
 * This is a '''templating''' typeclass, not a code generator. It converts Scala
 * values into JavaScript '''expressions''' (value literals). Every JavaScript
 * expression is also a valid statement, so `ToJs` output works in all template
 * contexts: inline event handlers, script content, and variable assignments.
 * For JavaScript control flow (`if`/`for`/`while`), use `Js("...")` directly.
 *
 * Instance behavior:
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
    private[this] var _codec: zio.blocks.schema.json.JsonCodec[A] = null
    private[this] def codec: zio.blocks.schema.json.JsonCodec[A]  = {
      if (_codec == null) _codec = schema.derive(zio.blocks.schema.json.JsonFormat)
      _codec
    }
    def toJs(a: A): String = {
      val json = codec.encodeToString(a)
      json.replace("<", "\\u003c").replace(">", "\\u003e")
    }
  }
}
