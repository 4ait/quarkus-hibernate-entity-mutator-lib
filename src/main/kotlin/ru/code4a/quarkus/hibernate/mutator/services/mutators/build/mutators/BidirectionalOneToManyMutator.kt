package ru.code4a.quarkus.hibernate.mutator.services.mutators.build.mutators

import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityCollectionMutator
import ru.code4a.quarkus.hibernate.mutator.services.inverse.InverseCollectionSynchronizer
import ru.code4a.quarkus.hibernate.mutator.utils.hibernate.HibernateFieldAccessor
import ru.code4a.quarkus.hibernate.mutator.utils.hibernate.HibernateLazyState

/**
 * Mutator for bidirectional OneToMany relationships.
 *
 * [add] and [remove] change the child reference and keep the collection in line without loading it
 * (see [InverseCollectionSynchronizer]). [set] replaces the whole collection, so it has to know
 * the current elements and loads the collection when it is not loaded yet.
 */
internal class BidirectionalOneToManyMutator(
  private val ownerAccessor: HibernateFieldAccessor,
  private val inverseAccessor: HibernateFieldAccessor,
  private val inverseCollections: InverseCollectionSynchronizer
) : HibernateEntityCollectionMutator {

  override fun set(entity: Any, values: Collection<Any>) {
    val currentCollection = getCollection(entity)
    val newValues = values.toSet()

    // Remove elements not in new collection
    val toRemove = currentCollection.filterNot { it in newValues }
    toRemove.forEach { element ->
      inverseAccessor.set(element, null)
    }

    // Add new elements
    val toAdd = newValues.filterNot { it in currentCollection }
    toAdd.forEach { element ->
      validateNotAssociated(element, entity)
      inverseAccessor.set(element, entity)
    }

    // Update collection
    if (toRemove.isNotEmpty()) {
      currentCollection.removeAll(toRemove.toSet())
    }
    if (toAdd.isNotEmpty()) {
      currentCollection.addAll(toAdd)
    }
  }

  override fun rawSet(entity: Any, values: Collection<Any>) {
    ownerAccessor.set(entity, values)
  }

  override fun remove(entity: Any, value: Any) {
    if (!HibernateLazyState.refersTo(inverseAccessor.get(value), entity)) {
      throw IllegalStateException("Entity is not associated with this collection")
    }

    inverseAccessor.set(value, null)
    inverseCollections.removeChild(owner = entity, child = value)
  }

  override fun add(entity: Any, value: Any) {
    validateNotAssociated(value, entity)

    inverseAccessor.set(value, entity)
    inverseCollections.addChild(owner = entity, child = value)
  }

  private fun validateNotAssociated(element: Any, expectedOwner: Any) {
    val currentOwner = inverseAccessor.get(element)

    if (currentOwner != null && !HibernateLazyState.refersTo(currentOwner, expectedOwner)) {
      throw IllegalStateException("Entity is already associated with another entity")
    }
  }

  private fun getCollection(entity: Any): MutableSet<Any> {
    @Suppress("UNCHECKED_CAST")
    return ownerAccessor.get(entity) as MutableSet<Any>
  }
}
