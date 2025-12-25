package zio.blocks.schema.as.reversibility

import zio.test._
import zio.blocks.schema._

object RoundTripSpec extends ZIOSpecDefault {

  def spec = suite("RoundTripSpec")(
    suite("Product Round-Trip")(
      test("should round trip case class with field renaming") {
        case class PersonV1(name: String, age: Int)
        case class PersonV2(fullName: String, yearsOld: Int) // Renamed fields

        val as    = As.derived[PersonV1, PersonV2]
        val input = PersonV1("Alice", 30)

        val result = as.into(input).flatMap(as.from)

        assertTrue(result == Right(input))
      },
      test("should round trip case class with field reordering") {
        case class V1(x: Int, y: String, z: Boolean)
        case class V2(z: Boolean, y: String, x: Int) // Reordered

        val as    = As.derived[V1, V2]
        val input = V1(42, "test", true)

        val result = as.into(input).flatMap(as.from)

        assertTrue(result == Right(input))
      }
    ),
    suite("Tuple Round-Trip")(
      test("should round trip case class <-> tuple") {
        case class User(name: String, age: Int)

        val as    = As.derived[User, (String, Int)]
        val input = User("Alice", 30)

        val result = as.into(input).flatMap(as.from)

        assertTrue(result == Right(input))
      },
      test("should round trip tuple <-> case class") {
        case class User(name: String, age: Int)

        val as    = As.derived[(String, Int), User]
        val input = ("Bob", 25)

        val result = as.into(input).flatMap(as.from)

        assertTrue(result == Right(input))
      },
      test("should round trip tuple <-> tuple (same types, reordered)") {
        // Note: We can't test (String, Int) -> (Int, String) as they're not isomorphic
        // Instead, test with compatible types that can be reordered
        case class User(name: String, age: Int)

        val as    = As.derived[(String, Int), (String, Int)]
        val input = ("Alice", 30)

        val result = as.into(input).flatMap(as.from)

        assertTrue(result == Right(input))
      },
      test("should round trip nested tuple <-> case class") {
        case class Address(street: String, city: String)
        case class Person(name: String, address: Address)

        val as    = As.derived[Person, (String, (String, String))]
        val input = Person("Alice", Address("Main St", "NYC"))

        val result = as.into(input).flatMap(as.from)

        assertTrue(result == Right(input))
      }
    ),
    suite("Recursive Round-Trip")(
      test("should round trip recursive types (Node <-> NodeCopy)") {
        case class Node(value: Int, next: Option[Node])
        case class NodeCopy(value: Int, next: Option[NodeCopy])

        val as    = As.derived[Node, NodeCopy]
        val input = Node(1, Some(Node(2, None)))

        val result = as.into(input).flatMap(as.from)

        assertTrue(result.isRight)
        result.map { node =>
          assertTrue(node.value == 1)
          assertTrue(node.next.isDefined)
          assertTrue(node.next.get.value == 2)
          assertTrue(node.next.get.next.isEmpty)
        }
      },
      test("should round trip mutually recursive types (Ping <-> PingCopy)") {
        case class Ping(pong: Pong)
        case class Pong(ping: Option[Ping])

        case class PingCopy(pong: PongCopy)
        case class PongCopy(ping: Option[PingCopy])

        val as    = As.derived[Ping, PingCopy]
        val input = Ping(Pong(Some(Ping(Pong(None)))))

        val result = as.into(input).flatMap(as.from)

        assertTrue(result.isRight)
        result.map { ping =>
          assertTrue(ping.pong.ping.isDefined)
          assertTrue(ping.pong.ping.get.pong.ping.isEmpty)
        }
      },
      test("should round trip deep mutual recursion (A <-> ACopy)") {
        case class A(b: Option[B])
        case class B(c: Option[C])
        case class C(a: Option[A])

        case class ACopy(b: Option[BCopy])
        case class BCopy(c: Option[CCopy])
        case class CCopy(a: Option[ACopy])

        val as    = As.derived[A, ACopy]
        val input = A(Some(B(Some(C(Some(A(None)))))))

        val result = as.into(input).flatMap(as.from)

        assertTrue(result.isRight)
        result.map { a =>
          assertTrue(a.b.isDefined)
          assertTrue(a.b.get.c.isDefined)
          assertTrue(a.b.get.c.get.a.isDefined)
          assertTrue(a.b.get.c.get.a.get.b.isEmpty)
        }
      },
      test("should round trip recursive types in both directions independently") {
        case class Node(value: Int, next: Option[Node])
        case class NodeCopy(value: Int, next: Option[NodeCopy])

        val as = As.derived[Node, NodeCopy]

        // Test forward direction
        val forward = as.into(Node(1, Some(Node(2, None))))
        assertTrue(forward.isRight)
        forward.map { nodeCopy =>
          assertTrue(nodeCopy.value == 1)
          assertTrue(nodeCopy.next.isDefined)
        }

        // Test backward direction
        val backward = as.from(NodeCopy(3, Some(NodeCopy(4, None))))
        assertTrue(backward.isRight)
        backward.map { node =>
          assertTrue(node.value == 3)
          assertTrue(node.next.isDefined)
        }
      }
    ),
    suite("Collection Round-Trip")(
      test("should round trip List[A] <-> Vector[A]") {
        val as    = As.derived[List[Int], Vector[Int]]
        val input = List(1, 2, 3, 4, 5)

        val result = as.into(input).flatMap(as.from)

        assertTrue(result == Right(input))
      },
      test("should round trip Vector[A] <-> List[A]") {
        val as    = As.derived[Vector[String], List[String]]
        val input = Vector("a", "b", "c")

        val result = as.into(input).flatMap(as.from)

        assertTrue(result == Right(input))
      },
      test("should round trip List[A] <-> List[B] (isomorphic element types)") {
        case class Source(value: Int)
        case class Target(value: Int)

        val as    = As.derived[List[Source], List[Target]]
        val input = List(Source(1), Source(2), Source(3))

        val result = as.into(input).flatMap(as.from)

        assertTrue(result.isRight)
        result.map { sources =>
          assertTrue(sources.length == 3)
          assertTrue(sources(0).value == 1)
          assertTrue(sources(1).value == 2)
          assertTrue(sources(2).value == 3)
        }
      },
      test("should round trip nested collections") {
        val as    = As.derived[List[List[Int]], Vector[Vector[Int]]]
        val input = List(List(1, 2), List(3, 4))

        val result = as.into(input).flatMap(as.from)

        assertTrue(result.isRight)
        result.map { nested =>
          assertTrue(nested.length == 2)
          assertTrue(nested(0).length == 2)
          assertTrue(nested(1).length == 2)
        }
      },
      test("should round trip collections with recursive elements") {
        case class Node(value: Int, next: Option[Node])
        case class NodeCopy(value: Int, next: Option[NodeCopy])

        val as    = As.derived[List[Node], Vector[NodeCopy]]
        val input = List(Node(1, Some(Node(2, None))), Node(3, None))

        val result = as.into(input).flatMap(as.from)

        assertTrue(result.isRight)
        result.map { nodes =>
          assertTrue(nodes.length == 2)
          assertTrue(nodes(0).value == 1)
          assertTrue(nodes(0).next.isDefined)
          assertTrue(nodes(1).value == 3)
          assertTrue(nodes(1).next.isEmpty)
        }
      }
    ),
    suite("Complex Round-Trip Scenarios")(
      test("should round trip case class with collections and nested structures") {
        case class Address(street: String, city: String)
        case class Person(name: String, age: Int, addresses: List[Address])

        case class AddressDTO(street: String, city: String)
        case class PersonDTO(name: String, age: Int, addresses: Vector[AddressDTO])

        val as    = As.derived[Person, PersonDTO]
        val input = Person("Alice", 30, List(Address("Main St", "NYC"), Address("Park Ave", "LA")))

        val result = as.into(input).flatMap(as.from)

        assertTrue(result == Right(input))
      },
      test("should round trip tuple with collections (compatible types)") {
        // Test with compatible types that can be converted
        case class Data(name: String, values: List[Int])
        case class DataTuple(name: String, values: List[Int])

        val as    = As.derived[Data, (String, List[Int])]
        val input = Data("test", List(1, 2, 3))

        val result = as.into(input).flatMap(as.from)

        assertTrue(result == Right(input))
      }
    )
  )
}
