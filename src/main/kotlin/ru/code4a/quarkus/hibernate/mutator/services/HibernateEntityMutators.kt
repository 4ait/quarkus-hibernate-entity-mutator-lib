package ru.code4a.quarkus.hibernate.mutator.services

import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import kotlinx.serialization.json.Json
import org.hibernate.Hibernate
import org.hibernate.bytecode.enhance.internal.tracker.SimpleFieldTracker
import ru.code4a.quarkus.hibernate.mutator.builds.FindAllHibernateAssociationsInfoBuildStep
import ru.code4a.quarkus.hibernate.mutator.interfaces.EntityFieldStateInitializer
import ru.code4a.quarkus.hibernate.mutator.interfaces.EntityStateInitializer
import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityCollectionMutator
import ru.code4a.quarkus.hibernate.mutator.mutators.interfaces.HibernateEntityRefMutator
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
      val trackChangeMethod = associationInfo.clazz.getTrackChangeMethod()

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
          val mutator =
            object : HibernateEntityCollectionMutator {
              override fun set(entity: Any, values: Collection<Any>) {
                val entityElements = field.get(entity) as MutableSet<Any>

                entityElements.clear()
                entityElements.addAll(values)
              }

              override fun beforeSetManual(entity: Any, values: Collection<Any>) {}
              override fun rawSet(entity: Any, values: Collection<Any>) {
                field.set(entity, values)
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
          val mappedByFieldName = mappedByField.name
          val mappedByAssociationTrackChangeMethod = mappedByAssociation.clazz.getTrackChangeMethod()

          val mappedByFieldSetter = { obj: Any, value: Any? ->
            mappedByAssociationTrackChangeMethod.invoke(obj, mappedByFieldName)
            mappedByField.set(obj, value)
          }

          field.isAccessible = true
          mappedByField.isAccessible = true

          if (fieldClass == Set::class.java) {
            val fieldSetter =
              { entity: Any, elementsToAdd: List<Any>, elementsToRemove: List<Any> ->
                val entityElements = field.get(entity) as MutableSet<Any>

                // Update the collection in one go
                if (elementsToRemove.isNotEmpty()) {
                  entityElements.removeAll(elementsToRemove.toSet())
                }
                if (elementsToAdd.isNotEmpty()) {
                  entityElements.addAll(elementsToAdd)
                }
              }

            val preprocessAndSetWithFieldSetter =
              { fieldSetter: (entity: Any, elementsToAdd: List<Any>, elementsToRemove: List<Any>) -> Unit, entity: Any, values: Collection<Any> ->
                val entityElements = field.get(entity) as MutableSet<Any>

                val newValues = values.toSet() // Convert to Set for better performance in contains() operations

                // Find elements to remove (those in current collection but not in new values)
                val elementsToRemove = entityElements.filter { it !in newValues }
                for (elementToRemove in elementsToRemove) {
                  mappedByFieldSetter.invoke(elementToRemove, null)
                }

                // Find elements to add (those in new values but not in current collection)
                val elementsToAdd = newValues.filter { it !in entityElements }
                for (elementToAdd in elementsToAdd) {
                  if (mappedByField.get(elementToAdd) != null) {
                    throw IllegalStateException("Entity already associated with another entity")
                  }
                  mappedByFieldSetter.invoke(elementToAdd, entity)
                }

                fieldSetter(
                  entity,
                  elementsToAdd,
                  elementsToRemove
                )
              }

            val mutator = object : HibernateEntityCollectionMutator {
              override fun set(entity: Any, values: Collection<Any>) {
                preprocessAndSetWithFieldSetter.invoke(fieldSetter, entity, values)
              }

              override fun beforeSetManual(entity: Any, values: Collection<Any>) {
                preprocessAndSetWithFieldSetter.invoke({ _, _, _ -> }, entity, values)
              }

              override fun rawSet(entity: Any, values: Collection<Any>) {
                field.set(entity, values)
              }

              override fun remove(entity: Any, value: Any) {
                if (mappedByField.get(value) == entity) {
                  val entityElements = field.get(entity) as MutableSet<Any>
                  mappedByFieldSetter.invoke(value, null)
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

                mappedByFieldSetter.invoke(value, entity)
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
                // This happens if kotlin generate constructor with empty params
                val collection = field.get(entity) as MutableSet<Any>

                if (collection.isNotEmpty()) {
                  field.set(entity, mutableSetOf<Any>())
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

        if (fieldClass == Set::class.java) {
          val mutator =
            object : HibernateEntityCollectionMutator {
              override fun set(entity: Any, values: Collection<Any>) {
                val entityElements = field.get(entity) as MutableSet<Any>

                entityElements.clear()
                entityElements.addAll(values)
              }

              override fun beforeSetManual(entity: Any, values: Collection<Any>) {}
              override fun rawSet(entity: Any, values: Collection<Any>) {
                field.set(entity, values)
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
            val fieldName = field.name

            val mappedFromField = mappedFromAssociation.field
            mappedFromField.isAccessible = true

            val fieldSetter = { obj: Any, value: Any? ->
              trackChangeMethod.invoke(obj, fieldName)
              field.set(obj, value)
            }

            val preprocessAndSetWithFieldSetter =
              { fieldSetter: (entity: Any, newValue: Any?) -> Unit, entity: Any, newValue: Any? ->
                val currentValue = field.get(entity)

                if (currentValue != newValue) {
                  if (currentValue != null) {
                    val currentMappedFromCollection = mappedFromField.get(currentValue) as MutableSet<Any>
                    if (Hibernate.isInitialized(currentMappedFromCollection)) {
                      currentMappedFromCollection.remove(entity)
                    }
                  }

                  if (newValue == null) {
                    fieldSetter.invoke(entity, null)
                  } else {
                    val newCollection = mappedFromField.get(newValue) as MutableSet<Any>
                    if (Hibernate.isInitialized(newCollection)) {
                      newCollection.add(entity)
                    }

                    fieldSetter.invoke(entity, newValue)
                  }
                }
              }

            val mutator =
              object : HibernateEntityRefMutator {
                override fun beforeSetManual(entity: Any, value: Any?) {
                  preprocessAndSetWithFieldSetter({ _, _ -> }, entity, value)
                }

                override fun set(entity: Any, value: Any?) {
                  preprocessAndSetWithFieldSetter(fieldSetter, entity, value)
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
                val value = field.get(entity)
                if (value != null) {
                  val newCollection = mappedFromField.get(value) as MutableSet<Any>
                  if (Hibernate.isInitialized(newCollection)) {
                    newCollection.add(entity)
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
        val fieldName = field.name

        val fieldSetter = { obj: Any, value: Any? ->
          trackChangeMethod.invoke(obj, fieldName)
          field.set(obj, value)
        }

        entityRefMutators[
          FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName(
            className = associationInfo.clazz.name,
            fieldName = associationInfo.field.name
          )
        ] = object : HibernateEntityRefMutator {
          override fun beforeSetManual(entity: Any, value: Any?) {}

          override fun set(entity: Any, value: Any?) {
            fieldSetter.invoke(entity, value)
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
          val fieldName = field.name

          val bidirectionalField = bidirectionalAssociation.field
          val bidirectionalFieldName = bidirectionalField.name
          val bidirectionalHibernateTrackChangeMethod = bidirectionalAssociation.clazz.getTrackChangeMethod()

          val fieldSetter = { obj: Any, value: Any? ->
            trackChangeMethod.invoke(obj, fieldName)
            field.set(obj, value)
          }

          val bidirectionalFieldSetter = { obj: Any, value: Any? ->
            bidirectionalHibernateTrackChangeMethod.invoke(obj, bidirectionalFieldName)
            bidirectionalField.set(obj, value)
          }

          field.isAccessible = true
          bidirectionalField.isAccessible = true

          val preprocessAndSetWithFieldSetter =
            { fieldSetter: (entity: Any, newValue: Any?) -> Unit, entity: Any, value: Any? ->
              val currentValue = field.get(entity)

              if (currentValue != value) {
                if (currentValue != null) {
                  bidirectionalFieldSetter.invoke(currentValue, null)
                }

                if (value != null) {
                  bidirectionalFieldSetter.invoke(value, entity)
                  fieldSetter.invoke(entity, value)
                } else {
                  fieldSetter.invoke(entity, null)
                }
              }
            }

          val mutator =
            object : HibernateEntityRefMutator {
              override fun beforeSetManual(entity: Any, value: Any?) {
                preprocessAndSetWithFieldSetter({ _, _ -> }, entity, value)
              }

              override fun set(entity: Any, value: Any?) {
                preprocessAndSetWithFieldSetter(fieldSetter, entity, value)
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
              val value = field.get(entity)
              if (value != null) {
                bidirectionalFieldSetter.invoke(value, entity)
              }
            }
          }

          continue
        }

        val field = associationInfo.field
        field.isAccessible = true
        val fieldName = field.name

        val fieldSetter = { obj: Any, value: Any? ->
          trackChangeMethod.invoke(obj, fieldName)
          field.set(obj, value)
        }

        entityRefMutators[
          FindAllHibernateAssociationsInfoBuildStep.ClassNameWithFieldName(
            className = associationInfo.clazz.name,
            fieldName = associationInfo.field.name
          )
        ] = object : HibernateEntityRefMutator {
          override fun beforeSetManual(entity: Any, value: Any?) {}

          override fun set(entity: Any, value: Any?) {
            fieldSetter.invoke(entity, value)
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

private fun Class<*>.getTrackChangeMethod(): (Any, String) -> Unit {
  val trackerField = declaredFields.find { it.name == "\$\$_hibernate_tracker" }!!

  trackerField.isAccessible = true

  return { obj: Any, fieldName: String ->
    val trackerFieldValue =
      trackerField.get(obj).let { trackerFieldValue ->
        if (trackerFieldValue == null) {
          val newTrackerFieldValue = SimpleFieldTracker()

          trackerField.set(obj, newTrackerFieldValue)

          newTrackerFieldValue
        } else {
          trackerFieldValue as SimpleFieldTracker
        }
      }

    trackerFieldValue.add(fieldName)
  }
}
