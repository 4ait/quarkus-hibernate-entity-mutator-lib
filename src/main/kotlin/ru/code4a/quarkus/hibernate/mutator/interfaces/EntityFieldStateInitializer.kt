package ru.code4a.quarkus.hibernate.mutator.interfaces

interface EntityFieldStateInitializer {
  fun initialize(entity: Any)
}
