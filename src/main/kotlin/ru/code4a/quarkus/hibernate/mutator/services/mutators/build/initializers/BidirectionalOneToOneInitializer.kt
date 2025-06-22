package ru.code4a.quarkus.hibernate.mutator.services.mutators.build.initializers

import ru.code4a.quarkus.hibernate.mutator.interfaces.EntityFieldStateInitializer
import ru.code4a.quarkus.hibernate.mutator.utils.hibernate.HibernateFieldAccessor

/**
 * Initializer for bidirectional OneToOne relationships
 */
internal class BidirectionalOneToOneInitializer(
  private val accessor: HibernateFieldAccessor,
  private val inverseAccessor: HibernateFieldAccessor
) : EntityFieldStateInitializer {

  override fun initialize(entity: Any) {
    val value = accessor.get(entity) ?: return
    inverseAccessor.set(value, entity)
  }
}
