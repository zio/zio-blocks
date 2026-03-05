package zio.blocks.schema.derive

trait Derivable[-D, TC[_]] {
  def deriver(d: D): Deriver[TC]
}

object Derivable {
  implicit def deriverDerivable[TC[_]]: Derivable[Deriver[TC], TC] =
    new Derivable[Deriver[TC], TC] {
      def deriver(d: Deriver[TC]): Deriver[TC] = d
    }
}
