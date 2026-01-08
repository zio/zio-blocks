package zio.blocks.schema.migration.optic

import scala.quoted.*
import zio.blocks.schema.migration.optic.OpticStep

/**
 * The Macro Engine for ZIO Schema Migration.
 * * This object is responsible for converting user-friendly selector functions 
 * (e.g., `_.address.street`) into our internal `DynamicOptic` data structure.
 * * We use Scala 3's 'Quotes' API which is much cleaner and safer than Scala 2 reflection.
 */
object SelectorMacro {

  /**
   * The entry point for the macro.
   * Converts a selector function into a DynamicOptic.
   * * @param selector A function like `person => person.name`
   * @return A DynamicOptic representing the path (e.g., Path("name"))
   */
  inline def translate[S, A](inline selector: S => A): DynamicOptic = 
    ${ translateImpl('selector) }

  /**
   * The core implementation logic.
   * It inspects the Abstract Syntax Tree (AST) of the code provided by the user.
   */
  private def translateImpl[S: Type, A: Type](selector: Expr[S => A])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect.*

    // রিকার্সিভ ফাংশন যা কোডের ভেতর ঢুকে পাথ বা ফিল্ড নামগুলো খুঁজে বের করবে
    def extractPath(term: Term, accum: List[OpticStep]): List[OpticStep] = {
      term match {
        
        // কেস ১: ল্যাম্বডা ফাংশন (যেমন: x => x.name)
        // আমরা ল্যাম্বডার বডি (body) নিয়ে কাজ করব
        case Inlined(_, _, body) => 
          extractPath(body, accum)
        
        case Block(List(), expr) => 
          extractPath(expr, accum)

        case Typed(expr, _) => 
          extractPath(expr, accum)

        case Lambda(_, body) => 
          extractPath(body, accum)

        // কেস ২: ফিল্ড সিলেকশন (যেমন: person.address)
        // 'Select' নোড দেখলে আমরা ফিল্ডের নামটা লিস্টে অ্যাড করব
        case Select(qualifier, name) =>
          val step = OpticStep.Field(name)
          extractPath(qualifier, step :: accum)

        // কেস ৩: রুট বা ভেরিয়েবল (ব্যাস কেস)
        // যখন আমরা 'person' ভেরিয়েবলে পৌঁছাব, তখন থামা উচিত
        case Ident(_) => 
          accum

        // এরর হ্যান্ডলিং: যদি ইউজার এমন কিছু লেখে যা আমরা সাপোর্ট করি না
        // যেমন: _.age + 1 (এটা পাথ নয়, অপারেশন)
        case other =>
          report.errorAndAbort(s"Invalid selector expression. Expected a simple path (e.g., _.field), but got: ${other.show}")
      }
    }

    // ম্যাক্রো শুরু হচ্ছে এখান থেকে
    val steps = extractPath(selector.asTerm, List.empty)
    
    // সংগৃহীত স্টেপগুলো দিয়ে DynamicOptic তৈরি করা হচ্ছে (Code Generation)
    '{ DynamicOptic(${Expr(steps.toVector)}) }
  }
}