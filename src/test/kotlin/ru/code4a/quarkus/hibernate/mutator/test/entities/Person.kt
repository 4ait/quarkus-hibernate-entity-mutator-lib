package ru.code4a.quarkus.hibernate.mutator.test.entities

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.Session
import ru.code4a.quarkus.hibernate.mutator.interfaces.HibernateEntityMutationSupport

/**
 * Owning side of a bidirectional `@OneToOne`: the foreign key lives in this table.
 */
@Entity
@Table(name = "test_person")
class Person : HibernateEntityMutationSupport {
  @Id
  var id: Long = 0

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "passport_id", unique = true)
  private var passport: Passport? = null

  fun getPassport(): Passport? = passport

  fun changePassport(passport: Passport?) {
    ::passport.setRef(passport)
  }

  companion object {
    fun create(session: Session, id: Long, passport: Passport? = null): Person =
      persistNewEntity(
        session,
        Person().also {
          it.id = id
          it.passport = passport
        }
      )
  }
}

/**
 * Inverse side of [Person.passport].
 */
@Entity
@Table(name = "test_passport")
class Passport : HibernateEntityMutationSupport {
  @Id
  var id: Long = 0

  @OneToOne(mappedBy = "passport", fetch = FetchType.LAZY)
  private var person: Person? = null

  fun getPerson(): Person? = person

  fun changePerson(person: Person?) {
    ::person.setRef(person)
  }

  companion object {
    fun create(session: Session, id: Long): Passport =
      persistNewEntity(session, Passport().also { it.id = id })
  }
}
