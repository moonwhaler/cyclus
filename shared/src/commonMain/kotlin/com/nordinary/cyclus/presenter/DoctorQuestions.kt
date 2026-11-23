package com.nordinary.cyclus.presenter

import com.nordinary.cyclus.backup.ExportFile
import com.nordinary.cyclus.backup.SettingKeys
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One of the person's own questions for the next appointment. [at]: epoch millis of the last change (merge: newer wins). */
@Serializable
data class DoctorQuestion(val id: String, val text: String, val done: Boolean = false, val at: Long = 0)

/**
 * „Fragen für die Ärztin“ (spec §7.2): the person's own list, stored as JSON in the `doctor.questions` setting
 * (encrypted DB, travels in exports and device backups). Never generated from data. List order = display order,
 * newest first; open questions are shown before done ones.
 */
object DoctorQuestions {
    const val MAX = 30
    const val MAX_LEN = 300
    private const val MAX_ID = 64
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = ListSerializer(DoctorQuestion.serializer())

    fun from(settings: Map<String, String>): List<DoctorQuestion> = parse(settings[SettingKeys.DOCTOR_QUESTIONS])

    /** Invalid items are dropped, texts trimmed and cut, duplicates (by id) dropped, the list capped. */
    fun parse(raw: String?): List<DoctorQuestion> {
        if (raw.isNullOrBlank()) return emptyList()
        val list = runCatching { json.decodeFromString(ser, raw) }.getOrNull() ?: return emptyList()
        return cap(clean(list))
    }

    /** The stored value: null for an empty list. Always fits an export's pref limit, so a backup can be imported again. */
    fun encode(list: List<DoctorQuestion>): String? {
        var l = cap(clean(list))
        while (l.isNotEmpty()) {
            val s = json.encodeToString(ser, l)
            if (s.length <= ExportFile.MAX_NOTE) return s
            // too long (escaped quotes, newlines): give up the oldest done question first, then the oldest open one
            val victim = l.filter { it.done }.minByOrNull { it.at } ?: l.minBy { it.at }
            l = l - victim
        }
        return null
    }

    fun open(list: List<DoctorQuestion>) = list.filter { !it.done }
    fun done(list: List<DoctorQuestion>) = list.filter { it.done }

    fun add(list: List<DoctorQuestion>, id: String, text: String, now: Long): List<DoctorQuestion> {
        val t = text.trim().take(MAX_LEN)
        if (t.isEmpty() || open(list).size >= MAX) return list
        return cap(listOf(DoctorQuestion(id, t, false, now)) + list)
    }

    fun edit(list: List<DoctorQuestion>, id: String, text: String, now: Long): List<DoctorQuestion> {
        val t = text.trim().take(MAX_LEN)
        if (t.isEmpty()) return delete(list, id)
        return list.map { if (it.id == id) it.copy(text = t, at = now) else it }
    }

    fun toggle(list: List<DoctorQuestion>, id: String, now: Long) = list.map { if (it.id == id) it.copy(done = !it.done, at = now) else it }
    fun delete(list: List<DoctorQuestion>, id: String) = list.filter { it.id != id }
    fun clearDone(list: List<DoctorQuestion>) = open(list)

    /**
     * Merge import: union by id, the newer change wins (a time from the future counts as [now]). No tombstones: a
     * question deleted here comes back when an older file that still has it is merged.
     */
    fun merge(mine: List<DoctorQuestion>, theirs: List<DoctorQuestion>, now: Long): List<DoctorQuestion> {
        val byId = LinkedHashMap<String, DoctorQuestion>()
        for (q in mine) byId[q.id] = q
        val fresh = mutableListOf<DoctorQuestion>()
        for (raw in theirs) {
            val q = if (raw.at > now) raw.copy(at = now) else raw
            val have = byId[q.id]
            if (have == null) fresh += q else if (q.at > have.at) byId[q.id] = q
        }
        return cap(clean(fresh + byId.values))
    }

    /** At most [MAX]: open questions are kept before done ones, each in list order. */
    private fun cap(list: List<DoctorQuestion>): List<DoctorQuestion> {
        if (list.size <= MAX) return list
        val keep = (open(list) + done(list)).take(MAX).toSet()
        return list.filter { it in keep }
    }

    private fun clean(list: List<DoctorQuestion>) = list
        .filter { it.id.isNotBlank() && it.id.length <= MAX_ID }
        .map { it.copy(text = it.text.trim().take(MAX_LEN)) }
        .filter { it.text.isNotEmpty() }
        .distinctBy { it.id }
}
