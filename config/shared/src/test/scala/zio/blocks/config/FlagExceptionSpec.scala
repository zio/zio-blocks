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

object FlagExceptionSpec extends ZIOSpecDefault {
  import FlagException._

  def spec = suite("FlagExceptionSpec")(
    suite("FlagValueParseException")(
      test("formats message with flag name, raw value, and expected type") {
        val ex = FlagValueParseException("my.flag", "abc", "Int")
        assertTrue(ex.getMessage == "Failed to parse value 'abc' for flag 'my.flag' (expected Int)")
      },
      test("includes cause message when present") {
        val cause = new Exception("For input string: \"abc\"")
        val ex    = FlagValueParseException("my.flag", "abc", "Int", Some(cause))
        assertTrue(
          ex.getMessage == "Failed to parse value 'abc' for flag 'my.flag' (expected Int): For input string: \"abc\""
        )
      },
      test("getMessage contains flag name") {
        val ex = FlagValueParseException("service.timeout", "bad", "Duration")
        assertTrue(ex.getMessage.contains("service.timeout"))
      },
      test("getMessage contains raw value") {
        val ex = FlagValueParseException("service.timeout", "bad", "Duration")
        assertTrue(ex.getMessage.contains("bad"))
      },
      test("getMessage contains expected type") {
        val ex = FlagValueParseException("service.timeout", "bad", "Duration")
        assertTrue(ex.getMessage.contains("Duration"))
      },
      test("extends FlagException") {
        val ex: FlagException = FlagValueParseException("f", "v", "T")
        assertTrue(ex.isInstanceOf[FlagException])
      },
      test("has no stack trace") {
        val ex = FlagValueParseException("f", "v", "T")
        assertTrue(ex.getStackTrace.length == 0)
      }
    ),
    suite("FlagNameException")(
      test("formats message with flag name and details") {
        val ex = FlagNameException("my.FlagClass", "must be defined as a Scala object")
        assertTrue(ex.getMessage == "Invalid flag name 'my.FlagClass': must be defined as a Scala object")
      },
      test("getMessage contains flag name") {
        val ex = FlagNameException("bad.FlagClass", "details")
        assertTrue(ex.getMessage.contains("bad.FlagClass"))
      },
      test("getMessage contains details") {
        val ex = FlagNameException("f", "must be a Scala object")
        assertTrue(ex.getMessage.contains("must be a Scala object"))
      },
      test("extends FlagException") {
        val ex: FlagException = FlagNameException("f", "d")
        assertTrue(ex.isInstanceOf[FlagException])
      },
      test("has no stack trace") {
        val ex = FlagNameException("f", "d")
        assertTrue(ex.getStackTrace.length == 0)
      }
    ),
    suite("FlagDuplicateNameException")(
      test("formats message with flag name and existing class") {
        val ex = FlagDuplicateNameException("my.Flag", "com.example.OldFlag")
        assertTrue(
          ex.getMessage == "Duplicate flag name 'my.Flag': already registered by com.example.OldFlag"
        )
      },
      test("getMessage contains flag name") {
        val ex = FlagDuplicateNameException("my.Flag", "com.example.OldFlag")
        assertTrue(ex.getMessage.contains("my.Flag"))
      },
      test("getMessage contains existing class") {
        val ex = FlagDuplicateNameException("my.Flag", "com.example.OldFlag")
        assertTrue(ex.getMessage.contains("com.example.OldFlag"))
      },
      test("extends FlagException") {
        val ex: FlagException = FlagDuplicateNameException("f", "c")
        assertTrue(ex.isInstanceOf[FlagException])
      },
      test("has no stack trace") {
        val ex = FlagDuplicateNameException("f", "c")
        assertTrue(ex.getStackTrace.length == 0)
      }
    ),
    suite("FlagValidationFailedException")(
      test("formats message with flag name and details") {
        val ex = FlagValidationFailedException("cache.size", "value must be positive")
        assertTrue(ex.getMessage == "Flag validation failed for 'cache.size': value must be positive")
      },
      test("getMessage contains flag name") {
        val ex = FlagValidationFailedException("cache.size", "details")
        assertTrue(ex.getMessage.contains("cache.size"))
      },
      test("getMessage contains details") {
        val ex = FlagValidationFailedException("f", "value must be positive")
        assertTrue(ex.getMessage.contains("value must be positive"))
      },
      test("extends FlagException") {
        val ex: FlagException = FlagValidationFailedException("f", "d")
        assertTrue(ex.isInstanceOf[FlagException])
      },
      test("has no stack trace") {
        val ex = FlagValidationFailedException("f", "d")
        assertTrue(ex.getStackTrace.length == 0)
      }
    ),
    suite("FlagExpressionParseException")(
      test("formats message with flag name, expression, and details") {
        val ex = FlagExpressionParseException("feature.x", "bad@expr!", "unexpected character '!'")
        assertTrue(
          ex.getMessage == "Invalid rollout expression 'bad@expr!' for flag 'feature.x': unexpected character '!'"
        )
      },
      test("getMessage contains flag name") {
        val ex = FlagExpressionParseException("feature.x", "expr", "details")
        assertTrue(ex.getMessage.contains("feature.x"))
      },
      test("getMessage contains expression") {
        val ex = FlagExpressionParseException("f", "bad@expr!", "details")
        assertTrue(ex.getMessage.contains("bad@expr!"))
      },
      test("getMessage contains details") {
        val ex = FlagExpressionParseException("f", "expr", "unexpected character '!'")
        assertTrue(ex.getMessage.contains("unexpected character '!'"))
      },
      test("extends FlagException") {
        val ex: FlagException = FlagExpressionParseException("f", "e", "d")
        assertTrue(ex.isInstanceOf[FlagException])
      },
      test("has no stack trace") {
        val ex = FlagExpressionParseException("f", "e", "d")
        assertTrue(ex.getStackTrace.length == 0)
      }
    ),
    suite("FlagRolloutParseException")(
      test("formats message with expression and details") {
        val ex = FlagRolloutParseException("true@beta/50;", "empty choice after ';'")
        assertTrue(
          ex.getMessage == "Rollout parse error in expression 'true@beta/50;': empty choice after ';'"
        )
      },
      test("getMessage contains expression") {
        val ex = FlagRolloutParseException("true@beta/50;", "details")
        assertTrue(ex.getMessage.contains("true@beta/50;"))
      },
      test("getMessage contains details") {
        val ex = FlagRolloutParseException("expr", "empty choice after ';'")
        assertTrue(ex.getMessage.contains("empty choice after ';'"))
      },
      test("extends FlagException") {
        val ex: FlagException = FlagRolloutParseException("e", "d")
        assertTrue(ex.isInstanceOf[FlagException])
      },
      test("has no stack trace") {
        val ex = FlagRolloutParseException("e", "d")
        assertTrue(ex.getStackTrace.length == 0)
      }
    ),
    suite("FlagChoiceParseException")(
      test("formats message with choice and details") {
        val ex = FlagChoiceParseException("@missing-value", "empty value before '@'")
        assertTrue(
          ex.getMessage == "Choice parse error in '@missing-value': empty value before '@'"
        )
      },
      test("getMessage contains choice") {
        val ex = FlagChoiceParseException("@missing-value", "details")
        assertTrue(ex.getMessage.contains("@missing-value"))
      },
      test("getMessage contains details") {
        val ex = FlagChoiceParseException("choice", "empty value before '@'")
        assertTrue(ex.getMessage.contains("empty value before '@'"))
      },
      test("extends FlagException") {
        val ex: FlagException = FlagChoiceParseException("c", "d")
        assertTrue(ex.isInstanceOf[FlagException])
      },
      test("has no stack trace") {
        val ex = FlagChoiceParseException("c", "d")
        assertTrue(ex.getStackTrace.length == 0)
      }
    ),
    suite("common properties")(
      test("all variants are FlagException") {
        val variants: List[FlagException] = List(
          FlagValueParseException("f", "v", "T"),
          FlagNameException("f", "d"),
          FlagDuplicateNameException("f", "c"),
          FlagValidationFailedException("f", "d"),
          FlagExpressionParseException("f", "e", "d"),
          FlagRolloutParseException("e", "d"),
          FlagChoiceParseException("c", "d")
        )
        assertTrue(variants.forall(_.isInstanceOf[FlagException]))
      },
      test("all variants extend Exception") {
        val variants: List[FlagException] = List(
          FlagValueParseException("f", "v", "T"),
          FlagNameException("f", "d"),
          FlagDuplicateNameException("f", "c"),
          FlagValidationFailedException("f", "d"),
          FlagExpressionParseException("f", "e", "d"),
          FlagRolloutParseException("e", "d"),
          FlagChoiceParseException("c", "d")
        )
        assertTrue(variants.forall(_.isInstanceOf[Exception]))
      },
      test("all variants have no stack trace") {
        val variants: List[FlagException] = List(
          FlagValueParseException("f", "v", "T"),
          FlagNameException("f", "d"),
          FlagDuplicateNameException("f", "c"),
          FlagValidationFailedException("f", "d"),
          FlagExpressionParseException("f", "e", "d"),
          FlagRolloutParseException("e", "d"),
          FlagChoiceParseException("c", "d")
        )
        assertTrue(variants.forall(_.getStackTrace.length == 0))
      }
    )
  )
}
