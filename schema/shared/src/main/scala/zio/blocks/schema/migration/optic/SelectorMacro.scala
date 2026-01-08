package zio.blocks.schema.migration.optic

import scala.quoted.*
import zio.blocks.schema.migration.optic.OpticStep

object SelectorMacro {

  inline def translate[S, A](inline selector: S => A): DynamicOptic = 
    ${ translateImpl('selector) }

  private def translateImpl[S: Type, A: Type](selector: Expr[S => A])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect.*

    def extractPath(term: Term, accum: List[OpticStep]): List[OpticStep] = {
      term match {
        
        // --- 1. Lambda Handling (Basics) ---
        case Inlined(_, _, body) => extractPath(body, accum)
        case Block(List(), expr) => extractPath(expr, accum)
        case Typed(expr, _)      => extractPath(expr, accum)
        case Lambda(_, body)     => extractPath(body, accum)

        // --- 2. Standard Case Class Field Access (.field) ---
        case Select(qualifier, name) =>
          val step = OpticStep.Field(name)
          extractPath(qualifier, step :: accum)

        // --- 3. NEW: Structural Type Support (.field on { def field: T }) ---
        // Scala 3 তে স্ট্রাকচারাল টাইপ এক্সেস করলে সেটা 'selectDynamic("name")' হয়ে যায়।
        // আমরা সেটাকে ডিটেক্ট করে সাধারণ ফিল্ড হিসেবে ট্রিট করব।
        case Apply(Select(qualifier, "selectDynamic"), List(Literal(StringConstant(name)))) =>
          val step = OpticStep.Field(name)
          extractPath(qualifier, step :: accum)

        // --- 4. NEW: Collection/Map Access Support ---
        // ইউজার যদি 'map("key")' বা 'list(0)' লেখে, সেটা 'apply' মেথড কল হিসেবে আসে।
        case Apply(Select(qualifier, "apply"), args) =>
          args match {
            // ইনডেক্স অ্যাক্সেস: list(0)
            case List(Literal(IntConstant(idx))) =>
               val step = OpticStep.Index(idx)
               extractPath(qualifier, step :: accum)
            
            // ম্যাপ কী অ্যাক্সেস: map("key")
            case List(Literal(StringConstant(key))) =>
               val step = OpticStep.Key(key)
               extractPath(qualifier, step :: accum)
               
            case _ =>
               report.errorAndAbort(s"Unsupported arguments in selector. Only String keys or Int indexes are allowed.")
          }

        // --- 5. Base Case (Root Variable) ---
        case Ident(_) => accum

        // --- 6. Strict Validation (Red Line Check) ---
        // যদি অন্য কোনো অপারেশন (যেমন যোগ, বিয়োগ, মেথড কল) থাকে, তবে কম্পাইল টাইমে আটকাবো।
        case Apply(Select(_, method), _) =>
          report.errorAndAbort(s"Invalid operation '$method'. Migrations only support direct path selection (e.g., _.address.street).")

        case other =>
          report.errorAndAbort(s"Invalid selector syntax: ${other.show}. expected a pure path.")
      }
    }

    val steps = extractPath(selector.asTerm, List.empty)
    '{ DynamicOptic(${Expr(steps.toVector)}) }
  }
}