/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.{DynamicOptic, Reflect, Schema}
import zio.blocks.typeid.TypeId

sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}

object MigrationAction {
  final case class AddField(at: DynamicOptic, default: MigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, default)
  }

  final case class DropField(at: DynamicOptic, defaultForReverse: MigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  final case class Rename(at: DynamicOptic, to: String) extends MigrationAction {
    def reverse: MigrationAction = Rename(DynamicOptic.root.field(to), at.toString.stripPrefix("."))
  }

  final case class TransformValue(at: DynamicOptic, transform: MigrationExpr) extends MigrationAction {
    /** Reverse is best-effort: only identity-preserving transforms are invertible. */
    def reverse: MigrationAction = TransformValue(at, MigrationExpr.Identity)
  }

  final case class Mandate(at: DynamicOptic, default: MigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at)
  }

  final case class Optionalize(at: DynamicOptic) extends MigrationAction {
    def reverse: MigrationAction = Mandate(at, MigrationExpr.DefaultValue)
  }

  final case class Join(at: DynamicOptic, sourcePaths: Vector[DynamicOptic], combiner: MigrationExpr) extends MigrationAction {
    /** Reverse is best-effort and does not reconstruct original source segmentation. */
    def reverse: MigrationAction = Split(at, sourcePaths, MigrationExpr.Identity)
  }

  final case class Split(at: DynamicOptic, targetPaths: Vector[DynamicOptic], splitter: MigrationExpr) extends MigrationAction {
    /** Reverse is best-effort and does not reconstruct original split intent. */
    def reverse: MigrationAction = Join(at, targetPaths, MigrationExpr.Identity)
  }

  final case class ChangeType(at: DynamicOptic, converter: MigrationExpr) extends MigrationAction {
    /** Reverse is best-effort unless converter is bijective. */
    def reverse: MigrationAction = ChangeType(at, MigrationExpr.Identity)
  }

  final case class RenameCase(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  final case class TransformCase(at: DynamicOptic, caseName: String, actions: Vector[MigrationAction]) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, caseName, actions.reverseIterator.map(_.reverse).toVector)
  }

  final case class TransformElements(at: DynamicOptic, transform: MigrationExpr) extends MigrationAction {
    /** Reverse is best-effort and cannot reconstruct non-invertible element transforms. */
    def reverse: MigrationAction = TransformElements(at, MigrationExpr.Identity)
  }

  final case class TransformKeys(at: DynamicOptic, transform: MigrationExpr) extends MigrationAction {
    /** Reverse is best-effort and cannot reconstruct key collisions or non-bijective maps. */
    def reverse: MigrationAction = TransformKeys(at, MigrationExpr.Identity)
  }

  final case class TransformValues(at: DynamicOptic, transform: MigrationExpr) extends MigrationAction {
    /** Reverse is best-effort and cannot reconstruct non-invertible value transforms. */
    def reverse: MigrationAction = TransformValues(at, MigrationExpr.Identity)
  }

  final case class NestedMigration(at: DynamicOptic, migration: DynamicMigration) extends MigrationAction {
    def reverse: MigrationAction = NestedMigration(at, migration.reverse)
  }

  private def record2[A](typeId: TypeId[A], f1: Reflect[Binding, ?], n1: String, f2: Reflect[Binding, ?], n2: String)(
    mk: (AnyRef, AnyRef) => A,
    unmk: A => (AnyRef, AnyRef)
  ): Schema[A] = new Schema(
    reflect = new Reflect.Record[Binding, A](
      fields = Chunk(f1.asTerm(n1), f2.asTerm(n2)),
      typeId = typeId,
      recordBinding = new Binding.Record(
        constructor = new Constructor[A] {
          def usedRegisters: RegisterOffset                     = RegisterOffset(objects = 2)
          def construct(in: Registers, offset: RegisterOffset): A =
            mk(in.getObject(offset), in.getObject(RegisterOffset.incrementObjects(offset)))
        },
        deconstructor = new Deconstructor[A] {
          def usedRegisters: RegisterOffset                          = RegisterOffset(objects = 2)
          def deconstruct(out: Registers, offset: RegisterOffset, in: A): Unit = {
            val (a1, a2) = unmk(in)
            out.setObject(offset, a1)
            out.setObject(RegisterOffset.incrementObjects(offset), a2)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val addFieldSchema: Schema[AddField] =
    record2(TypeId.of[AddField], Schema[DynamicOptic].reflect, "at", Schema[MigrationExpr].reflect, "default")(
      (a, b) => AddField(a.asInstanceOf[DynamicOptic], b.asInstanceOf[MigrationExpr]),
      in => (in.at, in.default)
    )

  implicit lazy val dropFieldSchema: Schema[DropField] =
    record2(TypeId.of[DropField], Schema[DynamicOptic].reflect, "at", Schema[MigrationExpr].reflect, "defaultForReverse")(
      (a, b) => DropField(a.asInstanceOf[DynamicOptic], b.asInstanceOf[MigrationExpr]),
      in => (in.at, in.defaultForReverse)
    )

  implicit lazy val renameSchema: Schema[Rename] =
    record2(TypeId.of[Rename], Schema[DynamicOptic].reflect, "at", Schema[String].reflect, "to")(
      (a, b) => Rename(a.asInstanceOf[DynamicOptic], b.asInstanceOf[String]),
      in => (in.at, in.to)
    )

  implicit lazy val transformValueSchema: Schema[TransformValue] =
    record2(TypeId.of[TransformValue], Schema[DynamicOptic].reflect, "at", Schema[MigrationExpr].reflect, "transform")(
      (a, b) => TransformValue(a.asInstanceOf[DynamicOptic], b.asInstanceOf[MigrationExpr]),
      in => (in.at, in.transform)
    )

  implicit lazy val mandateSchema: Schema[Mandate] =
    record2(TypeId.of[Mandate], Schema[DynamicOptic].reflect, "at", Schema[MigrationExpr].reflect, "default")(
      (a, b) => Mandate(a.asInstanceOf[DynamicOptic], b.asInstanceOf[MigrationExpr]),
      in => (in.at, in.default)
    )

  implicit lazy val optionalizeSchema: Schema[Optionalize] = new Schema(
    reflect = new Reflect.Record[Binding, Optionalize](
      fields = Chunk.single(Schema[DynamicOptic].reflect.asTerm("at")),
      typeId = TypeId.of[Optionalize],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Optionalize] {
          def usedRegisters: RegisterOffset                              = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): Optionalize =
            Optionalize(in.getObject(offset).asInstanceOf[DynamicOptic])
        },
        deconstructor = new Deconstructor[Optionalize] {
          def usedRegisters: RegisterOffset                                           = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Optionalize): Unit =
            out.setObject(offset, in.at)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val joinSchema: Schema[Join] = new Schema(
    reflect = new Reflect.Record[Binding, Join](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[Vector[DynamicOptic]].reflect.asTerm("sourcePaths"),
        Schema[MigrationExpr].reflect.asTerm("combiner")
      ),
      typeId = TypeId.of[Join],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Join] {
          def usedRegisters: RegisterOffset                        = RegisterOffset(objects = 3)
          def construct(in: Registers, offset: RegisterOffset): Join =
            Join(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[Vector[DynamicOptic]],
              in.getObject(RegisterOffset.incrementObjects(RegisterOffset.incrementObjects(offset))).asInstanceOf[MigrationExpr]
            )
        },
        deconstructor = new Deconstructor[Join] {
          def usedRegisters: RegisterOffset                                     = RegisterOffset(objects = 3)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Join): Unit = {
            out.setObject(offset, in.at)
            out.setObject(RegisterOffset.incrementObjects(offset), in.sourcePaths)
            out.setObject(RegisterOffset.incrementObjects(RegisterOffset.incrementObjects(offset)), in.combiner)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val splitSchema: Schema[Split] = new Schema(
    reflect = new Reflect.Record[Binding, Split](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[Vector[DynamicOptic]].reflect.asTerm("targetPaths"),
        Schema[MigrationExpr].reflect.asTerm("splitter")
      ),
      typeId = TypeId.of[Split],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Split] {
          def usedRegisters: RegisterOffset                         = RegisterOffset(objects = 3)
          def construct(in: Registers, offset: RegisterOffset): Split =
            Split(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[Vector[DynamicOptic]],
              in.getObject(RegisterOffset.incrementObjects(RegisterOffset.incrementObjects(offset))).asInstanceOf[MigrationExpr]
            )
        },
        deconstructor = new Deconstructor[Split] {
          def usedRegisters: RegisterOffset                                      = RegisterOffset(objects = 3)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Split): Unit = {
            out.setObject(offset, in.at)
            out.setObject(RegisterOffset.incrementObjects(offset), in.targetPaths)
            out.setObject(RegisterOffset.incrementObjects(RegisterOffset.incrementObjects(offset)), in.splitter)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val changeTypeSchema: Schema[ChangeType] =
    record2(TypeId.of[ChangeType], Schema[DynamicOptic].reflect, "at", Schema[MigrationExpr].reflect, "converter")(
      (a, b) => ChangeType(a.asInstanceOf[DynamicOptic], b.asInstanceOf[MigrationExpr]),
      in => (in.at, in.converter)
    )

  implicit lazy val renameCaseSchema: Schema[RenameCase] = new Schema(
    reflect = new Reflect.Record[Binding, RenameCase](
      fields = Chunk(Schema[DynamicOptic].reflect.asTerm("at"), Schema[String].reflect.asTerm("from"), Schema[String].reflect.asTerm("to")),
      typeId = TypeId.of[RenameCase],
      recordBinding = new Binding.Record(
        constructor = new Constructor[RenameCase] {
          def usedRegisters: RegisterOffset                              = RegisterOffset(objects = 3)
          def construct(in: Registers, offset: RegisterOffset): RenameCase =
            RenameCase(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[String],
              in.getObject(RegisterOffset.incrementObjects(RegisterOffset.incrementObjects(offset))).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[RenameCase] {
          def usedRegisters: RegisterOffset                                           = RegisterOffset(objects = 3)
          def deconstruct(out: Registers, offset: RegisterOffset, in: RenameCase): Unit = {
            out.setObject(offset, in.at)
            out.setObject(RegisterOffset.incrementObjects(offset), in.from)
            out.setObject(RegisterOffset.incrementObjects(RegisterOffset.incrementObjects(offset)), in.to)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val transformCaseSchema: Schema[TransformCase] = new Schema(
    reflect = new Reflect.Record[Binding, TransformCase](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("caseName"),
        Reflect.Deferred(() => Schema[Vector[MigrationAction]].reflect).asTerm("actions")
      ),
      typeId = TypeId.of[TransformCase],
      recordBinding = new Binding.Record(
        constructor = new Constructor[TransformCase] {
          def usedRegisters: RegisterOffset                                 = RegisterOffset(objects = 3)
          def construct(in: Registers, offset: RegisterOffset): TransformCase =
            TransformCase(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[String],
              in.getObject(RegisterOffset.incrementObjects(RegisterOffset.incrementObjects(offset))).asInstanceOf[Vector[MigrationAction]]
            )
        },
        deconstructor = new Deconstructor[TransformCase] {
          def usedRegisters: RegisterOffset                                              = RegisterOffset(objects = 3)
          def deconstruct(out: Registers, offset: RegisterOffset, in: TransformCase): Unit = {
            out.setObject(offset, in.at)
            out.setObject(RegisterOffset.incrementObjects(offset), in.caseName)
            out.setObject(RegisterOffset.incrementObjects(RegisterOffset.incrementObjects(offset)), in.actions)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val transformElementsSchema: Schema[TransformElements] =
    record2(TypeId.of[TransformElements], Schema[DynamicOptic].reflect, "at", Schema[MigrationExpr].reflect, "transform")(
      (a, b) => TransformElements(a.asInstanceOf[DynamicOptic], b.asInstanceOf[MigrationExpr]),
      in => (in.at, in.transform)
    )
  implicit lazy val transformKeysSchema: Schema[TransformKeys] =
    record2(TypeId.of[TransformKeys], Schema[DynamicOptic].reflect, "at", Schema[MigrationExpr].reflect, "transform")(
      (a, b) => TransformKeys(a.asInstanceOf[DynamicOptic], b.asInstanceOf[MigrationExpr]),
      in => (in.at, in.transform)
    )
  implicit lazy val transformValuesSchema: Schema[TransformValues] =
    record2(TypeId.of[TransformValues], Schema[DynamicOptic].reflect, "at", Schema[MigrationExpr].reflect, "transform")(
      (a, b) => TransformValues(a.asInstanceOf[DynamicOptic], b.asInstanceOf[MigrationExpr]),
      in => (in.at, in.transform)
    )

  implicit lazy val nestedMigrationSchema: Schema[NestedMigration] =
    record2(TypeId.of[NestedMigration], Schema[DynamicOptic].reflect, "at", Schema[DynamicMigration].reflect, "migration")(
      (a, b) => NestedMigration(a.asInstanceOf[DynamicOptic], b.asInstanceOf[DynamicMigration]),
      in => (in.at, in.migration)
    )

  implicit lazy val schema: Schema[MigrationAction] = new Schema(
    reflect = new Reflect.Variant[Binding, MigrationAction](
      cases = Chunk(
        addFieldSchema.reflect.asTerm("AddField"),
        dropFieldSchema.reflect.asTerm("DropField"),
        renameSchema.reflect.asTerm("Rename"),
        transformValueSchema.reflect.asTerm("TransformValue"),
        mandateSchema.reflect.asTerm("Mandate"),
        optionalizeSchema.reflect.asTerm("Optionalize"),
        joinSchema.reflect.asTerm("Join"),
        splitSchema.reflect.asTerm("Split"),
        changeTypeSchema.reflect.asTerm("ChangeType"),
        renameCaseSchema.reflect.asTerm("RenameCase"),
        Reflect.Deferred(() => transformCaseSchema.reflect).asTerm("TransformCase"),
        transformElementsSchema.reflect.asTerm("TransformElements"),
        transformKeysSchema.reflect.asTerm("TransformKeys"),
        transformValuesSchema.reflect.asTerm("TransformValues"),
        Reflect.Deferred(() => nestedMigrationSchema.reflect).asTerm("NestedMigration")
      ),
      typeId = TypeId.of[MigrationAction],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[MigrationAction] {
          def discriminate(a: MigrationAction): Int = a match {
            case _: AddField          => 0
            case _: DropField         => 1
            case _: Rename            => 2
            case _: TransformValue    => 3
            case _: Mandate           => 4
            case _: Optionalize       => 5
            case _: Join              => 6
            case _: Split             => 7
            case _: ChangeType        => 8
            case _: RenameCase        => 9
            case _: TransformCase     => 10
            case _: TransformElements => 11
            case _: TransformKeys     => 12
            case _: TransformValues   => 13
            case _: NestedMigration   => 14
          }
        },
        matchers = Matchers(
          new Matcher[AddField] { def downcastOrNull(a: Any): AddField = a match { case x: AddField => x; case _ => null.asInstanceOf[AddField] } },
          new Matcher[DropField] { def downcastOrNull(a: Any): DropField = a match { case x: DropField => x; case _ => null.asInstanceOf[DropField] } },
          new Matcher[Rename] { def downcastOrNull(a: Any): Rename = a match { case x: Rename => x; case _ => null.asInstanceOf[Rename] } },
          new Matcher[TransformValue] { def downcastOrNull(a: Any): TransformValue = a match { case x: TransformValue => x; case _ => null.asInstanceOf[TransformValue] } },
          new Matcher[Mandate] { def downcastOrNull(a: Any): Mandate = a match { case x: Mandate => x; case _ => null.asInstanceOf[Mandate] } },
          new Matcher[Optionalize] { def downcastOrNull(a: Any): Optionalize = a match { case x: Optionalize => x; case _ => null.asInstanceOf[Optionalize] } },
          new Matcher[Join] { def downcastOrNull(a: Any): Join = a match { case x: Join => x; case _ => null.asInstanceOf[Join] } },
          new Matcher[Split] { def downcastOrNull(a: Any): Split = a match { case x: Split => x; case _ => null.asInstanceOf[Split] } },
          new Matcher[ChangeType] { def downcastOrNull(a: Any): ChangeType = a match { case x: ChangeType => x; case _ => null.asInstanceOf[ChangeType] } },
          new Matcher[RenameCase] { def downcastOrNull(a: Any): RenameCase = a match { case x: RenameCase => x; case _ => null.asInstanceOf[RenameCase] } },
          new Matcher[TransformCase] { def downcastOrNull(a: Any): TransformCase = a match { case x: TransformCase => x; case _ => null.asInstanceOf[TransformCase] } },
          new Matcher[TransformElements] { def downcastOrNull(a: Any): TransformElements = a match { case x: TransformElements => x; case _ => null.asInstanceOf[TransformElements] } },
          new Matcher[TransformKeys] { def downcastOrNull(a: Any): TransformKeys = a match { case x: TransformKeys => x; case _ => null.asInstanceOf[TransformKeys] } },
          new Matcher[TransformValues] { def downcastOrNull(a: Any): TransformValues = a match { case x: TransformValues => x; case _ => null.asInstanceOf[TransformValues] } },
          new Matcher[NestedMigration] { def downcastOrNull(a: Any): NestedMigration = a match { case x: NestedMigration => x; case _ => null.asInstanceOf[NestedMigration] } }
        )
      ),
      modifiers = Chunk.empty
    )
  )
}
