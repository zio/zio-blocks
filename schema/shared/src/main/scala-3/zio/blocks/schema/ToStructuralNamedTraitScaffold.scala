package zio.blocks.schema

import scala.quoted._
import scala.annotation.experimental

/**
 * Scaffold for future named-trait generation for Scala 3 macros.
 *
 * The goal is to eventually generate a named trait per expansion such that
 * `ToStructural.Aux[A, GeneratedTrait]` can be summoned exactly at
 * compile-time.
 *
 * This file contains helper stubs and notes to implement the feature safely in
 * incremental steps.
 */
object ToStructuralNamedTraitScaffold {
  @experimental
  inline def createNamedTrait[A]: Option[String] = ${ createNamedTraitImpl[A] }

  @experimental
  private def createNamedTraitImpl[A: Type](using q: Quotes): Expr[Option[String]] = {
    import q.reflect._

    // NOTE: This is an intentionally conservative scaffold.
    //
    // When implemented, this macro will attempt to:
    // 1. Create a new synthetic trait symbol using `Symbol.newClass` inside
    //    `Symbol.spliceOwner`'s owner.
    // 2. Create a `ClassDef` with `DefDef` members matching constructor fields
    //    and insert it into the enclosing compilation unit.
    // 3. Return the fully-qualified generated trait name so the caller macro
    //    can refer to it in a stable way.
    //
    // For now, keep the scaffold inert and emit a helpful info message so
    // developers can see the macro invocation during compilation.

    // Feature flag to enable the experimental named-trait generation path.
    val enabled = sys.props.get("zio.blocks.structural.enableNamedTrait").contains("true")

    // Additional flags to control symbol emission and strictness:
    // - emitNamedTraitSymbol: when true, attempt to create a class symbol and method symbols.
    // - requireNamedTraitSymbol: when true, fail compilation if symbol emission was attempted but failed.
    val emitSymbol    = sys.props.get("zio.blocks.structural.emitNamedTraitSymbol").contains("true")
    val requireSymbol = sys.props.get("zio.blocks.structural.requireNamedTraitSymbol").contains("true")
    val emitToSource  = sys.props.get("zio.blocks.structural.emitToSource").contains("true")

    if (enabled) {
      report.warning(
        "ToStructuralNamedTraitScaffold: experimental named-trait generation is ENABLED; returning a generated name (no class emitted)"
      )

      // Produce a deterministic-looking generated name based on the splice owner
      val owner         = Symbol.spliceOwner.owner
      val ownerName     = Option(owner.fullName).getOrElse("Owner")
      val generatedName = s"Structural_${ownerName.replace('.', '_')}_${System.nanoTime().toHexString}"

      // Build a trait source string describing the members we would emit if
      // we were to synthesize a real trait. This experiment logs the source
      // for inspection and will also attempt to create a synthetic class
      // symbol and per-member method symbols (best-effort, non-invasive).
      var symbolCreated = false
      try {
        val tpe = TypeRepr.of[A]

        val params: List[(String, TypeRepr)] = tpe.classSymbol
          .map(_.primaryConstructor.paramSymss.flatten.map { p =>
            val pname = p.name
            val ptype = tpe.memberType(p).dealias
            pname -> ptype
          })
          .getOrElse(Nil)

        val members = params
          .sortBy(_._1)
          .map { case (n, tp) => s"  def $n: ${tp.show}" }
          .mkString("\n")

        val traitSource = s"trait $generatedName {\n$members\n}"

        report.info(s"[ToStructuralNamedTraitScaffold] Generated trait source:\n$traitSource")

        // Persist the generated trait source to `target/structural-generated` so
        // developers can inspect and optionally copy it into sources for further
        // experimentation. This is intentionally non-invasive: files written here
        // are not compiled during the same compilation run.
        try {
          import java.nio.file.{Files, Paths}
          val base = Paths.get("target", "structural-generated")
          Files.createDirectories(base)
          val fname   = s"${generatedName}.scala"
          val file    = base.resolve(fname)
          val content = s"package zio.blocks.structural.generated\n\n$traitSource\n"
          Files.writeString(file, content)
          report.info(s"[ToStructuralNamedTraitScaffold] Wrote generated trait to: ${file.toAbsolutePath}")
        } catch {
          case t: Throwable =>
            report.warning(s"ToStructuralNamedTraitScaffold: failed writing generated trait to disk: ${t.getMessage}")
        }

        if (emitSymbol) {
          // Best-effort: create a class symbol and per-field method symbols,
          // but do NOT attempt to insert a ClassDef into the compilation unit.
          try {
            val ownerSym                    = Symbol.spliceOwner.owner
            val parentReprs: List[TypeRepr] = List(TypeRepr.of[Object])

            val clsSym = Symbol.newClass(
              ownerSym,
              generatedName,
              parents = parentReprs,
              decls = _ => Nil,
              selfType = Some(TypeRepr.of[Any])
            )

            params.sortBy(_._1).foreach { case (n, tp) =>
              try {
                Symbol.newMethod(clsSym, n, MethodType(Nil)(_ => Nil, _ => tp))
              } catch {
                case t: Throwable =>
                  report.warning(s"ToStructuralNamedTraitScaffold: failed creating method symbol $n: ${t.getMessage}")
              }
            }

            report.info(s"[ToStructuralNamedTraitScaffold] Created symbol: ${clsSym.fullName}")
            symbolCreated = true

            // If emitToSource was not requested, attempt a best-effort in-memory
            // ClassDef emission: construct a ClassDef with abstract method
            // `DefDef`s and return it as part of the macro expansion. This is
            // experimental and heavily guarded; failures will fall back to the
            // previous behavior.
            if (!emitToSource) {
              try {
                val ctorSym = clsSym.primaryConstructor

                // Create abstract method DefDefs for each param
                val methodDefs: List[Statement] = params.sortBy(_._1).map { case (n, tp) =>
                  val mSym =
                    Symbol.newMethod(clsSym, n, MethodType(Nil)(_ => Nil, _ => tp), Flags.Deferred, Symbol.noSymbol)
                  DefDef(mSym, _ => None)
                }

                // Create a minimal constructor DefDef that delegates to `()`
                val ctorDef = DefDef(ctorSym, (_: List[List[Tree]]) => Some('{ () }.asTerm))

                val parents: List[Tree] = List(TypeTree.of[Object])

                val classDef = ClassDef(clsSym, parents, ctorDef :: methodDefs)

                // Return an expression that contains the class definition in a
                // block so the new type is available in the scope of the
                // expansion. If this fails to compile the enclosing code, we
                // catch and fall back.
                val resultTerm: Term = Block(List(classDef), '{ Some(${ Expr(generatedName) }) }.asTerm)
                return resultTerm.asExprOf[Option[String]]
              } catch {
                case t: Throwable =>
                  report.warning(s"ToStructuralNamedTraitScaffold: in-memory ClassDef emission failed: ${t.getMessage}")
              }
            }
          } catch {
            case t: Throwable =>
              report.warning(s"ToStructuralNamedTraitScaffold: symbol-creation experiment failed: ${t.getMessage}")
              symbolCreated = false
          }
        }
      } catch {
        case t: Throwable =>
          report.warning(s"ToStructuralNamedTraitScaffold: failed to build trait source: ${t.getMessage}")
      }

      // Return the generated name as a hint to the caller. No bytecode is emitted
      // unless `emitSymbol` is enabled and symbol creation succeeded. If
      // `requireSymbol` is set and symbol emission was attempted but failed,
      // report an error to make this opt-in strict mode fail the compilation.
      if (emitSymbol && requireSymbol && !symbolCreated) {
        report.error(
          "ToStructuralNamedTraitScaffold: required symbol emission failed; aborting compilation as requested by property 'zio.blocks.structural.requireNamedTraitSymbol'"
        )
        '{ None }
      } else if (emitSymbol && !symbolCreated) {
        // Symbol emission was attempted but failed; fall back to returning None
        // to signal the caller that no real named trait was created.
        Expr(None)
      } else {
        Expr(Some(generatedName))
      }
    } else {
      report.info("ToStructuralNamedTraitScaffold.createNamedTrait invoked (no-op scaffold)")
      '{ None }
    }
  }
}
