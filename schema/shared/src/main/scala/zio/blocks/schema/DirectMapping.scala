package zio.blocks.schema

/**
 * DirectMapping provides functionality to map directly between case classes
 * without going through intermediate representations like DynamicValue.
 */
object DirectMapping {
  
  case class FieldMapping(
    sourceField: String,
    targetField: String,
    transform: Option[Any => Any] = None
  )
  
  case class MappingConfig(
    explicitMappings: Vector[FieldMapping] = Vector.empty,
    ignoreFields: Set[String] = Set.empty,
    defaultValues: Map[String, Any] = Map.empty
  )
  
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
  
  def mapper: MapperBuilder = new MapperBuilder(MappingConfig())
  
  def config: MappingConfig = MappingConfig()
}