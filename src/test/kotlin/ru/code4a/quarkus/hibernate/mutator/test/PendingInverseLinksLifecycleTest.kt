package ru.code4a.quarkus.hibernate.mutator.test

import org.hibernate.Hibernate
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.code4a.quarkus.hibernate.mutator.test.entities.Department
import ru.code4a.quarkus.hibernate.mutator.test.entities.Employee
import ru.code4a.quarkus.hibernate.mutator.test.support.TestDatabase
import kotlin.test.assertEquals

/**
 * When remembered inverse changes are applied and when they are dropped.
 */
class PendingInverseLinksLifecycleTest {
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
  fun collectionsLoadedInOneBatch_allGetTheirPendingChildren() {
    val observed =
      TestDatabase.inTransaction { session ->
        val first = session.find(Department::class.java, 1L)
        val second = session.find(Department::class.java, 2L)
        Employee.create(session, id = 11, department = first)
        Employee.create(session, id = 12, department = second)
        val load = TestDatabase.recordStatements { employeeIds(first) }
        val secondLoadedInSameBatch = Hibernate.isInitialized(second.employeesCollection())
        val secondRead = TestDatabase.recordStatements { employeeIds(second) }

        BatchObservation(
          loadStatements = load.statements + secondRead.statements,
          secondLoadedInSameBatch = secondLoadedInSameBatch,
          firstChildIds = load.result,
          secondChildIds = secondRead.result,
          waitingLinks = TestDatabase.countWaitingInverseLinks(session)
        )
      }

    assertEquals(
      BatchObservation(
        loadStatements = listOf("select test_employee"),
        secondLoadedInSameBatch = true,
        firstChildIds = setOf(10L, 11L),
        secondChildIds = setOf(12L),
        waitingLinks = 0
      ),
      observed
    )
  }

  @Test
  fun flush_dropsLinksItWrote_andLoadedCollectionHasChildOnce() {
    val observed =
      TestDatabase.inTransaction { session ->
        val department = session.find(Department::class.java, 1L)
        Employee.create(session, id = 11, department = department)
        val waitingBeforeFlush = TestDatabase.countWaitingInverseLinks(session)
        session.flush()
        val waitingAfterFlush = TestDatabase.countWaitingInverseLinks(session)

        LinksAroundFlush(
          waitingBeforeFlush = waitingBeforeFlush,
          waitingAfterFlush = waitingAfterFlush,
          childIds = department.getEmployees().map(Employee::id).sorted()
        )
      }

    assertEquals(LinksAroundFlush(waitingBeforeFlush = 1, waitingAfterFlush = 0, childIds = listOf(10L, 11L)), observed)
  }

  @Test
  fun autoFlushBeforeQuery_dropsLinksItWrote() {
    val observed =
      TestDatabase.inTransaction { session ->
        val department = session.find(Department::class.java, 1L)
        Employee.create(session, id = 11, department = department)
        val waitingBeforeQuery = TestDatabase.countWaitingInverseLinks(session)
        session.createSelectionQuery("from Employee", Employee::class.java).list()

        LinksAroundFlush(
          waitingBeforeFlush = waitingBeforeQuery,
          waitingAfterFlush = TestDatabase.countWaitingInverseLinks(session),
          childIds = department.getEmployees().map(Employee::id).sorted()
        )
      }

    assertEquals(LinksAroundFlush(waitingBeforeFlush = 1, waitingAfterFlush = 0, childIds = listOf(10L, 11L)), observed)
  }

  @Test
  fun flushBeforeChildIsPersisted_keepsItsLink() {
    val observed =
      TestDatabase.inTransaction { session ->
        val department = session.find(Department::class.java, 1L)
        val employee = Employee.build(id = 11)
        department.addEmployee(employee)
        session.flush()
        val waitingAfterFlush = TestDatabase.countWaitingInverseLinks(session)
        session.persist(employee)

        waitingAfterFlush to department.getEmployees().map(Employee::id).toSet()
      }

    assertEquals(1 to setOf(10L, 11L), observed)
  }

  @Test
  fun removedChild_doesNotJoinCollectionWhenItLoads() {
    val observed =
      TestDatabase.inTransaction { session ->
        val first = session.find(Department::class.java, 1L)
        val second = session.find(Department::class.java, 2L)
        val employee = session.find(Employee::class.java, 10L)
        employee.changeDepartment(second)
        session.remove(employee)

        employeeIds(first) to employeeIds(second)
      }

    assertEquals(emptySet<Long>() to emptySet(), observed)
  }

  @Test
  fun linksOfOneSession_areNotAppliedInAnotherSession() {
    val observed =
      TestDatabase.inTransaction { firstSession ->
        val department = firstSession.find(Department::class.java, 1L)
        Employee.create(firstSession, id = 11, department = department)

        TestDatabase.inTransaction { secondSession ->
          val sameDepartment = secondSession.find(Department::class.java, 1L)

          employeeIds(sameDepartment) to TestDatabase.countWaitingInverseLinks(secondSession)
        } to TestDatabase.countWaitingInverseLinks(firstSession)
      }

    assertEquals((setOf(10L) to 0) to 1, observed)
  }

  @Test
  fun appliedLinks_areDropped() {
    val waitingLinks =
      TestDatabase.inTransaction { session ->
        val department = session.find(Department::class.java, 1L)
        Employee.create(session, id = 11, department = department)
        employeeIds(department)

        TestDatabase.countWaitingInverseLinks(session)
      }

    assertEquals(0, waitingLinks)
  }

  private fun employeeIds(department: Department): Set<Long> =
    department.getEmployees().map(Employee::id).toSet()

  private data class BatchObservation(
    val loadStatements: List<String>,
    val secondLoadedInSameBatch: Boolean,
    val firstChildIds: Set<Long>,
    val secondChildIds: Set<Long>,
    val waitingLinks: Int
  )

  private data class LinksAroundFlush(
    val waitingBeforeFlush: Int,
    val waitingAfterFlush: Int,
    val childIds: List<Long>
  )
}
