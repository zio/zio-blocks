package zio.blocks.schema

import zio.blocks.schema.internal.AsVersionSpecific

trait As[A, B] { self =>
  def to(a: A): Either[SchemaError, B]
  def from(b: B): Either[SchemaError, A]

  def flip: As[B, A] = new As[B, A] {
    def to(b: B): Either[SchemaError, A]   = self.from(b)
    def from(a: A): Either[SchemaError, B] = self.to(a)
  }
}

object As extends AsVersionSpecific {
  def apply[A, B](implicit ev: As[A, B]): As[A, B] = ev

  def instance[A, B](f: A => Either[SchemaError, B], g: B => Either[SchemaError, A]): As[A, B] = new As[A, B] {
    def to(a: A)   = f(a)
    def from(b: B) = g(b)
  }

  implicit def identity[A]: As[A, A] = instance(Right(_), Right(_))

  implicit def listAs[A, B](implicit ev: As[A, B]): As[List[A], List[B]] = {
    def convert[X, Y](xs: List[X], f: X => Either[SchemaError, Y]): Either[SchemaError, List[Y]] = {
      val results = xs.zipWithIndex.map { case (x, idx) => f(x).left.map(_.atPath(s"[$idx]")) }
      SchemaError.accumulateErrors(results).map(_.toList)
    }
    instance(l => convert(l, ev.to), l => convert(l, ev.from))
  }
}
