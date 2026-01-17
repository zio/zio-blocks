package zio.blocks.schema.bson

import zio.blocks.schema._
import java.time._

/**
 * Examples demonstrating BSON codec usage.
 * These are not tests but illustrative examples.
 */
object BsonExamples {

  // Example 1: Simple case class
  object Example1 {
    case class User(name: String, age: Int, email: String)
    object User {
      given Reflect[BsonFormat.type, User] = Reflect.derive[BsonFormat.type, User]
    }

    def run(): Unit = {
      val user = User("Alice", 30, "alice@example.com")
      val codec = summon[BsonFormat.TypeClass[User]]
      
      // Encode
      val encoded = codec.encode(user)
      println(s"Encoded ${encoded.length} bytes")
      
      // Decode
      val decoded = codec.decode(java.nio.ByteBuffer.wrap(encoded))
      decoded match {
        case Right(u) => println(s"Decoded: $u")
        case Left(err) => println(s"Error: $err")
      }
    }
  }

  // Example 2: Nested structures
  object Example2 {
    case class Address(street: String, city: String, country: String)
    object Address {
      given Reflect[BsonFormat.type, Address] = Reflect.derive[BsonFormat.type, Address]
    }

    case class Company(name: String, address: Address, employees: Int)
    object Company {
      given Reflect[BsonFormat.type, Company] = Reflect.derive[BsonFormat.type, Company]
    }

    def run(): Unit = {
      val company = Company(
        name = "Tech Corp",
        address = Address("123 Tech St", "San Francisco", "USA"),
        employees = 500
      )
      
      val codec = summon[BsonFormat.TypeClass[Company]]
      val encoded = codec.encode(company)
      val decoded = codec.decode(java.nio.ByteBuffer.wrap(encoded))
      
      println(s"Company: $company")
      println(s"Round-trip successful: ${decoded.isRight}")
    }
  }

  // Example 3: Sealed traits (ADTs)
  object Example3 {
    sealed trait PaymentMethod
    object PaymentMethod {
      case class CreditCard(number: String, expiry: String) extends PaymentMethod
      case class BankTransfer(accountNumber: String, routingNumber: String) extends PaymentMethod
      case class Cash(amount: Double) extends PaymentMethod

      given Reflect[BsonFormat.type, PaymentMethod] = Reflect.derive[BsonFormat.type, PaymentMethod]
    }

    def run(): Unit = {
      val payments: List[PaymentMethod] = List(
        PaymentMethod.CreditCard("1234-5678-9012-3456", "12/25"),
        PaymentMethod.BankTransfer("9876543210", "123456789"),
        PaymentMethod.Cash(100.50)
      )

      val codec = summon[BsonFormat.TypeClass[PaymentMethod]]
      
      payments.foreach { payment =>
        val encoded = codec.encode(payment)
        val decoded = codec.decode(java.nio.ByteBuffer.wrap(encoded))
        println(s"$payment -> ${decoded.isRight}")
      }
    }
  }

  // Example 4: Collections
  object Example4 {
    case class Playlist(name: String, songs: List[String], ratings: Map[String, Int])
    object Playlist {
      given Reflect[BsonFormat.type, Playlist] = Reflect.derive[BsonFormat.type, Playlist]
    }

    def run(): Unit = {
      val playlist = Playlist(
        name = "My Favorites",
        songs = List("Song A", "Song B", "Song C"),
        ratings = Map("Song A" -> 5, "Song B" -> 4, "Song C" -> 5)
      )

      val codec = summon[BsonFormat.TypeClass[Playlist]]
      val encoded = codec.encode(playlist)
      val decoded = codec.decode(java.nio.ByteBuffer.wrap(encoded))
      
      println(s"Playlist: $playlist")
      println(s"Decoded: $decoded")
    }
  }

  // Example 5: Optional fields
  object Example5 {
    case class Profile(
      username: String,
      bio: Option[String],
      website: Option[String],
      age: Option[Int]
    )
    object Profile {
      given Reflect[BsonFormat.type, Profile] = Reflect.derive[BsonFormat.type, Profile]
    }

    def run(): Unit = {
      val profiles = List(
        Profile("alice", Some("Software engineer"), Some("https://alice.dev"), Some(30)),
        Profile("bob", None, None, None),
        Profile("charlie", Some("Designer"), None, Some(25))
      )

      val codec = summon[BsonFormat.TypeClass[Profile]]
      
      profiles.foreach { profile =>
        val encoded = codec.encode(profile)
        val decoded = codec.decode(java.nio.ByteBuffer.wrap(encoded))
        println(s"$profile -> ${decoded.map(_.username)}")
      }
    }
  }

  // Example 6: Time types
  object Example6 {
    case class Event(
      name: String,
      timestamp: Instant,
      date: LocalDate,
      duration: Duration
    )
    object Event {
      given Reflect[BsonFormat.type, Event] = Reflect.derive[BsonFormat.type, Event]
    }

    def run(): Unit = {
      val event = Event(
        name = "Conference",
        timestamp = Instant.now(),
        date = LocalDate.of(2024, 6, 15),
        duration = Duration.ofHours(8)
      )

      val codec = summon[BsonFormat.TypeClass[Event]]
      val encoded = codec.encode(event)
      val decoded = codec.decode(java.nio.ByteBuffer.wrap(encoded))
      
      println(s"Event: $event")
      println(s"Round-trip: ${decoded.isRight}")
    }
  }

  // Example 7: Complex nested structure
  object Example7 {
    case class Tag(name: String, color: String)
    object Tag {
      given Reflect[BsonFormat.type, Tag] = Reflect.derive[BsonFormat.type, Tag]
    }

    case class Comment(author: String, text: String, timestamp: Instant)
    object Comment {
      given Reflect[BsonFormat.type, Comment] = Reflect.derive[BsonFormat.type, Comment]
    }

    case class BlogPost(
      title: String,
      content: String,
      author: String,
      tags: List[Tag],
      comments: List[Comment],
      publishedAt: Instant,
      views: Int
    )
    object BlogPost {
      given Reflect[BsonFormat.type, BlogPost] = Reflect.derive[BsonFormat.type, BlogPost]
    }

    def run(): Unit = {
      val post = BlogPost(
        title = "Introduction to BSON",
        content = "BSON is a binary serialization format...",
        author = "Alice",
        tags = List(
          Tag("tutorial", "blue"),
          Tag("bson", "green")
        ),
        comments = List(
          Comment("Bob", "Great article!", Instant.now()),
          Comment("Charlie", "Very helpful", Instant.now())
        ),
        publishedAt = Instant.now(),
        views = 1234
      )

      val codec = summon[BsonFormat.TypeClass[BlogPost]]
      val encoded = codec.encode(post)
      val decoded = codec.decode(java.nio.ByteBuffer.wrap(encoded))
      
      println(s"Blog post: ${post.title}")
      println(s"Encoded size: ${encoded.length} bytes")
      println(s"Decoded successfully: ${decoded.isRight}")
    }
  }

  def main(args: Array[String]): Unit = {
    println("=== BSON Examples ===\n")
    
    println("Example 1: Simple case class")
    Example1.run()
    println()
    
    println("Example 2: Nested structures")
    Example2.run()
    println()
    
    println("Example 3: Sealed traits")
    Example3.run()
    println()
    
    println("Example 4: Collections")
    Example4.run()
    println()
    
    println("Example 5: Optional fields")
    Example5.run()
    println()
    
    println("Example 6: Time types")
    Example6.run()
    println()
    
    println("Example 7: Complex nested structure")
    Example7.run()
  }
}
