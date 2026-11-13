package ru.code4a.quarkus.hibernate.mutator.test.entities

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.Session
import ru.code4a.quarkus.hibernate.mutator.interfaces.HibernateEntityMutationSupport

/**
 * Owner of a unidirectional `@ManyToMany`.
 */
@Entity
@Table(name = "test_project")
class Project : HibernateEntityMutationSupport {
  @Id
  var id: Long = 0

  @ManyToMany
  @JoinTable(
    name = "test_project_tag",
    joinColumns = [JoinColumn(name = "project_id")],
    inverseJoinColumns = [JoinColumn(name = "tag_id")]
  )
  private var tags: MutableSet<Tag> = mutableSetOf()

  fun tagsCollection(): MutableSet<Tag> = tags

  fun addTag(tag: Tag) {
    ::tags.add(tag)
  }

  fun removeTag(tag: Tag) {
    ::tags.remove(tag)
  }

  companion object {
    fun create(session: Session, id: Long): Project =
      persistNewEntity(session, Project().also { it.id = id })
  }
}

@Entity
@Table(name = "test_tag")
class Tag : HibernateEntityMutationSupport {
  @Id
  var id: Long = 0

  companion object {
    fun create(session: Session, id: Long): Tag =
      persistNewEntity(session, Tag().also { it.id = id })
  }
}

/**
 * Owner of a unidirectional `@OneToMany` that keeps the foreign key in the child table.
 */
@Entity
@Table(name = "test_team")
class Team : HibernateEntityMutationSupport {
  @Id
  var id: Long = 0

  @OneToMany
  @JoinColumn(name = "team_id")
  private var players: MutableSet<Player> = mutableSetOf()

  fun playersCollection(): MutableSet<Player> = players

  fun addPlayer(player: Player) {
    ::players.add(player)
  }

  companion object {
    fun create(session: Session, id: Long): Team =
      persistNewEntity(session, Team().also { it.id = id })
  }
}

@Entity
@Table(name = "test_player")
class Player : HibernateEntityMutationSupport {
  @Id
  var id: Long = 0

  companion object {
    fun create(session: Session, id: Long): Player =
      persistNewEntity(session, Player().also { it.id = id })
  }
}
