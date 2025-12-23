package zio.blocks.schema

import zio.test._

/**
 * Tests for Direct Mapping functionality.
 */
object DirectMappingSpec extends ZIOSpecDefault {
  
  // Test case classes
  case class PersonDTO(
    id: Long,
    name: String,
    age: Int,
    email: String
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
  
  def spec = suite("DirectMappingSpec")(
    
    test("Basic API compilation") {
      val mapper = DirectMapping.mapper
      val config = DirectMapping.config
      
      assertTrue(true) // Basic compilation test
    },
    
    test("Builder API compilation") {
      val mapperBuilder = DirectMapping.mapper
        .mapField("source", "target") { identity }
        .ignore("field")
        .withDefault("field", "value")
        .withDefaults(Map("field" -> "value"))
      val mapper = mapperBuilder.build
      
      assertTrue(true) // Builder API compilation test
    },
    
    test("Configuration API compilation") {
      val config = DirectMapping.MappingConfig()
      val mapper = new CaseClassMapper(config)
      
      assertTrue(true) // Configuration test
    },
    
    test("Error types compilation") {
      val fieldNotFound = MappingError.FieldNotFound("field", "source")
      val typeMismatch = MappingError.TypeMismatch("field", "expected", "actual")
      val transformError = MappingError.TransformError("field", "error")
      val unexpectedError = MappingError.UnexpectedError("message")
      
      assertTrue(true) // Error types compilation test
    }
  )
}