package zio.blocks.schema.migration.optic

import scala.quoted.*

object SelectorMacro {

  inline def translate[S, A](inline selector: S => A): DynamicOptic = 
    ${ translateImpl('selector) }

  private def translateImpl[S: Type, A: Type](selector: Expr[S => A])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect.*

    // --- ১. ম্যাক্রো হেল্পার: ToExpr ইমপ্লিমেন্টেশন ---
    given ToExpr[OpticStep] with {
      def apply(x: OpticStep)(using Quotes): Expr[OpticStep] = x match {
        case OpticStep.Field(n) => '{ OpticStep.Field(${Expr(n)}) }
        case OpticStep.Index(i) => '{ OpticStep.Index(${Expr(i)}) }
        case OpticStep.Key(k)   => '{ OpticStep.Key(${Expr(k)}) }
      }
    }

    given ToExpr[DynamicOptic] with {
      def apply(x: DynamicOptic)(using Quotes): Expr[DynamicOptic] = {
        val stepsExprs: Seq[Expr[OpticStep]] = x.steps.map(step => Expr(step))
        val seqExpr: Expr[Seq[OpticStep]] = Expr.ofSeq(stepsExprs)
        '{ DynamicOptic($seqExpr.toVector) }
      }
    }

    // --- ২. মূল লজিক (Updated Pattern Matching) ---
    def extractPath(term: Term, accum: List[OpticStep]): List[OpticStep] = {
      term match {
        case Inlined(_, _, body) => extractPath(body, accum)
        case Block(List(), expr) => extractPath(expr, accum)
        case Typed(expr, _)      => extractPath(expr, accum)
        case Lambda(_, body)     => extractPath(body, accum)

        // --- FIX 1: Handle asInstanceOf (Both standard and internal $) ---
        case TypeApply(Select(qualifier, name), _) if name == "asInstanceOf" || name == "$asInstanceOf$" => 
          extractPath(qualifier, accum)

        // --- FIX 2: Handle reflectiveSelectable (CRITICAL FIX FOR YOUR ERROR) ---
        // আপনার এরর লগে 'Ident(reflectiveSelectable)' এসেছিল, তাই এই লাইনটি সেটা ফিক্স করবে।
        case Apply(Ident("reflectiveSelectable"), List(innerTerm)) => 
           extractPath(innerTerm, accum)

        // ব্যাকআপ হিসেবে রাখা হলো (যদি Select হিসেবে আসে)
        case Apply(Select(_, "reflectiveSelectable"), List(innerTerm)) => 
           extractPath(innerTerm, accum)

        // Standard Field Access
        case Select(qualifier, name) =>
          val step = OpticStep.Field(name)
          extractPath(qualifier, step :: accum)

        // Structural Type Support (selectDynamic)
        case Apply(Select(qualifier, "selectDynamic"), List(Literal(StringConstant(name)))) =>
          val step = OpticStep.Field(name)
          extractPath(qualifier, step :: accum)
          
        // Map/List Access Support
        case Apply(Select(qualifier, "apply"), args) =>
          args match {
            case List(Literal(IntConstant(idx))) =>
               extractPath(qualifier, OpticStep.Index(idx) :: accum)
            case List(Literal(StringConstant(key))) =>
               extractPath(qualifier, OpticStep.Key(key) :: accum)
            case _ =>
               report.errorAndAbort(s"Unsupported arguments in selector.")
          }

        case Ident(_) => accum

        case Apply(Select(_, method), _) =>
          report.errorAndAbort(s"Invalid operation '$method'. Only path selection allowed.")

        case other =>
          // ডিবাগিংয়ের জন্য ডিটেইলড এরর মেসেজ
          report.errorAndAbort(s"Invalid selector syntax: ${other.show}\nRaw Structure: $other")
      }
    }

    val steps = extractPath(selector.asTerm, List.empty)
    Expr(DynamicOptic(steps.toVector))
  }
}