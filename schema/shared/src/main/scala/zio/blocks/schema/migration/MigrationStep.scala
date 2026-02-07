package zio.blocks.schema.migration

import scala.collection.immutable.{Map => IMap}

import zio.blocks.schema.{DynamicValue, Reflect, Schema}
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.TypeId

sealed trait MigrationStep {
  def reverse: MigrationStep
  def isEmpty: Boolean
}

object MigrationStep {

  final case class Record(
    fieldActions: Vector[FieldAction],
    nestedFields: IMap[String, MigrationStep]
  ) extends MigrationStep {

    def reverse: MigrationStep = {
      val renameMapping       = fieldActions.collect { case FieldAction.Rename(from, to) => to -> from }.toMap
      val updatedNestedFields = nestedFields.map { case (k, v) =>
        renameMapping.getOrElse(k, k) -> v.reverse
      }
      Record(
        fieldActions.reverseIterator.map(_.reverse).toVector,
        updatedNestedFields
      )
    }

    def isEmpty: Boolean = fieldActions.isEmpty && nestedFields.values.forall(_.isEmpty)

    def withFieldAction(action: FieldAction): Record =
      copy(fieldActions = fieldActions :+ action)

    private def withNestedField(fieldName: String, step: MigrationStep): Record =
      copy(nestedFields = nestedFields + (fieldName -> step))

    def addField(name: String, defaultValue: DynamicValue): Record =
      withFieldAction(FieldAction.Add(name, defaultValue))

    def removeField(name: String, defaultForReverse: DynamicValue): Record =
      withFieldAction(FieldAction.Remove(name, defaultForReverse))

    def renameField(from: String, to: String): Record =
      withFieldAction(FieldAction.Rename(from, to))

    def transformField(
      name: String,
      forward: DynamicValueTransform,
      backward: DynamicValueTransform
    ): Record =
      withFieldAction(FieldAction.Transform(name, forward, backward))

    def makeFieldOptional(name: String, defaultForReverse: DynamicValue): Record =
      withFieldAction(FieldAction.MakeOptional(name, defaultForReverse))

    def makeFieldRequired(name: String, defaultForNone: DynamicValue): Record =
      withFieldAction(FieldAction.MakeRequired(name, defaultForNone))

    def changeFieldType(
      name: String,
      forward: PrimitiveConversion,
      backward: PrimitiveConversion
    ): Record =
      withFieldAction(FieldAction.ChangeType(name, forward, backward))

    def joinFields(
      targetName: String,
      sourceNames: Vector[String],
      combiner: DynamicValueTransform,
      splitter: DynamicValueTransform
    ): Record =
      withFieldAction(FieldAction.JoinFields(targetName, sourceNames, combiner, splitter))

    def splitField(
      sourceName: String,
      targetNames: Vector[String],
      splitter: DynamicValueTransform,
      combiner: DynamicValueTransform
    ): Record =
      withFieldAction(FieldAction.SplitField(sourceName, targetNames, splitter, combiner))

    def nested(fieldName: String)(buildNested: Record => Record): Record = {
      val existingNested = nestedFields.getOrElse(fieldName, Record.empty) match {
        case r: Record => r
        case _         => Record.empty
      }
      withNestedField(fieldName, buildNested(existingNested))
    }
  }

  object Record {
    val empty: Record = Record(Vector.empty, IMap.empty)
  }

  final case class Variant(
    renames: IMap[String, String],
    nestedCases: IMap[String, MigrationStep]
  ) extends MigrationStep {

    def reverse: MigrationStep = {
      val reverseRenames     = renames.map { case (from, to) => to -> from }
      val updatedNestedCases = nestedCases.map { case (k, v) =>
        reverseRenames.getOrElse(k, k) -> v.reverse
      }
      Variant(reverseRenames, updatedNestedCases)
    }

    def isEmpty: Boolean = renames.isEmpty && nestedCases.values.forall(_.isEmpty)

    private def withNestedCase(caseName: String, step: MigrationStep): Variant =
      copy(nestedCases = nestedCases + (caseName -> step))

    def renameCase(from: String, to: String): Variant =
      copy(renames = renames + (from -> to))

    def nested(caseName: String)(buildNested: Record => Record): Variant = {
      val existingNested = nestedCases.getOrElse(caseName, Record.empty) match {
        case r: Record => r
        case _         => Record.empty
      }
      withNestedCase(caseName, buildNested(existingNested))
    }

    def transformCase(caseName: String)(buildNested: Record => Record): Variant =
      nested(caseName)(buildNested)
  }

  object Variant {
    val empty: Variant = Variant(IMap.empty, IMap.empty)
  }

  final case class Sequence(elementStep: MigrationStep) extends MigrationStep {
    def reverse: MigrationStep = Sequence(elementStep.reverse)
    def isEmpty: Boolean       = elementStep.isEmpty
  }

  final case class MapEntries(
    keyStep: MigrationStep,
    valueStep: MigrationStep
  ) extends MigrationStep {
    def reverse: MigrationStep = MapEntries(keyStep.reverse, valueStep.reverse)
    def isEmpty: Boolean       = keyStep.isEmpty && valueStep.isEmpty
  }

  case object NoOp extends MigrationStep {
    def reverse: MigrationStep = NoOp
    def isEmpty: Boolean       = true
  }

  implicit lazy val noOpSchema: Schema[NoOp.type] = new Schema(
    reflect = new Reflect.Record[Binding, NoOp.type](
      fields = Vector.empty,
      typeId = TypeId.of[NoOp.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[NoOp.type](NoOp),
        deconstructor = new ConstantDeconstructor[NoOp.type]
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val deferredMapOfMigrationStep: Reflect[Binding, IMap[String, MigrationStep]] =
    Reflect.Deferred(() => Reflect.map(Schema[String].reflect, schema.reflect))

  private lazy val deferredMigrationStepReflect: Reflect[Binding, MigrationStep] =
    Reflect.Deferred(() => schema.reflect)

  implicit lazy val recordStepSchema: Schema[Record] = new Schema(
    reflect = new Reflect.Record[Binding, Record](
      fields = Vector(
        Schema.vector(FieldAction.schema).reflect.asTerm("fieldActions"),
        deferredMapOfMigrationStep.asTerm("nestedFields")
      ),
      typeId = TypeId.of[Record],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Record] {
          def usedRegisters: RegisterOffset                            = 2
          def construct(in: Registers, offset: RegisterOffset): Record =
            Record(
              in.getObject(offset + 0).asInstanceOf[Vector[FieldAction]],
              in.getObject(offset + 1).asInstanceOf[IMap[String, MigrationStep]]
            )
        },
        deconstructor = new Deconstructor[Record] {
          def usedRegisters: RegisterOffset                                         = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Record): Unit = {
            out.setObject(offset + 0, in.fieldActions)
            out.setObject(offset + 1, in.nestedFields)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val variantStepSchema: Schema[Variant] = new Schema(
    reflect = new Reflect.Record[Binding, Variant](
      fields = Vector(
        Schema[IMap[String, String]].reflect.asTerm("renames"),
        deferredMapOfMigrationStep.asTerm("nestedCases")
      ),
      typeId = TypeId.of[Variant],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Variant] {
          def usedRegisters: RegisterOffset                             = 2
          def construct(in: Registers, offset: RegisterOffset): Variant =
            Variant(
              in.getObject(offset + 0).asInstanceOf[IMap[String, String]],
              in.getObject(offset + 1).asInstanceOf[IMap[String, MigrationStep]]
            )
        },
        deconstructor = new Deconstructor[Variant] {
          def usedRegisters: RegisterOffset                                          = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Variant): Unit = {
            out.setObject(offset + 0, in.renames)
            out.setObject(offset + 1, in.nestedCases)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val sequenceStepSchema: Schema[Sequence] = new Schema(
    reflect = new Reflect.Record[Binding, Sequence](
      fields = Vector(
        deferredMigrationStepReflect.asTerm("elementStep")
      ),
      typeId = TypeId.of[Sequence],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Sequence] {
          def usedRegisters: RegisterOffset                              = 1
          def construct(in: Registers, offset: RegisterOffset): Sequence =
            Sequence(in.getObject(offset + 0).asInstanceOf[MigrationStep])
        },
        deconstructor = new Deconstructor[Sequence] {
          def usedRegisters: RegisterOffset                                           = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Sequence): Unit =
            out.setObject(offset + 0, in.elementStep)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val mapEntriesSchema: Schema[MapEntries] = new Schema(
    reflect = new Reflect.Record[Binding, MapEntries](
      fields = Vector(
        deferredMigrationStepReflect.asTerm("keyStep"),
        deferredMigrationStepReflect.asTerm("valueStep")
      ),
      typeId = TypeId.of[MapEntries],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MapEntries] {
          def usedRegisters: RegisterOffset                                = 2
          def construct(in: Registers, offset: RegisterOffset): MapEntries =
            MapEntries(
              in.getObject(offset + 0).asInstanceOf[MigrationStep],
              in.getObject(offset + 1).asInstanceOf[MigrationStep]
            )
        },
        deconstructor = new Deconstructor[MapEntries] {
          def usedRegisters: RegisterOffset                                             = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MapEntries): Unit = {
            out.setObject(offset + 0, in.keyStep)
            out.setObject(offset + 1, in.valueStep)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val schema: Schema[MigrationStep] = new Schema(
    reflect = new Reflect.Variant[Binding, MigrationStep](
      cases = Vector(
        recordStepSchema.reflect.asTerm("Record"),
        variantStepSchema.reflect.asTerm("Variant"),
        sequenceStepSchema.reflect.asTerm("Sequence"),
        mapEntriesSchema.reflect.asTerm("MapEntries"),
        noOpSchema.reflect.asTerm("NoOp")
      ),
      typeId = TypeId.of[MigrationStep],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[MigrationStep] {
          def discriminate(a: MigrationStep): Int = a match {
            case _: Record     => 0
            case _: Variant    => 1
            case _: Sequence   => 2
            case _: MapEntries => 3
            case _: NoOp.type  => 4
          }
        },
        matchers = Matchers(
          new Matcher[Record] {
            def downcastOrNull(a: Any): Record = a match {
              case x: Record => x
              case _         => null.asInstanceOf[Record]
            }
          },
          new Matcher[Variant] {
            def downcastOrNull(a: Any): Variant = a match {
              case x: Variant => x
              case _          => null.asInstanceOf[Variant]
            }
          },
          new Matcher[Sequence] {
            def downcastOrNull(a: Any): Sequence = a match {
              case x: Sequence => x
              case _           => null.asInstanceOf[Sequence]
            }
          },
          new Matcher[MapEntries] {
            def downcastOrNull(a: Any): MapEntries = a match {
              case x: MapEntries => x
              case _             => null.asInstanceOf[MapEntries]
            }
          },
          new Matcher[NoOp.type] {
            def downcastOrNull(a: Any): NoOp.type = a match {
              case x: NoOp.type => x
              case _            => null.asInstanceOf[NoOp.type]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )
}
