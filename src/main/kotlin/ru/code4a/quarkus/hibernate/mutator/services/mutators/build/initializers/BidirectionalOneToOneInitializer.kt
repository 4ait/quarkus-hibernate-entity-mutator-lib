package ru.code4a.quarkus.hibernate.mutator.services.mutators.build.initializers

import ru.code4a.quarkus.hibernate.mutator.interfaces.EntityFieldStateInitializer
import ru.code4a.quarkus.hibernate.mutator.utils.hibernate.HibernateFieldAccessor
import ru.code4a.quarkus.hibernate.mutator.utils.hibernate.HibernateLazyState

/**
 * Initializer for bidirectional OneToOne relationships: the partner of a new entity points back to it.
 * A partner that is not loaded is loaded first (one row), see [HibernateLazyState.loadForWrite].
 */
internal class BidirectionalOneToOneInitializer(
  private val accessor: HibernateFieldAccessor,
  private val inverseAccessor: HibernateFieldAccessor
) : EntityFieldStateInitializer {

  override fun initialize(entity: Any) {
    val value = accessor.get(entity) ?: return
    inverseAccessor.set(HibernateLazyState.loadForWrite(value), entity)
  }
}
