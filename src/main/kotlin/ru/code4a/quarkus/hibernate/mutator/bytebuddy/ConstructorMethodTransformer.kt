package ru.code4a.quarkus.hibernate.mutator.bytebuddy

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Type
import org.objectweb.asm.commons.AdviceAdapter
import ru.code4a.quarkus.hibernate.mutator.services.HibernateEntityConstructorTracker
import ru.code4a.quarkus.hibernate.mutator.services.HibernateEntityMutatorProcessor
import kotlin.reflect.jvm.javaMethod

internal class ConstructorMethodTransformer(
  api: Int,
  visitor: ClassVisitor,
  private val className: String
) : ClassVisitor(api, visitor) {

  override fun visitMethod(
    access: Int,
    name: String,
    descriptor: String,
    signature: String?,
    exceptions: Array<out String>?
  ): MethodVisitor {
    val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)

    if (name == "<init>") {
      return ConstructorAdviceAdapter(
        api,
        methodVisitor,
        access,
        name,
        descriptor,
        className
      )
    }

    return methodVisitor
  }
}

internal class ConstructorAdviceAdapter(
  api: Int,
  methodVisitor: MethodVisitor,
  access: Int,
  name: String,
  descriptor: String,
  private val className: String
) : AdviceAdapter(api, methodVisitor, access, name, descriptor) {

  override fun onMethodEnter() {
    visitMethodInsn(
      INVOKESTATIC,
      Type.getInternalName(HibernateEntityConstructorTracker::class.java),
      HibernateEntityConstructorTracker::entityConstructorStarted.javaMethod!!.name,
      "()V",
      false
    )
  }

  override fun onMethodExit(opcode: Int) {
    visitMethodInsn(
      INVOKESTATIC,
      Type.getInternalName(HibernateEntityConstructorTracker::class.java),
      HibernateEntityConstructorTracker::entityConstructorFinished.javaMethod!!.name,
      "()V",
      false
    )
  }
}
