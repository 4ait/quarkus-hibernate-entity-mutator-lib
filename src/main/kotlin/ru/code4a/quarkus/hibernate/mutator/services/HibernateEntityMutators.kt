package ru.code4a.quarkus.hibernate.mutator.services

import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import kotlinx.serialization.json.Json
import org.hibernate.Hibernate
import ru.code4a.quarkus.hibernate.mutator.builds.FindAllHibernateAssociationsInfoBuildStep
import ru.code4a.quarkus.hibernate.mutator.mutators.HibernateEntityCollectionMutator
import ru.code4a.quarkus.hibernate.mutator.mutators.HibernateEntityRefMutator
import ru.code4a.quarkus.hibernate.mutator.utils.nullable.unwrapElseError
import java.lang.reflect.Field
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.jvm.kotlinProperty

class HibernateEntityMutators {

  private data class AssociationInfo(
    val clazz: Class<*>,
    val field: Field,
    var mappedFrom: AssociationInfo? = null,
    var mappedBy: AssociationInfo? = null
  )

  companion object {
    val entityCollectionMutators: Map<FindAllHibernateAssociationsInfoBuildStep.ClassWithField, HibernateEntityCollectionMutator>
    val entityRefMutators: Map<FindAllHibernateAssociationsInfoBuildStep.ClassWithField, HibernateEntityRefMutator>

    init {
      val classLoader = Thread.currentThread().contextClassLoader

      val associationsRawInfo: List<FindAllHibernateAssociationsInfoBuildStep.ClassWithField> =
        Json.decodeFromString(
          classLoader
            .getResource("ru/code4a/hibernate/gen/associations")
            .unwrapElseError {
              "Cannot find resource ru/code4a/hibernate/gen/associations"
            }
            .readText()
        )

      val entityCollectionMutators =
        mutableMapOf<FindAllHibernateAssociationsInfoBuildStep.ClassWithField, HibernateEntityCollectionMutator>()

      val entityRefMutators =
        mutableMapOf<FindAllHibernateAssociationsInfoBuildStep.ClassWithField, HibernateEntityRefMutator>()

      val associationsInfoMap =
        associationsRawInfo.map { associationRawInfo ->
          val clazz = classLoader.loadClass(associationRawInfo.className)
          val field = clazz.declaredFields.first { associationRawInfo.fieldName == it.name }

          AssociationInfo(
            clazz = clazz,
            field = field
          )
        }
          .associateBy {
            FindAllHibernateAssociationsInfoBuildStep.ClassWithField(
              className = it.clazz.name,
              fieldName = it.field.name
            )
          }

      for (associationInfo in associationsInfoMap.values) {
        val oneToManyAnnotation =
          associationInfo
            .field
            .annotations
            .find {
              it is OneToMany
            }
            ?.let {
              it as OneToMany
            }

        if (oneToManyAnnotation != null) {
          val fieldKotlinProperty =
            associationInfo
              .field
              .kotlinProperty
              .unwrapElseError { "Kotlin property must be present" }

          val fieldKReturnType =
            fieldKotlinProperty
              .returnType

          val associatedClass =
            fieldKReturnType
              .arguments[0]
              .type!!
              .jvmErasure
              .java

          val mappedBy = oneToManyAnnotation.mappedBy

          if(mappedBy != "") {
            val mappedByAssociation =
              associationsInfoMap[
                FindAllHibernateAssociationsInfoBuildStep.ClassWithField(
                  className = associatedClass.name,
                  fieldName = mappedBy
                )
              ]
                .unwrapElseError {
                  "Cannot find entity association ${
                    FindAllHibernateAssociationsInfoBuildStep.ClassWithField(
                      className = associatedClass.name,
                      fieldName = mappedBy
                    )
                  }"
                }

            require(associationInfo.mappedBy == null)
            require(mappedByAssociation.mappedFrom == null)

            associationInfo.mappedBy = mappedByAssociation
            mappedByAssociation.mappedFrom = associationInfo
          }
        }

        val oneToOneAnnotation =
          associationInfo
            .field
            .annotations
            .find {
              it is OneToOne
            }
            ?.let {
              it as OneToOne
            }

        if (oneToOneAnnotation != null) {
          val associatedClass = associationInfo.field.type

          val mappedBy = oneToOneAnnotation.mappedBy

          if(mappedBy != "") {
            val mappedByAssociation =
              associationsInfoMap[
                FindAllHibernateAssociationsInfoBuildStep.ClassWithField(
                  className = associatedClass.name,
                  fieldName = mappedBy
                )
              ]
                .unwrapElseError {
                  "Cannot find entity association ${
                    FindAllHibernateAssociationsInfoBuildStep.ClassWithField(
                      className = associatedClass.name,
                      fieldName = mappedBy
                    )
                  }"
                }

            require(associationInfo.mappedBy == null)
            require(mappedByAssociation.mappedFrom == null)

            associationInfo.mappedBy = mappedByAssociation
            mappedByAssociation.mappedFrom = associationInfo
          }
        }
      }

      for (associationInfo in associationsInfoMap.values) {
        val oneToManyAnnotation =
          associationInfo
            .field
            .annotations
            .find {
              it is OneToMany
            }
            ?.let {
              it as OneToMany
            }

        if (oneToManyAnnotation != null) {
          val mappedByAssociation = associationInfo.mappedBy
          if (mappedByAssociation != null) {
            val fieldKotlinProperty =
              associationInfo
                .field
                .kotlinProperty
                .unwrapElseError { "Kotlin property must be present" }

            val fieldClass =
              associationInfo
                .field
                .type

            val field = associationInfo.field
            val mappedByField = mappedByAssociation.field

            field.isAccessible = true
            mappedByField.isAccessible = true

            if (fieldClass == Set::class.java) {
              entityCollectionMutators[
                FindAllHibernateAssociationsInfoBuildStep.ClassWithField(
                  className = associationInfo.clazz.name,
                  fieldName = associationInfo.field.name
                )
              ] = object : HibernateEntityCollectionMutator {
                override fun set(entity: Any, values: Collection<Any>) {
                  val entityElements = field.get(entity) as MutableSet<Any>
                  val newValues = values.toSet() // Convert to Set for better performance in contains() operations

                  // Find elements to remove (those in current collection but not in new values)
                  val elementsToRemove = entityElements.filter { it !in newValues }
                  for (elementToRemove in elementsToRemove) {
                    mappedByField.set(elementToRemove, null)
                  }

                  // Find elements to add (those in new values but not in current collection)
                  val elementsToAdd = newValues.filter { it !in entityElements }
                  for (elementToAdd in elementsToAdd) {
                    if (mappedByField.get(elementToAdd) != null) {
                      throw IllegalStateException("Entity already associated with another entity")
                    }
                    mappedByField.set(elementToAdd, entity)
                  }

                  // Update the collection in one go
                  if (elementsToRemove.isNotEmpty()) {
                    entityElements.removeAll(elementsToRemove.toSet())
                  }
                  if (elementsToAdd.isNotEmpty()) {
                    entityElements.addAll(elementsToAdd)
                  }
                }

                override fun remove(entity: Any, value: Any) {
                  if (mappedByField.get(value) == entity) {
                    val entityElements = field.get(entity) as MutableSet<Any>
                    mappedByField.set(value, null)
                    entityElements.remove(value)
                  } else {
                    throw IllegalStateException("Entity associated with another entity")
                  }
                }

                override fun add(entity: Any, value: Any) {
                  val mappedByValue = mappedByField.get(value)

                  if (mappedByValue != null && mappedByValue != entity) {
                    throw IllegalStateException("Entity associated with another entity")
                  }

                  val entityElements = field.get(entity) as MutableSet<Any>

                  mappedByField.set(value, entity)
                  entityElements.add(value)
                }
              }
              continue
            } else {
              throw NotImplementedError("Mapped by with type $fieldClass is not implemented.")
            }
          }

          if (associationInfo.mappedBy != null || associationInfo.mappedFrom != null) {
            throw NotImplementedError("Not implemented ${associationInfo.clazz}::${associationInfo.field.name}.")
          }

          val field = associationInfo.field
          field.isAccessible = true

          val fieldClass =
            associationInfo
              .field
              .type

          if (fieldClass == Set::class.java) {
            entityCollectionMutators[
              FindAllHibernateAssociationsInfoBuildStep.ClassWithField(
                className = associationInfo.clazz.name,
                fieldName = associationInfo.field.name
              )
            ] = object : HibernateEntityCollectionMutator {
              override fun set(entity: Any, values: Collection<Any>) {
                val entityElements = field.get(entity) as MutableSet<Any>

                entityElements.clear()
                entityElements.addAll(values)
              }

              override fun remove(entity: Any, value: Any) {
                val entityElements = field.get(entity) as MutableSet<Any>

                entityElements.remove(value)
              }

              override fun add(entity: Any, value: Any) {
                val entityElements = field.get(entity) as MutableSet<Any>

                entityElements.add(value)
              }
            }

            continue
          } else {
            throw NotImplementedError("Mapped by with type $fieldClass is not implemented.")
          }

          throw NotImplementedError("Not implemented ${associationInfo.clazz}::${associationInfo.field.name}.")
        }

        val manyToOneAnnotation =
          associationInfo
            .field
            .annotations
            .find {
              it is ManyToOne
            }
            ?.let {
              it as ManyToOne
            }

        if (manyToOneAnnotation != null) {
          val mappedFromAssociation = associationInfo.mappedFrom
          if (mappedFromAssociation != null) {
            val mappedFromFieldClass =
              mappedFromAssociation
                .field
                .type

            if (mappedFromFieldClass == Set::class.java) {
              val field = associationInfo.field
              field.isAccessible = true

              val mappedFromField = mappedFromAssociation.field
              mappedFromField.isAccessible = true

              entityRefMutators[
                FindAllHibernateAssociationsInfoBuildStep.ClassWithField(
                  className = associationInfo.clazz.name,
                  fieldName = associationInfo.field.name
                )
              ] = object : HibernateEntityRefMutator {
                override fun set(entity: Any, value: Any?) {
                  val currentValue = field.get(entity)

                  if (currentValue == value) {
                    return
                  }

                  val mappedFromCollection = mappedFromField.get(currentValue) as MutableSet<Any>

                  val collectionIsInitialized = Hibernate.isInitialized(mappedFromCollection)

                  if (collectionIsInitialized) {
                    mappedFromCollection.remove(entity)
                  }

                  if (value == null) {
                    field.set(entity, null)
                  } else {
                    if (collectionIsInitialized) {
                      mappedFromCollection.add(entity)
                    }

                    field.set(entity, value)
                  }
                }
              }

              continue
            } else {
              throw NotImplementedError("Not implemented ${associationInfo.clazz}::${associationInfo.field.name}.")
            }
          }

          if (associationInfo.mappedBy != null || associationInfo.mappedFrom != null) {
            throw NotImplementedError("Not implemented ${associationInfo.clazz}::${associationInfo.field.name}.")
          }

          val field = associationInfo.field
          field.isAccessible = true

          entityRefMutators[
            FindAllHibernateAssociationsInfoBuildStep.ClassWithField(
              className = associationInfo.clazz.name,
              fieldName = associationInfo.field.name
            )
          ] = object : HibernateEntityRefMutator {
            override fun set(entity: Any, value: Any?) {
              field.set(entity, value)
            }
          }

          continue
        }

        val oneToOneAnnotation =
          associationInfo
            .field
            .annotations
            .find {
              it is OneToOne
            }
            ?.let {
              it as OneToOne
            }

        if(oneToOneAnnotation != null) {
          val bidirectionalAssociation =
            if(associationInfo.mappedFrom == null) {
              associationInfo.mappedBy
            } else {
              associationInfo.mappedFrom
            }

          if (bidirectionalAssociation != null) {
            val field = associationInfo.field
            val bidirectionalField = bidirectionalAssociation.field

            field.isAccessible = true
            bidirectionalField.isAccessible = true

            entityRefMutators[
              FindAllHibernateAssociationsInfoBuildStep.ClassWithField(
                className = associationInfo.clazz.name,
                fieldName = associationInfo.field.name
              )
            ] = object : HibernateEntityRefMutator {
              override fun set(entity: Any, value: Any?) {
                val currentValue = field.get(entity)

                if(currentValue == value) {
                  return
                }

                if(currentValue != null) {
                  bidirectionalField.set(currentValue, null)
                }

                if(value != null) {
                  bidirectionalField.set(value, entity)
                  field.set(entity, value)
                } else {
                  field.set(entity, null)
                }
              }
            }

            continue
          }

          val field = associationInfo.field
          field.isAccessible = true

          entityRefMutators[
            FindAllHibernateAssociationsInfoBuildStep.ClassWithField(
              className = associationInfo.clazz.name,
              fieldName = associationInfo.field.name
            )
          ] = object : HibernateEntityRefMutator {
            override fun set(entity: Any, value: Any?) {
              field.set(entity, value)
            }
          }

          continue
        }

        throw NotImplementedError("Not implemented ${associationInfo.clazz}::${associationInfo.field.name}.")
      }

      this.entityCollectionMutators = entityCollectionMutators
      this.entityRefMutators = entityRefMutators
    }
  }
}
