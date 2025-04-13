package ru.code4a.quarkus.hibernate.mutator.mutators

class HibernateRefMutator<V : Any?>(
  private val entity: Any,
  private val entityMutator: HibernateEntityRefMutator
) {
  fun set(value: V) {
    entityMutator.set(entity, value)
  }
}
