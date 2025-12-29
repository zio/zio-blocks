package zio.blocks.schema

import scala.quoted._

object RequireToStructuralMacro {
  inline def requireToStructural[A]: Unit = ${ requireToStructuralImpl[A] }

  private def requireToStructuralImpl[A: Type](using q: Quotes): Expr[Unit] = {
    import q.reflect._

    val enabled = sys.props.get("zio.blocks.structural.enableNamedTrait").contains("true")

    if (!enabled) {
      '{ () }
    } else {
      val toStructuralTpe = TypeRepr.of[ToStructural].appliedTo(TypeRepr.of[A])
      Implicits.search(toStructuralTpe) match {
        case _: ImplicitSearchSuccess =>
          report.info(s"StructuralStrict: found ToStructural for ${Type.show[A]}")
          '{ () }
        case failure: ImplicitSearchFailure =>
          report.error(s"StructuralStrict: missing ToStructural for ${Type.show[A]}: ${failure.explanation}")
          '{ () }
      }
    }
  }
}
