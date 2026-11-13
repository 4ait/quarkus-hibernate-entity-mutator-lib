package ru.code4a.quarkus.hibernate.mutator.test

import org.hibernate.Hibernate
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.code4a.quarkus.hibernate.mutator.test.entities.Department
import ru.code4a.quarkus.hibernate.mutator.test.entities.Employee
import ru.code4a.quarkus.hibernate.mutator.test.support.TestDatabase
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Changes made through the inverse `@OneToMany` collection (`add`, `remove`, `setRefs`).
 */
class OneToManyMutatorTest {
  @BeforeEach
  fun createTwoDepartments() {
    TestDatabase.removeAllData()
    TestDatabase.inTransaction { session ->
      val first = Department.create(session, id = 1)
      Department.create(session, id = 2)
      Employee.create(session, id = 10, department = first)
      Employee.create(session, id = 11, department = first)
    }
  }

  @Test
  fun add_toUnloadedCollection_issuesNoQueriesAndChildAppearsWhenCollectionLoads() {
    val observed =
      TestDatabase.inTransaction { session ->
        val department = session.find(Department::class.java, 2L)
        val employee = Employee.build(id = 20)
        val addition =
          TestDatabase.recordStatements {
            department.addEmployee(employee)
            session.persist(employee)
          }
        val collectionLoadedByAddition = Hibernate.isInitialized(department.employeesCollection())
        val load = TestDatabase.recordStatements { employeeIds(department) }

        CollectionChangeObservation(
          changeStatements = addition.statements,
          collectionLoadedByChange = collectionLoadedByAddition,
          childPointsToOwner = employee.getDepartment() === department,
          loadStatements = load.statements,
          childIds = load.result
        )
      }

    assertEquals(
      CollectionChangeObservation(
        changeStatements = emptyList(),
        collectionLoadedByChange = false,
        childPointsToOwner = true,
        loadStatements = listOf("select test_employee"),
        childIds = setOf(20L)
      ),
      observed
    )
  }

  @Test
  fun add_toLoadedCollection_issuesNoQueriesAndChildAppearsAtOnce() {
    val observed =
      TestDatabase.inTransaction { session ->
        val department = session.find(Department::class.java, 2L)
        employeeIds(department)
        val employee = Employee.build(id = 20)
        val addition =
          TestDatabase.recordStatements {
            department.addEmployee(employee)
            session.persist(employee)
          }
        val read = TestDatabase.recordStatements { employeeIds(department) }

        CollectionChangeObservation(
          changeStatements = addition.statements,
          collectionLoadedByChange = true,
          childPointsToOwner = employee.getDepartment() === department,
          loadStatements = read.statements,
          childIds = read.result
        )
      }

    assertEquals(
      CollectionChangeObservation(
        changeStatements = emptyList(),
        collectionLoadedByChange = true,
        childPointsToOwner = true,
        loadStatements = emptyList(),
        childIds = setOf(20L)
      ),
      observed
    )
  }

  @Test
  fun remove_fromUnloadedCollection_issuesNoQueriesAndChildIsGoneWhenCollectionLoads() {
    val observed =
      TestDatabase.inTransaction { session ->
        val department = session.find(Department::class.java, 1L)
        val employee = session.find(Employee::class.java, 10L)
        val removal = TestDatabase.recordStatements { department.removeEmployee(employee) }
        val collectionLoadedByRemoval = Hibernate.isInitialized(department.employeesCollection())
        val load = TestDatabase.recordStatements { employeeIds(department) }

        CollectionChangeObservation(
          changeStatements = removal.statements,
          collectionLoadedByChange = collectionLoadedByRemoval,
          childPointsToOwner = employee.getDepartment() != null,
          loadStatements = load.statements,
          childIds = load.result
        )
      }

    assertEquals(
      CollectionChangeObservation(
        changeStatements = emptyList(),
        collectionLoadedByChange = false,
        childPointsToOwner = false,
        loadStatements = listOf("select test_employee"),
        childIds = setOf(11L)
      ),
      observed
    )
  }

  @Test
  fun add_childOfAnotherOwner_failsWithoutQueries() {
    val statements =
      TestDatabase.inTransaction { session ->
        val otherDepartment = session.find(Department::class.java, 2L)
        val employee = session.find(Employee::class.java, 10L)

        TestDatabase.recordStatements {
          assertFailsWith<IllegalStateException> { otherDepartment.addEmployee(employee) }
        }.statements
      }

    assertEquals(emptyList(), statements)
  }

  @Test
  fun remove_childOfAnotherOwner_failsWithoutQueries() {
    val statements =
      TestDatabase.inTransaction { session ->
        val otherDepartment = session.find(Department::class.java, 2L)
        val employee = session.find(Employee::class.java, 10L)

        TestDatabase.recordStatements {
          assertFailsWith<IllegalStateException> { otherDepartment.removeEmployee(employee) }
        }.statements
      }

    assertEquals(emptyList(), statements)
  }

  @Test
  fun add_childAlreadyPointingToOwner_keepsSingleCopyWithoutQueries() {
    val observed =
      TestDatabase.inTransaction { session ->
        val department = session.find(Department::class.java, 1L)
        val employee = session.find(Employee::class.java, 10L)
        val addition = TestDatabase.recordStatements { department.addEmployee(employee) }

        addition.statements to department.getEmployees().count { it === employee }
      }

    assertEquals(emptyList<String>() to 1, observed)
  }

  @Test
  fun setRefs_loadsCurrentCollectionOnceAndKeepsBothSidesInLine() {
    val observed =
      TestDatabase.inTransaction { session ->
        val department = session.find(Department::class.java, 1L)
        val kept = session.find(Employee::class.java, 11L)
        val added = Employee.build(id = 20)
        val replacement =
          TestDatabase.recordStatements {
            department.replaceEmployees(listOf(kept, added))
            session.persist(added)
          }
        val dropped = session.find(Employee::class.java, 10L)

        ReplacementObservation(
          // Replacing the whole collection has to know its current elements.
          replacementStatements = replacement.statements,
          childIds = employeeIds(department),
          droppedChildOwnerIsNull = dropped.getDepartment() == null,
          addedChildPointsToOwner = added.getDepartment() === department
        )
      }

    assertEquals(
      ReplacementObservation(
        replacementStatements = listOf("select test_employee"),
        childIds = setOf(11L, 20L),
        droppedChildOwnerIsNull = true,
        addedChildPointsToOwner = true
      ),
      observed
    )
  }

  private fun employeeIds(department: Department): Set<Long> =
    department.getEmployees().map(Employee::id).toSet()

  private data class CollectionChangeObservation(
    val changeStatements: List<String>,
    val collectionLoadedByChange: Boolean,
    val childPointsToOwner: Boolean,
    val loadStatements: List<String>,
    val childIds: Set<Long>
  )

  private data class ReplacementObservation(
    val replacementStatements: List<String>,
    val childIds: Set<Long>,
    val droppedChildOwnerIsNull: Boolean,
    val addedChildPointsToOwner: Boolean
  )
}
