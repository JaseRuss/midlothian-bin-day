package com.midlothianbins

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val COUNCIL_URL = "https://my.midlothian.gov.uk/service/Bin_Collection_Dates"
private const val IMPORT_STEPS =
    "Get your PDF from Midlothian Council, then import it here:\n\n" +
        "1. Tap \"Open council website\".\n" +
        "2. Enter your postcode and choose your address.\n" +
        "3. Select the \"Generate PDF\" button and download the PDF.\n" +
        "4. Come back here, tap \"Import PDF\" and pick the downloaded file."
private val dateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                BinDayApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BinDayApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var schedule by remember { mutableStateOf(ScheduleRepo.get(ctx)) }
    var remindersOn by remember { mutableStateOf(Prefs.remindersEnabled(ctx)) }
    var hour by remember { mutableIntStateOf(Prefs.reminderHour(ctx)) }
    var visibleCount by remember { mutableIntStateOf(10) }
    // 1 = "Do you live on Sycamore Drive?", 2 = prompt to import a PDF, 0 = none
    var setupStep by remember { mutableIntStateOf(if (Prefs.setupDone(ctx)) 0 else 1) }

    fun applyReminderSettings() {
        ReminderScheduler.schedule(ctx)
        BinWidgetProvider.updateAll(ctx)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            remindersOn = true
            Prefs.setRemindersEnabled(ctx, true)
            applyReminderSettings()
        } else {
            scope.launch { snackbar.showSnackbar("Allow notifications to get reminders.") }
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { PdfImporter.import(ctx, uri) }
            result.onSuccess {
                ScheduleRepo.save(ctx, it)
                schedule = it
                applyReminderSettings()
                snackbar.showSnackbar("Imported ${it.pickups.size} collection days.")
            }.onFailure {
                snackbar.showSnackbar("Couldn't read that PDF: ${it.message ?: "unknown error"}")
            }
        }
    }

    if (setupStep == 1) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Welcome to Midlothian Bin Day") },
            text = { Text("Do you live on Sycamore Drive, Penicuik? The built-in schedule is for that street.") },
            confirmButton = {
                TextButton(onClick = {
                    Prefs.setSetupDone(ctx)
                    setupStep = 0
                }) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = {
                    Prefs.setSetupDone(ctx)
                    ScheduleRepo.clear(ctx)
                    schedule = ScheduleRepo.get(ctx)
                    applyReminderSettings()
                    setupStep = 2
                }) { Text("No") }
            }
        )
    }
    if (setupStep == 2) {
        AlertDialog(
            onDismissRequest = { setupStep = 0 },
            title = { Text("Upload your collection PDF") },
            text = { Text(IMPORT_STEPS) },
            confirmButton = {
                TextButton(onClick = {
                    setupStep = 0
                    pdfLauncher.launch(arrayOf("application/pdf"))
                }) { Text("Import PDF") }
            },
            dismissButton = {
                TextButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(COUNCIL_URL)))
                }) { Text("Open council website") }
            }
        )
    }

    val today = LocalDate.now()
    val upcoming = schedule.pickups.filter { !it.date.isBefore(today) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Midlothian Bin Day") }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val next = upcoming.firstOrNull()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Next collection", style = MaterialTheme.typography.labelLarge)
                        if (next == null) {
                            Text("No upcoming dates. Import a new schedule below.", Modifier.padding(top = 8.dp))
                        } else {
                            Text(
                                BinWidgetProvider.relativeLabel(next.date),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(dateFmt.format(next.date))
                            Spacer(Modifier.padding(top = 8.dp))
                            BinChips(next)
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Day-before reminder", fontWeight = FontWeight.Bold)
                                Text("Notification the evening before a collection", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = remindersOn, onCheckedChange = { on ->
                                if (on && !ReminderScheduler.hasNotificationPermission(ctx) && Build.VERSION.SDK_INT >= 33) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    remindersOn = on
                                    Prefs.setRemindersEnabled(ctx, on)
                                    applyReminderSettings()
                                }
                            })
                        }
                        if (remindersOn) {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Remind me at", Modifier.weight(1f))
                                TextButton(onClick = {
                                    hour = (hour + 23) % 24
                                    Prefs.setReminderHour(ctx, hour); applyReminderSettings()
                                }) { Text("−") }
                                Text(String.format(Locale.UK, "%02d:00", hour), fontWeight = FontWeight.Bold)
                                TextButton(onClick = {
                                    hour = (hour + 1) % 24
                                    Prefs.setReminderHour(ctx, hour); applyReminderSettings()
                                }) { Text("+") }
                            }
                        }
                    }
                }
            }

            item { Text("Upcoming", style = MaterialTheme.typography.titleMedium) }

            items(upcoming.drop(1).take(visibleCount), key = { it.date.toString() }) { pickup ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row {
                            Text(dateFmt.format(pickup.date), Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text(BinWidgetProvider.relativeLabel(pickup.date), style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.padding(top = 6.dp))
                        BinChips(pickup)
                    }
                }
            }

            if (upcoming.size - 1 > visibleCount) {
                item {
                    OutlinedButton(
                        onClick = { visibleCount += 20 },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Show more dates") }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Update your schedule", fontWeight = FontWeight.Bold)
                        Text(
                            "Get a new PDF from Midlothian Council, then import it here:\n\n" +
                                "1. Tap \"Open council website\" below.\n" +
                                "2. Enter your postcode and choose your address.\n" +
                                "3. Select the \"Generate PDF\" button and download the PDF.\n" +
                                "4. Come back here, tap \"Import PDF\" and pick the downloaded file.",
                            Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(COUNCIL_URL)))
                            }) { Text("Open council website") }
                            Button(onClick = { pdfLauncher.launch(arrayOf("application/pdf")) }) { Text("Import PDF") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BinChips(pickup: PickUp) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        pickup.bins.forEach { bin ->
            Box(
                Modifier
                    .background(Color(bin.color), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(bin.short, color = Color.White, fontSize = 13.sp)
            }
        }
    }
}
