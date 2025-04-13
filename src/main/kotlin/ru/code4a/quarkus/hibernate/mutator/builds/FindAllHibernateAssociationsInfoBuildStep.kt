package ru.code4a.quarkus.hibernate.mutator.builds

import io.quarkus.deployment.annotations.BuildProducer
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.builditem.CombinedIndexBuildItem
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class FindAllHibernateAssociationsInfoBuildStep {
  @Serializable
  data class ClassWithField(
    val className: String,
    val fieldName: String,
  )

  @BuildStep
  fun transformCameraEntity(
    combinedIndex: CombinedIndexBuildItem,
    resourceProducer: BuildProducer<GeneratedResourceBuildItem>
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
        .associateBy { ClassWithField(it.declaringClass().name().toString(), it.name()) }

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
        .associateBy { ClassWithField(it.declaringClass().name().toString(), it.name()) }

    val oneToOneFieldsMap =
      combinedIndex
        .index
        .getAnnotations(ManyToOne::class.java)
        .filter {
          it.target().kind() == org.jboss.jandex.AnnotationTarget.Kind.FIELD
        }
        .map {
          it.target().asField()
        }
        .associateBy { ClassWithField(it.declaringClass().name().toString(), it.name()) }

    val associations = mutableListOf<ClassWithField>()

    for (oneToManyField in oneToManyFieldsMap.values) {
      val classWithField =
        ClassWithField(
          className = oneToManyField.declaringClass().name().toString(),
          fieldName = oneToManyField.name(),
        )

      associations.add(classWithField)
    }

    for (manyToOneField in manyToOneFieldsMap.values) {
      val classWithField =
        ClassWithField(
          className = manyToOneField.declaringClass().name().toString(),
          fieldName = manyToOneField.name(),
        )

      associations.add(classWithField)
    }

    for (oneToOneField in oneToOneFieldsMap.values) {
      val classWithField =
        ClassWithField(
          className = oneToOneField.declaringClass().name().toString(),
          fieldName = oneToOneField.name(),
        )

      associations.add(classWithField)
    }

    resourceProducer.produce(
      GeneratedResourceBuildItem(
        "ru/code4a/hibernate/gen/associations",
        Json.encodeToString(associations).toByteArray()
      )
    )
  }
}
