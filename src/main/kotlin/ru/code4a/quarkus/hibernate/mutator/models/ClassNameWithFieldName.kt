package ru.code4a.quarkus.hibernate.mutator.models

import kotlinx.serialization.Serializable

@Serializable
data class ClassNameWithFieldName(
  val className: String,
  val fieldName: String,
)
