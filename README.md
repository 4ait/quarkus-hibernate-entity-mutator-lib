# Quarkus Hibernate Mutator

A Quarkus extension for safely managing Hibernate entity relationships in Kotlin applications.

## Overview

This extension provides a convenient and type-safe way to manage bidirectional JPA
relationships in your Quarkus applications.
It automatically detects entity associations at build time and generates
appropriate mutators to ensure both sides of relationships stay in sync.

## Features

- Build-time detection and analysis of JPA entity relationships
- Build-time validation of unsupported entity inheritance cases
- Support for both `@OneToMany` and `@ManyToOne` associations
- Type-safe mutation APIs for entity references
- Automatic synchronization of bidirectional relationships
- Inverse collections that are not loaded stay in sync without being loaded

## Installation

Requires Java 25 and Quarkus 3.40 (Hibernate ORM 7.4) or later.

Add the dependency to your project:

```kotlin
implementation("ru.code4a:quarkus-hibernate-entity-mutator:<version>")
```

## Usage

### Implementing the Interface

Make your entity classes implement the `HibernateEntityMutationSupport` interface:

```kotlin
import ru.code4a.quarkus.hibernate.mutator.interfaces.HibernateEntityMutationSupport
import jakarta.persistence.Entity
import jakarta.persistence.OneToMany
import jakarta.persistence.ManyToOne

@Entity
class Department : HibernateEntityMutationSupport {
    // ...
    @OneToMany(mappedBy = "department")
    lateinit var employees: MutableSet<Employee>
    // ...
}

@Entity
class Employee : HibernateEntityMutationSupport {
    // ...
    @ManyToOne
    lateinit var department: Department
    // ...
}
```

### Modifying Relationships

#### Setting a Single Reference

```kotlin
// Set department for an employee
mutatorRef(::department).set(department)
// or using extension function
::department.setRef(department)
```

#### Setting Collections

```kotlin
// Set all employees for a department
mutatorRefs(::employees).set(employeesList)
// or using extension function
::employees.setRefs(employeesList)
```

#### Adding to Collections

```kotlin
// Add an employee to a department
mutatorRefs(::employees).add(employee)
```

#### Removing from Collections

```kotlin
// Remove an employee from a department
mutatorRefs(::employees).remove(employee)
```

## How It Works

This extension works in two phases:

1. **Build-time phase**: The extension scans supported JPA association annotations and collects information about relationships.
   During this phase it also validates that association field names are not shadowed inside an entity inheritance chain.

2. **Runtime phase**: The extension uses the collected information to create appropriate mutators for each relationship, ensuring that both sides of the relationship stay synchronized.

The mutators handle different use cases:
- Setting or changing an entity reference
- Adding an entity to a collection
- Removing an entity from a collection
- Replacing an entire collection

### Inverse Collections That Are Not Loaded

When a child's `@ManyToOne` reference changes (a new child, `setRef`, or `add`/`remove` on the owner's
`@OneToMany` collection), the owner's inverse collection has to follow. If that collection, or the owner itself,
is not loaded yet, the mutators do not load it: loading a whole collection to add one element would be expensive,
for example when a new subject joins a workspace with thousands of them.

Instead the change is remembered in the current session. When Hibernate loads the collection later in the same
session, the change is applied right after the load, so the collection contains new children and no longer
contains children that moved elsewhere. This also works for collections loaded in one batch and for owners
referenced through proxies. Once a flush writes the changes to the database, the remembered changes are dropped:
collections loaded from then on read them from the database.

This is done by a Hibernate integrator, `InverseCollectionSynchronizationIntegrator`, which Hibernate and Quarkus
discover through `ServiceLoader`; no configuration is needed.

A collection loaded by a query (for example with `join fetch`) relies on Hibernate's automatic flush before
the query, which writes the pending changes first. With `FlushMode.COMMIT` or `FlushMode.MANUAL` such a collection
does not see pending changes until the next flush.

### What the Mutators Load

The mutators never query the database on their own, except where the result cannot be correct otherwise:

| Operation | Not loaded side | Queries |
|---|---|---|
| New child, `setRef` of a `@ManyToOne`, `add`/`remove` of a bidirectional `@OneToMany` | Inverse collection or its owner | none; the collection is updated when it loads |
| `setRefs` of a bidirectional `@OneToMany` | The collection | the collection, once: replacing it needs its current elements |
| `add`/`remove` of a unidirectional `@OneToMany` or `@ManyToMany` | The collection | the collection, once: Hibernate needs the elements of an owning `Set` to write the change |
| `setRef` of a bidirectional `@OneToOne`, new entity with a `@OneToOne` | The partner | the partner row; with batch fetching, partners of one type come in one query |
| `setRef` of a unidirectional reference | The referenced entity | none |

The partner of a `@OneToOne` is loaded because its reference cannot be fixed later: Hibernate has no hook after it
loads such an entity, and a partner loaded without the fix would point to the entity the database still holds.

### Validation of Association Field Shadowing

The extension now fails fast at build time if the same JPA association field name is declared more than once in a single entity inheritance chain.

Example of an unsupported model:

```kotlin
import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne
import jakarta.persistence.MappedSuperclass

@MappedSuperclass
open class BaseEmployee : HibernateEntityMutationSupport {
    @ManyToOne
    open var department: Department? = null
}

@Entity
class Manager : BaseEmployee() {
    @ManyToOne
    override var department: Department? = null
}
```

This validation exists to stop the application during Quarkus build instead of allowing an ambiguous mutator configuration to reach runtime.

The limitation comes from how mutators are resolved today:

- Mutators are addressed by the runtime entity class and the Kotlin property name.
- Mutators declared on a parent entity are expanded to child entities when the child reuses the same inherited field.
- If a child redeclares an association field with the same name, the key becomes ambiguous: for `Manager::department` the library can no longer reliably decide whether it should use the mutator built for the parent field or for the child field.

Because of that ambiguity, association field shadowing in entity hierarchies is intentionally unsupported and validated at build time.

## Benefits

- **Type safety**: All operations are type-safe and checked at compile time.
- **Reduced boilerplate**: No need to manually write bidirectional relationship management code.
- **No extra queries**: Lazy collections that are not loaded are kept in sync without loading them.
- **Consistency**: Both sides of relationships are kept in sync within the session, loaded or not.

## Limitations

- Currently supports `Set` collections for `@OneToMany` relationships
- JPA association fields must have unique names within a single entity inheritance chain
- Custom relationship handlers need to be implemented for complex cases

## Development

```bash
./gradlew test
```

Tests run plain Hibernate on in-memory H2. The test entities are enhanced at build time the way Quarkus enhances
entities, and every test checks both the resulting state and the exact SQL statements the mutators caused.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

Apache 2.0
