package ru.code4a.quarkus.hibernate.mutator.test

import org.hibernate.Hibernate
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.code4a.quarkus.hibernate.mutator.test.entities.Department
import ru.code4a.quarkus.hibernate.mutator.test.entities.Employee
import ru.code4a.quarkus.hibernate.mutator.test.support.TestDatabase
import kotlin.test.assertEquals

/**
 * Changing the `@ManyToOne` reference of a child moves it between inverse collections
 * (`setRef` of a bidirectional `@ManyToOne`).
 */
class ManyToOneChangeOwnerTest {
  @BeforeEach
  fun createTwoDepartments() {
    TestDatabase.removeAllData()
    TestDatabase.inTransaction { session ->
      val first = Department.create(session, id = 1)
      Department.create(session, id = 2)
      Employee.create(session, id = 10, department = first)
    }
  }

  @Test
  fun changeOwner_betweenUnloadedOwners_issuesNoQueriesAndMovesChildWhenCollectionsLoad() {
    val observed =
      TestDatabase.inTransaction { session ->
        val employee = session.find(Employee::class.java, 10L)
        val previousDepartment = employee.getDepartment()!!
        val nextDepartment = session.getReference(Department::class.java, 2L)
        val change = TestDatabase.recordStatements { employee.changeDepartment(nextDepartment) }
        val ownersLoadedByChange = Hibernate.isInitialized(previousDepartment) || Hibernate.isInitialized(nextDepartment)
        val load =
          TestDatabase.recordStatements {
            employeeIds(previousDepartment) to employeeIds(nextDepartment)
          }

        ChangeObservation(
          changeStatements = change.statements,
          loadedByChange = ownersLoadedByChange,
          loadStatements = load.statements,
          previousOwnerChildIds = load.result.first,
          nextOwnerChildIds = load.result.second
        )
      }

    assertEquals(
      ChangeObservation(
        changeStatements = emptyList(),
        loadedByChange = false,
        // Both department proxies and both collections are loaded in batches.
        loadStatements = listOf("select test_department", "select test_employee"),
        previousOwnerChildIds = emptySet(),
        nextOwnerChildIds = setOf(10L)
      ),
      observed
    )
  }

  @Test
  fun changeOwner_betweenLoadedOwnersWithUnloadedCollections_issuesNoQueriesAndMovesChildWhenCollectionsLoad() {
    val observed =
      TestDatabase.inTransaction { session ->
        val previousDepartment = session.find(Department::class.java, 1L)
        val nextDepartment = session.find(Department::class.java, 2L)
        val employee = session.find(Employee::class.java, 10L)
        val change = TestDatabase.recordStatements { employee.changeDepartment(nextDepartment) }
        val collectionsLoadedByChange =
          Hibernate.isInitialized(previousDepartment.employeesCollection()) ||
            Hibernate.isInitialized(nextDepartment.employeesCollection())
        val load =
          TestDatabase.recordStatements {
            employeeIds(previousDepartment) to employeeIds(nextDepartment)
          }

        ChangeObservation(
          changeStatements = change.statements,
          loadedByChange = collectionsLoadedByChange,
          loadStatements = load.statements,
          previousOwnerChildIds = load.result.first,
          nextOwnerChildIds = load.result.second
        )
      }

    assertEquals(
      ChangeObservation(
        changeStatements = emptyList(),
        loadedByChange = false,
        loadStatements = listOf("select test_employee"),
        previousOwnerChildIds = emptySet(),
        nextOwnerChildIds = setOf(10L)
      ),
      observed
    )
  }

  @Test
  fun changeOwner_withLoadedCollections_issuesNoQueriesAndMovesChildAtOnce() {
    val observed =
      TestDatabase.inTransaction { session ->
        val previousDepartment = session.find(Department::class.java, 1L)
        val nextDepartment = session.find(Department::class.java, 2L)
        employeeIds(previousDepartment)
        employeeIds(nextDepartment)
        val employee = session.find(Employee::class.java, 10L)
        val change = TestDatabase.recordStatements { employee.changeDepartment(nextDepartment) }
        val read =
          TestDatabase.recordStatements {
            employeeIds(previousDepartment) to employeeIds(nextDepartment)
          }

        ChangeObservation(
          changeStatements = change.statements,
          loadedByChange = true,
          loadStatements = read.statements,
          previousOwnerChildIds = read.result.first,
          nextOwnerChildIds = read.result.second
        )
      }

    assertEquals(
      ChangeObservation(
        changeStatements = emptyList(),
        loadedByChange = true,
        loadStatements = emptyList(),
        previousOwnerChildIds = emptySet(),
        nextOwnerChildIds = setOf(10L)
      ),
      observed
    )
  }

  @Test
  fun changeOwner_toNull_issuesNoQueriesAndRemovesChildWhenCollectionLoads() {
    val observed =
      TestDatabase.inTransaction { session ->
        val department = session.find(Department::class.java, 1L)
        val employee = session.find(Employee::class.java, 10L)
        val change = TestDatabase.recordStatements { employee.changeDepartment(null) }
        val load = TestDatabase.recordStatements { employeeIds(department) }

        change.statements to (load.statements to load.result)
      }

    assertEquals(emptyList<String>() to (listOf("select test_employee") to emptySet()), observed)
  }

  @Test
  fun changeOwner_andBack_beforeCollectionsLoad_keepsChildOnlyInOriginalCollection() {
    val observed =
      TestDatabase.inTransaction { session ->
        val previousDepartment = session.find(Department::class.java, 1L)
        val nextDepartment = session.find(Department::class.java, 2L)
        val employee = session.find(Employee::class.java, 10L)
        val change =
          TestDatabase.recordStatements {
            employee.changeDepartment(nextDepartment)
            employee.changeDepartment(previousDepartment)
          }

        change.statements to (employeeIds(previousDepartment) to employeeIds(nextDepartment))
      }

    assertEquals(emptyList<String>() to (setOf(10L) to emptySet()), observed)
  }

  @Test
  fun changeOwner_toSameOwner_changesNothing() {
    val observed =
      TestDatabase.inTransaction { session ->
        val department = session.find(Department::class.java, 1L)
        val employee = session.find(Employee::class.java, 10L)
        val change = TestDatabase.recordStatements { employee.changeDepartment(employee.getDepartment()) }

        change.statements to (employeeIds(department) to TestDatabase.countWaitingInverseLinks(session))
      }

    assertEquals(emptyList<String>() to (setOf(10L) to 0), observed)
  }

  private fun employeeIds(department: Department): Set<Long> =
    department.getEmployees().map(Employee::id).toSet()

  private data class ChangeObservation(
    val changeStatements: List<String>,
    val loadedByChange: Boolean,
    val loadStatements: List<String>,
    val previousOwnerChildIds: Set<Long>,
    val nextOwnerChildIds: Set<Long>
  )
}
