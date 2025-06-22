package ru.code4a.quarkus.hibernate.mutator.services.mutators.build

import MutatorBuilder
import ru.code4a.quarkus.hibernate.mutator.builds.utils.AssociationInfo
import ru.code4a.quarkus.hibernate.mutator.builds.utils.AssociationKey
import ru.code4a.quarkus.hibernate.mutator.interfaces.EntityFieldStateInitializer
import ru.code4a.quarkus.hibernate.mutator.models.ClassNameWithFieldName
import ru.code4a.quarkus.hibernate.mutator.models.ClassWithFieldName
import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityCollectionMutator
import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityRefMutator

/**
 * Factory for creating mutators based on association types
 */
internal class MutatorFactory {
  fun createMutators(
    associations: Map<AssociationKey, AssociationInfo>
  ): Triple<
    Map<ClassNameWithFieldName, HibernateEntityCollectionMutator>,
    Map<ClassNameWithFieldName, HibernateEntityRefMutator>,
    Map<ClassWithFieldName, EntityFieldStateInitializer>
    > {
    val collectionMutators =
      mutableMapOf<ClassNameWithFieldName, HibernateEntityCollectionMutator>()
    val refMutators =
      mutableMapOf<ClassNameWithFieldName, HibernateEntityRefMutator>()
    val fieldInitializers = mutableMapOf<ClassWithFieldName, EntityFieldStateInitializer>()

    associations.values.forEach { association ->
      val builder = MutatorBuilder(association)
      val result = builder.build()

      result.collectionMutator?.let {
        collectionMutators[association.classNameWithFieldName] = it
      }
      result.refMutator?.let {
        refMutators[association.classNameWithFieldName] = it
      }
      fieldInitializers[association.classWithFieldName] = result.initializer
    }

    return Triple(collectionMutators, refMutators, fieldInitializers)
  }
}
