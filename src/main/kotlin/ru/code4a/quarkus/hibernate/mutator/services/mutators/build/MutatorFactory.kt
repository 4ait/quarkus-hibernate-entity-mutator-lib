package ru.code4a.quarkus.hibernate.mutator.services.mutators.build

import MutatorBuilder
import ru.code4a.quarkus.hibernate.mutator.builds.FindAllHibernateAssociationsInfoBuildStep
import ru.code4a.quarkus.hibernate.mutator.builds.utils.AssociationInfo
import ru.code4a.quarkus.hibernate.mutator.builds.utils.AssociationKey
import ru.code4a.quarkus.hibernate.mutator.interfaces.EntityFieldStateInitializer
import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityCollectionMutator
import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityRefMutator
import ru.code4a.quarkus.hibernate.mutator.services.HibernateEntityMutators

/**
 * Factory for creating mutators based on association types
 */
internal class MutatorFactory {
  fun createMutators(
    associations: Map<AssociationKey, AssociationInfo>
  ): Triple<
    Map<FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName, HibernateEntityCollectionMutator>,
    Map<FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName, HibernateEntityRefMutator>,
    Map<HibernateEntityMutators.ClassWithFieldName, EntityFieldStateInitializer>
    > {
    val collectionMutators =
      mutableMapOf<FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName, HibernateEntityCollectionMutator>()
    val refMutators =
      mutableMapOf<FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName, HibernateEntityRefMutator>()
    val fieldInitializers = mutableMapOf<HibernateEntityMutators.ClassWithFieldName, EntityFieldStateInitializer>()

    associations.values.forEach { association ->
      val builder = MutatorBuilder(association)
      val result = builder.build()

      result.collectionMutator?.let {
        collectionMutators[association.buildItemKey] = it
      }
      result.refMutator?.let {
        refMutators[association.buildItemKey] = it
      }
      fieldInitializers[association.classWithFieldName] = result.initializer
    }

    return Triple(collectionMutators, refMutators, fieldInitializers)
  }
}
