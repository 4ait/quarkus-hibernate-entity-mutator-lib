package ru.code4a.quarkus.hibernate.mutator.test.support

import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.AvailableSettings
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.resource.jdbc.spi.StatementInspector
import ru.code4a.quarkus.hibernate.mutator.services.inverse.PendingInverseLinks
import ru.code4a.quarkus.hibernate.mutator.test.entities.Agency
import ru.code4a.quarkus.hibernate.mutator.test.entities.Company
import ru.code4a.quarkus.hibernate.mutator.test.entities.Department
import ru.code4a.quarkus.hibernate.mutator.test.entities.Employee
import ru.code4a.quarkus.hibernate.mutator.test.entities.Member
import ru.code4a.quarkus.hibernate.mutator.test.entities.Organization
import ru.code4a.quarkus.hibernate.mutator.test.entities.Passport
import ru.code4a.quarkus.hibernate.mutator.test.entities.Person
import ru.code4a.quarkus.hibernate.mutator.test.entities.Player
import ru.code4a.quarkus.hibernate.mutator.test.entities.Project
import ru.code4a.quarkus.hibernate.mutator.test.entities.Tag
import ru.code4a.quarkus.hibernate.mutator.test.entities.Team
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Plain Hibernate on in-memory H2 with the settings that matter for the mutators:
 * enhanced entities (see the build script), batch fetching as in Quarkus and a record of every SQL statement.
 *
 * The mutator integrator is not registered here: Hibernate finds it through `ServiceLoader`, as Quarkus does.
 */
object TestDatabase {
  private val executedStatements = CopyOnWriteArrayList<String>()

  private val entityClasses =
    listOf(
      Department::class.java,
      Employee::class.java,
      Organization::class.java,
      Company::class.java,
      Agency::class.java,
      Member::class.java,
      Person::class.java,
      Passport::class.java,
      Project::class.java,
      Tag::class.java,
      Team::class.java,
      Player::class.java
    )

  val sessionFactory: SessionFactory by lazy {
    val serviceRegistry =
      StandardServiceRegistryBuilder()
        .applySetting(AvailableSettings.JAKARTA_JDBC_URL, "jdbc:h2:mem:mutator-tests;DB_CLOSE_DELAY=-1")
        .applySetting(AvailableSettings.JAKARTA_JDBC_USER, "sa")
        .applySetting(AvailableSettings.JAKARTA_JDBC_PASSWORD, "")
        .applySetting(AvailableSettings.HBM2DDL_AUTO, "create-drop")
        // Quarkus loads lazy collections and proxies in batches of 16 by default.
        .applySetting(AvailableSettings.DEFAULT_BATCH_FETCH_SIZE, 16)
        .applySetting(
          AvailableSettings.STATEMENT_INSPECTOR,
          StatementInspector { sql ->
            executedStatements += sql
            sql
          }
        )
        .build()

    MetadataSources(serviceRegistry)
      .apply { entityClasses.forEach(::addAnnotatedClass) }
      .buildMetadata()
      .buildSessionFactory()
  }

  fun removeAllData() {
    sessionFactory.schemaManager.truncateMappedObjects()
  }

  fun <T> inTransaction(block: (Session) -> T): T =
    sessionFactory.fromTransaction(block)

  /**
   * Runs [block] and returns what it produced together with the SQL statements it executed.
   */
  fun <T> recordStatements(block: () -> T): RecordedStatements<T> {
    executedStatements.clear()
    val result = block()

    return RecordedStatements(result, executedStatements.map(::summarizeStatement))
  }

  fun countWaitingInverseLinks(session: Session): Int =
    PendingInverseLinks.countWaiting(session.unwrap(SharedSessionContractImplementor::class.java))

  /**
   * Shortens an SQL statement to "<kind> <table>", e.g. "select test_employee", so tests read like a query log.
   */
  private fun summarizeStatement(sql: String): String {
    val normalized = sql.trim().lowercase().replace(Regex("\\s+"), " ")
    val kind = normalized.substringBefore(' ')
    val table =
      when (kind) {
        "select" -> normalized.substringAfter(" from ").substringBefore(' ')
        "insert" -> normalized.substringAfter("insert into ").substringBefore(' ').substringBefore('(')
        "update" -> normalized.substringAfter("update ").substringBefore(' ')
        "delete" -> normalized.substringAfter("delete from ").substringBefore(' ')
        else -> normalized
      }

    return "$kind $table"
  }
}

data class RecordedStatements<T>(
  val result: T,
  val statements: List<String>
)
