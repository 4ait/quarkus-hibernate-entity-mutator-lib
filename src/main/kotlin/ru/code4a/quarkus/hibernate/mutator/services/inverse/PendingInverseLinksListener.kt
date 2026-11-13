package ru.code4a.quarkus.hibernate.mutator.services.inverse

import org.hibernate.event.spi.AutoFlushEvent
import org.hibernate.event.spi.AutoFlushEventListener
import org.hibernate.event.spi.FlushEvent
import org.hibernate.event.spi.FlushEventListener
import org.hibernate.event.spi.InitializeCollectionEvent
import org.hibernate.event.spi.InitializeCollectionEventListener

/**
 * Runs after the default Hibernate listeners:
 * - when a collection is loaded, applies the inverse changes that waited for it;
 * - after a flush, forgets the changes the flush wrote to the database.
 */
internal object PendingInverseLinksListener :
  InitializeCollectionEventListener,
  FlushEventListener,
  AutoFlushEventListener {
  override fun onInitializeCollection(event: InitializeCollectionEvent) {
    PendingInverseLinks.applyToLoadedCollections(event.session)
  }

  override fun onFlush(event: FlushEvent) {
    PendingInverseLinks.forgetWrittenToDatabase(event.session)
  }

  override fun onAutoFlush(event: AutoFlushEvent) {
    if (event.isFlushRequired) {
      PendingInverseLinks.forgetWrittenToDatabase(event.session)
    }
  }
}
