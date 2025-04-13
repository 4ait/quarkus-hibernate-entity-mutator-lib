# Quarkus Hibernate Mutator

A Quarkus extension for safely managing Hibernate entity relationships in Kotlin applications.

## Overview

This extension provides a convenient and type-safe way to manage bidirectional JPA
relationships in your Quarkus applications.
It automatically detects entity associations at build time and generates
appropriate mutators to ensure both sides of relationships stay in sync.

## Features

- Build-time detection and analysis of JPA entity relationships
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

1. **Build-time phase**: The extension scans all entity classes for `@OneToMany` and `@ManyToOne` annotations and collects information about relationships.

2. **Runtime phase**: The extension uses the collected information to create appropriate mutators for each relationship, ensuring that both sides of the relationship stay synchronized.

The mutators handle different use cases:
- Setting or changing an entity reference
- Adding an entity to a collection
- Removing an entity from a collection
- Replacing an entire collection

## Benefits

- **Type safety**: All operations are type-safe and checked at compile time.
- **Reduced boilerplate**: No need to manually write bidirectional relationship management code.
- **Lazy loading protection**: The extension handles lazy-loaded collections properly.
- **Consistency**: Both sides of relationships are always kept in sync.

## Limitations

- Currently supports `Set` collections for `@OneToMany` relationships
- Custom relationship handlers need to be implemented for complex cases

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

Apache 2.0
