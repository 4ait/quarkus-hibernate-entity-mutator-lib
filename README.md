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
- Lazy-initialization safe operations

## Installation

Add the dependency to your project:

```kotlin
implementation("ru.code4a:quarkus-hibernate-mutator:1.0.0") // Replace with the actual version
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
- **Lazy loading protection**: The extension handles lazy-loaded collections properly.
- **Consistency**: Both sides of relationships are always kept in sync.

## Limitations

- Currently supports `Set` collections for `@OneToMany` relationships
- JPA association fields must have unique names within a single entity inheritance chain
- Custom relationship handlers need to be implemented for complex cases

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

Apache 2.0
