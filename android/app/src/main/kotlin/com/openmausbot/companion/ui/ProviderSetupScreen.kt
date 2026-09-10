package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * In-app AI provider setup: anyone holding the phone can connect the paired
 * computer to an API provider without touching its terminal. Keys are stored
 * on the computer (PATCH /api/config, admin scope) — never on the phone —
 * so losing the phone never leaks them, and unpairing keeps working the way
 * SettingsScreen documents it.
 *
 * Server key slots today: "anthropic", "xai", and one shared "openaiCompat"
 * slot (OpenAI / OpenRouter / Groq / any OpenAI-compatible endpoint). Each
 * bot then picks its provider from the per-bot picker, which already exists.
 */
private data class ProviderOption(
    /** Server key kind: one of "anthropic", "xai", "openaiCompat". */
    val kind: String,
    val title: String,
    val blurb: String,
    val needsEndpointChoice: Boolean = false,
)

private data class EndpointPreset(val label: String, val url: String)

private val PROVIDERS = listOf(
    ProviderOption(
        kind = "openaiCompat",
        title = "OpenAI-compatible",
        blurb = "OpenAI, OpenRouter, Groq, or your own endpoint. One active endpoint at a time.",
        needsEndpointChoice = true,
    ),
    ProviderOption(kind = "anthropic", title = "Anthropic", blurb = "Claude API key, billed by Anthropic."),
    ProviderOption(kind = "xai", title = "xAI", blurb = "Grok API key, billed by xAI."),
)

private val ENDPOINT_PRESETS = listOf(
    EndpointPreset("OpenAI", "https://api.openai.com/v1"),
    EndpointPreset("OpenRouter", "https://openrouter.ai/api/v1"),
    EndpointPreset("Groq", "https://api.groq.com/openai/v1"),
    EndpointPreset("Custom", ""),
)

@Composable
fun ProviderSetupScreen(onBack: () -> Unit) {
    val environment = LocalCompanion.current
    val session = environment.session
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(0) }
    var provider by remember { mutableStateOf(PROVIDERS[0]) }
    var endpoint by remember { mutableStateOf(ENDPOINT_PRESETS[1]) }
    var customUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var verdict by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    fun effectiveUrl(): String? {
        if (provider.kind != "openaiCompat") return null
        if (endpoint.label == "Custom") return customUrl.trim().ifEmpty { null }
        return endpoint.url
    }

    fun resetVerdict() {
        verdict = null
        saved = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("AI providers", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider()
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (step == 0) {
                Text(
                    "Pick a provider. Each bot can use a different one afterwards, from its own profile.",
                    fontSize = 14.sp,
                )
                PROVIDERS.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = provider == option,
                                onClick = { provider = option; resetVerdict() },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = provider == option, onClick = null)
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(option.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(
                                option.blurb,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue")
                }
            } else {
                Text(
                    "Connect ${provider.title}. The key is stored on the paired computer, never on this phone.",
                    fontSize = 14.sp,
                )
                if (provider.needsEndpointChoice) {
                    Text("Endpoint", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    ENDPOINT_PRESETS.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = endpoint == preset,
                                    onClick = { endpoint = preset; resetVerdict() },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = endpoint == preset, onClick = null)
                            Text(preset.label, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (endpoint.label == "Custom") {
                        OutlinedTextField(
                            value = customUrl,
                            onValueChange = { customUrl = it; resetVerdict() },
                            label = { Text("Base URL") },
                            placeholder = { Text("https://my-host/v1") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it; resetVerdict() },
                        label = { Text("Model (optional)") },
                        placeholder = { Text("e.g. openai/gpt-4o-mini") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; resetVerdict() },
                    label = { Text("API key") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                verdict?.let {
                    Text(
                        it,
                        fontSize = 14.sp,
                        color = if (saved || it.startsWith("Key works")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                testing = true
                                verdict = null
                                val result = session.testProviderKey(
                                    provider.kind,
                                    apiKey.trim(),
                                    effectiveUrl(),
                                )
                                testing = false
                                verdict = when {
                                    result == null -> "Could not reach the computer."
                                    result.ok -> "Key works." +
                                        (if (result.models.isNotEmpty()) " Models: ${result.models.joinToString()}" else "")
                                    result.reason == "rejected" -> "Key rejected (401/403). Check it and try again."
                                    result.reason == "unreachable" -> "Provider unreachable from the computer. Check the endpoint."
                                    else -> "Unexpected provider answer."
                                }
                            }
                        },
                        enabled = !testing && !saving && apiKey.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (testing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Test key")
                        }
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                saving = true
                                val ok = session.saveProviderKey(
                                    provider.kind,
                                    apiKey.trim(),
                                    effectiveUrl(),
                                    model.trim().ifEmpty { null },
                                )
                                saving = false
                                saved = ok
                                verdict = if (ok) "Saved. Bots can use ${provider.title} now." else null
                            }
                        },
                        enabled = !testing && !saving && apiKey.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Save")
                        }
                    }
                }
                TextButton(onClick = { step = 0; resetVerdict() }) { Text("‹ Change provider") }
            }
        }
    }
}
