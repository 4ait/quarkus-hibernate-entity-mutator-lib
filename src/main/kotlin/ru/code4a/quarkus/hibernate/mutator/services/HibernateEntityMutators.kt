package ru.code4a.quarkus.hibernate.mutator.services

import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import kotlinx.serialization.json.Json
import org.hibernate.Hibernate
import ru.code4a.quarkus.hibernate.mutator.builds.FindAllHibernateAssociationsInfoBuildStep
import ru.code4a.quarkus.hibernate.mutator.interfaces.EntityFieldStateInitializer
import ru.code4a.quarkus.hibernate.mutator.interfaces.EntityStateInitializer
import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityCollectionMutator
import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityRefMutator
import ru.code4a.quarkus.hibernate.mutator.utils.hibernate.HibernateFieldAccessor
import ru.code4a.quarkus.hibernate.mutator.utils.nullable.unwrapElseError
import java.lang.reflect.Field
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.jvm.kotlinProperty

object HibernateEntityMutators {

  data class ClassWithFieldName(
    val clazz: Class<*>,
    val fieldName: String,
  )

  private data class AssociationInfo(
    val clazz: Class<*>,
    val field: Field,
    var mappedFrom: AssociationInfo? = null,
    var mappedBy: AssociationInfo? = null
  )

  val entityCollectionMutators: Map<FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName, HibernateEntityCollectionMutator>
  val entityRefMutators: Map<FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName, HibernateEntityRefMutator>
  val entityInitializers: Map<Class<*>, EntityStateInitializer>

  init {
    val classLoader = Thread.currentThread().contextClassLoader

    val associationsRawInfo: List<FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName> =
      Json.decodeFromString(
        classLoader
          .getResource("ru/code4a/hibernate/gen/associations")
          .unwrapElseError {
            "Cannot find resource ru/code4a/hibernate/gen/associations"
          }
          .readText()
      )

    val entityCollectionMutators =
      mutableMapOf<FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName, HibernateEntityCollectionMutator>()

    val entityRefMutators =
      mutableMapOf<FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName, HibernateEntityRefMutator>()

    val entityFieldStateInitializers =
      mutableMapOf<ClassWithFieldName, EntityFieldStateInitializer>()

    val errors = mutableListOf<String>()

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
          FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName(
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

        if (mappedBy != "") {
          val mappedByAssociation =
            associationsInfoMap[
              FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName(
                className = associatedClass.name,
                fieldName = mappedBy
              )
            ]
              .unwrapElseError {
                "Cannot find entity association ${
                  FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName(
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

        if (mappedBy != "") {
          val mappedByAssociation =
            associationsInfoMap[
              FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName(
                className = associatedClass.name,
                fieldName = mappedBy
              )
            ]
              .unwrapElseError {
                "Cannot find entity association ${
                  FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName(
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

      val manyToManyAnnotation =
        associationInfo
          .field
          .annotations
          .find {
            it is ManyToMany
          }
          ?.let {
            it as ManyToMany
          }

      if (manyToManyAnnotation != null) {
        if (manyToManyAnnotation.mappedBy.isNotBlank()) {
          error("ManyToMany mappedBy is not supported yet")
        }
      }
    }

    for (associationInfo in associationsInfoMap.values) {
      val manyToManyAnnotation =
        associationInfo
          .field
          .annotations
          .find {
            it is ManyToMany
          }
          ?.let {
            it as ManyToMany
          }

      if (manyToManyAnnotation != null) {
        val mappedByAssociation = associationInfo.mappedBy
        if (mappedByAssociation != null) {
          error("ManyToMany mappedBy is not supported yet")
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
          val fieldHibernateAccessor = HibernateFieldAccessor(field)

          val mutator =
            object : HibernateEntityCollectionMutator {
              override fun set(entity: Any, values: Collection<Any>) {
                val entityElements = fieldHibernateAccessor.get(entity) as MutableSet<Any>

                entityElements.clear()
                entityElements.addAll(values)
              }

              override fun rawSet(entity: Any, values: Collection<Any>) {
                fieldHibernateAccessor.set(entity, values)
              }

              override fun remove(entity: Any, value: Any) {
                val entityElements = fieldHibernateAccessor.get(entity) as MutableSet<Any>

                entityElements.remove(value)
              }

              override fun add(entity: Any, value: Any) {
                val entityElements = fieldHibernateAccessor.get(entity) as MutableSet<Any>

                entityElements.add(value)
              }
            }

          entityCollectionMutators[
            FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName(
              className = associationInfo.clazz.name,
              fieldName = associationInfo.field.name
            )
          ] = mutator

          entityFieldStateInitializers[
            ClassWithFieldName(
              clazz = associationInfo.clazz,
              fieldName = associationInfo.field.name
            )
          ] = object : EntityFieldStateInitializer {
            override fun initialize(entity: Any) {}
          }
          continue
        } else {
          throw NotImplementedError("Mapped by with type $fieldClass is not implemented.")
        }

        throw NotImplementedError("Not implemented ${associationInfo.clazz}::${associationInfo.field.name}.")
      }

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
          val fieldClass =
            associationInfo
              .field
              .type

          val field = associationInfo.field
          val mappedByField = mappedByAssociation.field

          val fieldHibernateAccessor = HibernateFieldAccessor(field)
          val mappedByFieldHibernateAccessor = HibernateFieldAccessor(mappedByField)

          field.isAccessible = true
          mappedByField.isAccessible = true

          if (fieldClass == Set::class.java) {
            val fieldCollectionUpdater =
              { entity: Any, elementsToAdd: List<Any>, elementsToRemove: List<Any> ->
                val entityElements = fieldHibernateAccessor.get(entity) as MutableSet<Any>

                // Update the collection in one go
                if (elementsToRemove.isNotEmpty()) {
                  entityElements.removeAll(elementsToRemove.toSet())
                }
                if (elementsToAdd.isNotEmpty()) {
                  entityElements.addAll(elementsToAdd)
                }
              }

            val mutator = object : HibernateEntityCollectionMutator {
              override fun set(entity: Any, values: Collection<Any>) {
                if (!Hibernate.isInitialized(entity)) {
                  Hibernate.initialize(entity)
                }

                val entityElements = fieldHibernateAccessor.get(entity) as MutableSet<Any>

                val newValues = values.toSet() // Convert to Set for better performance in contains() operations

                // Find elements to remove (those in current collection but not in new values)
                val elementsToRemove = entityElements.filter { it !in newValues }
                for (elementToRemove in elementsToRemove) {
                  mappedByFieldHibernateAccessor.set(elementToRemove, null)
                }

                // Find elements to add (those in new values but not in current collection)
                val elementsToAdd = newValues.filter { it !in entityElements }
                for (elementToAdd in elementsToAdd) {
                  if (mappedByFieldHibernateAccessor.get(elementToAdd) != null) {
                    throw IllegalStateException("Entity already associated with another entity")
                  }
                  mappedByFieldHibernateAccessor.set(elementToAdd, entity)
                }

                fieldCollectionUpdater(
                  entity,
                  elementsToAdd,
                  elementsToRemove
                )
              }

              override fun rawSet(entity: Any, values: Collection<Any>) {
                fieldHibernateAccessor.set(entity, values)
              }

              override fun remove(entity: Any, value: Any) {
                if (mappedByFieldHibernateAccessor.get(value) == entity) {
                  val entityElements = fieldHibernateAccessor.get(entity) as MutableSet<Any>
                  mappedByFieldHibernateAccessor.set(value, null)
                  entityElements.remove(value)
                } else {
                  throw IllegalStateException("Entity associated with another entity")
                }
              }

              override fun add(entity: Any, value: Any) {
                val mappedByValue = mappedByFieldHibernateAccessor.get(value)

                if (mappedByValue != null && mappedByValue != entity) {
                  throw IllegalStateException("Entity associated with another entity")
                }

                val entityElements = fieldHibernateAccessor.get(entity) as MutableSet<Any>

                mappedByFieldHibernateAccessor.set(value, entity)
                entityElements.add(value)
              }
            }

            entityCollectionMutators[
              FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName(
                className = associationInfo.clazz.name,
                fieldName = associationInfo.field.name
              )
            ] = mutator

            entityFieldStateInitializers[
              ClassWithFieldName(
                clazz = associationInfo.clazz,
                fieldName = associationInfo.field.name
              )
            ] = object : EntityFieldStateInitializer {
              override fun initialize(entity: Any) {
                val collection = fieldHibernateAccessor.get(entity) as MutableSet<Any>

                if (collection.isNotEmpty()) {
                  fieldHibernateAccessor.set(entity, mutableSetOf<Any>())
                  mutator.set(entity, collection)
                }
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

        val fieldHibernateAccessor = HibernateFieldAccessor(field)

        if (fieldClass == Set::class.java) {
          val mutator =
            object : HibernateEntityCollectionMutator {
              override fun set(entity: Any, values: Collection<Any>) {
                val entityElements = fieldHibernateAccessor.get(entity) as MutableSet<Any>

                entityElements.clear()
                entityElements.addAll(values)
              }

              override fun rawSet(entity: Any, values: Collection<Any>) {
                fieldHibernateAccessor.set(entity, values)
              }

              override fun remove(entity: Any, value: Any) {
                val entityElements = fieldHibernateAccessor.get(entity) as MutableSet<Any>

                entityElements.remove(value)
              }

              override fun add(entity: Any, value: Any) {
                val entityElements = fieldHibernateAccessor.get(entity) as MutableSet<Any>

                entityElements.add(value)
              }
            }

          entityCollectionMutators[
            FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName(
              className = associationInfo.clazz.name,
              fieldName = associationInfo.field.name
            )
          ] = mutator

          entityFieldStateInitializers[
            ClassWithFieldName(
              clazz = associationInfo.clazz,
              fieldName = associationInfo.field.name
            )
          ] = object : EntityFieldStateInitializer {
            override fun initialize(entity: Any) {}
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

            val fieldHibernateAccessor = HibernateFieldAccessor(field)
            val mappedFromFieldHibernateAccessor = HibernateFieldAccessor(mappedFromField)

            val mutator =
              object : HibernateEntityRefMutator {
                override fun set(entity: Any, value: Any?) {
                  val currentValue = fieldHibernateAccessor.get(entity)

                  if (currentValue != value) {
                    if (currentValue != null) {
                      val currentMappedFromCollection =
                        mappedFromFieldHibernateAccessor.get(currentValue) as MutableSet<Any>
                      if (Hibernate.isInitialized(currentMappedFromCollection)) {
                        currentMappedFromCollection.remove(entity)
                      }
                    }

                    if (value == null) {
                      fieldHibernateAccessor.set(entity, null)
                    } else {
                      val newCollection = mappedFromFieldHibernateAccessor.get(value) as MutableSet<Any>
                      if (Hibernate.isInitialized(newCollection)) {
                        newCollection.add(entity)
                      }

                      fieldHibernateAccessor.set(entity, value)
                    }
                  }
                }
              }

            entityRefMutators[
              FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName(
                className = associationInfo.clazz.name,
                fieldName = associationInfo.field.name
              )
            ] = mutator

            entityFieldStateInitializers[
              ClassWithFieldName(
                clazz = associationInfo.clazz,
                fieldName = associationInfo.field.name
              )
            ] = object : EntityFieldStateInitializer {
              override fun initialize(entity: Any) {
                val value = fieldHibernateAccessor.get(entity)
                if (value != null) {
                  if (Hibernate.isInitialized(value)) {
                    val newCollection = mappedFromFieldHibernateAccessor.get(value) as MutableSet<Any>
                    if (Hibernate.isInitialized(newCollection)) {
                      newCollection.add(entity)
                    }
                  }
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

        val fieldHibernateAccessor = HibernateFieldAccessor(field)

        entityRefMutators[
          FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName(
            className = associationInfo.clazz.name,
            fieldName = associationInfo.field.name
          )
        ] = object : HibernateEntityRefMutator {
          override fun set(entity: Any, value: Any?) {
            fieldHibernateAccessor.set(entity, value)
          }
        }

        entityFieldStateInitializers[
          ClassWithFieldName(
            clazz = associationInfo.clazz,
            fieldName = associationInfo.field.name
          )
        ] = object : EntityFieldStateInitializer {
          override fun initialize(entity: Any) {}
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

      if (oneToOneAnnotation != null) {
        val bidirectionalAssociation =
          if (associationInfo.mappedFrom == null) {
            associationInfo.mappedBy
          } else {
            associationInfo.mappedFrom
          }

        if (bidirectionalAssociation != null) {
          val field = associationInfo.field
          val bidirectionalField = bidirectionalAssociation.field

          val fieldHibernateAccessor = HibernateFieldAccessor(field)
          val bidirectionalFieldHibernateAccessor = HibernateFieldAccessor(bidirectionalField)

          field.isAccessible = true
          bidirectionalField.isAccessible = true

          val mutator =
            object : HibernateEntityRefMutator {
              override fun set(entity: Any, value: Any?) {
                val currentValue = fieldHibernateAccessor.get(entity)

                if (currentValue != value) {
                  if (currentValue != null) {
                    bidirectionalFieldHibernateAccessor.set(currentValue, null)
                  }

                  if (value != null) {
                    bidirectionalFieldHibernateAccessor.set(value, entity)
                    fieldHibernateAccessor.set(entity, value)
                  } else {
                    fieldHibernateAccessor.set(entity, null)
                  }
                }
              }
            }

          entityRefMutators[
            FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName(
              className = associationInfo.clazz.name,
              fieldName = associationInfo.field.name
            )
          ] = mutator

          entityFieldStateInitializers[
            ClassWithFieldName(
              clazz = associationInfo.clazz,
              fieldName = associationInfo.field.name
            )
          ] = object : EntityFieldStateInitializer {
            override fun initialize(entity: Any) {
              val value = fieldHibernateAccessor.get(entity)
              if (value != null) {
                bidirectionalFieldHibernateAccessor.set(value, entity)
              }
            }
          }

          continue
        }

        val field = associationInfo.field
        field.isAccessible = true
        val fieldName = field.name

        val fieldHibernateAccessor = HibernateFieldAccessor(field)

        entityRefMutators[
          FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName(
            className = associationInfo.clazz.name,
            fieldName = associationInfo.field.name
          )
        ] = object : HibernateEntityRefMutator {
          override fun set(entity: Any, value: Any?) {
            fieldHibernateAccessor.set(entity, value)
          }
        }

        entityFieldStateInitializers[
          ClassWithFieldName(
            clazz = associationInfo.clazz,
            fieldName = associationInfo.field.name
          )
        ] = object : EntityFieldStateInitializer {
          override fun initialize(entity: Any) {}
        }

        continue
      }

      throw NotImplementedError("Not implemented ${associationInfo.clazz}::${associationInfo.field.name}.")
    }

    this.entityCollectionMutators = entityCollectionMutators
    this.entityRefMutators = entityRefMutators
    this.entityInitializers =
      entityFieldStateInitializers
        .map { it }
        .groupBy { it.key.clazz }
        .mapValues {
          EntityStateInitializer(
            it.value.map { it.value }
          )
        }
  }
}
