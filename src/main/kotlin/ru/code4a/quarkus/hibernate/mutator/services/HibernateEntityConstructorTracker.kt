package ru.code4a.quarkus.hibernate.mutator.services

object HibernateEntityConstructorTracker {
  private val CONSTRUCTOR_COUNTERS = ThreadLocal.withInitial { 0 }

  @JvmStatic
  fun isEntityConstructorInProgress(): Boolean {
    println(CONSTRUCTOR_COUNTERS.get() > 0)
    return CONSTRUCTOR_COUNTERS.get() > 0
  }

  /**
   * Вызывается в начале выполнения конструктора.
   * Увеличивает счетчик для данного класса.
   */
  @JvmStatic
  fun entityConstructorStarted() {
    val counters = CONSTRUCTOR_COUNTERS.get()
    CONSTRUCTOR_COUNTERS.set(counters + 1)
  }

  /**
   * Вызывается в конце выполнения конструктора.
   * Уменьшает счетчик для данного класса.
   */
  @JvmStatic
  fun entityConstructorFinished() {
    val counters = CONSTRUCTOR_COUNTERS.get()
    CONSTRUCTOR_COUNTERS.set(counters - 1)
  }
}
