# Fénix Tareas Android

Aplicación Android nativa para crear, ejecutar y programar tareas automatizadas de búsqueda web, análisis asistido por IA y notificaciones locales.

## Estado del proyecto

Proyecto en fase inicial. La primera entrega prioriza una base Android compilable, almacenamiento local, programación de tareas y un historial trazable. Los conectores externos y las skills declarativas se incorporarán de forma incremental, documentando los límites y requisitos de cada proveedor.

## Arquitectura prevista

- Kotlin, Jetpack Compose y Material 3.
- Target SDK 36 (Android 16).
- Room para persistencia local.
- WorkManager para trabajo diferible y reintentos.
- AlarmManager solo cuando se requiera una hora exacta, sujeto a permisos y restricciones del sistema.
- Cifrado de credenciales con Android Keystore y AES-GCM.
- Skills declarativas validadas; no ejecutar código arbitrario de terceros.

## Seguridad

No incluir claves API, tokens ni credenciales en el repositorio. Las claves de los proveedores deberán permanecer en el dispositivo y almacenarse cifradas.

## Compilación y pruebas

La compilación se considerará verificada solo cuando un workflow o entorno de build real termine correctamente. Crear o editar archivos no equivale a una compilación validada. Los bloqueos y las funciones todavía no implementadas se documentarán explícitamente.
