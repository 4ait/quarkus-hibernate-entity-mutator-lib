package ru.code4a.quarkus.hibernate.mutator.services.mutators.build.mutators

import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityRefMutator
import ru.code4a.quarkus.hibernate.mutator.utils.hibernate.HibernateFieldAccessor
import ru.code4a.quarkus.hibernate.mutator.utils.hibernate.HibernateLazyState

/**
 * Mutator for bidirectional OneToOne relationships.
 *
 * Unlike an inverse collection, the reference of a partner that is not loaded cannot be fixed later:
 * Hibernate offers no hook after it loads such an entity. So the partner is loaded (one row) and updated
 * right away, see [HibernateLazyState.loadForWrite].
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
      inverseAccessor.set(HibernateLazyState.loadForWrite(currentValue), null)
    }

    // Set new association
    if (value != null) {
      inverseAccessor.set(HibernateLazyState.loadForWrite(value), entity)
    }

    accessor.set(entity, value)
  }
}
