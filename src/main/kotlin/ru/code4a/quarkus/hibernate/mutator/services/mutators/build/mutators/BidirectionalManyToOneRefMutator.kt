package ru.code4a.quarkus.hibernate.mutator.services.mutators.build.mutators

import org.hibernate.Hibernate
import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityRefMutator
import ru.code4a.quarkus.hibernate.mutator.utils.hibernate.HibernateFieldAccessor

/**
 * Mutator for bidirectional ManyToOne relationships
 */
internal class BidirectionalManyToOneRefMutator(
  private val manyAccessor: HibernateFieldAccessor,
  private val oneAccessor: HibernateFieldAccessor
) : HibernateEntityRefMutator {

  override fun set(entity: Any, value: Any?) {
    val currentValue = manyAccessor.get(entity)

    if (currentValue == value) return

    // Remove from old collection
    if (currentValue != null) {
      val oldCollection = oneAccessor.get(currentValue) as MutableSet<Any>
      if (Hibernate.isInitialized(oldCollection)) {
        oldCollection.remove(entity)
      }
    }

    // Add to new collection
    if (value != null) {
      val newCollection = oneAccessor.get(value) as MutableSet<Any>
      if (Hibernate.isInitialized(newCollection)) {
        newCollection.add(entity)
      }
    }

    manyAccessor.set(entity, value)
  }
}
