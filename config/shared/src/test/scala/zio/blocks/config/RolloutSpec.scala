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

object RolloutSpec extends ConfigBaseSpec {

  def spec = suite("RolloutSpec")(
    suite("bucketFor")(
      test("returns deterministic value in 0-99") {
        val bucket = Rollout.bucketFor("test-key")
        assertTrue(bucket >= 0 && bucket < 100)
      },
      test("same key always produces same bucket") {
        val b1 = Rollout.bucketFor("stable-key")
        val b2 = Rollout.bucketFor("stable-key")
        assertTrue(b1 == b2)
      },
      test("different keys can produce different buckets") {
        val buckets = (0 until 100).map(i => Rollout.bucketFor(s"key-$i")).toSet
        assertTrue(buckets.size > 1)
      }
    ),
    suite("parseChoices")(
      test("parse catch-all") {
        val result = Rollout.parseChoices("default")
        assertTrue(result == Right(Rollout.Choices(List(Rollout.Choice.CatchAll("default")))))
      },
      test("parse targeted choice") {
        val result = Rollout.parseChoices("v2@api/users")
        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.entries.size == 1)
      },
      test("parse multiple choices") {
        val result = Rollout.parseChoices("v2@api/users; v1")
        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.entries.size == 2)
      },
      test("parse percentage selector") {
        val result = Rollout.parseChoices("canary@prod50%")
        val choices = result.toOption.get
        val targeted = choices.entries.head.asInstanceOf[Rollout.Choice.Targeted]
        assertTrue(targeted.value == "canary") &&
        assertTrue(targeted.selector.percentage == Some(50))
      },
      test("reject empty expression") {
        val result = Rollout.parseChoices("")
        assertTrue(result.isLeft)
      },
      test("reject percentage > 100") {
        val result = Rollout.parseChoices("v2@path150%")
        assertTrue(result.isLeft)
      },
      test("reject empty value before @") {
        val result = Rollout.parseChoices("@path")
        assertTrue(result.isLeft)
      },
      test("reject empty selector after @") {
        val result = Rollout.parseChoices("value@")
        assertTrue(result.isLeft)
      }
    ),
    suite("evaluateIndex")(
      test("catch-all matches everything") {
        val choices = Rollout.Choices(List(Rollout.Choice.CatchAll("fallback")))
        assertTrue(Rollout.evaluateIndex(choices, "any/path", 50) == Some("fallback"))
      },
      test("targeted with exact path match") {
        val choices = Rollout.Choices(List(
          Rollout.Choice.Targeted(
            "matched",
            Rollout.Selector(List(Rollout.Segment.Literal("api"), Rollout.Segment.Literal("users")), None)
          )
        ))
        assertTrue(Rollout.evaluateIndex(choices, "api/users", 0) == Some("matched"))
      },
      test("targeted with wildcard match") {
        val choices = Rollout.Choices(List(
          Rollout.Choice.Targeted(
            "wild",
            Rollout.Selector(List(Rollout.Segment.Wildcard, Rollout.Segment.Literal("users")), None)
          )
        ))
        assertTrue(Rollout.evaluateIndex(choices, "api/users", 0) == Some("wild"))
      },
      test("path mismatch returns None") {
        val choices = Rollout.Choices(List(
          Rollout.Choice.Targeted(
            "miss",
            Rollout.Selector(List(Rollout.Segment.Literal("api"), Rollout.Segment.Literal("users")), None)
          )
        ))
        assertTrue(Rollout.evaluateIndex(choices, "web/pages", 0) == None)
      },
      test("0% never matches") {
        val choices = Rollout.Choices(List(
          Rollout.Choice.Targeted(
            "never",
            Rollout.Selector(List(Rollout.Segment.Literal("path")), Some(0))
          )
        ))
        assertTrue(Rollout.evaluateIndex(choices, "path", 0) == None)
      },
      test("100% always matches") {
        val choices = Rollout.Choices(List(
          Rollout.Choice.Targeted(
            "always",
            Rollout.Selector(List(Rollout.Segment.Literal("path")), Some(100))
          )
        ))
        assertTrue(Rollout.evaluateIndex(choices, "path", 99) == Some("always"))
      },
      test("percentage bucketing: bucket below threshold matches") {
        val choices = Rollout.Choices(List(
          Rollout.Choice.Targeted(
            "partial",
            Rollout.Selector(List(Rollout.Segment.Literal("path")), Some(50))
          )
        ))
        assertTrue(Rollout.evaluateIndex(choices, "path", 25) == Some("partial"))
      },
      test("percentage bucketing: bucket at threshold does not match") {
        val choices = Rollout.Choices(List(
          Rollout.Choice.Targeted(
            "partial",
            Rollout.Selector(List(Rollout.Segment.Literal("path")), Some(50))
          )
        ))
        assertTrue(Rollout.evaluateIndex(choices, "path", 50) == None)
      },
      test("first match wins (left to right)") {
        val choices = Rollout.Choices(List(
          Rollout.Choice.Targeted(
            "first",
            Rollout.Selector(List(Rollout.Segment.Literal("path")), None)
          ),
          Rollout.Choice.Targeted(
            "second",
            Rollout.Selector(List(Rollout.Segment.Literal("path")), None)
          )
        ))
        assertTrue(Rollout.evaluateIndex(choices, "path", 0) == Some("first"))
      }
    ),
    suite("select (one-shot)")(
      test("selects from expression string") {
        val result = Rollout.select("canary@api/v2; stable", "api/v2", 0)
        assertTrue(result == Some("canary"))
      },
      test("falls through to catch-all") {
        val result = Rollout.select("canary@api/v2; stable", "web/home", 0)
        assertTrue(result == Some("stable"))
      },
      test("returns None on parse error") {
        val result = Rollout.select("", "path", 0)
        assertTrue(result == None)
      }
    ),
    suite("validate")(
      test("valid expression returns empty warnings") {
        val result = Rollout.validate("v2@api; v1")
        assertTrue(result == Right(Nil))
      },
      test("warns about unreachable choices after catch-all") {
        val result = Rollout.validate("default; v2@api")
        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.exists(_.contains("unreachable")))
      },
      test("warns about cumulative percentage > 100") {
        val result = Rollout.validate("a@path60%; b@path60%")
        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.exists(_.contains("exceeds 100%")))
      },
      test("invalid expression returns Left") {
        val result = Rollout.validate("")
        assertTrue(result.isLeft)
      }
    )
  )
}
