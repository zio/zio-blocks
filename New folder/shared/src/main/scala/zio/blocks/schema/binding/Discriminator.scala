package zio.blocks.schema.binding

/**
 * A `Discriminator` is a typeclass that can discriminate between different
 * terms of a sum type, by returning a numerical index that represents which
 * term in the sum type the value represents.
 */
trait Discriminator[-A] {
  def discriminate(a: A): Int
}
