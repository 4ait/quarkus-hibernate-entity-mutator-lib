package ru.code4a.quarkus.hibernate.mutator.builds.utils

import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import ru.code4a.quarkus.hibernate.mutator.builds.FindAllHibernateAssociationsInfoBuildStep
import ru.code4a.quarkus.hibernate.mutator.services.HibernateEntityMutators
import ru.code4a.quarkus.hibernate.mutator.utils.nullable.unwrapElseError
import java.lang.reflect.Field
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.jvm.kotlinProperty

/**
 * Information about a JPA association
 */
internal data class AssociationInfo(
  val clazz: Class<*>,
  val field: Field,
  var mappedFrom: AssociationInfo? = null,
  var mappedBy: AssociationInfo? = null
) {
  init {
    field.isAccessible = true
  }

  val key: AssociationKey
    get() = AssociationKey(clazz.name, this.field.name)

  val buildItemKey: FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName
    get() = FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName(
      className = clazz.name,
      fieldName = this.field.name
    )

  val classWithFieldName: HibernateEntityMutators.ClassWithFieldName
    get() = HibernateEntityMutators.ClassWithFieldName(
      clazz = clazz,
      fieldName = this.field.name
    )

  fun getJpaAnnotation(): Annotation? {
    return field.annotations.firstOrNull { annotation ->
      annotation is OneToMany ||
        annotation is ManyToOne ||
        annotation is OneToOne ||
        annotation is ManyToMany
    }
  }

  fun getCollectionElementType(): Class<*> {
    val kotlinProperty = field.kotlinProperty
      .unwrapElseError { "Kotlin property must be present for ${clazz.name}::${field.name}" }

    return kotlinProperty.returnType.arguments[0].type!!.jvmErasure.java
  }
}

/**
 * Key for identifying associations
 */
internal data class AssociationKey(
  val className: String,
  val fieldName: String
)
