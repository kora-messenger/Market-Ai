package com.veltravia.marketai.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * Minimal multiplatform navigation with an API close to androidx.navigation
 * (navigate / popBackStack / popUpTo / route arguments) — identical behavior
 * on Android and iOS with zero platform-specific code.
 */
class NavBackStackEntry(val route: String, val arguments: Map<String, String> = emptyMap())

/** Route arguments: entry.arguments.getString("instrumentId") — same call sites as before. */
fun Map<String, String>.getString(key: String): String? = this[key]

class NavHostController internal constructor(initialRoute: String) {
    var stack by mutableStateOf(listOf(initialRoute))
        internal set

    val currentRoute: String get() = stack.lastOrNull() ?: initialRoute

    fun navigate(route: String, popUpTo: String? = null, inclusive: Boolean = false) {
        val newStack = stack.toMutableList()
        if (popUpTo != null) {
            val idx = newStack.indexOfFirst {
                it == popUpTo || it.substringBefore('/') == popUpTo
            }
            if (idx >= 0) {
                val end = if (inclusive) idx else idx + 1
                newStack.subList(0, end).clear()
            }
        }
        newStack.add(route)
        stack = newStack
    }

    fun popBackStack(): Boolean {
        if (stack.size <= 1) return false
        stack = stack.dropLast(1)
        return true
    }
}

@Composable
fun rememberNavController(startDestination: String): NavHostController =
    rememberSaveable(
        saver = Saver(
            save = { ArrayList(it.stack) },
            restore = { saved -> NavHostController(startDestination).also { c -> c.stack = saved } }
        )
    ) { NavHostController(startDestination) }

private class Destination(
    val base: String,
    val pattern: String,
    val body: @Composable (NavBackStackEntry) -> Unit
)

/** Composable navigation container; routes may declare arguments as "upload/{instrumentId}". */
@Composable
fun NavHost(
    navController: NavHostController,
    startDestination: String,
    builder: NavGraphBuilder.() -> Unit
) {
    val graph = NavGraphBuilder().apply(builder)
    val current = navController.currentRoute
    val base = current.substringBefore('/')
    val dest = graph.destinations.firstOrNull { it.base == base } ?: return
    // Extract route arguments by comparing the declared pattern with the actual route.
    val patternSegments = dest.pattern.split('/')
    val actualSegments = current.split('/')
    val args = mutableMapOf<String, String>()
    for (i in patternSegments.indices) {
        val p = patternSegments.getOrNull(i) ?: break
        if (p.startsWith("{") && p.endsWith("}")) {
            actualSegments.getOrNull(i)?.let { v ->
                args[p.removePrefix("{").removeSuffix("}")] = v
            }
        }
    }
    dest.body(NavBackStackEntry(current, args))
}

class NavGraphBuilder {
    internal val destinations = mutableListOf<Destination>()

    fun composable(route: String, body: @Composable (NavBackStackEntry) -> Unit) {
        destinations.add(Destination(route.substringBefore('/'), route, body))
    }
}
