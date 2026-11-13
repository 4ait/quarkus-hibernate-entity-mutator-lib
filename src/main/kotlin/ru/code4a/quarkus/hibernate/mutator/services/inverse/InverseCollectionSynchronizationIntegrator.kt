package ru.code4a.quarkus.hibernate.mutator.services.inverse

import org.hibernate.boot.Metadata
import org.hibernate.boot.spi.BootstrapContext
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.event.service.spi.EventListenerRegistry
import org.hibernate.event.spi.EventType
import org.hibernate.integrator.spi.Integrator

/**
 * Registers [PendingInverseLinksListener] in every session factory.
 *
 * Hibernate discovers integrators through `ServiceLoader`
 * (`META-INF/services/org.hibernate.integrator.spi.Integrator`); Quarkus does the same.
 */
class InverseCollectionSynchronizationIntegrator : Integrator {
  override fun integrate(
    metadata: Metadata,
    bootstrapContext: BootstrapContext,
    sessionFactory: SessionFactoryImplementor
  ) {
    val listeners = sessionFactory.serviceRegistry.requireService(EventListenerRegistry::class.java)

    listeners.appendListeners(EventType.INIT_COLLECTION, PendingInverseLinksListener)
    listeners.appendListeners(EventType.FLUSH, PendingInverseLinksListener)
    listeners.appendListeners(EventType.AUTO_FLUSH, PendingInverseLinksListener)
  }
}
