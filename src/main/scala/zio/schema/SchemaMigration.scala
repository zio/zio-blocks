import scala.util.Try
import scala.quoted._
import zio.schema.{DynamicValue, StandardType, Schema, TypeId}
import zio.Chunk

// 1. Enterprise Core: Context Preservation for Rollbacks
final case class MigrationContext(data: Map[String, DynamicValue], backup: Map[String, DynamicValue] = Map.empty)

sealed trait Migration[In, Out] {
  def invert: Migration[Out, In]
  def applyStep(ctx: MigrationContext): Either[String, MigrationContext]
}

object Migration {
  final case class Morph[In, Out](key: String, f: DynamicValue => DynamicValue, g: DynamicValue => DynamicValue, fromClass: String, toClass: String) extends Migration[In, Out] {
    override def invert: Migration[Out, In] = Morph(key, g, f, toClass, fromClass)
    override def applyStep(ctx: MigrationContext): Either[String, MigrationContext] =
      ctx.data.get(key) match {
        case Some(v) => Try(f(v)).toEither.map(res => ctx.copy(data = ctx.data + (key -> res))).left.map(e => s"[$fromClass -> $toClass] Morph failed for '$key': ${e.getMessage}")
        case None    => Left(s"[$fromClass -> $toClass] Field '$key' missing during Morph")
      }
  }

  final case class Rename[In, Out](from: String, to: String, fromClass: String, toClass: String) extends Migration[In, Out] {
    override def invert: Migration[Out, In] = Rename(to, from, toClass, fromClass)
    override def applyStep(ctx: MigrationContext): Either[String, MigrationContext] =
      ctx.data.get(from).map(v => ctx.copy(data = ctx.data - from + (to -> v))).toRight(s"[$fromClass -> $toClass] Field '$from' missing during Rename")
  }

  final case class DeleteField[In, Out](key: String, defaultValue: DynamicValue) extends Migration[In, Out] {
    override def invert: Migration[Out, In] = AddField(key, defaultValue)
    override def applyStep(ctx: MigrationContext): Either[String, MigrationContext] =
      ctx.data.get(key).map(v => ctx.copy(data = ctx.data - key, backup = ctx.backup + (key -> v))).map(Right(_)).getOrElse(Right(ctx))
  }

  final case class AddField[In, Out](key: String, defaultValue: DynamicValue) extends Migration[In, Out] {
    override def invert: Migration[Out, In] = DeleteField(key, defaultValue)
    override def applyStep(ctx: MigrationContext): Either[String, MigrationContext] =
      val finalValue = ctx.backup.getOrElse(key, defaultValue)
      Right(ctx.copy(data = ctx.data + (key -> finalValue), backup = ctx.backup - key))
  }
}

// 2. The Final Macro Implementation (Ghost v9.0)
object GhostMigrationDeriver {
  inline def derive[A, B]: MigrationPlan[A, B] = ${ deriveImpl[A, B] }

  def deriveImpl[A: Type, B: Type](using Quotes): Expr[MigrationPlan[A, B]] = {
    import quotes.reflect._

    val aType = TypeRepr.of[A]
    val bType = TypeRepr.of[B]
    val aShow = aType.show
    val bShow = bType.show
    
    def getZeroValue(tpe: TypeRepr, depth: Int = 0): Expr[DynamicValue] = {
      if (depth > 5) '{ DynamicValue.None } 
      else if (tpe =:= TypeRepr.of[String]) '{ DynamicValue.Primitive("", StandardType.StringType) }
      else if (tpe =:= TypeRepr.of[Int]) '{ DynamicValue.Primitive(0, StandardType.IntType) }
      else if (tpe.typeSymbol.flags.is(Flags.Case)) {
        val fields = tpe.typeSymbol.caseFields
        val mapExpr = Expr.ofList(fields.map(f => '{ ${Expr(f.name)} -> ${getZeroValue(tpe.memberType(f), depth + 1)} }))
        '{ DynamicValue.Record(TypeId.parse(${Expr(tpe.show)}), Map.from($mapExpr)) }
      } else '{ DynamicValue.None }
    }

    val fromFields = aType.typeSymbol.caseFields.map(s => s.name -> aType.memberType(s)).toMap
    val toFields = bType.typeSymbol.caseFields.map(s => s.name -> bType.memberType(s)).toMap

    val steps = List.newBuilder[Expr[Migration[A, B]]]
    val matchedFrom = scala.collection.mutable.Set[String]()
    val matchedTo = scala.collection.mutable.Set[String]()

    // Sorted priority mapping to avoid collisions
    for { fK <- fromFields.keySet.toList.sorted; tK <- toFields.keySet.toList.sorted } {
      if (!matchedFrom.contains(fK) && !matchedTo.contains(tK) && (fK == tK || fK.contains(tK) || tK.contains(fK))) {
        matchedFrom += fK; matchedTo += tK
        if (fK != tK) steps += '{ Migration.Rename[A, B](${Expr(fK)}, ${Expr(tK)}, ${Expr(aShow)}, ${Expr(bShow)}) }
        
        val fT = fromFields(fK); val tT = toFields(tK)
        if (!(fT =:= tT)) {
          val fwd = if (fT =:= TypeRepr.of[Int] && tT =:= TypeRepr.of[String])
              '{ (v: DynamicValue) => v match { case DynamicValue.Primitive(i: Int, _) => DynamicValue.Primitive(i.toString, StandardType.StringType); case _ => v } }
            else '{ (v: DynamicValue) => v }

          val bwd = if (fT =:= TypeRepr.of[Int] && tT =:= TypeRepr.of[String])
              '{ (v: DynamicValue) => v match { case DynamicValue.Primitive(s: String, _) => DynamicValue.Primitive(Try(s.toInt).getOrElse(0), StandardType.IntType); case _ => v } }
            else '{ (v: DynamicValue) => v }

          steps += '{ Migration.Morph[A, B](${Expr(tK)}, $fwd, $bwd, ${Expr(aShow)}, ${Expr(bShow)}) }
        }
      }
    }

    (toFields.keySet -- matchedTo).foreach(k => steps += '{ Migration.AddField[A, B](${Expr(k)}, ${getZeroValue(toFields(k))}) })
    (fromFields.keySet -- matchedFrom).foreach(k => steps += '{ Migration.DeleteField[A, B](${Expr(k)}, DynamicValue.None) })

    '{ MigrationPlan[A, B](Chunk.fromIterable(${Expr.ofList(steps.result())})) }
  }
}

// 3. Reversible Migration Plan
final case class MigrationPlan[In, Out](steps: Chunk[Migration[In, Out]]) {
  def run(data: Map[String, DynamicValue]): Either[String, MigrationContext] =
    steps.foldLeft[Either[String, MigrationContext]](Right(MigrationContext(data)))((acc, s) => acc.flatMap(s.applyStep))
  
  def invert: MigrationPlan[Out, In] = MigrationPlan(steps.reverse.map(_.invert))
}
