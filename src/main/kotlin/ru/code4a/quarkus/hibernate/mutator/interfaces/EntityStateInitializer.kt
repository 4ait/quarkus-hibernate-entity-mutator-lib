package ru.code4a.quarkus.hibernate.mutator.interfaces

class EntityStateInitializer(
  val entityFieldStateInitializers: List<EntityFieldStateInitializer>
) {
  fun initialize(entity: Any) {
    entityFieldStateInitializers.forEach { it.initialize(entity) }
  }
}
