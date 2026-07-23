package com.gammasweep.gvdemo

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gammasweep.gvintelligence.GS
import com.gammasweep.gvintelligence.GpsMode
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Minimal demo exercising the three things you test during integration:
 *   1. Get Session  — seal a token (sandbox=unencrypted, live=encrypted JWE)
 *   2. Get Device ID — the stable persisted identifier
 *   3. Render the decoded signal JSON (sandbox mode only) for eyeballing
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Step 1 — configure once at app start. Use sandbox=true with a gk_test_
        // key to inspect the raw signal envelope (unencrypted). Switch to false +
        // a live key for encrypted tokens that fraud-check decrypts.
        GV.configure(
            context = applicationContext,
            clientId = "YOUR_CLIENT_ID",
            gps = GpsMode.PROMPT,
            behavior = true,
            persistence = true,
            sandbox = true,
            debug = true,
        )

        setContent { DemoScreen() }
    }
}

@Composable
private fun DemoScreen() {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Idle") }
    var token by remember { mutableStateOf("") }
    var deviceId by remember { mutableStateOf("") }
    var signalJson by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val activity = androidx.compose.ui.platform.LocalContext.current as ComponentActivity
    val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* host app requests; the SDK reads location once granted */ }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Gamma Verify Intelligence Demo", style = MaterialTheme.typography.titleLarge)
            Text("Status: $status", style = MaterialTheme.typography.bodyMedium)

            Button(
                onClick = {
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    busy = true
                    status = "Collecting signals…"
                    scope.launch {
                        try {
                            val session = GV.getSession()
                            token = session
                            signalJson = decodeSandboxPayload(session) ?: ""
                            status = "Session ready"
                        } catch (e: Throwable) {
                            status = "Error: $e"
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Get Session") }

            OutlinedButton(
                onClick = {
                    deviceId = GV.getDeviceId()
                    status = "Device ID read"
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Get Device ID") }

            if (token.isNotEmpty()) Section("Token (send to YOUR backend)", token)
            if (deviceId.isNotEmpty()) Section("Device ID", deviceId)
            if (signalJson.isNotEmpty()) Section("Decoded signals (sandbox)", signalJson)
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * In sandbox mode the token is `gsds_sandbox.<base64(payload)>`. Decode the
 * second segment so we can pretty-print the signal envelope.
 */
private fun decodeSandboxPayload(token: String): String? {
    val parts = token.split(".")
    if (parts.size != 2 || parts[0] != "gsds_sandbox") return null
    val b64 = parts[1]
    val padded = b64 + "=".repeat((4 - b64.length % 4) % 4)
    return runCatching {
        val json = String(android.util.Base64.decode(padded, android.util.Base64.DEFAULT), Charsets.UTF_8)
        JSONObject(json).toString(2)
    }.getOrNull()
}
