package ru.code4a.quarkus.hibernate.mutator.bytebuddy

import ru.code4a.quarkus.hibernate.mutator.builds.FindAllHibernateAssociationsInfoBuildStep
import ru.code4a.quarkus.hibernate.mutator.services.HibernateEntityConstructorTracker
import ru.code4a.quarkus.hibernate.mutator.services.HibernateEntityMutatorProcessor
import ru.code4a.quarkus.hibernate.mutator.services.HibernateEntityMutators.entityCollectionMutators
import ru.code4a.quarkus.hibernate.mutator.services.HibernateEntityMutators.entityInitializers
import ru.code4a.quarkus.hibernate.mutator.utils.nullable.unwrapElseError

object HibernateEntityMutatorInjectedProcessor {
  @JvmStatic
  fun invokeBeforeSet(entity: Any, fieldName: String, value: Any?) {
    if (HibernateEntityConstructorTracker.isEntityConstructorInProgress()) {
      return
    }

    HibernateEntityMutatorProcessor.processBeforeSet(entity, fieldName, value)
  }

  @JvmStatic
  fun invokeSetCollection(entity: Any, fieldName: String, values: Collection<Any>) {
    println("Invoke set collection ${entity.javaClass.name} ${fieldName} ${values}")

    if (HibernateEntityConstructorTracker.isEntityConstructorInProgress()) {
      HibernateEntityMutatorProcessor.processRawSetCollection(entity, fieldName, values)
      return
    }

    HibernateEntityMutatorProcessor.processSetCollection(entity, fieldName, values)
  }

  @JvmStatic
  fun initializeEntity(entity: Any) {
    if (HibernateEntityConstructorTracker.isEntityConstructorInProgress()) {
      return
    }

    HibernateEntityMutatorProcessor.initializeEntity(entity)
  }
}
