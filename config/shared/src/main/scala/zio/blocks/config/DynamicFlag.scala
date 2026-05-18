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
 * A flag whose value is determined by a rollout expression that can be updated
 * at runtime.
 *
 * Each call to `apply` evaluates the rollout expression against the provided
 * key/attributes and tracks evaluation counters. Use `evaluate` for evaluation
 * without counter tracking.
 *
 * Intended to be extended by Scala `object` definitions:
 * {{{
 *   object MyFeature extends DynamicFlag[Boolean](false, "true@beta/50; false")
 * }}}
 *
 * Resolution order for initial expression: FlagProvider → system property → env
 * variable → constructor default.
 */
abstract class DynamicFlag[A](default: A, defaultExpression: String)(implicit reader: Flag.Reader[A]) {

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

  private val _history: mutable.ArrayDeque[DynamicFlag.UpdateRecord] =
    new mutable.ArrayDeque[DynamicFlag.UpdateRecord]()

  val isDynamic: Boolean = true

  /**
   * Evaluates the rollout expression for the given key and attributes, tracking
   * evaluation counters.
   */
  def apply(key: String, attributes: String*): A = {
    incrementCounter(key)
    evaluateInternal(key, attributes)
  }

  /**
   * Evaluates the rollout expression without tracking counters.
   */
  def evaluate(key: String, attributes: String*): A =
    evaluateInternal(key, attributes)

  /**
   * The current rollout expression string.
   */
  def expression: String = _snapshot.expression

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

  def reload(): Flag.ReloadResult = {
    val resolved = FlagProvider.Registry.resolve(name)
    resolved match {
      case None                => Flag.ReloadResult.NoProvider
      case Some((rawValue, _)) =>
        val trimmed = rawValue.trim
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
   * Current evaluation counter snapshot (key → count). Thread-safe but
   * approximate.
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
   * Update history (most recent first), limited to last 10 updates.
   */
  def updateHistory: List[DynamicFlag.UpdateRecord] =
    _history.synchronized {
      _history.reverseIterator.toList
    }

  private def evaluateInternal(key: String, attributes: Seq[String]): A = {
    val snap   = _snapshot
    val bucket = Rollout.bucketFor(key)
    val path   = if (attributes.isEmpty) key else (key +: attributes).mkString("/")
    Rollout.evaluateIndex(snap.choices, path, bucket) match {
      case Some(raw) =>
        snap.reader.parse(name, raw) match {
          case Right(v) => v
          case Left(_)  => snap.default
        }
      case None => snap.default
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

  private[config] val MaxDistinctKeys: Int = 100
  private[config] val MaxHistorySize: Int  = 10

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
      throw new IllegalArgumentException(
        s"DynamicFlag must be defined as a Scala object, but got class name: $className"
      )
    if (className.contains("$$Lambda$") || className.contains("$$anon"))
      throw new IllegalArgumentException(
        s"DynamicFlag must be defined as a Scala object, not a lambda or anonymous class: $className"
      )
  }

  private[config] def resolveInitialExpression(
    name: String,
    envName: String,
    defaultExpression: String
  ): String =
    FlagProvider.Registry.resolve(name) match {
      case Some((rawValue, _)) => rawValue
      case None                =>
        val sysProp = System.getProperty(name)
        if (sysProp != null) sysProp
        else {
          val envVal = System.getenv(envName)
          if (envVal != null) envVal
          else defaultExpression
        }
    }

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
          s"DynamicFlag '$name': invalid rollout expression '$expression': ${err.message}"
        )
    }

  private[config] def register(flag: DynamicFlag[_]): Unit = {
    val existing = Flag.registry.putIfAbsent(flag.name, flag)
    if (existing != null && (existing.asInstanceOf[AnyRef] ne flag.asInstanceOf[AnyRef]))
      throw new IllegalStateException(
        s"Duplicate DynamicFlag name '${flag.name}': already registered by ${existing.getClass.getName}"
      )
  }
}
