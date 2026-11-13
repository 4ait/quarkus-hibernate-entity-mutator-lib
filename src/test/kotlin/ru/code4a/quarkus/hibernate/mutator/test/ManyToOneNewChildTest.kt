package ru.code4a.quarkus.hibernate.mutator.test

import org.hibernate.Hibernate
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.code4a.quarkus.hibernate.mutator.test.entities.Company
import ru.code4a.quarkus.hibernate.mutator.test.entities.Department
import ru.code4a.quarkus.hibernate.mutator.test.entities.Employee
import ru.code4a.quarkus.hibernate.mutator.test.entities.Member
import ru.code4a.quarkus.hibernate.mutator.test.entities.Organization
import ru.code4a.quarkus.hibernate.mutator.test.support.TestDatabase
import kotlin.test.assertEquals

/**
 * A new child with a `@ManyToOne` reference joins the inverse collection of its owner
 * (initializer of a bidirectional `@ManyToOne`).
 */
class ManyToOneNewChildTest {
  @BeforeEach
  fun createDepartmentWithEmployee() {
    TestDatabase.removeAllData()
    TestDatabase.inTransaction { session ->
      val department = Department.create(session, id = 1)
      Employee.create(session, id = 10, department = department)
    }
  }

  @Test
  fun newChild_withLoadedOwnerAndUnloadedCollection_issuesNoQueriesAndJoinsCollectionOnLoad() {
    val observed =
      TestDatabase.inTransaction { session ->
        val department = session.find(Department::class.java, 1L)
        val creation = TestDatabase.recordStatements { Employee.create(session, id = 11, department = department) }
        val collectionLoadedByCreation = Hibernate.isInitialized(department.employeesCollection())
        val load = TestDatabase.recordStatements { department.getEmployees().map(Employee::id).toSet() }

        NewChildObservation(
          creationStatements = creation.statements,
          collectionLoadedByCreation = collectionLoadedByCreation,
          collectionLoadStatements = load.statements,
          collectionChildIds = load.result
        )
      }

    assertEquals(
      NewChildObservation(
        creationStatements = emptyList(),
        collectionLoadedByCreation = false,
        collectionLoadStatements = listOf("select test_employee"),
        collectionChildIds = setOf(10L, 11L)
      ),
      observed
    )
  }

  @Test
  fun newChild_withLoadedCollection_issuesNoQueriesAndJoinsCollectionAtOnce() {
    val observed =
      TestDatabase.inTransaction { session ->
        val department = session.find(Department::class.java, 1L)
        department.getEmployees()
        val creation = TestDatabase.recordStatements { Employee.create(session, id = 11, department = department) }
        val read = TestDatabase.recordStatements { department.getEmployees().map(Employee::id).toSet() }

        NewChildObservation(
          creationStatements = creation.statements,
          collectionLoadedByCreation = true,
          collectionLoadStatements = read.statements,
          collectionChildIds = read.result
        )
      }

    assertEquals(
      NewChildObservation(
        creationStatements = emptyList(),
        collectionLoadedByCreation = true,
        collectionLoadStatements = emptyList(),
        collectionChildIds = setOf(10L, 11L)
      ),
      observed
    )
  }

  @Test
  fun newChild_withOwnerReference_issuesNoQueriesAndJoinsCollectionWhenOwnerAndCollectionLoad() {
    val observed =
      TestDatabase.inTransaction { session ->
        val department = session.getReference(Department::class.java, 1L)
        val creation = TestDatabase.recordStatements { Employee.create(session, id = 11, department = department) }
        val ownerLoadedByCreation = Hibernate.isInitialized(department)
        val load = TestDatabase.recordStatements { department.getEmployees().map(Employee::id).toSet() }

        NewChildObservation(
          creationStatements = creation.statements,
          collectionLoadedByCreation = ownerLoadedByCreation,
          collectionLoadStatements = load.statements,
          collectionChildIds = load.result
        )
      }

    assertEquals(
      NewChildObservation(
        creationStatements = emptyList(),
        collectionLoadedByCreation = false,
        collectionLoadStatements = listOf("select test_department", "select test_employee"),
        collectionChildIds = setOf(10L, 11L)
      ),
      observed
    )
  }

  @Test
  fun newChild_withPolymorphicOwnerProxy_issuesNoQueriesAndJoinsCollectionWhenOwnerAndCollectionLoad() {
    TestDatabase.inTransaction { session -> Company.create(session, id = 1) }

    val observed =
      TestDatabase.inTransaction { session ->
        val organization = session.getReference(Organization::class.java, 1L)
        val creation = TestDatabase.recordStatements { Member.create(session, id = 11, organization = organization) }
        val ownerLoadedByCreation = Hibernate.isInitialized(organization)
        val load = TestDatabase.recordStatements { organization.membersCollection().map(Member::id).toSet() }

        NewChildObservation(
          creationStatements = creation.statements,
          collectionLoadedByCreation = ownerLoadedByCreation,
          collectionLoadStatements = load.statements,
          collectionChildIds = load.result
        )
      }

    assertEquals(
      NewChildObservation(
        creationStatements = emptyList(),
        collectionLoadedByCreation = false,
        collectionLoadStatements = listOf("select test_organization", "select test_member"),
        collectionChildIds = setOf(11L)
      ),
      observed
    )
  }

  @Test
  fun newChild_withNewOwner_joinsOwnerCollectionAtOnce() {
    val childIds =
      TestDatabase.inTransaction { session ->
        val department = Department.create(session, id = 2)
        Employee.create(session, id = 20, department = department)

        TestDatabase.recordStatements { department.getEmployees().map(Employee::id).toSet() }
      }

    assertEquals(setOf(20L) to emptyList(), childIds.result to childIds.statements)
  }

  private data class NewChildObservation(
    val creationStatements: List<String>,
    val collectionLoadedByCreation: Boolean,
    val collectionLoadStatements: List<String>,
    val collectionChildIds: Set<Long>
  )
}
