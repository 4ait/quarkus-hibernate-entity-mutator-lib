package ru.code4a.quarkus.hibernate.mutator.mutators.interfaces

interface HibernateEntityRefMutator {
  fun beforeSetManual(entity: Any, value: Any?)
  fun set(entity: Any, value: Any?)
}
