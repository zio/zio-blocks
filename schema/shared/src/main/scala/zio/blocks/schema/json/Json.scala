package zio.blocks.schema.json

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaError}

import java.io.{Reader, Writer}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * A JSON value represented as a Scala data type.
 *
 * Json provides a type-safe representation of JSON data with support for:
 *   - Type checking (isObject, isArray, etc.)
 *   - Type casting (asObject, asArray, etc.)
 *   - Navigation (get, apply)
 *   - Modification (set, delete, insert, modify)
 *   - Serialization (print, encode)
 */
sealed trait Json extends Product with Serializable { self =>

  // ============ Type Tests ============

  /** Returns true if this is a JSON object. */
  def isObject: scala.Boolean = false

  /** Returns true if this is a JSON array. */
  def isArray: scala.Boolean = false

  /** Returns true if this is a JSON string. */
  def isString: scala.Boolean = false

  /** Returns true if this is a JSON number. */
  def isNumber: scala.Boolean = false

  /** Returns true if this is a JSON boolean. */
  def isBoolean: scala.Boolean = false

  /** Returns true if this is JSON null. */
  def isNull: scala.Boolean = false

  // ============ Type Accessors (returning JsonSelection) ============

  /**
   * Returns a [[JsonSelection]] containing this value if it is an object,
   * otherwise an empty selection.
   */
  def asObject: JsonSelection = if (isObject) JsonSelection(self) else JsonSelection.empty

  /**
   * Returns a [[JsonSelection]] containing this value if it is an array,
   * otherwise an empty selection.
   */
  def asArray: JsonSelection = if (isArray) JsonSelection(self) else JsonSelection.empty

  /**
   * Returns a [[JsonSelection]] containing this value if it is a string,
   * otherwise an empty selection.
   */
  def asString: JsonSelection = if (isString) JsonSelection(self) else JsonSelection.empty

  /**
   * Returns a [[JsonSelection]] containing this value if it is a number,
   * otherwise an empty selection.
   */
  def asNumber: JsonSelection = if (isNumber) JsonSelection(self) else JsonSelection.empty

  /**
   * Returns a [[JsonSelection]] containing this value if it is a boolean,
   * otherwise an empty selection.
   */
  def asBoolean: JsonSelection = if (isBoolean) JsonSelection(self) else JsonSelection.empty

  /**
   * Returns a [[JsonSelection]] containing this value if it is null, otherwise
   * an empty selection.
   */
  def asNull: JsonSelection = if (isNull) JsonSelection(self) else JsonSelection.empty

  // ============ Direct Accessors ============

  /**
   * If this is an object, returns its fields as key-value pairs. Otherwise
   * returns an empty sequence.
   */
  def fields: Seq[(Predef.String, Json)] = Seq.empty

  /**
   * If this is an array, returns its elements. Otherwise returns an empty
   * sequence.
   */
  def elements: Seq[Json] = Seq.empty

  /** Returns the string value if this is a string, or None otherwise. */
  def stringValue: Option[String] = None

  /**
   * Returns the number value as a string if this is a number, or None
   * otherwise.
   */
  def numberValue: Option[String] = None

  /** Returns the boolean value if this is a boolean, or None otherwise. */
  def booleanValue: Option[Boolean] = None

  // ============ Navigation ============

  /**
   * Navigates to values at the given path.
   *
   * @param path
   *   The path to navigate
   * @return
   *   A [[JsonSelection]] containing values at the path
   */
  def get(path: DynamicOptic): JsonSelection =
    if (path.nodes.isEmpty) JsonSelection(self)
    else {
      var current: JsonSelection = JsonSelection(self)
      var idx                    = 0
      while (idx < path.nodes.length && current.nonEmpty) {
        val node = path.nodes(idx)
        current = node match {
          case DynamicOptic.Node.Field(name) => current.flatMap(_.apply(name))
          case DynamicOptic.Node.AtIndex(i)  => current.flatMap(_.apply(i))
          case DynamicOptic.Node.Elements    =>
            current.flatMap(j => JsonSelection.fromVector(j.elements.toVector))
          case _ => JsonSelection.fail(s"Unsupported path node: $node")
        }
        idx += 1
      }
      current
    }

  /** Alias for [[get]]. */
  def apply(path: DynamicOptic): JsonSelection = get(path)

  /**
   * If this is an object, returns a selection containing the value at the given
   * key. Returns an empty selection if not an object or key is not present.
   *
   * @param key
   *   The object key
   */
  def apply(@annotation.unused key: Predef.String): JsonSelection = JsonSelection.empty

  /**
   * If this is an array, returns a selection containing the element at the
   * given index. Returns an empty selection if not an array or index is out of
   * bounds.
   *
   * @param index
   *   The array index (0-based)
   */
  def apply(@annotation.unused index: Int): JsonSelection = JsonSelection.empty

  // ============ Modification ============

  /**
   * Modifies values at the given path using the provided function.
   *
   * If the path does not exist, returns this JSON unchanged.
   *
   * @param path
   *   The path to values to modify
   * @param f
   *   The modification function
   * @return
   *   The modified JSON
   */
  def modify(path: DynamicOptic, f: Json => Json): Json =
    modifyOrFail(path, { case j => f(j) }).getOrElse(this)

  /**
   * Modifies values at the given path using a partial function.
   *
   * Values for which the partial function is not defined are left unchanged.
   *
   * @param path
   *   The path to values to modify
   * @param pf
   *   The partial modification function
   * @return
   *   Either an error if the path is invalid, or the modified JSON
   */
  def modifyOrFail(path: DynamicOptic, pf: PartialFunction[Json, Json]): Either[JsonError, Json] = {
    val f: Json => Either[JsonError, Json] = { j =>
      if (pf.isDefinedAt(j)) Right(pf(j))
      else Right(j) // Leave unchanged if partial function not defined
    }
    if (path.nodes.isEmpty) f(this)
    else modifyAtPath(path.nodes, 0, f)
  }

  /** Sets the value at the given path. */
  def set(path: DynamicOptic, value: Json): Json = modify(path, _ => value)

  /**
   * Sets the value at the given path, returning an error if the path doesn't
   * exist.
   */
  def setOrFail(path: DynamicOptic, value: Json): Either[JsonError, Json] = modifyOrFail(path, { case _ => value })

  /** Deletes the value at the given path. */
  def delete(path: DynamicOptic): Json = deleteOrFail(path).getOrElse(this)

  /**
   * Deletes the value at the given path, returning an error if the path doesn't
   * exist.
   */
  def deleteOrFail(path: DynamicOptic): Either[JsonError, Json] =
    if (path.nodes.isEmpty) Left(JsonError("Cannot delete root"))
    else deleteAtPath(path.nodes, 0)

  /** Inserts a value at the given path. */
  def insert(path: DynamicOptic, value: Json): Json = insertOrFail(path, value).getOrElse(this)

  /**
   * Inserts a value at the given path, returning an error if insertion fails.
   */
  def insertOrFail(path: DynamicOptic, value: Json): Either[JsonError, Json] =
    if (path.nodes.isEmpty) Right(value)
    else insertAtPath(path.nodes, 0, value)

  // ============ Merging ============

  /** Merges this JSON value with another using the default merge strategy. */
  def merge(that: Json): Json = merge(that, MergeStrategy.Auto)

  /** Merges this JSON value with another using the specified merge strategy. */
  def merge(that: Json, strategy: MergeStrategy): Json = strategy.merge(DynamicOptic.root, this, that)

  // ============ Patching ============

  /**
   * Applies a [[JsonPatch]] to this JSON.
   *
   * @param patch
   *   The patch to apply
   * @return
   *   Either an error if the patch cannot be applied, or the patched JSON
   */
  def patch(patch: JsonPatch): Either[JsonError, Json] = ???

  /**
   * Applies a [[JsonPatch]], throwing on failure.
   *
   * @param patch
   *   The patch to apply
   * @return
   *   The patched JSON
   * @throws JsonError
   *   if the patch cannot be applied
   */
  def patchUnsafe(patch: JsonPatch): Json = this.patch(patch).fold(throw _, identity)

  // ============ Diffing ============

  /**
   * Computes a [[JsonPatch]] that transforms this JSON into the target.
   *
   * @param target
   *   The target JSON
   * @return
   *   A patch that transforms this into target
   */
  def diff(target: Json): JsonPatch = ???

  // ============ Validation ============

  /**
   * Validates this JSON against a [[JsonSchema]].
   *
   * @param schema
   *   The schema to validate against
   * @return
   *   `None` if valid, `Some(error)` if invalid
   */
  def check(schema: JsonSchema): Option[SchemaError] = ???

  /**
   * Returns `true` if this JSON conforms to the given [[JsonSchema]].
   */
  def conforms(schema: JsonSchema): scala.Boolean = check(schema).isEmpty

  // ============ Transformations ============

  /**
   * Transforms all values in this JSON bottom-up (children before parents).
   *
   * @param f
   *   The transformation function receiving path and value
   * @return
   *   The transformed JSON
   */
  def transformUp(f: (DynamicOptic, Json) => Json): Json =
    transformUpInternal(DynamicOptic.root, f)

  private def transformUpInternal(path: DynamicOptic, f: (DynamicOptic, Json) => Json): Json = {
    val transformed = this match {
      case Json.Object(flds) =>
        Json.Object(flds.map { case (k, v) =>
          (k, v.transformUpInternal(path.field(k), f))
        })
      case Json.Array(elems) =>
        Json.Array(elems.zipWithIndex.map { case (v, i) =>
          v.transformUpInternal(path.at(i), f)
        })
      case _ => this
    }
    f(path, transformed)
  }

  /**
   * Transforms all values in this JSON top-down (parents before children).
   *
   * @param f
   *   The transformation function receiving path and value
   * @return
   *   The transformed JSON
   */
  def transformDown(f: (DynamicOptic, Json) => Json): Json =
    transformDownInternal(DynamicOptic.root, f)

  private def transformDownInternal(path: DynamicOptic, f: (DynamicOptic, Json) => Json): Json = {
    val transformed = f(path, this)
    transformed match {
      case Json.Object(flds) =>
        Json.Object(flds.map { case (k, v) =>
          (k, v.transformDownInternal(path.field(k), f))
        })
      case Json.Array(elems) =>
        Json.Array(elems.zipWithIndex.map { case (v, i) =>
          v.transformDownInternal(path.at(i), f)
        })
      case other => other
    }
  }

  /**
   * Transforms all object keys in this JSON.
   *
   * @param f
   *   The key transformation function receiving path and key
   * @return
   *   The transformed JSON
   */
  def transformKeys(f: (DynamicOptic, String) => String): Json =
    transformKeysInternal(DynamicOptic.root, f)

  private def transformKeysInternal(path: DynamicOptic, f: (DynamicOptic, String) => String): Json =
    this match {
      case Json.Object(flds) =>
        Json.Object(flds.map { case (k, v) =>
          val newKey = f(path, k)
          (newKey, v.transformKeysInternal(path.field(k), f))
        })
      case Json.Array(elems) =>
        Json.Array(elems.zipWithIndex.map { case (v, i) =>
          v.transformKeysInternal(path.at(i), f)
        })
      case other => other
    }

  /**
   * Removes entries matching the predicate.
   *
   * For objects, removes matching key-value pairs. For arrays, removes matching
   * elements.
   *
   * @param p
   *   The predicate receiving path and value
   * @return
   *   The filtered JSON
   */
  def filterNot(p: (DynamicOptic, Json) => scala.Boolean): Json =
    filterNotInternal(DynamicOptic.root, p)

  private def filterNotInternal(path: DynamicOptic, p: (DynamicOptic, Json) => scala.Boolean): Json =
    this match {
      case Json.Object(flds) =>
        Json.Object(flds.collect {
          case (k, v) if !p(path.field(k), v) =>
            (k, v.filterNotInternal(path.field(k), p))
        })
      case Json.Array(elems) =>
        Json.Array(elems.zipWithIndex.collect {
          case (v, i) if !p(path.at(i), v) =>
            v.filterNotInternal(path.at(i), p)
        })
      case other => other
    }

  /**
   * Keeps only entries matching the predicate.
   *
   * @param p
   *   The predicate receiving path and value
   * @return
   *   The filtered JSON
   */
  def filter(p: (DynamicOptic, Json) => scala.Boolean): Json =
    filterNot((path, json) => !p(path, json))

  /**
   * Returns this JSON with all null values removed from objects.
   */
  def dropNulls: Json = this match {
    case Json.Object(flds) =>
      Json.Object(flds.collect { case (k, v) if !v.isNull => (k, v.dropNulls) })
    case Json.Array(elems) =>
      Json.Array(elems.map(_.dropNulls))
    case other =>
      other
  }

  /**
   * Returns this JSON with empty objects and arrays removed.
   */
  def dropEmpty: Json = this match {
    case Json.Object(flds) =>
      val filtered = flds.collect { case (k, v) =>
        val dropped = v.dropEmpty
        dropped match {
          case Json.Object(f) if f.isEmpty => None
          case Json.Array(e) if e.isEmpty  => None
          case other                       => Some((k, other))
        }
      }.flatten
      Json.Object(filtered)
    case Json.Array(elems) =>
      val filtered = elems.map(_.dropEmpty).filter {
        case Json.Object(f) if f.isEmpty => false
        case Json.Array(e) if e.isEmpty  => false
        case _                           => true
      }
      Json.Array(filtered)
    case other =>
      other
  }

  /**
   * Returns this JSON with all object keys sorted alphabetically (recursive).
   */
  def sortKeys: Json = this match {
    case Json.Object(flds) =>
      Json.Object(flds.map { case (k, v) => (k, v.sortKeys) }.sortBy(_._1))
    case Json.Array(elems) =>
      Json.Array(elems.map(_.sortKeys))
    case other =>
      other
  }

  /**
   * Returns a normalized version of this JSON.
   *
   * Normalization includes:
   *   - Sorting object keys alphabetically
   *   - Normalizing number representations
   *
   * Useful for comparison and hashing.
   */
  def normalize: Json = sortKeys

  /**
   * Flattens this JSON to a sequence of path-value pairs.
   *
   * Only leaf values (primitives, empty arrays, empty objects) are included.
   */
  def toKV: Seq[(DynamicOptic, Json)] = toKVInternal(DynamicOptic.root)

  private def toKVInternal(path: DynamicOptic): Seq[(DynamicOptic, Json)] = this match {
    case Json.Object(fields) if fields.nonEmpty =>
      fields.flatMap { case (k, v) => v.toKVInternal(path.field(k)) }
    case Json.Array(elems) if elems.nonEmpty =>
      elems.zipWithIndex.flatMap { case (v, i) => v.toKVInternal(path.at(i)) }
    case _ =>
      Seq((path, this))
  }

  /**
   * Folds over this JSON bottom-up (children before parents).
   *
   * @param z
   *   The initial accumulator value
   * @param f
   *   The fold function receiving path, value, and accumulator
   * @tparam B
   *   The accumulator type
   * @return
   *   The final accumulated value
   */
  def foldUp[B](z: B)(f: (DynamicOptic, Json, B) => B): B =
    foldUpInternal(DynamicOptic.root, z, f)

  private def foldUpInternal[B](path: DynamicOptic, z: B, f: (DynamicOptic, Json, B) => B): B = {
    val childResult = this match {
      case Json.Object(flds) =>
        flds.foldLeft(z) { case (acc, (k, v)) =>
          v.foldUpInternal(path.field(k), acc, f)
        }
      case Json.Array(elems) =>
        elems.zipWithIndex.foldLeft(z) { case (acc, (v, i)) =>
          v.foldUpInternal(path.at(i), acc, f)
        }
      case _ => z
    }
    f(path, this, childResult)
  }

  /**
   * Folds over this JSON top-down (parents before children).
   *
   * @param z
   *   The initial accumulator value
   * @param f
   *   The fold function receiving path, value, and accumulator
   * @tparam B
   *   The accumulator type
   * @return
   *   The final accumulated value
   */
  def foldDown[B](z: B)(f: (DynamicOptic, Json, B) => B): B =
    foldDownInternal(DynamicOptic.root, z, f)

  private def foldDownInternal[B](path: DynamicOptic, z: B, f: (DynamicOptic, Json, B) => B): B = {
    val thisResult = f(path, this, z)
    this match {
      case Json.Object(flds) =>
        flds.foldLeft(thisResult) { case (acc, (k, v)) =>
          v.foldDownInternal(path.field(k), acc, f)
        }
      case Json.Array(elems) =>
        elems.zipWithIndex.foldLeft(thisResult) { case (acc, (v, i)) =>
          v.foldDownInternal(path.at(i), acc, f)
        }
      case _ => thisResult
    }
  }

  /**
   * Folds over this JSON top-down, allowing the fold function to fail.
   *
   * Short-circuits on first failure.
   *
   * @param z
   *   The initial accumulator value
   * @param f
   *   The fold function that may fail
   * @tparam B
   *   The accumulator type
   * @return
   *   Either an error or the final accumulated value
   */
  def foldDownOrFail[B](z: B)(f: (DynamicOptic, Json, B) => Either[JsonError, B]): Either[JsonError, B] =
    foldDownOrFailInternal(DynamicOptic.root, z, f)

  private def foldDownOrFailInternal[B](
    path: DynamicOptic,
    z: B,
    f: (DynamicOptic, Json, B) => Either[JsonError, B]
  ): Either[JsonError, B] =
    f(path, this, z).flatMap { thisResult =>
      this match {
        case Json.Object(flds) =>
          flds.foldLeft[Either[JsonError, B]](Right(thisResult)) { case (acc, (k, v)) =>
            acc.flatMap(a => v.foldDownOrFailInternal(path.field(k), a, f))
          }
        case Json.Array(elems) =>
          elems.zipWithIndex.foldLeft[Either[JsonError, B]](Right(thisResult)) { case (acc, (v, i)) =>
            acc.flatMap(a => v.foldDownOrFailInternal(path.at(i), a, f))
          }
        case _ => Right(thisResult)
      }
    }

  /**
   * Folds over this JSON bottom-up, allowing the fold function to fail.
   *
   * Short-circuits on first failure.
   *
   * @param z
   *   The initial accumulator value
   * @param f
   *   The fold function that may fail
   * @tparam B
   *   The accumulator type
   * @return
   *   Either an error or the final accumulated value
   */
  def foldUpOrFail[B](z: B)(f: (DynamicOptic, Json, B) => Either[JsonError, B]): Either[JsonError, B] =
    foldUpOrFailInternal(DynamicOptic.root, z, f)

  private def foldUpOrFailInternal[B](
    path: DynamicOptic,
    z: B,
    f: (DynamicOptic, Json, B) => Either[JsonError, B]
  ): Either[JsonError, B] = {
    val childResult: Either[JsonError, B] = this match {
      case Json.Object(flds) =>
        flds.foldLeft[Either[JsonError, B]](Right(z)) { case (acc, (k, v)) =>
          acc.flatMap(a => v.foldUpOrFailInternal(path.field(k), a, f))
        }
      case Json.Array(elems) =>
        elems.zipWithIndex.foldLeft[Either[JsonError, B]](Right(z)) { case (acc, (v, i)) =>
          acc.flatMap(a => v.foldUpOrFailInternal(path.at(i), a, f))
        }
      case _ => Right(z)
    }
    childResult.flatMap(cr => f(path, this, cr))
  }

  // ============ Querying ============

  /**
   * Selects all values matching the predicate.
   *
   * @param p
   *   The predicate receiving path and value
   * @return
   *   A [[JsonSelection]] containing matching values
   */
  def query(p: (DynamicOptic, Json) => scala.Boolean): JsonSelection = {
    val results = foldDown(Vector.empty[Json]) { (path, json, acc) =>
      if (p(path, json)) acc :+ json else acc
    }
    JsonSelection.fromVector(results)
  }

  // ============ Projection ============

  /**
   * Projects this JSON to include only the specified paths.
   *
   * Paths that don't exist are ignored. Structure is preserved.
   *
   * @param paths
   *   The paths to include
   * @return
   *   A new JSON containing only the specified paths
   */
  def project(paths: DynamicOptic*): Json =
    if (paths.isEmpty) Json.Null
    else {
      var result: Json = Json.Null
      paths.foreach { path =>
        get(path).toEither match {
          case Right(values) if values.nonEmpty =>
            result = result.insert(path, values.head)
          case _ => // path not found, skip
        }
      }
      result
    }

  // ============ Partitioning ============

  /**
   * Partitions this JSON into two based on a predicate.
   *
   * Returns a tuple where the first element contains entries satisfying the
   * predicate, and the second contains entries that don't.
   *
   * @param p
   *   The predicate receiving path and value
   * @return
   *   A tuple of (matching, non-matching) JSON values
   */
  def partition(p: (DynamicOptic, Json) => scala.Boolean): (Json, Json) =
    (filter(p), filterNot(p))

  // ============ Serialization ============

  /**
   * Encodes this JSON to a compact string (no extra whitespace).
   */
  def print: String = encode(WriterConfig)

  /**
   * Encodes this JSON to a string using the specified configuration.
   *
   * @param config
   *   Writer configuration (indentation, unicode escaping, etc.)
   */
  def print(config: WriterConfig): String = encode(config)

  /**
   * Alias for [[print]].
   */
  def encode: String = encode(WriterConfig)

  /**
   * Encodes this JSON to a string using the specified configuration.
   *
   * @param config
   *   Writer configuration
   */
  def encode(config: WriterConfig): String = {
    val sb = new StringBuilder
    if (config.indentionStep > 0) {
      printPrettyTo(sb, config.indentionStep, 0, config.escapeUnicode)
    } else {
      printTo(sb, config.escapeUnicode)
    }
    sb.toString
  }

  /**
   * Encodes this JSON and writes to the provided [[Writer]].
   *
   * @param writer
   *   The writer to write to
   */
  def printTo(writer: Writer): Unit = printTo(writer, WriterConfig)

  /**
   * Encodes this JSON and writes to the provided [[Writer]] with configuration.
   *
   * @param writer
   *   The writer to write to
   * @param config
   *   Writer configuration
   */
  def printTo(writer: Writer, config: WriterConfig): Unit =
    writer.write(encode(config))

  /** Prints this JSON value as a pretty-printed string. */
  def printPretty: String = printPretty(2)

  /**
   * Prints this JSON value as a pretty-printed string with the given
   * indentation.
   */
  def printPretty(indent: Int): String = {
    val sb = new StringBuilder
    printPrettyTo(sb, indent, 0, escapeUnicode = false)
    sb.toString
  }

  /** Encodes this JSON value to UTF-8 bytes. */
  def encodeToBytes: Array[Byte] = encodeToBytes(WriterConfig)

  /**
   * Encodes this JSON value to UTF-8 bytes using the specified configuration.
   */
  def encodeToBytes(config: WriterConfig): Array[Byte] =
    encode(config).getBytes(StandardCharsets.UTF_8)

  /**
   * Encodes this JSON to a [[Chunk]] of bytes (UTF-8).
   */
  def encodeToChunk: Chunk[Byte] = encodeToChunk(WriterConfig)

  /**
   * Encodes this JSON to a [[Chunk]] of bytes (UTF-8) with configuration.
   *
   * @param config
   *   Writer configuration
   */
  def encodeToChunk(config: WriterConfig): Chunk[Byte] =
    Chunk.fromArray(encodeToBytes(config))

  /** Encodes this JSON value to a ByteBuffer. */
  def encodeTo(buffer: ByteBuffer): Unit = encodeTo(buffer, WriterConfig)

  /**
   * Encodes this JSON value to a ByteBuffer using the specified configuration.
   */
  def encodeTo(buffer: ByteBuffer, config: WriterConfig): Unit =
    buffer.put(encodeToBytes(config))

  // ============ Internal decoding ============

  /**
   * Internal: decode using an explicit codec.
   */
  private[json] def decodeWith[A](codec: JsonBinaryCodec[A]): Either[JsonError, A] =
    codec.decode(encodeToBytes) match {
      case Right(value) => Right(value)
      case Left(err)    => Left(JsonError.fromSchemaError(err))
    }

  // ============ Equality and Hashing ============

  override def hashCode(): Int = self match {
    case Json.Null         => 0
    case Json.Boolean(v)   => v.hashCode()
    case Json.Number(v)    => BigDecimal(v).hashCode()
    case Json.String(v)    => v.hashCode()
    case Json.Array(elems) => elems.hashCode()
    case Json.Object(flds) => flds.sortBy(_._1).hashCode()
  }

  override def equals(that: Any): scala.Boolean = that match {
    case other: Json => structuralEquals(other)
    case _           => false
  }

  override def toString: String = print

  /** Compares this JSON value with another for structural equality. */
  def structuralEquals(that: Json): scala.Boolean = {
    // Use reference equality check first to avoid infinite recursion
    if (this.asInstanceOf[AnyRef] eq that.asInstanceOf[AnyRef]) return true
    // Check types and values
    (self, that) match {
      case (Json.Object(f1), Json.Object(f2)) => f1.toMap == f2.toMap
      case (Json.Array(e1), Json.Array(e2))   =>
        e1.length == e2.length && e1.zip(e2).forall { case (a, b) => a.structuralEquals(b) }
      case (Json.String(s1), Json.String(s2))   => s1 == s2
      case (Json.Number(n1), Json.Number(n2))   => BigDecimal(n1) == BigDecimal(n2)
      case (Json.Boolean(b1), Json.Boolean(b2)) => b1 == b2
      case _                                    => false
    }
  }

  // ============ DynamicValue Interop ============

  /**
   * Converts this JSON to a [[DynamicValue]].
   *
   * This conversion is lossless; all JSON values can be represented as
   * DynamicValue.
   */
  def toDynamicValue: DynamicValue = this match {
    case Json.Null =>
      DynamicValue.Primitive(PrimitiveValue.Unit)
    case Json.Boolean(v) =>
      DynamicValue.Primitive(PrimitiveValue.Boolean(v))
    case Json.Number(v) =>
      DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(v)))
    case Json.String(v) =>
      DynamicValue.Primitive(PrimitiveValue.String(v))
    case Json.Array(elems) =>
      DynamicValue.Sequence(elems.map(_.toDynamicValue))
    case Json.Object(flds) =>
      DynamicValue.Record(flds.map { case (k, v) => (k, v.toDynamicValue) })
  }

  // ============ Typed Decoding ============

  /**
   * Decodes this JSON to a typed value.
   *
   * Uses implicit [[JsonDecoder]] which prefers explicit codecs over schema
   * derivation.
   *
   * @tparam A
   *   The target type
   * @return
   *   Either an error or the decoded value
   */
  def as[A](implicit decoder: JsonDecoder[A]): Either[JsonError, A] = decoder.decode(this)

  /**
   * Decodes this JSON to a typed value, throwing on failure.
   *
   * @tparam A
   *   The target type
   * @return
   *   The decoded value
   * @throws JsonError
   *   if decoding fails
   */
  def asUnsafe[A](implicit decoder: JsonDecoder[A]): A = as[A].fold(throw _, identity)

  // ============ Comparison ============

  /**
   * Compares this JSON to another for ordering.
   *
   * Ordering is defined as: Null < Boolean < Number < String < Array < Object
   */
  def compare(that: Json): Int = (this, that) match {
    case (Json.Null, Json.Null)             => 0
    case (Json.Null, _)                     => -1
    case (_, Json.Null)                     => 1
    case (Json.Boolean(a), Json.Boolean(b)) => a.compare(b)
    case (Json.Boolean(_), _)               => -1
    case (_, Json.Boolean(_))               => 1
    case (Json.Number(a), Json.Number(b))   => BigDecimal(a).compare(BigDecimal(b))
    case (Json.Number(_), _)                => -1
    case (_, Json.Number(_))                => 1
    case (Json.String(a), Json.String(b))   => a.compare(b)
    case (Json.String(_), _)                => -1
    case (_, Json.String(_))                => 1
    case (Json.Array(a), Json.Array(b))     => Json.compareArrays(a, b)
    case (Json.Array(_), _)                 => -1
    case (_, Json.Array(_))                 => 1
    case (Json.Object(a), Json.Object(b))   => Json.compareObjects(a, b)
  }

  // ============ Internal Helpers ============

  /** Returns the type label for error messages. */
  protected def typeLabel: Predef.String

  /**
   * Returns true if this value is empty (null, empty object, or empty array).
   */
  protected def isEmpty: scala.Boolean = this match {
    case Json.Null         => true
    case Json.Object(flds) => flds.isEmpty
    case Json.Array(elems) => elems.isEmpty
    case _                 => false
  }

  /** Internal path modification. */
  protected def modifyAtPath(
    nodes: IndexedSeq[DynamicOptic.Node],
    idx: Int,
    f: Json => Either[JsonError, Json]
  ): Either[JsonError, Json]

  /** Internal path deletion. */
  protected def deleteAtPath(nodes: IndexedSeq[DynamicOptic.Node], idx: Int): Either[JsonError, Json]

  /** Internal path insertion. */
  protected def insertAtPath(nodes: IndexedSeq[DynamicOptic.Node], idx: Int, value: Json): Either[JsonError, Json]

  /** Prints to a StringBuilder. */
  protected def printTo(sb: StringBuilder, escapeUnicode: scala.Boolean): Unit

  /** Pretty prints to a StringBuilder. */
  protected def printPrettyTo(sb: StringBuilder, indent: Int, depth: Int, escapeUnicode: scala.Boolean): Unit
}

object Json {

  // Type aliases to avoid confusion with Json.String and Json.Boolean
  private type Str  = Predef.String
  private type Bool = scala.Boolean

  // ============ JSON Value Types ============

  /** A JSON object with ordered fields. */
  final case class Object(value: Vector[(Str, Json)]) extends Json {
    override def isObject: Bool           = true
    override def asObject: JsonSelection  = JsonSelection(this)
    override def fields: Seq[(Str, Json)] = value
    override protected def typeLabel: Str = "object"

    override def apply(key: Str): JsonSelection =
      value.collectFirst { case (k, v) if k == key => v } match {
        case Some(v) => JsonSelection(v)
        case None    => JsonSelection.empty
      }

    override protected def modifyAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      f: Json => Either[JsonError, Json]
    ): Either[JsonError, Json] =
      nodes(idx) match {
        case DynamicOptic.Node.Field(name) =>
          val fieldIdx = value.indexWhere(_._1 == name)
          if (fieldIdx < 0) Left(JsonError.keyNotFound(name))
          else {
            val (k, v) = value(fieldIdx)
            val result = if (idx == nodes.length - 1) f(v) else v.modifyAtPath(nodes, idx + 1, f)
            result.map(newV => Object(value.updated(fieldIdx, (k, newV))))
          }
        case _ => Left(JsonError.typeMismatch("field access", nodes(idx).toString))
      }

    override protected def deleteAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int
    ): Either[JsonError, Json] =
      nodes(idx) match {
        case DynamicOptic.Node.Field(name) =>
          val fieldIdx = value.indexWhere(_._1 == name)
          if (fieldIdx < 0) Left(JsonError.keyNotFound(name))
          else if (idx == nodes.length - 1) {
            Right(Object(value.patch(fieldIdx, Nil, 1)))
          } else {
            value(fieldIdx)._2.deleteAtPath(nodes, idx + 1).map { newV =>
              Object(value.updated(fieldIdx, (name, newV)))
            }
          }
        case _ => Left(JsonError.typeMismatch("field access", nodes(idx).toString))
      }

    override protected def insertAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      newValue: Json
    ): Either[JsonError, Json] =
      nodes(idx) match {
        case DynamicOptic.Node.Field(name) =>
          val fieldIdx = value.indexWhere(_._1 == name)
          if (idx == nodes.length - 1) {
            if (fieldIdx < 0) Right(Object(value :+ (name, newValue)))
            else Right(Object(value.updated(fieldIdx, (name, newValue))))
          } else {
            if (fieldIdx < 0) Left(JsonError.keyNotFound(name))
            else
              value(fieldIdx)._2.insertAtPath(nodes, idx + 1, newValue).map { newV =>
                Object(value.updated(fieldIdx, (name, newV)))
              }
          }
        case _ => Left(JsonError.typeMismatch("field access", nodes(idx).toString))
      }

    override protected def printTo(sb: StringBuilder, escapeUnicode: Bool): Unit = {
      sb.append('{')
      var first = true
      value.foreach { case (k, v) =>
        if (!first) sb.append(',')
        first = false
        printString(sb, k, escapeUnicode)
        sb.append(':')
        v.printTo(sb, escapeUnicode)
      }
      sb.append('}')
    }

    override protected def printPrettyTo(sb: StringBuilder, indent: Int, depth: Int, escapeUnicode: Bool): Unit =
      if (value.isEmpty) {
        sb.append("{}")
      } else {
        sb.append("{\n")
        var first = true
        value.foreach { case (k, v) =>
          if (!first) sb.append(",\n")
          first = false
          sb.append(" " * (indent * (depth + 1)))
          printString(sb, k, escapeUnicode)
          sb.append(": ")
          v.printPrettyTo(sb, indent, depth + 1, escapeUnicode)
        }
        sb.append('\n')
        sb.append(" " * (indent * depth))
        sb.append('}')
      }
  }

  object Object {
    val empty: Object = Object(Vector.empty)

    def apply(fields: (Str, Json)*): Object = Object(fields.toVector)
  }

  /** A JSON array. */
  final case class Array(value: Vector[Json]) extends Json {
    override def isArray: Bool            = true
    override def asArray: JsonSelection   = JsonSelection(this)
    override def elements: Seq[Json]      = value
    override protected def typeLabel: Str = "array"

    override def apply(index: Int): JsonSelection =
      if (index >= 0 && index < value.length) JsonSelection(value(index))
      else JsonSelection.empty

    override protected def modifyAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      f: Json => Either[JsonError, Json]
    ): Either[JsonError, Json] =
      nodes(idx) match {
        case DynamicOptic.Node.AtIndex(i) =>
          if (i < 0 || i >= value.length) Left(JsonError.indexOutOfBounds(i, value.length))
          else {
            val result = if (idx == nodes.length - 1) f(value(i)) else value(i).modifyAtPath(nodes, idx + 1, f)
            result.map(newV => Array(value.updated(i, newV)))
          }
        case DynamicOptic.Node.Elements =>
          var errors      = Vector.empty[JsonError]
          val newElements = value.zipWithIndex.map { case (elem, i) =>
            val result = if (idx == nodes.length - 1) f(elem) else elem.modifyAtPath(nodes, idx + 1, f)
            result match {
              case Right(v)  => v
              case Left(err) => errors = errors :+ err.atIndex(i); elem
            }
          }
          if (errors.isEmpty) Right(Array(newElements))
          else Left(errors.reduce(_ ++ _))
        case _ => Left(JsonError.typeMismatch("array access", nodes(idx).toString))
      }

    override protected def deleteAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int
    ): Either[JsonError, Json] =
      nodes(idx) match {
        case DynamicOptic.Node.AtIndex(i) =>
          if (i < 0 || i >= value.length) Left(JsonError.indexOutOfBounds(i, value.length))
          else if (idx == nodes.length - 1) Right(Array(value.patch(i, Nil, 1)))
          else value(i).deleteAtPath(nodes, idx + 1).map(newV => Array(value.updated(i, newV)))
        case _ => Left(JsonError.typeMismatch("array access", nodes(idx).toString))
      }

    override protected def insertAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      newValue: Json
    ): Either[JsonError, Json] =
      nodes(idx) match {
        case DynamicOptic.Node.AtIndex(i) =>
          if (idx == nodes.length - 1) {
            if (i < 0) Left(JsonError.indexOutOfBounds(i, value.length))
            else if (i >= value.length) Right(Array(value :+ newValue))
            else Right(Array(value.updated(i, newValue)))
          } else {
            if (i < 0 || i >= value.length) Left(JsonError.indexOutOfBounds(i, value.length))
            else value(i).insertAtPath(nodes, idx + 1, newValue).map(newV => Array(value.updated(i, newV)))
          }
        case _ => Left(JsonError.typeMismatch("array access", nodes(idx).toString))
      }

    override protected def printTo(sb: StringBuilder, escapeUnicode: Bool): Unit = {
      sb.append('[')
      var first = true
      value.foreach { v =>
        if (!first) sb.append(',')
        first = false
        v.printTo(sb, escapeUnicode)
      }
      sb.append(']')
    }

    override protected def printPrettyTo(sb: StringBuilder, indent: Int, depth: Int, escapeUnicode: Bool): Unit =
      if (value.isEmpty) {
        sb.append("[]")
      } else {
        sb.append("[\n")
        var first = true
        value.foreach { v =>
          if (!first) sb.append(",\n")
          first = false
          sb.append(" " * (indent * (depth + 1)))
          v.printPrettyTo(sb, indent, depth + 1, escapeUnicode)
        }
        sb.append('\n')
        sb.append(" " * (indent * depth))
        sb.append(']')
      }
  }

  object Array {
    val empty: Array = Array(Vector.empty)

    def apply(elements: Json*): Array = Array(elements.toVector)
  }

  /** A JSON string. */
  final case class String(value: java.lang.String) extends Json {
    override def isString: Bool                           = true
    override def asString: JsonSelection                  = JsonSelection(this)
    override def stringValue: Option[scala.Predef.String] = Some(value)
    override protected def typeLabel: java.lang.String    = "string"

    override protected def modifyAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      f: Json => Either[JsonError, Json]
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "string"))

    override protected def deleteAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "string"))

    override protected def insertAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      newValue: Json
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "string"))

    override protected def printTo(sb: StringBuilder, escapeUnicode: Bool): Unit =
      printString(sb, value, escapeUnicode)

    override protected def printPrettyTo(sb: StringBuilder, indent: Int, depth: Int, escapeUnicode: Bool): Unit =
      printTo(sb, escapeUnicode)
  }

  /**
   * A JSON number.
   *
   * Stored as a string to preserve exact representation (precision, trailing
   * zeros, etc.). Provides lazy conversion to numeric types.
   *
   * @param value
   *   The number as a string (should be valid JSON number syntax)
   */
  final case class Number(value: java.lang.String) extends Json {
    override def isNumber: Bool                        = true
    override def asNumber: JsonSelection               = JsonSelection(this)
    override def numberValue: Option[java.lang.String] = Some(value)
    override protected def typeLabel: java.lang.String = "number"

    /** Converts to `Int`, truncating if necessary. */
    lazy val toInt: Int = toBigDecimal.toInt

    /** Converts to `Long`, truncating if necessary. */
    lazy val toLong: Long = toBigDecimal.toLong

    /** Converts to `Float`. */
    lazy val toFloat: Float = value.toFloat

    /** Converts to `Double`. */
    lazy val toDouble: Double = value.toDouble

    /** Converts to `BigInt`, truncating fractional part. */
    lazy val toBigInt: BigInt = toBigDecimal.toBigInt

    /** Converts to `BigDecimal` (lossless). */
    lazy val toBigDecimal: BigDecimal = BigDecimal(value)

    override protected def modifyAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      f: Json => Either[JsonError, Json]
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "number"))

    override protected def deleteAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "number"))

    override protected def insertAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      newValue: Json
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "number"))

    override protected def printTo(sb: StringBuilder, escapeUnicode: Bool): Unit = sb.append(value)

    override protected def printPrettyTo(sb: StringBuilder, indent: Int, depth: Int, escapeUnicode: Bool): Unit =
      printTo(sb, escapeUnicode)
  }

  object Number {
    def apply(value: Int): Number        = Number(value.toString)
    def apply(value: Long): Number       = Number(value.toString)
    def apply(value: Double): Number     = Number(value.toString)
    def apply(value: Float): Number      = Number(value.toString)
    def apply(value: BigInt): Number     = Number(value.toString)
    def apply(value: BigDecimal): Number = Number(value.toString)
  }

  /** A JSON boolean. */
  final case class Boolean(value: scala.Boolean) extends Json {
    override def isBoolean: Bool                       = true
    override def asBoolean: JsonSelection              = JsonSelection(this)
    override def booleanValue: Option[scala.Boolean]   = Some(value)
    override protected def typeLabel: java.lang.String = "boolean"

    override protected def modifyAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      f: Json => Either[JsonError, Json]
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "boolean"))

    override protected def deleteAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "boolean"))

    override protected def insertAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      newValue: Json
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "boolean"))

    override protected def printTo(sb: StringBuilder, escapeUnicode: Bool): Unit =
      sb.append(if (value) "true" else "false")

    override protected def printPrettyTo(sb: StringBuilder, indent: Int, depth: Int, escapeUnicode: Bool): Unit =
      printTo(sb, escapeUnicode)
  }

  object Boolean {
    val True: Boolean  = Boolean(true)
    val False: Boolean = Boolean(false)
  }

  /** JSON null. */
  case object Null extends Json {
    override def isNull: Bool             = true
    override def asNull: JsonSelection    = JsonSelection(this)
    override protected def typeLabel: Str = "null"

    override protected def modifyAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      f: Json => Either[JsonError, Json]
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "null"))

    override protected def deleteAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "null"))

    override protected def insertAtPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      newValue: Json
    ): Either[JsonError, Json] = Left(JsonError.typeMismatch("object or array", "null"))

    override protected def printTo(sb: StringBuilder, escapeUnicode: Bool): Unit = sb.append("null")

    override protected def printPrettyTo(sb: StringBuilder, indent: Int, depth: Int, escapeUnicode: Bool): Unit =
      printTo(sb, escapeUnicode)
  }

  // ============ Parsing / Decoding ============

  /**
   * Parses a JSON value from a string.
   *
   * @param s
   *   The JSON string
   * @return
   *   Either a [[JsonError]] or the parsed JSON
   */
  def parse(s: java.lang.String): Either[JsonError, Json] = decode(s)

  /**
   * Parses a JSON value from a `CharSequence`.
   */
  def parse(s: CharSequence): Either[JsonError, Json] = decode(s)

  /**
   * Parses JSON from a byte array (UTF-8).
   */
  def parse(bytes: scala.Array[Byte]): Either[JsonError, Json] = decode(bytes)

  /**
   * Parses JSON from a ByteBuffer (UTF-8).
   */
  def parse(buffer: ByteBuffer): Either[JsonError, Json] = decode(buffer)

  /**
   * Parses a JSON value from a [[Chunk]] of bytes (UTF-8).
   *
   * @param chunk
   *   The JSON bytes
   * @return
   *   Either a [[JsonError]] or the parsed JSON
   */
  def parse(chunk: Chunk[Byte]): Either[JsonError, Json] = decode(chunk)

  /**
   * Parses a JSON value from a [[Reader]].
   */
  def parse(reader: Reader): Either[JsonError, Json] = decode(reader)

  /**
   * Decodes a JSON value from a string.
   */
  def decode(s: java.lang.String): Either[JsonError, Json] = {
    val parser = new JsonParser(s)
    parser.parse()
  }

  /**
   * Decodes a JSON value from a `CharSequence`.
   */
  def decode(s: CharSequence): Either[JsonError, Json] = decode(s.toString)

  /**
   * Decodes a JSON value from a byte array (UTF-8).
   */
  def decode(bytes: scala.Array[Byte]): Either[JsonError, Json] =
    decode(new java.lang.String(bytes, StandardCharsets.UTF_8))

  /**
   * Decodes a JSON value from a [[ByteBuffer]] (UTF-8).
   */
  def decode(buffer: ByteBuffer): Either[JsonError, Json] = {
    val bytes = new scala.Array[Byte](buffer.remaining())
    buffer.get(bytes)
    decode(bytes)
  }

  /**
   * Decodes a JSON value from a [[Chunk]] of bytes (UTF-8).
   */
  def decode(chunk: Chunk[Byte]): Either[JsonError, Json] =
    decode(chunk.toArray)

  /**
   * Decodes a JSON value from a [[Reader]].
   */
  def decode(reader: Reader): Either[JsonError, Json] = {
    val sb  = new StringBuilder
    val buf = new scala.Array[Char](8192)
    var n   = reader.read(buf)
    while (n >= 0) {
      sb.appendAll(buf, 0, n)
      n = reader.read(buf)
    }
    decode(sb.toString)
  }

  /**
   * Parses a JSON value from a string, throwing on failure.
   *
   * @param s
   *   The JSON string
   * @return
   *   The parsed JSON
   * @throws JsonError
   *   if parsing fails
   */
  def parseUnsafe(s: java.lang.String): Json = decode(s).fold(throw _, identity)

  /**
   * Alias for [[parseUnsafe]].
   */
  def decodeUnsafe(s: java.lang.String): Json = parseUnsafe(s)

  // ============ KV Interop ============

  /**
   * Assembles JSON from a sequence of path-value pairs.
   *
   * @param kvs
   *   The path-value pairs
   * @return
   *   Either an error (for conflicting paths) or the assembled JSON
   */
  def fromKV(kvs: Seq[(DynamicOptic, Json)]): Either[JsonError, Json] = {
    var result: Json = Null
    kvs.foreach { case (path, value) =>
      result = result.insert(path, value)
    }
    Right(result)
  }

  /**
   * Assembles JSON from path-value pairs, throwing on conflict.
   */
  def fromKVUnsafe(kvs: Seq[(DynamicOptic, Json)]): Json =
    fromKV(kvs).fold(throw _, identity)

  // ============ Patch Interop ============

  /**
   * Serializes a [[JsonPatch]] to its JSON representation.
   *
   * The format follows RFC 6902 (JSON Patch) for standard operations, with
   * extensions for LCS-based sequence diffs.
   *
   * @param patch
   *   The patch to serialize
   * @return
   *   The JSON representation of the patch
   */
  def fromJsonPatch(patch: JsonPatch): Json = ???

  /**
   * Deserializes a JSON representation into a [[JsonPatch]].
   *
   * @param json
   *   The JSON patch representation
   * @return
   *   Either an error or the parsed patch
   */
  def toJsonPatch(json: Json): Either[JsonError, JsonPatch] = ???

  // ============ Typed Encoding ============

  /**
   * Encodes a typed value to JSON.
   *
   * Uses implicit [[JsonEncoder]] which prefers explicit codecs over schema
   * derivation.
   *
   * @param value
   *   The value to encode
   * @return
   *   The encoded JSON
   */
  def from[A](value: A)(implicit encoder: JsonEncoder[A]): Json = encoder.encode(value)

  // ============ Convenience Constructors ============

  /** Creates a JSON number from an `Int`. */
  def number(n: Int): Number = Number(n.toString)

  /** Creates a JSON number from a `Long`. */
  def number(n: Long): Number = Number(n.toString)

  /** Creates a JSON number from a `Float`. */
  def number(n: Float): Number = Number(n.toString)

  /** Creates a JSON number from a `Double`. */
  def number(n: Double): Number = Number(n.toString)

  /** Creates a JSON number from a `BigInt`. */
  def number(n: BigInt): Number = Number(n.toString)

  /** Creates a JSON number from a `BigDecimal`. */
  def number(n: BigDecimal): Number = Number(n.toString)

  /** Creates a JSON number from a `Short`. */
  def number(n: Short): Number = Number(n.toString)

  /** Creates a JSON number from a `Byte`. */
  def number(n: Byte): Number = Number(n.toString)

  /** Creates a JSON object from key-value pairs. */
  def obj(fields: (java.lang.String, Json)*): Object = Object(fields.toVector)

  /** Creates a JSON array from elements. */
  def arr(elements: Json*): Array = Array(elements.toVector)

  /** Creates a JSON string. */
  def str(value: java.lang.String): String = String(value)

  /** Creates a JSON number from an Int. */
  def num(value: Int): Number = Number(value)

  /** Creates a JSON number from a Long. */
  def num(value: Long): Number = Number(value)

  /** Creates a JSON number from a Double. */
  def num(value: Double): Number = Number(value)

  /** Creates a JSON boolean. */
  def bool(value: scala.Boolean): Boolean = Boolean(value)

  /** JSON null value. */
  val `null`: Null.type = Null

  // ============ Internal encoding ============

  /**
   * Internal: encode using an explicit codec.
   */
  private[json] def encodeWith[A](value: A, codec: JsonBinaryCodec[A]): Json = {
    val bytes = codec.encode(value)
    parse(bytes).getOrElse(Null)
  }

  // ============ DynamicValue Interop ============

  /**
   * Converts a [[DynamicValue]] to JSON.
   *
   * This conversion is lossy for `DynamicValue` types that have no JSON
   * equivalent:
   *   - `PrimitiveValue` types like `java.time.*` are converted to strings
   *   - `DynamicValue.Variant` uses a discriminator field
   */
  def fromDynamicValue(value: DynamicValue): Json = value match {
    case DynamicValue.Primitive(pv)    => fromPrimitiveValue(pv)
    case DynamicValue.Record(flds)     => Object(flds.map { case (k, v) => (k, fromDynamicValue(v)) })
    case DynamicValue.Variant(name, v) =>
      Object(Vector("_type" -> String(name), "_value" -> fromDynamicValue(v)))
    case DynamicValue.Sequence(elems) => Array(elems.map(fromDynamicValue))
    case DynamicValue.Map(entries)    =>
      Array(entries.map { case (k, v) =>
        Object(Vector("key" -> fromDynamicValue(k), "value" -> fromDynamicValue(v)))
      })
  }

  private def fromPrimitiveValue(pv: PrimitiveValue): Json = pv match {
    case PrimitiveValue.Unit              => Null
    case PrimitiveValue.Boolean(v)        => Boolean(v)
    case PrimitiveValue.Byte(v)           => Number(v.toInt)
    case PrimitiveValue.Short(v)          => Number(v.toInt)
    case PrimitiveValue.Int(v)            => Number(v)
    case PrimitiveValue.Long(v)           => Number(v)
    case PrimitiveValue.Float(v)          => Number(v.toDouble)
    case PrimitiveValue.Double(v)         => Number(v)
    case PrimitiveValue.Char(v)           => String(v.toString)
    case PrimitiveValue.String(v)         => String(v)
    case PrimitiveValue.BigInt(v)         => Number(v)
    case PrimitiveValue.BigDecimal(v)     => Number(v)
    case PrimitiveValue.DayOfWeek(v)      => String(v.toString)
    case PrimitiveValue.Duration(v)       => String(v.toString)
    case PrimitiveValue.Instant(v)        => String(v.toString)
    case PrimitiveValue.LocalDate(v)      => String(v.toString)
    case PrimitiveValue.LocalDateTime(v)  => String(v.toString)
    case PrimitiveValue.LocalTime(v)      => String(v.toString)
    case PrimitiveValue.Month(v)          => String(v.toString)
    case PrimitiveValue.MonthDay(v)       => String(v.toString)
    case PrimitiveValue.OffsetDateTime(v) => String(v.toString)
    case PrimitiveValue.OffsetTime(v)     => String(v.toString)
    case PrimitiveValue.Period(v)         => String(v.toString)
    case PrimitiveValue.Year(v)           => String(v.toString)
    case PrimitiveValue.YearMonth(v)      => String(v.toString)
    case PrimitiveValue.ZoneId(v)         => String(v.getId)
    case PrimitiveValue.ZoneOffset(v)     => String(v.toString)
    case PrimitiveValue.ZonedDateTime(v)  => String(v.toString)
    case PrimitiveValue.Currency(v)       => String(v.getCurrencyCode)
    case PrimitiveValue.UUID(v)           => String(v.toString)
  }

  // ============ Ordering ============

  /**
   * Ordering for JSON values.
   *
   * Order: Null < Boolean < Number < String < Array < Object
   */
  implicit val ordering: Ordering[Json] = (x: Json, y: Json) => x.compare(y)

  // ============ Comparison Helpers ============

  private def compareArrays(a: Vector[Json], b: Vector[Json]): Int = {
    val len = math.min(a.size, b.size)
    var i   = 0
    while (i < len) {
      val cmp = a(i).compare(b(i))
      if (cmp != 0) return cmp
      i += 1
    }
    a.size.compare(b.size)
  }

  private def compareObjects(a: Vector[(Str, Json)], b: Vector[(Str, Json)]): Int = {
    val aSorted = a.sortBy(_._1)
    val bSorted = b.sortBy(_._1)
    val len     = math.min(aSorted.size, bSorted.size)
    var i       = 0
    while (i < len) {
      val (ak, av) = aSorted(i)
      val (bk, bv) = bSorted(i)
      val keyCmp   = ak.compare(bk)
      if (keyCmp != 0) return keyCmp
      val valCmp = av.compare(bv)
      if (valCmp != 0) return valCmp
      i += 1
    }
    aSorted.size.compare(bSorted.size)
  }

  // ============ Helpers ============

  private def printString(sb: StringBuilder, s: java.lang.String, escapeUnicode: scala.Boolean): Unit = {
    sb.append('"')
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      c match {
        case '"'         => sb.append("\\\"")
        case '\\'        => sb.append("\\\\")
        case '\b'        => sb.append("\\b")
        case '\f'        => sb.append("\\f")
        case '\n'        => sb.append("\\n")
        case '\r'        => sb.append("\\r")
        case '\t'        => sb.append("\\t")
        case _ if c < 32 =>
          sb.append("\\u")
          sb.append(f"${c.toInt}%04x")
        case _ if escapeUnicode && c > 127 =>
          sb.append("\\u")
          sb.append(f"${c.toInt}%04x")
        case _ => sb.append(c)
      }
      i += 1
    }
    sb.append('"')
  }
}

/**
 * A simple JSON parser.
 */
private class JsonParser(input: java.lang.String) {
  private var pos: Int    = 0
  private var line: Int   = 1
  private var column: Int = 1

  private def makeError(message: String): JsonError =
    JsonError.parseError(message, Some(pos.toLong), Some(line), Some(column))

  def parse(): Either[JsonError, Json] = {
    skipWhitespace()
    if (pos >= input.length) Left(makeError("Unexpected end of input"))
    else {
      val result = parseValue()
      result.flatMap { json =>
        skipWhitespace()
        if (pos < input.length)
          Left(makeError(s"Unexpected character: ${input.charAt(pos)}"))
        else Right(json)
      }
    }
  }

  private def parseValue(): Either[JsonError, Json] = {
    skipWhitespace()
    if (pos >= input.length) Left(makeError("Unexpected end of input"))
    else {
      input.charAt(pos) match {
        case '{'                                     => parseObject()
        case '['                                     => parseArray()
        case '"'                                     => parseString()
        case 't'                                     => parseTrue()
        case 'f'                                     => parseFalse()
        case 'n'                                     => parseNull()
        case c if c == '-' || (c >= '0' && c <= '9') => parseNumber()
        case c                                       => Left(makeError(s"Unexpected character: $c"))
      }
    }
  }

  private def parseObject(): Either[JsonError, Json] = {
    advance() // skip '{'
    skipWhitespace()
    if (pos < input.length && input.charAt(pos) == '}') {
      advance()
      Right(Json.Object.empty)
    } else {
      var fields                   = Vector.empty[(java.lang.String, Json)]
      var continue                 = true
      var error: Option[JsonError] = None

      while (continue && error.isEmpty) {
        skipWhitespace()
        parseString() match {
          case Left(err)      => error = Some(err)
          case Right(keyJson) =>
            val key = keyJson.stringValue.get
            skipWhitespace()
            if (pos >= input.length || input.charAt(pos) != ':') {
              error = Some(makeError("Expected ':'"))
            } else {
              advance() // skip ':'
              parseValue() match {
                case Left(err)    => error = Some(err)
                case Right(value) =>
                  fields = fields :+ (key, value)
                  skipWhitespace()
                  if (pos < input.length) {
                    input.charAt(pos) match {
                      case ',' => advance()
                      case '}' => advance(); continue = false
                      case _   => error = Some(makeError("Expected ',' or '}'"))
                    }
                  } else {
                    error = Some(makeError("Unexpected end of input"))
                  }
              }
            }
        }
      }

      error match {
        case Some(err) => Left(err)
        case None      => Right(Json.Object(fields))
      }
    }
  }

  private def parseArray(): Either[JsonError, Json] = {
    advance() // skip '['
    skipWhitespace()
    if (pos < input.length && input.charAt(pos) == ']') {
      advance()
      Right(Json.Array.empty)
    } else {
      var elements                 = Vector.empty[Json]
      var continue                 = true
      var error: Option[JsonError] = None

      while (continue && error.isEmpty) {
        parseValue() match {
          case Left(err)    => error = Some(err)
          case Right(value) =>
            elements = elements :+ value
            skipWhitespace()
            if (pos < input.length) {
              input.charAt(pos) match {
                case ',' => advance()
                case ']' => advance(); continue = false
                case _   => error = Some(makeError("Expected ',' or ']'"))
              }
            } else {
              error = Some(makeError("Unexpected end of input"))
            }
        }
      }

      error match {
        case Some(err) => Left(err)
        case None      => Right(Json.Array(elements))
      }
    }
  }

  private def parseString(): Either[JsonError, Json] =
    if (pos >= input.length || input.charAt(pos) != '"') {
      Left(makeError("Expected '\"'"))
    } else {
      advance() // skip opening '"'
      val sb                       = new StringBuilder
      var done                     = false
      var error: Option[JsonError] = None

      while (!done && error.isEmpty) {
        if (pos >= input.length) {
          error = Some(makeError("Unterminated string"))
        } else {
          val c = input.charAt(pos)
          c match {
            case '"'  => advance(); done = true
            case '\\' =>
              advance()
              if (pos >= input.length) {
                error = Some(makeError("Unterminated escape sequence"))
              } else {
                input.charAt(pos) match {
                  case '"'  => sb.append('"'); advance()
                  case '\\' => sb.append('\\'); advance()
                  case '/'  => sb.append('/'); advance()
                  case 'b'  => sb.append('\b'); advance()
                  case 'f'  => sb.append('\f'); advance()
                  case 'n'  => sb.append('\n'); advance()
                  case 'r'  => sb.append('\r'); advance()
                  case 't'  => sb.append('\t'); advance()
                  case 'u'  =>
                    advance()
                    if (pos + 4 > input.length) {
                      error = Some(makeError("Invalid unicode escape"))
                    } else {
                      val hex = input.substring(pos, pos + 4)
                      try {
                        sb.append(Integer.parseInt(hex, 16).toChar)
                        pos += 4
                        column += 4
                      } catch {
                        case _: NumberFormatException =>
                          error = Some(makeError("Invalid unicode escape"))
                      }
                    }
                  case other =>
                    error = Some(makeError(s"Invalid escape character: $other"))
                }
              }
            case _ if c < 32 =>
              error = Some(makeError("Control character in string"))
            case _ => sb.append(c); advance()
          }
        }
      }

      error match {
        case Some(err) => Left(err)
        case None      => Right(Json.String(sb.toString))
      }
    }

  private def parseNumber(): Either[JsonError, Json] = {
    val start = pos
    // Handle negative sign
    if (pos < input.length && input.charAt(pos) == '-') advance()
    // Integer part
    if (pos < input.length && input.charAt(pos) == '0') {
      advance()
    } else {
      while (pos < input.length && input.charAt(pos) >= '0' && input.charAt(pos) <= '9') advance()
    }
    // Fractional part
    if (pos < input.length && input.charAt(pos) == '.') {
      advance()
      while (pos < input.length && input.charAt(pos) >= '0' && input.charAt(pos) <= '9') advance()
    }
    // Exponent part
    if (pos < input.length && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
      advance()
      if (pos < input.length && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) advance()
      while (pos < input.length && input.charAt(pos) >= '0' && input.charAt(pos) <= '9') advance()
    }
    Right(Json.Number(input.substring(start, pos)))
  }

  private def parseTrue(): Either[JsonError, Json] =
    if (input.substring(pos).startsWith("true")) {
      pos += 4
      column += 4
      Right(Json.Boolean.True)
    } else {
      Left(makeError("Expected 'true'"))
    }

  private def parseFalse(): Either[JsonError, Json] =
    if (input.substring(pos).startsWith("false")) {
      pos += 5
      column += 5
      Right(Json.Boolean.False)
    } else {
      Left(makeError("Expected 'false'"))
    }

  private def parseNull(): Either[JsonError, Json] =
    if (input.substring(pos).startsWith("null")) {
      pos += 4
      column += 4
      Right(Json.Null)
    } else {
      Left(makeError("Expected 'null'"))
    }

  private def skipWhitespace(): Unit =
    while (pos < input.length) {
      input.charAt(pos) match {
        case ' ' | '\t' | '\r' => advance()
        case '\n'              => pos += 1; line += 1; column = 1
        case _                 => return
      }
    }

  private def advance(): Unit = {
    pos += 1
    column += 1
  }
}
