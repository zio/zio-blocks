package storecaseclassesinmongodb

import com.mongodb.MongoTimeoutException
import com.mongodb.client.{MongoClients, MongoCollection, MongoDatabase}
import org.bson.BsonDocument
import org.bson.conversions.Bson
import zio.blocks.schema.bson._

import scala.jdk.CollectionConverters._

/**
 * How to Store Case Classes in MongoDB — Insert and Query with the MongoDB Java
 * Driver
 *
 * Our own glue between a schema-derived `BsonCodec` and the driver: ZIO Blocks
 * has no MongoDB `CodecProvider`, so we convert through `BsonDocument`.
 */
final class TypedCollection[A](val raw: MongoCollection[BsonDocument], codec: BsonCodec[A]) {
  def insert(value: A): Unit =
    raw.insertOne(codec.encoder.toBsonValue(value).asDocument())

  def find(filter: Bson): Either[BsonDecoder.Error, List[A]] = {
    val decoded = raw.find(filter).asScala.toList.map(document => codec.decoder.fromBsonValue(document))
    decoded.collectFirst { case Left(error) => Left(error) }
      .getOrElse(Right(decoded.collect { case Right(value) => value }))
  }
}

object Mongo {
  val uri: String =
    sys.env.getOrElse("MONGODB_URI", "mongodb://localhost:27017/?serverSelectionTimeoutMS=2000")

  def withDatabase[A](name: String)(use: MongoDatabase => A): A = {
    val client = MongoClients.create(uri)
    try use(client.getDatabase(name))
    catch {
      case _: MongoTimeoutException =>
        System.err.println(
          s"No MongoDB server answered at $uri. Start one with: docker run --rm -d -p 27017:27017 --name mongo mongo:7"
        )
        sys.exit(1)
    } finally client.close()
  }
}
