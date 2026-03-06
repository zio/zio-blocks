package zio.blocks.typeid

/**
 * A compile-time witness that `A` is a nominal type.
 *
 * A nominal type is any fully-known, concrete named type: a class, trait,
 * object, enum, or applied type constructor (e.g. `List[Int]`). Abstract type
 * parameters, union types (`A | B`), intersection types (`A & B`), structural
 * refinements, and type lambdas are not nominal and will cause derivation to
 * fail with a compile error.
 *
 * `IsNominalType[A]` is automatically derived by a macro when `A` is nominal.
 * The key effect of requiring `IsNominalType[A]` in a method signature is that
 * it prevents the method from being called with an unresolved type parameter,
 * ensuring the type is concrete at the call site.
 *
 * @tparam A
 *   The type being witnessed as nominal.
 */
trait IsNominalType[A] {
  def typeId: TypeId[A]
  def typeIdErased: TypeId.Erased
}

object IsNominalType extends IsNominalTypeVersionSpecific {
  def apply[A](implicit ev: IsNominalType[A]): IsNominalType[A] = ev
}
