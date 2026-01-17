package zio.blocks.schema.bson

import org.bson.types.ObjectId
import zio.blocks.schema.{Namespace, Reflect, Schema, TypeName}
import zio.test._

/**
 * Tests for ObjectId support in BSON codec. Covers:
 *   - Direct ObjectId encoding/decoding
 *   - ObjectId wrapped in AnyVal (CustomerId)
 *   - ObjectId used in case classes (Customer)
 *   - Lists of ObjectId wrappers
 */
object BsonCodecObjectIdSpec extends ZIOSpecDefault {

  // Import ObjectId schema support
  import ObjectIdSupport._

  // CustomerId is an AnyVal wrapper around ObjectId
  case class CustomerId(value: ObjectId) extends AnyVal

  object CustomerId {
    // Schema for CustomerId - must be a Wrapper (not Record) to encode as bare ObjectId
    // Using Schema.derived would create a Record with field "value", encoding as {"value": <ObjectId>}
    // Instead, we manually create a Wrapper schema that transparently wraps ObjectId
    implicit val schema: Schema[CustomerId] = new Schema(
      new Reflect.Wrapper[zio.blocks.schema.binding.Binding, CustomerId, ObjectId](
        wrapped = objectIdSchema.reflect,
        typeName = TypeName(Namespace(List("zio", "blocks", "schema", "bson", "BsonCodecObjectIdSpec")), "CustomerId"),
        wrapperPrimitiveType = None,
        wrapperBinding = new zio.blocks.schema.binding.Binding.Wrapper[CustomerId, ObjectId](
          wrap = oid => Right(CustomerId(oid)),
          unwrap = _.value
        )
      )
    )
  }

  // Customer uses ObjectId for id and List of CustomerId for friends
  case class Customer(id: CustomerId, name: String, age: Int, invitedFriends: List[CustomerId])

  object Customer {
    implicit val schema: Schema[Customer] = Schema.derived

    val example: Customer = Customer(
      id = CustomerId(new ObjectId("507f1f77bcf86cd799439011")),
      name = "Joseph",
      age = 18,
      invitedFriends = List(
        CustomerId(new ObjectId("507f1f77bcf86cd799439012")),
        CustomerId(new ObjectId("507f1f77bcf86cd799439013"))
      )
    )

    // Generator for CustomerId
    def genCustomerId: Gen[Any, CustomerId] =
      Gen.listOfN(12)(Gen.byte).map(bs => new ObjectId(bs.toArray)).map(CustomerId.apply)

    // Generator for Customer
    def gen: Gen[Any, Customer] =
      for {
        id      <- genCustomerId
        name    <- Gen.alphaNumericStringBounded(1, 20)
        age     <- Gen.int(0, 120)
        friends <- Gen.listOfBounded(0, 5)(genCustomerId)
      } yield Customer(id, name, age, friends)
  }

  def spec = suite("BsonCodecObjectIdSpec")(
    suite("ObjectId primitive")(
      test("encode/decode ObjectId") {
        val oid     = new ObjectId("507f1f77bcf86cd799439011")
        val codec   = BsonSchemaCodec.bsonCodec(objectIdSchema)
        val bson    = codec.encoder.toBsonValue(oid)
        val decoded = codec.decoder.fromBsonValueUnsafe(bson, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == oid)
      },
      test("round-trip random ObjectIds") {
        val codec = BsonSchemaCodec.bsonCodec(objectIdSchema)
        val gen   = Gen.listOfN(12)(Gen.byte).map(bs => new ObjectId(bs.toArray))

        check(gen) { oid =>
          val bson    = codec.encoder.toBsonValue(oid)
          val decoded =
            codec.decoder.fromBsonValueUnsafe(bson, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

          assertTrue(decoded == oid)
        }
      },
      test("ObjectId encodes as BsonObjectId (not BsonString)") {
        val oid   = new ObjectId("507f1f77bcf86cd799439011")
        val codec = BsonSchemaCodec.bsonCodec(objectIdSchema)
        val bson  = codec.encoder.toBsonValue(oid)

        // Verify it's a BsonObjectId, not a BsonString
        assertTrue(bson.isObjectId)
      }
    ),
    suite("CustomerId (AnyVal wrapper)")(
      test("encode/decode CustomerId") {
        val customerId = CustomerId(new ObjectId("507f1f77bcf86cd799439011"))
        val codec      = BsonSchemaCodec.bsonCodec(CustomerId.schema)
        val bson       = codec.encoder.toBsonValue(customerId)
        val decoded    =
          codec.decoder.fromBsonValueUnsafe(bson, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == customerId)
      },
      test("round-trip random CustomerIds") {
        val codec = BsonSchemaCodec.bsonCodec(CustomerId.schema)

        check(Customer.genCustomerId) { customerId =>
          val bson    = codec.encoder.toBsonValue(customerId)
          val decoded =
            codec.decoder.fromBsonValueUnsafe(bson, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

          assertTrue(decoded == customerId)
        }
      }
    ),
    suite("Customer with ObjectId fields")(
      test("encode/decode example Customer") {
        val customer = Customer.example
        val codec    = BsonSchemaCodec.bsonCodec(Customer.schema)
        val bson     = codec.encoder.toBsonValue(customer)
        val decoded  =
          codec.decoder.fromBsonValueUnsafe(bson, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == customer)
      },
      test("Customer encodes to document with ObjectId fields") {
        val customer = Customer.example
        val codec    = BsonSchemaCodec.bsonCodec(Customer.schema)
        val bson     = codec.encoder.toBsonValue(customer).asDocument()

        // Verify structure
        assertTrue(
          bson.containsKey("id") &&
            bson.containsKey("name") &&
            bson.containsKey("age") &&
            bson.containsKey("invitedFriends") &&
            bson.get("id").isObjectId && // id field is ObjectId
            bson.get("name").isString &&
            bson.get("age").isInt32 &&
            bson.get("invitedFriends").isArray
        )
      },
      test("invitedFriends is array of ObjectIds") {
        val customer = Customer.example
        val codec    = BsonSchemaCodec.bsonCodec(Customer.schema)
        val bson     = codec.encoder.toBsonValue(customer).asDocument()

        val friends = bson.get("invitedFriends").asArray()
        assertTrue(
          friends.size() == 2 &&
            friends.get(0).isObjectId &&
            friends.get(1).isObjectId
        )
      },
      test("round-trip random Customers") {
        val codec = BsonSchemaCodec.bsonCodec(Customer.schema)

        check(Customer.gen) { customer =>
          val bson    = codec.encoder.toBsonValue(customer)
          val decoded =
            codec.decoder.fromBsonValueUnsafe(bson, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

          assertTrue(decoded == customer)
        }
      }
    ),
    suite("Edge cases")(
      test("empty invitedFriends list") {
        val customer = Customer(
          id = CustomerId(new ObjectId("507f1f77bcf86cd799439011")),
          name = "Alice",
          age = 25,
          invitedFriends = List.empty
        )
        val codec   = BsonSchemaCodec.bsonCodec(Customer.schema)
        val bson    = codec.encoder.toBsonValue(customer)
        val decoded =
          codec.decoder.fromBsonValueUnsafe(bson, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == customer)
      },
      test("Customer with many friends") {
        val customer = Customer(
          id = CustomerId(new ObjectId("507f1f77bcf86cd799439011")),
          name = "Bob",
          age = 30,
          invitedFriends = (1 to 20).map(_ => CustomerId(ObjectId.get())).toList
        )
        val codec   = BsonSchemaCodec.bsonCodec(Customer.schema)
        val bson    = codec.encoder.toBsonValue(customer)
        val decoded =
          codec.decoder.fromBsonValueUnsafe(bson, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded.id == customer.id && decoded.invitedFriends.length == 20)
      }
    )
  )
}
