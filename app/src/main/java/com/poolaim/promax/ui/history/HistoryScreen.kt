package com.poolaim.promax.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.poolaim.promax.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase F screen: recent analysis history list.
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpenSession: (Long) -> Unit,
) {
    val sessions by viewModel.sessions.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        if (sessions.isEmpty()) {
            Text(text = stringResource(R.string.history_empty))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(sessions, key = { it.id }) { session ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onOpenSession(session.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                        .format(Date(session.timestamp)),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = session.playerGroup,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            session.bestShotDifficulty?.let {
                                Text(
                                    text = "أفضل ضربة: ${difficultyLabel(it)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun difficultyLabel(name: String): String = when (name) {
    "EASY" -> "سهلة"
    "MEDIUM" -> "متوسطة"
    "HARD" -> "صعبة"
    else -> "صعبة جدًا"
}
