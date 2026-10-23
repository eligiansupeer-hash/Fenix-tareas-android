# Conectores personales: asistente de configuración y seguridad

Estado: especificación de producto para implementar por etapas. Este documento no afirma que los conectores estén implementados.

## Objetivo

Fénix debe guiar al usuario paso a paso para conectar sus propias cuentas y servicios, como lo hace Kai 9000: detectar qué integración necesita la tarea, explicar las opciones reales de autenticación, abrir la documentación oficial adecuada, ayudar a crear las credenciales y validar la configuración antes de guardarla. La IA puede explicar y generar instrucciones, pero no debe pedir que se peguen secretos dentro del chat ni recibir contraseñas de cuentas.

## Flujo de usuario

1. El usuario elige un servicio y una capacidad concreta (por ejemplo, buscar correos, leer mensajes, enviar correo, listar archivos de Drive o crear documentos).
2. Fénix explica qué podrá hacer el conector, qué datos saldrán del dispositivo, qué permisos solicita y si la capacidad es de lectura o escritura.
3. El asistente determina el método adecuado y distingue entre OAuth, API key, token personal, contraseña de aplicación, archivo JSON de cliente y conexión local. No debe presentar estos métodos como intercambiables.
4. Fénix abre las instrucciones oficiales del proveedor y guía al usuario mediante pasos numerados. Los nombres de menús y requisitos deben estar versionados/verificados; si no se han confirmado, la interfaz lo indica.
5. El usuario selecciona una credencial o archivo mediante el selector seguro del sistema Android, o completa un formulario dedicado. El contenido secreto nunca se incorpora a la conversación con la IA.
6. Antes de guardar, Fénix muestra un resumen sin revelar el secreto: tipo de credencial, cuenta/servicio, permisos y ubicación de almacenamiento.
7. Fénix valida la configuración con una operación mínima no destructiva. El resultado no debe incluir tokens, contraseñas, claves privadas ni respuestas que los contengan.
8. El usuario puede revisar capacidades y permisos, probar la conexión, renovar credenciales, desconectar el servicio y borrar las credenciales guardadas.

## Google: elegir el método correcto

### OAuth de usuario

Para Gmail API, Google Drive API, Calendar y otros servicios de Google, la ruta preferida es OAuth con los scopes mínimos necesarios. El usuario autoriza su cuenta y Fénix conserva tokens, no la contraseña de Google.

La verificación de la aplicación no es un requisito uniforme para todas las integraciones. Depende, entre otros factores, del tipo de aplicación, los scopes solicitados, el estado de publicación, los usuarios permitidos y las reglas vigentes de Google. Una app externa en modo de prueba puede permitir usuarios de prueba y mostrar advertencias o tener límites; ciertos scopes sensibles o restringidos pueden exigir verificación adicional. Fénix no debe prometer que se evitará la verificación: debe explicar la situación real para el método y los permisos elegidos y ofrecer alternativas permitidas cuando existan.

No reutilizar un client secret embebido en una app Android como si pudiera mantenerse confidencial: el paquete instalado no es un entorno seguro para secretos compartidos. Preferir el flujo OAuth recomendado para aplicaciones instaladas y un backend confidencial solo cuando la arquitectura lo requiera.

### Contraseña de aplicación e IMAP/SMTP

Una contraseña de aplicación de Google es una credencial separada, disponible solo para cuentas y configuraciones elegibles y generalmente dependiente de la verificación en dos pasos. No equivale a OAuth ni concede acceso a Drive API.

IMAP/SMTP puede servir para capacidades de correo compatibles con esos protocolos, pero no sustituye a Gmail API ni permite acceder a Drive, Calendar o todos los metadatos/acciones de Gmail. Debe quedar claro qué funciones y limitaciones tiene el adaptador. Nunca solicitar la contraseña principal de Google.

### Archivo JSON de credenciales

Un archivo JSON descargado de Google Cloud puede contener identificadores, metadatos o secretos, según el tipo de credencial. Antes de aceptarlo, Fénix debe identificar el tipo de credencial y rechazar formatos no compatibles. No imprimir su contenido en pantalla, logs, analítica, informes ni prompts. No subirlo automáticamente a un backend. Si la integración necesita secretos en nube, solicitar consentimiento separado y explicar el destino y el almacenamiento antes de transferirlos.

## Almacenamiento seguro

- Credenciales locales: cifrado autenticado con clave protegida por Android Keystore; excluirlas de logs, backups no controlados, exportaciones y telemetría.
- Credenciales cloud: guardarlas solo en un almacén de secretos o mecanismo equivalente del backend elegido, nunca en tablas públicas ni en el cuerpo de una tarea.
- Tokens de acceso de corta duración y refresh tokens: cifrado en reposo, acceso restringido por usuario, rotación/revocación y borrado al desconectar.
- No guardar credenciales en preferencias en texto plano, Room sin cifrar, archivos compartidos ni repositorio.
- En la UI, ocultar valores secretos por defecto y no permitir copiarlos accidentalmente desde pantallas de estado.
- La IA recibe un identificador de conexión y las capacidades disponibles, nunca el secreto en sí.
- Enviar secretos a un backend es una acción explícita e independiente de la conexión local; explicar región/servicio y posibles costes.

## Permisos y ejecución

Cada conexión declara capacidades separadas, por ejemplo `gmail.read`, `gmail.send`, `drive.read` y `drive.write`. No solicitar permisos de escritura si la tarea solo necesita lectura. Mostrar una confirmación justo antes de enviar correo, compartir/publicar contenido, modificar o borrar archivos, o realizar cualquier acción externa sensible. Las páginas web, correos y documentos recuperados se tratan como datos no confiables: no pueden conceder permisos ni autorizar acciones.

## Matriz obligatoria por conector

Antes de publicar un adaptador, registrar:

- proveedor, método de autenticación y documentación oficial;
- capacidades de lectura/escritura y scopes exactos;
- si requiere registro de aplicación, revisión/verificación, usuarios de prueba o consentimiento de administrador;
- si exige tarjeta, plan de pago o configuración de facturación;
- cuotas, límites, caducidad y renovación de credenciales;
- datos enviados al dispositivo, a Fénix y al proveedor externo;
- método de revocación y comportamiento ante token expirado;
- pruebas de credencial inválida, permiso insuficiente, revocación, desconexión y ausencia de secretos en logs.

## Criterios de aceptación

- El asistente guía sin pedir contraseñas principales ni mostrar secretos en el chat.
- La importación usa el selector del sistema y valida tipo/tamaño/formato del archivo.
- El secreto se cifra antes de persistir y nunca aparece en logs, informes o prompts.
- Se prueban conexión, permiso insuficiente, token caducado/revocado y desconexión.
- El usuario puede ver y revocar permisos, eliminar credenciales y distinguir conexión local de conexión cloud.
- El asistente no promete que un servicio evitará verificación, tarjeta o límites sin evidencia oficial vigente.
