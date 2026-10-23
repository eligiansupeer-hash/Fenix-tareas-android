# Fénix Tareas Android

Aplicación Android nativa para conversar con modelos de IA, investigar en la web, crear tareas programadas y consultar sus resultados.

## Funciones en esta entrega

- Chat como pantalla inicial, configuración de proveedores, tareas y bandeja de resultados.
- Configuración de proveedores compatibles con la API de chat de OpenAI, con hasta diez claves por proveedor y router secuencial ante errores.
- Cifrado local de la configuración de proveedores con AES-GCM y clave generada en Android Keystore.
- Catálogo configurable con Pollinations y AI Horde públicos sin clave, además de Ollama sin clave para una instancia propia local/remota. Los servicios públicos pueden limitar o cambiar el acceso; Ollama necesita un servidor accesible por red.
- Los proveedores nuevos se incorporan a instalaciones existentes sin borrar URLs, modelos ni claves que ya estén guardados.
- Investigación web sin API key: búsqueda HTML pública de DuckDuckGo, alternativa de búsqueda de Wikipedia si el buscador principal no devuelve resultados, extracción de texto de hasta tres páginas públicas, títulos, dominios, fragmentos y URLs. La extracción puede fallar en sitios que bloquean bots, exigen JavaScript o requieren iniciar sesión; se conserva el fragmento cuando la página no puede leerse.
- Tareas programadas que llaman a un proveedor de IA y, cuando la instrucción lo pide, recuperan evidencia web y la pasan al modelo con instrucciones para separar hechos e inferencias y numerar fuentes.
- Plantillas editables para investigación con fuentes, comparación de opciones, resumen/plan de acción y seguimiento periódico de novedades.
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
- La investigación no es un crawler completo ni una búsqueda académica exhaustiva: lee como máximo tres páginas, no garantiza independencia o autoridad de las fuentes y no verifica automáticamente cada afirmación. Revisar las URLs, fechas y fuentes primarias antes de decisiones importantes.

## Build y pruebas

GitHub Actions compila la APK de depuración, ejecuta pruebas unitarias y publica un artefacto de instalación. Una compilación verde no sustituye la prueba de red, permisos y alarmas en un dispositivo físico.
