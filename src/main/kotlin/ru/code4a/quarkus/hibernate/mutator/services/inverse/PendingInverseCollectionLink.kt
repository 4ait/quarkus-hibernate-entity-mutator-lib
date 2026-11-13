package ru.code4a.quarkus.hibernate.mutator.services.inverse

import org.hibernate.Hibernate
import org.hibernate.engine.spi.SharedSessionContractImplementor
import ru.code4a.quarkus.hibernate.mutator.utils.hibernate.HibernateFieldAccessor
import ru.code4a.quarkus.hibernate.mutator.utils.hibernate.HibernateLazyState
import java.lang.ref.WeakReference

/**
 * A change of a child's `@ManyToOne` reference that its owner's inverse `@OneToMany` collection
 * has not seen yet, because the collection was not loaded when the change happened.
 *
 * Once the collection is loaded, the link puts the child into it or takes it out, depending on
 * where the child points at that moment. The latest state wins, so several changes of the same
 * child are applied correctly in any order.
 */
internal class PendingInverseCollectionLink(
  owner: Any,
  child: Any,
  private val childToOwnerAccessor: HibernateFieldAccessor,
  private val ownerToCollectionAccessor: HibernateFieldAccessor
) {
  private val ownerReference = WeakReference(owner)
  private val childReference = WeakReference(child)

  /**
   * Whether the collection is in memory now, so the link can be applied without loading anything,
   * or the link no longer matters because its entities are gone.
   */
  fun isApplicableOrObsolete(): Boolean {
    val owner = ownerReference.get() ?: return true
    childReference.get() ?: return true

    val loadedOwner = HibernateLazyState.loadedEntityOrNull(owner) ?: return false
    val collection = ownerToCollectionAccessor.get(loadedOwner) ?: return true

    return Hibernate.isInitialized(collection)
  }

  /**
   * Whether a flush wrote the child change to the database, so the collection loaded later contains it.
   */
  fun isWrittenToDatabase(session: SharedSessionContractImplementor): Boolean {
    val child = childReference.get() ?: return true

    return HibernateLazyState.isManaged(child, session)
  }

  fun applyToLoadedCollection(session: SharedSessionContractImplementor) {
    val owner = ownerReference.get() ?: return
    val child = childReference.get() ?: return
    val loadedOwner = HibernateLazyState.loadedEntityOrNull(owner) ?: return

    @Suppress("UNCHECKED_CAST")
    val collection = ownerToCollectionAccessor.get(loadedOwner) as? MutableSet<Any> ?: return
    val childPointsToOwner = HibernateLazyState.refersTo(childToOwnerAccessor.get(child), loadedOwner)

    if (childPointsToOwner && !HibernateLazyState.isRemoved(child, session)) {
      collection.add(child)
    } else {
      collection.remove(child)
    }
  }
}
