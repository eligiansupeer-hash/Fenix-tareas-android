# Fénix Tareas — Plan de evolución técnica

Este plan es el contrato de implementación para convertir Fénix Tareas en un motor de automatización verificable. Cada etapa debe compilar y pasar las pruebas automatizadas antes de continuar. Las funciones que dependen del comportamiento del sistema operativo también deben probarse en un dispositivo Android real.

## Principios de diseño

- **Seguridad por defecto:** los datos externos son no confiables; no se ejecutan instrucciones encontradas en páginas; no se exponen secretos en resultados ni logs.
- **Ejecuciones auditables:** cada ejecución tiene identidad, estado, tiempos, resultado y error claramente diferenciados.
- **Persistencia compatible:** toda modificación del esquema Room incluye migración; no se borran datos existentes.
- **Tolerancia a fallos:** reintentar solo fallos transitorios, evitar ejecuciones duplicadas y respetar cancelaciones.
- **Evidencia transparente:** las citas deben apuntar a fuentes realmente recuperadas; una cita no se presenta como verificación automática.
- **Privacidad local:** resultados e historial se conservan localmente salvo que una tarea use un proveedor remoto, en cuyo caso su contenido se envía a ese proveedor.
- **Compatibilidad con proveedores diversos:** no asumir que todos soportan herramientas, JSON estructurado o las mismas capacidades.

## Fase 0 — Línea base y trazabilidad

- Mantener el flujo de CI: compilación de APK, pruebas unitarias y publicación de artefacto.
- Usar una rama de trabajo y revisar el resultado de CI antes de integrar cambios.
- Registrar limitaciones conocidas y procedimientos de prueba manual.

**Criterio de salida:** CI verde y APK descargable; las pruebas de dispositivo quedan identificadas como tales, sin confundirlas con pruebas de compilación.

## Fase 1 — Seguridad de investigación web

- Validar esquema, host y direcciones IP no públicas tanto para la URL inicial como para cada redirección.
- Desactivar redirecciones automáticas y seguirlas manualmente con un límite estricto.
- Rechazar localhost, dominios locales, IPv4/IPv6 privadas, loopback, link-local, multicast y rangos reservados relevantes.
- Limitar tiempo, tamaño de respuesta, cantidad de redirecciones y páginas leídas.
- Mantener explícito que el texto de las páginas es evidencia no confiable, nunca instrucciones.
- Agregar pruebas unitarias de URLs permitidas/rechazadas y de límites de redirección cuando la estructura lo permita.

**Criterio de salida:** ninguna redirección se sigue sin volver a validar el destino; pruebas de seguridad pasan.

## Fase 2 — Motor de ejecución confiable

- Definir estados explícitos: pendiente, en cola, en ejecución, reintentando, completada, error, cancelada y requiere atención.
- Persistir identificador y marcas de tiempo por ejecución, duración, proveedor/modelo, intento y error sanitizado.
- Evitar ejecuciones concurrentes de una misma tarea y resultados tardíos que sobrescriban una cancelación.
- Distinguir error transitorio de error permanente; limitar reintentos y evitar duplicar notificaciones.
- Hacer que cancelar detenga el trabajo activo y la programación futura, cuando Android/WorkManager lo permita.
- Agregar pruebas de transiciones de estado, duplicados, reintentos y cancelación.

**Criterio de salida:** las transiciones son reproducibles y el historial no se reduce al último resultado de una tarea.

## Fase 3 — Historial y almacenamiento de resultados

- Introducir una entidad de ejecución separada de la definición de tarea.
- Conservar el resultado actual de forma compatible durante la migración; no perder tareas previas.
- Añadir filtros/búsqueda por tarea, estado y fecha.
- Permitir exportar un informe y borrar resultados de forma explícita.
- Mantener un límite de retención configurable antes de implementar limpieza automática.

**Criterio de salida:** múltiples ejecuciones de una misma tarea pueden consultarse y compararse.

## Fase 4 — Investigación con evidencia

- Normalizar cada fuente con título, URL canónica, dominio, fecha de consulta, fecha de publicación si existe, fragmento y texto extraído.
- Deduplicar URLs y detectar duplicación evidente de dominios.
- Marcar fuentes que no se pudieron leer; no tratarlas como si se hubiera leído el artículo completo.
- Numerar fuentes de forma determinista y preservar el vínculo entre afirmaciones e insumos disponibles.
- Separar hechos respaldados, inferencias y preguntas abiertas; identificar contradicciones.
- Mejorar el buscador por etapas; no prometer cobertura académica exhaustiva ni verificación automática sin un mecanismo real.

**Criterio de salida:** cada referencia presentada corresponde a una fuente recuperada y los fallos de lectura quedan visibles.

## Fase 5 — Comparación de informes periódicos

- Guardar por separado el resultado de cada ejecución.
- Comparar la ejecución actual con la anterior usando evidencia nueva, fechas y URLs, no solo el texto generado.
- Mostrar novedades, cambios, elementos estables y puntos no confirmados.
- No describir como actual un dato histórico sin evidencia reciente.

**Criterio de salida:** la comparación identifica explícitamente su ventana temporal y las fuentes nuevas.

## Fase 6 — Selección inteligente de proveedores

- Mantener un catálogo de capacidades por proveedor/modelo: clave requerida, formato de API, herramientas, JSON estructurado y límites conocidos.
- Registrar latencia y errores recientes sin almacenar claves.
- Seleccionar proveedor según capacidad requerida y disponibilidad, con opción manual.
- No confundir proveedor configurado con proveedor operativo; ofrecer prueba de conexión.
- Respetar proveedores sin clave y servidores Ollama propios sin prometer disponibilidad de terceros.

**Criterio de salida:** la selección es explicable, configurable y probada ante proveedores caídos o respuestas incompatibles.

## Fase 7 — UX de operación

- Panel de tareas activas, próximas ejecuciones, resultados recientes y fallos.
- Notificaciones accionables y avisos de fallos persistentes.
- Mostrar próxima ejecución estimada y explicar límites de ejecución en segundo plano de Android.
- Biblioteca local de informes con búsqueda, exportación y acceso sin conexión.
- Formularios y plantillas para investigación, comparación, resumen y seguimiento.

**Criterio de salida:** el usuario puede saber qué está ocurriendo, recuperar un resultado y resolver un error sin inspeccionar logs técnicos.

## Fase 8 — Flujos encadenados con permisos

- Representar cada paso como una unidad con entrada, salida, condición de continuación y política de reintentos.
- Validar datos entre pasos y poner límites a duración, consumo y tamaño de salida.
- Mantener aprobación explícita antes de acciones externas sensibles.
- No ejecutar comandos ni acciones extraídas del contenido web o de la salida del modelo sin autorización.

**Criterio de salida:** los flujos son cancelables, auditables y no amplían permisos de forma implícita.

## Fase 9 — Validación en dispositivo

Lista mínima manual:
- Crear, ejecutar, cancelar, reprogramar y eliminar tareas.
- Desconectar la red, simular cuota agotada y recuperar la conexión.
- Reiniciar el teléfono y revisar la programación pendiente.
- Probar permisos de notificación, ahorro de batería y cambios de zona horaria.
- Verificar que no aparezcan claves en pantalla, resultados o logs.
- Comprobar investigación con redirecciones, páginas bloqueadas y contenido que incluya instrucciones maliciosas.
- Comprobar migración desde una instalación con base de datos anterior.

## Orden de ejecución

1. Seguridad web.
2. Modelo de ejecución e historial.
3. Pruebas unitarias y comportamiento de cancelación.
4. Evidencia de investigación.
5. Comparación temporal.
6. Selección de proveedores.
7. Panel, notificaciones y biblioteca.
8. Flujos encadenados.
9. Validación integral en dispositivo.

No se considera una fase terminada solo por compilar: cada fase debe satisfacer sus criterios de salida y documentar cualquier limitación no resuelta.
