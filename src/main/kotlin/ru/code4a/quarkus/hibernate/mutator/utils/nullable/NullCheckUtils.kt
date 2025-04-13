package ru.code4a.quarkus.hibernate.mutator.utils.nullable

object NullCheckUtils {
  fun hasMaximumOneNotNull(vararg objects: Any?): Boolean {
    return objects.map { it != null }.count { it } <= 1
  }

  fun hasMoreThanOneNotNull(vararg objects: Any?): Boolean {
    return objects.map { it != null }.count { it } > 1
  }
}
