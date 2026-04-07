package com.surveysparrow.surveysparrow_android_sdk.internal.builder

import androidx.compose.runtime.Composable
import org.json.JSONObject

internal typealias ComposeComponentFactory = @Composable (
    props: Map<String, Any?>,
    children: @Composable () -> Unit
) -> Unit

internal object ComponentRegistry {
    private val components = mutableMapOf<String, ComposeComponentFactory>()

    fun register(name: String, factory: ComposeComponentFactory) {
        components[name] = factory
    }

    fun get(name: String): ComposeComponentFactory? = components[name]

    fun has(name: String): Boolean = components.containsKey(name)

    fun list(): Set<String> = components.keys
}
