package me.trevi.navparser.lib

import timber.log.Timber as Log
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.*

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

    fun mutableEquals(other: Any?): Boolean {
        if (other == null || other::class != this::class)
            return false

        this::class.members.forEach { m ->
            if (m is KProperty && m.hasAnnotation<Mutable>()) {
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

interface Introspectable {
    private fun serializableMap(map: Map<String, Any?>, returnKClass: KClass<*>) : AbstractMapStringAnySerializable {
        return try {
            returnKClass.constructors.find {
                return@find (it.parameters.size == 1 &&
                        map::class.isSubclassOf(it.parameters[0].type.classifier as KClass<*>))
            }!!.call(map) as AbstractMapStringAnySerializable
        } catch (e: Throwable) {
            throw NoClassDefFoundError("Impossible to find required constructor for $returnKClass")
        }
    }

    fun asMap() : MapStringAny {
        val map = mutableMapOf<String, Any?>()

        this::class.members.forEach { m ->
            if (m is KProperty && m.visibility == KVisibility.PUBLIC && m.isFinal)
                map[m.name] = m.getter.call(this)
        }

        return MapStringAny(map)
    }

    fun diffMap(other: Introspectable, returnKClass: KClass<*>) : AbstractMapStringAnySerializable {
        val diff = mutableMapOf<String, Any?>()
        val mutableDiff = mutableMapOf<String, Any?>()

        this::class.members.forEach { m ->
            if (m is KProperty && m.visibility == KVisibility.PUBLIC && m.isFinal) {
                val destMap = if (m.hasAnnotation<Mutable>()) mutableDiff else diff
                m.getter.call(other).also { otherValue ->
                    val thisValue = m.getter.call(this)
                    val equals = if (thisValue != null && m.hasAnnotation<Mutable>() &&
                        (m.returnType.classifier as KClass<*>).isSubclassOf(MutableContent::class)) {
                        (thisValue as MutableContent).mutableEquals(otherValue)
                    } else {
                        thisValue == otherValue
                    }

                    if (!equals) {
                        destMap[m.name] = otherValue
                    }
                }
            }
        }

        if (diff.isNotEmpty())
            diff.putAll(mutableDiff)

        return serializableMap(diff, returnKClass)
    }

    fun debugIntrospect(value: Any = this, klass: KClass<*> = this::class, pad : Int = 0) {
        var padStr = ""
        for (i in 0..pad)
            padStr += "  "

        Log.d("${padStr}Looking for members of $klass")

        klass.members.forEach { m ->
            if (m is KProperty && m.visibility == KVisibility.PUBLIC) {
                val memberValue = m.getter.call(value)

                Log.d("${padStr}${m.name}: $memberValue (${m.returnType})")
                (m.returnType.classifier as KClass<*>).also { memberClass ->
                    if (memberValue != null && memberClass.isData)
                        debugIntrospect(memberValue, memberClass, pad + 1)
                }
            }
        }
    }

    fun deepDiff(other : Introspectable,
                 returnKClass: KClass<*>,
                 thisValue: Any = this, otherValue: Any = other,
                 klass: KClass<*> = this::class) : AbstractMapStringAnySerializable {
        val diffMap = mutableMapOf<String, Any?>()
        val mutableDiffMap = mutableMapOf<String, Any?>()
        val isRoot = thisValue == this && klass == this::class

        klass.members.forEach { m ->
            if (m is KProperty && m.visibility == KVisibility.PUBLIC) {
                (m.returnType.classifier as KClass<*>).also { memberClass ->
                    val tv = m.getter.call(thisValue)
                    val ov = m.getter.call(otherValue)
                    val destMap = if (isRoot && m.hasAnnotation<Mutable>()) mutableDiffMap else diffMap
                    val equals = if (tv != null && m.hasAnnotation<Mutable>() &&
                            memberClass.isSubclassOf(MutableContent::class)) {
                        (tv as MutableContent).mutableEquals(ov)
                    } else {
                        tv == ov
                    }

                    if (!equals) {
                        if (tv != null && ov != null && memberClass.isData)
                            destMap[m.name] = deepDiff(other, returnKClass, tv, ov, memberClass)
                        else
                            destMap[m.name] = ov
                    }
                }
            }
        }

        if (isRoot && diffMap.isNotEmpty()) {
            diffMap.putAll(mutableDiffMap)
        }

        return serializableMap(diffMap, returnKClass)
    }

    fun deepDiff(other : Introspectable) : MapStringAny {
        return deepDiff<MapStringAny>(other)
    }
}

inline fun <reified T : AbstractMapStringAnySerializable> Introspectable.deepDiff(other : Introspectable) : T {
    return deepDiff(other, T::class) as T
}

inline fun <reified T : AbstractMapStringAnySerializable> Introspectable.diffMap(other : Introspectable) : T {
    return diffMap(other, T::class) as T
}
