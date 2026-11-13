package ru.code4a.quarkus.hibernate.mutator.services.inverse

import org.hibernate.Hibernate
import ru.code4a.quarkus.hibernate.mutator.utils.hibernate.HibernateFieldAccessor
import ru.code4a.quarkus.hibernate.mutator.utils.hibernate.HibernateLazyState

/**
 * Keeps the inverse `@OneToMany` collection of an owner in line with the `@ManyToOne` reference of a child.
 *
 * A collection that is already in memory is changed at once. A collection that is not loaded yet
 * (or belongs to an owner that is not loaded yet) is not loaded for this: the change is remembered
 * in [PendingInverseLinks] and applied when Hibernate loads the collection, so the mutator
 * never issues database queries on its own.
 */
internal class InverseCollectionSynchronizer(
  private val childToOwnerAccessor: HibernateFieldAccessor,
  private val ownerToCollectionAccessor: HibernateFieldAccessor
) {
  fun addChild(owner: Any, child: Any) {
    val collection = loadedCollectionOrNull(owner)

    if (collection != null) {
      collection.add(child)
    } else {
      rememberUntilCollectionIsLoaded(owner, child)
    }
  }

  fun removeChild(owner: Any, child: Any) {
    val collection = loadedCollectionOrNull(owner)

    if (collection != null) {
      collection.remove(child)
    } else {
      rememberUntilCollectionIsLoaded(owner, child)
    }
  }

  private fun loadedCollectionOrNull(owner: Any): MutableSet<Any>? {
    val loadedOwner = HibernateLazyState.loadedEntityOrNull(owner) ?: return null

    @Suppress("UNCHECKED_CAST")
    val collection = ownerToCollectionAccessor.get(loadedOwner) as? MutableSet<Any> ?: return null

    return collection.takeIf(Hibernate::isInitialized)
  }

  private fun rememberUntilCollectionIsLoaded(owner: Any, child: Any) {
    // A collection of a new or detached owner is never loaded into a session later, so there is nothing to wait for.
    val session = sessionOfOwnerCollection(owner) ?: return

    PendingInverseLinks.remember(
      session,
      PendingInverseCollectionLink(
        owner = owner,
        child = child,
        childToOwnerAccessor = childToOwnerAccessor,
        ownerToCollectionAccessor = ownerToCollectionAccessor
      )
    )
  }

  private fun sessionOfOwnerCollection(owner: Any) =
    when (val loadedOwner = HibernateLazyState.loadedEntityOrNull(owner)) {
      null -> HibernateLazyState.sessionOf(owner)
      else -> ownerToCollectionAccessor.get(loadedOwner)?.let(HibernateLazyState::sessionOf)
    }
}
