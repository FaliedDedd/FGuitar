package org.thefproject.fguitar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
import java.util.UUID
import java.util.concurrent.TimeUnit
import android.util.Base64 as AndroidBase64

class ConstructorActivity : AppCompatActivity() {

    // URL для JSON-файла с данными (например, аккорды) в репозитории GitHub
    private val baseGithubApiUrl = "https://api.github.com/repos/FaliedDedd/FGuitar/contents/json/accords.json"
    // GitHub API токен (без префикса "Bearer")
    private val apiKey = "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"
    private var sha: String? = null

    // URI выбранного изображения из галереи
    private var selectedImageUri: Uri? = null

    // Лаунчер для выбора изображения через Activity Result API
    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_constructor)

        // Регистрируем лаунчер для выбора изображения
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                selectedImageUri = uri
                Log.d("ConstructorActivity", "Выбрано изображение: $uri")
            }
        }

        // В разметке остаются только поля для заголовка и описания
        val titleEditText = findViewById<EditText>(R.id.titleEditText)
        val descriptionEditText = findViewById<EditText>(R.id.descriptionEditText)
        val addButton = findViewById<Button>(R.id.addButton)
        val chooseImageButton = findViewById<Button>(R.id.addButton2)
        val statusTextView = findViewById<TextView>(R.id.statusTextView)

        chooseImageButton.setOnClickListener {
            // Открываем галерею для выбора изображения
            imagePickerLauncher.launch("image/*")
        }

        addButton.setOnClickListener {
            val title = titleEditText.text.toString().trim()
            val description = descriptionEditText.text.toString().trim()

            // Проверяем, что заполнены заголовок, описание и выбрано изображение из галереи
            if (title.isNotEmpty() && description.isNotEmpty() && selectedImageUri != null) {
                // Загружаем выбранное изображение на GitHub
                uploadImageToGitHub(selectedImageUri!!) { uploadedImageUrl ->
                    runOnUiThread {
                        if (uploadedImageUrl != null && uploadedImageUrl.isNotEmpty()) {
                            // Используем полученный URL изображения для обновления JSON файла
                            fetchJsonAndUpdate(title, uploadedImageUrl, description, statusTextView)
                        } else {
                            statusTextView.text = "Ошибка загрузки изображения"
                        }
                    }
                }
            } else {
                statusTextView.text = "Заполните заголовок, описание и выберите изображение!"
            }
        }
    }

    // Формирует URL с параметром времени для обхода кеша
    private fun getGithubUrl(): String {
        return "$baseGithubApiUrl?t=" + System.currentTimeMillis()
    }

    // Получает JSON с GitHub, декодирует содержимое и сохраняет его в временный файл,
    // затем вызывает обновление (processJson)
    private fun fetchJsonAndUpdate(title: String, imageUrl: String, description: String, statusTextView: TextView) {
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
                    statusTextView.text = "Ошибка загрузки JSON: ${e.message}"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody == null) {
                    runOnUiThread {
                        statusTextView.text = "Ошибка загрузки JSON: ${response.code} ${response.message}"
                    }
                    return
                }
                Log.d("DEBUG", "GET ответ: $responseBody")
                val jsonResponse = JSONObject(responseBody)
                // Извлекаем актуальный SHA файла
                sha = jsonResponse.optString("sha", null)

                // Декодируем содержимое (Base64)
                val contentBase64 = jsonResponse.optString("content", "")
                val cleanedContent = contentBase64.replace("\\s".toRegex(), "")
                val contentJson = try {
                    String(Base64.getDecoder().decode(cleanedContent))
                } catch (e: IllegalArgumentException) {
                    Log.e("DEBUG", "Ошибка декодирования Base64: ${e.message}")
                    "{}"
                }
                // Сохраняем JSON во временный файл
                saveJsonToTemp(contentJson)
                processJson(title, imageUrl, description, statusTextView)
            }
        })
    }

    // Сохраняет JSON-строку во временный файл
    private fun saveJsonToTemp(jsonContent: String) {
        val tempFile = File(cacheDir, "temp.json")
        FileOutputStream(tempFile).use { it.write(jsonContent.toByteArray()) }
    }

    // Читает JSON из временного файла
    private fun readJsonFromTemp(): String {
        val tempFile = File(cacheDir, "temp.json")
        return if (tempFile.exists()) {
            FileInputStream(tempFile).use { it.readBytes().toString(Charsets.UTF_8) }
        } else {
            "{}"
        }
    }

    // Обработка JSON: добавление нового элемента в массив "pages" с данными и отметкой пользователя,
    // затем вызов отправки обновлённого JSON на GitHub
    private fun processJson(title: String, imageUrl: String, description: String, statusTextView: TextView) {
        val jsonContent = readJsonFromTemp()
        val jsonObject = try {
            JSONObject(jsonContent)
        } catch (e: Exception) {
            Log.e("DEBUG", "Ошибка парсинга JSON: ${e.message}")
            JSONObject()
        }

        // Получаем массив "pages" или создаём новый, если его нет
        val pagesArray = jsonObject.optJSONArray("pages") ?: JSONArray()
        Log.d("DEBUG", "Текущий pagesArray: $pagesArray")

        // Генерируем новый id для раздела (например, "page_1", "page_2", ...)
        val newPageId = if (pagesArray.length() == 0) {
            "page_1"
        } else {
            val lastEntry = pagesArray.getJSONObject(pagesArray.length() - 1)
            val lastNumber = lastEntry.optString("id", "page_0").substringAfter("_").toIntOrNull() ?: 0
            "page_${lastNumber + 1}"
        }

        // Получаем имя текущего пользователя (предполагается, что оно сохранено в SharedPreferences)
        val sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val username = sharedPref.getString("username", "unknown")

        // Формируем новый JSON-объект раздела с переданными данными
        val newEntry = JSONObject().apply {
            put("id", newPageId)
            put("title", title)
            put("image_url", imageUrl)
            put("description", description)
            put("uploaded_by", username)
        }

        pagesArray.put(newEntry)
        jsonObject.put("pages", pagesArray)
        Log.d("DEBUG", "Обновленный JSON перед отправкой: $jsonObject")

        val updatedJson = jsonObject.toString(4)
        saveJsonToTemp(updatedJson)
        sendJsonToGithub(updatedJson, statusTextView)
    }

    // Отправляет обновлённый JSON на GitHub методом PUT, используя текущий SHA
    private fun sendJsonToGithub(updatedJson: String, statusTextView: TextView) {
        val encodedJson = Base64.getEncoder().encodeToString(updatedJson.toByteArray())
        val client = OkHttpClient()

        val requestBody = JSONObject().apply {
            put("message", "Добавлен новый раздел")
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
                    statusTextView.text = "Ошибка обновления JSON: ${e.message}"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val resBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful) {
                        Log.d("DEBUG", "PUT ответ: $resBody")
                        try {
                            val jsonRes = JSONObject(resBody)
                            val contentJson = jsonRes.getJSONObject("content")
                            sha = contentJson.getString("sha")
                        } catch (e: Exception) {
                            Log.e("DEBUG", "Ошибка извлечения нового SHA: ${e.message}")
                        }
                        statusTextView.text = "Данные успешно добавлены!"
                    } else {
                        statusTextView.text = "Ошибка ${response.code}: ${response.message}"
                    }
                }
            }
        })
    }


    private fun uploadImageToGitHub(uri: Uri, callback: (String?) -> Unit) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                callback(null)
                return
            }
            val imageBytes = inputStream.readBytes()
            inputStream.close()

            // Кодируем изображение в Base64 без переносов строк
            val base64Image = AndroidBase64.encodeToString(imageBytes, AndroidBase64.NO_WRAP)

            // Получаем имя пользователя для формирования пути
            val sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
            val username = sharedPref.getString("username", "unknown")
            // Генерируем случайное имя файла с расширением .jpg
            val randomFileName = "${UUID.randomUUID()}.jpg"
            // Формируем путь в репозитории: images/<username>/<randomFileName>
            val imagePath = "images/$username/$randomFileName"
            val uploadUrl = "https://api.github.com/repos/FaliedDedd/FGuitar/contents/$imagePath"

            val requestBodyJson = JSONObject().apply {
                put("message", "Upload image $imagePath")
                put("content", base64Image)
            }
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(uploadUrl)
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/vnd.github.v3+json")
                .put(RequestBody.create("application/json".toMediaType(), requestBodyJson.toString()))
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("ConstructorActivity", "Ошибка загрузки изображения: ${e.message}")
                    callback(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        Log.e("ConstructorActivity", "Ошибка загрузки изображения, код: ${response.code}")
                        callback(null)
                        return
                    }
                    val resBody = response.body?.string()
                    try {
                        val jsonRes = JSONObject(resBody)
                        val contentJson = jsonRes.getJSONObject("content")
                        val downloadUrl = contentJson.getString("download_url")


                        val formattedDownloadUrl = if (downloadUrl.contains("/FGuitar/main/")) {
                            downloadUrl.replace("/FGuitar/main/", "/FGuitar/refs/heads/main/")
                        } else {
                            downloadUrl
                        }

                        Log.d("ConstructorActivity", "Изображение успешно загружено: $formattedDownloadUrl")
                        callback(formattedDownloadUrl)
                    } catch (e: Exception) {
                        Log.e("ConstructorActivity", "Ошибка извлечения download_url: ${e.message}")
                        callback(null)
                    }
                }
            })

        } catch (e: Exception) {
            Log.e("ConstructorActivity", "Ошибка чтения изображения: ${e.message}")
            callback(null)
        }
    }

}
