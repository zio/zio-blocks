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

package zio.blocks.otel

import scala.quoted.*

private[otel] object LogMacros {

  def logImpl(
    self: Expr[log.type],
    message: Expr[String],
    enrichments: Expr[Seq[Any]],
    severity: Expr[Severity]
  )(using Quotes): Expr[Unit] = {
    import quotes.reflect.*

    val pos = Position.ofMacroExpansion

    val filePath   = Expr(pos.sourceFile.path)
    val lineNumber = Expr(pos.startLine + 1)

    val methodName = Expr(findEnclosingMethod(Symbol.spliceOwner))
    val namespace  = Expr(findEnclosingClass(Symbol.spliceOwner))

    enrichments match {
      case Varargs(enrichmentExprs) =>
        if (enrichmentExprs.isEmpty) {
          '{
            val state = GlobalLogState.get()
            if (state != null && $severity.number >= state.effectiveLevel($namespace)) {
              $self.emit(
                $severity,
                $message,
                SourceLocation($filePath, $namespace, $methodName, $lineNumber)
              )
            }
          }
        } else {
          generateDirectBuilderPath(
            self,
            message,
            severity,
            filePath,
            namespace,
            methodName,
            lineNumber,
            enrichmentExprs
          )
        }

      case _ =>
        report.errorAndAbort(
          "log methods require explicit arguments, not `args: _*` syntax"
        )
    }
  }

  private def generateDirectBuilderPath(
    self: Expr[log.type],
    message: Expr[String],
    severity: Expr[Severity],
    filePath: Expr[String],
    namespace: Expr[String],
    methodName: Expr[String],
    lineNumber: Expr[Int],
    enrichmentExprs: Seq[Expr[Any]]
  )(using Quotes): Expr[Unit] = {
    import quotes.reflect.*

    // Classify each enrichment by type at compile time
    sealed trait EnrichmentKind
    case class StringStringKV(idx: Int)  extends EnrichmentKind
    case class StringLongKV(idx: Int)    extends EnrichmentKind
    case class StringIntKV(idx: Int)     extends EnrichmentKind
    case class StringDoubleKV(idx: Int)  extends EnrichmentKind
    case class StringBooleanKV(idx: Int) extends EnrichmentKind
    case class ThrowableKind(idx: Int)   extends EnrichmentKind
    case class SeverityKind(idx: Int)    extends EnrichmentKind
    case class AttributesKind(idx: Int)  extends EnrichmentKind
    case class StringBodyKind(idx: Int)  extends EnrichmentKind
    case class FallbackKind(idx: Int)    extends EnrichmentKind

    val classified: Seq[EnrichmentKind] = enrichmentExprs.zipWithIndex.map { case (enrichmentExpr, idx) =>
      val tpe = enrichmentExpr.asTerm.tpe.widen
      tpe.asType match {
        case '[(String, String)]                    => StringStringKV(idx)
        case '[(String, Long)]                      => StringLongKV(idx)
        case '[(String, Int)]                       => StringIntKV(idx)
        case '[(String, Double)]                    => StringDoubleKV(idx)
        case '[(String, Boolean)]                   => StringBooleanKV(idx)
        case '[t] if tpe <:< TypeRepr.of[Throwable] => ThrowableKind(idx)
        case '[Severity]                            => SeverityKind(idx)
        case '[Attributes]                          => AttributesKind(idx)
        case '[String]                              => StringBodyKind(idx)
        case '[t]                                   =>
          Expr.summon[LogEnrichment[t]] match {
            case Some(_) => FallbackKind(idx)
            case None    =>
              report.errorAndAbort(
                s"No LogEnrichment instance found for type ${Type.show[t]}. " +
                  s"Provide an implicit LogEnrichment[${Type.show[t]}] instance.",
                enrichmentExpr.asTerm.pos
              )
          }
      }
    }

    val hasFallbacks = classified.exists(_.isInstanceOf[FallbackKind])

    '{
      val state = GlobalLogState.get()
      if (state != null && $severity.number >= state.effectiveLevel($namespace)) {
        val now     = System.nanoTime()
        val builder = AttributeBuilderPool.get()
        builder.put("code.filepath", $filePath)
        builder.put("code.namespace", $namespace)
        builder.put("code.function", $methodName)
        builder.put("code.lineno", $lineNumber.toLong)

        val annotations = LogAnnotations.get()
        if (annotations.nonEmpty) {
          annotations.foreach { case (k, v) => builder.put(k, v) }
        }

        var throwableVar: Option[Throwable] = None
        var severityVar: Severity           = $severity
        var severityTextVar: String         = $severity.text
        var bodyVar: String                 = $message

        // All enrichments applied in-order:
        ${
          val stmts: List[Expr[Any]] = classified.flatMap { kind =>
            kind match {
              case StringStringKV(idx) =>
                val expr = enrichmentExprs(idx).asExprOf[(String, String)]
                List('{ val kv = $expr; builder.put(kv._1, kv._2) })
              case StringLongKV(idx) =>
                val expr = enrichmentExprs(idx).asExprOf[(String, Long)]
                List('{ val kv = $expr; builder.put(kv._1, kv._2) })
              case StringIntKV(idx) =>
                val expr = enrichmentExprs(idx).asExprOf[(String, Int)]
                List('{ val kv = $expr; builder.put(kv._1, kv._2.toLong) })
              case StringDoubleKV(idx) =>
                val expr = enrichmentExprs(idx).asExprOf[(String, Double)]
                List('{ val kv = $expr; builder.put(kv._1, kv._2) })
              case StringBooleanKV(idx) =>
                val expr = enrichmentExprs(idx).asExprOf[(String, Boolean)]
                List('{ val kv = $expr; builder.put(kv._1, kv._2) })
              case AttributesKind(idx) =>
                val expr = enrichmentExprs(idx).asExprOf[Attributes]
                List('{
                  $expr.foreach { (k, v) =>
                    v match {
                      case AttributeValue.StringValue(s)  => builder.put(k, s)
                      case AttributeValue.LongValue(l)    => builder.put(k, l)
                      case AttributeValue.DoubleValue(d)  => builder.put(k, d)
                      case AttributeValue.BooleanValue(b) => builder.put(k, b)
                      case other                          => builder.put(k, other.toString)
                    }
                  }
                })
              case ThrowableKind(idx) =>
                val expr = enrichmentExprs(idx).asExprOf[Throwable]
                List('{
                  val t = $expr
                  throwableVar = Some(t)
                  builder.put("exception.type", t.getClass.getName)
                  builder.put("exception.message", if (t.getMessage != null) t.getMessage else "")
                })
              case SeverityKind(idx) =>
                val expr = enrichmentExprs(idx).asExprOf[Severity]
                List('{
                  val s = $expr
                  severityVar = s
                  severityTextVar = s.text
                })
              case StringBodyKind(idx) =>
                val expr = enrichmentExprs(idx).asExprOf[String]
                List('{ bodyVar = $expr })
              case FallbackKind(_) =>
                // Handled after record construction
                Nil
            }
          }.toList

          if (stmts.isEmpty) '{ () } else Expr.block(stmts.init, stmts.last.asExprOf[Any])
        }

        val record = LogRecord(
          timestampNanos = now,
          observedTimestampNanos = now,
          severity = severityVar,
          severityText = severityTextVar,
          body = bodyVar,
          attributes = builder.build,
          traceIdHi = 0L,
          traceIdLo = 0L,
          spanId = 0L,
          traceFlags = 0,
          resource = Resource.empty,
          instrumentationScope = $self.logInstrumentationScope,
          throwable = throwableVar
        )

        ${
          if (hasFallbacks) {
            // Build fallback enrichment chain
            val fallbackChain: Expr[LogRecord] => Expr[LogRecord] =
              classified.collect { case FallbackKind(idx) => idx }
                .foldLeft(identity[Expr[LogRecord]]) { (chain, idx) =>
                  val enrichmentExpr = enrichmentExprs(idx)
                  val tpe            = enrichmentExpr.asTerm.tpe.widen
                  tpe.asType match {
                    case '[t] =>
                      Expr.summon[LogEnrichment[t]] match {
                        case Some(inst) =>
                          val value = enrichmentExpr.asExprOf[t]
                          (prev: Expr[LogRecord]) => '{ $inst.enrich(${ chain(prev) }, $value) }
                        case None => chain
                      }
                  }
                }

            '{
              val finalRecord = ${ fallbackChain('record) }
              try state.logger.emit(finalRecord)
              catch {
                case e: Throwable =>
                  System.err.println(s"[zio-blocks-otel] logging error: ${e.getMessage}")
              }
            }
          } else {
            '{
              try state.logger.emit(record)
              catch {
                case e: Throwable =>
                  System.err.println(s"[zio-blocks-otel] logging error: ${e.getMessage}")
              }
            }
          }
        }
      }
    }
  }

  private def findEnclosingMethod(using Quotes)(sym: quotes.reflect.Symbol): String = {
    import quotes.reflect.*
    var current = sym
    while (current != Symbol.noSymbol) {
      if (current.isDefDef && !current.name.startsWith("$") && current.name != "<init>") {
        return current.name
      }
      current = current.owner
    }
    "<unknown>"
  }

  private def findEnclosingClass(using Quotes)(sym: quotes.reflect.Symbol): String = {
    import quotes.reflect.*
    var current = sym
    while (current != Symbol.noSymbol) {
      if (current.isClassDef) {
        return current.fullName
      }
      current = current.owner
    }
    "<unknown>"
  }
}
