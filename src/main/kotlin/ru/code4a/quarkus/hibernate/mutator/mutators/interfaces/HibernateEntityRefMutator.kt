package ru.code4a.quarkus.hibernate.mutator.mutators.interfaces

interface HibernateEntityRefMutator {
  fun set(entity: Any, value: Any?)
}
