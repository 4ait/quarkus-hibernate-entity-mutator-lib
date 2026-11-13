package ru.code4a.quarkus.hibernate.mutator.utils.hibernate

import org.hibernate.Hibernate
import org.hibernate.bytecode.enhance.spi.interceptor.SessionAssociableInterceptor
import org.hibernate.collection.spi.AbstractPersistentCollection
import org.hibernate.engine.spi.PersistentAttributeInterceptable
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.engine.spi.Status
import org.hibernate.proxy.HibernateProxy

/**
 * Inspects Hibernate entities and collections without triggering lazy loading.
 *
 * Mutators use it to decide whether a side of an association is already in memory.
 * Anything that is not loaded yet is left alone: Hibernate loads it from the database later.
 */
internal object HibernateLazyState {
  /**
   * Returns the entity instance behind [reference] if it is already loaded, otherwise `null`.
   * Unlike [Hibernate.unproxy] it never initializes an uninitialized proxy.
   */
  fun loadedEntityOrNull(reference: Any): Any? =
    if (Hibernate.isInitialized(reference)) Hibernate.unproxy(reference) else null

  /**
   * Loads an entity that is about to be written to and returns it.
   *
   * Writing to an uninitialized reference also works, but Hibernate then loads the entity and, in a separate
   * query, the old value of the written lazy attribute, only to replace it. Loading the entity first costs
   * one query: the write afterwards just marks the attribute as loaded.
   */
  fun loadForWrite(reference: Any): Any {
    Hibernate.initialize(reference)
    return reference
  }

  /**
   * Whether [reference] points to [entity]. A proxy points to the entity it was initialized with;
   * an uninitialized proxy is compared by identity only, so it is never loaded for the comparison.
   */
  fun refersTo(reference: Any?, entity: Any): Boolean {
    if (reference == null) return false
    if (reference === entity) return true

    val loadedEntity = loadedEntityOrNull(entity) ?: return false
    val loadedReference = loadedEntityOrNull(reference) ?: return false

    return loadedReference === loadedEntity
  }

  /**
   * The session an uninitialized collection or entity reference is bound to, or `null`
   * when it is not bound to any session (a new or detached object).
   */
  fun sessionOf(collectionOrEntity: Any): SharedSessionContractImplementor? =
    when (collectionOrEntity) {
      is AbstractPersistentCollection<*> -> collectionOrEntity.session
      is HibernateProxy -> collectionOrEntity.hibernateLazyInitializer.session
      is PersistentAttributeInterceptable ->
        (collectionOrEntity.`$$_hibernate_getInterceptor`() as? SessionAssociableInterceptor)?.linkedSession
      else -> null
    }

  /**
   * Whether [entity] is scheduled for deletion or already deleted in [session].
   */
  fun isRemoved(entity: Any, session: SharedSessionContractImplementor): Boolean =
    when (session.persistenceContextInternal.getEntry(entity)?.status) {
      Status.DELETED, Status.GONE -> true
      else -> false
    }

  /**
   * Whether [entity] has an entry in the persistence context of [session], i.e. it was persisted or loaded.
   */
  fun isManaged(entity: Any, session: SharedSessionContractImplementor): Boolean =
    session.persistenceContextInternal.getEntry(entity) != null
}
