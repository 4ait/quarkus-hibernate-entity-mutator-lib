package ru.code4a.quarkus.hibernate.mutator.interfaces

import ru.code4a.quarkus.hibernate.mutator.builds.FindAllHibernateAssociationsInfoBuildStep
import ru.code4a.quarkus.hibernate.mutator.mutators.HibernateRefMutator
import ru.code4a.quarkus.hibernate.mutator.utils.nullable.unwrapElseError
import kotlin.reflect.KMutableProperty0

interface HibernateEntityMutationSupport {
  fun <V> mutatorRef(property: KMutableProperty0<V>): HibernateRefMutator<V> {
    val currentClassWithField =
      FindAllHibernateAssociationsInfoBuildStep.ClassWithField(
        className = this::class.java.name,
        fieldName = property.name
      )

    val entityMutator =
      ru.code4a.quarkus.hibernate.mutator.services.HibernateEntityMutators.entityRefMutators[currentClassWithField]
        .unwrapElseError {
          "Cannot find mutator for $currentClassWithField"
        }

    return HibernateRefMutator(this, entityMutator)
  }

  fun <V : Any?> KMutableProperty0<V>.setRef(value: V) {
    mutatorRef(this).set(value)
  }

  fun <V : Any> mutatorRefs(property: KMutableProperty0<MutableSet<V>>): ru.code4a.quarkus.hibernate.mutator.mutators.HibernateCollectionMutator<V> {
    val currentClassWithField =
      FindAllHibernateAssociationsInfoBuildStep.ClassWithField(
        className = this::class.java.name,
        fieldName = property.name
      )

    val entityMutator =
      ru.code4a.quarkus.hibernate.mutator.services.HibernateEntityMutators.entityCollectionMutators[currentClassWithField]
        .unwrapElseError {
          "Cannot find mutator for $currentClassWithField"
        }

    return ru.code4a.quarkus.hibernate.mutator.mutators.HibernateCollectionMutator(this, entityMutator)
  }

  fun <V : Any> KMutableProperty0<MutableSet<V>>.setRefs(values: Collection<V>) {
    mutatorRefs(this).set(values)
  }
}
