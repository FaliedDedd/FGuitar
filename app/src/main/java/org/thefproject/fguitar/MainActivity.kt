package org.thefproject.fguitar




import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager


import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.LocalTime
import java.time.format.DateTimeFormatter

import java.util.Calendar
class MainActivity : AppCompatActivity() {

    private lateinit var downloadProgressDialog: AlertDialog
    private var downloadedBytes: Long = 0L
    private var totalBytes: Long = 0L
    val osVersion = android.os.Build.VERSION.SDK_INT
    val osVersionName = android.os.Build.VERSION.RELEASE
    val versionName = BuildConfig.VERSION_NAME
    val versionCode = BuildConfig.VERSION_CODE
    private lateinit var branchSwitch: SwitchMaterial
    private var downloadId: Long = -1
    data class BannerData(
        val title: String,
        val message: String,
        val imageUrl: String
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
checkForUpdates()
        val g = findViewById<Button>(R.id.button)
        g.setOnClickListener {
            val g = Intent(this, AccordsList::class.java)
            startActivity(g)
        }
    }



    private fun checkForUpdates() {
        val client = OkHttpClient()
        val url = "https://raw.githubusercontent.com/FaliedDedd/FGuitar/refs/heads/main/json/ota.json"


        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Невозможно подключиться к серверу",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (response.isSuccessful) {
                    response.body?.string()?.let { jsonString ->
                        val jsonObject = JSONObject(jsonString)
                        val latestVersion = jsonObject.getString("version")
                        val downloadUrl = jsonObject.getString("url")
                        val changesArray = jsonObject.getJSONArray("changes")
                        var lastBuildNumber = jsonObject.getString("build")

                        val changesList = mutableListOf<String>()
                        for (i in 0 until changesArray.length()) {
                            changesList.add(changesArray.getString(i))
                        }

                        val currentVersion = BuildConfig.VERSION_NAME
                        if (latestVersion > currentVersion) {
                            runOnUiThread {
                                showUpdateDialog(
                                    latestVersion,
                                    currentVersion,
                                    lastBuildNumber,
                                    changesList,
                                    downloadUrl
                                )
                            }
                        } else {
                            runOnUiThread {


                            }
                        }
                    } ?: runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Ошибка: Пустой ответ от сервера",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Ошибка: ${response.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    private fun showUpdateDialog(
        latestVersion: String,
        currentVersion: String,
        lastBuildNumber: String,
        changesList: List<String>,
        downloadUrl: String
    ) {
        val dialogView = layoutInflater.inflate(R.layout.update_dialog, null)
        val dialogBuilder = MaterialAlertDialogBuilder(this)
            .setTitle("Обновление доступно!")
            .setView(dialogView)
            .setPositiveButton("Обновить") { _, _ ->
                downloadAPK(downloadUrl)
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }

        val textViewCurrent = dialogView.findViewById<TextView>(R.id.currentVersionText)
        val textViewLatest = dialogView.findViewById<TextView>(R.id.latestVersionText)
        val textViewChanges = dialogView.findViewById<TextView>(R.id.changesListText)

        textViewCurrent.text = "Текущая версия: $currentVersion [$versionCode]"
        textViewLatest.text = "Новая версия: $latestVersion [$lastBuildNumber]"
        textViewChanges.text = changesList.joinToString("\n") { "• $it" }

        val dialog = dialogBuilder.create()
        dialog.show()
    }








    private fun updateProgressDialog(downloaded: Long, total: Long) {
        if (::downloadProgressDialog.isInitialized && downloadProgressDialog.isShowing) {
            val progress = if (total > 0) (downloaded * 100 / total).toInt() else 0
            val downloadedMB = downloaded / (1024 * 1024)
            val totalMB = total / (1024 * 1024)

            val progressBar = downloadProgressDialog.findViewById<ProgressBar>(R.id.progressBar)
            val progressText = downloadProgressDialog.findViewById<TextView>(R.id.progressText)
            val volumeText = downloadProgressDialog.findViewById<TextView>(R.id.volumeText)

            progressBar?.progress = progress
            progressText?.text = "Прогресс: $progress%"
            volumeText?.text = "Скачано: $downloadedMB MB из $totalMB MB"
        }
    }

    private fun showDownloadProgressDialog() {
        val dialogView = layoutInflater.inflate(R.layout.download_dialog, null)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
        val progressText = dialogView.findViewById<TextView>(R.id.progressText)
        val volumeText = dialogView.findViewById<TextView>(R.id.volumeText)

        downloadProgressDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Загрузка обновления...")
            .setView(dialogView)
            .setCancelable(false)
            .create()

        downloadProgressDialog.show()
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
            if (id == downloadId) {
                downloadProgressDialog.dismiss()
                Toast.makeText(this@MainActivity, "Загрузка завершена", Toast.LENGTH_SHORT).show()
                installAPK()
                unregisterReceiver(this)
            }
        }
    }

    private fun installAPK() {
        val apkFile = File(getExternalFilesDir(null), "EduCA.apk")
        val apkUri = FileProvider.getUriForFile(this, "${packageName}.provider", apkFile)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = apkUri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }
    private fun downloadAPK(url: String) {
        val fileName = "EduCA.apk"
        val dir = getExternalFilesDir(null)

        clearTempDirectory(dir)

        val existingFile = File(dir, fileName)
        if (existingFile.exists()) {
            existingFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(url))
        request.setTitle("Загрузка обновления для EduCA")
        request.setDescription("Начало загрузки...")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalFilesDir(this, null, fileName)

        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)

        showDownloadProgressDialog()

        Thread {
            val query = DownloadManager.Query().setFilterById(downloadId)
            while (true) {
                val cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    downloadedBytes =
                        cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    totalBytes =
                        cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                    runOnUiThread {
                        updateProgressDialog(downloadedBytes, totalBytes)
                    }

                    if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
                        runOnUiThread {
                            installAPK()
                        }
                        break
                    }
                }
                cursor?.close()
                Thread.sleep(500)
            }
        }.start()
    }

    private fun clearTempDirectory(dir: File?) {
        dir?.listFiles()?.forEach { file ->
            if (file.name.endsWith(".apk")) {
                file.delete()
            }
        }
    }

}
