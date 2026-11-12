package com.seqn.timewatcher.data.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder

/** Gson-friendly shapes for export/import. Plain fields, no Kotlin-isms Gson can't handle. */

data class ExportStateDef(
    val id: String,
    val name: String,
    val category: String,
    val pomodoroEnabled: Boolean,
    val pomodoroMinutes: Int,
    val sortOrder: Int,
    val note: String? = null,
)

data class ExportEvent(
    val id: Long,
    val stateId: String,
    val stateName: String,
    val category: String,
    val timestamp: Long,
    val pomodoroEndTs: Long?,
    val pomodoroApplied: Boolean,
)

data class ExportConfig(
    val states: List<ExportStateDef>,
    val pomodoroExitStateId: String,
    /** Added in schema 3. Nullable so schema-2 backups deserialize safely. */
    val lambda: Int? = null,
    /** Added in schema 4. Includes colors for deleted states that still have history. */
    val stateColors: Map<String, Int>? = null,
    val minimumFocusBreakMinutes: Int? = null,
    val lastFocusPomodoroEndTs: Long? = null,
)

data class ExportBundle(
    val schemaVersion: Int,
    val exportedAt: Long,
    val config: ExportConfig,
    val events: List<ExportEvent>,
)

object JsonCodec {
    val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun toJson(bundle: ExportBundle): String = gson.toJson(bundle)

    fun fromJson(json: String): ExportBundle? = try {
        gson.fromJson(json, ExportBundle::class.java)
    } catch (e: Exception) {
        null
    }
}
