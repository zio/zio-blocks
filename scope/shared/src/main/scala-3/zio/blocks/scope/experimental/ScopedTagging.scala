package zio.blocks.scope.experimental

import scala.util.NotGiven

// === Core Types ===

opaque infix type @@[A, S] = A
object @@ {
  def tag[A, S](a: A): A @@ S = a

  extension [A, S](tagged: A @@ S) {
    infix def $[B](f: A => B)(using scope: Scope { type Tag = S })(using u: Untag[B, S]): u.Out =
      u(f(tagged))
  }
}

trait Scope {
  type Tag
}

// === SafeData: types that can escape untagged ===
// (Better name? PureData? StaticData? Escapable?)

trait SafeData[A]
object SafeData {
  given SafeData[Int]                                = new SafeData[Int] {}
  given SafeData[String]                             = new SafeData[String] {}
  given SafeData[Boolean]                            = new SafeData[Boolean] {}
  given [A: SafeData]: SafeData[Array[A]]            = new SafeData[Array[A]] {}
  given [A: SafeData]: SafeData[List[A]]             = new SafeData[List[A]] {}
  given [A: SafeData]: SafeData[Option[A]]           = new SafeData[Option[A]] {}
  given SafeData[Byte]                               = new SafeData[Byte] {}
  given SafeData[Short]                              = new SafeData[Short] {}
  given SafeData[Long]                               = new SafeData[Long] {}
  given SafeData[Float]                              = new SafeData[Float] {}
  given SafeData[Double]                             = new SafeData[Double] {}
  given SafeData[Char]                               = new SafeData[Char] {}
  given SafeData[Unit]                               = new SafeData[Unit] {}
  given SafeData[BigInt]                             = new SafeData[BigInt] {}
  given SafeData[BigDecimal]                         = new SafeData[BigDecimal] {}
  given [A: SafeData, B: SafeData]: SafeData[(A, B)] = new SafeData[(A, B)] {}
  // Types with Schema are SafeData (conceptually)
}

// === Conditional untagging ===

trait Untag[A, S] {
  type Out
  def apply(a: A): Out
}

object Untag {
  // SafeData escapes untagged
  given safeData[A, S](using SafeData[A]): Untag[A, S] with {
    type Out = A
    def apply(a: A): Out = a
  }

  // Everything else stays tagged (safer default)
  given resourceful[A, S](using NotGiven[SafeData[A]]): Untag[A, S] with {
    type Out = A @@ S
    def apply(a: A): Out = @@.tag(a)
  }
}
