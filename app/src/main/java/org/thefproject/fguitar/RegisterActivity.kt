package org.thefproject.fguitar

import android.os.Bundle
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
import java.util.Base64
import java.util.concurrent.TimeUnit

class RegisterActivity : AppCompatActivity() {

    private val baseGithubApiUrl = "https://api.github.com/repos/FaliedDedd/FGuitar/contents/json/users.json"
    private val apiKey = "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"
    private var sha: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val usernameEditText = findViewById<EditText>(R.id.regUsernameEditText)
        val emailEditText = findViewById<EditText>(R.id.regEmailEditText)
        val passwordEditText = findViewById<EditText>(R.id.regPasswordEditText)
        val registerButton = findViewById<Button>(R.id.registerButton)
        val statusTextView = findViewById<TextView>(R.id.regStatusTextView)

        registerButton.setOnClickListener {
            val username = usernameEditText.text.toString().trim()
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (username.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty()) {
                performRegistration(username, email, password, statusTextView)
            } else {
                statusTextView.text = "Заполните все поля!"
            }
        }
    }

    // Добавляем параметр времени для обхода кеша
    private fun getGithubUrl(): String {
        return "$baseGithubApiUrl?t=" + System.currentTimeMillis()
    }

    // Выполняем GET-запрос для получения файла users.json с GitHub
    private fun performRegistration(
        username: String,
        email: String,
        password: String,
        statusView: TextView
    ) {
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
                Log.d("DEBUG", "GET ответ: $responseBody")
                val jsonResponse = JSONObject(responseBody)
                // Сохраняем актуальный SHA файла
                sha = jsonResponse.optString("sha", null)

                // Получаем кодированное содержимое файла (Base64)
                val contentBase64 = jsonResponse.optString("content", "")
                // Удаляем пробельные символы (переводы строк, пробелы и т.д.)
                val cleanedContent = contentBase64.replace("\\s".toRegex(), "")
                val contentJson = try {
                    String(Base64.getDecoder().decode(cleanedContent))
                } catch (e: IllegalArgumentException) {
                    Log.e("DEBUG", "Ошибка декодирования Base64: ${e.message}")
                    "{}"
                }

                // Сохраняем полученные данные во временный файл "users_temp.json"
                saveJsonToTemp(contentJson, "users_temp.json")
                processRegistration(username, email, password, statusView)
            }
        })
    }

    // Сохраняем строку JSON во временный файл с указанным именем
    private fun saveJsonToTemp(jsonContent: String, fileName: String) {
        val tempFile = File(cacheDir, fileName)
        FileOutputStream(tempFile).use { it.write(jsonContent.toByteArray()) }
    }

    // Читаем содержимое временного файла (users_temp.json)
    private fun readJsonFromTemp(fileName: String): String {
        val tempFile = File(cacheDir, fileName)
        return if (tempFile.exists()) {
            FileInputStream(tempFile).use { it.readBytes().toString(Charsets.UTF_8) }
        } else {
            "{}"
        }
    }

    // Обрабатываем JSON, проверяем, существует ли уже пользователь; если нет – добавляем нового
    private fun processRegistration(
        username: String,
        email: String,
        password: String,
        statusView: TextView
    ) {
        val jsonContent = readJsonFromTemp("users_temp.json")
        val jsonObject = try {
            JSONObject(jsonContent)
        } catch (e: Exception) {
            Log.e("DEBUG", "Ошибка парсинга JSON: ${e.message}")
            JSONObject()
        }

        // Получаем массив пользователей; если массив отсутствует, создаём новый
        val usersArray = jsonObject.optJSONArray("users") ?: JSONArray()
        Log.d("DEBUG", "Текущий usersArray: $usersArray")

        // Проверяем, существует ли пользователь с таким логином
        for (i in 0 until usersArray.length()) {
            val user = usersArray.getJSONObject(i)
            if (user.optString("username") == username) {
                runOnUiThread {
                    statusView.text = "Пользователь с таким логином уже существует"
                }
                return
            }
        }

        // Если пользователь не найден, создаём новый объект пользователя
        val newUser = JSONObject().apply {
            put("username", username)
            put("email", email)
            put("password", password)
            // Изначально массив logins пустой
            put("logins", JSONArray())
        }

        // Добавляем нового пользователя в массив
        usersArray.put(newUser)
        jsonObject.put("users", usersArray)
        Log.d("DEBUG", "Обновленный JSON перед отправкой: $jsonObject")

        val updatedJson = jsonObject.toString(4)
        // Сохраняем обновлённое содержимое локально
        saveJsonToTemp(updatedJson, "users_temp.json")
        sendJsonToGithub(updatedJson, statusView)
    }

    // Отправляем обновлённый JSON (users.json) на GitHub с актуальным SHA
    private fun sendJsonToGithub(updatedJson: String, statusView: TextView) {
        val encodedJson = Base64.getEncoder().encodeToString(updatedJson.toByteArray())
        val client = OkHttpClient()

        val requestBody = JSONObject().apply {
            put("message", "Зарегистрирован новый пользователь: $updatedJson")
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
                        Log.d("DEBUG", "PUT ответ: $resBody")
                        try {
                            val jsonRes = JSONObject(resBody)
                            // Извлекаем новый SHA для последующих обновлений
                            val contentJson = jsonRes.getJSONObject("content")
                            sha = contentJson.getString("sha")
                        } catch (e: Exception) {
                            Log.e("DEBUG", "Ошибка извлечения нового SHA: ${e.message}")
                        }
                        statusView.text = "Регистрация прошла успешно!"
                    } else {
                        statusView.text = "Ошибка ${response.code}: ${response.message}"
                    }
                }
            }
        })
    }
}
