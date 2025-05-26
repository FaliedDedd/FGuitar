package org.thefproject.fguitar

import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class Accord_1 : AppCompatActivity() {

    private val jsonUrl = "https://api.github.com/repos/your-username/your-private-repo/contents/test.json"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accord1)

        val titleTextView = findViewById<TextView>(R.id.titleTextView)
        val imageView = findViewById<ImageView>(R.id.imageView)
        val descriptionTextView = findViewById<TextView>(R.id.descriptionTextView)

        fetchJsonData(titleTextView, imageView, descriptionTextView)
    }

    private fun fetchJsonData(titleView: TextView, imageView: ImageView, descriptionView: TextView) {
        val apiKey = BuildConfig.GITHUB_API_KEY

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(jsonUrl)
            .header("Authorization", "token $apiKey") // Используем API-ключ для доступа
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ERROR", "Ошибка загрузки JSON: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("SERVER_RESPONSE", responseBody ?: "Пустой ответ")

                responseBody?.let {
                    try {
                        val jsonObject = JSONObject(it)
                        val title = jsonObject.getString("title")
                        val imageUrl = jsonObject.getString("image_url")
                        val description = jsonObject.getString("description")

                        runOnUiThread {
                            titleView.text = title
                            descriptionView.text = description
                            Glide.with(this@Accord_1).load(imageUrl).into(imageView)
                        }
                    } catch (e: Exception) {
                        Log.e("ERROR", "Ошибка обработки JSON: ${e.message}")
                    }
                }
            }
        })
    }
}
