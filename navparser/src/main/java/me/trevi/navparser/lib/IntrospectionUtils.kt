package me.trevi.navparser.lib

import kotlin.reflect.KProperty
import kotlin.reflect.full.hasAnnotation

@Target(AnnotationTarget.PROPERTY)
annotation class Mutable

abstract class MutableContent {
    override fun equals(other: Any?): Boolean {
        if (other == null || other::class != this::class)
            return false

        this::class.members.forEach { m ->
            if (m is KProperty && !m.hasAnnotation<Mutable>()) {
                if (m.getter.call(this) != m.getter.call(other))
                    return false
            }
        }

        return true
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}
