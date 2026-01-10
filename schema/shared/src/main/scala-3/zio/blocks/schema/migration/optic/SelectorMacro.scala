package zio.blocks.schema.migration.optic

import scala.quoted.*
import zio.blocks.schema.migration.optic.{OpticStep, Selector}

object SelectorMacro {

  /**
   * Entry Point: Generates an implicit Selector[S, A] instance.
   * Matches the ZIO style of using 'inline given'.
   */
  inline given materialize[S, A](using inline f: S => A): Selector[S, A] = 
    ${ materializeImpl('f) }

  /**
   * The Macro Implementation.
   */
  private def materializeImpl[S: Type, A: Type](f: Expr[S => A])(using Quotes): Expr[Selector[S, A]] = {
    import quotes.reflect.*

    // --- 1. Helper Utilities ---
    
    def fail(msg: String): Nothing = 
      report.errorAndAbort(msg, Position.ofMacroExpansion)

    // A helper to lift OpticStep into Expr domain
    given ToExpr[OpticStep] with {
      def apply(x: OpticStep)(using Quotes): Expr[OpticStep] = x match {
        case OpticStep.Field(n) => '{ OpticStep.Field(${Expr(n)}) }
        case OpticStep.Index(i) => '{ OpticStep.Index(${Expr(i)}) }
        case OpticStep.Key(k)   => '{ OpticStep.Key(${Expr(k)}) }
      }
    }

    // --- 2. AST Traversal Logic ---

    def extractPath(term: Term, accum: List[OpticStep]): List[OpticStep] = {
      term match {
        // Standard inlining wrapper removal
        case Inlined(_, _, body) => 
          extractPath(body, accum)
        
        // Handling blocks
        case Block(List(), expr) => 
          extractPath(expr, accum)
        
        // Handling type ascriptions
        case Typed(expr, _) => 
          extractPath(expr, accum)
        
        // Base case: Lambda (x => ...)
        case Lambda(_, body) => 
          extractPath(body, accum)

        // Case: Field Access (_.address)
        case Select(qualifier, name) =>
          val step = OpticStep.Field(name)
          extractPath(qualifier, step :: accum)

        // Case: Structural Type (_.selectDynamic("field"))
        case Apply(Select(qualifier, "selectDynamic"), List(Literal(StringConstant(name)))) =>
          val step = OpticStep.Field(name)
          extractPath(qualifier, step :: accum)

        // Case: Collection/Map Access
        case Apply(Select(qualifier, "apply"), args) =>
          args match {
            case List(Literal(IntConstant(idx))) =>
               extractPath(qualifier, OpticStep.Index(idx) :: accum)
            case List(Literal(StringConstant(key))) =>
               extractPath(qualifier, OpticStep.Key(key) :: accum)
            case _ =>
               fail(s"Unsupported arguments in selector. Expected literal Int or String.")
          }

        // Case: Identity (x)
        case Ident(_) => 
          accum

        case other =>
          fail(s"Invalid selector syntax: ${other.show}. Supported: _.field, .selectDynamic, .apply")
      }
    }

    // --- 3. Execution & Code Generation ---

    val steps = extractPath(f.asTerm, List.empty)
    
    // Lift the list of steps into an Expr[Vector[OpticStep]]
    val vectorExpr = Expr.ofSeq(steps.map(s => Expr(s)))

    // FIX: Use Fully Qualified Names (_root_.zio...) to avoid Ambiguity Error
    // FIX: Explicitly implement the trait to avoid Type Mismatch Error
    '{
      new _root_.zio.blocks.schema.migration.optic.Selector[S, A] {
        override def path: _root_.zio.blocks.schema.migration.optic.DynamicOptic = 
          _root_.zio.blocks.schema.migration.optic.DynamicOptic($vectorExpr.toVector)
      }
    }
  }
}