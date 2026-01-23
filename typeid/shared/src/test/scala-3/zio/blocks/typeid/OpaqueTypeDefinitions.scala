package zio.blocks.typeid

/**
 * Opaque type definitions for testing. These must be defined in a separate
 * compilation unit so that TypeId derivation happens from outside the defining
 * scope (where opaque types are transparent).
 */
object OpaqueTypeDefinitions {
  // Define opaque types
  opaque type Email  = String
  opaque type UserId = String
  opaque type Age    = Int

  // Expose TypeIds derived from WITHIN the defining scope
  // This is important: within this object, the macro sees the underlying type
  // But from outside, opaque types should be nominally distinct
  val emailTypeId: TypeId[Email]   = TypeId.of[Email]
  val userIdTypeId: TypeId[UserId] = TypeId.of[UserId]
  val ageTypeId: TypeId[Age]       = TypeId.of[Age]

  // Expose underlying type TypeIds for comparison
  val stringTypeId: TypeId[String] = TypeId.of[String]
  val intTypeId: TypeId[Int]       = TypeId.of[Int]

  // Factory methods (so we can use the opaque types from outside)
  def email(s: String): Email   = s
  def userId(s: String): UserId = s
  def age(i: Int): Age          = i
}
