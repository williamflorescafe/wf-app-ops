package com.example.wfappops.home.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wfappops.R
import com.example.wfappops.ui.theme.ColorTokens

/** El texto es nativo y la ilustración es decorativa: toda la tarjeta es una única acción. */
@Composable
internal fun CreateOrderCard(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().testTag("home-create-order-card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ColorTokens.Primary, contentColor = ColorTokens.OnPrimary,
            disabledContainerColor = ColorTokens.Primary, disabledContentColor = ColorTokens.OnPrimary.copy(alpha = 0.55f))) {
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 144.dp)
            .background(Brush.horizontalGradient(listOf(ColorTokens.Primary, ColorTokens.PrimaryDeep)))) {
            Image(painterResource(R.drawable.home_create_order_art), contentDescription = null,
                modifier = Modifier.matchParentSize().padding(start = maxWidth * 0.34f).offset(x = 20.dp, y = 8.dp),
                contentScale = ContentScale.Fit, alignment = Alignment.BottomEnd,
                alpha = if (enabled) 1f else 0.55f)
            Column(Modifier.width(maxWidth * 0.55f).padding(start = 18.dp, end = 2.dp, top = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.home_create_order), style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 24.sp, lineHeight = 26.sp), maxLines = 2)
                Text(stringResource(R.string.home_create_order_tagline), style = MaterialTheme.typography.bodySmall,
                    color = ColorTokens.OnPrimary.copy(alpha = if (enabled) 0.90f else 0.55f))
                Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null,
                    modifier = Modifier.size(20.dp))
            }
        }
    }
}
