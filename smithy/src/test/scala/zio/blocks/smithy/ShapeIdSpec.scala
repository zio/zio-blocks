package zio.blocks.smithy

import zio.test._

object ShapeIdSpec extends ZIOSpecDefault {
  def spec = suite("ShapeId")(
    suite("parse")(
      test("parses absolute shape reference 'namespace#name'") {
        val result = ShapeId.parse("com.example#MyShape")
        assertTrue(
          result == Right(ShapeId("com.example", "MyShape"))
        )
      },
      test("parses with complex namespace") {
        val result = ShapeId.parse("aws.ec2.admin#DescribeInstances")
        assertTrue(
          result == Right(ShapeId("aws.ec2.admin", "DescribeInstances"))
        )
      },
      test("returns error for missing namespace") {
        val result = ShapeId.parse("MyShape")
        assertTrue(result.isLeft)
      },
      test("returns error for missing name") {
        val result = ShapeId.parse("com.example#")
        assertTrue(result.isLeft)
      },
      test("returns error for empty string") {
        val result = ShapeId.parse("")
        assertTrue(result.isLeft)
      },
      test("returns error for missing # separator") {
        val result = ShapeId.parse("com.example.MyShape")
        assertTrue(result.isLeft)
      },
      test("parses member reference with $member suffix") {
        val result   = ShapeId.parse("com.example#MyShape$member")
        val expected = Right(ShapeId.Member(ShapeId("com.example", "MyShape"), "member"))
        assertTrue(result == expected)
      },
      test("parses member with complex name") {
        val result   = ShapeId.parse("aws.ec2#DescribeInstances$InstanceIds")
        val expected = Right(
          ShapeId.Member(ShapeId("aws.ec2", "DescribeInstances"), "InstanceIds")
        )
        assertTrue(result == expected)
      },
      test("returns error for invalid member reference (no name after $)") {
        val result = ShapeId.parse("com.example#MyShape$")
        assertTrue(result.isLeft)
      }
    ),
    suite("toString")(
      test("formats as 'namespace#name'") {
        val shape = ShapeId("com.example", "MyShape")
        assertTrue(shape.toString == "com.example#MyShape")
      },
      test("formats with complex namespace") {
        val shape = ShapeId("aws.ec2.admin", "DescribeInstances")
        assertTrue(shape.toString == "aws.ec2.admin#DescribeInstances")
      },
      test("member formats as 'namespace#name$member'") {
        val member = ShapeId.Member(ShapeId("com.example", "MyShape"), "member")
        assertTrue(member.toString == "com.example#MyShape$member")
      },
      test("member formats with complex names") {
        val member =
          ShapeId.Member(ShapeId("aws.ec2", "DescribeInstances"), "InstanceIds")
        assertTrue(member.toString == "aws.ec2#DescribeInstances$InstanceIds")
      }
    ),
    suite("equality")(
      test("two identical shapes are equal") {
        val shape1 = ShapeId("com.example", "MyShape")
        val shape2 = ShapeId("com.example", "MyShape")
        assertTrue(shape1 == shape2)
      },
      test("different namespaces are not equal") {
        val shape1 = ShapeId("com.example", "MyShape")
        val shape2 = ShapeId("com.other", "MyShape")
        assertTrue(shape1 != shape2)
      },
      test("different names are not equal") {
        val shape1 = ShapeId("com.example", "MyShape")
        val shape2 = ShapeId("com.example", "OtherShape")
        assertTrue(shape1 != shape2)
      },
      test("two identical members are equal") {
        val member1 = ShapeId.Member(ShapeId("com.example", "MyShape"), "member")
        val member2 = ShapeId.Member(ShapeId("com.example", "MyShape"), "member")
        assertTrue(member1 == member2)
      },
      test("different member names are not equal") {
        val member1 = ShapeId.Member(ShapeId("com.example", "MyShape"), "member1")
        val member2 = ShapeId.Member(ShapeId("com.example", "MyShape"), "member2")
        assertTrue(member1 != member2)
      },
      test("shape and member have different types") {
        val shape  = ShapeId("com.example", "MyShape")
        val member = ShapeId.Member(shape, "member")
        assertTrue(
          shape.isInstanceOf[ShapeId],
          member.isInstanceOf[ShapeId.Member]
        )
      }
    ),
    suite("construction")(
      test("creates shape with namespace and name") {
        val shape = ShapeId("com.example", "MyShape")
        assertTrue(
          shape.namespace == "com.example",
          shape.name == "MyShape"
        )
      },
      test("creates member with shape and member name") {
        val shape  = ShapeId("com.example", "MyShape")
        val member = ShapeId.Member(shape, "member")
        assertTrue(
          member.shape == shape,
          member.memberName == "member"
        )
      }
    )
  )
}
