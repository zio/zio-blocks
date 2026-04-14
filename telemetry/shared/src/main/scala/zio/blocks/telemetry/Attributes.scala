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

package zio.blocks.telemetry

import Attributes._

/**
 * An immutable collection of typed key-value attribute pairs.
 *
 * Backed by parallel arrays with dual-slot storage: primitive values (Long,
 * Double, Boolean) are stored unboxed in a `Long` array, while String values
 * use a separate `String` array. A `Byte` type discriminator array selects the
 * slot. Seq values use a separate `AnyRef` array for rare cases.
 *
 * This design eliminates `AttributeValue` boxing for the common primitive
 * cases, reducing GC pressure on hot paths.
 */
final class Attributes private (
  private[telemetry] val keys: Array[String],
  private[telemetry] val types: Array[Byte],
  private[telemetry] val longs: Array[Long],
  private[telemetry] val strings: Array[String],
  private[telemetry] val seqs: Array[AnyRef],
  private[telemetry] val len: Int
) {

  private val cachedHash: Int = {
    var h = 1
    var i = 0
    while (i < len) {
      h = 31 * h + keys(i).hashCode
      h = 31 * h + types(i)
      h = 31 * h + java.lang.Long.hashCode(longs(i))
      if (strings(i) != null) h = 31 * h + strings(i).hashCode
      if (seqs != null && seqs(i) != null) h = 31 * h + seqs(i).hashCode
      i += 1
    }
    h
  }

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
        val value: Any = types(i) match {
          case T_STRING      => strings(i)
          case T_LONG        => longs(i)
          case T_DOUBLE      => java.lang.Double.longBitsToDouble(longs(i))
          case T_BOOLEAN     => longs(i) != 0L
          case T_STRING_SEQ  => seqs(i)
          case T_LONG_SEQ    => seqs(i)
          case T_DOUBLE_SEQ  => seqs(i)
          case T_BOOLEAN_SEQ => seqs(i)
          case _             => strings(i)
        }
        return Some(value.asInstanceOf[A])
      }
      i -= 1
    }
    None
  }

  /**
   * Invokes a visitor for each attribute without boxing primitives.
   */
  def accept(visitor: AttributeVisitor): Unit = {
    var i = 0
    while (i < len) {
      types(i) match {
        case T_STRING      => visitor.visitString(keys(i), strings(i))
        case T_LONG        => visitor.visitLong(keys(i), longs(i))
        case T_DOUBLE      => visitor.visitDouble(keys(i), java.lang.Double.longBitsToDouble(longs(i)))
        case T_BOOLEAN     => visitor.visitBoolean(keys(i), longs(i) != 0L)
        case T_STRING_SEQ  => if (seqs != null) visitor.visitStringSeq(keys(i), seqs(i).asInstanceOf[Seq[String]])
        case T_LONG_SEQ    => if (seqs != null) visitor.visitLongSeq(keys(i), seqs(i).asInstanceOf[Seq[Long]])
        case T_DOUBLE_SEQ  => if (seqs != null) visitor.visitDoubleSeq(keys(i), seqs(i).asInstanceOf[Seq[Double]])
        case T_BOOLEAN_SEQ =>
          if (seqs != null) visitor.visitBooleanSeq(keys(i), seqs(i).asInstanceOf[Seq[Boolean]])
        case _ => ()
      }
      i += 1
    }
  }

  /**
   * Invokes a function for each attribute. Creates `AttributeValue` wrappers
   * lazily, so callers that need unboxed access should prefer `accept`.
   */
  def foreach(f: (String, AttributeValue) => Unit): Unit = {
    var i = 0
    while (i < len) {
      val value: AttributeValue = types(i) match {
        case T_STRING     => AttributeValue.StringValue(strings(i))
        case T_LONG       => AttributeValue.LongValue(longs(i))
        case T_DOUBLE     => AttributeValue.DoubleValue(java.lang.Double.longBitsToDouble(longs(i)))
        case T_BOOLEAN    => AttributeValue.BooleanValue(longs(i) != 0L)
        case T_STRING_SEQ =>
          if (seqs != null && seqs(i) != null) AttributeValue.StringSeqValue(seqs(i).asInstanceOf[Seq[String]])
          else AttributeValue.StringSeqValue(Seq.empty)
        case T_LONG_SEQ =>
          if (seqs != null && seqs(i) != null) AttributeValue.LongSeqValue(seqs(i).asInstanceOf[Seq[Long]])
          else AttributeValue.LongSeqValue(Seq.empty)
        case T_DOUBLE_SEQ =>
          if (seqs != null && seqs(i) != null) AttributeValue.DoubleSeqValue(seqs(i).asInstanceOf[Seq[Double]])
          else AttributeValue.DoubleSeqValue(Seq.empty)
        case T_BOOLEAN_SEQ =>
          if (seqs != null && seqs(i) != null) AttributeValue.BooleanSeqValue(seqs(i).asInstanceOf[Seq[Boolean]])
          else AttributeValue.BooleanSeqValue(Seq.empty)
        case _ => AttributeValue.StringValue("")
      }
      f(keys(i), value)
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
      val newLen = len + other.len
      val k      = new Array[String](newLen)
      val t      = new Array[Byte](newLen)
      val l      = new Array[Long](newLen)
      val s      = new Array[String](newLen)
      val sq     = if (seqs != null || other.seqs != null) {
        val arr = new Array[AnyRef](newLen)
        if (seqs != null) System.arraycopy(seqs, 0, arr, 0, len)
        if (other.seqs != null) System.arraycopy(other.seqs, 0, arr, len, other.len)
        arr
      } else null
      System.arraycopy(keys, 0, k, 0, len)
      System.arraycopy(types, 0, t, 0, len)
      System.arraycopy(longs, 0, l, 0, len)
      System.arraycopy(strings, 0, s, 0, len)
      System.arraycopy(other.keys, 0, k, len, other.len)
      System.arraycopy(other.types, 0, t, len, other.len)
      System.arraycopy(other.longs, 0, l, len, other.len)
      System.arraycopy(other.strings, 0, s, len, other.len)
      new Attributes(k, t, l, s, sq, newLen)
    }

  /**
   * Converts this Attributes to a Map with string keys and AttributeValue
   * values.
   */
  def toMap: Map[String, AttributeValue] = {
    var map = Map.empty[String, AttributeValue]
    foreach { (k, v) => map = map + ((k, v)) }
    map
  }

  override def hashCode(): Int = cachedHash

  override def equals(obj: Any): Boolean =
    (this eq obj.asInstanceOf[AnyRef]) || (obj match {
      case other: Attributes =>
        if (len != other.len) false
        else {
          var i = 0
          while (i < len) {
            var found = false
            var j     = 0
            while (j < other.len && !found) {
              if (
                keys(i) == other.keys(j) && types(i) == other.types(j) &&
                longs(i) == other.longs(j) &&
                strings(i) == other.strings(j) &&
                (seqs == null && other.seqs == null || seqs != null && other.seqs != null && seqs(i) == other.seqs(j))
              ) found = true
              j += 1
            }
            if (!found) return false
            i += 1
          }
          true
        }
      case _ => false
    })
}

object Attributes {

  // Type discriminator constants — inlined as literal Byte values for
  // tableswitch compatibility across Scala 2 and 3.
  private[telemetry] final val T_STRING: Byte      = 0
  private[telemetry] final val T_LONG: Byte        = 1
  private[telemetry] final val T_DOUBLE: Byte      = 2
  private[telemetry] final val T_BOOLEAN: Byte     = 3
  private[telemetry] final val T_STRING_SEQ: Byte  = 4
  private[telemetry] final val T_LONG_SEQ: Byte    = 5
  private[telemetry] final val T_DOUBLE_SEQ: Byte  = 6
  private[telemetry] final val T_BOOLEAN_SEQ: Byte = 7

  /**
   * An empty Attributes collection.
   */
  val empty: Attributes = new Attributes(
    Array.empty,
    Array.empty,
    Array.empty,
    Array.empty,
    null,
    0
  )

  /**
   * Creates an Attributes collection with a single typed attribute.
   */
  def of[A](key: AttributeKey[A], value: A): Attributes = {
    val b = new AttributesBuilder()
    b.put(key, value)
    b.build
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
   * Mutable builder for Attributes.
   */
  class AttributesBuilder private[Attributes] () {
    private var _keys: Array[String]    = new Array[String](8)
    private var _types: Array[Byte]     = new Array[Byte](8)
    private var _longs: Array[Long]     = new Array[Long](8)
    private var _strings: Array[String] = new Array[String](8)
    private var _seqs: Array[AnyRef]    = null
    private var _len: Int               = 0

    private def ensureSeqs(): Unit =
      if (_seqs == null) {
        _seqs = new Array[AnyRef](_keys.length)
      }

    /**
     * Adds or updates a typed attribute.
     */
    def put[A](key: AttributeKey[A], value: A): AttributesBuilder = {
      key.`type` match {
        case AttributeType.StringType     => put(key.name, value.asInstanceOf[String])
        case AttributeType.LongType       => put(key.name, value.asInstanceOf[Long])
        case AttributeType.DoubleType     => put(key.name, value.asInstanceOf[Double])
        case AttributeType.BooleanType    => put(key.name, value.asInstanceOf[Boolean])
        case AttributeType.StringSeqType  => putSeq(key.name, T_STRING_SEQ, value.asInstanceOf[AnyRef])
        case AttributeType.LongSeqType    => putSeq(key.name, T_LONG_SEQ, value.asInstanceOf[AnyRef])
        case AttributeType.DoubleSeqType  => putSeq(key.name, T_DOUBLE_SEQ, value.asInstanceOf[AnyRef])
        case AttributeType.BooleanSeqType => putSeq(key.name, T_BOOLEAN_SEQ, value.asInstanceOf[AnyRef])
      }
      this
    }

    /**
     * Adds or updates a string attribute.
     */
    def put(key: String, value: String): AttributesBuilder = {
      val i = findOrAdd(key)
      _types(i) = T_STRING
      _strings(i) = value
      this
    }

    /**
     * Adds or updates a long attribute.
     */
    def put(key: String, value: Long): AttributesBuilder = {
      val i = findOrAdd(key)
      _types(i) = T_LONG
      _longs(i) = value
      this
    }

    /**
     * Adds or updates a double attribute.
     */
    def put(key: String, value: Double): AttributesBuilder = {
      val i = findOrAdd(key)
      _types(i) = T_DOUBLE
      _longs(i) = java.lang.Double.doubleToRawLongBits(value)
      this
    }

    /**
     * Adds or updates a boolean attribute.
     */
    def put(key: String, value: Boolean): AttributesBuilder = {
      val i = findOrAdd(key)
      _types(i) = T_BOOLEAN
      _longs(i) = if (value) 1L else 0L
      this
    }

    private def putSeq(key: String, tpe: Byte, seq: AnyRef): Unit = {
      ensureSeqs()
      val i = findOrAdd(key)
      _types(i) = tpe
      _longs(i) = 0L
      _strings(i) = null
      _seqs(i) = seq
    }

    /**
     * Resets the builder to empty, allowing reuse without allocation.
     */
    private[telemetry] def builderKeys: Array[String]    = _keys
    private[telemetry] def builderTypes: Array[Byte]     = _types
    private[telemetry] def builderLongs: Array[Long]     = _longs
    private[telemetry] def builderStrings: Array[String] = _strings
    private[telemetry] def builderLen: Int               = _len

    def clear(): Unit = {
      var i = 0
      while (i < _len) {
        _keys(i) = null
        _strings(i) = null
        if (_seqs != null) _seqs(i) = null
        i += 1
      }
      _len = 0
    }

    /**
     * Builds and returns the immutable Attributes.
     */
    def build: Attributes =
      if (_len == 0) Attributes.empty
      else {
        val k  = java.util.Arrays.copyOf(_keys, _len)
        val t  = java.util.Arrays.copyOf(_types, _len)
        val l  = java.util.Arrays.copyOf(_longs, _len)
        val s  = java.util.Arrays.copyOf(_strings, _len)
        val sq = if (_seqs != null) java.util.Arrays.copyOf(_seqs, _len) else null
        new Attributes(k, t, l, s, sq, _len)
      }

    /**
     * Builds Attributes by handing off internal arrays (zero copy). After
     * calling this, the builder's arrays are reset to fresh allocations. Use
     * this when the builder is obtained from a pool and will be reused.
     */
    def buildAndReset(): Attributes =
      if (_len == 0) Attributes.empty
      else {
        val a   = new Attributes(_keys, _types, _longs, _strings, _seqs, _len)
        val cap = 8
        _keys = new Array[String](cap)
        _types = new Array[Byte](cap)
        _longs = new Array[Long](cap)
        _strings = new Array[String](cap)
        _seqs = null
        _len = 0
        a
      }

    private def findOrAdd(key: String): Int = {
      var i = 0
      while (i < _len) {
        if (_keys(i) == key) return i
        i += 1
      }
      ensureCapacity()
      _keys(_len) = key
      _len += 1
      _len - 1
    }

    private def ensureCapacity(): Unit =
      if (_len >= _keys.length) {
        val newCap = _keys.length * 2
        _keys = java.util.Arrays.copyOf(_keys, newCap)
        _types = java.util.Arrays.copyOf(_types, newCap)
        _longs = java.util.Arrays.copyOf(_longs, newCap)
        _strings = java.util.Arrays.copyOf(_strings, newCap)
        if (_seqs != null) _seqs = java.util.Arrays.copyOf(_seqs, newCap)
      }
  }
}
