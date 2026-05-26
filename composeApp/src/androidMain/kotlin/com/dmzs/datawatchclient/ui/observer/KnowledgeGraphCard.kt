package com.dmzs.datawatchclient.ui.observer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Composable
internal fun KnowledgeGraphCard() {
    val scope = rememberCoroutineScope()
    var queryEntity by remember { mutableStateOf("") }
    var triples by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var queryBanner by remember { mutableStateOf<String?>(null) }
    var queried by remember { mutableStateOf(false) }

    var subject by remember { mutableStateOf("") }
    var predicate by remember { mutableStateOf("") }
    var obj by remember { mutableStateOf("") }
    var addBanner by remember { mutableStateOf<String?>(null) }
    var addBusy by remember { mutableStateOf(false) }

    suspend fun transport(): TransportClient? {
        val id = ServiceLocator.activeServerStore.get()
        val profiles = ServiceLocator.profileRepository.observeAll().first().filter { it.enabled }
        val p = profiles.firstOrNull { it.id == id } ?: profiles.firstOrNull()
        return p?.let { ServiceLocator.transportFor(it) }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard().padding(12.dp),
    ) {
        PwaSectionTitle("Knowledge Graph", docsAnchor = "memory")

        // Query section
        Text(
            "Query",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = queryEntity,
                onValueChange = { queryEntity = it },
                label = { Text("Entity") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    scope.launch {
                        queryBanner = null
                        transport()?.queryKg(queryEntity.trim())
                            ?.onSuccess { result -> triples = result; queried = true }
                            ?.onFailure { queryBanner = it.message }
                    }
                },
                enabled = queryEntity.isNotBlank(),
            ) { Text("Query") }
        }

        queryBanner?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
        }

        if (queried) {
            if (triples.isEmpty()) {
                Text(
                    "No triples found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Text(
                    "${triples.size} triple${if (triples.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                triples.forEach { t ->
                    val s = t.str("subject") ?: "?"
                    val p = t.str("predicate") ?: "?"
                    val o = t.str("object") ?: "?"
                    val ts = t.str("valid_from")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        Text(p, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                        Text(o, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        ts?.let { Text(it.take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))

        // Add triple section
        Text(
            "Add Triple",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = predicate, onValueChange = { predicate = it }, label = { Text("Predicate") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = obj, onValueChange = { obj = it }, label = { Text("Object") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        addBanner?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    addBusy = true
                    addBanner = null
                    transport()?.addKgTriple(subject.trim(), predicate.trim(), obj.trim())
                        ?.onSuccess {
                            addBanner = "Triple added"
                            subject = ""; predicate = ""; obj = ""
                            if (queryEntity.isNotBlank()) {
                                transport()?.queryKg(queryEntity.trim())
                                    ?.onSuccess { result -> triples = result }
                            }
                        }
                        ?.onFailure { addBanner = "Error: ${it.message}" }
                    addBusy = false
                }
            },
            enabled = !addBusy && subject.isNotBlank() && predicate.isNotBlank() && obj.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) { Text("Add triple") }
    }
}

private fun JsonObject.str(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
