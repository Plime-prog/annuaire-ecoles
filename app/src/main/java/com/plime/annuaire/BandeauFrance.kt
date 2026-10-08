package com.plime.annuaire

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BandeauFrance() {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(10.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF2F6DB5)))
            Box(Modifier.weight(1f).fillMaxHeight().background(Color.White))
            Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFFDC2626)))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE2E8F0)))
    }
}
