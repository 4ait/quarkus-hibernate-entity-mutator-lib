package ru.code4a.quarkus.hibernate.mutator.bytebuddy

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.AdviceAdapter.DUP
import org.objectweb.asm.commons.AdviceAdapter.INVOKESPECIAL
import org.objectweb.asm.commons.AdviceAdapter.INVOKESTATIC
import ru.code4a.quarkus.hibernate.mutator.services.HibernateEntityMutatorProcessor
import ru.code4a.quarkus.hibernate.mutator.utils.asm.asAsmArguments
import java.lang.reflect.Modifier
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.jvmErasure

/**
 * ASM MethodVisitor that finds field assignments and adds interceptor calls before them.
 */
internal class EntityMethodTransformer(
  api: Int,
  visitor: MethodVisitor,
  private val entityClassName: String,
  private val entityJpaFields: Set<String>
) : MethodVisitor(api, visitor) {

  private val entityInternalName = entityClassName.replace('.', '/')

  override fun visitMethodInsn(
    opcode: Int,
    owner: String,
    name: String,
    descriptor: String,
    isInterface: Boolean
  ) {
    // Call the original instruction
    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)

    // Check if this is a constructor call for our entity
    if (opcode == INVOKESPECIAL &&
      owner == entityInternalName &&
      name == "<init>"
    ) {
      // After constructor call, the stack has the new entity instance on top
      // We need to duplicate it for our initialization call
      mv.visitInsn(DUP)  // Duplicate the entity reference

      val invokeObj = HibernateEntityMutatorInjectedProcessor
      val invoke = invokeObj::initializeEntity

      Modifier.isStatic(invoke.javaMethod!!.modifiers)

      require(invoke.valueParameters.size == 1)
      require(
        Any::class.java.isAssignableFrom(invoke.valueParameters[0].type.jvmErasure.java)
      )

      // Call the initializer
      mv.visitMethodInsn(
        INVOKESTATIC,
        Type.getInternalName(invokeObj::class.java),
        invoke.javaMethod!!.name,
        invoke.valueParameters.asAsmArguments(),
        false
      )

      // Now the stack is back to having just the entity reference on top
    }
  }

  /*override fun visitFieldInsn(
    opcode: Int,
    owner: String,
    name: String,
    descriptor: String
  ) {
    // Intercept PUTFIELD operations on JPA fields in the current class
    if (opcode == Opcodes.PUTFIELD && owner == entityInternalName && name in entityJpaFields) {
      // We need to check if this is a collection field
      val isCollectionField = isCollectionField(descriptor)

      if (isCollectionField) {
        // For collections, we completely replace the PUTFIELD with our interceptor call
        // Stack before: target, value

        // The interceptReplaceCollection signature: (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V
        mv.visitLdcInsn(name)  // Push field name onto stack
        // Stack now: target, value, fieldName

        // Reorder stack: target, fieldName, value
        mv.visitInsn(Opcodes.SWAP)  // Swap value and fieldName

        val invokeObj = HibernateEntityMutatorInjectedProcessor
        val invoke = invokeObj::invokeSetCollection

        Modifier.isStatic(invoke.javaMethod!!.modifiers)

        require(invoke.valueParameters.size == 3)
        require(
          Any::class.java.isAssignableFrom(invoke.valueParameters[0].type.jvmErasure.java)
        )
        require(
          String::class.java.isAssignableFrom(invoke.valueParameters[1].type.jvmErasure.java)
        )
        require(
          Collection::class.java.isAssignableFrom(invoke.valueParameters[2].type.jvmErasure.java)
        )

        // Call the interceptor which will handle the assignment
        mv.visitMethodInsn(
          Opcodes.INVOKESTATIC,
          Type.getInternalName(invokeObj::class.java),
          invoke.javaMethod!!.name,
          invoke.valueParameters.asAsmArguments(),
          false
        )
        // Stack now: -
      } else {
        // For non-collection fields, we add intercept call before PUTFIELD
        // Stack before: target, value

        // Duplicate the target and value for the interceptor call
        mv.visitInsn(Opcodes.DUP2)  // Duplicate top two stack elements (target, value)
        // Stack now: target, value, target, value

        // The interceptSetField signature: (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V
        mv.visitLdcInsn(name)  // Push field name onto stack
        // Stack now: target, value, target, value, fieldName

        // Swap value and fieldName to get the right parameter order
        mv.visitInsn(Opcodes.SWAP)  // Swap value and fieldName
        // Stack now: target, value, target, fieldName, value

        val invokeObj = HibernateEntityMutatorInjectedProcessor
        val invoke = invokeObj::invokeBeforeSet

        Modifier.isStatic(invoke.javaMethod!!.modifiers)

        require(invoke.valueParameters.size == 3)
        require(
          Any::class.java.isAssignableFrom(invoke.valueParameters[0].type.jvmErasure.java)
        )
        require(
          String::class.java.isAssignableFrom(invoke.valueParameters[1].type.jvmErasure.java)
        )
        require(
          Any::class.java.isAssignableFrom(invoke.valueParameters[2].type.jvmErasure.java)
        )

        // Call the interceptor
        mv.visitMethodInsn(
          Opcodes.INVOKESTATIC,
          Type.getInternalName(invokeObj::class.java),
          invoke.javaMethod!!.name,
          invoke.valueParameters.asAsmArguments(),
          false
        )
        // Stack now: target, value

        // After the interceptor call, the original target and value are still on the stack
        // Proceed with the original PUTFIELD instruction
        super.visitFieldInsn(opcode, owner, name, descriptor)
        // Stack now: -
      }
    } else {
      // For other operations, use original behavior
      super.visitFieldInsn(opcode, owner, name, descriptor)
    }
  }*/

  /**
   * Checks if a field descriptor represents a collection type.
   */
  private fun isCollectionField(descriptor: String): Boolean {
    // Check for common collection types: Set, List, Collection
    return descriptor.contains("Ljava/util/Set;") ||
      descriptor.contains("Ljava/util/List;") ||
      descriptor.contains("Ljava/util/Collection;") ||
      descriptor.contains("Ljava/util/ArrayList;") ||
      descriptor.contains("Ljava/util/HashSet;") ||
      descriptor.contains("Ljava/util/LinkedList;") ||
      descriptor.contains("Ljava/util/LinkedHashSet;")
  }
}
