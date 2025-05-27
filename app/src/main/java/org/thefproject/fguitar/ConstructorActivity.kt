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
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

class ConstructorActivity : AppCompatActivity() {
    private val githubApiUrl = "https://api.github.com/repos/FaliedDedd/FGuitar/contents/json/accords.json"
    private val apiKey = "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"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_constructor)

        val titleEditText = findViewById<EditText>(R.id.titleEditText)
        val imageUrlEditText = findViewById<EditText>(R.id.imageUrlEditText)
        val descriptionEditText = findViewById<EditText>(R.id.descriptionEditText)
        val addButton = findViewById<Button>(R.id.addButton)
        val statusTextView = findViewById<TextView>(R.id.statusTextView)

        addButton.setOnClickListener {
            val title = titleEditText.text.toString()
            val imageUrl = imageUrlEditText.text.toString()
            val description = descriptionEditText.text.toString()

            if (title.isNotEmpty() && imageUrl.isNotEmpty() && description.isNotEmpty()) {
                addToJson(title, imageUrl, description, statusTextView)
            }
        }
    }

    private fun addToJson(title: String, imageUrl: String, description: String, statusView: TextView) {
        statusView.text = "Запрос SHA для обновления..."
        statusView.setTextColor(android.graphics.Color.BLUE)

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        // Запрос SHA файла для обновления
        val shaRequest = Request.Builder()
            .url(githubApiUrl)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        client.newCall(shaRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    statusView.text = "Ошибка SHA: ${e.message}"
                    statusView.setTextColor(android.graphics.Color.RED)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody == null) {
                    runOnUiThread {
                        statusView.text = "Ошибка получения SHA: ${response.code} ${response.message}"
                        statusView.setTextColor(android.graphics.Color.RED)
                    }
                    return
                }

                val jsonObject = JSONObject(responseBody)
                val sha = jsonObject.optString("sha", null) // Проверяем, есть ли SHA

                if (sha == null) {
                    runOnUiThread {
                        statusView.text = "Ошибка: SHA отсутствует!"
                        statusView.setTextColor(android.graphics.Color.RED)
                    }
                    return
                }

                Log.d("DEBUG", "SHA файла: $sha")

                // Проверка на наличие ключа "pages"
                if (!jsonObject.has("pages")) {
                    jsonObject.put("pages", JSONArray()) // Если "pages" отсутствует, создаём его
                }

                val pagesArray = jsonObject.getJSONArray("pages")

                // Получаем последний ID и увеличиваем его
                val lastPageId = if (pagesArray.length() > 0) {
                    pagesArray.getJSONObject(pagesArray.length() - 1).getString("id")
                } else {
                    "page_0"
                }
                val newPageNumber = lastPageId.removePrefix("page_").toInt() + 1
                val newPageId = "page_$newPageNumber"

                val newEntry = JSONObject().apply {
                    put("id", newPageId)
                    put("title", title)
                    put("image_url", imageUrl)
                    put("description", description)
                }

                pagesArray.put(newEntry)

                val updatedJson = jsonObject.toString(4)
                val encodedJson = Base64.getEncoder().encodeToString(updatedJson.toByteArray())

                Log.d("DEBUG", "Обновлённый JSON: $updatedJson") // Логируем перед отправкой

                val updateRequestBody = JSONObject().apply {
                    put("message", "Добавлен новый раздел")
                    put("content", encodedJson)
                    put("sha", sha)
                }

                val updateRequest = Request.Builder()
                    .url(githubApiUrl)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Accept", "application/vnd.github.v3+json")
                    .put(RequestBody.create("application/json".toMediaType(), updateRequestBody.toString()))
                    .build()

                client.newCall(updateRequest).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        runOnUiThread {
                            statusView.text = "Ошибка обновления JSON: ${e.message}"
                            statusView.setTextColor(android.graphics.Color.RED)
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        runOnUiThread {
                            if (response.isSuccessful) {
                                statusView.text = "Данные успешно добавлены!"
                                statusView.setTextColor(android.graphics.Color.GREEN)
                            } else {
                                statusView.text = "Ошибка ${response.code}: ${response.message}"
                                statusView.setTextColor(android.graphics.Color.RED)
                            }
                        }
                    }
                })
            }
        })
    }
}
