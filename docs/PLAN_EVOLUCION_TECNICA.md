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


## Estado de implementación (23 de octubre de 2026)

Implementado en la rama `feature/reliable-task-engine`, pendiente de la validación final de CI y pruebas en dispositivo:

- Seguridad web: redirecciones manuales limitadas, validación repetida de URL y comprobaciones DNS defensivas contra destinos locales/privados; pruebas unitarias de rangos especiales.
- Motor de ejecución: historial por ejecución, actualizaciones Room transaccionales, estados persistentes, reintentos, cancelación y notificaciones con acción de reintento.
- Historial local: búsqueda, filtros por estado y fecha, compartir, borrado individual/global y retención configurable (sin límite, 30 o 90 días), incluyendo la eliminación de la copia del último resultado guardada en la tarea.
- Evidencia: fuentes numeradas, URL final validada, fecha de publicación cuando la página la expone y marca de consulta; deduplicación por URL normalizada, eliminación de parámetros de seguimiento para comparar duplicados y preferencia por dominios diferentes. Las limitaciones de lectura permanecen visibles.
- Proveedores: adaptadores declarados por protocolo, prueba de conexión explícita, registro local de éxito/latencia y enfriamiento temporal después de fallos repetidos. No se afirma soporte de tool-calling o JSON estructurado.
- Flujos encadenados: hasta diez pasos, transferencia del resultado previo al paso siguiente, comparación explícita con el informe previo en tareas recurrentes, límites de longitud de instrucciones/pasos/respuestas y checkpoints persistidos para reanudar un reintento.
- Límites de respuesta de proveedores: se rechazan respuestas de texto mayores a 100.000 caracteres y respuestas JSON mayores a 500.000 caracteres para reducir riesgo de consumo excesivo de memoria.

Pendiente antes de declarar la entrega completa:

- Validar el commit más reciente con CI verde y revisar cualquier fallo hasta corregirlo.
- Instalar y probar en Android físico: migraciones Room desde versiones anteriores, notificaciones, acción de reintento, cancelación real, modo ahorro de batería, reinicio y cambio de zona horaria.
- Probar de forma controlada redirecciones web inseguras y contenido que intente inyectar instrucciones.
- Revisar límites de memoria, tamaño de informes y comportamiento ante interrupciones durante cada paso del flujo.
- La ejecución de flujos por ahora encadena investigación y generación de texto; no ejecuta acciones externas sensibles ni comandos del sistema.


## Fase 10 — Protocolo Deep Research iterativo

Objetivo: pasar de una búsqueda limitada con síntesis a un proceso de investigación controlado, trazable y evaluable.

- Convertir la pregunta en un plan con subpreguntas, alcance, criterios de inclusión/exclusión, actualidad necesaria y presupuesto de investigación.
- Ejecutar rondas de búsqueda adaptativas: buscar, evaluar cobertura, detectar vacíos y contradicciones, y volver a buscar cuando exista una pregunta pendiente relevante.
- Separar resultados de búsqueda, páginas intentadas, páginas leídas íntegramente, páginas parcialmente leídas y errores de extracción.
- Incorporar adaptadores de fuentes especializadas cuando sean pertinentes: documentación técnica, repositorios de código, Crossref, OpenAlex, arXiv, PubMed/Europe PMC y Semantic Scholar, respetando sus condiciones y límites.
- Evaluar procedencia, fecha, autoría, tipo de fuente, independencia aparente, metodología y condición de fuente primaria o secundaria. No tratar métricas de popularidad como prueba de verdad.
- Crear un registro de evidencia con identificadores estables. Vincular cada afirmación sustantiva del informe con una o más evidencias recuperadas; etiquetar inferencias, incertidumbres y afirmaciones no corroboradas.
- Detectar contradicciones y solicitar búsqueda adicional cuando la evidencia sea insuficiente. No declarar verificación automática si solo se pidió al mismo modelo que revise su respuesta.
- Producir informe con alcance, fecha de corte, método, hallazgos, fuentes, desacuerdos, limitaciones, vacíos y preguntas abiertas.
- Presupuestos configurables por perfil (rápido, estándar, profundo y extendido): consultas, páginas, tiempo, tokens/costo cuando se conozcan, bytes descargados y rondas máximas. Los valores son límites de ejecución, no garantías de cobertura.
- Cachear resultados reutilizables con fecha y política de caducidad; las tareas periódicas deben buscar cambios nuevos y comparar evidencias, no solo comparar prosa generada.
- Mantener protección contra prompt injection: el contenido de fuentes es dato no confiable; nunca concede permisos ni autoriza herramientas por sí mismo.

Criterios de salida:
- Pruebas con preguntas de referencia y fuentes esperadas; medir cobertura de subpreguntas, exactitud de citas, afirmaciones sin soporte, diversidad y actualidad de fuentes, contradicciones detectadas, fallos de extracción, tiempo y consumo.
- Cada cita debe resolver a una fuente realmente recuperada y a un fragmento o registro que respalde la afirmación.
- El informe declara qué se examinó y qué no; no utiliza la etiqueta Deep Research como promesa de exhaustividad si no se satisfacen criterios definidos.

## Fase 11 — Catálogo de conectores personales y autorización granular

Diseñar una sección de Conectores en la aplicación, similar al patrón de un catálogo de integraciones: catálogo, descripción, estado conectado/desconectado, capacidades, permisos concedidos, prueba de conexión, revocación y registro de actividad.

- Separar conectores de solo lectura de conectores con capacidad de escritura/ejecución.
- Solicitar únicamente permisos mínimos, preferentemente OAuth o autorización oficial del proveedor. Permitir seleccionar repositorios, espacios de trabajo y recursos cuando el proveedor lo soporte.
- Mostrar antes de autorizar qué datos podrá leer la IA y qué acciones podrá realizar. Solicitar confirmación explícita para operaciones sensibles, destructivas, financieras, publicaciones externas o cambios de permisos.
- Guardar tokens y secretos cifrados; nunca incluirlos en prompts, logs, informes ni almacenamiento público. Preferir un almacén de secretos de backend cuando el conector deba ejecutarse en la nube. Implementar renovación, expiración, revocación y manejo de errores de autorización.
- No asumir que tener un login gratuito significa que una API es gratuita, ilimitada o disponible. Cada conector tendrá matriz de autenticación, coste, cuota, alcance, límites y política de datos.
- Conectores potenciales iniciales: GitHub; Supabase; Cloudflare; Context7; Jules cuando exista un método de integración documentado y autorizado; proveedores de almacenamiento/documentos; calendario/correo/tareas; gestores de proyectos; comunicación; fuentes académicas; RSS y servicios públicos.
- Jules debe tratarse como un agente externo de programación, no como un conector genérico de ejecución. Su integración requiere confirmar API/CLI oficial, autenticación, límites, retorno de estado y aprobación de cambios antes de incorporarla.
- Context7 se clasifica principalmente como recuperación de documentación técnica, no como conector para modificar sistemas.
- Las APIs académicas abiertas sin cuenta, como Crossref, y servicios con claves gratuitas, como OpenAlex, pueden incorporarse como fuentes de investigación; confirmar las condiciones y cuotas actuales en la implementación.

Criterios de salida:
- Matriz pública por conector: lectura/escritura, autenticación, scopes, necesidad de tarjeta, cuotas gratuitas verificadas, datos transmitidos, límites y permisos.
- Pruebas de autorización insuficiente, token expirado/revocado, fallo de proveedor, revocación desde la interfaz y ausencia de secretos en logs.
- Ningún conector se activa ni recibe permisos por el solo hecho de que la IA lo recomiende.

## Fase 12 — Orquestación opcional de tareas en la nube

Objetivo: permitir que una tarea siga avanzando con la aplicación cerrada y que los resultados se sincronicen al recuperar conectividad, sin convertir la nube en requisito para Fénix Lite.

Arquitectura propuesta:
1. La app valida la tarea, muestra el plan, selecciona conectores y solicita autorización para cualquier acción que lo requiera.
2. Si el usuario conecta un backend propio (por ejemplo Supabase o Cloudflare), Fénix despliega/configura solo los componentes necesarios mediante un flujo explícito y revisable; nunca crea recursos, consume cuotas o habilita facturación sin autorización.
3. Un registro de trabajos guarda estado, programación, siguiente intento, presupuesto, permisos, checkpoints y claves de idempotencia. Los secretos no se guardan en el contenido del trabajo.
4. Un scheduler/cron despierta el despachador. El despachador reclama trabajos con bloqueo/lease, comprueba autorización y presupuesto, y los entrega a un worker.
5. El worker ejecuta pasos acotados, persiste checkpoints, aplica backoff y límites, y puede reanudar tras fallos. Webhooks se validan criptográficamente cuando existan.
6. Los resultados se guardan de forma segura y se sincronizan con la app al reconectarse. La app muestra última sincronización, estado, errores y consumo.
7. Un mecanismo de cancelación y revocación detiene futuros pasos cuando sea posible y bloquea nuevas llamadas al conector. Los pasos ya enviados a un servicio externo pueden no ser reversibles.

- Supabase puede servir como base de datos, autenticación, almacenamiento y Edge Functions dentro de las cuotas del plan elegido; sus proyectos gratuitos pueden pausarse por inactividad.
- Cloudflare Workers/Cron puede servir como scheduler y ejecución ligera dentro de sus límites de CPU, memoria, subrequests y cron; no es una máquina virtual persistente ni sirve para cualquier trabajo largo.
- Usar colas o almacenamiento duradero cuando el trabajo requiera reintentos fiables. Un cron no debe ser la cola ni ejecutar directamente una investigación extensa en una única invocación.
- Separar plano de control (estado, permisos, cron) de plano de ejecución (investigación/modelo/conectores). Permitir elegir backend propio y ofrecer modo local cuando no haya backend.
- Las claves de modelos y tokens de conectores deben permanecer en secretos del backend cuando el trabajo se ejecute en nube. Nunca pedir al usuario que pegue secretos en instrucciones del modelo.
- El plan debe ser utilizable con cuentas gratuitas cuando el proveedor lo permita, sin requerir tarjeta para el flujo gratuito; documentar cualquier excepción. Las cuotas gratuitas pueden cambiar, agotarse o pausarse.
- Respetar restricciones de conectividad móvil: la app no necesita permanecer abierta; sí necesita conexión para sincronizar, y el backend necesita acceso a Internet para consultar servicios externos. Si el teléfono no tiene datos, el trabajo cloud puede continuar, pero no puede entregar actualizaciones al dispositivo hasta que vuelva a conectarse.
- Diseñar límites por trabajo, cuotas diarias, timeout, número de pasos, tamaño de resultado, retención, protección contra loops y alertas de presupuesto. No habilitar gasto de pago automáticamente.

Criterios de salida:
- Demostrar ejecución con la app cerrada, reintento tras error, idempotencia, recuperación de checkpoint, cancelación, token revocado, sincronización tardía y agotamiento de cuota.
- Documentar por backend qué queda realmente cubierto por el nivel gratuito y qué requeriría un plan pago.
- Fénix Lite debe seguir funcionando en modo local/sin backend; las funciones cloud se muestran como opcionales y no se anuncian como disponibles hasta completar configuración y prueba.

## Fase 13 — Acciones reales mediante conectores y control humano

- Cada herramienta publica un contrato tipado con esquema de entrada/salida, capacidades, permisos requeridos, límites, timeout y política de reintento.
- Clasificar operaciones: lectura; escritura reversible; acción externa sensible; acción destructiva/financiera. Aplicar confirmación acorde al riesgo.
- Separar la planificación del modelo de la ejecución del conector: el modelo propone una llamada, el motor valida esquema, permiso, alcance, presupuesto y política antes de ejecutarla.
- Usar claves de idempotencia para evitar duplicar creaciones o envíos durante reintentos. Registrar actor, conector, acción, parámetros no secretos, resultado y marca de tiempo.
- Aplicar lista permitida de herramientas, aislamiento por usuario, protección CSRF/SSRF, validación de webhook, límites de tasa y auditoría. Nunca ejecutar código arbitrario recibido de una página web.
- Antes de cambios irreversibles, presentar resumen de la acción y recurso afectado; permitir cancelar y revocar acceso.
- Los conectores deben tener pruebas de contrato y sandbox/mock para desarrollo; las pruebas reales requieren una cuenta de prueba y autorización explícita.

## Orden de implementación ampliado

1. Completar y validar la rama de estabilidad actual.
2. Deep Research iterativo con evidencia y presupuesto, primero usando fuentes públicas sin autenticación.
3. Catálogo de conectores de solo lectura: documentación técnica y fuentes académicas.
4. Conectores de cuentas personales con OAuth/permisos granulares y pruebas de seguridad.
5. Backend cloud opcional con registro de trabajos, cola, cron, checkpoints y sincronización.
6. Conectores de escritura y automatización con confirmación humana, idempotencia y auditoría.
7. Evaluación comparativa con tareas de referencia y pruebas de operación en dispositivo.

Las fases anteriores son objetivos de arquitectura y no deben interpretarse como funciones ya implementadas. La disponibilidad, coste, necesidad de tarjeta, cuotas y API oficial de cada servicio deben verificarse nuevamente antes de implementar su adaptador.
