package com.azarai.goworkbro.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azarai.goworkbro.R
import com.azarai.goworkbro.ui.appString
import com.azarai.goworkbro.ui.components.MarkdownText
import com.azarai.goworkbro.ui.theme.AppTheme

/** Full-screen news reader with a date rail of cached editions. */
@Composable
fun NewsReaderScreen(initialDate: String?, onClose: () -> Unit, vm: TodayViewModel = viewModel()) {
    val colors = AppTheme.colors
    val editions by vm.news.collectAsState()
    var selectedDate by remember {
        mutableStateOf(initialDate ?: editions.firstOrNull()?.date ?: "")
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.scaffold)
            .statusBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = appString(R.string.back), tint = colors.textBody)
            }
            Text(
                appString(R.string.news_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textSecondary,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            editions.forEach { edition ->
                val selected = edition.date == selectedDate
                Box(
                    modifier = Modifier
                        .background(
                            if (selected) colors.primary else colors.inputFill,
                            RoundedCornerShape(999.dp),
                        )
                        .clickable { selectedDate = edition.date }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        edition.date,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) androidx.compose.ui.graphics.Color.White else colors.textMuted,
                    )
                }
            }
        }

        val selected = editions.firstOrNull { it.date == selectedDate }
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (selected == null) {
                Text(
                    appString(R.string.news_empty),
                    color = colors.textFaint,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                MarkdownText(
                    markdown = selected.markdown,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                )
            }
        }
    }
}
