# Carta de Salón en OPS

Integración de `GET /v1/ops/locations/{location}/menu`, con Bearer Ops y `X-Business-Id`. El local debe pertenecer al contexto autorizado y exige `pos.menus.view`. No utiliza el environment ni el catálogo/personalizador local para completar la respuesta.

## Datos y selección

- `menu: null` produce una carta vacía, sin recurrir a los productos del prototipo.
- Se conservan el orden de categorías/subcategorías y los productos directamente asociados a la categoría. Si el mismo producto se repite en categoría y subcategoría, se presenta una vez por categoría, preferentemente dentro de la subcategoría. El mismo ID con definiciones contradictorias se rechaza.
- El precio pertenece a la variante seleccionada: `price_minor` y `currency_code`. Con una variante se selecciona automáticamente; con varias se exige una elección. Disponibilidad efectiva: producto y variante disponibles.
- Los modificadores se resuelven desde `data.modifier_groups`, nunca desde `personalizacion.json`. Sus opciones conservan ID, disponibilidad, recargo y predeterminación.
- Los grupos con `variant_id: null` son generales. Para un mismo `group_id`, el enlace específico de la variante prevalece sobre el general al seleccionar esa variante.
- Se interpreta `min_select`/`max_select` como cantidad total de unidades elegidas. `allows_repeat` habilita cantidades por opción; el límite del grupo sigue aplicándose. Esta interpretación se comunicó para confirmar con backend.
- Las opciones predeterminadas disponibles se aplican y sus recargos se muestran inmediatamente. Los recargos se interpretan en la moneda de la variante seleccionada. No se mezclan monedas en una atención.

## Borrador y compatibilidad

Cada línea conserva `productId`, `variantId`, `modifierSelections` (grupo, opción y cantidad), `locationId`, `menuId` y moneda, además de los textos necesarios para mostrarla. La persistencia es compatible con las líneas anteriores sin IDs; no se inventan referencias remotas a partir del nombre.

Las líneas de variantes u opciones distintas no se fusionan por tener el mismo nombre/precio. La primera incorporación vincula la atención al local de la carta, aun si todavía no tiene mesa. Después no puede trasladarse a una mesa de otro local.

Antes de incorporar una línea, se valida contra la carta vigente de ese local: variante, grupos, opciones, límites, moneda y precio. Si el catálogo cambió, se actualiza la selección y se informa del cambio en vez de guardar un precio distinto silenciosamente. Esta comprobación usa la caché descrita abajo; no sustituye la validación autoritativa que deberá hacer el futuro endpoint de creación/edición de comandas.

## Caché y errores

La ausencia de `pos.menus.view` en el contexto guardado provoca una revalidación de `GET /ops/context` antes de denegar Carta. La acción «Actualizar acceso» también revalida el contexto y vuelve a consultar la carta. El contexto se guarda solo si la misma sesión sigue activa; una respuesta pendiente no restaura una sesión cerrada ni sobrescribe otro acceso. Esto no concede permisos: si el contexto vigente sigue sin incluir la acción, se muestra «Sin acceso a Carta». El log de staging distingue esa denegación local (`OpsMenuAccess`) de un HTTP 403 real del endpoint (`OpsAuthApi`), sin imprimir tokens ni respuestas completas.

Una respuesta 200 con `menu: null` o sin productos muestra el estado vacío. Un 403 muestra un aviso específico de acceso a Carta, no el mismo texto de una carta vacía ni únicamente el título genérico de operación fallida.

La instantánea se guarda por negocio, empleado, membresía, versión de acceso y local. Se reutiliza durante 60 segundos y se comparte el trabajo concurrente de una misma clave. El selector admite actualizar arrastrando hacia abajo; no consulta por cada búsqueda o pulsación.

No se usa `generated_at` como revisión ni se presupone soporte de ETag. Una instantánea vencida no se utiliza como respaldo ante fallos de red. Un 401 invalida la sesión y devuelve al ingreso; 403/404/409 no se ocultan con datos anteriores. Al cerrar sesión o cambiar el contexto se invalida la caché correspondiente. No se almacenan tokens en la caché del catálogo.

## Pendientes fuera de este endpoint

La lectura de carta y la construcción del borrador quedan conectadas. Los contratos de creación con primer envío, detalle, listado abierto, rondas adicionales y anulación completa fueron recibidos el 25 de noviembre de 2026 y se integran según [Mesas y atenciones](mesas-atenciones-pendientes.md). El borrador continúa siendo local: el primer POST confirmado asigna el número oficial. El servidor confirma precios, disponibilidad y permisos al enviar. Las líneas antiguas sin IDs deben volver a seleccionarse desde Carta antes de enviarlas. Las imágenes no están en la respuesta proporcionada y no se inventan URLs.
