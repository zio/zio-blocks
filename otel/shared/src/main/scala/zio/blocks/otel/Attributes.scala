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

package zio.blocks.otel

/**
 * An immutable collection of typed key-value attribute pairs.
 *
 * Backed by parallel arrays of keys and values for efficient storage of small
 * attribute sets (typical spans have <10 attributes).
 *
 * Attributes are accessed via typed keys (AttributeKey[A]) for compile-time
 * type safety.
 */
final class Attributes private (
  private[otel] val keys: Array[String],
  private[otel] val values: Array[AttributeValue],
  private[otel] val len: Int
) {

  /**
   * Number of attributes in this collection.
   */
  def size: Int = len

  /**
   * True if this collection contains no attributes.
   */
  def isEmpty: Boolean = len == 0

  /**
   * Retrieves a typed attribute by key, returning None if not found.
   *
   * @tparam A
   *   The expected type of the value
   * @param key
   *   The typed key to look up
   * @return
   *   Some(value) if found, None otherwise
   */
  def get[A](key: AttributeKey[A]): Option[A] = {
    var i = len - 1
    while (i >= 0) {
      if (keys(i) == key.name) {
        return Some(valueToType(values(i)).asInstanceOf[A])
      }
      i -= 1
    }
    None
  }

  /**
   * Invokes a function for each attribute.
   */
  def foreach(f: (String, AttributeValue) => Unit): Unit = {
    var i = 0
    while (i < len) {
      f(keys(i), values(i))
      i += 1
    }
  }

  /**
   * Merges this Attributes with another, with values from `other` taking
   * precedence on conflicts.
   */
  def ++(other: Attributes): Attributes =
    if (other.isEmpty) {
      this
    } else if (this.isEmpty) {
      other
    } else {
      val newKeys = new Array[String](len + other.len)
      val newVals = new Array[AttributeValue](len + other.len)
      System.arraycopy(keys, 0, newKeys, 0, len)
      System.arraycopy(values, 0, newVals, 0, len)
      System.arraycopy(other.keys, 0, newKeys, len, other.len)
      System.arraycopy(other.values, 0, newVals, len, other.len)
      new Attributes(newKeys, newVals, len + other.len)
    }

  /**
   * Converts this Attributes to a Map with string keys and AttributeValue
   * values.
   */
  def toMap: Map[String, AttributeValue] = {
    var map = Map.empty[String, AttributeValue]
    var i   = 0
    while (i < len) {
      map = map + ((keys(i), values(i)))
      i += 1
    }
    map
  }

  /**
   * Internal: extracts the typed value from an AttributeValue.
   */
  private def valueToType(v: AttributeValue): Any = v match {
    case AttributeValue.StringValue(s)       => s
    case AttributeValue.BooleanValue(b)      => b
    case AttributeValue.LongValue(l)         => l
    case AttributeValue.DoubleValue(d)       => d
    case AttributeValue.StringSeqValue(seq)  => seq
    case AttributeValue.LongSeqValue(seq)    => seq
    case AttributeValue.DoubleSeqValue(seq)  => seq
    case AttributeValue.BooleanSeqValue(seq) => seq
  }

  override def hashCode(): Int = {
    var h = 1
    var i = 0
    while (i < len) {
      h = 31 * h + keys(i).hashCode
      h = 31 * h + values(i).hashCode
      i += 1
    }
    h
  }

  override def equals(obj: Any): Boolean = obj match {
    case other: Attributes =>
      if (len != other.len) false
      else {
        var i = 0
        while (i < len) {
          var found = false
          var j     = 0
          while (j < other.len && !found) {
            if (keys(i) == other.keys(j) && values(i) == other.values(j)) found = true
            j += 1
          }
          if (!found) return false
          i += 1
        }
        true
      }
    case _ => false
  }
}

object Attributes {

  /**
   * An empty Attributes collection.
   */
  val empty: Attributes = new Attributes(Array.empty, Array.empty, 0)

  /**
   * Creates an Attributes collection with a single typed attribute.
   */
  def of[A](key: AttributeKey[A], value: A): Attributes = {
    val attrValue = typeToValue(value)
    new Attributes(Array(key.name), Array(attrValue), 1)
  }

  /**
   * Returns a mutable builder for constructing Attributes incrementally.
   */
  def builder: AttributesBuilder =
    new AttributesBuilder()

  /**
   * Predefined attribute key for service name.
   */
  val ServiceName: AttributeKey[String] = AttributeKey.string("service.name")

  /**
   * Predefined attribute key for service version.
   */
  val ServiceVersion: AttributeKey[String] = AttributeKey.string("service.version")

  /**
   * Internal: converts a typed value to an AttributeValue.
   */
  private def typeToValue[A](value: A): AttributeValue =
    value match {
      case s: String   => AttributeValue.StringValue(s)
      case b: Boolean  => AttributeValue.BooleanValue(b)
      case l: Long     => AttributeValue.LongValue(l)
      case d: Double   => AttributeValue.DoubleValue(d)
      case seq: Seq[_] =>
        seq.headOption match {
          case Some(_: String)  => AttributeValue.StringSeqValue(seq.asInstanceOf[Seq[String]])
          case Some(_: Long)    => AttributeValue.LongSeqValue(seq.asInstanceOf[Seq[Long]])
          case Some(_: Double)  => AttributeValue.DoubleSeqValue(seq.asInstanceOf[Seq[Double]])
          case Some(_: Boolean) => AttributeValue.BooleanSeqValue(seq.asInstanceOf[Seq[Boolean]])
          case _                => AttributeValue.StringSeqValue(Seq.empty)
        }
      case _ => AttributeValue.StringValue(value.toString)
    }

  /**
   * Mutable builder for Attributes.
   */
  class AttributesBuilder private[Attributes] () {
    private var keys: Array[String]         = new Array[String](8)
    private var vals: Array[AttributeValue] = new Array[AttributeValue](8)
    private var len: Int                    = 0

    /**
     * Adds or updates a typed attribute.
     */
    def put[A](key: AttributeKey[A], value: A): AttributesBuilder = {
      putInternal(key.name, typeToValue(value))
      this
    }

    /**
     * Adds or updates a string attribute.
     */
    def put(key: String, value: String): AttributesBuilder = {
      putInternal(key, AttributeValue.StringValue(value))
      this
    }

    /**
     * Adds or updates a long attribute.
     */
    def put(key: String, value: Long): AttributesBuilder = {
      putInternal(key, AttributeValue.LongValue(value))
      this
    }

    /**
     * Adds or updates a double attribute.
     */
    def put(key: String, value: Double): AttributesBuilder = {
      putInternal(key, AttributeValue.DoubleValue(value))
      this
    }

    /**
     * Adds or updates a boolean attribute.
     */
    def put(key: String, value: Boolean): AttributesBuilder = {
      putInternal(key, AttributeValue.BooleanValue(value))
      this
    }

    /**
     * Resets the builder to empty, allowing reuse without allocation.
     */
    def clear(): Unit = {
      var i = 0
      while (i < len) { keys(i) = null; vals(i) = null; i += 1 }
      len = 0
    }

    /**
     * Builds and returns the immutable Attributes.
     */
    def build: Attributes =
      if (len == 0) Attributes.empty
      else {
        val k = new Array[String](len)
        val v = new Array[AttributeValue](len)
        System.arraycopy(keys, 0, k, 0, len)
        System.arraycopy(vals, 0, v, 0, len)
        new Attributes(k, v, len)
      }

    /**
     * Internal: updates or adds an entry by key.
     */
    private def putInternal(key: String, value: AttributeValue): Unit = {
      var i = 0
      while (i < len) {
        if (keys(i) == key) { vals(i) = value; return }
        i += 1
      }
      if (len >= keys.length) {
        val newCap  = keys.length * 2
        val newKeys = new Array[String](newCap)
        val newVals = new Array[AttributeValue](newCap)
        System.arraycopy(keys, 0, newKeys, 0, len)
        System.arraycopy(vals, 0, newVals, 0, len)
        keys = newKeys
        vals = newVals
      }
      keys(len) = key
      vals(len) = value
      len += 1
    }
  }
}
