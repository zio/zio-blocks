package zio.blocks.schema

import zio.test._

object DirectMappingSpec extends ZIOSpecDefault {
  
  case class PersonDTO(
    id: Long,
    name: String,
    age: Int,
    email: String
  )
  
  case class PersonEntity(
    personId: Long,
    fullName: String,
    yearsOld: Int,
    emailAddress: String
  )
  
  case class SimplePerson(
    name: String,
    age: Int
  )
  
  case class PersonApp(
    id: Long,
    name: String,
    age: Int,
    email: String,
    createdAt: java.time.LocalDateTime
  )
  
  case class ProductDTO(
    id: Long,
    title: String,
    price: Double,
    category: String
  )
  
  case class ProductEntity(
    productId: Long,
    name: String,
    cost: Double,
    `type`: String,
    createdAt: java.time.LocalDateTime
  )
  
  // Nested case classes for tests
  case class Address(
    street: String,
    city: String,
    zipCode: String,
    country: String
  )
  
  case class ContactInfo(
    email: String,
    phone: String,
    address: Address
  )
  
  case class PersonNestedDTO(
    id: Long,
    name: String,
    age: Int,
    contact: ContactInfo
  )
  
  case class PersonNestedEntity(
    personId: Long,
    fullName: String,
    yearsOld: Int,
    contactInfo: ContactInfo,
    isActive: Boolean = true,
    accountBalance: BigDecimal = BigDecimal(0)
  )
  
  // Test for advanced type conversions
  case class TypeConversionDTO(
    stringValue: String,
    intValue: Int,
    longValue: Long,
    doubleValue: Double,
    bigDecimalValue: String,
    booleanValue: String,
    nullableValue: String
  )
  
  case class TypeConversionEntity(
    stringValue: String,
    intValue: Int,
    longValue: Long,
    doubleValue: Double,
    bigDecimalValue: BigDecimal,
    booleanValue: Boolean,
    nullableValue: Option[String]
  )
  
  // Large case class for performance testing
  case class LargeEntity(
    field1: String, field2: String, field3: String, field4: String, field5: String,
    field6: Int, field7: Int, field8: Int, field9: Int, field10: Int,
    field11: Long, field12: Long, field13: Long, field14: Long, field15: Long,
    field16: Double, field17: Double, field18: Double, field19: Double, field20: Double
  )
  
  case class LargeDTO(
    field1: String, field2: String, field3: String, field4: String, field5: String,
    field6: Int, field7: Int, field8: Int, field9: Int, field10: Int,
    field11: Long, field12: Long, field13: Long, field14: Long, field15: Long,
    field16: Double, field17: Double, field18: Double, field19: Double, field20: Double
  )
  
  implicit val personDTOSchema: Schema[PersonDTO] = Schema.derived
  implicit val personEntitySchema: Schema[PersonEntity] = Schema.derived
  implicit val simplePersonSchema: Schema[SimplePerson] = Schema.derived
  implicit val personAppSchema: Schema[PersonApp] = Schema.derived
  implicit val productDTOSchema: Schema[ProductDTO] = Schema.derived
  implicit val productEntitySchema: Schema[ProductEntity] = Schema.derived
  implicit val addressSchema: Schema[Address] = Schema.derived
  implicit val contactInfoSchema: Schema[ContactInfo] = Schema.derived
  implicit val personNestedDTOSchema: Schema[PersonNestedDTO] = Schema.derived
  implicit val personNestedEntitySchema: Schema[PersonNestedEntity] = Schema.derived
  implicit val typeConversionDTOSchema: Schema[TypeConversionDTO] = Schema.derived
  implicit val typeConversionEntitySchema: Schema[TypeConversionEntity] = Schema.derived
  implicit val largeEntitySchema: Schema[LargeEntity] = Schema.derived
  implicit val largeDTOSchema: Schema[LargeDTO] = Schema.derived
  
  def spec = suite("DirectMappingSpec")(
    
    test("Basic field mapping with same field names") {
      val source = PersonDTO(1L, "John Doe", 30, "john@example.com")
      val mapper = DirectMapping.mapper.build
      
      for {
        result <- mapper.map[PersonDTO, SimplePerson](source)
      } yield assertTrue(
        result.name == "John Doe" &&
        result.age == 30
      )
    },
    
    test("Field mapping with different names") {
      val source = PersonDTO(1L, "Alice Smith", 28, "alice@example.com")
      
      val mapper = DirectMapping.mapper
        .mapField("id", "personId")(identity)
        .mapField("name", "fullName")(identity)
        .mapField("age", "yearsOld")(identity)
        .mapField("email", "emailAddress")(identity)
        .build
      
      for {
        result <- mapper.map[PersonDTO, PersonEntity](source)
      } yield assertTrue(
        result.personId == 1L &&
        result.fullName == "Alice Smith" &&
        result.yearsOld == 28 &&
        result.emailAddress == "alice@example.com"
      )
    },
    
    test("Field mapping with transformations") {
      val source = PersonDTO(1L, "Bob Johnson", 35, "bob@example.com")
      
      val mapper = DirectMapping.mapper
        .mapField("name", "fullName") { name =>
          name.toString.toUpperCase
        }
        .mapField("email", "emailAddress") { email =>
          email.toString.replace("@", "[at]")
        }
        .mapField("age", "yearsOld") { age =>
          age.asInstanceOf[Int] * 2
        }
        .build
      
      for {
        result <- mapper.map[PersonDTO, PersonEntity](source)
      } yield assertTrue(
        result.fullName == "BOB JOHNSON" &&
        result.emailAddress == "bob[at]example.com" &&
        result.yearsOld == 70
      )
    },
    
    test("Mapping with default values") {
      val source = PersonDTO(1L, "Diana Prince", 32, "diana@example.com")
      
      val mapper = DirectMapping.mapper
        .withDefault("createdAt", java.time.LocalDateTime.of(2024, 1, 1, 0, 0))
        .build
      
      for {
        result <- mapper.map[PersonDTO, PersonApp](source)
      } yield assertTrue(
        result.id == 1L &&
        result.name == "Diana Prince" &&
        result.age == 32 &&
        result.email == "diana@example.com" &&
        result.createdAt == java.time.LocalDateTime.of(2024, 1, 1, 0, 0)
      )
    },
    
    test("Mapping with multiple default values") {
      val source = ProductDTO(2L, "Laptop", 999.99, "Electronics")
      
      val mapper = DirectMapping.mapper
        .mapField("id", "productId")(identity)
        .mapField("title", "name")(identity)
        .mapField("price", "cost")(identity)
        .mapField("category", "type")(identity)
        .withDefaults(Map(
          "createdAt" -> java.time.LocalDateTime.of(2024, 12, 23, 12, 0, 0)
        ))
        .build
      
      for {
        result <- mapper.map[ProductDTO, ProductEntity](source)
        expectedTime = java.time.LocalDateTime.of(2024, 12, 23, 12, 0, 0)
      } yield assertTrue(
        result.productId == 2L &&
        result.name == "Laptop" &&
        result.cost == 999.99 &&
        result.`type` == "Electronics" &&
        result.createdAt == expectedTime
      )
    },
    
    test("Collection mapping") {
      val sources = List(
        PersonDTO(1L, "Person 1", 25, "person1@example.com"),
        PersonDTO(2L, "Person 2", 30, "person2@example.com"),
        PersonDTO(3L, "Person 3", 35, "person3@example.com")
      )
      
      val mapper = DirectMapping.mapper.build
      
      for {
        results <- mapper.mapMany[PersonDTO, SimplePerson](sources)
      } yield assertTrue(
        results.length == 3 &&
        results(0).name == "Person 1" &&
        results(1).name == "Person 2" &&
        results(2).name == "Person 3" &&
        results.forall(_.age > 0)
      )
    },
    
    test("Type conversion during mapping") {
      val source = PersonDTO(1L, "Charlie Brown", 40, "charlie@example.com")
      
      val mapper = DirectMapping.mapper
        .mapField("id", "personId") { id =>
          id.asInstanceOf[Long].toString
        }
        .mapField("age", "yearsOld") { age =>
          age.asInstanceOf[Int].toDouble
        }
        .build
      
      for {
        result <- mapper.map[PersonDTO, PersonEntity](source)
      } yield assertTrue(
        result.personId == 1L &&
        result.yearsOld == 40.0
      )
    },
    
    test("Error handling - missing field without default") {
      val source = PersonDTO(1L, "Eve Wilson", 27, "eve@example.com")
      
      
      val mapper = DirectMapping.mapper.build
      
      for {
        result <- mapper.map[PersonDTO, PersonApp](source)
      } yield result match {
        case Left(MappingError.FieldNotFound(field, _)) =>
          assertTrue(field == "createdAt")
        case _ =>
          throw new RuntimeException("Should have failed with FieldNotFound error")
      }
    },
    
    test("Error handling - transform error") {
      val source = PersonDTO(1L, "Frank Miller", 45, "frank@example.com")
      
      val mapper = DirectMapping.mapper
        .mapField("name", "fullName") { name =>
          throw new RuntimeException("Transform failed")
          name
        }
        .build
      
      for {
        result <- mapper.map[PersonDTO, PersonEntity](source)
      } yield result match {
        case Left(MappingError.UnexpectedError(_, _)) =>
          assertTrue(true)
        case _ =>
          throw new RuntimeException("Should have failed with UnexpectedError")
      }
    },
    
    test("Chained mapping operations") {
      val source = PersonDTO(1L, "Grace Lee", 29, "grace@example.com")
      
      val mapper = DirectMapping.mapper
        .mapField("name", "fullName") { name =>
          name.toString.trim
        }
        .mapField("age", "yearsOld") { age =>
          age.asInstanceOf[Int] + 1
        }
        .withDefault("emailAddress", "default@example.com")
        .build
      
      for {
        result <- mapper.map[PersonDTO, PersonEntity](source)
      } yield assertTrue(
        result.fullName == "Grace Lee" &&
        result.yearsOld == 30 &&
        result.emailAddress == "grace@example.com"
      )
    },
    
    test("Empty collection mapping") {
      val sources = List.empty[PersonDTO]
      val mapper = DirectMapping.mapper.build
      
      for {
        results <- mapper.mapMany[PersonDTO, SimplePerson](sources)
      } yield assertTrue(results.isEmpty)
    },
    
    test("Single field mapping") {
      val source = PersonDTO(1L, "Henry Davis", 33, "henry@example.com")
      
      val mapper = DirectMapping.mapper
        .mapField("name", "name")(identity)
        .build
      
      for {
        result <- mapper.map[PersonDTO, SimplePerson](source)
      } yield assertTrue(
        result.name == "Henry Davis" &&
        result.age == 0
      )
    },
    
    // ============ NEW TESTS FOR ADVANCED FUNCTIONALITIES ============
    
    test("Nested case class mapping") {
      val address = Address("123 Main St", "Anytown", "12345", "USA")
      val contact = ContactInfo("john@example.com", "555-1234", address)
      val source = PersonNestedDTO(1L, "John Doe", 30, contact)
      
      val mapper = DirectMapping.mapper
        .mapField("id", "personId")(identity)
        .mapField("name", "fullName")(identity)
        .mapField("age", "yearsOld")(identity)
        .mapField("contact", "contactInfo")(identity)
        .build
      
      for {
        result <- mapper.map[PersonNestedDTO, PersonNestedEntity](source)
      } yield assertTrue(
        result.personId == 1L &&
        result.fullName == "John Doe" &&
        result.yearsOld == 30 &&
        result.contactInfo.email == "john@example.com" &&
        result.contactInfo.address.street == "123 Main St"
      )
    },
    
    test("Nested case class with automatic defaults") {
      val address = Address("456 Oak Ave", "Somewhere", "54321", "Canada")
      val contact = ContactInfo("jane@example.com", "555-5678", address)
      val source = PersonNestedDTO(2L, "Jane Smith", 25, contact)
      
      val mapper = DirectMapping.mapper
        .mapField("id", "personId")(identity)
        .mapField("name", "fullName")(identity)
        .mapField("age", "yearsOld")(identity)
        .mapField("contact", "contactInfo")(identity)
        .build
      
      for {
        result <- mapper.map[PersonNestedDTO, PersonNestedEntity](source)
      } yield assertTrue(
        result.isActive == true &&
        result.accountBalance == BigDecimal(0)
      )
    },
    
    test("Advanced type conversions") {
      val source = TypeConversionDTO(
        "test-string",
        42,
        123456789L,
        3.14159,
        "123.45",
        "true",
        "some-value"
      )
      
      val mapper = DirectMapping.mapper.build
      
      for {
        result <- mapper.map[TypeConversionDTO, TypeConversionEntity](source)
      } yield assertTrue(
        result.stringValue == "test-string" &&
        result.intValue == 42 &&
        result.longValue == 123456789L &&
        result.doubleValue == 3.14159 &&
        result.bigDecimalValue == BigDecimal("123.45") &&
        result.booleanValue == true &&
        result.nullableValue == Some("some-value")
      )
    },
    
    test("String to number conversions") {
      val source = TypeConversionDTO(
        "100",
        50,
        999999L,
        2.71828,
        "999.999",
        "false",
        null
      )
      
      val mapper = DirectMapping.mapper.build
      
      for {
        result <- mapper.map[TypeConversionDTO, TypeConversionEntity](source)
      } yield assertTrue(
        result.intValue == 100 &&
        result.longValue == 999999L &&
        result.doubleValue == 2.71828 &&
        result.bigDecimalValue == BigDecimal("999.999") &&
        result.booleanValue == false &&
        result.nullableValue == None
      )
    },
    
    test("Enhanced error handling - type mismatch") {
      val source = TypeConversionDTO(
        "not-a-number",
        42,
        123L,
        3.14,
        "invalid-decimal",
        "yes",
        "value"
      )
      
      val mapper = DirectMapping.mapper.build
      
      for {
        result <- mapper.map[TypeConversionDTO, TypeConversionEntity](source)
      } yield result match {
        case Left(MappingError.TypeMismatch("conversion", "Int", "String")) =>
          assertTrue(true)
        case Left(MappingError.TypeMismatch("conversion", "BigDecimal", "String")) =>
          assertTrue(true)
        case _ =>
          throw new RuntimeException("Should have failed with type mismatch errors")
      }
    },
    
    test("Batch processing optimization") {
      val sources = List.tabulate(150) { i =>
        PersonDTO(i.toLong, s"Person $i", 20 + i, s"person$i@example.com")
      }
      
      val mapper = DirectMapping.mapper.build
      
      for {
        results <- mapper.mapMany[PersonDTO, SimplePerson](sources)
      } yield assertTrue(
        results.length == 150 &&
        results.head.name == "Person 0" &&
        results.last.name == "Person 149" &&
        results.forall(_.age >= 20)
      )
    },
    
    test("Large entity mapping performance") {
      val source = LargeDTO(
        "field1", "field2", "field3", "field4", "field5",
        1, 2, 3, 4, 5,
        10L, 20L, 30L, 40L, 50L,
        1.1, 2.2, 3.3, 4.4, 5.5
      )
      
      val mapper = DirectMapping.mapper.build
      
      for {
        result <- mapper.map[LargeDTO, LargeEntity](source)
      } yield assertTrue(
        result.field1 == "field1" &&
        result.field5 == "field5" &&
        result.field6 == 1 &&
        result.field10 == 5 &&
        result.field11 == 10L &&
        result.field15 == 50L &&
        result.field16 == 1.1 &&
        result.field20 == 5.5
      )
    },
    
    test("Cache effectiveness with repeated mappings") {
      val source1 = PersonDTO(1L, "Cache Test 1", 25, "cache1@example.com")
      val source2 = PersonDTO(2L, "Cache Test 2", 30, "cache2@example.com")
      val sources = List(source1, source2)
      
      val mapper = DirectMapping.mapper.build
      
      for {
        // First mapping
        result1 <- mapper.map[PersonDTO, SimplePerson](source1)
        
        // Second mapping (should benefit from cache)
        result2 <- mapper.map[PersonDTO, SimplePerson](source2)
        
        // Batch mapping
        batchResults <- mapper.mapMany[PersonDTO, SimplePerson](sources)
      } yield assertTrue(
        result1.name == "Cache Test 1" &&
        result2.name == "Cache Test 2" &&
        batchResults.length == 2 &&
        batchResults.forall(_.age > 0)
      )
    },
    
    test("Nested case class with missing fields and defaults") {
      val address = Address("789 Pine St", "Elsewhere", "67890", "UK")
      val contact = ContactInfo("incomplete@example.com", null, address)
      val source = PersonNestedDTO(3L, "Incomplete Person", 35, contact)
      
      val mapper = DirectMapping.mapper
        .mapField("id", "personId")(identity)
        .mapField("name", "fullName")(identity)
        .mapField("age", "yearsOld")(identity)
        .mapField("contact", "contactInfo")(identity)
        .withDefault("isActive", false)
        .withDefault("accountBalance", BigDecimal(1000.50))
        .build
      
      for {
        result <- mapper.map[PersonNestedDTO, PersonNestedEntity](source)
      } yield assertTrue(
        result.personId == 3L &&
        result.fullName == "Incomplete Person" &&
        result.yearsOld == 35 &&
        result.isActive == false &&
        result.accountBalance == BigDecimal(1000.50)
      )
    },
    
    test("Complex transformation with nested case classes") {
      val address = Address("321 Elm St", "Transform City", "11111", "Australia")
      val contact = ContactInfo("transform@example.com", "555-TEST", address)
      val source = PersonNestedDTO(4L, "Transform Test", 40, contact)
      
      val mapper = DirectMapping.mapper
        .mapField("id", "personId") { id => 
          id.asInstanceOf[Long] * 100 // Transform ID
        }
        .mapField("name", "fullName") { name => 
          name.toString.toUpperCase + " (TRANSFORMED)"
        }
        .mapField("age", "yearsOld") { age => 
          age.asInstanceOf[Int] + 10
        }
        .mapField("contact.email", "contactInfo.email") { email =>
          email.toString.replace("@", "[at]")
        }
        .build
      
      for {
        result <- mapper.map[PersonNestedDTO, PersonNestedEntity](source)
      } yield assertTrue(
        result.personId == 400L &&
        result.fullName == "TRANSFORM TEST (TRANSFORMED)" &&
        result.yearsOld == 50 &&
        result.contactInfo.email == "transform[at]example.com"
      )
    }
  )
}