package zio.blocks.schema

/**
 * DirectMapping provides functionality to map directly between case classes
 * without going through intermediate representations like DynamicValue.
 */
object DirectMapping {
  
  /**
   * Represents a field mapping between source and target case classes.
   */
  case class FieldMapping(
    sourceField: String,
    targetField: String,
    transform: Option[Any => Any] = None
  )
  
  /**
   * Configuration for mapping between two case classes.
   */
  case class MappingConfig(
    explicitMappings: Vector[FieldMapping] = Vector.empty,
    ignoreFields: Set[String] = Set.empty,
    defaultValues: Map[String, Any] = Map.empty
  )
  
  /**
   * Builder for creating mappings between case classes.
   */
  class MapperBuilder(private val config: MappingConfig) {
    
    def mapField(sourceField: String, targetField: String)(transform: Any => Any = identity): MapperBuilder = {
      new MapperBuilder(config.copy(
        explicitMappings = config.explicitMappings :+ FieldMapping(sourceField, targetField, Some(transform))
      ))
    }
    
    def ignore(field: String): MapperBuilder = {
      new MapperBuilder(config.copy(ignoreFields = config.ignoreFields + field))
    }
    
    def withDefault(field: String, value: Any): MapperBuilder = {
      new MapperBuilder(config.copy(defaultValues = config.defaultValues + (field -> value)))
    }
    
    def withDefaults(values: Map[String, Any]): MapperBuilder = {
      new MapperBuilder(config.copy(defaultValues = config.defaultValues ++ values))
    }
    
    def build: CaseClassMapper = {
      new CaseClassMapper(config)
    }
  }
  
  /**
   * Creates a mapper builder for mapping from type A to type B.
   */
  def mapper: MapperBuilder = new MapperBuilder(MappingConfig())
  
  /**
   * Creates a default mapping configuration.
   */
  def config: MappingConfig = MappingConfig()
}