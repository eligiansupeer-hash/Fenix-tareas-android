# Fénix Tareas Android

Aplicación Android nativa para conversar con modelos de IA, investigar en la web, crear tareas programadas y consultar sus resultados.

## Funciones en esta entrega

- Chat como pantalla inicial, configuración de proveedores, tareas y bandeja de resultados.
- Configuración de proveedores compatibles con la API de chat de OpenAI, con hasta diez claves por proveedor y router secuencial ante errores.
- Cifrado local de la configuración de proveedores con AES-GCM y clave generada en Android Keystore.
- Catálogo inicial de diez proveedores/espacios configurables. La disponibilidad gratuita y los requisitos de cuenta cambian; verificar las condiciones vigentes del proveedor.
- Conector sin clave marcado como experimental, sujeto a disponibilidad del servicio.
- Búsqueda web mediante resultados HTML públicos; puede sufrir bloqueos, limitación de solicitudes o cambios en la página.
- Tareas programadas que llaman a un proveedor de IA y, cuando la instrucción lo pide, intentan recuperar resultados web; las respuestas y errores se guardan en el dispositivo.
- Skill básica de alarma desde el chat: abre la aplicación de reloj de Android para que el usuario confirme la hora. No crea alarmas silenciosamente.
- Room para persistencia y WorkManager para ejecución diferible.

## Límites y seguridad

- No incluir claves API ni credenciales en el repositorio.
- Las claves se cifran en el almacenamiento local de la app; no se transmiten al backend de Fénix.
- El router intenta las claves configuradas y luego los proveedores habilitados. No puede evitar que un proveedor suspenda una cuenta o cambie su cuota.
- Los endpoints y modelos editables deben corresponder a una API compatible con `/chat/completions`. No todos los proveedores implementan el mismo formato.
- El proveedor sin clave es experimental y puede dejar de estar disponible o exigir autenticación.
- Las alarmas usan la interfaz del reloj del sistema; el usuario debe confirmar. El permiso de alarma exacta declarado no implica por sí solo programación exacta desde Fénix.
- WorkManager no garantiza ejecución al segundo exacto. Las tareas programadas dependen de las restricciones y políticas de Android.
- Los resultados web son evidencia preliminar: contrastar las fuentes antes de tomar decisiones importantes.

## Build y pruebas

GitHub Actions compila la APK de depuración, ejecuta pruebas unitarias y publica un artefacto de instalación. Una compilación verde no sustituye la prueba de red, permisos y alarmas en un dispositivo físico.
