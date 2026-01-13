package golem.runtime.rpc

import golem.data.GolemSchema
import org.scalatest.funsuite.AnyFunSuite
import zio.blocks.schema.Schema

import scala.util.Random

private[rpc] object ValueMappingFuzzSpecTypes {
  final case class TinyProduct(a: Int, b: String, c: Option[Int], d: List[String])
  object TinyProduct { implicit val schema: Schema[TinyProduct] = Schema.derived }

  sealed trait TinySum
  object TinySum {
    case object A                      extends TinySum
    final case class B(i: Int)         extends TinySum
    final case class C(p: TinyProduct) extends TinySum
    implicit val schema: Schema[TinySum] = Schema.derived
  }
}

final class ValueMappingFuzzSpec extends AnyFunSuite {
  import ValueMappingFuzzSpecTypes._

  private val rng = new Random(0xc0ffee)

  private def genString(max: Int): String = {
    val n  = rng.nextInt(max + 1)
    val sb = new StringBuilder(n)
    var i  = 0
    while (i < n) {
      val ch = ('a'.toInt + rng.nextInt(26)).toChar
      sb.append(ch)
      i += 1
    }
    sb.result()
  }

  private def genTinyProduct(): TinyProduct =
    TinyProduct(
      a = rng.nextInt(1000) - 500,
      b = genString(16),
      c = if (rng.nextBoolean()) Some(rng.nextInt(100)) else None,
      d = List.fill(rng.nextInt(5))(genString(8))
    )

  private def genTinySum(): TinySum =
    rng.nextInt(3) match {
      case 0 => TinySum.A
      case 1 => TinySum.B(rng.nextInt(1000))
      case _ => TinySum.C(genTinyProduct())
    }

  private def rpcRoundTrip[A: Schema](label: String, iterations: Int)(gen: => A): Unit = {
    implicit val gs: GolemSchema[A] = GolemSchema.fromBlocksSchema[A]
    test(s"rpc roundtrip fuzz: $label ($iterations cases)") {
      var i = 0
      while (i < iterations) {
        val in     = gen
        val params = RpcValueCodec.encodeArgs(in).fold(err => fail(err), identity)
        val out    = RpcValueCodec.decodeValue[A](params(0)).fold(err => fail(err), identity)
        assert(out == in)
        i += 1
      }
    }
  }

  rpcRoundTrip[Int]("int", 200)(rng.nextInt())
  rpcRoundTrip[String]("string", 200)(genString(64))
  rpcRoundTrip[Option[Int]]("option", 200)(if (rng.nextBoolean()) Some(rng.nextInt(1000)) else None)
  rpcRoundTrip[List[String]]("list", 150)(List.fill(rng.nextInt(6))(genString(12)))
  rpcRoundTrip[Map[String, Int]]("map", 150)(
    (0 until rng.nextInt(6)).map(_ => genString(6) -> rng.nextInt(100)).toMap
  )
  rpcRoundTrip[TinyProduct]("product", 150)(genTinyProduct())
  rpcRoundTrip[TinySum]("sum", 150)(genTinySum())
}
