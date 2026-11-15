package com.vuhpquang.appdepot.core

const val SELF_SLUG = "app-depot-android"

sealed interface InstallProgress {
    data object Queued : InstallProgress
    data class Downloading(val percent: Int) : InstallProgress
    data object Verifying : InstallProgress
    data object Installing : InstallProgress
    data object NeedsUserAction : InstallProgress
    data object NeedsPermission : InstallProgress
    data class Done(val version: String) : InstallProgress
    data class Failed(val message: String) : InstallProgress
}

sealed interface InstallOutcome {
    data class Success(val version: String) : InstallOutcome
    data class Failed(val message: String) : InstallOutcome
    data object NeedsUserAction : InstallOutcome
    data object NeedsPermission : InstallOutcome
}

fun interface Installer {
    suspend fun install(app: CatalogApp): InstallOutcome
}

/** Self last, everything else in the given (catalog) order. */
fun orderForUpdateAll(slugs: List<String>, selfSlug: String = SELF_SLUG): List<String> =
    slugs.filter { it != selfSlug } + slugs.filter { it == selfSlug }

fun outcomeProgress(o: InstallOutcome): InstallProgress = when (o) {
    is InstallOutcome.Success -> InstallProgress.Done(o.version)
    is InstallOutcome.Failed -> InstallProgress.Failed(o.message)
    InstallOutcome.NeedsUserAction -> InstallProgress.NeedsUserAction
    InstallOutcome.NeedsPermission -> InstallProgress.NeedsPermission
}

/**
 * Runs the apps one at a time, self last. A failed app does not stop the rest; a missing
 * "Install unknown apps" permission does (every later app would hit the same wall).
 */
suspend fun runInstallQueue(
    apps: List<CatalogApp>,
    installer: Installer,
    onProgress: (slug: String, InstallProgress) -> Unit,
): Map<String, InstallOutcome> {
    val bySlug = apps.associateBy { it.slug }
    val order = orderForUpdateAll(apps.map { it.slug })
    order.forEach { onProgress(it, InstallProgress.Queued) }
    val results = LinkedHashMap<String, InstallOutcome>()
    for ((i, slug) in order.withIndex()) {
        val outcome = installer.install(bySlug.getValue(slug))
        results[slug] = outcome
        onProgress(slug, outcomeProgress(outcome))
        if (outcome == InstallOutcome.NeedsPermission) {
            order.drop(i + 1).forEach { onProgress(it, InstallProgress.NeedsPermission) }
            break
        }
    }
    return results
}
