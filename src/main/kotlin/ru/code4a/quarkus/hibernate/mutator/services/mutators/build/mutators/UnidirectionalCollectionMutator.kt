package ru.code4a.quarkus.hibernate.mutator.services.mutators.build.mutators

import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityCollectionMutator
import ru.code4a.quarkus.hibernate.mutator.utils.hibernate.HibernateFieldAccessor

/**
 * Simple mutator for unidirectional collections
 */
internal class UnidirectionalCollectionMutator(
  private val accessor: HibernateFieldAccessor
) : HibernateEntityCollectionMutator {

  override fun set(entity: Any, values: Collection<Any>) {
    val collection = getCollection(entity)
    collection.clear()
    collection.addAll(values)
  }

  override fun rawSet(entity: Any, values: Collection<Any>) {
    accessor.set(entity, values)
  }

  override fun remove(entity: Any, value: Any) {
    getCollection(entity).remove(value)
  }

  override fun add(entity: Any, value: Any) {
    getCollection(entity).add(value)
  }

  private fun getCollection(entity: Any): MutableSet<Any> {
    return accessor.get(entity) as MutableSet<Any>
  }
}
