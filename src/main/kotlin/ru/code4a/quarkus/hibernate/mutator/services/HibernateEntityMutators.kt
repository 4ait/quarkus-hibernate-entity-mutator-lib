package ru.code4a.quarkus.hibernate.mutator.services

import AssociationsLoader
import ru.code4a.quarkus.hibernate.mutator.interfaces.EntityFieldStateInitializer
import ru.code4a.quarkus.hibernate.mutator.interfaces.EntityStateInitializer
import ru.code4a.quarkus.hibernate.mutator.models.ClassNameWithFieldName
import ru.code4a.quarkus.hibernate.mutator.models.ClassWithFieldName
import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityCollectionMutator
import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityRefMutator
import ru.code4a.quarkus.hibernate.mutator.services.mutators.build.AssociationProcessor
import ru.code4a.quarkus.hibernate.mutator.services.mutators.build.MutatorFactory

/**
 * Main service for managing Hibernate entity mutators.
 * Processes JPA associations and creates appropriate mutators for entity relationships.
 */
object HibernateEntityMutators {
  val entityCollectionMutators: Map<ClassNameWithFieldName, HibernateEntityCollectionMutator>
  val entityRefMutators: Map<ClassNameWithFieldName, HibernateEntityRefMutator>
  val entityInitializers: Map<Class<*>, EntityStateInitializer>

  init {
    val loader = AssociationsLoader()
    val associations = loader.loadAssociations()

    val processor = AssociationProcessor()
    val processedAssociations = processor.process(associations)

    val factory = MutatorFactory()
    val (collectionMutators, refMutators, fieldInitializers) = factory.createMutators(processedAssociations)

    entityCollectionMutators = collectionMutators
    entityRefMutators = refMutators
    entityInitializers = createEntityInitializers(fieldInitializers)
  }

  private fun createEntityInitializers(
    fieldInitializers: Map<ClassWithFieldName, EntityFieldStateInitializer>
  ): Map<Class<*>, EntityStateInitializer> {
    return fieldInitializers
      .entries
      .groupBy { it.key.clazz }
      .mapValues { (_, entries) ->
        EntityStateInitializer(entries.map { it.value })
      }
  }
}
