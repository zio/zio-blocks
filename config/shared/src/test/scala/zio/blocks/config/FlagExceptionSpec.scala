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
import zio.test.TestAspect

object FlagExceptionSpec extends ZIOSpecDefault {
  import FlagException._

  // Flags defined at object level so class names end with '$' and pass validateObjectName.
  object DupRegTestFlag extends StaticFlag[Int](0)
  object DupDynTestFlag extends DynamicFlag[Int](0, "0")

  final class StaticDuplicateHolder {
    object DuplicateFlag extends StaticFlag[Int](0)
  }

  final class DynamicDuplicateHolder {
    object DuplicateFlag extends DynamicFlag[Int](0, "0")
  }

  final class InvalidDynamicInitHolder {
    object InvalidDynamicFlag extends DynamicFlag[Int](0, "")
  }

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
        val ex = FlagDuplicateNameException("my.Flag")
        assertTrue(ex.getMessage == "Duplicate flag name 'my.Flag'")
      },
      test("getMessage contains flag name") {
        val ex = FlagDuplicateNameException("my.Flag")
        assertTrue(ex.getMessage.contains("my.Flag"))
      },
      test("extends FlagException") {
        val ex: FlagException = FlagDuplicateNameException("f")
        assertTrue(ex.isInstanceOf[FlagException])
      },
      test("has no stack trace") {
        val ex = FlagDuplicateNameException("f")
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
    suite("common properties")(
      test("all variants are FlagException") {
        val variants: List[FlagException] = List(
          FlagValueParseException("f", "v", "T"),
          FlagNameException("f", "d"),
          FlagDuplicateNameException("f"),
          FlagExpressionParseException("f", "e", "d")
        )
        assertTrue(variants.forall(_.isInstanceOf[FlagException]))
      },
      test("all variants extend Exception") {
        val variants: List[FlagException] = List(
          FlagValueParseException("f", "v", "T"),
          FlagNameException("f", "d"),
          FlagDuplicateNameException("f"),
          FlagExpressionParseException("f", "e", "d")
        )
        assertTrue(variants.forall(_.isInstanceOf[Exception]))
      },
      test("all variants have no stack trace") {
        val variants: List[FlagException] = List(
          FlagValueParseException("f", "v", "T"),
          FlagNameException("f", "d"),
          FlagDuplicateNameException("f"),
          FlagExpressionParseException("f", "e", "d")
        )
        assertTrue(variants.forall(_.getStackTrace.length == 0))
      }
    ),
    suite("production throw sites")(
      test("StaticFlag.deriveName throws FlagNameException for non-object class") {
        val result = scala.util.Try(StaticFlag.deriveName(classOf[String]))
        assertTrue(result.isFailure) &&
        assertTrue(result.failed.get.isInstanceOf[FlagNameException])
      },
      test("DynamicFlag.deriveName throws FlagNameException for non-object class") {
        val result = scala.util.Try(DynamicFlag.deriveName(classOf[String]))
        assertTrue(result.isFailure) &&
        assertTrue(result.failed.get.isInstanceOf[FlagNameException])
      },
      test(
        "StaticFlag.resolve wraps parse failures in ExceptionInInitializerError with FlagValueParseException cause"
      ) {
        val flagName = "test.flagexspec.badparse"
        System.setProperty(flagName, "not-a-number")
        val result =
          try {
            StaticFlag.resolve[Int](flagName, "TEST_FLAGEXSPEC_BADPARSE", 0, Flag.Reader.intReader)
            Left("should have thrown")
          } catch {
            case e: ExceptionInInitializerError => Right(e)
          } finally {
            System.clearProperty(flagName)
          }
        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.getCause.isInstanceOf[FlagValueParseException]) &&
        assertTrue(
          result.toOption.get.getCause
            .asInstanceOf[FlagValueParseException]
            .cause
            .exists(_.getMessage.contains("not-a-number"))
        )
      },
      test("DynamicFlag.initSnapshot wraps expression parse failures in ExceptionInInitializerError") {
        val result =
          try {
            DynamicFlag.initSnapshot("test.flag.init", "", 0, Flag.Reader.intReader)
            Left("should have thrown")
          } catch {
            case e: ExceptionInInitializerError => Right(e)
          }
        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.getCause.isInstanceOf[FlagExpressionParseException])
      },
      test("StaticFlag.register throws FlagDuplicateNameException when name already registered") {
        // Temporarily replace the registry entry with a sentinel to simulate a prior registrant
        val original = Flag.registry.get(DupRegTestFlag.name)
        Flag.registry.put(DupRegTestFlag.name, new Object())
        val result = scala.util.Try(StaticFlag.register(DupRegTestFlag))
        Flag.registry.put(DupRegTestFlag.name, original)
        assertTrue(result.isFailure) &&
        assertTrue(result.failed.get.isInstanceOf[FlagDuplicateNameException])
      },
      test("DynamicFlag.register throws FlagDuplicateNameException when name already registered") {
        val original = Flag.registry.get(DupDynTestFlag.name)
        Flag.registry.put(DupDynTestFlag.name, new Object())
        val result = scala.util.Try(DynamicFlag.register(DupDynTestFlag))
        Flag.registry.put(DupDynTestFlag.name, original)
        assertTrue(result.isFailure) &&
        assertTrue(result.failed.get.isInstanceOf[FlagDuplicateNameException])
      },
      test("StaticFlag duplicate registration path throws FlagDuplicateNameException") {
        val first  = new StaticDuplicateHolder().DuplicateFlag
        val result =
          try scala.util.Try(new StaticDuplicateHolder().DuplicateFlag)
          finally Flag.registry.remove(first.name, first)

        assertTrue(result.isFailure) &&
        assertTrue(result.failed.get.isInstanceOf[FlagDuplicateNameException])
      },
      test("DynamicFlag duplicate registration path throws FlagDuplicateNameException") {
        val first  = new DynamicDuplicateHolder().DuplicateFlag
        val result =
          try scala.util.Try(new DynamicDuplicateHolder().DuplicateFlag)
          finally Flag.registry.remove(first.name, first)

        assertTrue(result.isFailure) &&
        assertTrue(result.failed.get.isInstanceOf[FlagDuplicateNameException])
      },
      test("DynamicFlag field initializer wraps FlagExpressionParseException in ExceptionInInitializerError") {
        val result =
          try {
            new InvalidDynamicInitHolder().InvalidDynamicFlag
            Left("should have thrown")
          } catch {
            case e: ExceptionInInitializerError => Right(e)
          }
        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.getCause.isInstanceOf[FlagExpressionParseException])
      }
    ) @@ TestAspect.sequential
  )
}
