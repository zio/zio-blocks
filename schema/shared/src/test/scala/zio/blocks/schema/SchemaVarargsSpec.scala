/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

object SchemaVarargsSpec extends SchemaBaseSpec {

  final case class Varargs(xs: Int*)

  object Varargs {
    implicit val schema: Schema[Varargs] = Schema.derived
  }

  final case class GenericVarargs[A](xs: A*)

  object GenericVarargs {
    implicit val schema: Schema[GenericVarargs[String]] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("SchemaVarargsSpec")(
    test("compiles Schema.derived for a case class with varargs") {
      typeCheck {
        """
        import zio.blocks.schema._

        case class Varargs(xs: Int*)

        object Varargs {
          implicit val schema: Schema[Varargs] = Schema.derived
        }
        """
      }.map(result => assert(result)(isRight))
    },
    test("round-trips a case class with varargs through DynamicValue") {
      val schema = Varargs.schema
      assert(schema.fromDynamicValue(schema.toDynamicValue(Varargs())))(isRight(equalTo(Varargs()))) &&
      assert(schema.fromDynamicValue(schema.toDynamicValue(Varargs(1, 2, 3))))(isRight(equalTo(Varargs(1, 2, 3))))
    },
    test("round-trips a generic case class with varargs through DynamicValue") {
      val schema = GenericVarargs.schema
      val value  = GenericVarargs[String]("a", "b", "c")
      assert(schema.fromDynamicValue(schema.toDynamicValue(value)))(isRight(equalTo(value)))
    }
  )
}
