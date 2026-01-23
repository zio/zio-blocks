package zio.blocks.schema.bson

import zio.blocks.schema.Schema
import zio.test._

object BsonCodecRecursiveSpec extends ZIOSpecDefault {

  // Recursive data structure - Binary Tree
  sealed trait Tree
  object Tree {
    case object Empty                                          extends Tree
    final case class Node(value: Int, left: Tree, right: Tree) extends Tree

    implicit val schema: Schema[Tree] = Schema.derived[Tree]
  }

  // Recursive data structure - Linked List
  sealed trait LinkedList
  object LinkedList {
    case object Nil                                       extends LinkedList
    final case class Cons(head: String, tail: LinkedList) extends LinkedList

    implicit val schema: Schema[LinkedList] = Schema.derived[LinkedList]
  }

  // Mutually recursive structures
  sealed trait Expr
  object Expr {
    final case class Literal(value: Int)                extends Expr
    final case class Add(left: Expr, right: Expr)       extends Expr
    final case class Multiply(left: Expr, right: Expr)  extends Expr
    final case class Block(statements: List[Statement]) extends Expr

    implicit val schema: Schema[Expr] = Schema.derived[Expr]
  }

  sealed trait Statement
  object Statement {
    final case class Assign(name: String, value: Expr) extends Statement
    final case class Print(expr: Expr)                 extends Statement

    implicit val schema: Schema[Statement] = Schema.derived[Statement]
  }

  def spec = suite("BsonCodecRecursiveSpec")(
    suite("Binary Tree")(
      test("encode/decode Empty") {
        val tree: Tree = Tree.Empty
        val codec      = BsonSchemaCodec.bsonCodec(Schema[Tree])

        val encoded = codec.encoder.toBsonValue(tree)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == tree)
      },
      test("encode/decode single Node") {
        val tree: Tree = Tree.Node(42, Tree.Empty, Tree.Empty)
        val codec      = BsonSchemaCodec.bsonCodec(Schema[Tree])

        val encoded = codec.encoder.toBsonValue(tree)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == tree)
      },
      test("encode/decode nested tree") {
        val tree: Tree = Tree.Node(
          10,
          Tree.Node(5, Tree.Empty, Tree.Empty),
          Tree.Node(15, Tree.Empty, Tree.Empty)
        )
        val codec = BsonSchemaCodec.bsonCodec(Schema[Tree])

        val encoded = codec.encoder.toBsonValue(tree)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == tree)
      },
      test("encode/decode deeply nested tree") {
        val tree: Tree = Tree.Node(
          1,
          Tree.Node(
            2,
            Tree.Node(3, Tree.Empty, Tree.Empty),
            Tree.Node(4, Tree.Empty, Tree.Empty)
          ),
          Tree.Node(
            5,
            Tree.Node(6, Tree.Empty, Tree.Empty),
            Tree.Empty
          )
        )
        val codec = BsonSchemaCodec.bsonCodec(Schema[Tree])

        val encoded = codec.encoder.toBsonValue(tree)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == tree)
      }
    ),
    suite("Linked List")(
      test("encode/decode Nil") {
        val list: LinkedList = LinkedList.Nil
        val codec            = BsonSchemaCodec.bsonCodec(Schema[LinkedList])

        val encoded = codec.encoder.toBsonValue(list)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == list)
      },
      test("encode/decode single element") {
        val list: LinkedList = LinkedList.Cons("first", LinkedList.Nil)
        val codec            = BsonSchemaCodec.bsonCodec(Schema[LinkedList])

        val encoded = codec.encoder.toBsonValue(list)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == list)
      },
      test("encode/decode multiple elements") {
        val list: LinkedList = LinkedList.Cons(
          "first",
          LinkedList.Cons(
            "second",
            LinkedList.Cons("third", LinkedList.Nil)
          )
        )
        val codec = BsonSchemaCodec.bsonCodec(Schema[LinkedList])

        val encoded = codec.encoder.toBsonValue(list)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == list)
      }
    ),
    suite("Mutually recursive structures")(
      test("encode/decode simple literal") {
        val expr: Expr = Expr.Literal(42)
        val codec      = BsonSchemaCodec.bsonCodec(Schema[Expr])

        val encoded = codec.encoder.toBsonValue(expr)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == expr)
      },
      test("encode/decode arithmetic expression") {
        val expr: Expr = Expr.Add(
          Expr.Literal(10),
          Expr.Multiply(Expr.Literal(5), Expr.Literal(2))
        )
        val codec = BsonSchemaCodec.bsonCodec(Schema[Expr])

        val encoded = codec.encoder.toBsonValue(expr)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == expr)
      },
      test("encode/decode block with statements") {
        val expr: Expr = Expr.Block(
          List(
            Statement.Assign("x", Expr.Literal(10)),
            Statement.Print(Expr.Add(Expr.Literal(5), Expr.Literal(3)))
          )
        )
        val codec = BsonSchemaCodec.bsonCodec(Schema[Expr])

        val encoded = codec.encoder.toBsonValue(expr)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == expr)
      },
      test("encode/decode statement with expression") {
        val stmt: Statement = Statement.Assign(
          "result",
          Expr.Multiply(
            Expr.Add(Expr.Literal(2), Expr.Literal(3)),
            Expr.Literal(4)
          )
        )
        val codec = BsonSchemaCodec.bsonCodec(Schema[Statement])

        val encoded = codec.encoder.toBsonValue(stmt)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == stmt)
      }
    )
  )
}
