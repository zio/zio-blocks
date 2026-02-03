package zio.blocks.schema.binding

import zio.blocks.schema.SchemaBaseSpec
import zio.test.Assertion._
import zio.test._

object RegisterOffsetSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("RegisterOffsetSpec")(
    suite("RegisterOffset.apply")(
      test("calculates correct RegisterOffset") {
        assert(
          RegisterOffset(
            booleans = 1,
            bytes = 2,
            chars = 3,
            shorts = 4,
            floats = 5,
            ints = 6,
            doubles = 7,
            longs = 8,
            objects = 9
          )
        )(equalTo(((1 + 2 + (3 + 4) * 2 + (5 + 6) * 4 + (7 + 8) * 8).toLong << 32) | 9))
      },
      test("throws IllegalArgumentException in case of overflow") {
        assert(
          RegisterOffset(
            booleans = 1,
            bytes = 2,
            chars = 3,
            shorts = 4,
            floats = 5,
            ints = 6,
            doubles = Int.MaxValue / 16,
            longs = Int.MaxValue / 16,
            objects = 9
          )
        )(throwsA[IllegalArgumentException])
      }
    ),
    suite("RegisterOffset.add")(
      test("adds RegisterOffset values") {
        val offset = RegisterOffset(
          booleans = 1,
          bytes = 2,
          chars = 3,
          shorts = 4,
          floats = 5,
          ints = 6,
          doubles = 7,
          longs = 8,
          objects = 9
        )
        assert(RegisterOffset.add(offset, offset))(
          equalTo(
            RegisterOffset(
              booleans = 2,
              bytes = 4,
              chars = 6,
              shorts = 8,
              floats = 10,
              ints = 12,
              doubles = 14,
              longs = 16,
              objects = 18
            )
          )
        )
      },
      test("throws IllegalArgumentException in case of overflow") {
        val offset1 = RegisterOffset(
          booleans = 1,
          bytes = 2,
          chars = 3,
          shorts = 4,
          floats = 5,
          ints = 6,
          doubles = 7,
          longs = 8,
          objects = Int.MaxValue
        )
        val offset2 = RegisterOffset(
          booleans = 1,
          bytes = 2,
          chars = 3,
          shorts = 4,
          floats = 5,
          ints = 6,
          doubles = Int.MaxValue / 32,
          longs = Int.MaxValue / 32,
          objects = 9
        )
        assert(RegisterOffset.add(offset1, offset1))(throwsA[IllegalArgumentException]) &&
        assert(RegisterOffset.add(offset2, offset2))(throwsA[IllegalArgumentException])
      }
    )
  )
}
