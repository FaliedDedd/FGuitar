package org.thefproject.fguitar

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class AccordsListActivity : AppCompatActivity() {

    // URL для получения файла accord.json из GitHub
    private val githubUrl = "https://api.github.com/repos/FaliedDedd/FGuitar/contents/json/accords.json"
    // Токен GitHub – укажите ваш токен без префикса "Bearer"
    private val apiToken = "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"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accords_list)

        // При запуске загружаем данные с GitHub
        fetchAccordsJson { jsonString ->
            runOnUiThread {
                if (jsonString != null) {
                    populateAccordsWithJson(jsonString)
                } else {
                    // В случае ошибки выводим сообщение
                    val container = findViewById<LinearLayout>(R.id.linearLayoutContainer)
                    val errorText = TextView(this)
                    errorText.text = "Ошибка загрузки аккордов"
                    container.addView(errorText)
                }
            }
        }
    }


    private fun fetchAccordsJson(callback: (String?) -> Unit) {
        val urlWithTimestamp = "$githubUrl?t=" + System.currentTimeMillis()
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(urlWithTimestamp)
            .header("Authorization", "Bearer $apiToken")
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("AccordsListActivity", "Ошибка запроса: ${e.message}")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("AccordsListActivity", "Ошибка запроса: Code ${response.code}")
                    callback(null)
                    return
                }
                val bodyString = response.body?.string() ?: ""
                try {
                    val jsonResponse = JSONObject(bodyString)
                    // Содержимое файла находится в поле "content" и закодировано в Base64
                    val contentBase64 = jsonResponse.getString("content")
                    // Удаляем все пробельные символы, включая переводы строки
                    val cleanedContent = contentBase64.replace("\\s".toRegex(), "")
                    // Декодируем – используем android.util.Base64
                    val decodedBytes = Base64.decode(cleanedContent, Base64.DEFAULT)
                    val decodedJson = String(decodedBytes, StandardCharsets.UTF_8)
                    callback(decodedJson)
                } catch (e: Exception) {
                    Log.e("AccordsListActivity", "Ошибка декодирования: ${e.message}")
                    callback(null)
                }
            }
        })
    }


    private fun populateAccordsWithJson(jsonString: String) {
        try {
            val jsonObject = JSONObject(jsonString)
            val pagesArray: JSONArray = jsonObject.optJSONArray("pages") ?: JSONArray()
            val container = findViewById<LinearLayout>(R.id.linearLayoutContainer)
            container.removeAllViews()

            for (i in 0 until pagesArray.length()) {
                val page = pagesArray.getJSONObject(i)
                val title = page.optString("title", "Без названия")
                val imageUrl = page.optString("image_url", "")
                val description = page.optString("description", "")

                // Инфлейтим карточку из разметки item_accord.xml
                val itemView = layoutInflater.inflate(R.layout.item_accord, container, false)
                val titleTextView = itemView.findViewById<TextView>(R.id.textViewTitle)
                titleTextView.text = title

                // При нажатии на кнопку "Открыть" запустится AccordDetailsActivity с данными
                val openButton = itemView.findViewById<Button>(R.id.buttonOpen)
                openButton.setOnClickListener {
                    val intent = Intent(this@AccordsListActivity, AccordDetailsActivity::class.java)
                    intent.putExtra("title", title)
                    intent.putExtra("image_url", imageUrl)
                    intent.putExtra("description", description)
                    startActivity(intent)
                }
                container.addView(itemView)
            }
        } catch (e: Exception) {
            Log.e("AccordsListActivity", "Ошибка парсинга JSON: ${e.message}")
        }
    }
}
