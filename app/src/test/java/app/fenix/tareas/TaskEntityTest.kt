package app.fenix.tareas
import app.fenix.tareas.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Test
class TaskEntityTest {
    @Test fun newTaskStartsPendingAndDisabled() {
        val task = TaskEntity(title = "Buscar novedades", instructions = "Consultar fuentes")
        assertEquals("Pendiente", task.status)
        assertEquals(false, task.enabled)
        assertEquals(null, task.scheduleAtMillis)
    }
}
