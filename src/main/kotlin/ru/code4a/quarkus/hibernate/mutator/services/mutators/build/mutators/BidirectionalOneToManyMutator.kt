package ru.code4a.quarkus.hibernate.mutator.services.mutators.build.mutators

import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityCollectionMutator
import ru.code4a.quarkus.hibernate.mutator.utils.hibernate.HibernateFieldAccessor

/**
 * Mutator for bidirectional OneToMany relationships
 */
internal class BidirectionalOneToManyMutator(
  private val ownerAccessor: HibernateFieldAccessor,
  private val inverseAccessor: HibernateFieldAccessor
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
    if (inverseAccessor.get(value) == entity) {
      inverseAccessor.set(value, null)
      getCollection(entity).remove(value)
    } else {
      throw IllegalStateException("Entity is not associated with this collection")
    }
  }

  override fun add(entity: Any, value: Any) {
    val currentOwner = inverseAccessor.get(value)
    if (currentOwner != null && currentOwner != entity) {
      throw IllegalStateException("Entity is already associated with another entity")
    }

    inverseAccessor.set(value, entity)
    getCollection(entity).add(value)
  }

  private fun validateNotAssociated(element: Any, expectedOwner: Any) {
    val currentOwner = inverseAccessor.get(element)
    if (currentOwner != null && currentOwner != expectedOwner) {
      throw IllegalStateException("Entity is already associated with another entity")
    }
  }

  private fun getCollection(entity: Any): MutableSet<Any> {
    return ownerAccessor.get(entity) as MutableSet<Any>
  }
}
