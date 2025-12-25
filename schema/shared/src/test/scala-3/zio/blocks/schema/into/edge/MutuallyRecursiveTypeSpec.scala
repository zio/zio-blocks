package zio.blocks.schema.into.edge

import zio.test._
import zio.blocks.schema._

object MutuallyRecursiveTypeSpec extends ZIOSpecDefault {

  def spec = suite("MutuallyRecursiveTypeSpec")(
    suite("Mutually Recursive Types")(
      test("should convert mutually recursive types (Ping -> PingCopy)") {
        case class Ping(pong: Pong)
        case class Pong(ping: Option[Ping]) // Option per rompere il ciclo infinito di costruzione valori

        case class PingCopy(pong: PongCopy)
        case class PongCopy(ping: Option[PingCopy])

        val derivation = Into.derived[Ping, PingCopy]
        val input      = Ping(Pong(Some(Ping(Pong(None)))))
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        result.map { pingCopy =>
          assertTrue(pingCopy.pong.ping.isDefined)
          assertTrue(pingCopy.pong.ping.get.pong.ping.isEmpty)
        }
      },
      test("should convert mutually recursive types (identity)") {
        case class Ping(pong: Pong)
        case class Pong(ping: Option[Ping])

        val derivation = Into.derived[Ping, Ping]
        val input      = Ping(Pong(Some(Ping(Pong(None)))))
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        result.map { ping =>
          assertTrue(ping.pong.ping.isDefined)
          assertTrue(ping.pong.ping.get.pong.ping.isEmpty)
        }
      },
      test("should handle deep mutual recursion (A->B->C->A)") {
        case class A(b: Option[B])
        case class B(c: Option[C])
        case class C(a: Option[A])

        case class ACopy(b: Option[BCopy])
        case class BCopy(c: Option[CCopy])
        case class CCopy(a: Option[ACopy])

        val derivation = Into.derived[A, ACopy]
        val input      = A(Some(B(Some(C(Some(A(None)))))))
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        result.map { aCopy =>
          assertTrue(aCopy.b.isDefined)
          assertTrue(aCopy.b.get.c.isDefined)
          assertTrue(aCopy.b.get.c.get.a.isDefined)
          assertTrue(aCopy.b.get.c.get.a.get.b.isEmpty)
        }
      },
      test("should handle empty mutual recursion") {
        case class Ping(pong: Pong)
        case class Pong(ping: Option[Ping])

        case class PingCopy(pong: PongCopy)
        case class PongCopy(ping: Option[PingCopy])

        val derivation = Into.derived[Ping, PingCopy]
        val input      = Ping(Pong(None))
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        result.map { pingCopy =>
          assertTrue(pingCopy.pong.ping.isEmpty)
        }
      }
    )
  )
}
