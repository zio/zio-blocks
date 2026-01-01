package zio.blocks.typeid

import scala.quoted._

trait TypeIdVersionSpecific {
  inline def derive[A]: TypeId[A] =
    ${ TypeIdMacros.deriveMacro[A] }

  inline def derive1[F[_]]: TypeId[F] =
    ${ TypeIdMacros.deriveMacro[F] }

  inline def derive2[F[_, _]]: TypeId[F] =
    ${ TypeIdMacros.deriveMacro[F] }
}
