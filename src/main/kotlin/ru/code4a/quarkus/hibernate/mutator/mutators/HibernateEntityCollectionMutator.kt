package ru.code4a.quarkus.hibernate.mutator.mutators

interface HibernateEntityCollectionMutator {
  fun set(entity: Any, values: Collection<Any>)
  fun remove(entity: Any, value: Any)
  fun add(entity: Any, value: Any)
}
