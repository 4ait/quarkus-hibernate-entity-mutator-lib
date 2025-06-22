package ru.code4a.quarkus.hibernate.mutator.utils.hibernate

import java.lang.reflect.Field
import java.lang.reflect.Method

class HibernateFieldAccessor(
  val field: Field
) {
  val readMethod: Method
  val writeMethod: Method

  init {
    val clazz =
      field.declaringClass

    readMethod =
      clazz.declaredMethods.find { it.name == "$\$_hibernate_read_${field.name}" }
        ?: error("Can't find getter $\$_hibernate_read_${field.name} for field ${field.name} in class ${clazz.name}")

    writeMethod =
      clazz.declaredMethods.find { it.name == "$\$_hibernate_write_${field.name}" }
        ?: error("Can't find setter $\$_hibernate_write_${field.name} for field ${field.name} in class ${clazz.name}")
  }

  fun get(entity: Any): Any? {
    return readMethod.invoke(entity)
  }

  fun set(entity: Any, value: Any?) {
    writeMethod.invoke(entity, value)
  }
}
