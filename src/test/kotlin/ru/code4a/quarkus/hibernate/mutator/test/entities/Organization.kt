package ru.code4a.quarkus.hibernate.mutator.test.entities

import jakarta.persistence.DiscriminatorColumn
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.Session
import ru.code4a.quarkus.hibernate.mutator.interfaces.HibernateEntityMutationSupport

/**
 * Polymorphic owner: Hibernate cannot use an enhanced instance as a proxy for an entity with subclasses,
 * so references to it are ByteBuddy proxies.
 */
@Entity
@Table(name = "test_organization")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "kind")
abstract class Organization : HibernateEntityMutationSupport {
  @Id
  var id: Long = 0

  @OneToMany(mappedBy = "organization")
  private var members: MutableSet<Member> = mutableSetOf()

  fun membersCollection(): MutableSet<Member> = members

  fun addMember(member: Member) {
    ::members.add(member)
  }
}

@Entity
@DiscriminatorValue("company")
class Company : Organization() {
  companion object {
    fun create(session: Session, id: Long): Company =
      persistNewEntity(session, Company().also { it.id = id })
  }
}

@Entity
@DiscriminatorValue("agency")
class Agency : Organization()

@Entity
@Table(name = "test_member")
class Member : HibernateEntityMutationSupport {
  @Id
  var id: Long = 0

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id")
  private var organization: Organization? = null

  fun getOrganization(): Organization? = organization

  fun changeOrganization(organization: Organization?) {
    ::organization.setRef(organization)
  }

  companion object {
    fun create(session: Session, id: Long, organization: Organization?): Member =
      persistNewEntity(
        session,
        Member().also {
          it.id = id
          it.organization = organization
        }
      )
  }
}
