package ru.code4a.quarkus.hibernate.mutator.mutators.wrappers

import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityCollectionMutator

class HibernateCollectionMutator<V : Any>(
  private val entity: Any,
  private val entityMutator: HibernateEntityCollectionMutator
) {
  fun set(values: Collection<V>) {
    entityMutator.set(entity, values)
  }

  fun remove(value: V) {
    entityMutator.remove(entity, value)
  }

  fun add(value: V) {
    entityMutator.add(entity, value)
  }
}
