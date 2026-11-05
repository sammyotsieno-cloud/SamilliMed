package org.samillimed.app.data.shifts

import com.google.firebase.firestore.DocumentId
import java.time.LocalDateTime

/**
 * A working shift, defined by an admin (later). code is one or two letters used in visit
 * references, for example M for Morning. Times are minutes after midnight.
 */
data class Shift(
    @DocumentId val id: String = "",
    val name: String = "",
    val code: String = "",
    val startMinute: Int = 0,
    val endMinute: Int = 0,
    val active: Boolean = true,
)

/** A night shift ends on the next day (for example 22:00 to 06:00). */
val Shift.crossesMidnight: Boolean get() = endMinute <= startMinute

/** Until an admin defines shifts, everything belongs to one all-day shift called General. */
val DefaultShift = Shift(id = "general", name = "General", code = "G", startMinute = 0, endMinute = 1440)

/**
 * The shift a bill belongs to. dateKey is the date that shift started (so a night shift keeps one
 * date), and afterShift is true when the user kept working after their shift ended.
 */
class ShiftContext(val shift: Shift, val dateKey: String, val afterShift: Boolean)

object ShiftResolver {
    /**
     * Today there is only the default shift. When logins, roles and shift assignments exist, this
     * is the one place that changes: it will look up the shift assigned to the signed-in user.
     */
    fun current(now: LocalDateTime = LocalDateTime.now()): ShiftContext =
        ShiftContext(DefaultShift, now.toLocalDate().toString(), afterShift = false)
}
