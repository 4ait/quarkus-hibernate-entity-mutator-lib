package ru.code4a.quarkus.hibernate.mutator.services.mutators.build.initializers

import ru.code4a.quarkus.hibernate.mutator.interfaces.EntityFieldStateInitializer
import ru.code4a.quarkus.hibernate.mutator.services.inverse.InverseCollectionSynchronizer
import ru.code4a.quarkus.hibernate.mutator.utils.hibernate.HibernateFieldAccessor

/**
 * Initializer for bidirectional ManyToOne relationships: a new child joins the inverse collection
 * of its owner, even when that collection is not loaded yet (see [InverseCollectionSynchronizer]).
 */
internal class BidirectionalManyToOneInitializer(
  private val manyAccessor: HibernateFieldAccessor,
  private val inverseCollections: InverseCollectionSynchronizer
) : EntityFieldStateInitializer {

  override fun initialize(entity: Any) {
    val owner = manyAccessor.get(entity) ?: return

    inverseCollections.addChild(owner = owner, child = entity)
  }
}
