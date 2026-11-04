package io.github.vgdotnet.dayboard.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import io.github.vgdotnet.dayboard.model.CalendarEvent
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Events from the device calendar store (what the Google Calendar app syncs), read through
 * [CalendarContract.Instances] so repeating events come expanded. Needs READ_CALENDAR.
 */
class DeviceCalendar(context: Context) {
    private val context = context.applicationContext

    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun events(from: Instant, to: Instant, zone: ZoneId): List<CalendarEvent> {
        if (!hasPermission) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(from.toEpochMilli().toString())
            .appendPath(to.toEpochMilli().toString())
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        )
        val selection = "${CalendarContract.Instances.VISIBLE} = 1"
        val result = ArrayList<CalendarEvent>()
        val cursor = runCatching {
            context.contentResolver.query(uri, projection, selection, null, "${CalendarContract.Instances.BEGIN} ASC")
        }.getOrNull() ?: return emptyList()
        cursor.use { c ->
            while (c.moveToNext()) {
                val status = c.getInt(8)
                if (status == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                val eventId = c.getLong(0)
                val begin = c.getLong(2)
                val end = c.getLong(3)
                val allDay = c.getInt(4) == 1
                val description = c.getString(6).orEmpty()
                val location = c.getString(7).orEmpty()
                val meet = MEET_LINK.find("$location\n$description")?.value
                val eventZone = if (allDay) ZoneId.of("UTC") else zone
                result += CalendarEvent(
                    id = "$eventId@$begin",
                    title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(без названия)",
                    start = ZonedDateTime.ofInstant(Instant.ofEpochMilli(begin), eventZone),
                    end = ZonedDateTime.ofInstant(Instant.ofEpochMilli(end), eventZone),
                    color = 0xFF000000L or (c.getInt(5).toLong() and 0xFFFFFFL),
                    allDay = allDay,
                    meetUrl = meet,
                    openUrl = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId).toString(),
                    calendarName = c.getString(9).orEmpty(),
                )
            }
        }
        return result
    }

    /** Calls [onChange] whenever the calendar store changes; returns the unregister action. */
    fun observe(onChange: () -> Unit): () -> Unit {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        return { context.contentResolver.unregisterContentObserver(observer) }
    }

    private companion object {
        val MEET_LINK = Regex("https://meet\\.google\\.com/[a-z]{3}-[a-z]{4}-[a-z]{3}")
    }
}
