package zio.blocks.schema.json

import zio.blocks.schema._

/**
 * A JSON Patch represents a sequence of operations to transform one JSON value into another.
 * 
 * This implementation follows RFC 6902 (JSON Patch) standard with operations:
 * - add: Add a value at a path
 * - remove: Remove a value at a path
 * - replace: Replace a value at a path
 * - move: Move a value from one path to another
 * - copy: Copy a value from one path to another
 * - test: Test that a value at a path equals a specified value
 * 
 * JsonPatch can be:
 * - Computed via diff between two JSON values
 * - Applied to transform JSON values
 * - Composed with other patches
 * - Serialized/deserialized via Schema
 */
final case class JsonPatch(operations: Vector[JsonPatch.Operation]) {
  
  /**
   * Apply this patch to a JSON value.
   * Returns Either an error message or the patched JSON.
   */
  def apply(json: Json): Either[String, Json] = {
    var current = json
    var idx = 0
    
    while (idx < operations.length) {
      operations(idx).apply(current) match {
        case Right(updated) => current = updated
        case Left(error) => return Left(s"Operation ${idx + 1} failed: $error")
      }
      idx += 1
    }
    
    Right(current)
  }
  
  /**
   * Compose this patch with another. The result applies this patch first, then that patch.
   */
  def ++(that: JsonPatch): JsonPatch = JsonPatch(operations ++ that.operations)
  
  /**
   * Check if this patch is empty (no operations).
   */
  def isEmpty: Boolean = operations.isEmpty
  
  /**
   * Convert to RFC 6902 JSON Patch format.
   */
  def toJson: Json = {
    Json.Arr(operations.map(_.toJson))
  }
}

object JsonPatch {
  
  /**
   * Empty patch - identity element for composition.
   */
  val empty: JsonPatch = JsonPatch(Vector.empty)
  
  /**
   * Create a patch with a single operation.
   */
  def single(operation: Operation): JsonPatch = JsonPatch(Vector(operation))
  
  /**
   * Compute the diff between two JSON values.
   * Returns a JsonPatch that transforms oldValue into newValue.
   */
  def diff(oldValue: Json, newValue: Json): JsonPatch = {
    if (oldValue == newValue) {
      empty
    } else {
      (oldValue, newValue) match {
        case (Json.Obj(oldFields), Json.Obj(newFields)) =>
          diffObject(JsonPath.root, oldFields, newFields)
          
        case (Json.Arr(oldElements), Json.Arr(newElements)) =>
          diffArray(JsonPath.root, oldElements, newElements)
          
        case _ =>
          // Different types or primitive values - use replace
          single(Operation.Replace(JsonPath.root, newValue))
      }
    }
  }
  
  /**
   * Diff two JSON objects.
   */
  private def diffObject(
    basePath: JsonPath,
    oldFields: Map[String, Json],
    newFields: Map[String, Json]
  ): JsonPatch = {
    val ops = Vector.newBuilder[Operation]
    
    // Find removed fields
    oldFields.foreach { case (key, _) =>
      if (!newFields.contains(key)) {
        ops += Operation.Remove(basePath / key)
      }
    }
    
    // Find added and modified fields
    newFields.foreach { case (key, newValue) =>
      oldFields.get(key) match {
        case None =>
          // Field added
          ops += Operation.Add(basePath / key, newValue)
          
        case Some(oldValue) if oldValue != newValue =>
          // Field modified - recursively diff
          val fieldPath = basePath / key
          (oldValue, newValue) match {
            case (Json.Obj(oldNested), Json.Obj(newNested)) =>
              // Nested object - diff recursively
              val nestedPatch = diffObject(fieldPath, oldNested, newNested)
              ops ++= nestedPatch.operations
              
            case (Json.Arr(oldNested), Json.Arr(newNested)) =>
              // Nested array - diff recursively
              val nestedPatch = diffArray(fieldPath, oldNested, newNested)
              ops ++= nestedPatch.operations
              
            case _ =>
              // Different types or primitives - replace
              ops += Operation.Replace(fieldPath, newValue)
          }
          
        case Some(_) =>
          // Field unchanged
          ()
      }
    }
    
    JsonPatch(ops.result())
  }
  
  /**
   * Diff two JSON arrays using a simple algorithm.
   * For optimal diffs, we'd use LCS or Myers diff, but this provides a working baseline.
   */
  private def diffArray(
    basePath: JsonPath,
    oldElements: Vector[Json],
    newElements: Vector[Json]
  ): JsonPatch = {
    val ops = Vector.newBuilder[Operation]
    val minLen = math.min(oldElements.length, newElements.length)
    
    // Diff common elements
    var idx = 0
    while (idx < minLen) {
      if (oldElements(idx) != newElements(idx)) {
        val elemPath = basePath(idx)
        (oldElements(idx), newElements(idx)) match {
          case (Json.Obj(oldNested), Json.Obj(newNested)) =>
            val nestedPatch = diffObject(elemPath, oldNested, newNested)
            ops ++= nestedPatch.operations
            
          case (Json.Arr(oldNested), Json.Arr(newNested)) =>
            val nestedPatch = diffArray(elemPath, oldNested, newNested)
            ops ++= nestedPatch.operations
            
          case _ =>
            ops += Operation.Replace(elemPath, newElements(idx))
        }
      }
      idx += 1
    }
    
    // Handle length differences
    if (oldElements.length > newElements.length) {
      // Remove extra elements from the end (in reverse order to maintain indices)
      var removeIdx = oldElements.length - 1
      while (removeIdx >= newElements.length) {
        ops += Operation.Remove(basePath(removeIdx))
        removeIdx -= 1
      }
    } else if (newElements.length > oldElements.length) {
      // Add new elements
      var addIdx = oldElements.length
      while (addIdx < newElements.length) {
        ops += Operation.Add(basePath(addIdx), newElements(addIdx))
        addIdx += 1
      }
    }
    
    JsonPatch(ops.result())
  }
  
  /**
   * Parse a JSON Patch from RFC 6902 format.
   */
  def fromJson(json: Json): Either[String, JsonPatch] = {
    json match {
      case Json.Arr(elements) =>
        val ops = elements.map(Operation.fromJson)
        val errors = ops.collect { case Left(err) => err }
        if (errors.nonEmpty) {
          Left(s"Failed to parse operations: ${errors.mkString(", ")}")
        } else {
          Right(JsonPatch(ops.collect { case Right(op) => op }))
        }
      case _ =>
        Left("JSON Patch must be an array")
    }
  }
  
  /**
   * A single JSON Patch operation.
   */
  sealed trait Operation {
    def apply(json: Json): Either[String, Json]
    def toJson: Json
  }
  
  object Operation {
    
    /**
     * Add operation - adds a value at the specified path.
     * If the path points to an array index, inserts at that index.
     * If the path points to an object field, adds or replaces the field.
     */
    final case class Add(path: JsonPath, value: Json) extends Operation {
      def apply(json: Json): Either[String, Json] = {
        path match {
          case JsonPath.Root =>
            Right(value)
            
          case JsonPath.Field(parent, name) =>
            parent.navigate(json) match {
              case Some(Json.Obj(fields)) =>
                parent.update(json, Json.Obj(fields.updated(name, value)))
                  .toRight(s"Failed to update at path ${path.toPointer}")
              case Some(_) =>
                Left(s"Path ${parent.toPointer} does not point to an object")
              case None =>
                Left(s"Path ${parent.toPointer} not found")
            }
            
          case JsonPath.Index(parent, idx) =>
            parent.navigate(json) match {
              case Some(Json.Arr(elements)) =>
                if (idx < 0 || idx > elements.length) {
                  Left(s"Index $idx out of bounds for array of length ${elements.length}")
                } else {
                  val (before, after) = elements.splitAt(idx)
                  parent.update(json, Json.Arr(before ++ Vector(value) ++ after))
                    .toRight(s"Failed to update at path ${path.toPointer}")
                }
              case Some(_) =>
                Left(s"Path ${parent.toPointer} does not point to an array")
              case None =>
                Left(s"Path ${parent.toPointer} not found")
            }
        }
      }
      
      def toJson: Json = Json.obj(
        "op" -> Json.Str("add"),
        "path" -> Json.Str(path.toPointer),
        "value" -> value
      )
    }
    
    /**
     * Remove operation - removes the value at the specified path.
     */
    final case class Remove(path: JsonPath) extends Operation {
      def apply(json: Json): Either[String, Json] = {
        path.delete(json).toRight(s"Failed to remove at path ${path.toPointer}")
      }
      
      def toJson: Json = Json.obj(
        "op" -> Json.Str("remove"),
        "path" -> Json.Str(path.toPointer)
      )
    }
    
    /**
     * Replace operation - replaces the value at the specified path.
     */
    final case class Replace(path: JsonPath, value: Json) extends Operation {
      def apply(json: Json): Either[String, Json] = {
        path.update(json, value).toRight(s"Failed to replace at path ${path.toPointer}")
      }
      
      def toJson: Json = Json.obj(
        "op" -> Json.Str("replace"),
        "path" -> Json.Str(path.toPointer),
        "value" -> value
      )
    }
    
    /**
     * Move operation - moves a value from one path to another.
     */
    final case class Move(from: JsonPath, path: JsonPath) extends Operation {
      def apply(json: Json): Either[String, Json] = {
        for {
          value <- from.navigate(json).toRight(s"Source path ${from.toPointer} not found")
          removed <- from.delete(json).toRight(s"Failed to remove from ${from.toPointer}")
          result <- Add(path, value).apply(removed)
        } yield result
      }
      
      def toJson: Json = Json.obj(
        "op" -> Json.Str("move"),
        "from" -> Json.Str(from.toPointer),
        "path" -> Json.Str(path.toPointer)
      )
    }
    
    /**
     * Copy operation - copies a value from one path to another.
     */
    final case class Copy(from: JsonPath, path: JsonPath) extends Operation {
      def apply(json: Json): Either[String, Json] = {
        for {
          value <- from.navigate(json).toRight(s"Source path ${from.toPointer} not found")
          result <- Add(path, value).apply(json)
        } yield result
      }
      
      def toJson: Json = Json.obj(
        "op" -> Json.Str("copy"),
        "from" -> Json.Str(from.toPointer),
        "path" -> Json.Str(path.toPointer)
      )
    }
    
    /**
     * Test operation - tests that a value at a path equals the specified value.
     * Fails if the values don't match.
     */
    final case class Test(path: JsonPath, value: Json) extends Operation {
      def apply(json: Json): Either[String, Json] = {
        path.navigate(json) match {
          case Some(actual) if actual == value =>
            Right(json)
          case Some(actual) =>
            Left(s"Test failed at ${path.toPointer}: expected ${value.toCompactString}, got ${actual.toCompactString}")
          case None =>
            Left(s"Test failed: path ${path.toPointer} not found")
        }
      }
      
      def toJson: Json = Json.obj(
        "op" -> Json.Str("test"),
        "path" -> Json.Str(path.toPointer),
        "value" -> value
      )
    }
    
    /**
     * Parse an operation from JSON.
     */
    def fromJson(json: Json): Either[String, Operation] = {
      for {
        obj <- json.asObject.toRight("Operation must be an object")
        opType <- obj.get("op").flatMap(_.asString).toRight("Missing 'op' field")
        pathStr <- obj.get("path").flatMap(_.asString).toRight("Missing 'path' field")
        path <- JsonPath.fromPointer(pathStr)
        operation <- opType match {
          case "add" =>
            obj.get("value").toRight("Missing 'value' field for add operation")
              .map(value => Add(path, value))
              
          case "remove" =>
            Right(Remove(path))
            
          case "replace" =>
            obj.get("value").toRight("Missing 'value' field for replace operation")
              .map(value => Replace(path, value))
              
          case "move" =>
            for {
              fromStr <- obj.get("from").flatMap(_.asString).toRight("Missing 'from' field for move operation")
              from <- JsonPath.fromPointer(fromStr)
            } yield Move(from, path)
            
          case "copy" =>
            for {
              fromStr <- obj.get("from").flatMap(_.asString).toRight("Missing 'from' field for copy operation")
              from <- JsonPath.fromPointer(fromStr)
            } yield Copy(from, path)
            
          case "test" =>
            obj.get("value").toRight("Missing 'value' field for test operation")
              .map(value => Test(path, value))
              
          case other =>
            Left(s"Unknown operation type: $other")
        }
      } yield operation
    }
  }
  
  /**
   * Schema for JsonPatch.
   */
  implicit lazy val schema: Schema[JsonPatch] = {
    Schema.sequence[Vector[Operation], Operation](Operation.schema)
      .transform(JsonPatch.apply, _.operations)
  }
  
  object Operation {
    /**
     * Schema for Operation.
     * Uses a discriminated union based on the operation type.
     */
    implicit lazy val schema: Schema[Operation] = Schema.defer {
      Schema.Enum[Operation](
        TypeName("Operation", Vector("zio", "blocks", "schema", "json", "JsonPatch")),
        Vector(
          Schema.Case[Operation, Add](
            "Add",
            Schema.CaseClass2[JsonPath, Json, Add](
              TypeName("Add", Vector("zio", "blocks", "schema", "json", "JsonPatch", "Operation")),
              Schema.Field("path", Schema[JsonPath], get0 = _.path, set0 = (a, v) => a.copy(path = v)),
              Schema.Field("value", Schema[Json], get0 = _.value, set0 = (a, v) => a.copy(value = v)),
              Add.apply
            ),
            _.asInstanceOf[Add],
            identity,
            _.isInstanceOf[Add]
          ),
          Schema.Case[Operation, Remove](
            "Remove",
            Schema.CaseClass1[JsonPath, Remove](
              TypeName("Remove", Vector("zio", "blocks", "schema", "json", "JsonPatch", "Operation")),
              Schema.Field("path", Schema[JsonPath], get0 = _.path, set0 = (a, v) => a.copy(path = v)),
              Remove.apply
            ),
            _.asInstanceOf[Remove],
            identity,
            _.isInstanceOf[Remove]
          ),
          Schema.Case[Operation, Replace](
            "Replace",
            Schema.CaseClass2[JsonPath, Json, Replace](
              TypeName("Replace", Vector("zio", "blocks", "schema", "json", "JsonPatch", "Operation")),
              Schema.Field("path", Schema[JsonPath], get0 = _.path, set0 = (a, v) => a.copy(path = v)),
              Schema.Field("value", Schema[Json], get0 = _.value, set0 = (a, v) => a.copy(value = v)),
              Replace.apply
            ),
            _.asInstanceOf[Replace],
            identity,
            _.isInstanceOf[Replace]
          ),
          Schema.Case[Operation, Move](
            "Move",
            Schema.CaseClass2[JsonPath, JsonPath, Move](
              TypeName("Move", Vector("zio", "blocks", "schema", "json", "JsonPatch", "Operation")),
              Schema.Field("from", Schema[JsonPath], get0 = _.from, set0 = (a, v) => a.copy(from = v)),
              Schema.Field("path", Schema[JsonPath], get0 = _.path, set0 = (a, v) => a.copy(path = v)),
              Move.apply
            ),
            _.asInstanceOf[Move],
            identity,
            _.isInstanceOf[Move]
          ),
          Schema.Case[Operation, Copy](
            "Copy",
            Schema.CaseClass2[JsonPath, JsonPath, Copy](
              TypeName("Copy", Vector("zio", "blocks", "schema", "json", "JsonPatch", "Operation")),
              Schema.Field("from", Schema[JsonPath], get0 = _.from, set0 = (a, v) => a.copy(from = v)),
              Schema.Field("path", Schema[JsonPath], get0 = _.path, set0 = (a, v) => a.copy(path = v)),
              Copy.apply
            ),
            _.asInstanceOf[Copy],
            identity,
            _.isInstanceOf[Copy]
          ),
          Schema.Case[Operation, Test](
            "Test",
            Schema.CaseClass2[JsonPath, Json, Test](
              TypeName("Test", Vector("zio", "blocks", "schema", "json", "JsonPatch", "Operation")),
              Schema.Field("path", Schema[JsonPath], get0 = _.path, set0 = (a, v) => a.copy(path = v)),
              Schema.Field("value", Schema[Json], get0 = _.value, set0 = (a, v) => a.copy(value = v)),
              Test.apply
            ),
            _.asInstanceOf[Test],
            identity,
            _.isInstanceOf[Test]
          )
        )
      )
    }
  }
  
  /**
   * Schema for JsonPath.
   */
  implicit lazy val jsonPathSchema: Schema[JsonPath] = {
    Schema.Primitive(PrimitiveType.String)
      .transformOrFail(
        str => JsonPath.fromPointer(str),
        path => Right(path.toPointer)
      )
  }
}
