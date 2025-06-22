package ru.code4a.quarkus.hibernate.mutator.services.mutators.build

import jakarta.persistence.ManyToMany
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import ru.code4a.quarkus.hibernate.mutator.builds.utils.AssociationInfo
import ru.code4a.quarkus.hibernate.mutator.builds.utils.AssociationKey
import ru.code4a.quarkus.hibernate.mutator.utils.nullable.unwrapElseError

/**
 * Processes associations to establish bidirectional relationships
 */
internal class AssociationProcessor {
  fun process(associations: List<AssociationInfo>): Map<AssociationKey, AssociationInfo> {
    val associationsMap = associations.associateBy { it.key }

    // Process each association to find its mapped counterpart
    associations.forEach { association ->
      processAssociation(association, associationsMap)
    }

    return associationsMap
  }

  private fun processAssociation(
    association: AssociationInfo,
    associationsMap: Map<AssociationKey, AssociationInfo>
  ) {
    when (val annotation = association.getJpaAnnotation()) {
      is OneToMany -> processOneToMany(association, annotation, associationsMap)
      is OneToOne -> processOneToOne(association, annotation, associationsMap)
      is ManyToMany -> processManyToMany(association, annotation)
      // ManyToOne doesn't need special processing for mappedBy
    }
  }

  private fun processOneToMany(
    association: AssociationInfo,
    annotation: OneToMany,
    associationsMap: Map<AssociationKey, AssociationInfo>
  ) {
    if (annotation.mappedBy.isBlank()) return

    val associatedClass = association.getCollectionElementType()
    val mappedByKey = AssociationKey(associatedClass.name, annotation.mappedBy)

    val mappedByAssociation = associationsMap[mappedByKey]
      .unwrapElseError { "Cannot find entity association $mappedByKey" }

    linkBidirectionalAssociations(association, mappedByAssociation)
  }

  private fun processOneToOne(
    association: AssociationInfo,
    annotation: OneToOne,
    associationsMap: Map<AssociationKey, AssociationInfo>
  ) {
    if (annotation.mappedBy.isBlank()) return

    val associatedClass = association.field.type
    val mappedByKey = AssociationKey(associatedClass.name, annotation.mappedBy)

    val mappedByAssociation = associationsMap[mappedByKey]
      .unwrapElseError { "Cannot find entity association $mappedByKey" }

    linkBidirectionalAssociations(association, mappedByAssociation)
  }

  private fun processManyToMany(
    association: AssociationInfo,
    annotation: ManyToMany
  ) {
    if (annotation.mappedBy.isNotBlank()) {
      error("ManyToMany mappedBy is not supported yet")
    }
  }

  private fun linkBidirectionalAssociations(
    owner: AssociationInfo,
    inverse: AssociationInfo
  ) {
    require(owner.mappedBy == null) { "Association already has mappedBy set" }
    require(inverse.mappedFrom == null) { "Association already has mappedFrom set" }

    owner.mappedBy = inverse
    inverse.mappedFrom = owner
  }
}
