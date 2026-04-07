package ru.code4a.quarkus.hibernate.mutator.builds.utils

import kotlinx.serialization.json.Json
import ru.code4a.quarkus.hibernate.mutator.builds.FindAllHibernateAssociationsInfoBuildStep.Companion.ENTITY_CLASSES_JSON_RESOURCE_PATH
import ru.code4a.quarkus.hibernate.mutator.utils.nullable.unwrapElseError

internal class EntityClassesLoader {
  fun loadEntityClasses(): List<Class<*>> {
    val classLoader = Thread.currentThread().contextClassLoader

    val resourceContent =
      classLoader
        .getResource(ENTITY_CLASSES_JSON_RESOURCE_PATH)
        .unwrapElseError { "Cannot find resource $ENTITY_CLASSES_JSON_RESOURCE_PATH" }
        .readText()

    val rawEntityClasses: List<String> =
      Json.decodeFromString(resourceContent)

    return rawEntityClasses
      .distinct()
      .map(classLoader::loadClass)
  }
}
