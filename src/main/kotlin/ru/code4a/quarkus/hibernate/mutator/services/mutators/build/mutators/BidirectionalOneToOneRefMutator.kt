package ru.code4a.quarkus.hibernate.mutator.services.mutators.build.mutators

import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityRefMutator
import ru.code4a.quarkus.hibernate.mutator.utils.hibernate.HibernateFieldAccessor

/**
 * Mutator for bidirectional OneToOne relationships
 */
internal class BidirectionalOneToOneRefMutator(
  private val accessor: HibernateFieldAccessor,
  private val inverseAccessor: HibernateFieldAccessor
) : HibernateEntityRefMutator {

  override fun set(entity: Any, value: Any?) {
    val currentValue = accessor.get(entity)

    if (currentValue == value) return

    // Clear old association
    if (currentValue != null) {
      inverseAccessor.set(currentValue, null)
    }

    // Set new association
    if (value != null) {
      inverseAccessor.set(value, entity)
    }

    accessor.set(entity, value)
  }
}
