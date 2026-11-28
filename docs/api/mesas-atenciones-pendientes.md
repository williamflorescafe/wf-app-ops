# Mesas y atenciones en OPS

Actualizado: 26 de noviembre de 2026. Las aclaraciones del 25 de noviembre reemplazan la propuesta anterior de guardar borradores en backend y numerarlos antes del envío.

## Contratos recibidos e integrados

### Envío completo o selección dentro del borrador (27 de noviembre)

El botón inferior muestra «Enviar» y el avión a su derecha. Su primer toque abre una confirmación compacta,
sin bolsa ni lista de productos: «¿Deseas enviar todo lo que está en borrador?», con «Seleccionar» y «Enviar todo» en una fila.
Enviar todo conserva las cantidades completas. Seleccionar cierra el modal y habilita la selección en la tarjeta del borrador.
Cada fila tiene un checkbox circular visible, check al marcar y fondo verde; toda la fila es pulsable y expone semántica de checkbox.
Durante la selección no hay controles de cantidad ni edición de notas que interfieran con el toque de fila.
Los CTA inferiores son Cancelar y Enviar; Enviar se habilita al seleccionar entre 1 y 60 líneas.
Cada producto seleccionado se envía con toda su cantidad actual y los demás quedan en caché.
Cancelar o volver sale del modo sin modificar productos ni llamar al API. Las notas se conservan y los valores de presentación vacíos/`null` no se muestran.
Se retiró `OrderSendSelection`, el modal anterior que combinaba productos y cantidades.
El contrato de envío y su captura persistente mantienen los mismos UUID, validación y recuperación de reintentos.

### Separación de funciones: OPS para el mozo (27 de noviembre)

Esta corrección reemplaza la conexión automática de preparación descrita históricamente más abajo.
El refresco de Inicio/Comandas solo consulta órdenes: se retiraron del repositorio de OPS el listado de estaciones,
las unidades por estación, su sincronización automática y las cachés/reintentos de esas consultas.
El mozo recibe preparación como información desde `orders/{order}/delivery-units`, al consultar el detalle.
No solicita permisos de estación ni produce eventos de iniciar/listo/reabrir; esos eventos corresponden a cocina/barra.

El menú y la etiqueta filtran siempre iniciar, listo y reabrir, incluso si una respuesta trae todas las capabilities en true.
La etiqueta de preparación es informativa; el avance directo disponible es Listo → Servido cuando el servidor permite entregar.
También se retira del flujo de usuario la simulación debug del avance de cocina.
Se conservan entrega, sus correcciones, confirmaciones legacy, anulación y cierre según sus capacidades y permisos.
El repositorio rechaza nuevas escrituras de cocina. Si existe una captura antigua de cocina pendiente,
la acción de recuperación solo vuelve a leer el detalle/entrega para reconciliarla: nunca repite un POST de preparación.

### Corrección de carga y edición local del 27 de noviembre

Se confirmó un HTTP 403 real de `preparation-stations` que estaba propagándose como fallo de toda la lista.
La observación de la caché ahora comienza en paralelo con el refresco remoto y continúa después de un fallo de red.
La consulta complementaria de preparación no invalida comandas/ocupación ya verificadas; se muestra un aviso sin ocultar tarjetas.
Si falla la consulta principal de órdenes, las tarjetas guardadas permanecen visibles con aviso y la ocupación deja de darse por verificada.
Un 401 continúa invalidando el acceso: no se usa esta recuperación para eludir la autenticación.
Se conservan los fallos de seguimiento durante su espera y se limita su reintento durante 60 segundos por ámbito autorizado.

Agregar productos, ajustar cantidades y editar notas locales ya no activa el bloqueo visual global ni deshabilita todas las tarjetas.
Las operaciones se serializan con un mutex y toman el estado más reciente al ejecutarse, conservando pulsaciones consecutivas.
Las escrituras remotas mantienen su bloqueo y los borradores siguen persistiendo antes de mostrarse como guardados.

### Escrituras de preparación, entrega y cierre: integradas el 27 de noviembre de 2026

| Método y sufijo de /v1/ops/locations/{location}/orders/{order} | Acción |
| --- | --- |
| POST /preparation/start | Iniciar preparación. |
| POST /preparation/ready | Marcar listo para recoger. |
| POST /preparation/reopen | Corregir un registro de preparación. |
| POST /delivery/deliver | Marcar servido. |
| POST /delivery/undo | Corregir una entrega registrada. |
| POST /delivery/confirm-legacy | Confirmar una entrega anterior observada. |
| POST /delivery/revoke-legacy | Corregir una confirmación de entrega anterior. |
| GET /closure-readiness | Comprobar si la atención se puede finalizar y leer sus bloqueos. |
| POST /close | Finalizar la atención y liberar su reserva de mesa. No procesa pagos ni comprobantes. |

Los productos con seguimiento real ofrecen únicamente acciones habilitadas por sus `capabilities` y referencias disponibles.
El menú «Acciones» permite elegirlas; la etiqueta mantiene el avance directo y el botón de servido cuando corresponde.
La versión enviada es la revisión de cada unidad, no la de la comanda. Una selección admite entre 1 y 100 unidades.
Las correcciones exigen motivo de 3 a 1000 caracteres, confirmación explícita del error y los IDs reales del evento corregido.
Reabrir usa `last_preparation_event_id`; deshacer entrega, `last_delivery_event_id`; revocar legacy,
`legacy_confirmation.confirmation_operation_id`. No se generan IDs de eventos inexistentes.
Confirmar legacy requiere motivo y `confirm_observed_delivery=true`; su versión nullable se conserva como JSON null.

El UUID y el cuerpo de cada operación se guardan antes del POST. Un resultado incierto deja «Confirmar operación pendiente»
o «Confirmar cierre pendiente»; repetir reutiliza exactamente la captura, también después de reiniciar.
Se valida el recibo, se refrescan detalle y seguimiento de entrega, y se actualiza la caché de notificaciones por los estados reales.
No se deduce el éxito de `changed` ni se interpretan estructuras no especificadas dentro de `units`.
Un rechazo definitivo permite corregir; un 409 refresca el estado y exige otra decisión. Nunca se cambia silenciosamente la versión de una captura.
Los permisos de escritura adicionales los decide el servidor; las capabilities son un control de disponibilidad, no una autorización alternativa.

El ícono de cierre consulta readiness incluso si el frontend cree que aún hay pendientes. Muestra los bloqueos del servidor;
solo `can_close=true` sin bloqueos habilita la confirmación, que requiere marcar «Confirmo que la atención terminó».
El cierre envía `client_close_id`, la `order_version` de readiness y `confirm_attention_finished=true`.
No declara libre la mesa hasta confirmar el resultado remoto. Los borradores adicionales permanecen locales.
Esta sección reemplaza los pendientes históricos de iniciar/listo/servido/correcciones/cierre indicados más abajo.

### Consultas de preparación y entrega: integradas el 27 de noviembre de 2026

| Consulta | Uso en OPS |
| --- | --- |
| GET /v1/ops/locations/{location}/preparation-stations | Estaciones reales del local con nombre e indicador activo. |
| GET /v1/ops/locations/{location}/preparation-stations/{station}/units | Unidades de cada estación y avance real de preparación y entrega. |
| GET /v1/ops/locations/{location}/orders/{order}/delivery-units | Seguimiento de entrega de las unidades de una comanda al abrir/refrescar su detalle. |

El refresco existente del listado consulta las estaciones y sus unidades, sin abrir el detalle de cada tarjeta.
Las estaciones se reutilizan en memoria durante 60 segundos, con clave de negocio, empleado, sesión, membresía,
versión de acceso y local. Los estados siguen el refresco de comandas y comparten su ventana de cinco segundos.
El detalle consulta las unidades de entrega con `status=all`; preparación también usa `all` para conservar servidas y reabiertas.
Los métodos de lectura de preparación admiten `work`, `ready` y `all`. Entrega admite `pending`, `delivered` y `all`.
Las consultas de unidades usan límite 100 y recorren `next_cursor` hasta agotarlo, codificando el cursor opaco;
se rechazan ciclos y resultados duplicados. No se presenta la primera página como una lista completa.
La lista de estaciones sigue `next_cursor` si el servidor lo devuelve; su petición inicial no inventa filtros.

Los datos de seguimiento se guardan en caché privada por cuenta y negocio, con IDs de unidad/comanda/ronda,
estación, estados, versiones, horas, capacidades y eventos originales. Una versión de unidad más vieja no sustituye una nueva.
`fulfillment.version` y `fulfillment_version` se conservan: se admite la versión exterior nula y se rechazan
valores no nulos contradictorios. No se compara esa revisión con la versión de la comanda.
Los errores HTTP no se convierten en listas vacías y se mantiene el manejo de 401 y acceso del repositorio.
Los permisos adicionales de estos endpoints los decide backend; no se inventan nombres de permisos de preparación.

La proyección usa `queued` → Enviado, `in_progress` → En preparación, `ready` → Listo y
`delivery_status=delivered` → Servido. Un anulado conserva prioridad. Los estados desconocidos no generan un listo/servido ficticio.
Los resúmenes `all_*` no pintan comandas por sí solos: una lista vacía puede traer esos booleanos en true.
Cada unidad conocida del detalle se relaciona por ID de comanda, ronda y unidad. Una línea con estados mezclados
se divide visualmente conservando cantidad total, precio original e IDs; la anulación selecciona la misma proyección.
Estos estados alimentan Inicio, Comandas, detalle y la caché de notificaciones existentes.
Las unidades de órdenes cuyo detalle todavía no está cargado se conservan, pero no se fabrican líneas con precios o autores ausentes:
la proyección de productos y avisos requiere el detalle conocido. La consulta de estaciones no reemplaza ese contrato.

Los datos remotos tienen prioridad sobre la simulación debug. Cuando una comanda ya tiene seguimiento real,
sus etiquetas no avanzan estados de prueba. No se implementa una pantalla nueva de cocina ni escrituras inventadas.
Faltan los contratos POST de iniciar, listo, reabrir, servir y deshacer entrega: `capabilities` describe qué se podría ejecutar,
pero las tres rutas integradas aquí solo leen. Los envíos, precios, numeración, ocupación y cierre siguen siendo autoritativos del API.

### Anulación de productos: integrado el 27 de noviembre de 2026

`POST /v1/ops/locations/{location}/orders/{order}/cancel-items` requiere `pos.orders.cancel`.
La selección por pulsación larga permite marcar líneas enviadas, en preparación o listas; las servidas y anuladas no se seleccionan.
Se envían los IDs reales de todas las unidades de cada línea marcada, el motivo obligatorio de 3 a 255 caracteres,
la versión vista al abrir la confirmación y un UUID de operación. Por ahora no hay selector de cantidad para anular solo parte de una línea agrupada.

La decisión completa se persiste antes de enviarla. Ante timeout, red o respuesta incierta aparece «Confirmar anulación pendiente»;
el reintento conserva exactamente UUID, motivo, versión y unidades, incluso tras reiniciar.
No se permiten otros envíos ni anulación completa mientras esa decisión está pendiente.
Se valida el recibo y se consulta el detalle existente para actualizar productos, precios, historial y ocupación con datos del servidor.
No se interpretan campos internos de `units`, porque el ejemplo recibido contiene objetos vacíos.
Un conflicto de versión no se reintenta con otra versión automáticamente: se actualiza el detalle y se exige una nueva selección/decisión.
Si el servidor anula la atención al no quedar unidades vigentes, su detalle libera la ocupación remota; el borrador adicional sigue local.
Esta sección reemplaza los pendientes CU-10 y CU-11 y la restricción anterior de no habilitar anulación parcial.

Todas las rutas llevan Bearer Ops y X-Business-Id. El local procede del contexto autorizado. Los IDs, nombres y precios de las muestras no se usan como datos fijos.

| Método y ruta | Uso | Permisos |
| --- | --- | --- |
| GET /v1/ops/locations/{location}/tables | Distribución física, identificación y capacidad. | pos.tables.view |
| GET /v1/ops/locations/{location}/orders | Resúmenes abiertos sin rondas ni paginación. table.id identifica las mesas ocupadas. | pos.orders.view |
| POST /v1/ops/locations/{location}/orders | Primer envío y creación oficial. | pos.orders.create, pos.orders.send_to_kitchen |
| GET /v1/ops/locations/{location}/orders/{order} | Detalle completo, también de una comanda anulada. | pos.orders.view |
| POST /v1/ops/locations/{location}/orders/{order}/rounds | Agregar solo productos nuevos a una comanda abierta. | pos.orders.update, pos.orders.send_to_kitchen |
| POST /v1/ops/locations/{location}/orders/{order}/cancel | Anulación completa, libera la mesa y conserva historial. | pos.orders.cancel |

order es el UUID público, comparado por igualdad exacta. number es el correlativo visible. Se conservan referencias de producto, variante, grupo, opción, ronda, línea y unidad.

## Borrador y primer envío

- El borrador se guarda persistentemente en el dispositivo, separado por negocio y colaborador. Editarlo, retirar productos o vaciarlo no escribe una comanda en el API.
- Su client_order_id es un UUID local estable. No tiene número oficial hasta confirmar el primer envío.
- `Borrador #1`, `Borrador #2`, etc. son identificadores locales de presentación, guardados por negocio y colaborador. Se asignan al primer producto del borrador, en orden de creación; no se calculan por la posición actual de la lista. Enviar o vaciar uno no renumera los restantes ni reutiliza su número durante ese ciclo. Cuando no queda ningún borrador independiente con productos, el siguiente ciclo comienza en #1. Los borradores adicionales dentro de una comanda oficial no consumen esta numeración.
- Esta secuencia local no modifica `number` ni `sequence`: el número oficial de comanda y el número de cada ronda confirmada siempre proceden de la API. El contador y la asignación local se guardan de forma atómica junto con las visitas; la caché anterior se migra conservando su contenido.
- Mientras no exista una mesa seleccionada, las tarjetas muestran el ícono de mesa y `sin asignar`. El contador interno de visitas nunca se presenta ni se busca como si fuera el código de una mesa.
- Se puede preparar sin mesa; table_id es obligatorio al enviar. Carta y mesa deben corresponder al mismo local.
- La primera respuesta aceptada fija ID, número, fecha operativa, apertura, iniciador, importes y versión oficiales.
- El servidor calcula precios. expected_unit_total_minor incluye modificadores. Ante precios cambiados se conserva el borrador para revisión.
- Cada captura se acepta o rechaza completa. Registrar el envío no acredita que cocina lo recibió.

## Casos de uso acordados

| Caso | Operación | Resultado |
| --- | --- | --- |
| CU-01 · Primer producto | Guardado local. | Borrador sin número oficial. La indicación local de atención no reserva la mesa en otros dispositivos. |
| CU-02 · Retirada parcial | Edición local. | Disminuye o retira la línea. No genera una anulación visible. |
| CU-03 · Último producto retirado sin envíos | Edición local; sin cancelar por API. | Desaparece de Inicio y Comandas y se libera la indicación local. No hay número oficial que cancelar. |
| CU-04 · Borrador adicional retirado | Edición local. | Mantiene enviados, comanda y ocupación remota. |
| CU-05 · Primer envío | POST de creación. | El servidor asigna correlativo y ocupa la mesa. Se descuenta del borrador solo lo confirmado. |
| CU-06 · Envío parcial del borrador | Selección de líneas y cantidades; creación o ronda adicional. | Se envía la selección y el resto permanece local. De tres cafés se pueden enviar dos y conservar uno. |
| CU-07 · Nuevos productos | POST de ronda. | Misma comanda, número, apertura e iniciador. La ronda conserva su propio autor y fecha. |
| CU-08 · Anular toda la comanda | POST de cancelación con motivo y expected_version. | Libera la mesa al confirmar y conserva historial. Un borrador adicional se conserva localmente y puede continuarse en una nueva atención, eligiendo mesa antes de enviar. |
| CU-09 · Conflicto de versión al anular | Refrescar detalle; no repetir automáticamente la escritura. | El colaborador revisa los cambios antes de decidir nuevamente. |
| CU-10 · Anular productos enviados parcialmente | Contrato pendiente. | Deben permanecer visibles con trazabilidad. No se simula editando el borrador ni anulando toda la comanda. |
| CU-11 · Todos los enviados anulados | Pendiente del contrato de anulación parcial y su regla de cierre. | No liberar una comanda vigente por deducción del frontend. La anulación completa sí tiene una operación explícita que libera. |
| CU-12 · Nueva atención tras liberar | Nuevo borrador y posterior primer envío. | Otro client_order_id; el servidor asigna nueva identidad y numeración al aceptar. |

## Reintentos y concurrencia

La captura se guarda antes del POST: ruta, cuerpo, client_round_id y líneas/cantidades seleccionadas. Un error de red, timeout, respuesta ilegible o fallo de persistencia conserva esa captura. El reintento reutiliza su identidad y contenido incluso después de reiniciar; no genera otra ronda. La repetición aceptada puede responder 200 en lugar de 201.

Mientras el resultado es incierto se bloquea la edición de la captura. La confirmación descuenta solo lo capturado y conserva el resto en una escritura local. Si el detalle ya contiene ese client_round_id, también permite reconciliar el envío. Un rechazo definitivo permite corregir el borrador. pos.client_id_reused conserva la captura para evitar reenviar contenido distinto bajo una identidad usada.

La anulación requiere motivo de 3 a 255 caracteres y la versión que vio el colaborador. pos.stale_version actualiza el detalle y exige una nueva decisión; nunca se sustituye la versión para reintentar automáticamente. Repetir una anulación registrada responde 200 sin otra escritura, según el contrato.

El servidor resuelve conflictos de mesa entre equipos. Si otra tablet confirma primero, el rechazo conserva el borrador local. Un 401 borra la sesión y devuelve al ingreso. La caché se filtra por identidad, negocio, locales y permisos. Una respuesta de una sesión anterior no se aplica a otra.

## Diseño conservado

- Envíos enumerados por sequence del servidor. El bloque Borrador no adelanta un número de envío aún no confirmado.
- Por petición del usuario se recuperaron los contenedores individuales por envío, con bordes redondeados, fondo de encabezado y separación entre tarjetas. Se mantiene el ícono de comentario junto a #1, #2, etc., sin las etiquetas globales de estado en esos encabezados. Los estados de cada producto se mantienen.
- La pulsación larga vuelve a seleccionar el envío con respuesta háptica, también cuando sus productos ya están servidos. Esta selección visual no depende de habilitar escrituras de cocina o anulación parcial; mientras falta ese contrato, la acción disponible es quitar la selección. El toque corto de la etiqueta sigue controlando el avance local de prueba.
- El comentario de una ronda ya enviada se consulta desde su encabezado. El comentario del próximo envío se agrega o edita desde Borrador y sigue viajando como `round.note`. No hay un comentario global en la barra superior ni edición de rondas confirmadas sin contrato de API.
- La nota se integra en un campo del encabezado entre el título y la hora/chevrón: ícono y “Agregar nota” en el borrador vacío, o vista previa de hasta dos líneas cuando existe. Una ronda enviada sin comentario muestra “Sin nota”; su texto completo se consulta desde ese campo. Se mantienen los colores vigentes. Las filas muestran producto, cantidad, precio, estado y su nota, sin encabezados repetidos de columnas. Al comprimir el contenedor se muestra a la derecha la suma de unidades, con singular y plural correctos.
- El botón de cocina permite seleccionar productos y cantidades. Las líneas enviadas no tienen edición local.
- Las rondas conservan notas, autores y horas. opened_by no se reemplaza por el sent_by de una ronda posterior.
- Cada unidad conserva ID y estado. Las cantidades en estados distintos se muestran por separado.
- Borde compartido de 4 dp. Regla corregida el 26 de noviembre: borrador puro gris; enviados, en preparación o listos naranja; verde únicamente cuando hay productos enviados vigentes y todos están servidos. Un borrador adicional no quita el verde a lo ya servido. Las unidades anuladas no cuentan como pendientes y, por sí solas, no habilitan verde. Cierre y anulación tienen prioridad sobre el avance operativo; sus nuevos colores están en revisión (se propuso azul pizarra para cierre y el highlight frambuesa de Web Store para anulación).
- Una comanda vacía no se muestra. Un resumen remoto con unidades sí aparece sin inventar productos ni mostrar un chevrón vacío.
- Mesas conserva identificación/capacidad del API, blanco con borde naranja para ocupadas y verde suave con más circular para libres. Muestra iniciador y apertura confirmados. El borrador local aporta solo una indicación privada de este equipo.
- Hora de presentación actual: America/Lima. Se conservan los timestamps y la fecha operativa del servidor.

## Actualización y caché

La distribución física conserva su caché. La ocupación remota se obtiene del listado abierto del local. El detalle se consulta al abrir la comanda y cada 30 segundos con la pantalla activa. El listado se actualiza al volver, manualmente y cada 30 segundos en primer plano. No hay un GET de detalle por tarjeta.

Consultas automáticas cercanas del mismo local comparten cinco segundos de vigencia. Un POST confirmado actualiza la proyección local inmediatamente. Los GET conservan borradores y una versión vieja no reemplaza una reciente. Un fallo al consultar ocupación muestra error; no declara todas las mesas libres.

El resumen no incluye estados unitarios ni hora de última transición. Los filtros operativos y el verde de servido requieren detalle vigente conocido; cambiar la versión invalida el anterior. Para actualizar todas las tarjetas por estados sin consultas por comanda falta enriquecer el resumen o recibir eventos con revisión/última actividad. No se inventan horas de preparación, listo o entrega.

## Cambio temporal de estado desde la etiqueta (pruebas)

Por petición del usuario, la versión debug recupera el avance manual al tocar la etiqueta del producto: Enviado → En preparación → Listo → Servido. El botón explícito de marcar servido también sigue disponible. Tocar el nombre del producto no avanza el estado en este modo.

Los avances se guardan como una proyección local independiente de las líneas del API, por negocio y colaborador. No envían solicitudes ni cambian el número oficial, la versión, los precios, los envíos o la ocupación remota. Las etiquetas, Inicio, Comandas y los avisos leen esa misma proyección para poder probar el ciclo completo de listo y servido.

Las horas de esta proyección usan la misma precisión de milisegundos que la caché. Así, pulsaciones consecutivas después de guardar no disparan un falso conflicto por diferencias de precisión. Se mantienen las comprobaciones de producto y versión. El aviso textual de pruebas se retiró de la pantalla por petición del usuario; el cierre en caja continúa pendiente de su contrato real.

Se conserva al reiniciar y al refrescar la misma versión. Una versión nueva o un estado diferente recibido del servidor tiene prioridad y deja sin efecto la simulación correspondiente. La versión release ignora estas simulaciones y no habilita las etiquetas interactivas; los endpoints reales de cocina y entrega continúan pendientes. Esta habilitación no activa la anulación parcial, la edición de enviados ni el cierre en caja.

## Notificaciones locales: alcance inicial acordado el 26 de noviembre

- Se implementan dos tipos: **Listo para recoger** y **¿Ya lo serviste?**. Los demás estados no generan avisos en esta etapa.
- El historial se guarda en un archivo privado del dispositivo, separado por negocio y colaborador, fuera del backup de Android. Se mantiene al reiniciar OPS y después de servir, cerrar o anular. Borrar los datos de la aplicación o desinstalarla elimina esta caché; no existe sincronización del historial entre dispositivos.
- La pantalla Notificaciones lee exclusivamente esta caché. Muestra círculo con identificación de la mesa, producto/cantidad, comanda, local y tiempo desde la recepción, agrupando por Hoy, Ayer y fechas anteriores. Abrirla no solicita el listado ni el detalle de comandas al API.
- El registro se genera al observar `ready` en los datos locales conocidos de la cuenta. Los IDs estables de unidades evitan repetir el aviso al refrescar o reagrupar una línea por estados. Las unidades que llegan listas juntas se agrupan por producto/ronda. No se inventan productos, estaciones ni confirmaciones de recepción de cocina.
- Si no se recibe `ready_at`, se registra cuándo OPS detectó el estado. Esta es una **hora de recepción local**, no la hora de preparación ni de envío. No se reconstruyen notificaciones anteriores a disponer de este historial.
- Si el producto sigue en `ready`, se recuerda cada cinco minutos mientras OPS está abierta. El recordatorio de ese producto/ronda actualiza su propia fila en lugar de agregar otra cada cinco minutos. El aviso inicial mantiene su fecha original.
- Al conocer el estado servido, anulado o el cierre, ese producto deja de estar entre los listos y no vuelve a generar recordatorios. Se conserva su historial. La ausencia temporal de un detalle no genera otro aviso inicial cuando vuelve a leerse.
- La caché funciona aunque el permiso de notificaciones de Android esté desactivado. Sonido y vibración dependen del permiso y de la configuración del equipo.
- Esto no agrega endpoints, push ni tareas de fondo. Sigue pendiente recibir los cambios remotos oportunamente y el contrato para marcar unidades como servidas. Los avances habilitados en debug son únicamente proyecciones de prueba en caché y no confirman una entrega al servidor.

### Notificaciones pendientes para la siguiente etapa

- [ ] **Envío rechazado:** registrar el rechazo definitivo y su motivo permitido, separado de un timeout o resultado incierto. Mantener el borrador y permitir su revisión sin duplicar rondas.
- [ ] **Cambio de responsable:** registrar quién entrega y quién recibe la atención, comanda/mesa y momento confirmado. Dirigir el aviso a la cuenta correspondiente cuando exista el contrato o evento real.
- [ ] **Precio actualizado:** avisar qué producto del borrador cambió de precio y requerir revisión antes de reenviar. Basarse en una respuesta real de precio cambiado o en una carta vigente, sin cambiar silenciosamente una captura pendiente de confirmación.

## Pendientes reales

- Operaciones de preparación, listo y entrega, con timestamps. Hasta integrar sus contratos, debug permite la simulación local descrita arriba y release mantiene los controles deshabilitados.
- Anulación parcial por unidades, con motivo, autor, fecha y regla cuando todas terminan anuladas.
- Envío a caja/cierre y momento exacto de liberación. No se simula un cierre local ni se confunde pase a caja con pago.
- Historial remoto por período: el listado recibido devuelve solo abiertas. El detalle conserva las ya conocidas, pero no reconstruye el historial completo tras reinstalar.
- Eventos/push y resumen con estados/última actividad. Configurar sonido y vibración no equivale a integrar notificaciones del servidor.
- Confirmar límites de modificadores repetibles y soporte ETag de Carta. Su caché actual usa TTL de 60 segundos; generated_at y la versión de comanda no son ETag.
- Instalación y prueba con sesión real, verificación de los locales actuales y pruebas entre dos dispositivos. El usuario pidió continuar con compilación y pruebas automatizadas mientras el teléfono no está disponible.
