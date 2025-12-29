package zio.blocks.schema

object StructuralDerivedTypedCompileSpec {
  // Simple case class used to ensure `derivedTyped` materializes for Scala 3.
  final case class CompilePerson(name: String, age: Int)

  // This summon should compile if `ToStructuralVersionSpecific.derivedTyped` materializes.
  import zio.blocks.schema.ToStructuralVersionSpecific.derivedTyped

  val summonTyped: ToStructural[CompilePerson] = summon[ToStructural[CompilePerson]]
}
