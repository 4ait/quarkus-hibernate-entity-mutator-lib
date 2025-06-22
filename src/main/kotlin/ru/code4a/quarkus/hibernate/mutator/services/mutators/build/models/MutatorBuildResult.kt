package ru.code4a.quarkus.hibernate.mutator.services.mutators.build.models

import ru.code4a.quarkus.hibernate.mutator.interfaces.EntityFieldStateInitializer
import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityCollectionMutator
import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityRefMutator

/**
 * Data class representing the result of building mutators
 */
internal data class MutatorBuildResult(
  val collectionMutator: HibernateEntityCollectionMutator? = null,
  val refMutator: HibernateEntityRefMutator? = null,
  val initializer: EntityFieldStateInitializer
)
