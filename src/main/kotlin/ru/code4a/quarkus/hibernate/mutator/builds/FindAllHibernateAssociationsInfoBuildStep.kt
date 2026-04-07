package ru.code4a.quarkus.hibernate.mutator.builds

import io.quarkus.deployment.annotations.BuildProducer
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.builditem.CombinedIndexBuildItem
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem
import jakarta.persistence.Entity
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import kotlinx.serialization.json.Json
import ru.code4a.quarkus.hibernate.mutator.models.ClassNameWithFieldName

class FindAllHibernateAssociationsInfoBuildStep {
  companion object {
    const val ASSOCIATIONS_JSON_RESOURCE_PATH = "ru/code4a/hibernate/gen/associations"
    const val ENTITY_CLASSES_JSON_RESOURCE_PATH = "ru/code4a/hibernate/gen/entities"
  }

  @BuildStep
  fun transformEntities(
    combinedIndex: CombinedIndexBuildItem,
    resourceProducer: BuildProducer<GeneratedResourceBuildItem>
  ) {
    val oneToManyFieldsMap =
      combinedIndex
        .index
        .getAnnotations(OneToMany::class.java)
        .filter { it.target().kind() == org.jboss.jandex.AnnotationTarget.Kind.FIELD }
        .map { it.target().asField() }
        .associateBy { ClassNameWithFieldName(it.declaringClass().name().toString(), it.name()) }

    val manyToOneFieldsMap =
      combinedIndex
        .index
        .getAnnotations(ManyToOne::class.java)
        .filter { it.target().kind() == org.jboss.jandex.AnnotationTarget.Kind.FIELD }
        .map { it.target().asField() }
        .associateBy { ClassNameWithFieldName(it.declaringClass().name().toString(), it.name()) }

    val oneToOneFieldsMap =
      combinedIndex
        .index
        .getAnnotations(OneToOne::class.java)
        .filter { it.target().kind() == org.jboss.jandex.AnnotationTarget.Kind.FIELD }
        .map { it.target().asField() }
        .associateBy { ClassNameWithFieldName(it.declaringClass().name().toString(), it.name()) }

    val manyToManyFieldsMap =
      combinedIndex
        .index
        .getAnnotations(ManyToMany::class.java)
        .filter { it.target().kind() == org.jboss.jandex.AnnotationTarget.Kind.FIELD }
        .map { it.target().asField() }
        .associateBy { ClassNameWithFieldName(it.declaringClass().name().toString(), it.name()) }

    val associations = buildList {
      addAll(oneToManyFieldsMap.keys)
      addAll(manyToOneFieldsMap.keys)
      addAll(oneToOneFieldsMap.keys)
      addAll(manyToManyFieldsMap.keys)
    }

    val entityClasses =
      combinedIndex
        .index
        .getAnnotations(Entity::class.java)
        .filter { it.target().kind() == org.jboss.jandex.AnnotationTarget.Kind.CLASS }
        .map { it.target().asClass().name().toString() }
        .distinct()

    validateNoAssociationFieldShadowingInEntityHierarchies(
      associations = associations,
      entityClassNames = entityClasses,
      combinedIndex = combinedIndex
    )

    resourceProducer.produce(
      GeneratedResourceBuildItem(
        ASSOCIATIONS_JSON_RESOURCE_PATH,
        Json.encodeToString(associations).toByteArray()
      )
    )

    resourceProducer.produce(
      GeneratedResourceBuildItem(
        ENTITY_CLASSES_JSON_RESOURCE_PATH,
        Json.encodeToString(entityClasses).toByteArray()
      )
    )
  }

  private fun validateNoAssociationFieldShadowingInEntityHierarchies(
    associations: List<ClassNameWithFieldName>,
    entityClassNames: List<String>,
    combinedIndex: CombinedIndexBuildItem
  ) {
    val associationsByClassName =
      associations
        .groupBy { it.className }
        .mapValues { (_, values) -> values.map { it.fieldName }.toSet() }

    entityClassNames.forEach { entityClassName ->
      val seenFieldNames = mutableMapOf<String, String>()
      var currentClassName: String? = entityClassName

      while (currentClassName != null && currentClassName != Any::class.java.name) {
        associationsByClassName[currentClassName].orEmpty().forEach { fieldName ->
          val previousDeclaringClassName = seenFieldNames.putIfAbsent(fieldName, currentClassName)

          require(previousDeclaringClassName == null) {
            "Association field '$fieldName' is declared multiple times in entity inheritance chain: " +
              "$previousDeclaringClassName and ${currentClassName}. " +
              "This is not supported because mutator lookup by runtime class becomes ambiguous."
          }
        }

        currentClassName =
          combinedIndex
            .index
            .getClassByName(org.jboss.jandex.DotName.createSimple(currentClassName))
            ?.superName()
            ?.toString()
      }
    }
  }
}
