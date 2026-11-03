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

package zio.blocks.config

import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable

/**
 * A flag whose value is chosen on each call by a [[Rollout]] expression, which
 * can be replaced while the program runs. Extend it with a Scala `object`,
 * passing the value to use when nothing matches and the initial expression. The
 * flag's name is the object's fully qualified name.
 *
 * `flag(key, attributes*)` matches the expression against the path
 * `key/attribute1/attribute2/...`, with the percentage bucket of `key`
 * ([[Rollout.bucketFor]]), and counts the call under `key`. It returns the
 * matched value parsed by the [[Flag.Reader]], or `default` when no choice
 * matches or the value does not parse. The bucket ignores the flag's name, so
 * flags rolled out to the same percentage pick the same keys, a known bug.
 *
 * The initial expression comes from the first of: [[FlagSource.Registry]], the
 * system property with the flag's name, the environment variable named like the
 * flag (dots replaced by underscores, upper case; on Scala.js this step finds
 * nothing, a known bug), and `defaultExpression`. An expression that does not
 * parse throws an `ExceptionInInitializerError` whose cause is a
 * [[FlagException.FlagExpressionParseException]]. Values are not checked
 * against the reader until a call matches them.
 *
 * Spaces around a `/` in a selector are ignored, so `* / eu / 50%` is the
 * selector `*`, `eu`, `50%`.
 *
 * {{{
 * object NewCheckout extends DynamicFlag[Boolean](false, "true@* / eu / 50%; false")
 *
 * val bucket = Rollout.bucketFor("user-5")
 * // 0
 * val inEu   = NewCheckout("user-5", "eu")
 * // true
 * val inUs   = NewCheckout("user-5", "us")
 * // false
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/config/flags/ Flags]]
 */
abstract class DynamicFlag[A](default: A, defaultExpression: String)(implicit reader: Flag.Reader[A]) {

  /**
   * The flag's name: the object's fully qualified class name without the
   * trailing `$`, with each inner `$` replaced by a dot.
   */
  val name: String = DynamicFlag.deriveName(getClass)

  private val envName: String = name.replace('.', '_').toUpperCase

  @volatile private var _snapshot: DynamicFlag.Snapshot[A] = {
    val initialExpr = DynamicFlag.resolveInitialExpression(name, envName, defaultExpression)
    DynamicFlag.initSnapshot(name, initialExpr, default, reader)
  }

  private val _counters: ConcurrentHashMap[String, java.util.concurrent.atomic.LongAdder] =
    new ConcurrentHashMap[String, java.util.concurrent.atomic.LongAdder]()

  @volatile private var _countersBounded: Boolean = false

  private val OverflowBucket: String = "other"

  private val _parseErrorCount: java.util.concurrent.atomic.LongAdder =
    new java.util.concurrent.atomic.LongAdder()

  private val _history: mutable.ArrayDeque[DynamicFlag.UpdateRecord] =
    new mutable.ArrayDeque[DynamicFlag.UpdateRecord]()

  /**
   * Evaluates the expression for the path `key/attributes...` and the bucket of
   * `key`, and adds one to the counter of `key`. Returns `default` when no
   * choice matches or the matched value does not parse.
   */
  def apply(key: String, attributes: String*): A = {
    incrementCounter(key)
    evaluateInternal(key, attributes)
  }

  /** Evaluates like `apply` without touching the counters. */
  def evaluate(key: String, attributes: String*): A =
    evaluateInternal(key, attributes)

  /** The current rollout expression, trimmed. */
  def expression: String = _snapshot.expression

  /**
   * Replaces the expression with `newExpression`, trimmed, and records the
   * change in `updateHistory`. Returns the parse error and keeps the old
   * expression when `newExpression` does not parse. An empty or blank
   * expression changes nothing and returns `Right(())`. Values are not checked
   * against the reader, so `maybe` for a `DynamicFlag[Boolean]` is accepted and
   * every matching call returns the default.
   *
   * {{{
   * object SearchBackend extends DynamicFlag[String]("solr", "solr")
   *
   * val result = SearchBackend.update("opensearch@* / 10%; solr")
   * // Right(())
   * val now    = SearchBackend.expression
   * // opensearch@* / 10%; solr
   * }}}
   */
  def update(newExpression: String): Either[ConfigError, Unit] = {
    val trimmed = newExpression.trim
    if (trimmed.isEmpty) return Right(())

    Rollout.parseChoices(trimmed) match {
      case Right(choices) =>
        val oldExpr = _snapshot.expression
        _snapshot = DynamicFlag.Snapshot(trimmed, choices, default, reader)
        recordUpdate(oldExpr, trimmed)
        Right(())
      case Left(err) => Left(err)
    }
  }

  /**
   * Reads the expression for this flag's name from [[FlagSource.Registry]] and
   * applies it like `update`. Returns `NoSource` when no registered source has
   * a value (system properties and the environment are not read again),
   * `Unchanged` when the trimmed value equals the current expression, `Failed`
   * when it does not parse, and otherwise `Updated`.
   */
  def reload(): Flag.ReloadResult = {
    val resolved = FlagSource.Registry.resolve(name)
    resolved.fold[Flag.ReloadResult](Flag.ReloadResult.NoSource) { raw =>
      val SourceValue(rawValue, _) = raw
      val trimmed                  = rawValue.trim
      if (trimmed == _snapshot.expression) Flag.ReloadResult.Unchanged
      else
        Rollout.parseChoices(trimmed) match {
          case Right(choices) =>
            val oldExpr = _snapshot.expression
            _snapshot = DynamicFlag.Snapshot(trimmed, choices, default, reader)
            recordUpdate(oldExpr, trimmed)
            Flag.ReloadResult.Updated(oldExpr, trimmed)
          case Left(err) =>
            Flag.ReloadResult.Failed(err)
        }
    }
  }

  /**
   * The number of `apply` calls per key. The first 100 distinct keys get their
   * own entry; calls with any later new key are counted under `other`. Counts
   * that change while this map is built may be partly included.
   */
  def counters: Map[String, Long] = {
    val builder = Map.newBuilder[String, Long]
    val iter    = _counters.entrySet().iterator()
    while (iter.hasNext) {
      val entry = iter.next()
      builder += entry.getKey -> entry.getValue.sum()
    }
    builder.result()
  }

  /**
   * The expression changes made by `update` and `reload`, most recent first, at
   * most the last 10.
   */
  def updateHistory: List[DynamicFlag.UpdateRecord] =
    _history.synchronized {
      _history.reverseIterator.toList
    }

  /**
   * The number of calls, through `apply` or `evaluate`, whose matched value
   * could not be parsed and that returned the default instead. A non-zero count
   * means the expression holds a value the reader rejects.
   */
  def parseErrorCount: Long = _parseErrorCount.sum()

  private def evaluateInternal(key: String, attributes: Seq[String]): A = {
    val snap   = _snapshot
    val bucket = Rollout.bucketFor(key)
    val path   =
      if (attributes.isEmpty) key
      else {
        val sb = new StringBuilder(key)
        attributes.foreach { a => sb.append('/'); sb.append(a) }
        sb.toString
      }
    Rollout.evaluateIndex(snap.choices, path, bucket).fold(snap.default) { rawValue =>
      snap.reader.parse(name, rawValue) match {
        case Right(v) => v
        case Left(_)  =>
          _parseErrorCount.increment()
          snap.default
      }
    }
  }

  private def incrementCounter(key: String): Unit = {
    val existing = _counters.get(key)
    if (existing != null) {
      existing.increment()
    } else if (_countersBounded) {
      val overflow = _counters.get(OverflowBucket)
      if (overflow != null) overflow.increment()
      else {
        val adder = new java.util.concurrent.atomic.LongAdder()
        adder.increment()
        val prev = _counters.putIfAbsent(OverflowBucket, adder)
        if (prev != null) prev.increment()
      }
    } else {
      val adder = new java.util.concurrent.atomic.LongAdder()
      adder.increment()
      val prev = _counters.putIfAbsent(key, adder)
      if (prev != null) {
        prev.increment()
      } else if (_counters.size() >= DynamicFlag.MaxDistinctKeys) {
        _countersBounded = true
      }
    }
  }

  private def recordUpdate(oldExpr: String, newExpr: String): Unit =
    _history.synchronized {
      _history.append(DynamicFlag.UpdateRecord(oldExpr, newExpr, System.currentTimeMillis()))
      while (_history.size > DynamicFlag.MaxHistorySize)
        _history.removeHead()
    }

  DynamicFlag.register(this)
}

object DynamicFlag {

  /**
   * Soft limit: once this many distinct keys have been seen, new keys are
   * counted under the `"other"` overflow bucket rather than their own entry. No
   * exception is thrown; existing counters continue to work normally.
   */
  private[config] val MaxDistinctKeys: Int = 100
  private[config] val MaxHistorySize: Int  = 10

  /**
   * One change of a [[DynamicFlag]]'s expression, with the time of the change
   * in milliseconds since the epoch.
   */
  final case class UpdateRecord(oldExpression: String, newExpression: String, timestampMillis: Long)

  private[config] final case class Snapshot[A](
    expression: String,
    choices: Rollout.Choices,
    default: A,
    reader: Flag.Reader[A]
  )

  private[config] def deriveName(clazz: Class[_]): String = {
    val raw = clazz.getName
    validateObjectName(raw)
    raw
      .stripSuffix("$")
      .replace('$', '.')
  }

  private def validateObjectName(className: String): Unit = {
    if (!className.endsWith("$"))
      throw FlagException.FlagNameException(
        className,
        s"must be defined as a Scala object, but got class name: $className"
      )
    if (className.contains("$$Lambda$") || className.contains("$$anon"))
      throw FlagException.FlagNameException(
        className,
        s"must be defined as a Scala object, not a lambda or anonymous class: $className"
      )
  }

  private[config] def resolveInitialExpression(
    name: String,
    envName: String,
    defaultExpression: String
  ): String =
    FlagSource.Registry
      .resolve(name)
      .fold {
        val sysProp = System.getProperty(name)
        if (sysProp != null) sysProp
        else {
          val envVal = System.getenv(envName)
          if (envVal != null) envVal
          else defaultExpression
        }
      }(_.value)

  private[config] def initSnapshot[A](
    name: String,
    expression: String,
    default: A,
    reader: Flag.Reader[A]
  ): Snapshot[A] =
    Rollout.parseChoices(expression) match {
      case Right(choices) => Snapshot(expression, choices, default, reader)
      case Left(err)      =>
        throw new ExceptionInInitializerError(
          FlagException.FlagExpressionParseException(name, expression, err.message)
        )
    }

  private[config] def register(flag: DynamicFlag[_]): Unit = {
    val existing = Flag.registry.putIfAbsent(flag.name, flag)
    if (existing != null && (existing.asInstanceOf[AnyRef] ne flag.asInstanceOf[AnyRef]))
      throw FlagException.FlagDuplicateNameException(flag.name)
  }
}
