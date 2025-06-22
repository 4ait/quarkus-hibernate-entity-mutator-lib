import kotlinx.serialization.json.Json
import ru.code4a.quarkus.hibernate.mutator.builds.FindAllHibernateAssociationsInfoBuildStep.Companion.ASSOCIATIONS_JSON_RESOURCE_PATH
import ru.code4a.quarkus.hibernate.mutator.builds.utils.AssociationInfo
import ru.code4a.quarkus.hibernate.mutator.models.ClassNameWithFieldName
import ru.code4a.quarkus.hibernate.mutator.utils.nullable.unwrapElseError

/**
 * Loads association information from generated resources
 */
internal class AssociationsLoader {
  fun loadAssociations(): List<AssociationInfo> {
    val classLoader = Thread.currentThread().contextClassLoader
    val resourcePath = ASSOCIATIONS_JSON_RESOURCE_PATH

    val resourceContent = classLoader
      .getResource(resourcePath)
      .unwrapElseError { "Cannot find resource $resourcePath" }
      .readText()

    val rawAssociations: List<ClassNameWithFieldName> =
      Json.decodeFromString(resourceContent)

    return rawAssociations.map { raw ->
      val clazz = classLoader.loadClass(raw.className)
      val field = clazz.declaredFields.first { it.name == raw.fieldName }
      AssociationInfo(clazz = clazz, field = field)
    }
  }
}
