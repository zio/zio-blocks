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

package zio.blocks.telemetry

import scala.reflect.macros.blackbox

private[telemetry] object LogMacros {

  def traceImpl(c: blackbox.Context)(message: c.Expr[String], enrichments: c.Expr[Any]*): c.Expr[Unit] =
    logImpl(c)(message, enrichments, c.universe.reify(Severity.Trace), "TRACE", 1)

  def debugImpl(c: blackbox.Context)(message: c.Expr[String], enrichments: c.Expr[Any]*): c.Expr[Unit] =
    logImpl(c)(message, enrichments, c.universe.reify(Severity.Debug), "DEBUG", 5)

  def infoImpl(c: blackbox.Context)(message: c.Expr[String], enrichments: c.Expr[Any]*): c.Expr[Unit] =
    logImpl(c)(message, enrichments, c.universe.reify(Severity.Info), "INFO", 9)

  def warnImpl(c: blackbox.Context)(message: c.Expr[String], enrichments: c.Expr[Any]*): c.Expr[Unit] =
    logImpl(c)(message, enrichments, c.universe.reify(Severity.Warn), "WARN", 13)

  def errorImpl(c: blackbox.Context)(message: c.Expr[String], enrichments: c.Expr[Any]*): c.Expr[Unit] =
    logImpl(c)(message, enrichments, c.universe.reify(Severity.Error), "ERROR", 17)

  def fatalImpl(c: blackbox.Context)(message: c.Expr[String], enrichments: c.Expr[Any]*): c.Expr[Unit] =
    logImpl(c)(message, enrichments, c.universe.reify(Severity.Fatal), "FATAL", 21)

  // Rate-limited: every N

  def traceEveryImpl(c: blackbox.Context)(every: c.Expr[Int], message: c.Expr[String], enrichments: c.Expr[Any]*): c.Expr[Unit] =
    logEveryImpl(c)(every, message, enrichments, c.universe.reify(Severity.Trace), "TRACE", 1)

  def debugEveryImpl(c: blackbox.Context)(every: c.Expr[Int], message: c.Expr[String], enrichments: c.Expr[Any]*): c.Expr[Unit] =
    logEveryImpl(c)(every, message, enrichments, c.universe.reify(Severity.Debug), "DEBUG", 5)

  def infoEveryImpl(c: blackbox.Context)(every: c.Expr[Int], message: c.Expr[String], enrichments: c.Expr[Any]*): c.Expr[Unit] =
    logEveryImpl(c)(every, message, enrichments, c.universe.reify(Severity.Info), "INFO", 9)

  def warnEveryImpl(c: blackbox.Context)(every: c.Expr[Int], message: c.Expr[String], enrichments: c.Expr[Any]*): c.Expr[Unit] =
    logEveryImpl(c)(every, message, enrichments, c.universe.reify(Severity.Warn), "WARN", 13)

  def errorEveryImpl(c: blackbox.Context)(every: c.Expr[Int], message: c.Expr[String], enrichments: c.Expr[Any]*): c.Expr[Unit] =
    logEveryImpl(c)(every, message, enrichments, c.universe.reify(Severity.Error), "ERROR", 17)

  def fatalEveryImpl(c: blackbox.Context)(every: c.Expr[Int], message: c.Expr[String], enrichments: c.Expr[Any]*): c.Expr[Unit] =
    logEveryImpl(c)(every, message, enrichments, c.universe.reify(Severity.Fatal), "FATAL", 21)

  // Rate-limited: at most once per interval

  def traceAtMostImpl(c: blackbox.Context)(intervalMillis: c.Expr[Long], message: c.Expr[String], enrichments: c.Expr[Any]*): c.Expr[Unit] =
    logAtMostImpl(c)(intervalMillis, message, enrichments, c.universe.reify(Severity.Trace), "TRACE", 1)

  def debugAtMostImpl(c: blackbox.Context)(intervalMillis: c.Expr[Long], message: c.Expr[String], enrichments: c.Expr[Any]*): c.Expr[Unit] =
    logAtMostImpl(c)(intervalMillis, message, enrichments, c.universe.reify(Severity.Debug), "DEBUG", 5)

  def infoAtMostImpl(c: blackbox.Context)(intervalMillis: c.Expr[Long], message: c.Expr[String], enrichments: c.Expr[Any]*): c.Expr[Unit] =
    logAtMostImpl(c)(intervalMillis, message, enrichments, c.universe.reify(Severity.Info), "INFO", 9)

  def warnAtMostImpl(c: blackbox.Context)(intervalMillis: c.Expr[Long], message: c.Expr[String], enrichments: c.Expr[Any]*): c.Expr[Unit] =
    logAtMostImpl(c)(intervalMillis, message, enrichments, c.universe.reify(Severity.Warn), "WARN", 13)

  def errorAtMostImpl(c: blackbox.Context)(intervalMillis: c.Expr[Long], message: c.Expr[String], enrichments: c.Expr[Any]*): c.Expr[Unit] =
    logAtMostImpl(c)(intervalMillis, message, enrichments, c.universe.reify(Severity.Error), "ERROR", 17)

  def fatalAtMostImpl(c: blackbox.Context)(intervalMillis: c.Expr[Long], message: c.Expr[String], enrichments: c.Expr[Any]*): c.Expr[Unit] =
    logAtMostImpl(c)(intervalMillis, message, enrichments, c.universe.reify(Severity.Fatal), "FATAL", 21)

  private def logImpl(c: blackbox.Context)(
    message: c.Expr[String],
    enrichments: Seq[c.Expr[Any]],
    severity: c.Expr[Severity],
    severityTextLiteral: String,
    severityNumber: Int
  ): c.Expr[Unit] = {
    import c.universe._

    val pos        = c.enclosingPosition
    val filePath   = Literal(Constant(pos.source.path))
    val lineNo     = Literal(Constant(pos.line))
    val methodName = Literal(Constant(findEnclosingMethod(c)))
    val namespace  = Literal(Constant(findEnclosingClass(c)))
    val self       = c.prefix.tree

    generateDirectBuilderPath(c)(
      self,
      message,
      enrichments,
      severity,
      severityTextLiteral,
      severityNumber,
      filePath,
      lineNo,
      methodName,
      namespace
    )
  }

  private def logEveryImpl(c: blackbox.Context)(
    every: c.Expr[Int],
    message: c.Expr[String],
    enrichments: Seq[c.Expr[Any]],
    severity: c.Expr[Severity],
    severityTextLiteral: String,
    severityNumber: Int
  ): c.Expr[Unit] = {
    import c.universe._

    val pos    = c.enclosingPosition
    val siteId = Literal(Constant((pos.source.path + ":" + pos.line).hashCode))

    val body = logImpl(c)(message, enrichments, severity, severityTextLiteral, severityNumber)

    c.Expr[Unit](q"""
      if (_root_.zio.blocks.telemetry.LogRateLimit.shouldLogEvery($siteId, ${every.tree})) {
        ${body.tree}
      }
    """)
  }

  private def logAtMostImpl(c: blackbox.Context)(
    intervalMillis: c.Expr[Long],
    message: c.Expr[String],
    enrichments: Seq[c.Expr[Any]],
    severity: c.Expr[Severity],
    severityTextLiteral: String,
    severityNumber: Int
  ): c.Expr[Unit] = {
    import c.universe._

    val pos    = c.enclosingPosition
    val siteId = Literal(Constant((pos.source.path + ":" + pos.line).hashCode))

    val body = logImpl(c)(message, enrichments, severity, severityTextLiteral, severityNumber)

    c.Expr[Unit](q"""
      if (_root_.zio.blocks.telemetry.LogRateLimit.shouldLogAtMost($siteId, ${intervalMillis.tree})) {
        ${body.tree}
      }
    """)
  }

  private def generateDirectBuilderPath(c: blackbox.Context)(
    self: c.universe.Tree,
    message: c.Expr[String],
    enrichments: Seq[c.Expr[Any]],
    severity: c.Expr[Severity],
    severityTextLiteral: String,
    severityNumber: Int,
    filePath: c.universe.Tree,
    lineNo: c.universe.Tree,
    methodName: c.universe.Tree,
    namespace: c.universe.Tree
  ): c.Expr[Unit] = {
    import c.universe._

    val tuple2StringString  = typeOf[(String, String)]
    val tuple2StringLong    = typeOf[(String, Long)]
    val tuple2StringInt     = typeOf[(String, Int)]
    val tuple2StringDouble  = typeOf[(String, Double)]
    val tuple2StringBoolean = typeOf[(String, Boolean)]
    val throwableType       = typeOf[Throwable]
    val severityType        = typeOf[Severity]
    val attributesType      = typeOf[Attributes]
    val stringType          = typeOf[String]
    val logEnrichmentTpe    = typeOf[LogEnrichment[_]].typeConstructor

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

    val classified: Seq[EnrichmentKind] = enrichments.zipWithIndex.map { case (enrichExpr, idx) =>
      val argType = enrichExpr.actualType.dealias
      if (argType <:< tuple2StringString) StringStringKV(idx)
      else if (argType <:< tuple2StringInt) StringIntKV(idx)
      else if (argType <:< tuple2StringLong) StringLongKV(idx)
      else if (argType <:< tuple2StringDouble) StringDoubleKV(idx)
      else if (argType <:< tuple2StringBoolean) StringBooleanKV(idx)
      else if (argType <:< throwableType) ThrowableKind(idx)
      else if (argType <:< severityType) SeverityKind(idx)
      else if (argType <:< attributesType) AttributesKind(idx)
      else if (argType <:< stringType) StringBodyKind(idx)
      else {
        val enrichmentType   = appliedType(logEnrichmentTpe, List(argType))
        val implicitInstance = c.inferImplicitValue(enrichmentType)
        if (implicitInstance == EmptyTree) {
          c.abort(
            enrichExpr.tree.pos,
            s"No LogEnrichment instance found for type ${argType}. " +
              s"Provide an implicit LogEnrichment[${argType}] instance."
          )
        }
        FallbackKind(idx)
      }
    }

    val hasFallbacks = classified.exists(_.isInstanceOf[FallbackKind])

    val enrichStmts: List[Tree] = classified.flatMap {
      case StringStringKV(idx) =>
        val expr = enrichments(idx).tree
        List(q"{ val kv = $expr; builder.put(kv._1, kv._2) }")
      case StringLongKV(idx) =>
        val expr = enrichments(idx).tree
        List(q"{ val kv = $expr; builder.put(kv._1, kv._2) }")
      case StringIntKV(idx) =>
        val expr = enrichments(idx).tree
        List(q"{ val kv = $expr; builder.put(kv._1, kv._2.toLong) }")
      case StringDoubleKV(idx) =>
        val expr = enrichments(idx).tree
        List(q"{ val kv = $expr; builder.put(kv._1, kv._2) }")
      case StringBooleanKV(idx) =>
        val expr = enrichments(idx).tree
        List(q"{ val kv = $expr; builder.put(kv._1, kv._2) }")
      case ThrowableKind(idx) =>
        val expr = enrichments(idx).tree
        List(q"""{
          val t = $expr
          throwableVar = _root_.scala.Some(t)
          builder.put("exception.type", t.getClass.getName)
          builder.put("exception.message", if (t.getMessage != null) t.getMessage else "")
        }""")
      case SeverityKind(idx) =>
        val expr = enrichments(idx).tree
        List(q"""{
          val s = $expr
          severityVar = s
          severityTextVar = s.text
        }""")
      case AttributesKind(idx) =>
        val expr = enrichments(idx).tree
        List(q"""$expr.foreach { (k: _root_.scala.Predef.String, v: _root_.zio.blocks.telemetry.AttributeValue) =>
          v match {
            case _root_.zio.blocks.telemetry.AttributeValue.StringValue(s)  => builder.put(k, s)
            case _root_.zio.blocks.telemetry.AttributeValue.LongValue(l)    => builder.put(k, l)
            case _root_.zio.blocks.telemetry.AttributeValue.DoubleValue(d)  => builder.put(k, d)
            case _root_.zio.blocks.telemetry.AttributeValue.BooleanValue(b) => builder.put(k, b)
            case other                                                 => builder.put(k, other.toString)
          }
        }""")
      case StringBodyKind(idx) =>
        val expr = enrichments(idx).tree
        List(q"bodyVar = $expr")
      case FallbackKind(_) =>
        Nil
    }.toList

    val emitTree: Tree = if (hasFallbacks) {
      // Fallback path: build LogRecord for LogEnrichment instances
      val fallbackIndices  = classified.collect { case FallbackKind(idx) => idx }
      var recordTree: Tree = q"record"
      for (idx <- fallbackIndices) {
        val argType          = enrichments(idx).actualType.dealias
        val enrichmentType   = appliedType(logEnrichmentTpe, List(argType))
        val implicitInstance = c.inferImplicitValue(enrichmentType)
        val expr             = enrichments(idx).tree
        recordTree = q"$implicitInstance.enrich($recordTree, $expr)"
      }
      q"""
        val record = _root_.zio.blocks.telemetry.LogRecord(
          timestampNanos = now,
          observedTimestampNanos = now,
          severity = severityVar,
          severityText = severityTextVar,
          body = _root_.zio.blocks.telemetry.LogMessage(bodyVar),
          attributes = builder.buildAndReset(),
          traceIdHi = 0L,
          traceIdLo = 0L,
          spanId = 0L,
          traceFlags = 0,
          resource = _root_.zio.blocks.telemetry.Resource.empty,
          instrumentationScope = $self.logInstrumentationScope,
          throwable = throwableVar
        )
        val finalRecord = $recordTree
        try state.logger.emit(finalRecord)
        catch {
          case e: _root_.java.lang.Throwable =>
            _root_.java.lang.System.err.println("[zio-blocks-telemetry] logging error: " + e.getMessage)
        }
      """
    } else {
      // Fast path: pass raw values to emitRaw
      q"""
        state.logger.emitRaw(
          now,
          severityVar,
          severityTextVar,
          bodyVar,
          builder,
          0L,
          0L,
          0L,
          0.toByte,
          _root_.zio.blocks.telemetry.Resource.empty,
          $self.logInstrumentationScope,
          throwableVar
        )
      """
    }

    val sevNum = Literal(Constant(severityNumber))

    c.Expr[Unit](q"""
      {
        if ($sevNum >= _root_.zio.blocks.telemetry.GlobalLogState.globalMinLevel) {
          val state = _root_.zio.blocks.telemetry.GlobalLogState.get()
          if (state != null && $severity.number >= state.effectiveLevel($namespace)) {
            val now = _root_.java.lang.System.nanoTime()
            val builder = _root_.zio.blocks.telemetry.AttributeBuilderPool.get()
            builder.put("code.filepath", $filePath)
            builder.put("code.namespace", $namespace)
            builder.put("code.function", $methodName)
            builder.put("code.lineno", $lineNo.toLong)

            val annotations = _root_.zio.blocks.telemetry.LogAnnotations.get()
            if (annotations.nonEmpty) annotations.foreach { case (k, v) => builder.put(k, v) }

            var bodyVar: _root_.scala.Predef.String = $message
            var severityVar: _root_.zio.blocks.telemetry.Severity = $severity
            var severityTextVar: _root_.scala.Predef.String = ${Literal(Constant(severityTextLiteral))}
            var throwableVar: _root_.scala.Option[_root_.java.lang.Throwable] = _root_.scala.None

            ..$enrichStmts

            $emitTree
          }
        }
      }
    """)
  }

  private def findEnclosingMethod(c: blackbox.Context): String = {
    var owner = c.internal.enclosingOwner
    while (owner != c.universe.NoSymbol) {
      if (owner.isMethod && !owner.isConstructor) {
        return owner.name.decodedName.toString
      }
      owner = owner.owner
    }
    "<unknown>"
  }

  private def findEnclosingClass(c: blackbox.Context): String = {
    var owner = c.internal.enclosingOwner
    while (owner != c.universe.NoSymbol) {
      if (owner.isClass) {
        return owner.fullName
      }
      owner = owner.owner
    }
    "<unknown>"
  }
}
