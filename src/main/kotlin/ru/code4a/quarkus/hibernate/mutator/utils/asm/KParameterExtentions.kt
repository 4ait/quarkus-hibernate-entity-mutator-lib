package ru.code4a.quarkus.hibernate.mutator.utils.asm

import org.objectweb.asm.Type
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.jvmErasure

fun List<KParameter>.asAsmArguments(): String {
  val params =
    joinToString("") {
      "L${Type.getInternalName(it.type.jvmErasure.java)};"
    }

  return "(${params})V"
}
