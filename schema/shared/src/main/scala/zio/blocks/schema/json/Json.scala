package zio.blocks.schema.json

import zio.blocks.schema._

/**
 * A proper JSON data type representing JSON values according to RFC 8259.
 * 
 * This ADT provides a type-safe representation of JSON with:
 * - Constructors for all JSON value types
 * - Methods for manipulation and querying
 * - Integration with ZIO Blocks Schema
 * - Support for JsonPatch and JsonSchema
 */
sealed trait Json { self =>
  
  /**
   * Merge this JSON value with another. For objects, performs a deep merge.
   * For other types, the right value wins.
   */
  def merge(that: Json): Json = (self, that) match {
    case (Json.Obj(fields1), Json.Obj(fields2)) =>
      val merged = fields1.foldLeft(fields2) { case (acc, (key, value)) =>
        acc.get(key) match {
          case Some(existing) => acc.updated(key, existing.merge(value))
          case None => acc + (key -> value)
        }
      }
      Json.Obj(merged)
    case _ => that
  }
  
  /**
   * Navigate to a field in a JSON object.
   */
  def \(field: String): Option[Json] = self match {
    case Json.Obj(fields) => fields.get(field)
    case _ => None
  }
  
  /**
   * Navigate to an index in a JSON array.
   */
  def apply(index: Int): Option[Json] = self match {
    case Json.Arr(elements) => 
      if (index >= 0 && index < elements.length) Some(elements(index))
      else None
    case _ => None
  }
  
  /**
   * Deep navigation using a path.
   */
  def at(path: JsonPath): Option[Json] = path.navigate(self)
  
  /**
   * Update a value at the given path.
   */
  def update(path: JsonPath, value: Json): Option[Json] = 
    path.update(self, value)
  
  /**
   * Delete a value at the given path.
   */
  def delete(path: JsonPath): Option[Json] = 
    path.delete(self)
  
  /**
   * Check if this JSON value is null.
   */
  def isNull: Boolean = self match {
    case Json.Null => true
    case _ => false
  }
  
  /**
   * Check if this JSON value is an object.
   */
  def isObject: Boolean = self match {
    case Json.Obj(_) => true
    case _ => false
  }
  
  /**
   * Check if this JSON value is an array.
   */
  def isArray: Boolean = self match {
    case Json.Arr(_) => true
    case _ => false
  }
  
  /**
   * Check if this JSON value is a string.
   */
  def isString: Boolean = self match {
    case Json.Str(_) => true
    case _ => false
  }
  
  /**
   * Check if this JSON value is a number.
   */
  def isNumber: Boolean = self match {
    case Json.Num(_) => true
    case _ => false
  }
  
  /**
   * Check if this JSON value is a boolean.
   */
  def isBoolean: Boolean = self match {
    case Json.Bool(_) => true
    case _ => false
  }
  
  /**
   * Convert to Option[String].
   */
  def asString: Option[String] = self match {
    case Json.Str(value) => Some(value)
    case _ => None
  }
  
  /**
   * Convert to Option[BigDecimal].
   */
  def asNumber: Option[BigDecimal] = self match {
    case Json.Num(value) => Some(value)
    case _ => None
  }
  
  /**
   * Convert to Option[Boolean].
   */
  def asBoolean: Option[Boolean] = self match {
    case Json.Bool(value) => Some(value)
    case _ => None
  }
  
  /**
   * Convert to Option[Map[String, Json]].
   */
  def asObject: Option[Map[String, Json]] = self match {
    case Json.Obj(fields) => Some(fields)
    case _ => None
  }
  
  /**
   * Convert to Option[Vector[Json]].
   */
  def asArray: Option[Vector[Json]] = self match {
    case Json.Arr(elements) => Some(elements)
    case _ => None
  }
  
  /**
   * Fold over this JSON value.
   */
  def fold[A](
    onNull: => A,
    onBool: Boolean => A,
    onNum: BigDecimal => A,
    onStr: String => A,
    onArr: Vector[Json] => A,
    onObj: Map[String, Json] => A
  ): A = self match {
    case Json.Null => onNull
    case Json.Bool(value) => onBool(value)
    case Json.Num(value) => onNum(value)
    case Json.Str(value) => onStr(value)
    case Json.Arr(elements) => onArr(elements)
    case Json.Obj(fields) => onObj(fields)
  }
  
  /**
   * Transform this JSON value recursively.
   */
  def transform(f: Json => Json): Json = {
    val transformed = self match {
      case Json.Arr(elements) => Json.Arr(elements.map(_.transform(f)))
      case Json.Obj(fields) => Json.Obj(fields.view.mapValues(_.transform(f)).toMap)
      case other => other
    }
    f(transformed)
  }
  
  /**
   * Filter object fields or array elements.
   */
  def filter(predicate: Json => Boolean): Json = self match {
    case Json.Arr(elements) => Json.Arr(elements.filter(predicate))
    case Json.Obj(fields) => Json.Obj(fields.filter { case (_, v) => predicate(v) })
    case other => other
  }
  
  /**
   * Map over array elements.
   */
  def mapArray(f: Json => Json): Json = self match {
    case Json.Arr(elements) => Json.Arr(elements.map(f))
    case other => other
  }
  
  /**
   * Map over object values.
   */
  def mapObject(f: (String, Json) => Json): Json = self match {
    case Json.Obj(fields) => Json.Obj(fields.map { case (k, v) => k -> f(k, v) })
    case other => other
  }
  
  /**
   * Convert to DynamicValue for schema operations.
   */
  def toDynamicValue: DynamicValue = self match {
    case Json.Null => DynamicValue.Primitive(PrimitiveValue.Null)
    case Json.Bool(value) => DynamicValue.Primitive(PrimitiveValue.Bool(value))
    case Json.Num(value) => 
      // Try to preserve integer types when possible
      if (value.isValidInt) DynamicValue.Primitive(PrimitiveValue.Int(value.toInt))
      else if (value.isValidLong) DynamicValue.Primitive(PrimitiveValue.Long(value.toLong))
      else DynamicValue.Primitive(PrimitiveValue.BigDecimal(value))
    case Json.Str(value) => DynamicValue.Primitive(PrimitiveValue.String(value))
    case Json.Arr(elements) => DynamicValue.Sequence(elements.map(_.toDynamicValue))
    case Json.Obj(fields) => 
      DynamicValue.Record(fields.toVector.map { case (k, v) => (k, v.toDynamicValue) })
  }
  
  /**
   * Pretty print this JSON value.
   */
  def prettyPrint(indent: Int = 2): String = {
    def loop(json: Json, depth: Int): String = {
      val currentIndent = " " * (depth * indent)
      val nextIndent = " " * ((depth + 1) * indent)
      
      json match {
        case Json.Null => "null"
        case Json.Bool(value) => value.toString
        case Json.Num(value) => value.toString
        case Json.Str(value) => s""""${escape(value)}""""
        case Json.Arr(elements) if elements.isEmpty => "[]"
        case Json.Arr(elements) =>
          val items = elements.map(e => s"$nextIndent${loop(e, depth + 1)}").mkString(",\n")
          s"[\n$items\n$currentIndent]"
        case Json.Obj(fields) if fields.isEmpty => "{}"
        case Json.Obj(fields) =>
          val items = fields.toSeq.sortBy(_._1).map { case (k, v) =>
            s"""$nextIndent"${escape(k)}": ${loop(v, depth + 1)}"""
          }.mkString(",\n")
          s"{\n$items\n$currentIndent}"
      }
    }
    
    loop(self, 0)
  }
  
  /**
   * Compact JSON string representation.
   */
  def toCompactString: String = self match {
    case Json.Null => "null"
    case Json.Bool(value) => value.toString
    case Json.Num(value) => value.toString
    case Json.Str(value) => s""""${escape(value)}""""
    case Json.Arr(elements) => elements.map(_.toCompactString).mkString("[", ",", "]")
    case Json.Obj(fields) => 
      fields.toSeq.sortBy(_._1).map { case (k, v) =>
        s""""${escape(k)}":${v.toCompactString}"""
      }.mkString("{", ",", "}")
  }
  
  private def escape(s: String): String = {
    s.flatMap {
      case '"' => "\\\""
      case '\\' => "\\\\"
      case '\b' => "\\b"
      case '\f' => "\\f"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c if c.isControl => f"\\u${c.toInt}%04x"
      case c => c.toString
    }
  }
}

object Json {
  
  /**
   * JSON null value.
   */
  case object Null extends Json
  
  /**
   * JSON boolean value.
   */
  final case class Bool(value: Boolean) extends Json
  
  /**
   * JSON number value. Uses BigDecimal for arbitrary precision.
   */
  final case class Num(value: BigDecimal) extends Json
  
  /**
   * JSON string value.
   */
  final case class Str(value: String) extends Json
  
  /**
   * JSON array value.
   */
  final case class Arr(elements: Vector[Json]) extends Json
  
  /**
   * JSON object value.
   */
  final case class Obj(fields: Map[String, Json]) extends Json
  
  // Smart constructors
  
  def obj(fields: (String, Json)*): Json = Obj(fields.toMap)
  
  def arr(elements: Json*): Json = Arr(elements.toVector)
  
  def fromInt(value: Int): Json = Num(BigDecimal(value))
  
  def fromLong(value: Long): Json = Num(BigDecimal(value))
  
  def fromDouble(value: Double): Json = Num(BigDecimal(value))
  
  def fromBigDecimal(value: BigDecimal): Json = Num(value)
  
  def fromString(value: String): Json = Str(value)
  
  def fromBoolean(value: Boolean): Json = Bool(value)
  
  def fromOption[A](opt: Option[A])(implicit encoder: A => Json): Json = 
    opt.fold[Json](Null)(encoder)
  
  def fromIterable[A](iter: Iterable[A])(implicit encoder: A => Json): Json =
    Arr(iter.map(encoder).toVector)
  
  def fromMap[A](map: Map[String, A])(implicit encoder: A => Json): Json =
    Obj(map.view.mapValues(encoder).toMap)
  
  /**
   * Convert from DynamicValue.
   */
  def fromDynamicValue(dv: DynamicValue): Json = dv match {
    case DynamicValue.Primitive(PrimitiveValue.Null) => Null
    case DynamicValue.Primitive(PrimitiveValue.Bool(value)) => Bool(value)
    case DynamicValue.Primitive(PrimitiveValue.Byte(value)) => Num(BigDecimal(value))
    case DynamicValue.Primitive(PrimitiveValue.Short(value)) => Num(BigDecimal(value))
    case DynamicValue.Primitive(PrimitiveValue.Int(value)) => Num(BigDecimal(value))
    case DynamicValue.Primitive(PrimitiveValue.Long(value)) => Num(BigDecimal(value))
    case DynamicValue.Primitive(PrimitiveValue.Float(value)) => Num(BigDecimal(value.toDouble))
    case DynamicValue.Primitive(PrimitiveValue.Double(value)) => Num(BigDecimal(value))
    case DynamicValue.Primitive(PrimitiveValue.BigInt(value)) => Num(BigDecimal(value))
    case DynamicValue.Primitive(PrimitiveValue.BigDecimal(value)) => Num(value)
    case DynamicValue.Primitive(PrimitiveValue.String(value)) => Str(value)
    case DynamicValue.Primitive(PrimitiveValue.Char(value)) => Str(value.toString)
    case DynamicValue.Sequence(elements) => Arr(elements.map(fromDynamicValue))
    case DynamicValue.Record(fields) => 
      Obj(fields.map { case (k, v) => k -> fromDynamicValue(v) }.toMap)
    case DynamicValue.Map(entries) =>
      // Convert map to object if keys are strings, otherwise to array of [key, value] pairs
      val stringKeys = entries.forall { case (k, _) => 
        k match {
          case DynamicValue.Primitive(PrimitiveValue.String(_)) => true
          case _ => false
        }
      }
      if (stringKeys) {
        Obj(entries.map { case (k, v) =>
          val key = k match {
            case DynamicValue.Primitive(PrimitiveValue.String(s)) => s
            case _ => k.toString
          }
          key -> fromDynamicValue(v)
        }.toMap)
      } else {
        Arr(entries.map { case (k, v) =>
          arr(fromDynamicValue(k), fromDynamicValue(v))
        })
      }
    case DynamicValue.Variant(_, value) => fromDynamicValue(value)
    case _ => Null
  }
  
  /**
   * Schema for Json type.
   */
  implicit lazy val schema: Schema[Json] = Schema.defer {
    Schema.Enum[Json](
      TypeName("Json", Vector("zio", "blocks", "schema", "json")),
      Vector(
        Schema.Case[Json, Null.type](
          "Null",
          Schema.singleton(Null),
          _.asInstanceOf[Null.type],
          identity,
          _.isNull
        ),
        Schema.Case[Json, Bool](
          "Bool",
          Schema.Primitive(PrimitiveType.Bool).transform(Bool.apply, _.value),
          _.asInstanceOf[Bool],
          identity,
          _.isBoolean
        ),
        Schema.Case[Json, Num](
          "Num",
          Schema.Primitive(PrimitiveType.BigDecimal).transform(Num.apply, _.value),
          _.asInstanceOf[Num],
          identity,
          _.isNumber
        ),
        Schema.Case[Json, Str](
          "Str",
          Schema.Primitive(PrimitiveType.String).transform(Str.apply, _.value),
          _.asInstanceOf[Str],
          identity,
          _.isString
        ),
        Schema.Case[Json, Arr](
          "Arr",
          Schema.sequence[Vector[Json], Json](schema).transform(Arr.apply, _.elements),
          _.asInstanceOf[Arr],
          identity,
          _.isArray
        ),
        Schema.Case[Json, Obj](
          "Obj",
          Schema.map[String, Json](schema).transform(Obj.apply, _.fields),
          _.asInstanceOf[Obj],
          identity,
          _.isObject
        )
      )
    )
  }
}
