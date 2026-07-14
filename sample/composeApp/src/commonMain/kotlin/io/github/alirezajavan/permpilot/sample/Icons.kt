package io.github.alirezajavan.permpilot.sample

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhonePaused
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.alirezajavan.permpilot.Permission

fun Permission.icon(): ImageVector =
    when (this) {
        Permission.Camera -> Icons.Default.CameraAlt
        Permission.Microphone -> Icons.Default.Mic
        Permission.LocationWhileInUse -> Icons.Default.LocationOn
        Permission.LocationAlways -> Icons.Default.LocationOn
        Permission.Notifications -> Icons.Default.Notifications
        Permission.Contacts -> Icons.Default.Person
        Permission.WriteContacts -> Icons.Default.Person
        is Permission.Calendar -> Icons.Default.DateRange
        Permission.PhotoLibrary -> Icons.Default.PhotoLibrary
        Permission.MediaLocation -> Icons.Default.Place
        Permission.AudioFiles -> Icons.Default.Audiotrack
        Permission.BluetoothScan -> Icons.Default.Bluetooth
        Permission.BluetoothConnect -> Icons.Default.Bluetooth
        Permission.BluetoothAdvertise -> Icons.Default.Bluetooth
        Permission.NearbyWifiDevices -> Icons.Default.Wifi
        Permission.BodySensors -> Icons.Default.Favorite
        Permission.BodySensorsBackground -> Icons.Default.Favorite
        Permission.ActivityRecognition -> Icons.AutoMirrored.Filled.DirectionsRun
        is Permission.Health -> Icons.Default.HealthAndSafety
        Permission.CallPhone -> Icons.Default.Phone
        Permission.ReadPhoneState -> Icons.Default.Phone
        Permission.ReadPhoneNumbers -> Icons.Default.Phone
        Permission.AnswerPhoneCalls -> Icons.Default.Phone
        Permission.ReadCallLog -> Icons.Default.PhonePaused
        Permission.WriteCallLog -> Icons.Default.PhonePaused
        Permission.SendSms -> Icons.Default.Sms
        Permission.ReadSms -> Icons.Default.Sms
        Permission.ReceiveSms -> Icons.Default.Sms
        Permission.AppTrackingTransparency -> Icons.Default.AdsClick
        Permission.SpeechRecognition -> Icons.Default.RecordVoiceOver
        Permission.Reminders -> Icons.AutoMirrored.Filled.ListAlt
        // Special
        Permission.SystemAlertWindow -> Icons.Default.Warning
        Permission.ExactAlarm -> Icons.Default.Alarm
        Permission.FullScreenIntent -> Icons.Default.Fullscreen
        Permission.IgnoreBatteryOptimizations -> Icons.Default.BatterySaver
        Permission.WriteSettings -> Icons.Default.Settings
        Permission.ManageExternalStorage -> Icons.Default.Storage
        Permission.DoNotDisturbAccess -> Icons.Default.DoNotDisturb
        Permission.UsageAccess -> Icons.Default.DataUsage
        Permission.NotificationListenerAccess -> Icons.Default.NotificationsActive
        else -> Icons.Default.Extension
    }
