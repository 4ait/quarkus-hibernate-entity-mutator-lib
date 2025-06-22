package ru.code4a.quarkus.hibernate.mutator.services.mutators.build.initializers

import ru.code4a.quarkus.hibernate.mutator.interfaces.EntityFieldStateInitializer
import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityCollectionMutator
import ru.code4a.quarkus.hibernate.mutator.utils.hibernate.HibernateFieldAccessor

/**
 * Initializer for bidirectional OneToMany relationships
 */
internal class BidirectionalOneToManyInitializer(
  private val accessor: HibernateFieldAccessor,
  private val mutator: HibernateEntityCollectionMutator
) : EntityFieldStateInitializer {

  override fun initialize(entity: Any) {
    val collection = accessor.get(entity) as MutableSet<Any>

    if (collection.isNotEmpty()) {
      // Re-initialize a collection to ensure bidirectional consistency
      accessor.set(entity, mutableSetOf<Any>())
      mutator.set(entity, collection)
    }
  }
}
