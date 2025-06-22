package ru.code4a.quarkus.hibernate.mutator.services.mutators.build.initializers

import org.hibernate.Hibernate
import ru.code4a.quarkus.hibernate.mutator.interfaces.EntityFieldStateInitializer
import ru.code4a.quarkus.hibernate.mutator.utils.hibernate.HibernateFieldAccessor

/**
 * Initializer for bidirectional ManyToOne relationships
 */
internal class BidirectionalManyToOneInitializer(
  private val manyAccessor: HibernateFieldAccessor,
  private val oneAccessor: HibernateFieldAccessor
) : EntityFieldStateInitializer {

  override fun initialize(entity: Any) {
    val value = manyAccessor.get(entity) ?: return

    if (Hibernate.isInitialized(value)) {
      val collection = oneAccessor.get(value) as MutableSet<Any>
      if (Hibernate.isInitialized(collection)) {
        collection.add(entity)
      }
    }
  }
}
