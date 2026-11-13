package ru.code4a.quarkus.hibernate.mutator.test

import org.hibernate.Hibernate
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.code4a.quarkus.hibernate.mutator.services.inverse.PendingInverseLinks
import ru.code4a.quarkus.hibernate.mutator.test.entities.Company
import ru.code4a.quarkus.hibernate.mutator.test.entities.Department
import ru.code4a.quarkus.hibernate.mutator.test.entities.Employee
import ru.code4a.quarkus.hibernate.mutator.test.entities.Member
import ru.code4a.quarkus.hibernate.mutator.test.entities.Organization
import ru.code4a.quarkus.hibernate.mutator.test.entities.initializeNewEntity
import ru.code4a.quarkus.hibernate.mutator.test.support.TestDatabase
import kotlin.test.assertEquals

/**
 * Less common ways to reach an inverse collection, and the cost of many changes at once.
 */
class InverseCollectionEdgeCasesTest {
  @BeforeEach
  fun createData() {
    TestDatabase.removeAllData()
    TestDatabase.inTransaction { session ->
      val first = Department.create(session, id = 1)
      Department.create(session, id = 2)
      Department.create(session, id = 3)
      Employee.create(session, id = 10, department = first)
      val firstCompany = Company.create(session, id = 1)
      Company.create(session, id = 2)
      Member.create(session, id = 10, organization = firstCompany)
    }
  }

  @Test
  fun manyNewChildren_issueNoQueriesAndCollectionLoadsWithOneQuery() {
    val observed =
      TestDatabase.inTransaction { session ->
        val department = session.find(Department::class.java, 1L)
        val creation =
          TestDatabase.recordStatements {
            (100L until 300L).forEach { Employee.create(session, id = it, department = department) }
          }
        val load = TestDatabase.recordStatements { department.getEmployees().size }

        Triple(creation.statements, load.statements, load.result)
      }

    assertEquals(Triple(emptyList(), listOf("select test_employee"), 201), observed)
  }

  @Test
  fun changeOwner_betweenPolymorphicOwnerProxies_issuesNoQueriesAndMovesChildWhenCollectionsLoad() {
    val observed =
      TestDatabase.inTransaction { session ->
        val member = session.find(Member::class.java, 10L)
        val previousOrganization = member.getOrganization()!!
        val nextOrganization = session.getReference(Organization::class.java, 2L)
        val change = TestDatabase.recordStatements { member.changeOrganization(nextOrganization) }
        val ownersLoadedByChange = Hibernate.isInitialized(previousOrganization) || Hibernate.isInitialized(nextOrganization)

        Triple(
          change.statements,
          ownersLoadedByChange,
          memberIds(previousOrganization) to memberIds(nextOrganization)
        )
      }

    assertEquals(Triple(emptyList(), false, emptySet<Long>() to setOf(10L)), observed)
  }

  @Test
  fun addThroughPolymorphicOwner_issuesNoQueriesAndChildAppearsWhenCollectionLoads() {
    val observed =
      TestDatabase.inTransaction { session ->
        val organization = session.find(Organization::class.java, 2L)
        val member = session.find(Member::class.java, 10L)
        member.changeOrganization(null)
        val addition = TestDatabase.recordStatements { organization.addMember(member) }

        addition.statements to memberIds(organization)
      }

    assertEquals(emptyList<String>() to setOf(10L), observed)
  }

  @Test
  fun changeOwner_throughThreeOwnersBeforeCollectionsLoad_leavesChildOnlyInLastOne() {
    val observed =
      TestDatabase.inTransaction { session ->
        val first = session.find(Department::class.java, 1L)
        val second = session.find(Department::class.java, 2L)
        val third = session.find(Department::class.java, 3L)
        val employee = session.find(Employee::class.java, 10L)
        employee.changeDepartment(second)
        employee.changeDepartment(third)

        listOf(employeeIds(first), employeeIds(second), employeeIds(third))
      }

    assertEquals(listOf(emptySet(), emptySet(), setOf(10L)), observed)
  }

  @Test
  fun newOwnerWithFilledCollection_pointsItsChildrenToIt() {
    val observed =
      TestDatabase.inTransaction { session ->
        val employee = Employee.build(id = 20)
        val department =
          Department().also {
            it.id = 4
            it.employeesCollection().add(employee)
          }
        initializeNewEntity(department)
        session.persist(department)
        session.persist(employee)

        employee.getDepartment() === department
      }

    assertEquals(true, observed)
  }

  @Test
  fun detachedOwner_isChangedWithoutQueriesAndWithoutWaitingLinks() {
    val detachedDepartment = TestDatabase.inTransaction { session -> session.find(Department::class.java, 2L) }

    val observed =
      TestDatabase.inTransaction { session ->
        val employee = session.find(Employee::class.java, 10L)
        val change = TestDatabase.recordStatements { employee.changeDepartment(detachedDepartment) }

        change.statements to TestDatabase.countWaitingInverseLinks(session)
      }

    // The previous department is a reference of this session and waits for its collection;
    // the detached one is never loaded into this session.
    assertEquals(emptyList<String>() to 1, observed)
  }

  @Test
  fun joinFetchQuery_flushesPendingChildAndReturnsItInCollection() {
    val observed =
      TestDatabase.inTransaction { session ->
        val department = session.find(Department::class.java, 1L)
        Employee.create(session, id = 11, department = department)
        val query =
          TestDatabase.recordStatements {
            session
              .createSelectionQuery("from Department d join fetch d.employees where d.id = 1", Department::class.java)
              .singleResult
          }

        Triple(
          query.statements,
          query.result.getEmployees().map(Employee::id).toSet(),
          TestDatabase.countWaitingInverseLinks(session)
        )
      }

    assertEquals(Triple(listOf("insert test_employee", "select test_department"), setOf(10L, 11L), 0), observed)
  }

  @Test
  fun closedSession_releasesItsWaitingLinks() {
    lateinit var closedSession: SharedSessionContractImplementor

    val waitingBeforeClose =
      TestDatabase.sessionFactory.openSession().use { session ->
        closedSession = session.unwrap(SharedSessionContractImplementor::class.java)
        session.beginTransaction()
        val department = session.find(Department::class.java, 1L)
        Employee.create(session, id = 11, department = department)
        val waiting = TestDatabase.countWaitingInverseLinks(session)
        session.transaction.rollback()
        waiting
      }

    assertEquals(1 to 0, waitingBeforeClose to PendingInverseLinks.countWaiting(closedSession))
  }

  private fun employeeIds(department: Department): Set<Long> =
    department.getEmployees().map(Employee::id).toSet()

  private fun memberIds(organization: Organization): Set<Long> =
    organization.membersCollection().map(Member::id).toSet()
}
