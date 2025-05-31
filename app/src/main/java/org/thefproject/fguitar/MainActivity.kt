package org.thefproject.fguitar




import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager


import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsAnimation
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
import okhttp3.Call
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
    private val apiKey = "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"

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
    override fun onStart() {
        super.onStart()
        setContentView(R.layout.activity_main)
        checkAuthorization()
        checkForUpdates()
        ButtonSetup()
        val sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("isLoggedIn", true)
            apply()
        }

    }

    fun ButtonSetup(){
        val g = findViewById<Button>(R.id.button)
        g.setOnClickListener {
            val g = Intent(this, AccordsListActivity::class.java)
            startActivity(g)
    }
        val add = findViewById<Button>(R.id.button3)
       add.setOnClickListener {
            val g = Intent(this, ConstructorActivity::class.java)
            startActivity(g)
        }
        }


    private fun checkAuthorization() {
        // Здесь мы используем SharedPreferences для хранения флага авторизации
        // При успешном входе мы должны сохранить в SharedPreferences isLoggedIn = true
        val sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPref.getBoolean("isLoggedIn", false)

        // Если пользователь не авторизован — перенаправляем на экран входа (LoginActivity)
        if (!isLoggedIn) {
            val intent = Intent(this, LoginActivity::class.java)
            // Флаги, чтобы убрать текущий стек активностей
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish() // завершаем текущую активность
        }
    }
    private fun checkForUpdates() {
        val client = OkHttpClient()

        val url = "https://api.github.com/repos/FaliedDedd/FGuitar/contents/json/ota.json?ref=main"

        val request = Request.Builder()
            .url(url)
            // Добавляем заголовки для авторизации и для корректного ответа API
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/vnd.github.v3+json")
            .build()

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
                    val bodyString = response.body?.string() ?: ""
                    try {

                        val jsonResponse = JSONObject(bodyString)

                        val latestVersion = jsonResponse.getString("version")
                        val downloadUrl = jsonResponse.getString("url")
                        val changesArray = jsonResponse.getJSONArray("changes")
                        val lastBuildNumber = jsonResponse.getString("build")

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
                                // Обработка случая, когда обновлений нет
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Ошибка обработки данных обновления",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
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
        val apkFile = File(getExternalFilesDir(null), "FGuitar.apk")
        val apkUri = FileProvider.getUriForFile(this, "${packageName}.provider", apkFile)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = apkUri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }
    private fun downloadAPK(url: String) {
        val fileName = "FGuitar.apk"
        val dir = getExternalFilesDir(null)

        clearTempDirectory(dir)

        val existingFile = File(dir, fileName)
        if (existingFile.exists()) {
            existingFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(url))
        request.setTitle("Загрузка обновления для FGuitar")
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
