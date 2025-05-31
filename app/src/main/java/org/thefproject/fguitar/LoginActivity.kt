package org.thefproject.fguitar

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    // URL для файла users.json на GitHub
    private val baseGithubApiUrl = "https://api.github.com/repos/FaliedDedd/FGuitar/contents/json/users.json"
    // Токен GitHub – оставляем только сам токен (без "Bearer")
    private val apiKey = "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"
    private var sha: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val usernameEditText = findViewById<EditText>(R.id.usernameEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val statusTextView = findViewById<TextView>(R.id.statusTextView)
        val loginButton = findViewById<Button>(R.id.loginButton)

        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            if (username.isNotEmpty() && password.isNotEmpty()) {
                performLogin(username, password, statusTextView)
            } else {
                statusTextView.text = "Введите логин и пароль!"
            }
        }
    }

    // Формируем URL с параметром времени для обхода кеша
    private fun getGithubUrl(): String {
        return "$baseGithubApiUrl?t=" + System.currentTimeMillis()
    }

    // GET-запрос для получения файла с пользователями (users.json) с GitHub
    private fun performLogin(username: String, password: String, statusView: TextView) {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(getGithubUrl())
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    statusView.text = "Ошибка загрузки данных: ${e.message}"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody == null) {
                    runOnUiThread {
                        statusView.text = "Ошибка загрузки данных: ${response.code} ${response.message}"
                    }
                    return
                }
                Log.d("LoginActivity", "GET ответ: $responseBody")
                val jsonResponse = JSONObject(responseBody)
                sha = jsonResponse.optString("sha", null)

                // Получаем содержимое файла, оно закодировано в Base64
                val contentBase64 = jsonResponse.optString("content", "")
                val cleanedContent = contentBase64.replace("\\s".toRegex(), "")
                val contentJson = try {
                    String(Base64.decode(cleanedContent, Base64.DEFAULT), Charsets.UTF_8)
                } catch (e: IllegalArgumentException) {
                    Log.e("LoginActivity", "Ошибка декодирования Base64: ${e.message}")
                    "{}"
                }

                // Сохраняем полученные данные во временный файл "users_temp.json"
                saveJsonToTemp(contentJson, "users_temp.json")
                // Переходим к обработке логина
                processLogin(username, password, statusView)
            }
        })
    }

    // Сохраняем строку JSON во временный файл с указанным именем
    private fun saveJsonToTemp(jsonContent: String, fileName: String) {
        val tempFile = File(cacheDir, fileName)
        FileOutputStream(tempFile).use { it.write(jsonContent.toByteArray()) }
    }

    // Читаем JSON из временного файла
    private fun readJsonFromTemp(fileName: String): String {
        val tempFile = File(cacheDir, fileName)
        return if (tempFile.exists()) {
            FileInputStream(tempFile).use { it.readBytes().toString(Charsets.UTF_8) }
        } else {
            "{}"
        }
    }

    // Обработка логина: поиск пользователя, проверка данных, затем добавление события входа
    private fun processLogin(username: String, password: String, statusView: TextView) {
        val jsonContent = readJsonFromTemp("users_temp.json")
        val jsonObject = try {
            JSONObject(jsonContent)
        } catch (e: Exception) {
            Log.e("LoginActivity", "Ошибка парсинга JSON: ${e.message}")
            JSONObject()
        }

        // Получаем массив пользователей, или создаём новый, если он отсутствует
        val usersArray = jsonObject.optJSONArray("users") ?: JSONArray()
        Log.d("LoginActivity", "Текущий usersArray: $usersArray")

        var userFound: JSONObject? = null
        for (i in 0 until usersArray.length()) {
            val user = usersArray.getJSONObject(i)
            if (user.optString("username") == username && user.optString("password") == password) {
                userFound = user
                break
            }
        }

        if (userFound == null) {
            runOnUiThread {
                statusView.text = "Неверный логин или пароль"
            }
            return
        }

        // После проверки учетных данных нужно получить реальный IP через внешний API
        fetchPublicIp { ipAddress ->
            // Если IP определить не удалось, используем значение "unknown"
            val ip = ipAddress ?: "unknown"
            val deviceType = "${Build.MANUFACTURER} ${Build.MODEL}"
            val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())

            val loginEvent = JSONObject().apply {
                put("ip_address", ip)
                put("device_type", deviceType)
                put("timestamp", timestamp)
            }

            // Добавляем событие входа к пользователю
            val loginsArray = userFound.optJSONArray("logins") ?: JSONArray()
            loginsArray.put(loginEvent)
            userFound.put("logins", loginsArray)

            // Обновляем массив пользователей в общем JSON-объекте
            jsonObject.put("users", usersArray)
            Log.d("LoginActivity", "Обновленный JSON перед отправкой: $jsonObject")

            val updatedJson = jsonObject.toString(4)
            saveJsonToTemp(updatedJson, "users_temp.json")
            sendJsonToGithub(updatedJson, statusView)
        }
    }

    /**
     * Функция fetchPublicIp выполняет запрос к API (например, ipify) для определения публичного IP.
     * После получения IP (или в случае ошибки – возвращает null) вызывается callback.
     */
    private fun fetchPublicIp(callback: (String?) -> Unit) {
        val url = "https://api.ipify.org?format=json"
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("LoginActivity", "Ошибка получения IP: ${e.message}")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("LoginActivity", "Ошибка получения IP: Code ${response.code}")
                    callback(null)
                    return
                }
                val bodyString = response.body?.string()
                try {
                    val json = JSONObject(bodyString)
                    val ip = json.getString("ip")
                    callback(ip)
                } catch (e: Exception) {
                    Log.e("LoginActivity", "Ошибка парсинга IP: ${e.message}")
                    callback(null)
                }
            }
        })
    }

    // Отправка обновлённого JSON на GitHub с использованием актуального SHA
    private fun sendJsonToGithub(updatedJson: String, statusView: TextView) {
        val encodedJson = Base64.encodeToString(updatedJson.toByteArray(), Base64.DEFAULT)
        val client = OkHttpClient()

        val requestBody = JSONObject().apply {
            put("message", "Обновлены данные входа для пользователя")
            put("content", encodedJson)
            put("sha", sha ?: "")
        }

        val request = Request.Builder()
            .url(baseGithubApiUrl)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/vnd.github.v3+json")
            .put(RequestBody.create("application/json".toMediaType(), requestBody.toString()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    statusView.text = "Ошибка обновления данных: ${e.message}"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val resBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful) {
                        Log.d("LoginActivity", "PUT ответ: $resBody")
                        try {
                            val jsonRes = JSONObject(resBody)
                            val contentJson = jsonRes.getJSONObject("content")
                            sha = contentJson.getString("sha")
                        } catch (e: Exception) {
                            Log.e("LoginActivity", "Ошибка извлечения нового SHA: ${e.message}")
                        }
                        statusView.text = "Вход выполнен успешно!"
                        // Сохраняем флаг авторизации
                        val sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
                        with(sharedPref.edit()) {
                            putBoolean("isLoggedIn", true)
                            apply()
                        }
                        // После успешного входа можно перейти на MainActivity
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    } else {
                        statusView.text = "Ошибка ${response.code}: ${response.message}"
                    }
                }
            }
        })
    }
}
