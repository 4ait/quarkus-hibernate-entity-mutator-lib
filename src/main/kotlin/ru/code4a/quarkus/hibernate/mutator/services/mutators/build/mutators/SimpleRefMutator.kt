package ru.code4a.quarkus.hibernate.mutator.services.mutators.build.mutators

import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityRefMutator
import ru.code4a.quarkus.hibernate.mutator.utils.hibernate.HibernateFieldAccessor

/**
 * Simple mutator for unidirectional reference fields
 */
internal class SimpleRefMutator(
  private val accessor: HibernateFieldAccessor
) : HibernateEntityRefMutator {

  override fun set(entity: Any, value: Any?) {
    accessor.set(entity, value)
  }
}
