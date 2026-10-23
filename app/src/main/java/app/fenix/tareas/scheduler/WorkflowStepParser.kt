package app.fenix.tareas.scheduler

object WorkflowStepParser {
    /** Parses one user-authored step per line, accepting common numbered and bullet prefixes. */
    fun parse(raw: String): List<String> = raw.lineSequence()
        .map { it.trim().replace(Regex("^(?:[-*•]\\s*|\\d+[.)]\\s*)"), "") }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()
}
