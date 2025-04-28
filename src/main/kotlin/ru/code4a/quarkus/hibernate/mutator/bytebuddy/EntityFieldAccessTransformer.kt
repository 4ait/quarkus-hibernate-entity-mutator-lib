package ru.code4a.quarkus.hibernate.mutator.bytebuddy

import io.netty.util.internal.ConcurrentSet
import io.vertx.core.impl.ConcurrentHashSet
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * ASM ClassVisitor that transforms direct field assignments to use the entity interceptor.
 */
internal class EntityFieldAccessTransformer(
  api: Int,
  visitor: ClassVisitor,
  private val entityClassName: String,
  private val entityJpaFields: Set<String>
) : ClassVisitor(api, visitor) {

  override fun visitMethod(
    access: Int,
    name: String,
    descriptor: String,
    signature: String?,
    exceptions: Array<out String>?
  ): MethodVisitor {
    val originalVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)

    // Only transform non-synthetic methods
    if (access and Opcodes.ACC_SYNTHETIC == 0) {
      return EntityMethodTransformer(
        api = api,
        visitor = originalVisitor,
        entityClassName = entityClassName,
        entityJpaFields = entityJpaFields
      )
    }

    return originalVisitor
  }
}
