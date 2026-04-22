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

package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.TypeId

/**
 * A {{SchemaExpr}} is an expression on the value of a type fully described by a
 * {{Schema}}.
 *
 * {{SchemaExpr}} are used for persistence DSLs, implemented in third-party
 * libraries, as well as for validation, implemented in this library. In
 * addition, {{SchemaExpr}} could be used for data migration.
 */
sealed trait SchemaExpr[A, +B] { self =>

  /**
   * Evaluate the expression on the input value.
   *
   * @param input
   *   the input value
   *
   * @return
   *   the result of the expression
   */
  def eval(input: A): Either[OpticCheck, Seq[B]]

  /**
   * Evaluate the expression on the input value.
   *
   * @param input
   *   the input value
   *
   * @return
   *   the result of the expression, converted to {{DynamicValue}} values.
   */
  def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]]

  final def &&[B2](that: SchemaExpr[A, B2])(implicit ev: B <:< Boolean, ev2: B2 =:= Boolean): SchemaExpr[A, Boolean] =
    new SchemaExpr.Logical(self.asEquivalent[Boolean], that.asEquivalent[Boolean], SchemaExpr.LogicalOperator.And)

  final def ||[B2](that: SchemaExpr[A, B2])(implicit ev: B <:< Boolean, ev2: B2 =:= Boolean): SchemaExpr[A, Boolean] =
    new SchemaExpr.Logical(self.asEquivalent[Boolean], that.asEquivalent[Boolean], SchemaExpr.LogicalOperator.Or)

  private final def asEquivalent[B2](implicit ev: B <:< B2): SchemaExpr[A, B2] = {
    val _ = ev // suppress unused warning
    self.asInstanceOf[SchemaExpr[A, B2]]
  }
}

object SchemaExpr {
  final case class Literal[S, A](value: A, schema: Schema[A]) extends SchemaExpr[S, A] {
    def eval(input: S): Either[OpticCheck, Seq[A]] = result

    def evalDynamic(input: S): Either[OpticCheck, Seq[DynamicValue]] = dynamicResult

    private[this] val result        = new Right(Chunk.single(value))
    private[this] val dynamicResult = new Right(Chunk.single(schema.toDynamicValue(value)))
  }

  final case class Optic[A, B](optic: zio.blocks.schema.Optic[A, B]) extends SchemaExpr[A, B] {
    def eval(input: A): Either[OpticCheck, Seq[B]] = optic match {
      case l: Lens[?, ?] =>
        new Right(Chunk.single(l.get(input)))
      case p: Prism[?, ?] =>
        p.getOrFail(input) match {
          case Right(x: B @scala.unchecked) => new Right(Chunk.single(x))
          case left                         => left.asInstanceOf[Either[OpticCheck, Seq[B]]]
        }
      case o: Optional[?, ?] =>
        o.getOrFail(input) match {
          case Right(x) => new Right(Chunk.single(x))
          case left     => left.asInstanceOf[Either[OpticCheck, Seq[B]]]
        }
      case t: Traversal[?, ?] =>
        val sb = Seq.newBuilder[B]
        t.fold[Unit](input)((), (_, a) => sb.addOne(a))
        val r = sb.result()
        if (r.isEmpty) new Left(t.check(input).get)
        else new Right(r)
    }

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] = optic match {
      case l: Lens[?, ?] =>
        new Right(Chunk.single(toDynamicValue(l.get(input))))
      case p: Prism[?, ?] =>
        p.getOrFail(input) match {
          case Right(x: B @scala.unchecked) => new Right(Chunk.single(toDynamicValue(x)))
          case left                         => left.asInstanceOf[Either[OpticCheck, Seq[DynamicValue]]]
        }
      case o: Optional[?, ?] =>
        o.getOrFail(input) match {
          case Right(x) => new Right(Chunk.single(toDynamicValue(x)))
          case left     => left.asInstanceOf[Either[OpticCheck, Seq[DynamicValue]]]
        }
      case t: Traversal[?, ?] =>
        val sb = Seq.newBuilder[DynamicValue]
        t.fold[Unit](input)((), (_, a) => sb.addOne(toDynamicValue(a)))
        val r = sb.result()
        if (r.isEmpty) new Left(t.check(input).get)
        else new Right(r)
    }

    private[this] val toDynamicValue: B => DynamicValue = optic.focus.toDynamicValue
  }

  sealed trait UnaryOp[A, +B] extends SchemaExpr[A, B] {
    def expr: SchemaExpr[A, B]
  }

  sealed trait BinaryOp[A, +B, +C] extends SchemaExpr[A, C] {
    def left: SchemaExpr[A, B]

    def right: SchemaExpr[A, B]
  }

  final case class Relational[A, B](left: SchemaExpr[A, B], right: SchemaExpr[A, B], operator: RelationalOperator)
      extends BinaryOp[A, B, Boolean] {
    def eval(input: A): Either[OpticCheck, Seq[Boolean]] =
      if ((operator eq RelationalOperator.Equal) || (operator eq RelationalOperator.NotEqual)) {
        for {
          xs <- left.eval(input)
          ys <- right.eval(input)
        } yield {
          if (operator eq RelationalOperator.Equal) for { x <- xs; y <- ys } yield x == y
          else for { x <- xs; y <- ys } yield x != y
        }
      } else { // FIXME: Use Ordering to avoid converisons to dynamic values
        for {
          xs <- left.evalDynamic(input)
          ys <- right.evalDynamic(input)
        } yield {
          if (operator eq RelationalOperator.LessThan) for { x <- xs; y <- ys } yield x < y
          else if (operator eq RelationalOperator.LessThanOrEqual) for { x <- xs; y <- ys } yield x <= y
          else if (operator eq RelationalOperator.GreaterThan) for { x <- xs; y <- ys } yield x > y
          else for { x <- xs; y <- ys } yield x >= y
        }
      }

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- left.evalDynamic(input)
        ys <- right.evalDynamic(input)
      } yield operator match {
        case _: RelationalOperator.LessThan.type           => for { x <- xs; y <- ys } yield toDynamicValue(x < y)
        case _: RelationalOperator.LessThanOrEqual.type    => for { x <- xs; y <- ys } yield toDynamicValue(x <= y)
        case _: RelationalOperator.GreaterThan.type        => for { x <- xs; y <- ys } yield toDynamicValue(x > y)
        case _: RelationalOperator.GreaterThanOrEqual.type => for { x <- xs; y <- ys } yield toDynamicValue(x >= y)
        case _: RelationalOperator.Equal.type              => for { x <- xs; y <- ys } yield toDynamicValue(x == y)
        case _: RelationalOperator.NotEqual.type           => for { x <- xs; y <- ys } yield toDynamicValue(x != y)
      }

    private[this] def toDynamicValue(value: Boolean): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Boolean(value))
  }

  sealed trait RelationalOperator

  object RelationalOperator {
    case object LessThan           extends RelationalOperator
    case object GreaterThan        extends RelationalOperator
    case object LessThanOrEqual    extends RelationalOperator
    case object GreaterThanOrEqual extends RelationalOperator
    case object Equal              extends RelationalOperator
    case object NotEqual           extends RelationalOperator
  }

  final case class Logical[A](left: SchemaExpr[A, Boolean], right: SchemaExpr[A, Boolean], operator: LogicalOperator)
      extends BinaryOp[A, Boolean, Boolean] {
    def eval(input: A): Either[OpticCheck, Seq[Boolean]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield operator match {
        case _: LogicalOperator.And.type => for { x <- xs; y <- ys } yield x && y
        case _: LogicalOperator.Or.type  => for { x <- xs; y <- ys } yield x || y
      }

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield operator match {
        case _: LogicalOperator.And.type => for { x <- xs; y <- ys } yield toDynamicValue(x && y)
        case _: LogicalOperator.Or.type  => for { x <- xs; y <- ys } yield toDynamicValue(x || y)
      }

    private[this] def toDynamicValue(value: Boolean): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Boolean(value))
  }

  sealed trait LogicalOperator

  object LogicalOperator {
    case object And extends LogicalOperator
    case object Or  extends LogicalOperator
  }

  final case class Not[A](expr: SchemaExpr[A, Boolean]) extends UnaryOp[A, Boolean] {
    def eval(input: A): Either[OpticCheck, Seq[Boolean]] =
      for {
        xs <- expr.eval(input)
      } yield xs.map(!_)

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- expr.eval(input)
      } yield xs.map(x => toDynamicValue(!x))

    private[this] def toDynamicValue(value: Boolean): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Boolean(value))
  }

  final case class Arithmetic[S, A](
    left: SchemaExpr[S, A],
    right: SchemaExpr[S, A],
    operator: ArithmeticOperator,
    isNumeric: IsNumeric[A]
  ) extends BinaryOp[S, A, A] {
    def eval(input: S): Either[OpticCheck, Seq[A]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield {
        val n = isNumeric.numeric
        operator match {
          case _: ArithmeticOperator.Add.type      => for { x <- xs; y <- ys } yield n.plus(x, y)
          case _: ArithmeticOperator.Subtract.type => for { x <- xs; y <- ys } yield n.minus(x, y)
          case _: ArithmeticOperator.Multiply.type => for { x <- xs; y <- ys } yield n.times(x, y)
        }
      }

    def evalDynamic(input: S): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield {
        val n = isNumeric.numeric
        operator match {
          case _: ArithmeticOperator.Add.type      => for { x <- xs; y <- ys } yield toDynamicValue(n.plus(x, y))
          case _: ArithmeticOperator.Subtract.type => for { x <- xs; y <- ys } yield toDynamicValue(n.minus(x, y))
          case _: ArithmeticOperator.Multiply.type => for { x <- xs; y <- ys } yield toDynamicValue(n.times(x, y))
        }
      }

    private[this] val toDynamicValue: A => DynamicValue = isNumeric.primitiveType.toDynamicValue
  }

  sealed trait ArithmeticOperator

  object ArithmeticOperator {
    case object Add      extends ArithmeticOperator
    case object Subtract extends ArithmeticOperator
    case object Multiply extends ArithmeticOperator
  }

  final case class StringConcat[A](left: SchemaExpr[A, String], right: SchemaExpr[A, String])
      extends BinaryOp[A, String, String] {
    def eval(input: A): Either[OpticCheck, Seq[String]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield for { x <- xs; y <- ys } yield x + y

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield for { x <- xs; y <- ys } yield toDynamicValue(x + y)

    private[this] def toDynamicValue(value: String): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.String(value))
  }

  final case class StringRegexMatch[A](regex: SchemaExpr[A, String], string: SchemaExpr[A, String])
      extends SchemaExpr[A, Boolean] {
    def eval(input: A): Either[OpticCheck, Seq[Boolean]] =
      for {
        xs <- regex.eval(input)
        ys <- string.eval(input)
      } yield for { x <- xs; y <- ys } yield y.matches(x)

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- regex.eval(input)
        ys <- string.eval(input)
      } yield for { x <- xs; y <- ys } yield toDynamicValue(y.matches(x))

    private[this] def toDynamicValue(value: Boolean): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Boolean(value))
  }

  final case class StringLength[A](string: SchemaExpr[A, String]) extends SchemaExpr[A, Int] {
    def eval(input: A): Either[OpticCheck, Seq[Int]] =
      for {
        xs <- string.eval(input)
      } yield xs.map(_.length)

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- string.eval(input)
      } yield xs.map(x => toDynamicValue(x.length))

    private[this] def toDynamicValue(value: Int): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Int(value))
  }

  final case class DefaultValue[S](fieldPath: DynamicOptic, targetSchemaRepr: SchemaRepr)
      extends SchemaExpr[S, Nothing] {
    def eval(input: S): Either[OpticCheck, Seq[Nothing]] = error

    def evalDynamic(input: S): Either[OpticCheck, Seq[DynamicValue]] = error

    private[this] val error: Left[OpticCheck, Nothing] =
      new Left(
        new OpticCheck(
          new ::(
            new OpticCheck.WrappingError(
              fieldPath,
              fieldPath,
              SchemaError("DefaultValue is not directly evaluable; interpreted by migration system")
            ),
            Nil
          )
        )
      )
  }

  // --- Migration-used SchemaExpr subset bridge ------------------------------
  //
  // Manual Schema instances for the three SchemaExpr constructors actually
  // stored by migration code: DefaultValue, Literal, StringConcat.
  // These unblock `Schema.derived[MigrationAction]` by providing an implicit
  // `Schema[SchemaExpr[_, _]]` that the derivation macro's `Implicits.search`
  // picks up instead of recursing into the full expression algebra (which
  // would fail on BinaryOp's existential B type parameter and on unrelated
  // arithmetic / logical / relational / regex families).
  //
  // Scope: the derivation-compile gate. Round-trip fidelity for
  // Literal's `schema: Schema[A]` field is intentionally lossy — construct
  // reproduces `Literal[Any, DynamicValue](dv, Schema[DynamicValue])` because
  // recovering the original `A` requires repo-wide expression-algebra cleanup
  // that is explicitly out of scope (deferred).
  // Later waves validate the migration round-trip surface concretely.
  //
  // Non-migration SchemaExpr constructors (Arithmetic, Logical, Relational,
  // Not, StringRegexMatch, StringLength, Optic) are NOT covered by this
  // bridge; they do not appear in any stored MigrationAction payload.

  implicit lazy val defaultValueSchema: Schema[DefaultValue[Any]] = new Schema(
    reflect = new Reflect.Record[Binding, DefaultValue[Any]](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("fieldPath"),
        Schema[SchemaRepr].reflect.asTerm("targetSchemaRepr")
      ),
      typeId = TypeId.of[DefaultValue[Any]],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DefaultValue[Any]] {
          def usedRegisters: RegisterOffset                                    = 2
          def construct(in: Registers, offset: RegisterOffset): DefaultValue[Any] =
            new DefaultValue[Any](
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[SchemaRepr]
            )
        },
        deconstructor = new Deconstructor[DefaultValue[Any]] {
          def usedRegisters: RegisterOffset                                                 = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: DefaultValue[Any]): Unit = {
            out.setObject(offset + 0, in.fieldPath)
            out.setObject(offset + 1, in.targetSchemaRepr)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val literalSchema: Schema[Literal[Any, Any]] = new Schema(
    reflect = new Reflect.Record[Binding, Literal[Any, Any]](
      fields = Chunk(
        Schema[DynamicValue].reflect.asTerm("value"),
        Schema[SchemaRepr].reflect.asTerm("schemaRepr")
      ),
      typeId = TypeId.of[Literal[Any, Any]],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Literal[Any, Any]] {
          def usedRegisters: RegisterOffset                                  = 2
          def construct(in: Registers, offset: RegisterOffset): Literal[Any, Any] = {
            val dv = in.getObject(offset + 0).asInstanceOf[DynamicValue]
            // schemaRepr is preserved for consumers that introspect the
            // original stored shape; the reconstructed Literal carries the
            // DynamicValue itself (Schema[DynamicValue]) because recovering
            // the original `A` requires repo-wide SchemaExpr redesign
            // (deferred).
            new Literal[Any, Any](
              dv.asInstanceOf[Any],
              Schema[DynamicValue].asInstanceOf[Schema[Any]]
            )
          }
        },
        deconstructor = new Deconstructor[Literal[Any, Any]] {
          def usedRegisters: RegisterOffset                                              = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Literal[Any, Any]): Unit = {
            // Project the typed `value` through its carried `schema` into a
            // serializable DynamicValue, and pair it with a structural repr
            // placeholder (Wildcard) — this bridge only guarantees the
            // derivation-compile gate; richer SchemaRepr projection is
            // downstream work.
            out.setObject(offset + 0, in.schema.toDynamicValue(in.value))
            out.setObject(offset + 1, SchemaRepr.Wildcard)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val stringConcatSchema: Schema[StringConcat[Any]] = new Schema(
    reflect = new Reflect.Record[Binding, StringConcat[Any]](
      fields = Chunk(
        Reflect.Deferred(() => migrationSchema.reflect).asTerm("left"),
        Reflect.Deferred(() => migrationSchema.reflect).asTerm("right")
      ),
      typeId = TypeId.of[StringConcat[Any]],
      recordBinding = new Binding.Record(
        constructor = new Constructor[StringConcat[Any]] {
          def usedRegisters: RegisterOffset                                 = 2
          def construct(in: Registers, offset: RegisterOffset): StringConcat[Any] =
            new StringConcat[Any](
              in.getObject(offset + 0).asInstanceOf[SchemaExpr[Any, String]],
              in.getObject(offset + 1).asInstanceOf[SchemaExpr[Any, String]]
            )
        },
        deconstructor = new Deconstructor[StringConcat[Any]] {
          def usedRegisters: RegisterOffset                                             = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: StringConcat[Any]): Unit = {
            out.setObject(offset + 0, in.left)
            out.setObject(offset + 1, in.right)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * Schema for the migration-used SchemaExpr subset: DefaultValue, Literal,
   * StringConcat. Used by `Schema.derived[MigrationAction]` via implicit
   * resolution inside the derivation macro.
   */
  implicit val migrationSchema: Schema[SchemaExpr[_, _]] = new Schema(
    reflect = new Reflect.Variant[Binding, SchemaExpr[_, _]](
      cases = Chunk(
        defaultValueSchema.reflect.asTerm("DefaultValue"),
        literalSchema.reflect.asTerm("Literal"),
        stringConcatSchema.reflect.asTerm("StringConcat")
      ),
      typeId = TypeId.of[SchemaExpr[_, _]],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[SchemaExpr[_, _]] {
          def discriminate(a: SchemaExpr[_, _]): Int = a match {
            case _: DefaultValue[_]   => 0
            case _: Literal[_, _]     => 1
            case _: StringConcat[_]   => 2
            case other                =>
              throw new IllegalArgumentException(
                s"SchemaExpr constructor not supported by the migration-used subset: ${other.getClass.getName}"
              )
          }
        },
        matchers = Matchers(
          new Matcher[DefaultValue[Any]] {
            def downcastOrNull(a: Any): DefaultValue[Any] = a match {
              case x: DefaultValue[_] => x.asInstanceOf[DefaultValue[Any]]
              case _                  => null.asInstanceOf[DefaultValue[Any]]
            }
          },
          new Matcher[Literal[Any, Any]] {
            def downcastOrNull(a: Any): Literal[Any, Any] = a match {
              case x: Literal[_, _] => x.asInstanceOf[Literal[Any, Any]]
              case _                => null.asInstanceOf[Literal[Any, Any]]
            }
          },
          new Matcher[StringConcat[Any]] {
            def downcastOrNull(a: Any): StringConcat[Any] = a match {
              case x: StringConcat[_] => x.asInstanceOf[StringConcat[Any]]
              case _                  => null.asInstanceOf[StringConcat[Any]]
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )
}
