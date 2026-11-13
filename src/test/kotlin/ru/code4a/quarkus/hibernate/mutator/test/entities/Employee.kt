package ru.code4a.quarkus.hibernate.mutator.test.entities

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.Session
import ru.code4a.quarkus.hibernate.mutator.interfaces.HibernateEntityMutationSupport

/**
 * Child side of [Department.employees]; [mentor] is a unidirectional reference.
 */
@Entity
@Table(name = "test_employee")
class Employee : HibernateEntityMutationSupport {
  @Id
  var id: Long = 0

  var name: String = ""

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "department_id")
  private var department: Department? = null

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "mentor_id")
  private var mentor: Employee? = null

  fun getDepartment(): Department? = department

  fun changeDepartment(department: Department?) {
    ::department.setRef(department)
  }

  fun getMentor(): Employee? = mentor

  fun changeMentor(mentor: Employee?) {
    ::mentor.setRef(mentor)
  }

  companion object {
    fun create(session: Session, id: Long, department: Department?, name: String = "Employee $id"): Employee =
      persistNewEntity(
        session,
        Employee().also {
          it.id = id
          it.name = name
          it.department = department
        }
      )

    /**
     * Creates the entity without persisting it, as code does before handing it over to an owner.
     */
    fun build(id: Long, name: String = "Employee $id"): Employee =
      initializeNewEntity(
        Employee().also {
          it.id = id
          it.name = name
        }
      )
  }
}
