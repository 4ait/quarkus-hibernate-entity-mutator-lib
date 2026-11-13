package ru.code4a.quarkus.hibernate.mutator.test.entities

import org.hibernate.Session
import ru.code4a.quarkus.hibernate.mutator.services.HibernateEntityMutatorProcessor

/**
 * Runs the mutator initializers of a new entity, as applications do before persisting it.
 */
fun <T : Any> initializeNewEntity(entity: T): T {
  HibernateEntityMutatorProcessor.initializeEntityIfInitializerPresent(entity)
  return entity
}

fun <T : Any> persistNewEntity(session: Session, entity: T): T {
  initializeNewEntity(entity)
  session.persist(entity)
  return entity
}
