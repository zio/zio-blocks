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

import zio.test._

object DynamicFlagSpec extends ConfigBaseSpec {

  object SimpleBoolFlag extends DynamicFlag[Boolean](false, "true")

  object CatchAllIntFlag extends DynamicFlag[Int](0, "42")

  object RolloutFlag extends DynamicFlag[String]("off", "on@beta; off")

  object PercentageFlag extends DynamicFlag[Boolean](false, "true@*/100%; false")

  object ReloadUnchangedFlag extends DynamicFlag[Int](0, "1")

  def spec = suite("DynamicFlagSpec")(
    suite("name derivation")(
      test("derives dot-separated name from object class") {
        assertTrue(SimpleBoolFlag.name == "zio.blocks.config.DynamicFlagSpec.SimpleBoolFlag")
      }
    ),
    suite("isDynamic")(
      test("isDynamic is true") {
        assertTrue(SimpleBoolFlag.isDynamic)
      }
    ),
    suite("basic evaluation")(
      test("catch-all expression returns parsed value") {
        assertTrue(CatchAllIntFlag("any-key") == 42)
      },
      test("evaluate also returns parsed value without tracking") {
        assertTrue(CatchAllIntFlag.evaluate("any-key") == 42)
      },
      test("boolean catch-all") {
        assertTrue(SimpleBoolFlag("user1"))
      },
      test("rollout with path matching") {
        assertTrue(RolloutFlag("beta") == "on") &&
        assertTrue(RolloutFlag("prod") == "off")
      },
      test("percentage rollout at 100% matches all") {
        assertTrue(PercentageFlag("anything") == true)
      },
      test("default value used when expression has no match") {
        object NoMatchFlag extends DynamicFlag[Int](99, "1@specific-path")
        assertTrue(NoMatchFlag("different-path") == 99)
      },
      test("multi-segment path with attributes") {
        object MultiSegFlag extends DynamicFlag[String]("miss", "hit@region/us")
        assertTrue(MultiSegFlag("region", "us") == "hit") &&
        assertTrue(MultiSegFlag("region", "eu") == "miss")
      }
    ),
    suite("expression")(
      test("returns current expression string") {
        assertTrue(SimpleBoolFlag.expression == "true")
      }
    ),
    suite("update")(
      test("returns Right and updates expression on valid input") {
        object UpdatableFlag extends DynamicFlag[Int](0, "10")
        assertTrue(UpdatableFlag("k") == 10)
        val result = UpdatableFlag.update("20")
        assertTrue(result == Right(())) &&
        assertTrue(UpdatableFlag("k") == 20) &&
        assertTrue(UpdatableFlag.expression == "20")
      },
      test("returns Right and leaves expression unchanged on empty input") {
        object StableFlag extends DynamicFlag[String]("x", "hello")
        val result = StableFlag.update("")
        assertTrue(result == Right(())) &&
        assertTrue(StableFlag("k") == "hello") &&
        assertTrue(StableFlag.expression == "hello")
      },
      test("returns Left on unparseable rollout expression") {
        object StableFlag2 extends DynamicFlag[String]("x", "hello")
        val result = StableFlag2.update("@@@invalid;;;")
        assertTrue(result.isLeft) &&
        assertTrue(StableFlag2.expression == "hello")
      },
      test("update records history") {
        object HistFlag extends DynamicFlag[Int](0, "1")
        HistFlag.update("2")
        HistFlag.update("3")
        val history = HistFlag.updateHistory
        assertTrue(history.size == 2) &&
        assertTrue(history.head.oldExpression == "2") &&
        assertTrue(history.head.newExpression == "3") &&
        assertTrue(history(1).oldExpression == "1") &&
        assertTrue(history(1).newExpression == "2")
      },
      test("history is bounded to 10 entries") {
        object BoundedHistFlag extends DynamicFlag[Int](0, "0")
        (1 to 15).foreach(i => BoundedHistFlag.update(i.toString))
        assertTrue(BoundedHistFlag.updateHistory.size == 10)
      }
    ),
    suite("counters")(
      test("apply tracks evaluation counts") {
        object CountedFlag extends DynamicFlag[Boolean](true, "true")
        CountedFlag("a")
        CountedFlag("a")
        CountedFlag("b")
        val c = CountedFlag.counters
        assertTrue(c("a") == 2L) &&
        assertTrue(c("b") == 1L)
      },
      test("evaluate does not track counts") {
        object UncountedFlag extends DynamicFlag[Boolean](true, "true")
        UncountedFlag.evaluate("x")
        UncountedFlag.evaluate("y")
        assertTrue(UncountedFlag.counters.isEmpty)
      },
      test("counters overflow to 'other' bucket after 100 distinct keys") {
        object OverflowFlag extends DynamicFlag[Boolean](true, "true")
        (1 to 105).foreach(i => OverflowFlag(s"key-$i"))
        val c = OverflowFlag.counters
        assertTrue(c.size <= 101) &&
        assertTrue(c.values.sum == 105L)
      }
    ),
    suite("reload")(
      test("returns NoSource when no FlagSource registered") {
        object NoProviderFlag extends DynamicFlag[Int](0, "1")
        FlagSource.Registry.clear()
        assertTrue(NoProviderFlag.reload() == Flag.ReloadResult.NoSource)
      },
      test("returns Unchanged when provider value matches current expression") {
        FlagSource.Registry.clear()
        FlagSource.Registry.register(
          FlagSource.fromMap(Map(ReloadUnchangedFlag.name -> "1"), id = "test-reload")
        )
        val result = ReloadUnchangedFlag.reload()
        FlagSource.Registry.clear()
        assertTrue(result == Flag.ReloadResult.Unchanged)
      },
      test("returns Updated and changes expression when provider has new value") {
        object ReloadableFlag extends DynamicFlag[Int](0, "1")
        FlagSource.Registry.clear()
        FlagSource.Registry.register(
          FlagSource.fromMap(Map(ReloadableFlag.name -> "99"), id = "test-reload2")
        )
        val result = ReloadableFlag.reload()
        FlagSource.Registry.clear()
        assertTrue(result == Flag.ReloadResult.Updated("1", "99")) &&
        assertTrue(ReloadableFlag("k") == 99)
      },
      test("returns Failed on invalid provider value") {
        object FailReloadFlag extends DynamicFlag[Int](0, "1")
        FlagSource.Registry.clear()
        FlagSource.Registry.register(
          FlagSource.fromMap(Map(FailReloadFlag.name -> ""), id = "test-reload3")
        )
        val result = FailReloadFlag.reload()
        FlagSource.Registry.clear()
        assertTrue(result.isInstanceOf[Flag.ReloadResult.Failed])
      }
    ) @@ TestAspect.sequential,
    suite("self-registration")(
      test("registers in Flag.registry") {
        assertTrue(Flag.registry.containsKey(SimpleBoolFlag.name))
      },
      test("registered instance is same object") {
        assertTrue(Flag.registry.get(SimpleBoolFlag.name).asInstanceOf[AnyRef] eq SimpleBoolFlag.asInstanceOf[AnyRef])
      }
    ),
    suite("FlagSource integration")(
      test("initial expression resolved from FlagSource") {
        val flagName = "zio.blocks.config.DynamicFlagSpec.ProviderInitFlag"
        FlagSource.Registry.clear()
        FlagSource.Registry.register(
          FlagSource.fromMap(Map(flagName -> "provider-value"), id = "test-init")
        )
        val expr = DynamicFlag.resolveInitialExpression(flagName, "IGNORED", "default-expr")
        FlagSource.Registry.clear()
        assertTrue(expr == "provider-value")
      }
    ) @@ TestAspect.sequential,
    suite("fail-fast at init")(
      test("throws ExceptionInInitializerError wrapping FlagExpressionParseException on invalid default expression") {
        val result =
          try {
            DynamicFlag.initSnapshot("test", "", 0, Flag.Reader.intReader)
            Left("should have thrown")
          } catch {
            case e: ExceptionInInitializerError => Right(e)
          }
        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.getCause.isInstanceOf[FlagException.FlagExpressionParseException])
      }
    ),
    suite("validation")(
      test("rejects non-object usage with FlagNameException") {
        val result = scala.util.Try(DynamicFlag.deriveName(classOf[String]))
        assertTrue(result.isFailure) &&
        assertTrue(result.failed.get.isInstanceOf[FlagException.FlagNameException]) &&
        assertTrue(result.failed.get.getMessage.contains("Scala object"))
      }
    )
  ) @@ TestAspect.sequential
}
