package ru.code4a.quarkus.hibernate.mutator.services.mutators.build.initializers

import ru.code4a.quarkus.hibernate.mutator.interfaces.EntityFieldStateInitializer

/**
 * No-op initializer for fields that don't need initialization
 */
internal object NoOpFieldInitializer : EntityFieldStateInitializer {
  override fun initialize(entity: Any) {
    // No initialization needed
  }
}
