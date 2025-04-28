package ru.code4a.quarkus.hibernate.mutator.interfaces

import ru.code4a.quarkus.hibernate.mutator.builds.FindAllHibernateAssociationsInfoBuildStep
import ru.code4a.quarkus.hibernate.mutator.mutators.wrappers.HibernateRefMutator
import ru.code4a.quarkus.hibernate.mutator.mutators.wrappers.HibernateCollectionMutator
import ru.code4a.quarkus.hibernate.mutator.services.HibernateEntityMutators
import ru.code4a.quarkus.hibernate.mutator.utils.nullable.unwrapElseError
import kotlin.reflect.KProperty0

interface HibernateEntityMutationSupport {
  fun <V : HibernateEntityMutationSupport?> mutatorRef(property: KProperty0<V>): HibernateRefMutator<V> {
    val currentClassNameWithFieldName =
      FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName(
        className = this::class.java.name,
        fieldName = property.name
      )

    val entityMutator =
      HibernateEntityMutators.entityRefMutators[currentClassNameWithFieldName]
        .unwrapElseError {
          "Cannot find mutator for $currentClassNameWithFieldName"
        }

    return HibernateRefMutator(this, entityMutator)
  }

  fun <V : HibernateEntityMutationSupport?> KProperty0<V>.setRef(value: V) {
    mutatorRef(this).set(value)
  }

  fun <V : HibernateEntityMutationSupport> mutatorRefs(property: KProperty0<MutableSet<V>>): HibernateCollectionMutator<V> {
    val currentClassNameWithFieldName =
      FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName(
        className = this::class.java.name,
        fieldName = property.name
      )

    val entityMutator =
      HibernateEntityMutators.entityCollectionMutators[currentClassNameWithFieldName]
        .unwrapElseError {
          "Cannot find mutator for $currentClassNameWithFieldName"
        }

    return HibernateCollectionMutator(this, entityMutator)
  }

  fun <V : HibernateEntityMutationSupport> KProperty0<MutableSet<V>>.setRefs(values: Collection<V>) {
    mutatorRefs(this).set(values)
  }

  fun <V : HibernateEntityMutationSupport> KProperty0<MutableSet<V>>.add(value: V) {
    mutatorRefs(this).add(value)
  }

  fun <V : HibernateEntityMutationSupport> KProperty0<MutableSet<V>>.remove(value: V) {
    mutatorRefs(this).remove(value)
  }
}
