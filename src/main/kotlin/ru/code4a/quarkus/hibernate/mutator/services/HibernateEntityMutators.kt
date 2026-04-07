package ru.code4a.quarkus.hibernate.mutator.services

import AssociationsLoader
import ru.code4a.quarkus.hibernate.mutator.builds.utils.EntityClassesLoader
import ru.code4a.quarkus.hibernate.mutator.interfaces.EntityFieldStateInitializer
import ru.code4a.quarkus.hibernate.mutator.interfaces.EntityStateInitializer
import ru.code4a.quarkus.hibernate.mutator.models.ClassNameWithFieldName
import ru.code4a.quarkus.hibernate.mutator.models.ClassWithFieldName
import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityCollectionMutator
import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityRefMutator
import ru.code4a.quarkus.hibernate.mutator.services.mutators.build.AssociationProcessor
import ru.code4a.quarkus.hibernate.mutator.services.mutators.build.MutatorFactory
import java.lang.reflect.Field

object HibernateEntityMutators {
  val entityCollectionMutators: Map<ClassNameWithFieldName, HibernateEntityCollectionMutator>
  val entityRefMutators: Map<ClassNameWithFieldName, HibernateEntityRefMutator>
  val entityInitializers: Map<Class<*>, EntityStateInitializer>

  init {
    val associationsLoader = AssociationsLoader()
    val associations = associationsLoader.loadAssociations()

    val processor = AssociationProcessor()
    val processedAssociations = processor.process(associations)

    val factory = MutatorFactory()
    val (collectionMutators, refMutators, fieldInitializers) = factory.createMutators(processedAssociations)

    val entityClasses =
      (
        EntityClassesLoader().loadEntityClasses() +
          fieldInitializers.keys.map { it.clazz }
        ).distinct()

    entityCollectionMutators = expandMutators(collectionMutators, entityClasses)
    entityRefMutators = expandMutators(refMutators, entityClasses)
    entityInitializers = createEntityInitializers(fieldInitializers, entityClasses)
  }

  private fun <T> expandMutators(
    source: Map<ClassNameWithFieldName, T>,
    entityClasses: List<Class<*>>
  ): Map<ClassNameWithFieldName, T> {
    val expanded = LinkedHashMap(source)
    val classByName = entityClasses.associateBy { it.name }
    val sourceEntries = source.entries.toList()

    for (entityClass in entityClasses) {
      for ((key, mutator) in sourceEntries) {
        val declaringClass = classByName[key.className] ?: continue

        if (!declaringClass.isAssignableFrom(entityClass)) {
          continue
        }

        val actualField = findFieldInHierarchy(entityClass, key.fieldName) ?: continue

        if (actualField.declaringClass != declaringClass) {
          continue
        }

        expanded.putIfAbsent(
          ClassNameWithFieldName(entityClass.name, key.fieldName),
          mutator
        )
      }
    }

    return expanded
  }

  private fun createEntityInitializers(
    fieldInitializers: Map<ClassWithFieldName, EntityFieldStateInitializer>,
    entityClasses: List<Class<*>>
  ): Map<Class<*>, EntityStateInitializer> {
    val initializersByDeclaringClass =
      fieldInitializers.entries.groupBy { it.key.clazz }

    return entityClasses
      .mapNotNull { entityClass ->
        val initializers =
          classHierarchy(entityClass)
            .asReversed()
            .flatMap { currentClass ->
              initializersByDeclaringClass[currentClass].orEmpty()
                .filter { entry ->
                  findFieldInHierarchy(entityClass, entry.key.fieldName)?.declaringClass == currentClass
                }
                .map { it.value }
            }

        if (initializers.isEmpty()) {
          null
        } else {
          entityClass to EntityStateInitializer(initializers)
        }
      }
      .toMap()
  }

  private fun classHierarchy(clazz: Class<*>): List<Class<*>> {
    val result = mutableListOf<Class<*>>()
    var current: Class<*>? = clazz

    while (current != null && current != Any::class.java) {
      result += current
      current = current.superclass
    }

    return result
  }

  private fun findFieldInHierarchy(clazz: Class<*>, fieldName: String): Field? {
    var current: Class<*>? = clazz

    while (current != null && current != Any::class.java) {
      current.declaredFields.firstOrNull { it.name == fieldName }?.let { return it }
      current = current.superclass
    }

    return null
  }
}
