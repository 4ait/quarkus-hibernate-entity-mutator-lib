package ru.code4a.quarkus.hibernate.mutator.mutators.interfaces

interface HibernateEntityCollectionMutator {
  fun set(entity: Any, values: Collection<Any>)
  fun beforeSetManual(entity: Any, values: Collection<Any>)
  fun rawSet(entity: Any, values: Collection<Any>)
  fun remove(entity: Any, value: Any)
  fun add(entity: Any, value: Any)
}
