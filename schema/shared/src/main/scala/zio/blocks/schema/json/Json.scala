package zio.blocks.schema.json

/**
 * A JSON value represented as an algebraic data type.
 *
 * This type represents the complete JSON data model:
 *   - `Json.Null` - JSON null value
 *   - `Json.Bool` - JSON boolean (true/false)
 *   - `Json.Num` - JSON number (stored as string for precision)
 *   - `Json.Str` - JSON string
 *   - `Json.Arr` - JSON array
 *   - `Json.Obj` - JSON object
 *
 * Numbers are stored as strings to preserve exact precision and avoid
 * floating-point representation errors. Use `numValue` for numeric operations.
 * Objects preserve insertion order using `Vector[(String, Json)]`.
 */
sealed trait Json {
  def typeIndex: Int

  def isNull: Boolean    = this.isInstanceOf[Json.Null.type]
  def isBoolean: Boolean = this.isInstanceOf[Json.Bool]
  def isNumber: Boolean  = this.isInstanceOf[Json.Num]
  def isString: Boolean  = this.isInstanceOf[Json.Str]
  def isArray: Boolean   = this.isInstanceOf[Json.Arr]
  def isObject: Boolean  = this.isInstanceOf[Json.Obj]
}

object Json {

  case object Null extends Json {
    def typeIndex: Int = 0
  }

  final case class Bool(value: Boolean) extends Json {
    def typeIndex: Int = 1
  }

  final case class Num(value: String) extends Json {
    def typeIndex: Int            = 2
    lazy val numValue: BigDecimal = BigDecimal(value)
  }

  object Num {
    def apply(value: Int): Num        = Num(value.toString)
    def apply(value: Long): Num       = Num(value.toString)
    def apply(value: Double): Num     = Num(value.toString)
    def apply(value: BigDecimal): Num = Num(value.toString)
    def apply(value: BigInt): Num     = Num(value.toString)
  }

  final case class Str(value: String) extends Json {
    def typeIndex: Int = 3
  }

  final case class Arr(elements: Vector[Json]) extends Json {
    def typeIndex: Int   = 4
    def isEmpty: Boolean = elements.isEmpty
    def length: Int      = elements.length
  }

  object Arr {
    val empty: Arr                  = Arr(Vector.empty)
    def apply(elements: Json*): Arr = Arr(elements.toVector)
  }

  final case class Obj(fields: Vector[(String, Json)]) extends Json {
    def typeIndex: Int   = 5
    def isEmpty: Boolean = fields.isEmpty
    def size: Int        = fields.length

    def get(key: String): Option[Json] = {
      var idx = 0
      while (idx < fields.length) {
        val (k, v) = fields(idx)
        if (k == key) return Some(v)
        idx += 1
      }
      None
    }

    def getOrElse(key: String, default: => Json): Json = get(key).getOrElse(default)
    def contains(key: String): Boolean                 = get(key).isDefined
    def keys: Vector[String]                           = fields.map(_._1)
    def values: Vector[Json]                           = fields.map(_._2)
    def toMap: Map[String, Json]                       = fields.toMap
  }

  object Obj {
    val empty: Obj                          = Obj(Vector.empty)
    def apply(fields: (String, Json)*): Obj = Obj(fields.toVector)
  }

  val True: Json  = Bool(true)
  val False: Json = Bool(false)

  def obj(fields: (String, Json)*): Obj = Obj(fields.toVector)
  def arr(elements: Json*): Arr         = Arr(elements.toVector)
  def str(value: String): Str           = Str(value)
  def num(value: Int): Num              = Num(value.toString)
  def num(value: Long): Num             = Num(value.toString)
  def num(value: Double): Num           = Num(value.toString)
  def num(value: BigDecimal): Num       = Num(value.toString)
  def bool(value: Boolean): Bool        = Bool(value)
}
