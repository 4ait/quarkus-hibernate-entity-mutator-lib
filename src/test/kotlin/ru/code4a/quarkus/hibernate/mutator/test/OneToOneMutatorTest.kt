package ru.code4a.quarkus.hibernate.mutator.test

import org.hibernate.Hibernate
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.code4a.quarkus.hibernate.mutator.test.entities.Passport
import ru.code4a.quarkus.hibernate.mutator.test.entities.Person
import ru.code4a.quarkus.hibernate.mutator.test.support.TestDatabase
import kotlin.test.assertEquals

/**
 * Bidirectional `@OneToOne`: [Person.passport] holds the foreign key, [Passport.person] is its inverse side.
 *
 * A partner that is not loaded is loaded with the change, one query for all such partners of the same type.
 * Its reference cannot be fixed later, as an inverse collection is: Hibernate has no hook after it loads
 * the partner, and a partner loaded without the fix would point to the owner that the database still holds.
 * Writing to the unloaded partner directly would cost a second query for the old value of the written attribute.
 */
class OneToOneMutatorTest {
  @BeforeEach
  fun createPeopleAndPassports() {
    TestDatabase.removeAllData()
    TestDatabase.inTransaction { session ->
      val firstPassport = Passport.create(session, id = 1)
      Passport.create(session, id = 2)
      Person.create(session, id = 1, passport = firstPassport)
      Person.create(session, id = 2)
    }
  }

  @Test
  fun changeOwningSide_withLoadedPartners_issuesNoQueriesAndUpdatesInverseSidesAtOnce() {
    val observed =
      TestDatabase.inTransaction { session ->
        val person = session.find(Person::class.java, 1L)
        val previousPassport = session.find(Passport::class.java, 1L)
        val nextPassport = session.find(Passport::class.java, 2L)
        previousPassport.getPerson()
        nextPassport.getPerson()
        val change = TestDatabase.recordStatements { person.changePassport(nextPassport) }

        change.statements to (previousPassport.getPerson()?.id to nextPassport.getPerson()?.id)
      }

    assertEquals(emptyList<String>() to (null to 1L), observed)
  }

  @Test
  fun changeOwningSide_withUnloadedPartners_loadsThemInOneQueryAndKeepsInverseSidesInLine() {
    val observed =
      TestDatabase.inTransaction { session ->
        val person = session.find(Person::class.java, 1L)
        val previousPassport = person.getPassport()!!
        val nextPassport = session.getReference(Passport::class.java, 2L)
        val change = TestDatabase.recordStatements { person.changePassport(nextPassport) }
        val partnersLoadedByChange = Hibernate.isInitialized(previousPassport) || Hibernate.isInitialized(nextPassport)
        val load =
          TestDatabase.recordStatements {
            previousPassport.getPerson()?.id to nextPassport.getPerson()?.id
          }

        ReferenceChangeObservation(
          changeStatements = change.statements,
          partnersLoadedByChange = partnersLoadedByChange,
          // No query of test_person, which still holds the old foreign key until the flush.
          readStatements = load.statements,
          previousPartnerPerson = load.result.first,
          nextPartnerPerson = load.result.second
        )
      }

    assertEquals(
      ReferenceChangeObservation(
        changeStatements = listOf("select test_passport"),
        partnersLoadedByChange = true,
        readStatements = emptyList(),
        previousPartnerPerson = null,
        nextPartnerPerson = 1L
      ),
      observed
    )
  }

  @Test
  fun changeOwningSide_withUnloadedPartners_writesOnlyOwningSideOnFlush() {
    val flushStatements =
      TestDatabase.inTransaction { session ->
        val person = session.find(Person::class.java, 1L)
        val previousPassport = person.getPassport()!!
        val nextPassport = session.getReference(Passport::class.java, 2L)
        person.changePassport(nextPassport)
        previousPassport.getPerson()
        nextPassport.getPerson()

        TestDatabase.recordStatements { session.flush() }.statements
      }

    assertEquals(listOf("update test_person"), flushStatements)
  }

  @Test
  fun changeOwningSide_isVisibleFromInverseSideInNextSession() {
    TestDatabase.inTransaction { session ->
      val person = session.find(Person::class.java, 1L)
      person.changePassport(session.getReference(Passport::class.java, 2L))
    }

    val inversePersons =
      TestDatabase.inTransaction { session ->
        session.find(Passport::class.java, 1L).getPerson()?.id to session.find(Passport::class.java, 2L).getPerson()?.id
      }

    assertEquals(null to 1L, inversePersons)
  }

  @Test
  fun newOwningSide_withUnloadedPartner_loadsItInOneQueryAndPointsItBack() {
    val observed =
      TestDatabase.inTransaction { session ->
        val passport = session.getReference(Passport::class.java, 2L)
        val creation = TestDatabase.recordStatements { Person.create(session, id = 3, passport = passport) }
        val read = TestDatabase.recordStatements { passport.getPerson()?.id }

        creation.statements to (read.statements to read.result)
      }

    assertEquals(listOf("select test_passport") to (emptyList<String>() to 3L), observed)
  }

  @Test
  fun changeOwningSide_andBack_beforePartnersLoad_keepsOriginalInverseSides() {
    val observed =
      TestDatabase.inTransaction { session ->
        val person = session.find(Person::class.java, 1L)
        val previousPassport = person.getPassport()!!
        val nextPassport = session.getReference(Passport::class.java, 2L)
        person.changePassport(nextPassport)
        person.changePassport(previousPassport)

        previousPassport.getPerson()?.id to nextPassport.getPerson()?.id
      }

    assertEquals(1L to null, observed)
  }

  @Test
  fun changeInverseSide_withLoadedOwningPartner_issuesNoQueriesAndUpdatesForeignKeyOnFlush() {
    val observed =
      TestDatabase.inTransaction { session ->
        val passport = session.find(Passport::class.java, 2L)
        val person = session.find(Person::class.java, 2L)
        passport.getPerson()
        val change = TestDatabase.recordStatements { passport.changePerson(person) }
        val flush = TestDatabase.recordStatements { session.flush() }

        Triple(change.statements, person.getPassport() === passport, flush.statements)
      }

    assertEquals(Triple(emptyList(), true, listOf("update test_person")), observed)
  }

  @Test
  fun changeInverseSide_withUnloadedOwningPartner_loadsItBecauseItsForeignKeyChanges() {
    val observed =
      TestDatabase.inTransaction { session ->
        val passport = session.find(Passport::class.java, 2L)
        passport.getPerson()
        val person = session.getReference(Person::class.java, 2L)
        val change = TestDatabase.recordStatements { passport.changePerson(person) }

        change.statements to (person.getPassport() === passport)
      }

    assertEquals(listOf("select test_person") to true, observed)
  }

  private data class ReferenceChangeObservation(
    val changeStatements: List<String>,
    val partnersLoadedByChange: Boolean,
    val readStatements: List<String>,
    val previousPartnerPerson: Long?,
    val nextPartnerPerson: Long?
  )
}
