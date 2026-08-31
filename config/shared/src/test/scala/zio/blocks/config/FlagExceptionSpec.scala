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
    ) @@ TestAspect.sequential,
    suite("FlagValueParseException sensitive redaction")(
      test("sensitive flag redacts rawValue and cause, retains structured fields") {
        val secret = "SENTINEL_FLAG_SECRET_a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8"
        val cause  = new RuntimeException(s"For input string: \"$secret\"")
        val ex     = FlagValueParseException("my.secret.token", secret, "Int", Some(cause))
        assertTrue(!ex.getMessage.contains(secret)) &&
        assertTrue(!ex.getMessage.contains("For input string")) &&
        assertTrue(ex.getMessage.contains("<secret>")) &&
        assertTrue(ex.getMessage.contains("my.secret.token")) &&
        assertTrue(ex.getMessage.contains("Int")) &&
        assertTrue(ex.rawValue == secret) &&
        assertTrue(ex.cause.exists(_.getMessage.contains(secret))) &&
        assertTrue(ex.flagName == "my.secret.token")
      },
      test("sensitive flag with password marker redacts") {
        val secret = "SENTINEL_FLAG_PWD_zzz111yyy222xxx333www444vvv555uuu666ttt777sss888"
        val ex     = FlagValueParseException("db.password", secret, "Int", Some(new RuntimeException(secret)))
        assertTrue(!ex.getMessage.contains(secret)) &&
        assertTrue(ex.getMessage.contains("<secret>")) &&
        assertTrue(ex.rawValue == secret)
      },
      test("sensitive flag api-key kebab and credential markers redact") {
        val s1  = "SENTINEL_FLAG_APIKEY_aaa111bbb222ccc333ddd444"
        val s2  = "SENTINEL_FLAG_CRED_eee555fff666ggg777hhh888"
        val ex1 = FlagValueParseException("service.api-key", s1, "Int", Some(new RuntimeException(s"bad $s1")))
        val ex2 = FlagValueParseException("my.credentials", s2, "Int", Some(new RuntimeException(s2)))
        assertTrue(!ex1.getMessage.contains(s1)) &&
        assertTrue(ex1.getMessage.contains("<secret>")) &&
        assertTrue(ex1.rawValue == s1) &&
        assertTrue(!ex2.getMessage.contains(s2)) &&
        assertTrue(ex2.getMessage.contains("<secret>")) &&
        assertTrue(ex2.rawValue == s2)
      },
      test("non-sensitive flag retains rawValue and cause details") {
        val ex = FlagValueParseException(
          "service.timeout",
          "badValue123",
          "Int",
          Some(new RuntimeException("For input string: \"badValue123\""))
        )
        assertTrue(ex.getMessage.contains("badValue123")) &&
        assertTrue(ex.getMessage.contains("For input string")) &&
        assertTrue(!ex.getMessage.contains("<secret>")) &&
        assertTrue(ex.getMessage.contains("service.timeout")) &&
        assertTrue(ex.getMessage.contains("Int"))
      },
      test("sensitive flag Composite wrapper does not leak") {
        val secret = "SENTINEL_FLAG_COMP_999888777666555444333222111000aaaabbbbccccdddd"
        val cause  = new RuntimeException(secret)
        val ex     = FlagValueParseException("auth.token", secret, "Int", Some(cause))
        // Flag exceptions are typically wrapped in ExceptionInInitializerError; simulate via Composite-like concatenation
        val wrapperMsg = new ExceptionInInitializerError(ex).getCause.getMessage
        assertTrue(!wrapperMsg.contains(secret)) &&
        assertTrue(wrapperMsg.contains("<secret>")) &&
        assertTrue(ex.rawValue == secret) &&
        assertTrue(ex.cause.exists(_.getMessage.contains(secret)))
      },
      test("Sensitive.isSensitive covers all markers for flag names") {
        val secret         = "SENTINEL_GENERIC_abc123"
        val sensitiveNames = List(
          "my.secret",
          "db.password",
          "svc.passwd",
          "auth.token",
          "api.apiKey",
          "x.api_key",
          "svc.accessKey",
          "svc.access_key",
          "m.privateKey",
          "m.private_key",
          "svc.credential",
          "svc.credentials"
        )
        assertTrue(sensitiveNames.forall { name =>
          val ex = FlagValueParseException(name, secret, "Int", Some(new RuntimeException(secret)))
          !ex.getMessage.contains(secret) && ex.getMessage.contains("<secret>")
        })
      },
      test("non-sensitive flag with long secret value still shows value") {
        val raw = "SENTINEL_NON_SENSITIVE_LONG_" + ("x" * 150)
        val ex  = FlagValueParseException("app.port", raw, "Int", Some(new RuntimeException(raw)))
        assertTrue(ex.getMessage.contains(raw)) &&
        assertTrue(ex.rawValue == raw)
      }
    )
  )
}
