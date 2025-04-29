package ru.code4a.quarkus.hibernate.mutator.builds

import io.quarkus.deployment.annotations.BuildProducer
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem
import io.quarkus.deployment.builditem.CombinedIndexBuildItem
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class FindAllHibernateAssociationsInfoBuildStep {
  @Serializable
  data class ClassNameWithFieldName(
    val className: String,
    val fieldName: String,
  )

  @BuildStep
  fun transformEntities(
    combinedIndex: CombinedIndexBuildItem,
    resourceProducer: BuildProducer<GeneratedResourceBuildItem>,
    bytecodeTransformerProducer: BuildProducer<BytecodeTransformerBuildItem>,
  ) {
    val oneToManyFieldsMap =
      combinedIndex
        .index
        .getAnnotations(OneToMany::class.java)
        .filter {
          it.target().kind() == org.jboss.jandex.AnnotationTarget.Kind.FIELD
        }
        .map {
          it.target().asField()
        }
        .associateBy { ClassNameWithFieldName(it.declaringClass().name().toString(), it.name()) }

    val manyToOneFieldsMap =
      combinedIndex
        .index
        .getAnnotations(ManyToOne::class.java)
        .filter {
          it.target().kind() == org.jboss.jandex.AnnotationTarget.Kind.FIELD
        }
        .map {
          it.target().asField()
        }
        .associateBy { ClassNameWithFieldName(it.declaringClass().name().toString(), it.name()) }

    val oneToOneFieldsMap =
      combinedIndex
        .index
        .getAnnotations(OneToOne::class.java)
        .filter {
          it.target().kind() == org.jboss.jandex.AnnotationTarget.Kind.FIELD
        }
        .map {
          it.target().asField()
        }
        .associateBy { ClassNameWithFieldName(it.declaringClass().name().toString(), it.name()) }

    val manyToManyFieldsMap =
      combinedIndex
        .index
        .getAnnotations(ManyToMany::class.java)
        .filter {
          it.target().kind() == org.jboss.jandex.AnnotationTarget.Kind.FIELD
        }
        .map {
          it.target().asField()
        }
        .associateBy { ClassNameWithFieldName(it.declaringClass().name().toString(), it.name()) }

    val associations = mutableListOf<ClassNameWithFieldName>()

    for (oneToManyField in oneToManyFieldsMap.values) {
      val classNameWithFieldName =
        ClassNameWithFieldName(
          className = oneToManyField.declaringClass().name().toString(),
          fieldName = oneToManyField.name(),
        )

      associations.add(classNameWithFieldName)
    }

    for (manyToOneField in manyToOneFieldsMap.values) {
      val classNameWithFieldName =
        ClassNameWithFieldName(
          className = manyToOneField.declaringClass().name().toString(),
          fieldName = manyToOneField.name(),
        )

      associations.add(classNameWithFieldName)
    }

    for (oneToOneField in oneToOneFieldsMap.values) {
      val classNameWithFieldName =
        ClassNameWithFieldName(
          className = oneToOneField.declaringClass().name().toString(),
          fieldName = oneToOneField.name(),
        )

      associations.add(classNameWithFieldName)
    }

    for (manyToManyField in manyToManyFieldsMap.values) {
      val classNameWithFieldName =
        ClassNameWithFieldName(
          className = manyToManyField.declaringClass().name().toString(),
          fieldName = manyToManyField.name(),
        )

      associations.add(classNameWithFieldName)
    }

    resourceProducer.produce(
      GeneratedResourceBuildItem(
        "ru/code4a/hibernate/gen/associations",
        Json.encodeToString(associations).toByteArray()
      )
    )
  }
}
