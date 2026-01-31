package zio.blocks.schema.json.patch

import zio.blocks.chunk.Chunk
import zio.blocks.schema.json.Json
import zio.test.Gen

object JsonGen {

  // Primitive Generators

  val genNull: Gen[Any, Json] = Gen.const(Json.Null)

  val genBoolean: Gen[Any, Json] = Gen.boolean.map(Json.Boolean(_))

  val genNumber: Gen[Any, Json] = Gen.oneOf(
    Gen.const(Json.Number("0")),
    Gen.int.map(i => Json.Number(i.toString)),
    Gen.long.map(l => Json.Number(l.toString))
  )

  val genString: Gen[Any, Json] = Gen.oneOf(
    Gen.const(Json.String("")),
    Gen.alphaNumericStringBounded(1, 10).map(Json.String(_)),
    Gen.alphaNumericStringBounded(10, 30).map(Json.String(_))
  )

  val genPrimitive: Gen[Any, Json] = Gen.oneOf(genNull, genBoolean, genNumber, genString)

  // Composite Generators

  private val maxDepth: Int    = 3
  private val maxElements: Int = 5

  private def genArrayWithDepth(depth: Int): Gen[Any, Json] =
    Gen.listOfBounded(0, maxElements)(genJsonWithDepth(depth - 1)).map { elems =>
      Json.Array(Chunk.from(elems))
    }

  private def genObjectWithDepth(depth: Int): Gen[Any, Json] =
    Gen
      .listOfBounded(1, maxElements) {
        for {
          key   <- Gen.alphaNumericStringBounded(1, 8)
          value <- genJsonWithDepth(depth - 1)
        } yield (key, value)
      }
      .map { fields =>
        Json.Object(Chunk.from(fields.distinctBy(_._1)))
      }

  private def genJsonWithDepth(depth: Int): Gen[Any, Json] =
    if (depth <= 0) genPrimitive
    else Gen.oneOf(genPrimitive, genArrayWithDepth(depth), genObjectWithDepth(depth))

  val genJson: Gen[Any, Json] = genJsonWithDepth(maxDepth)

  /** Object with at least 2 fields, useful for removal tests. */
  val genNonEmptyObject: Gen[Any, Json.Object] =
    for {
      k1    <- Gen.alphaNumericStringBounded(1, 6)
      k2    <- Gen.alphaNumericStringBounded(1, 6).filter(_ != k1)
      v1    <- genPrimitive
      v2    <- genPrimitive
      extra <- Gen.listOfBounded(0, 3) {
                 for {
                   k <- Gen.alphaNumericStringBounded(1, 6)
                   v <- genPrimitive
                 } yield (k, v)
               }
    } yield {
      val fields = ((k1, v1) :: (k2, v2) :: extra).distinctBy(_._1)
      Json.Object(Chunk.from(fields))
    }

  /** Object with a nested object field. */
  val genNestedObject: Gen[Any, Json.Object] =
    for {
      outerKey    <- Gen.alphaNumericStringBounded(1, 6)
      innerKey    <- Gen.alphaNumericStringBounded(1, 6)
      innerVal    <- genPrimitive
      otherFields <- Gen.listOfBounded(0, 2) {
                       for {
                         k <- Gen.alphaNumericStringBounded(1, 6)
                         v <- genPrimitive
                       } yield (k, v)
                     }
    } yield {
      val inner  = Json.Object(Chunk((innerKey, innerVal)))
      val fields = ((outerKey, inner) :: otherFields).distinctBy(_._1)
      Json.Object(Chunk.from(fields))
    }

  /** Non-empty array. */
  val genNonEmptyArray: Gen[Any, Json.Array] =
    Gen.listOfBounded(2, 6)(genPrimitive).map(elems => Json.Array(Chunk.from(elems)))

  /** Add a new field to an object. */
  val genWithAddition: Gen[Any, (Json, Json)] =
    for {
      obj    <- genNonEmptyObject
      newKey <- Gen.alphaNumericStringBounded(1, 6).filter(k => !obj.fields.exists(_._1 == k))
      newVal <- genPrimitive
    } yield {
      val updated = Json.Object(obj.fields :+ (newKey, newVal))
      (obj, updated)
    }

  /** Remove a field from an object. */
  val genWithRemoval: Gen[Any, (Json, Json)] =
    genNonEmptyObject.map { obj =>
      val updated = Json.Object(obj.fields.drop(1))
      (obj, updated)
    }

  /** Change a value inside a nested object. */
  val genWithNestedChange: Gen[Any, (Json, Json)] =
    for {
      obj    <- genNestedObject
      newVal <- genPrimitive
    } yield {
      // Find the nested object and change its first field's value
      val updated = Json.Object(obj.fields.map {
        case (k, Json.Object(inner)) if inner.nonEmpty =>
          val (innerKey, _) = inner.head
          (k, Json.Object(Chunk((innerKey, newVal)) ++ inner.tail))
        case other => other
      })
      (obj, updated)
    }

  /** Modify elements in an array (tests LCS-based diffing). */
  val genArrayWithLCS: Gen[Any, (Json, Json)] =
    for {
      arr    <- genNonEmptyArray
      newVal <- genPrimitive
      idx    <- Gen.int(0, arr.elements.length - 1)
    } yield {
      val updated = Json.Array(arr.elements.updated(idx, newVal))
      (arr, updated)
    }

  /** Change a number value in an object. */
  val genWithNumberChange: Gen[Any, (Json, Json)] =
    for {
      key   <- Gen.alphaNumericStringBounded(1, 6)
      n1    <- Gen.int(-1000, 1000)
      n2    <- Gen.int(-1000, 1000).filter(_ != n1)
      extra <- Gen.listOfBounded(0, 2) {
                 for {
                   k <- Gen.alphaNumericStringBounded(1, 6)
                   v <- genPrimitive
                 } yield (k, v)
               }
    } yield {
      val fields1 = ((key, Json.Number(n1.toString)) :: extra).distinctBy(_._1)
      val fields2 = fields1.map {
        case (k, _) if k == key => (k, Json.Number(n2.toString))
        case other              => other
      }
      (Json.Object(Chunk.from(fields1)), Json.Object(Chunk.from(fields2)))
    }

  /** Change a string value in an object. */
  val genWithStringChange: Gen[Any, (Json, Json)] =
    for {
      key   <- Gen.alphaNumericStringBounded(1, 6)
      s1    <- Gen.alphaNumericStringBounded(1, 10)
      s2    <- Gen.alphaNumericStringBounded(1, 10).filter(_ != s1)
      extra <- Gen.listOfBounded(0, 2) {
                 for {
                   k <- Gen.alphaNumericStringBounded(1, 6)
                   v <- genPrimitive
                 } yield (k, v)
               }
    } yield {
      val fields1 = ((key, Json.String(s1)) :: extra).distinctBy(_._1)
      val fields2 = fields1.map {
        case (k, _) if k == key => (k, Json.String(s2))
        case other              => other
      }
      (Json.Object(Chunk.from(fields1)), Json.Object(Chunk.from(fields2)))
    }

  /** Change type at a field (e.g., number to string). */
  val genWithTypeChange: Gen[Any, (Json, Json)] =
    for {
      key   <- Gen.alphaNumericStringBounded(1, 6)
      v1    <- genNumber
      v2    <- genString
      extra <- Gen.listOfBounded(0, 2) {
                 for {
                   k <- Gen.alphaNumericStringBounded(1, 6)
                   v <- genPrimitive
                 } yield (k, v)
               }
    } yield {
      val fields1 = ((key, v1) :: extra).distinctBy(_._1)
      val fields2 = fields1.map {
        case (k, _) if k == key => (k, v2)
        case other              => other
      }
      (Json.Object(Chunk.from(fields1)), Json.Object(Chunk.from(fields2)))
    }

  /** General tweaked pair - picks a random tweak strategy. */
  val genTweakedPair: Gen[Any, (Json, Json)] =
    Gen.oneOf(
      genWithAddition,
      genWithRemoval,
      genWithNestedChange,
      genArrayWithLCS,
      genWithNumberChange,
      genWithStringChange,
      genWithTypeChange
    )

  /** Pair where b is a tweaked version of a. */
  val genTestPair: Gen[Any, (Json, Json)] = genTweakedPair

  /** Triple where each is a tweak of the previous (no duplicate keys). */
  val genTestTriple: Gen[Any, (Json, Json, Json)] =
    for {
      base        <- genNonEmptyObject
      tweak1      <- genPrimitive
      tweak2      <- genPrimitive
      tweak3      <- genPrimitive
      existingKeys = base.fields.map(_._1).toSet
      k1          <- Gen.alphaNumericStringBounded(1, 4).filter(k => !existingKeys.contains(k))
      k2          <- Gen.alphaNumericStringBounded(1, 4).filter(k => !existingKeys.contains(k) && k != k1)
      k3          <- Gen.alphaNumericStringBounded(1, 4).filter(k => !existingKeys.contains(k) && k != k1 && k != k2)
    } yield {
      val a = Json.Object(base.fields :+ (k1, tweak1))
      val b = Json.Object(base.fields :+ (k1, tweak1) :+ (k2, tweak2))
      val c = Json.Object(base.fields :+ (k1, tweak1) :+ (k2, tweak2) :+ (k3, tweak3))
      (a, b, c)
    }
}
