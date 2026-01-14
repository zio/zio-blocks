package zio.blocks.schema.patch

import zio.blocks.schema._
import zio.blocks.schema.binding._

sealed trait PatchMode

object PatchMode {

  // Fail on precondition violations.
  case object Strict extends PatchMode

  // Skip operations that fail preconditions.
  case object Lenient extends PatchMode

  // Replace/overwrite on conflicts.

  case object Clobber extends PatchMode

  // Schema instance for PatchMode - manually written for Scala 2 compatibility
  implicit lazy val schema: Schema[PatchMode] = {
    import zio.blocks.schema._

    new Schema(
      reflect = new Reflect.Variant[Binding, PatchMode](
        cases = Vector(
          strictSchema.reflect.asTerm("Strict"),
          lenientSchema.reflect.asTerm("Lenient"),
          clobberSchema.reflect.asTerm("Clobber")
        ),
        typeName = TypeName(Namespace(List("zio", "blocks", "schema", "patch")), "PatchMode"),
        variantBinding = new Binding.Variant(
          discriminator = new Discriminator[PatchMode] {
            def discriminate(a: PatchMode): Int = a match {
              case _: Strict.type  => 0
              case _: Lenient.type => 1
              case _: Clobber.type => 2
            }
          },
          matchers = Matchers(
            new Matcher[Strict.type] {
              def downcastOrNull(a: Any): Strict.type = a match {
                case x: Strict.type => x
                case _              => null.asInstanceOf[Strict.type]
              }
            },
            new Matcher[Lenient.type] {
              def downcastOrNull(a: Any): Lenient.type = a match {
                case x: Lenient.type => x
                case _               => null.asInstanceOf[Lenient.type]
              }
            },
            new Matcher[Clobber.type] {
              def downcastOrNull(a: Any): Clobber.type = a match {
                case x: Clobber.type => x
                case _               => null.asInstanceOf[Clobber.type]
              }
            }
          )
        ),
        modifiers = Vector.empty
      )
    )
  }

  private lazy val strictSchema: Schema[Strict.type]   = Schema.derived
  private lazy val lenientSchema: Schema[Lenient.type] = Schema.derived
  private lazy val clobberSchema: Schema[Clobber.type] = Schema.derived

}
