package ru.code4a.quarkus.hibernate.mutator.services.inverse

import org.hibernate.SessionEventListener
import org.hibernate.engine.spi.SharedSessionContractImplementor
import java.util.concurrent.ConcurrentHashMap

/**
 * Inverse collection changes that wait until Hibernate loads their collection, kept per session.
 *
 * Loading a collection only to keep it in sync would read the whole collection from the database,
 * so a mutator remembers the change instead. [PendingInverseLinksListener] applies it right after
 * Hibernate loads the collection and drops it once a flush wrote the change to the database.
 *
 * Every collection load looks the session up here, so the lookup takes no lock;
 * a session's entry is removed when the session closes.
 */
internal object PendingInverseLinks {
  private val linksBySession = ConcurrentHashMap<SharedSessionContractImplementor, SessionLinks>()

  fun remember(session: SharedSessionContractImplementor, link: PendingInverseCollectionLink) {
    linksOf(session).add(link)
  }

  /**
   * Applies the links whose collections are loaded now. Hibernate may load several collections
   * at once (batch fetching), so every link of the session is checked.
   */
  fun applyToLoadedCollections(session: SharedSessionContractImplementor) {
    linksBySession[session]?.applyLoaded(session)
  }

  /**
   * Drops the links whose changes a flush wrote to the database: collections loaded from now on contain them.
   */
  fun forgetWrittenToDatabase(session: SharedSessionContractImplementor) {
    linksBySession[session]?.forgetWritten(session)
  }

  /**
   * Number of links waiting in [session]; tests use it to check that nothing is left behind.
   */
  fun countWaiting(session: SharedSessionContractImplementor): Int =
    linksBySession[session]?.size ?: 0

  private fun linksOf(session: SharedSessionContractImplementor): SessionLinks =
    linksBySession.computeIfAbsent(session) {
      session.eventListenerManager.addListener(RemoveLinksOnSessionEnd(session))
      SessionLinks()
    }

  private class RemoveLinksOnSessionEnd(
    private val session: SharedSessionContractImplementor
  ) : SessionEventListener {
    override fun end() {
      linksBySession.remove(session)
    }
  }

  /**
   * Links of one session. A session is used by one thread at a time, so no synchronization is needed.
   */
  private class SessionLinks {
    private var links = mutableListOf<PendingInverseCollectionLink>()

    val size: Int
      get() = links.size

    fun add(link: PendingInverseCollectionLink) {
      links.add(link)
    }

    fun applyLoaded(session: SharedSessionContractImplementor) {
      if (links.isEmpty()) return

      // Applying a link must not see links added meanwhile, so the applicable ones are taken out first.
      val (applicable, waiting) = links.partition(PendingInverseCollectionLink::isApplicableOrObsolete)
      if (applicable.isEmpty()) return

      links = waiting.toMutableList()
      applicable.forEach { it.applyToLoadedCollection(session) }
    }

    fun forgetWritten(session: SharedSessionContractImplementor) {
      links = links.filterNotTo(mutableListOf()) { it.isWrittenToDatabase(session) }
    }
  }
}
