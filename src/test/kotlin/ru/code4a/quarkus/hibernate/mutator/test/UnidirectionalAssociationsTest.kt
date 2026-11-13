package ru.code4a.quarkus.hibernate.mutator.test

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.code4a.quarkus.hibernate.mutator.test.entities.Department
import ru.code4a.quarkus.hibernate.mutator.test.entities.Employee
import ru.code4a.quarkus.hibernate.mutator.test.entities.Player
import ru.code4a.quarkus.hibernate.mutator.test.entities.Project
import ru.code4a.quarkus.hibernate.mutator.test.entities.Tag
import ru.code4a.quarkus.hibernate.mutator.test.entities.Team
import ru.code4a.quarkus.hibernate.mutator.test.support.TestDatabase
import kotlin.test.assertEquals

/**
 * Associations without an inverse side: the mutator only changes the owning side.
 *
 * Adding to an owning `Set` that is not loaded makes Hibernate load it: it has to know the current
 * elements to decide which rows the flush inserts. That single load is Hibernate's own and happens
 * once per collection; the mutator adds no queries of its own.
 */
class UnidirectionalAssociationsTest {
  @BeforeEach
  fun createData() {
    TestDatabase.removeAllData()
    TestDatabase.inTransaction { session ->
      val project = Project.create(session, id = 1)
      val tag = Tag.create(session, id = 1)
      Tag.create(session, id = 2)
      Tag.create(session, id = 3)
      project.addTag(tag)
      Team.create(session, id = 1)
      Player.create(session, id = 1)
      Player.create(session, id = 2)
      val department = Department.create(session, id = 1)
      Employee.create(session, id = 10, department = department)
      Employee.create(session, id = 11, department = department)
    }
  }

  @Test
  fun manyToManyAdd_loadsUnloadedCollectionOnceAndWritesJoinRowOnFlush() {
    val observed =
      TestDatabase.inTransaction { session ->
        val project = session.find(Project::class.java, 1L)
        val secondTag = session.find(Tag::class.java, 2L)
        val thirdTag = session.find(Tag::class.java, 3L)
        val firstAddition = TestDatabase.recordStatements { project.addTag(secondTag) }
        val secondAddition = TestDatabase.recordStatements { project.addTag(thirdTag) }
        val flush = TestDatabase.recordStatements { session.flush() }

        Triple(firstAddition.statements, secondAddition.statements, flush.statements)
      }

    assertEquals(
      Triple(
        listOf("select test_project_tag"),
        emptyList(),
        listOf("insert test_project_tag", "insert test_project_tag")
      ),
      observed
    )
  }

  @Test
  fun manyToManyRemove_writesJoinRowDeletionOnFlush() {
    val observed =
      TestDatabase.inTransaction { session ->
        val project = session.find(Project::class.java, 1L)
        val tag = session.find(Tag::class.java, 1L)
        val removal = TestDatabase.recordStatements { project.removeTag(tag) }
        val flush = TestDatabase.recordStatements { session.flush() }

        removal.statements to flush.statements
      }

    assertEquals(listOf("select test_project_tag") to listOf("delete test_project_tag"), observed)
  }

  @Test
  fun unidirectionalOneToManyAdd_loadsUnloadedCollectionOnceAndWritesForeignKeyOnFlush() {
    val observed =
      TestDatabase.inTransaction { session ->
        val team = session.find(Team::class.java, 1L)
        val firstPlayer = session.find(Player::class.java, 1L)
        val secondPlayer = session.find(Player::class.java, 2L)
        val firstAddition = TestDatabase.recordStatements { team.addPlayer(firstPlayer) }
        val secondAddition = TestDatabase.recordStatements { team.addPlayer(secondPlayer) }
        val flush = TestDatabase.recordStatements { session.flush() }

        Triple(firstAddition.statements, secondAddition.statements, flush.statements)
      }

    assertEquals(
      Triple(listOf("select test_player"), emptyList(), listOf("update test_player", "update test_player")),
      observed
    )
  }

  @Test
  fun unidirectionalReference_toUnloadedEntity_issuesNoQueries() {
    val observed =
      TestDatabase.inTransaction { session ->
        val employee = session.find(Employee::class.java, 10L)
        val mentor = session.getReference(Employee::class.java, 11L)
        val change = TestDatabase.recordStatements { employee.changeMentor(mentor) }
        val flush = TestDatabase.recordStatements { session.flush() }

        Triple(change.statements, employee.getMentor() === mentor, flush.statements)
      }

    assertEquals(Triple(emptyList(), true, listOf("update test_employee")), observed)
  }
}
