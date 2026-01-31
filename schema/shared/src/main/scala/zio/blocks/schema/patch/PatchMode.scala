package zio.blocks.schema.patch

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.typeid.TypeId

sealed trait PatchMode

object PatchMode {

  // Fail on precondition violations.
  case object Strict extends PatchMode

  // Skip operations that fail preconditions.
  case object Lenient extends PatchMode

  // Replace/overwrite on conflicts.
  case object Clobber extends PatchMode

  // Schema Definitions, Manual derivation for Scala 2 compatability
  // In Scala 3, You can derive Schema using `Schema.derive`

  implicit lazy val strictSchema: Schema[PatchMode.Strict.type] = new Schema(
    reflect = new Reflect.Record[Binding, PatchMode.Strict.type](
      fields = Vector.empty,
      typeId = TypeId.of[PatchMode.Strict.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[PatchMode.Strict.type](PatchMode.Strict),
        deconstructor = new ConstantDeconstructor[PatchMode.Strict.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val lenientSchema: Schema[PatchMode.Lenient.type] = new Schema(
    reflect = new Reflect.Record[Binding, PatchMode.Lenient.type](
      fields = Vector.empty,
      typeId = TypeId.of[PatchMode.Lenient.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[PatchMode.Lenient.type](PatchMode.Lenient),
        deconstructor = new ConstantDeconstructor[PatchMode.Lenient.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val clobberSchema: Schema[PatchMode.Clobber.type] = new Schema(
    reflect = new Reflect.Record[Binding, PatchMode.Clobber.type](
      fields = Vector.empty,
      typeId = TypeId.of[PatchMode.Clobber.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[PatchMode.Clobber.type](PatchMode.Clobber),
        deconstructor = new ConstantDeconstructor[PatchMode.Clobber.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val schema: Schema[PatchMode] = new Schema(
    reflect = new Reflect.Variant[Binding, PatchMode](
      cases = Vector(
        strictSchema.reflect.asTerm("Strict"),
        lenientSchema.reflect.asTerm("Lenient"),
        clobberSchema.reflect.asTerm("Clobber")
      ),
      typeId = TypeId.of[PatchMode],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[PatchMode] {
          def discriminate(a: PatchMode): Int = a match {
            case _: PatchMode.Strict.type  => 0
            case _: PatchMode.Lenient.type => 1
            case _: PatchMode.Clobber.type => 2
          }
        },
        matchers = Matchers(
          new Matcher[PatchMode.Strict.type] {
            def downcastOrNull(a: Any): PatchMode.Strict.type = a match {
              case x: PatchMode.Strict.type => x
              case _                        => null.asInstanceOf[PatchMode.Strict.type]
            }
          },
          new Matcher[PatchMode.Lenient.type] {
            def downcastOrNull(a: Any): PatchMode.Lenient.type = a match {
              case x: PatchMode.Lenient.type => x
              case _                         => null.asInstanceOf[PatchMode.Lenient.type]
            }
          },
          new Matcher[PatchMode.Clobber.type] {
            def downcastOrNull(a: Any): PatchMode.Clobber.type = a match {
              case x: PatchMode.Clobber.type => x
              case _                         => null.asInstanceOf[PatchMode.Clobber.type]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )
}
