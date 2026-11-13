package ru.code4a.quarkus.hibernate.mutator.test.entities

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.Session
import ru.code4a.quarkus.hibernate.mutator.interfaces.HibernateEntityMutationSupport

/**
 * Owner side of a bidirectional `@OneToMany` / `@ManyToOne` association.
 */
@Entity
@Table(name = "test_department")
class Department : HibernateEntityMutationSupport {
  @Id
  var id: Long = 0

  var name: String = ""

  @OneToMany(mappedBy = "department")
  private var employees: MutableSet<Employee> = mutableSetOf()

  /**
   * The collection itself, so tests can check whether Hibernate loaded it without loading it.
   */
  fun employeesCollection(): MutableSet<Employee> = employees

  fun getEmployees(): Set<Employee> = employees.toSet()

  fun addEmployee(employee: Employee) {
    ::employees.add(employee)
  }

  fun removeEmployee(employee: Employee) {
    ::employees.remove(employee)
  }

  fun replaceEmployees(employees: Collection<Employee>) {
    ::employees.setRefs(employees)
  }

  companion object {
    fun create(session: Session, id: Long, name: String = "Department $id"): Department =
      persistNewEntity(
        session,
        Department().also {
          it.id = id
          it.name = name
        }
      )
  }
}
