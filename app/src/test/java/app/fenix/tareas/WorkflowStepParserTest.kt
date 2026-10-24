package app.fenix.tareas

import app.fenix.tareas.scheduler.WorkflowStepParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowStepParserTest {
    @Test
    fun parsesNumberedAndBulletedStepsInOrder() {
        assertEquals(
            listOf("Investigar el tema", "Comparar fuentes", "Redactar informe"),
            WorkflowStepParser.parse("1. Investigar el tema\n- Comparar fuentes\n• Redactar informe")
        )
    }

    @Test
    fun ignoresBlankLinesAndWhitespace() {
        assertEquals(listOf("Paso A", "Paso B"), WorkflowStepParser.parse("  \n Paso A \n\n 2) Paso B  "))
    }

    @Test
    fun emptyInputProducesNoSteps() {
        assertTrue(WorkflowStepParser.parse(" \n \n").isEmpty())
    }
}
