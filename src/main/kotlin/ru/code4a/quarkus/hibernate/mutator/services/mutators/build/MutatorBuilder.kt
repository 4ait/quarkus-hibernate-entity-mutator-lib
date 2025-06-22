import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import ru.code4a.quarkus.hibernate.mutator.builds.utils.AssociationInfo
import ru.code4a.quarkus.hibernate.mutator.services.mutators.build.initializers.BidirectionalManyToOneInitializer
import ru.code4a.quarkus.hibernate.mutator.services.mutators.build.initializers.BidirectionalOneToManyInitializer
import ru.code4a.quarkus.hibernate.mutator.services.mutators.build.initializers.BidirectionalOneToOneInitializer
import ru.code4a.quarkus.hibernate.mutator.services.mutators.build.initializers.NoOpFieldInitializer
import ru.code4a.quarkus.hibernate.mutator.services.mutators.build.models.MutatorBuildResult
import ru.code4a.quarkus.hibernate.mutator.services.mutators.build.mutators.BidirectionalManyToOneRefMutator
import ru.code4a.quarkus.hibernate.mutator.services.mutators.build.mutators.BidirectionalOneToManyMutator
import ru.code4a.quarkus.hibernate.mutator.services.mutators.build.mutators.BidirectionalOneToOneRefMutator
import ru.code4a.quarkus.hibernate.mutator.services.mutators.build.mutators.SimpleManyToManyMutator
import ru.code4a.quarkus.hibernate.mutator.services.mutators.build.mutators.SimpleRefMutator
import ru.code4a.quarkus.hibernate.mutator.services.mutators.build.mutators.UnidirectionalCollectionMutator
import ru.code4a.quarkus.hibernate.mutator.utils.hibernate.HibernateFieldAccessor

/**
 * Builder for creating mutators for a specific association
 */
internal class MutatorBuilder(private val association: AssociationInfo) {

  fun build(): MutatorBuildResult {
    return when (val annotation = association.getJpaAnnotation()) {
      is OneToMany -> buildOneToMany(annotation)
      is ManyToOne -> buildManyToOne()
      is OneToOne -> buildOneToOne()
      is ManyToMany -> buildManyToMany()
      else -> error("Unsupported annotation type: $annotation")
    }
  }

  private fun buildOneToMany(annotation: OneToMany): MutatorBuildResult {
    validateSetType()

    return if (association.mappedBy != null) {
      buildBidirectionalOneToMany()
    } else {
      buildUnidirectionalOneToMany()
    }
  }

  private fun buildManyToOne(): MutatorBuildResult {
    return if (association.mappedFrom != null) {
      buildBidirectionalManyToOne()
    } else {
      buildUnidirectionalManyToOne()
    }
  }

  private fun buildOneToOne(): MutatorBuildResult {
    val bidirectional = association.mappedFrom ?: association.mappedBy

    return if (bidirectional != null) {
      buildBidirectionalOneToOne(bidirectional)
    } else {
      buildUnidirectionalOneToOne()
    }
  }

  private fun buildManyToMany(): MutatorBuildResult {
    validateSetType()
    validateNoMappedBy()

    val accessor = HibernateFieldAccessor(association.field)
    val mutator = SimpleManyToManyMutator(accessor)

    return MutatorBuildResult(
      collectionMutator = mutator,
      initializer = NoOpFieldInitializer
    )
  }

  private fun buildBidirectionalOneToMany(): MutatorBuildResult {
    val accessor = HibernateFieldAccessor(association.field)
    val mappedByAccessor = HibernateFieldAccessor(association.mappedBy!!.field)

    val mutator = BidirectionalOneToManyMutator(accessor, mappedByAccessor)
    val initializer = BidirectionalOneToManyInitializer(accessor, mutator)

    return MutatorBuildResult(
      collectionMutator = mutator,
      initializer = initializer
    )
  }

  private fun buildUnidirectionalOneToMany(): MutatorBuildResult {
    val accessor = HibernateFieldAccessor(association.field)
    val mutator = UnidirectionalCollectionMutator(accessor)

    return MutatorBuildResult(
      collectionMutator = mutator,
      initializer = NoOpFieldInitializer
    )
  }

  private fun buildBidirectionalManyToOne(): MutatorBuildResult {
    val accessor = HibernateFieldAccessor(association.field)
    val mappedFromAccessor = HibernateFieldAccessor(association.mappedFrom!!.field)

    validateSetType(association.mappedFrom!!.field.type)

    val mutator = BidirectionalManyToOneRefMutator(accessor, mappedFromAccessor)
    val initializer = BidirectionalManyToOneInitializer(accessor, mappedFromAccessor)

    return MutatorBuildResult(
      refMutator = mutator,
      initializer = initializer
    )
  }

  private fun buildUnidirectionalManyToOne(): MutatorBuildResult {
    val accessor = HibernateFieldAccessor(association.field)
    val mutator = SimpleRefMutator(accessor)

    return MutatorBuildResult(
      refMutator = mutator,
      initializer = NoOpFieldInitializer
    )
  }

  private fun buildBidirectionalOneToOne(bidirectional: AssociationInfo): MutatorBuildResult {
    val accessor = HibernateFieldAccessor(association.field)
    val bidirectionalAccessor = HibernateFieldAccessor(bidirectional.field)

    val mutator = BidirectionalOneToOneRefMutator(accessor, bidirectionalAccessor)
    val initializer = BidirectionalOneToOneInitializer(accessor, bidirectionalAccessor)

    return MutatorBuildResult(
      refMutator = mutator,
      initializer = initializer
    )
  }

  private fun buildUnidirectionalOneToOne(): MutatorBuildResult {
    val accessor = HibernateFieldAccessor(association.field)
    val mutator = SimpleRefMutator(accessor)

    return MutatorBuildResult(
      refMutator = mutator,
      initializer = NoOpFieldInitializer
    )
  }

  private fun validateSetType(type: Class<*> = association.field.type) {
    require(type == Set::class.java) {
      "Only Set collections are supported, but got $type for ${association.clazz}::${association.field.name}"
    }
  }

  private fun validateNoMappedBy() {
    require(association.mappedBy == null && association.mappedFrom == null) {
      "Bidirectional ManyToMany is not supported yet"
    }
  }
}
