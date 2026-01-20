package zio.blocks.schema.json

import zio.Chunk
import zio.blocks.schema.{DynamicValue, PrimitiveValue}

/**
 * An immutable representation of a JSON value.
 *
 * Json provides a type-safe, algebraic data type for representing JSON values
 * with operations for construction, access, transformation, and conversion
 * to/from DynamicValue.
 *
 * ==Example Usage==
 * {{{
 * val json = Json.obj(
 *   "name" -> Json.str("Alice"),
 *   "age" -> Json.num(30),
 *   "active" -> Json.`true`
 * )
 *
 * json("name").flatMap(_.asString) // Some("Alice")
 * json.dropNulls.sortKeys          // Normalized JSON
 * }}}
 */
sealed trait Json { self =>

  /** Type discriminator for pattern matching optimization. */
  def typeIndex: Int

  // Type predicates
  def isNull: Boolean = this == Json.Null
  def isBoolean: Boolean = this.isInstanceOf[Json.Bool]
  def isNumber: Boolean = this.isInstanceOf[Json.Num]
  def isString: Boolean = this.isInstanceOf[Json.Str]
  def isArray: Boolean = this.isInstanceOf[Json.Arr]
  def isObject: Boolean = this.isInstanceOf[Json.Obj]

  // Safe accessors
  def asBoolean: Option[Boolean] = this match {
    case Json.Bool(value) => Some(value)
    case _                => None
  }

  def asNumber: Option[java.math.BigDecimal] = this match {
    case Json.Num(value) => Some(value)
    case _               => None
  }

  def asString: Option[String] = this match {
    case Json.Str(value) => Some(value)
    case _               => None
  }

  def asArray: Option[Chunk[Json]] = this match {
    case Json.Arr(elements) => Some(elements)
    case _                  => None
  }

  def asObject: Option[Chunk[(String, Json)]] = this match {
    case Json.Obj(fields) => Some(fields)
    case _                => None
  }

  /** Access object field by key. Returns None if not an object or key missing. */
  def apply(key: String): Option[Json] = this match {
    case Json.Obj(fields) => fields.find(_._1 == key).map(_._2)
    case _                => None
  }

  /** Access array element by index. Returns None if not an array or index out of bounds. */
  def apply(index: Int): Option[Json] = this match {
    case Json.Arr(elements) if index >= 0 && index < elements.length => Some(elements(index))
    case _ => None
  }

  /**
   * Deep merge two JSON values. For objects, recursively merges fields.
   * For other types, `that` takes precedence.
   */
  def merge(that: Json): Json = (this, that) match {
    case (Json.Obj(fields1), Json.Obj(fields2)) =>
      val merged = fields1.foldLeft(fields2.toMap) { case (acc, (k, v)) =>
        acc.get(k) match {
          case Some(existing) => acc.updated(k, v.merge(existing))
          case None           => acc.updated(k, v)
        }
      }
      Json.Obj(Chunk.fromIterable(merged.toSeq))
    case (_, that) => that
  }

  /** Fold over all JSON values in pre-order (parent before children). */
  def foldDown[A](z: A)(f: (A, Json) => A): A = {
    val acc = f(z, this)
    this match {
      case Json.Arr(elements) => elements.foldLeft(acc)((a, j) => j.foldDown(a)(f))
      case Json.Obj(fields)   => fields.foldLeft(acc)((a, kv) => kv._2.foldDown(a)(f))
      case _                  => acc
    }
  }

  /** Fold over all JSON values in post-order (children before parent). */
  def foldUp[A](z: A)(f: (A, Json) => A): A = {
    val inner = this match {
      case Json.Arr(elements) => elements.foldLeft(z)((a, j) => j.foldUp(a)(f))
      case Json.Obj(fields)   => fields.foldLeft(z)((a, kv) => kv._2.foldUp(a)(f))
      case _                  => z
    }
    f(inner, this)
  }

  /** Transform all values in pre-order. */
  def transformDown(f: Json => Json): Json = {
    val transformed = f(this)
    transformed match {
      case Json.Arr(elements) => Json.Arr(elements.map(_.transformDown(f)))
      case Json.Obj(fields)   => Json.Obj(fields.map { case (k, v) => (k, v.transformDown(f)) })
      case other              => other
    }
  }

  /** Transform all values in post-order. */
  def transformUp(f: Json => Json): Json = {
    val inner = this match {
      case Json.Arr(elements) => Json.Arr(elements.map(_.transformUp(f)))
      case Json.Obj(fields)   => Json.Obj(fields.map { case (k, v) => (k, v.transformUp(f)) })
      case other              => other
    }
    f(inner)
  }

  /** Remove all null values from objects recursively. */
  def dropNulls: Json = transformDown {
    case Json.Obj(fields) => Json.Obj(fields.filter(_._2 != Json.Null))
    case other            => other
  }

  /** Sort object keys alphabetically at all levels. */
  def sortKeys: Json = transformDown {
    case Json.Obj(fields) => Json.Obj(Chunk.fromIterable(fields.sortBy(_._1)))
    case other            => other
  }

  /** Normalize JSON by dropping nulls and sorting keys. */
  def normalize: Json = dropNulls.sortKeys

  /** Convert to DynamicValue for schema operations. */
  def toDynamicValue: DynamicValue = this match {
    case Json.Null        => DynamicValue.Primitive(PrimitiveValue.Unit)
    case Json.Bool(v)     => DynamicValue.Primitive(PrimitiveValue.Boolean(v))
    case Json.Num(v)      => DynamicValue.Primitive(PrimitiveValue.BigDecimal(v))
    case Json.Str(v)      => DynamicValue.Primitive(PrimitiveValue.String(v))
    case Json.Arr(elems)  => DynamicValue.Sequence(elems.map(_.toDynamicValue).toVector)
    case Json.Obj(fields) => DynamicValue.Record(fields.map { case (k, v) => (k, v.toDynamicValue) }.toVector)
  }

  /** Compute a patch that transforms this JSON into that JSON. */
  def diff(that: Json): JsonPatch = JsonPatch.diff(this, that)
}

object Json {
  case object Null extends Json {
    def typeIndex: Int = 0
  }

  final case class Bool(value: Boolean) extends Json {
    def typeIndex: Int = 1
  }

  final case class Num(value: java.math.BigDecimal) extends Json {
    def typeIndex: Int = 2

    override def equals(other: Any): Boolean = other match {
      case Num(v) => value.compareTo(v) == 0
      case _      => false
    }

    override def hashCode: Int = value.stripTrailingZeros.hashCode
  }

  final case class Str(value: String) extends Json {
    def typeIndex: Int = 3
  }

  final case class Arr(elements: Chunk[Json]) extends Json {
    def typeIndex: Int = 4
  }

  final case class Obj(fields: Chunk[(String, Json)]) extends Json {
    def typeIndex: Int = 5

    def toMap: Map[String, Json] = fields.toMap

    def keys: Chunk[String] = fields.map(_._1)

    def values: Chunk[Json] = fields.map(_._2)
  }

  // Constructors
  def obj(fields: (String, Json)*): Obj = Obj(Chunk.fromIterable(fields))
  def arr(elements: Json*): Arr = Arr(Chunk.fromIterable(elements))
  def str(value: String): Str = Str(value)
  def num(value: Int): Num = Num(java.math.BigDecimal.valueOf(value.toLong))
  def num(value: Long): Num = Num(java.math.BigDecimal.valueOf(value))
  def num(value: Double): Num = Num(java.math.BigDecimal.valueOf(value))
  def num(value: java.math.BigDecimal): Num = Num(value)
  def bool(value: Boolean): Bool = Bool(value)
  val `null`: Null.type = Null
  val `true`: Bool = Bool(true)
  val `false`: Bool = Bool(false)

  /** Convert from DynamicValue. Returns None if the structure is incompatible. */
  def fromDynamicValue(dv: DynamicValue): Option[Json] = dv match {
    case DynamicValue.Primitive(PrimitiveValue.Unit)           => Some(Null)
    case DynamicValue.Primitive(PrimitiveValue.Boolean(v))     => Some(Bool(v))
    case DynamicValue.Primitive(PrimitiveValue.BigDecimal(v))  => Some(Num(v))
    case DynamicValue.Primitive(PrimitiveValue.String(v))      => Some(Str(v))
    case DynamicValue.Primitive(PrimitiveValue.Int(v))         => Some(Num(java.math.BigDecimal.valueOf(v.toLong)))
    case DynamicValue.Primitive(PrimitiveValue.Long(v))        => Some(Num(java.math.BigDecimal.valueOf(v)))
    case DynamicValue.Primitive(PrimitiveValue.Double(v))      => Some(Num(java.math.BigDecimal.valueOf(v)))
    case DynamicValue.Primitive(PrimitiveValue.Float(v))       => Some(Num(java.math.BigDecimal.valueOf(v.toDouble)))
    case DynamicValue.Sequence(elems) =>
      val converted = elems.flatMap(fromDynamicValue)
      if (converted.length == elems.length) Some(Arr(Chunk.fromIterable(converted)))
      else None
    case DynamicValue.Record(fields) =>
      val converted = fields.flatMap { case (k, v) => fromDynamicValue(v).map(k -> _) }
      if (converted.length == fields.length) Some(Obj(Chunk.fromIterable(converted)))
      else None
    case _ => None
  }

  /** Ordering for Json values based on type then value. */
  implicit val ordering: Ordering[Json] = (x: Json, y: Json) => {
    val typeCmp = x.typeIndex.compareTo(y.typeIndex)
    if (typeCmp != 0) typeCmp
    else (x, y) match {
      case (Null, Null)           => 0
      case (Bool(a), Bool(b))     => a.compareTo(b)
      case (Num(a), Num(b))       => a.compareTo(b)
      case (Str(a), Str(b))       => a.compareTo(b)
      case (Arr(a), Arr(b))       => compareChunks(a, b)
      case (Obj(a), Obj(b))       => compareFields(a, b)
      case _                      => 0
    }
  }

  private def compareChunks(a: Chunk[Json], b: Chunk[Json]): Int = {
    val minLen = math.min(a.length, b.length)
    var i = 0
    while (i < minLen) {
      val cmp = ordering.compare(a(i), b(i))
      if (cmp != 0) return cmp
      i += 1
    }
    a.length.compareTo(b.length)
  }

  private def compareFields(a: Chunk[(String, Json)], b: Chunk[(String, Json)]): Int = {
    val minLen = math.min(a.length, b.length)
    var i = 0
    while (i < minLen) {
      val (k1, v1) = a(i)
      val (k2, v2) = b(i)
      val keyCmp = k1.compareTo(k2)
      if (keyCmp != 0) return keyCmp
      val valCmp = ordering.compare(v1, v2)
      if (valCmp != 0) return valCmp
      i += 1
    }
    a.length.compareTo(b.length)
  }
}
