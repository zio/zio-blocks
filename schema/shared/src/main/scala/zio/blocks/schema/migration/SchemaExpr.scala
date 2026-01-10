package zio.blocks.schema.migration

import zio.schema.DynamicValue
import zio.blocks.schema.migration.optic.DynamicOptic

sealed trait SchemaExpr[A, +B] extends Serializable { self =>

  def eval(input: DynamicValue): Either[OpticCheck, Seq[B]]
  def evalDynamic(input: DynamicValue): Either[OpticCheck, Seq[DynamicValue]]

  final def &&[B2](that: SchemaExpr[A, B2])(implicit ev: B <:< Boolean, ev2: B2 =:= Boolean): SchemaExpr[A, Boolean] =
    SchemaExpr.Logical(self.asInstanceOf[SchemaExpr[A, Boolean]], that.asInstanceOf[SchemaExpr[A, Boolean]], SchemaExpr.LogicalOperator.And)

  final def ||[B2](that: SchemaExpr[A, B2])(implicit ev: B <:< Boolean, ev2: B2 =:= Boolean): SchemaExpr[A, Boolean] =
    SchemaExpr.Logical(self.asInstanceOf[SchemaExpr[A, Boolean]], that.asInstanceOf[SchemaExpr[A, Boolean]], SchemaExpr.LogicalOperator.Or)
}

object SchemaExpr {

  final case class Literal[S, A](value: A, dynamicValue: DynamicValue) extends SchemaExpr[S, A] {
    def eval(input: DynamicValue): Either[OpticCheck, Seq[A]] = Right(Seq(value))
    def evalDynamic(input: DynamicValue): Either[OpticCheck, Seq[DynamicValue]] = Right(Seq(dynamicValue))
  }
  
  def ConstantValue[S](dv: DynamicValue): Literal[S, Any] = Literal(dv, dv)

  final case class Access[A, B](path: DynamicOptic) extends SchemaExpr[A, B] {
    private def traverse(input: DynamicValue): Either[OpticCheck, DynamicValue] = {
      path.steps.foldLeft[Either[OpticCheck, DynamicValue]](Right(input)) {
        case (Right(DynamicValue.Record(values)), step) =>
          step match {
            case optic.OpticStep.Field(name) =>
              values.get(name).toRight(OpticCheck(s"Field $name not found"))
            case _ => Left(OpticCheck("Invalid step for record"))
          }
        case (Right(_), _) => Left(OpticCheck("Path traversal failed: Not a record"))
        case (Left(e), _) => Left(e)
      }
    }

    def eval(input: DynamicValue): Either[OpticCheck, Seq[B]] = 
      traverse(input).map { dv => Seq(dv.asInstanceOf[DynamicValue.Primitive[B]].value) }

    def evalDynamic(input: DynamicValue): Either[OpticCheck, Seq[DynamicValue]] = 
      traverse(input).map(Seq(_))
  }

  sealed trait RelationalOperator
  object RelationalOperator {
    case object LessThan            extends RelationalOperator
    case object GreaterThan         extends RelationalOperator
    case object LessThanOrEqual     extends RelationalOperator
    case object GreaterThanOrEqual  extends RelationalOperator
    case object Equal               extends RelationalOperator
    case object NotEqual            extends RelationalOperator
  }

  final case class Relational[A, B](left: SchemaExpr[A, B], right: SchemaExpr[A, B], operator: RelationalOperator) extends SchemaExpr[A, Boolean] {
    def eval(input: DynamicValue): Either[OpticCheck, Seq[Boolean]] = Right(Seq(false)) 
    def evalDynamic(input: DynamicValue): Either[OpticCheck, Seq[DynamicValue]] = Right(Seq(DynamicValue.Primitive(false)))
  }

  sealed trait LogicalOperator
  object LogicalOperator {
    case object And extends LogicalOperator
    case object Or  extends LogicalOperator
  }

  final case class Logical[A](left: SchemaExpr[A, Boolean], right: SchemaExpr[A, Boolean], operator: LogicalOperator) extends SchemaExpr[A, Boolean] {
    def eval(input: DynamicValue): Either[OpticCheck, Seq[Boolean]] = Right(Seq(false))
    def evalDynamic(input: DynamicValue): Either[OpticCheck, Seq[DynamicValue]] = Right(Seq(DynamicValue.Primitive(false)))
  }

  sealed trait ArithmeticOperator
  object ArithmeticOperator {
    case object Add      extends ArithmeticOperator
    case object Subtract extends ArithmeticOperator
    case object Multiply extends ArithmeticOperator
  }

  final case class Arithmetic[S, A](left: SchemaExpr[S, A], right: SchemaExpr[S, A], operator: ArithmeticOperator, isNumeric: IsNumeric[A]) extends SchemaExpr[S, A] {
    def eval(input: DynamicValue): Either[OpticCheck, Seq[A]] = Left(OpticCheck("Not impl"))
    def evalDynamic(input: DynamicValue): Either[OpticCheck, Seq[DynamicValue]] = Left(OpticCheck("Not impl"))
  }

  final case class DefaultValue[S, A]() extends SchemaExpr[S, A] {
     def eval(input: DynamicValue): Either[OpticCheck, Seq[A]] = Left(OpticCheck("DefaultValue cannot be evaluated directly"))
     def evalDynamic(input: DynamicValue): Either[OpticCheck, Seq[DynamicValue]] = Left(OpticCheck("DefaultValue cannot be evaluated directly"))
  }
}