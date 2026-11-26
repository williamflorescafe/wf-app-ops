package com.example.wfappops.designsystem.icons

import androidx.annotation.DrawableRes
import com.example.wfappops.R

/**
 * Catálogo de íconos de OPS, organizado como `designsystem/icons/WfIcons` de App Team.
 *
 * Los vectores XML permanecen en `app/src/main/res/drawable`, la carpeta de recursos de Android.
 * Para agregar una acción: guardar su vector `ic_<nombre>.xml` en esa carpeta, registrarlo aquí
 * y consumirlo con `painterResource(WfIcons.Nombre)`. Evita duplicar el XML entre componentes.
 */
internal object WfIcons {
    /** Comentario del envío o nota del producto. */
    @get:DrawableRes
    val Comment: Int get() = R.drawable.ic_order_comment

    /** Signo más para el CTA compacto de agregar productos. */
    @get:DrawableRes
    val AddProduct: Int get() = R.drawable.ic_order_add_product

    /** Avión triangular de envío para el CTA compacto de enviar a cocina. */
    @get:DrawableRes
    val SendToKitchen: Int get() = R.drawable.ic_order_send_kitchen

    /** Caja registradora para finalizar la atención y registrarla para caja. */
    @get:DrawableRes
    val Cashier: Int get() = R.drawable.ic_order_cashier
}
