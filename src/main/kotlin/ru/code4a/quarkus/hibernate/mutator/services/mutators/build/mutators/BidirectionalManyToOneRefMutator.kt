package ru.code4a.quarkus.hibernate.mutator.services.mutators.build.mutators

import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityRefMutator
import ru.code4a.quarkus.hibernate.mutator.services.inverse.InverseCollectionSynchronizer
import ru.code4a.quarkus.hibernate.mutator.utils.hibernate.HibernateFieldAccessor

/**
 * Mutator for bidirectional ManyToOne relationships.
 *
 * Moves the child from the inverse collection of the previous owner to the collection of the new one.
 * Collections that are not loaded are not loaded for this; see [InverseCollectionSynchronizer].
 */
internal class BidirectionalManyToOneRefMutator(
  private val manyAccessor: HibernateFieldAccessor,
  private val inverseCollections: InverseCollectionSynchronizer
) : HibernateEntityRefMutator {

  override fun set(entity: Any, value: Any?) {
    val currentValue = manyAccessor.get(entity)

    if (currentValue === value) return

    if (currentValue != null) {
      inverseCollections.removeChild(owner = currentValue, child = entity)
    }

    if (value != null) {
      inverseCollections.addChild(owner = value, child = entity)
    }

    manyAccessor.set(entity, value)
  }
}
