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
 * Backed by an array of (String, AttributeValue) tuples for efficient storage
 * of small attribute sets (typical spans have <10 attributes).
 *
 * Attributes are accessed via typed keys (AttributeKey[A]) for compile-time
 * type safety.
 */
final class Attributes private (
  private val entries: Array[(String, AttributeValue)]
) {

  /**
   * Number of attributes in this collection.
   */
  def size: Int = entries.length

  /**
   * True if this collection contains no attributes.
   */
  def isEmpty: Boolean = entries.length == 0

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
    var i = entries.length - 1
    while (i >= 0) {
      val (k, v) = entries(i)
      if (k == key.name) {
        return Some(valueToType(v).asInstanceOf[A])
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
    while (i < entries.length) {
      val (k, v) = entries(i)
      f(k, v)
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
      var result = this
      var i      = 0
      while (i < other.entries.length) {
        val (k, v) = other.entries(i)
        result = result.updated(k, v)
        i += 1
      }
      result
    }

  /**
   * Converts this Attributes to a Map with string keys and AttributeValue
   * values.
   */
  def toMap: Map[String, AttributeValue] = {
    var map = Map.empty[String, AttributeValue]
    var i   = 0
    while (i < entries.length) {
      val (k, v) = entries(i)
      map = map + ((k, v))
      i += 1
    }
    map
  }

  /**
   * Internal: updates or adds an attribute by key name.
   */
  private def updated(key: String, value: AttributeValue): Attributes = {
    var found = false
    var i     = 0
    while (i < entries.length && !found) {
      if (entries(i)._1 == key) found = true
      i += 1
    }
    if (found) {
      val newEntries = new Array[(String, AttributeValue)](entries.length)
      var j          = 0
      var k          = 0
      while (j < entries.length) {
        if (entries(j)._1 != key) {
          newEntries(k) = entries(j)
          k += 1
        }
        j += 1
      }
      newEntries(k) = (key, value)
      new Attributes(newEntries)
    } else {
      val newEntries = new Array[(String, AttributeValue)](entries.length + 1)
      System.arraycopy(entries, 0, newEntries, 0, entries.length)
      newEntries(entries.length) = (key, value)
      new Attributes(newEntries)
    }
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
}

object Attributes {

  /**
   * An empty Attributes collection.
   */
  val empty: Attributes = new Attributes(Array.empty)

  /**
   * Creates an Attributes collection with a single typed attribute.
   */
  def of[A](key: AttributeKey[A], value: A): Attributes = {
    val attrValue = typeToValue(value)
    new Attributes(Array((key.name, attrValue)))
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
    private var entries: Array[(String, AttributeValue)] = Array.empty

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
    def clear(): Unit =
      entries = Array.empty

    /**
     * Builds and returns the immutable Attributes.
     */
    def build: Attributes =
      new Attributes(entries)

    /**
     * Internal: updates or adds an entry by key.
     */
    private def putInternal(key: String, value: AttributeValue): Unit = {
      var found = false
      var i     = 0
      while (i < entries.length && !found) {
        if (entries(i)._1 == key) found = true
        i += 1
      }
      if (found) {
        var j          = 0
        var k          = 0
        val newEntries = new Array[(String, AttributeValue)](entries.length)
        while (j < entries.length) {
          if (entries(j)._1 != key) {
            newEntries(k) = entries(j)
            k += 1
          }
          j += 1
        }
        newEntries(k) = (key, value)
        entries = newEntries
      } else {
        val newEntries = new Array[(String, AttributeValue)](entries.length + 1)
        System.arraycopy(entries, 0, newEntries, 0, entries.length)
        newEntries(entries.length) = (key, value)
        entries = newEntries
      }
    }
  }
}
