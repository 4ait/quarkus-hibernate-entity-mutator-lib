package ru.code4a.quarkus.hibernate.mutator.mutators

interface HibernateEntityRefMutator {
  fun set(entity: Any, value: Any?)
}
