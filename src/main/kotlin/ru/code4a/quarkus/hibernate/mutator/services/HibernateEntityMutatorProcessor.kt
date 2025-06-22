package ru.code4a.quarkus.hibernate.mutator.services

import ru.code4a.quarkus.hibernate.mutator.models.ClassNameWithFieldName
import ru.code4a.quarkus.hibernate.mutator.services.HibernateEntityMutators.entityCollectionMutators
import ru.code4a.quarkus.hibernate.mutator.services.HibernateEntityMutators.entityInitializers
import ru.code4a.quarkus.hibernate.mutator.utils.nullable.unwrapElseError

object HibernateEntityMutatorProcessor {
  fun processSetCollection(entity: Any, fieldName: String, values: Collection<Any>) {
    val currentClassNameWithFieldName =
      ClassNameWithFieldName(
        className = entity::class.java.name,
        fieldName = fieldName
      )

    val entityMutator =
      entityCollectionMutators[currentClassNameWithFieldName]
        .unwrapElseError {
          "Cannot find mutator for $currentClassNameWithFieldName"
        }

    entityMutator.set(entity, values)
  }

  fun processRawSetCollection(entity: Any, fieldName: String, values: Collection<Any>) {
    val currentClassNameWithFieldName =
      ClassNameWithFieldName(
        className = entity::class.java.name,
        fieldName = fieldName
      )

    val entityMutator =
      entityCollectionMutators[currentClassNameWithFieldName]
        .unwrapElseError {
          "Cannot find mutator for $currentClassNameWithFieldName"
        }

    entityMutator.rawSet(entity, values)
  }

  fun initializeEntity(entity: Any) {
    val entityInitializer =
      entityInitializers[entity::class.java]
        .unwrapElseError {
          "Cannot find initializer for ${entity::class.java}"
        }

    entityInitializer.initialize(entity)
  }

  fun initializeEntityIfInitializerPresent(entity: Any) {
    val entityInitializer =
      entityInitializers[entity::class.java] ?: return

    entityInitializer.initialize(entity)
  }
}
