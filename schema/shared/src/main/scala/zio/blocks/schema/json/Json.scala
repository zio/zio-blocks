package zio.blocks.schema.json

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaError}

import java.io.{Reader, Writer}
import scala.annotation.unused
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Represents a JSON value.
 */
sealed trait Json { self =>

  def isObject: scala.Boolean = false
  def isArray: scala.Boolean = false
  def isString: scala.Boolean = false
  def isNumber: scala.Boolean = false
  def isBoolean: scala.Boolean = false
  def isNull: scala.Boolean = false

  def asObject: JsonSelection = if (isObject) JsonSelection(self) else JsonSelection.empty
  def asArray: JsonSelection = if (isArray) JsonSelection(self) else JsonSelection.empty
  def asString: JsonSelection = if (isString) JsonSelection(self) else JsonSelection.empty
  def asNumber: JsonSelection = if (isNumber) JsonSelection(self) else JsonSelection.empty
  def asBoolean: JsonSelection = if (isBoolean) JsonSelection(self) else JsonSelection.empty
  def asNull: JsonSelection = if (isNull) JsonSelection(self) else JsonSelection.empty

  def fields: Seq[(java.lang.String, Json)] = Seq.empty
  def elements: Seq[Json] = Seq.empty
  def stringValue: Option[java.lang.String] = None
  def numberValue: Option[java.lang.String] = None
  def booleanValue: Option[scala.Boolean] = None

  def get(path: DynamicOptic): JsonSelection = Json.navigate(self, path.nodes, 0)

  def apply(path: DynamicOptic): JsonSelection = get(path)

  def apply(index: Int): JsonSelection = self match {
    case Json.Array(elems) if index >= 0 && index < elems.size => JsonSelection(elems(index))
    case _ => JsonSelection.empty
  }

  def apply(key: java.lang.String): JsonSelection = self match {
    case Json.Object(flds) =>
      flds.collectFirst { case (k, v) if k == key => v } match {
        case Some(v) => JsonSelection(v)
        case None    => JsonSelection.empty
      }
    case _ => JsonSelection.empty
  }

  def modify(path: DynamicOptic, f: Json => Json): Json =
    Json.modifyAt(self, path.nodes, 0, f)

  def set(path: DynamicOptic, value: Json): Json = modify(path, _ => value)

  def delete(path: DynamicOptic): Json = Json.deleteAt(self, path.nodes, 0)

  def merge(other: Json, strategy: MergeStrategy = MergeStrategy.Auto): Json =
    Json.mergeValues(self, other, DynamicOptic.root, strategy)

  def sortKeys: Json = self match {
    case Json.Object(flds) => Json.Object(flds.map { case (k, v) => (k, v.sortKeys) }.sortBy(_._1))
    case Json.Array(elems) => Json.Array(elems.map(_.sortKeys))
    case other             => other
  }

  def dropNulls: Json = self match {
    case Json.Object(flds) => Json.Object(flds.collect { case (k, v) if !v.isNull => (k, v.dropNulls) })
    case Json.Array(elems) => Json.Array(elems.map(_.dropNulls))
    case other             => other
  }

  def dropEmpty: Json = self match {
    case Json.Object(flds) =>
      val filtered = flds.flatMap { case (k, v) =>
        v.dropEmpty match {
          case Json.Object(f) if f.isEmpty => None
          case Json.Array(e) if e.isEmpty  => None
          case other                       => Some((k, other))
        }
      }
      Json.Object(filtered)
    case Json.Array(elems) =>
      val filtered = elems.map(_.dropEmpty).filter {
        case Json.Object(f) if f.isEmpty => false
        case Json.Array(e) if e.isEmpty  => false
        case _                           => true
      }
      Json.Array(filtered)
    case other => other
  }

  def compare(that: Json): Int = Json.compareJson(self, that)

  def toDynamicValue: DynamicValue = self match {
    case Json.Null       => DynamicValue.Primitive(PrimitiveValue.Unit)
    case Json.Boolean(v) => DynamicValue.Primitive(PrimitiveValue.Boolean(v))
    case Json.Number(v)  => DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(v)))
    case Json.String(v)  => DynamicValue.Primitive(PrimitiveValue.String(v))
    case Json.Array(es)  => DynamicValue.Sequence(es.map(_.toDynamicValue))
    case Json.Object(fs) => DynamicValue.Record(fs.map { case (k, v) => (k, v.toDynamicValue) })
  }

  def encode: java.lang.String = encode(WriterConfig)
  def encode(config: WriterConfig): java.lang.String = Json.encodeToString(self, config)

  def encodeToChunk: Chunk[Byte] = encodeToChunk(WriterConfig)
  def encodeToChunk(config: WriterConfig): Chunk[Byte] = Chunk.fromArray(encode(config).getBytes(StandardCharsets.UTF_8))

  // Aliases for encode
  def print: String = encode
  def print(config: WriterConfig): String = encode(config)

  // Write to a Writer
  def printTo(writer: Writer): Unit = printTo(writer, WriterConfig)
  def printTo(writer: Writer, config: WriterConfig): Unit = writer.write(encode(config))

  // Encode to bytes
  def encodeToBytes: scala.Array[Byte] = encodeToBytes(WriterConfig)
  def encodeToBytes(config: WriterConfig): scala.Array[Byte] = encode(config).getBytes(StandardCharsets.UTF_8)

  // Encode to ByteBuffer
  def encodeTo(buffer: ByteBuffer): Unit = encodeTo(buffer, WriterConfig)
  def encodeTo(buffer: ByteBuffer, config: WriterConfig): Unit = buffer.put(encodeToBytes(config))

  // Modification with error handling
  def modifyOrFail(path: DynamicOptic, pf: PartialFunction[Json, Json]): Either[JsonError, Json] = {
    val selection = get(path)
    selection.toEither match {
      case Left(err) => Left(JsonError.fromSchemaError(err))
      case Right(values) if values.isEmpty => Left(JsonError("Path not found", path))
      case Right(_) =>
        val f: Json => Json = j => if (pf.isDefinedAt(j)) pf(j) else j
        Right(modify(path, f))
    }
  }

  def setOrFail(path: DynamicOptic, value: Json): Either[JsonError, Json] = {
    val selection = get(path)
    selection.toEither match {
      case Left(err) => Left(JsonError.fromSchemaError(err))
      case Right(values) if values.isEmpty => Left(JsonError("Path not found", path))
      case Right(_) => Right(set(path, value))
    }
  }

  def deleteOrFail(path: DynamicOptic): Either[JsonError, Json] = {
    val selection = get(path)
    selection.toEither match {
      case Left(err) => Left(JsonError.fromSchemaError(err))
      case Right(values) if values.isEmpty => Left(JsonError("Path not found", path))
      case Right(_) => Right(delete(path))
    }
  }

  def insert(path: DynamicOptic, value: Json): Json = Json.insertAt(self, path.nodes, 0, value)

  def insertOrFail(path: DynamicOptic, value: Json): Either[JsonError, Json] =
    Json.insertAtOrFail(self, path.nodes, 0, value, path)

  // Patching (delegates to placeholder JsonPatch)
  def patch(@unused patch: JsonPatch): Either[JsonError, Json] = Right(self) // Placeholder - JsonPatch out of scope

  def patchUnsafe(jsonPatch: JsonPatch): Json = patch(jsonPatch).fold(throw _, identity)

  // Transformation
  def transformUp(f: (DynamicOptic, Json) => Json): Json =
    Json.transformUpImpl(self, DynamicOptic.root, f)

  def transformDown(f: (DynamicOptic, Json) => Json): Json =
    Json.transformDownImpl(self, DynamicOptic.root, f)

  def transformKeys(f: (DynamicOptic, java.lang.String) => java.lang.String): Json =
    Json.transformKeysImpl(self, DynamicOptic.root, f)

  // Filtering with path
  def filterNot(p: (DynamicOptic, Json) => scala.Boolean): Json =
    Json.filterNotImpl(self, DynamicOptic.root, p)

  def filter(p: (DynamicOptic, Json) => scala.Boolean): Json =
    filterNot((path, json) => !p(path, json))

  // Projection
  def project(paths: DynamicOptic*): Json = {
    val kvs = paths.flatMap { path =>
      get(path).toEither.toOption.getOrElse(Vector.empty).map(v => (path, v))
    }
    Json.fromKV(kvs).getOrElse(Json.Object.empty)
  }

  // Partitioning
  def partition(p: (DynamicOptic, Json) => scala.Boolean): (Json, Json) =
    (filter(p), filterNot(p))

  // Normalization
  def normalize: Json = {
    val sorted = sortKeys
    Json.normalizeNumbers(sorted)
  }

  // Diffing (placeholder - returns empty patch)
  def diff(@unused target: Json): JsonPatch = JsonPatch.empty // Placeholder - JsonPatch out of scope

  // Folding
  def foldDown[B](z: B)(f: (DynamicOptic, Json, B) => B): B =
    Json.foldDownImpl(self, DynamicOptic.root, z, f)

  def foldUp[B](z: B)(f: (DynamicOptic, Json, B) => B): B =
    Json.foldUpImpl(self, DynamicOptic.root, z, f)

  def foldDownOrFail[B](z: B)(f: (DynamicOptic, Json, B) => Either[JsonError, B]): Either[JsonError, B] =
    Json.foldDownOrFailImpl(self, DynamicOptic.root, z, f)

  def foldUpOrFail[B](z: B)(f: (DynamicOptic, Json, B) => Either[JsonError, B]): Either[JsonError, B] =
    Json.foldUpOrFailImpl(self, DynamicOptic.root, z, f)

  // Querying
  def query(p: (DynamicOptic, Json) => scala.Boolean): JsonSelection = {
    val results = foldDown(Vector.empty[(DynamicOptic, Json)]) { (path, json, acc) =>
      if (p(path, json)) acc :+ (path, json) else acc
    }
    JsonSelection.fromVector(results.map(_._2))
  }

  // Validation (placeholder - JsonSchema out of scope)
  def check(schema: JsonSchema): Option[SchemaError] = JsonSchema.validate(self, schema)
  def conforms(schema: JsonSchema): scala.Boolean = check(schema).isEmpty

  // KV representation
  def toKV: Seq[(DynamicOptic, Json)] = {
    foldDown(Vector.empty[(DynamicOptic, Json)]) { (path, json, acc) =>
      json match {
        case Json.Object(flds) if flds.nonEmpty => acc // Non-leaf, skip
        case Json.Array(elems) if elems.nonEmpty => acc // Non-leaf, skip
        case leaf => acc :+ (path, leaf)
      }
    }
  }

  // Typed decoding
  def as[A](implicit decoder: JsonDecoder[A]): Either[JsonError, A] = decoder.decode(self)
  def asUnsafe[A](implicit decoder: JsonDecoder[A]): A = as[A].fold(throw _, identity)

  override def hashCode(): Int = self match {
    case Json.Null       => 0
    case Json.Boolean(v) => v.hashCode()
    case Json.Number(v)  => BigDecimal(v).hashCode()
    case Json.String(v)  => v.hashCode()
    case Json.Array(es)  => es.hashCode()
    case Json.Object(fs) => fs.sortBy(_._1).hashCode()
  }



  override def toString: java.lang.String = encode
}

object Json {

  final case class Object(objectFields: Vector[(java.lang.String, Json)]) extends Json {
    override def isObject: scala.Boolean = true
    override def fields: Seq[(java.lang.String, Json)] = objectFields

    override def hashCode: Int = objectFields.sortBy(_._1).hashCode

    override def equals(that: Any): scala.Boolean = that match {
      case Object(f2) => this.objectFields.sortBy(_._1) == f2.sortBy(_._1)
      case _          => false
    }
  }

  object Object {
    val empty: Object = Object(Vector.empty)
    def apply(fields: (java.lang.String, Json)*): Object = Object(fields.toVector)
  }

  final case class Array(arrayElements: Vector[Json]) extends Json {
    override def isArray: scala.Boolean = true
    override def elements: Seq[Json] = arrayElements
  }

  object Array {
    val empty: Array = Array(Vector.empty)
    def apply(elements: Json*): Array = Array(elements.toVector)
  }

  final case class String(value: java.lang.String) extends Json {
    override def isString: scala.Boolean = true
    override def stringValue: Option[java.lang.String] = Some(value)
  }

  final case class Number(value: java.lang.String) extends Json {
    override def isNumber: scala.Boolean = true
    override def numberValue: Option[java.lang.String] = Some(value)
    lazy val toInt: Int = toBigDecimal.toInt
    lazy val toLong: Long = toBigDecimal.toLong
    lazy val toFloat: Float = value.toFloat
    lazy val toDouble: Double = value.toDouble
    lazy val toBigInt: BigInt = toBigDecimal.toBigInt
    lazy val toBigDecimal: BigDecimal = BigDecimal(value)
  }

  final case class Boolean(value: scala.Boolean) extends Json {
    override def isBoolean: scala.Boolean = true
    override def booleanValue: Option[scala.Boolean] = Some(value)
  }

  object Boolean {
    val True: Boolean = Boolean(true)
    val False: Boolean = Boolean(false)
  }

  case object Null extends Json {
    override def isNull: scala.Boolean = true
  }

  def number(n: Int): Number = Number(n.toString)
  def number(n: Long): Number = Number(n.toString)
  def number(n: Float): Number = Number(n.toString)
  def number(n: Double): Number = Number(n.toString)
  def number(n: BigInt): Number = Number(n.toString)
  def number(n: BigDecimal): Number = Number(n.toString)
  def number(n: Short): Number = Number(n.toString)
  def number(n: Byte): Number = Number(n.toString)

  def parse(s: java.lang.String): Either[JsonError, Json] = decode(s)

  def decode(s: java.lang.String): Either[JsonError, Json] = {
    try {
      Right(parseString(s, 0)._1)
    } catch {
      case e: Exception => Left(JsonError(e.getMessage))
    }
  }

  // Parse from CharSequence
  def parse(s: CharSequence): Either[JsonError, Json] = decode(s)
  def decode(s: CharSequence): Either[JsonError, Json] = decode(s.toString)

  // Parse from byte array
  def parse(bytes: scala.Array[Byte]): Either[JsonError, Json] = decode(bytes)
  def decode(bytes: scala.Array[Byte]): Either[JsonError, Json] =
    decode(new java.lang.String(bytes, StandardCharsets.UTF_8))

  // Parse from Chunk
  def parse(chunk: Chunk[Byte]): Either[JsonError, Json] = decode(chunk)
  def decode(chunk: Chunk[Byte]): Either[JsonError, Json] =
    decode(new java.lang.String(chunk.toArray, StandardCharsets.UTF_8))

  // Parse from ByteBuffer
  def parse(buffer: ByteBuffer): Either[JsonError, Json] = decode(buffer)
  def decode(buffer: ByteBuffer): Either[JsonError, Json] = {
    val bytes = new scala.Array[Byte](buffer.remaining())
    buffer.get(bytes)
    decode(bytes)
  }

  // Parse from Reader
  def parse(reader: Reader): Either[JsonError, Json] = decode(reader)
  def decode(reader: Reader): Either[JsonError, Json] = {
    try {
      val sb = new StringBuilder
      val buf = new scala.Array[Char](8192)
      var read = reader.read(buf)
      while (read != -1) {
        sb.appendAll(buf, 0, read)
        read = reader.read(buf)
      }
      decode(sb.toString)
    } catch {
      case e: Exception => Left(JsonError(e.getMessage))
    }
  }

  // Unsafe parse
  def parseUnsafe(s: java.lang.String): Json = parse(s).fold(throw _, identity)
  def decodeUnsafe(s: java.lang.String): Json = parseUnsafe(s)

  // Typed encoding
  def from[A](value: A)(implicit encoder: JsonEncoder[A]): Json = encoder.encode(value)

  // KV interop
  def fromKV(kvs: Seq[(DynamicOptic, Json)]): Either[JsonError, Json] = {
    try {
      var result: Json = Object.empty
      kvs.foreach { case (path, value) =>
        result = result.insert(path, value)
      }
      Right(result)
    } catch {
      case e: Exception => Left(JsonError(e.getMessage))
    }
  }

  def fromKVUnsafe(kvs: Seq[(DynamicOptic, Json)]): Json =
    fromKV(kvs).fold(throw _, identity)

  // JsonPatch interop (placeholders - out of scope)
  def fromJsonPatch(@unused patch: JsonPatch): Json = Object.empty // Placeholder
  def toJsonPatch(@unused json: Json): Either[JsonError, JsonPatch] = Right(JsonPatch.empty) // Placeholder

  def fromDynamicValue(value: DynamicValue): Json = value match {
    case DynamicValue.Primitive(pv)       => fromPrimitiveValue(pv)
    case DynamicValue.Record(flds)        => Object(flds.map { case (k, v) => (k, fromDynamicValue(v)) })
    case DynamicValue.Variant(name, v)    => Object(Vector("_type" -> String(name), "_value" -> fromDynamicValue(v)))
    case DynamicValue.Sequence(elems)     => Array(elems.map(fromDynamicValue))
    case DynamicValue.Map(entries)        => Array(entries.map { case (k, v) =>
        Object(Vector("key" -> fromDynamicValue(k), "value" -> fromDynamicValue(v)))
      })
  }

  private def fromPrimitiveValue(pv: PrimitiveValue): Json = pv match {
    case PrimitiveValue.Unit              => Null
    case PrimitiveValue.Boolean(v)        => Boolean(v)
    case PrimitiveValue.Byte(v)           => number(v)
    case PrimitiveValue.Short(v)          => number(v)
    case PrimitiveValue.Int(v)            => number(v)
    case PrimitiveValue.Long(v)           => number(v)
    case PrimitiveValue.Float(v)          => number(v)
    case PrimitiveValue.Double(v)         => number(v)
    case PrimitiveValue.Char(v)           => String(v.toString)
    case PrimitiveValue.String(v)         => String(v)
    case PrimitiveValue.BigInt(v)         => number(v)
    case PrimitiveValue.BigDecimal(v)     => number(v)
    case other                            => String(other.toString)
  }

  implicit val ordering: Ordering[Json] = (x: Json, y: Json) => x.compare(y)

  private def navigate(json: Json, nodes: IndexedSeq[DynamicOptic.Node], idx: Int): JsonSelection = {
    if (idx >= nodes.length) JsonSelection(json)
    else nodes(idx) match {
      case DynamicOptic.Node.Field(name) => json(name).flatMap(j => navigate(j, nodes, idx + 1))
      case DynamicOptic.Node.AtIndex(i)  => json(i).flatMap(j => navigate(j, nodes, idx + 1))
      case DynamicOptic.Node.Elements    => json match {
        case Array(elems) => JsonSelection.fromVector(elems).flatMap(j => navigate(j, nodes, idx + 1))
        case _            => JsonSelection.empty
      }
      case _ => JsonSelection.empty
    }
  }

  private def modifyAt(json: Json, nodes: IndexedSeq[DynamicOptic.Node], idx: Int, f: Json => Json): Json = {
    if (idx >= nodes.length) f(json)
    else nodes(idx) match {
      case DynamicOptic.Node.Field(name) => json match {
        case Object(flds) => Object(flds.map { case (k, v) =>
          if (k == name) (k, modifyAt(v, nodes, idx + 1, f)) else (k, v)
        })
        case other => other
      }
      case DynamicOptic.Node.AtIndex(i) => json match {
        case Array(elems) if i >= 0 && i < elems.size =>
          Array(elems.updated(i, modifyAt(elems(i), nodes, idx + 1, f)))
        case other => other
      }
      case _ => json
    }
  }

  private def deleteAt(json: Json, nodes: IndexedSeq[DynamicOptic.Node], idx: Int): Json = {
    if (idx >= nodes.length) Null
    else if (idx == nodes.length - 1) nodes(idx) match {
      case DynamicOptic.Node.Field(name) => json match {
        case Object(flds) => Object(flds.filterNot(_._1 == name))
        case other        => other
      }
      case DynamicOptic.Node.AtIndex(i) => json match {
        case Array(elems) if i >= 0 && i < elems.size =>
          Array(elems.take(i) ++ elems.drop(i + 1))
        case other => other
      }
      case _ => json
    }
    else nodes(idx) match {
      case DynamicOptic.Node.Field(name) => json match {
        case Object(flds) => Object(flds.map { case (k, v) =>
          if (k == name) (k, deleteAt(v, nodes, idx + 1)) else (k, v)
        })
        case other => other
      }
      case DynamicOptic.Node.AtIndex(i) => json match {
        case Array(elems) if i >= 0 && i < elems.size =>
          Array(elems.updated(i, deleteAt(elems(i), nodes, idx + 1)))
        case other => other
      }
      case _ => json
    }
  }

  private def mergeValues(left: Json, right: Json, path: DynamicOptic, strategy: MergeStrategy): Json = {
    strategy match {
      case MergeStrategy.Replace => right
      case MergeStrategy.Custom(f) => f(path, left, right)
      case _ => (left, right) match {
        case (Object(lf), Object(rf)) if strategy != MergeStrategy.Shallow =>
          val merged = lf.map { case (k, v) =>
            rf.find(_._1 == k) match {
              case Some((_, rv)) => (k, mergeValues(v, rv, path.field(k), strategy))
              case None          => (k, v)
            }
          }
          val newFields = rf.filterNot(kv => lf.exists(_._1 == kv._1))
          Object(merged ++ newFields)
        case (Array(le), Array(re)) if strategy == MergeStrategy.Concat || strategy == MergeStrategy.Auto =>
          Array(le ++ re)
        case _ => right
      }
    }
  }

  private def compareJson(x: Json, y: Json): Int = (x, y) match {
    case (Null, Null) => 0
    case (Null, _)    => -1
    case (_, Null)    => 1
    case (Boolean(a), Boolean(b)) => a.compare(b)
    case (Boolean(_), _) => -1
    case (_, Boolean(_)) => 1
    case (Number(a), Number(b)) => BigDecimal(a).compare(BigDecimal(b))
    case (Number(_), _) => -1
    case (_, Number(_)) => 1
    case (String(a), String(b)) => a.compare(b)
    case (String(_), _) => -1
    case (_, String(_)) => 1
    case (Array(a), Array(b)) => compareArrays(a, b)
    case (Array(_), _) => -1
    case (_, Array(_)) => 1
    case (Object(a), Object(b)) => compareObjects(a, b)
  }

  private def compareArrays(a: Vector[Json], b: Vector[Json]): Int = {
    val len = math.min(a.size, b.size)
    var i = 0
    while (i < len) {
      val cmp = a(i).compare(b(i))
      if (cmp != 0) return cmp
      i += 1
    }
    a.size.compare(b.size)
  }

  private def compareObjects(a: Vector[(java.lang.String, Json)], b: Vector[(java.lang.String, Json)]): Int = {
    val as = a.sortBy(_._1)
    val bs = b.sortBy(_._1)
    val len = math.min(as.size, bs.size)
    var i = 0
    while (i < len) {
      val (ak, av) = as(i)
      val (bk, bv) = bs(i)
      val kc = ak.compare(bk)
      if (kc != 0) return kc
      val vc = av.compare(bv)
      if (vc != 0) return vc
      i += 1
    }
    as.size.compare(bs.size)
  }

  private def encodeToString(json: Json, config: WriterConfig): java.lang.String = {
    val sb = new StringBuilder
    encodeValue(json, sb, 0, config)
    sb.toString
  }

  private def encodeValue(json: Json, sb: StringBuilder, indent: Int, config: WriterConfig): Unit = {
    json match {
      case Null => sb.append("null")
      case Boolean(v) => sb.append(if (v) "true" else "false")
      case Number(v) => sb.append(v)
      case String(v) => encodeString(v, sb, config)
      case Array(elems) =>
        sb.append('[')
        if (elems.nonEmpty) {
          val newIndent = indent + config.indentionStep
          var first = true
          elems.foreach { e =>
            if (!first) sb.append(',')
            first = false
            if (config.indentionStep > 0) { sb.append('\n'); sb.append(" " * newIndent) }
            encodeValue(e, sb, newIndent, config)
          }
          if (config.indentionStep > 0) { sb.append('\n'); sb.append(" " * indent) }
        }
        sb.append(']')
      case Object(flds) =>
        sb.append('{')
        if (flds.nonEmpty) {
          val newIndent = indent + config.indentionStep
          var first = true
          flds.foreach { case (k, v) =>
            if (!first) sb.append(',')
            first = false
            if (config.indentionStep > 0) { sb.append('\n'); sb.append(" " * newIndent) }
            encodeString(k, sb, config)
            sb.append(':')
            if (config.indentionStep > 0) sb.append(' ')
            encodeValue(v, sb, newIndent, config)
          }
          if (config.indentionStep > 0) { sb.append('\n'); sb.append(" " * indent) }
        }
        sb.append('}')
    }
  }

  private def encodeString(s: java.lang.String, sb: StringBuilder, config: WriterConfig): Unit = {
    sb.append('"')
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      c match {
        case '"'  => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\b' => sb.append("\\b")
        case '\f' => sb.append("\\f")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case _ if c < 32 || (config.escapeUnicode && c > 127) =>
          sb.append("\\u")
          sb.append(f"${c.toInt}%04x")
        case _ => sb.append(c)
      }
      i += 1
    }
    sb.append('"')
  }

  private def parseString(s: java.lang.String, start: Int): (Json, Int) = {
    val i = skipWhitespace(s, start)
    if (i >= s.length) throw new Exception("Unexpected end of input")
    s.charAt(i) match {
      case 'n' => if (s.startsWith("null", i)) (Null, i + 4) else throw new Exception(s"Invalid token at $i")
      case 't' => if (s.startsWith("true", i)) (Boolean(true), i + 4) else throw new Exception(s"Invalid token at $i")
      case 'f' => if (s.startsWith("false", i)) (Boolean(false), i + 5) else throw new Exception(s"Invalid token at $i")
      case '"' => parseStringValue(s, i)
      case '[' => parseArray(s, i)
      case '{' => parseObject(s, i)
      case c if c == '-' || c.isDigit => parseNumber(s, i)
      case c => throw new Exception(s"Unexpected character '$c' at $i")
    }
  }

  private def skipWhitespace(s: java.lang.String, i: Int): Int = {
    var j = i
    while (j < s.length && (s.charAt(j) == ' ' || s.charAt(j) == '\n' || s.charAt(j) == '\r' || s.charAt(j) == '\t')) j += 1
    j
  }

  private def parseStringValue(s: java.lang.String, start: Int): (Json, Int) = {
    val sb = new StringBuilder
    var i = start + 1
    while (i < s.length && s.charAt(i) != '"') {
      if (s.charAt(i) == '\\' && i + 1 < s.length) {
        s.charAt(i + 1) match {
          case '"'  => sb.append('"'); i += 2
          case '\\' => sb.append('\\'); i += 2
          case '/'  => sb.append('/'); i += 2
          case 'b'  => sb.append('\b'); i += 2
          case 'f'  => sb.append('\f'); i += 2
          case 'n'  => sb.append('\n'); i += 2
          case 'r'  => sb.append('\r'); i += 2
          case 't'  => sb.append('\t'); i += 2
          case 'u'  =>
            val hex = s.substring(i + 2, i + 6)
            sb.append(Integer.parseInt(hex, 16).toChar)
            i += 6
          case c => sb.append(c); i += 2
        }
      } else { sb.append(s.charAt(i)); i += 1 }
    }
    (String(sb.toString), i + 1)
  }

  private def parseNumber(s: java.lang.String, start: Int): (Json, Int) = {
    var i = start
    if (i < s.length && s.charAt(i) == '-') i += 1
    while (i < s.length && s.charAt(i).isDigit) i += 1
    if (i < s.length && s.charAt(i) == '.') { i += 1; while (i < s.length && s.charAt(i).isDigit) i += 1 }
    if (i < s.length && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
      i += 1
      if (i < s.length && (s.charAt(i) == '+' || s.charAt(i) == '-')) i += 1
      while (i < s.length && s.charAt(i).isDigit) i += 1
    }
    (Number(s.substring(start, i)), i)
  }

  private def parseArray(s: java.lang.String, start: Int): (Json, Int) = {
    var i = skipWhitespace(s, start + 1)
    val elems = Vector.newBuilder[Json]
    if (i < s.length && s.charAt(i) == ']') return (Array(Vector.empty), i + 1)
    while (true) {
      val (v, ni) = parseString(s, i)
      elems += v
      i = skipWhitespace(s, ni)
      if (i >= s.length) throw new Exception("Unexpected end of array")
      if (s.charAt(i) == ']') return (Array(elems.result()), i + 1)
      if (s.charAt(i) != ',') throw new Exception(s"Expected ',' or ']' at $i")
      i = skipWhitespace(s, i + 1)
    }
    throw new Exception("Unreachable")
  }

  private def parseObject(s: java.lang.String, start: Int): (Json, Int) = {
    var i = skipWhitespace(s, start + 1)
    val flds = Vector.newBuilder[(java.lang.String, Json)]
    if (i < s.length && s.charAt(i) == '}') return (Object(Vector.empty), i + 1)
    while (true) {
      if (s.charAt(i) != '"') throw new Exception(s"Expected '\"' at $i")
      val (keyJson, ki) = parseStringValue(s, i)
      val key = keyJson match {
        case String(k) => k
        case _         => throw new Exception("Expected string key")
      }
      i = skipWhitespace(s, ki)
      if (i >= s.length || s.charAt(i) != ':') throw new Exception(s"Expected ':' at $i")
      i = skipWhitespace(s, i + 1)
      val (v, vi) = parseString(s, i)
      flds += ((key, v))
      i = skipWhitespace(s, vi)
      if (i >= s.length) throw new Exception("Unexpected end of object")
      if (s.charAt(i) == '}') return (Object(flds.result()), i + 1)
      if (s.charAt(i) != ',') throw new Exception(s"Expected ',' or '}' at $i")
      i = skipWhitespace(s, i + 1)
    }
    throw new Exception("Unreachable")
  }

  // Insert implementation
  private def insertAt(json: Json, nodes: IndexedSeq[DynamicOptic.Node], idx: Int, value: Json): Json = {
    if (idx >= nodes.length) value
    else nodes(idx) match {
      case DynamicOptic.Node.Field(name) => json match {
        case Object(flds) =>
          val fieldIdx = flds.indexWhere(_._1 == name)
          if (fieldIdx >= 0) {
            Object(flds.updated(fieldIdx, (name, insertAt(flds(fieldIdx)._2, nodes, idx + 1, value))))
          } else if (idx == nodes.length - 1) {
            Object(flds :+ (name, value))
          } else {
            Object(flds :+ (name, insertAt(Object.empty, nodes, idx + 1, value)))
          }
        case other => other
      }
      case DynamicOptic.Node.AtIndex(i) => json match {
        case Array(elems) =>
          if (i >= 0 && i < elems.size) {
            Array(elems.updated(i, insertAt(elems(i), nodes, idx + 1, value)))
          } else if (i == elems.size && idx == nodes.length - 1) {
            Array(elems :+ value)
          } else {
            json
          }
        case other => other
      }
      case _ => json
    }
  }

  private def insertAtOrFail(
    json: Json,
    nodes: IndexedSeq[DynamicOptic.Node],
    idx: Int,
    value: Json,
    fullPath: DynamicOptic
  ): Either[JsonError, Json] = {
    try {
      Right(insertAt(json, nodes, idx, value))
    } catch {
      case e: Exception => Left(JsonError(e.getMessage, fullPath))
    }
  }

  // Transform implementations
  private def transformUpImpl(json: Json, path: DynamicOptic, f: (DynamicOptic, Json) => Json): Json = {
    val transformed = json match {
      case Object(flds) =>
        Object(flds.map { case (k, v) =>
          (k, transformUpImpl(v, path.field(k), f))
        })
      case Array(elems) =>
        Array(elems.zipWithIndex.map { case (e, i) =>
          transformUpImpl(e, path.at(i), f)
        })
      case other => other
    }
    f(path, transformed)
  }

  private def transformDownImpl(json: Json, path: DynamicOptic, f: (DynamicOptic, Json) => Json): Json = {
    val transformed = f(path, json)
    transformed match {
      case Object(flds) =>
        Object(flds.map { case (k, v) =>
          (k, transformDownImpl(v, path.field(k), f))
        })
      case Array(elems) =>
        Array(elems.zipWithIndex.map { case (e, i) =>
          transformDownImpl(e, path.at(i), f)
        })
      case other => other
    }
  }

  private def transformKeysImpl(
    json: Json,
    path: DynamicOptic,
    f: (DynamicOptic, java.lang.String) => java.lang.String
  ): Json = json match {
    case Object(flds) =>
      Object(flds.map { case (k, v) =>
        val newKey = f(path, k)
        (newKey, transformKeysImpl(v, path.field(k), f))
      })
    case Array(elems) =>
      Array(elems.zipWithIndex.map { case (e, i) =>
        transformKeysImpl(e, path.at(i), f)
      })
    case other => other
  }

  // Filter implementation
  private def filterNotImpl(
    json: Json,
    path: DynamicOptic,
    p: (DynamicOptic, Json) => scala.Boolean
  ): Json = json match {
    case Object(flds) =>
      Object(flds.flatMap { case (k, v) =>
        val childPath = path.field(k)
        if (p(childPath, v)) None
        else Some((k, filterNotImpl(v, childPath, p)))
      })
    case Array(elems) =>
      Array(elems.zipWithIndex.flatMap { case (e, i) =>
        val childPath = path.at(i)
        if (p(childPath, e)) None
        else Some(filterNotImpl(e, childPath, p))
      })
    case other => other
  }

  // Fold implementations
  private def foldDownImpl[B](
    json: Json,
    path: DynamicOptic,
    z: B,
    f: (DynamicOptic, Json, B) => B
  ): B = {
    val acc1 = f(path, json, z)
    json match {
      case Object(flds) =>
        flds.foldLeft(acc1) { case (acc, (k: java.lang.String, v)) =>
          foldDownImpl(v, path.field(k), acc, f)
        }
      case Array(elems) =>
        elems.zipWithIndex.foldLeft(acc1) { case (acc, (e, i)) =>
          foldDownImpl(e, path.at(i), acc, f)
        }
      case _ => acc1
    }
  }

  private def foldUpImpl[B](
    json: Json,
    path: DynamicOptic,
    z: B,
    f: (DynamicOptic, Json, B) => B
  ): B = {
    val childAcc = json match {
      case Object(flds) =>
        flds.foldLeft(z) { case (acc, (k: java.lang.String, v)) =>
          foldUpImpl(v, path.field(k), acc, f)
        }
      case Array(elems) =>
        elems.zipWithIndex.foldLeft(z) { case (acc, (e, i)) =>
          foldUpImpl(e, path.at(i), acc, f)
        }
      case _ => z
    }
    f(path, json, childAcc)
  }

  private def foldDownOrFailImpl[B](
    json: Json,
    path: DynamicOptic,
    z: B,
    f: (DynamicOptic, Json, B) => Either[JsonError, B]
  ): Either[JsonError, B] = {
    f(path, json, z).flatMap { acc1 =>
      json match {
        case Object(flds) =>
          flds.foldLeft[Either[JsonError, B]](Right(acc1)) { case (accE, (k: java.lang.String, v)) =>
            accE.flatMap(acc => foldDownOrFailImpl(v, path.field(k), acc, f))
          }
        case Array(elems) =>
          elems.zipWithIndex.foldLeft[Either[JsonError, B]](Right(acc1)) { case (accE, (e, i)) =>
            accE.flatMap(acc => foldDownOrFailImpl(e, path.at(i), acc, f))
          }
        case _ => Right(acc1)
      }
    }
  }

  private def foldUpOrFailImpl[B](
    json: Json,
    path: DynamicOptic,
    z: B,
    f: (DynamicOptic, Json, B) => Either[JsonError, B]
  ): Either[JsonError, B] = {
    val childAccE: Either[JsonError, B] = json match {
      case Object(flds) =>
        flds.foldLeft[Either[JsonError, B]](Right(z)) { case (accE, (k: java.lang.String, v)) =>
          accE.flatMap(acc => foldUpOrFailImpl(v, path.field(k), acc, f))
        }
      case Array(elems) =>
        elems.zipWithIndex.foldLeft[Either[JsonError, B]](Right(z)) { case (accE, (e, i)) =>
          accE.flatMap(acc => foldUpOrFailImpl(e, path.at(i), acc, f))
        }
      case _ => Right(z)
    }
    childAccE.flatMap(childAcc => f(path, json, childAcc))
  }

  // Normalize numbers (remove trailing zeros, normalize exponent)
  private def normalizeNumbers(json: Json): Json = json match {
    case Number(v) =>
      try {
        val bd = BigDecimal(v)
        Number(bd.underlying.stripTrailingZeros().toPlainString)
      } catch {
        case _: Exception => json
      }
    case Object(flds) => Object(flds.map { case (k, v) => (k, normalizeNumbers(v)) })
    case Array(elems) => Array(elems.map(normalizeNumbers))
    case other => other
  }
}
