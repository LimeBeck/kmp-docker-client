package dev.limebeck.libs.docker.client.dslUtils

import kotlin.jvm.JvmName
import kotlin.reflect.KProperty

interface ApiCacheHolder {
    val apiCache: MutableMap<Any, Any>
}

class ApiDelegate<T : Any, R : ApiCacheHolder>(val factory: (R) -> T) {
    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: R, property: KProperty<*>): T {
        return thisRef.apiCache.getOrPut(property.name) {
            factory(thisRef)
        } as T
    }
}

fun <T : Any, R : ApiCacheHolder> api(factory: (R) -> T) =
    ApiDelegate(factory)

typealias ApiFactory<T, R> = (R) -> T

@JvmName("apiDelegateExtension")
fun <T : Any, R : ApiCacheHolder> ApiFactory<T, R>.api() =
    ApiDelegate(this)
