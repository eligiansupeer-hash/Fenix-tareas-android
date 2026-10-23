package app.fenix.tareas.ai

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DeviceClock {
    fun contextLine(): String {
        val now = ZonedDateTime.now()
        val formatted = now.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy, HH:mm:ss", Locale.forLanguageTag("es-AR")))
        return "Fecha y hora local REAL del dispositivo al iniciar esta solicitud: $formatted. Zona horaria: ${now.zone.id}. Para preguntas sobre ahora, hoy, mañana o tiempos relativos, usá esta referencia; no inventes ni estimes la hora."
    }

    fun currentTime(): String =
        ZonedDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT))
}
