package com.polymath.fs.domain.canvas.models

import java.util.UUID

enum class PresetLayoutType {
    PROJECT_FLOW,
    CHRONOLOGICAL,
    RESOURCE_CLUSTERS,
    HIERARCHICAL_ORBIT,
    CUSTOM
}

data class CanvasPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val isBuiltIn: Boolean = false,
    val layoutType: PresetLayoutType,
    val createdAt: Long = System.currentTimeMillis(),
    val templateJson: String = "{}"
) {
    companion object {
        val BUILT_IN_PRESETS = listOf(
            CanvasPreset(
                id = "builtin_project_flow",
                name = "Project Flow",
                description = "Left-to-right development pipeline (Config → Core Code → Assets → Binaries)",
                isBuiltIn = true,
                layoutType = PresetLayoutType.PROJECT_FLOW
            ),
            CanvasPreset(
                id = "builtin_chronological",
                name = "Chronological Timeline",
                description = "Timeline flow ordered by modification recency (Active today → Older archives)",
                isBuiltIn = true,
                layoutType = PresetLayoutType.CHRONOLOGICAL
            ),
            CanvasPreset(
                id = "builtin_resource_clusters",
                name = "Resource Clusters",
                description = "Orbital satellite clusters partitioned by file MIME categories & types",
                isBuiltIn = true,
                layoutType = PresetLayoutType.RESOURCE_CLUSTERS
            ),
            CanvasPreset(
                id = "builtin_hierarchical_orbit",
                name = "Hierarchical Orbit",
                description = "Concentric radial orbits: Central directory root → Folder ring → File satellites",
                isBuiltIn = true,
                layoutType = PresetLayoutType.HIERARCHICAL_ORBIT
            )
        )
    }
}
