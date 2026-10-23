# Fénix Tareas Android

Aplicación Android nativa para conversar con modelos de IA, investigar en la web, crear tareas programadas y consultar sus resultados.

## Funciones en esta entrega

- Chat como pantalla inicial, configuración de proveedores, tareas y bandeja de resultados.
- Configuración de proveedores compatibles con la API de chat de OpenAI, con hasta diez claves por proveedor y router secuencial ante errores.
- Cifrado local de la configuración de proveedores con AES-GCM y clave generada en Android Keystore.
- Catálogo configurable con Pollinations y AI Horde públicos sin clave, además de Ollama sin clave para una instancia propia local/remota. Los servicios públicos pueden limitar o cambiar el acceso; Ollama necesita un servidor accesible por red.
- Los proveedores nuevos se incorporan a instalaciones existentes sin borrar URLs, modelos ni claves que ya estén guardados.
- La pantalla de proveedores muestra las capacidades implementadas y permite probar explícitamente una conexión con el proveedor/modelo seleccionado. El router prioriza proveedores con éxito reciente y aplica una pausa temporal después de fallos repetidos.
- Investigación web sin API key: búsqueda HTML pública de DuckDuckGo, alternativa de búsqueda de Wikipedia si el buscador principal no devuelve resultados, extracción de texto de hasta tres páginas públicas, títulos, dominios, fragmentos, URLs, fecha de publicación detectada y momento de consulta. La extracción puede fallar en sitios que bloquean bots, exigen JavaScript o requieren iniciar sesión; se conserva el fragmento cuando la página no puede leerse.
- Tareas programadas que registran cada ejecución por separado, con estado, hora de inicio/fin, duración, intentos, proveedor, resultado, error y cantidad de fuentes; las actualizaciones del estado y del registro se guardan en una transacción Room.
- Historial de ejecuciones con búsqueda por tarea/informe, filtro por estado, compartir un resultado, borrar registros individuales o borrar el historial completo con confirmación. Las definiciones de tareas se conservan al borrar el historial; también se puede elegir retención sin límite, 30 o 90 días.
- Plantillas editables para investigación con fuentes, comparación de opciones, resumen/plan de acción, seguimiento periódico y flujos de hasta diez pasos encadenados.
- Los flujos pasan el resultado de cada paso al siguiente, guardan checkpoints y pueden reanudar desde el último paso persistido cuando WorkManager reintenta la ejecución.
- Controles para detener una tarea o cancelar su horario; los fallos de red/cuota temporales tienen hasta tres intentos con espera exponencial.
- El chat y las tareas reciben la fecha, hora y zona horaria del reloj real del dispositivo. La skill de alarma procesa localmente la hora solicitada, muestra la hora actual y abre la aplicación de reloj para confirmación.
- Room para persistencia y WorkManager para ejecución diferible.

## Límites y seguridad

- No incluir claves API ni credenciales en el repositorio.
- Las claves se cifran en el almacenamiento local de la app; no se transmiten al backend de Fénix.
- El router intenta las claves configuradas y luego los proveedores habilitados. No puede evitar que un proveedor suspenda una cuenta o cambie su cuota.
- Los proveedores con clave usan una API compatible con `/v1/chat/completions`; Pollinations y AI Horde tienen adaptadores específicos. Ollama utiliza su endpoint compatible con OpenAI.
- Pollinations es experimental; AI Horde usa una cola pública y puede demorar o no tener disponible el modelo pedido. Ollama no es un servicio cloud y requiere un servidor ejecutándose.
- Las alarmas usan la interfaz del reloj del sistema; el usuario debe confirmar. El permiso de alarma exacta declarado no implica por sí solo programación exacta desde Fénix.
- WorkManager no garantiza ejecución al segundo exacto. Las tareas programadas dependen de las restricciones y políticas de Android.
- La lectura web limita redirecciones, vuelve a validar cada destino y rechaza direcciones locales/privadas, incluidas respuestas DNS que apunten a ellas. Estas comprobaciones son defensa en profundidad; no sustituyen reglas de salida de red del sistema operativo.
- El enrutador registra señales locales de salud de proveedores, prioriza proveedores que respondieron recientemente y aplica una pausa temporal después de fallos repetidos; aun así prueba alternativas disponibles. Las capacidades declaradas no afirman soporte para llamadas a herramientas ni JSON estructurado porque el cliente actual no implementa esos formatos.
- Las notificaciones permiten abrir Resultados y, tras un fallo terminal, reintentar la tarea. WorkManager puede retrasar el trabajo por las políticas de batería de Android.
- La investigación no es un crawler completo ni una búsqueda académica exhaustiva: lee como máximo tres páginas, no garantiza independencia o autoridad de las fuentes y no verifica automáticamente cada afirmación. Revisar las URLs, fechas y fuentes primarias antes de decisiones importantes.

## Build y pruebas

GitHub Actions compila la APK de depuración, ejecuta pruebas unitarias y publica un artefacto de instalación. Una compilación verde no sustituye la prueba de red, permisos y alarmas en un dispositivo físico.


## Plan técnico

El plan completo por fases, con criterios de salida y una lista de validación en dispositivo, está en [`docs/PLAN_EVOLUCION_TECNICA.md`](docs/PLAN_EVOLUCION_TECNICA.md). La rama de trabajo incluye seguridad web, historial por ejecución, selección de proveedores, notificaciones accionables, retención configurable y flujos encadenados. La integración depende de que CI y las pruebas en dispositivo sean satisfactorias.
