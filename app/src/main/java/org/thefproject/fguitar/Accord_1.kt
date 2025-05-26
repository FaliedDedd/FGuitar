package org.thefproject.fguitar

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class Accord_1 : AppCompatActivity() {
    private val jsonUrl = "https://raw.githubusercontent.com/FaliedDedd/FGuitar/refs/heads/main/json/accords.json"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accord1)

        val titleTextView = findViewById<TextView>(R.id.titleTextView)
        val imageView = findViewById<ImageView>(R.id.imageView)
        val descriptionTextView = findViewById<TextView>(R.id.descriptionTextView)

        fetchSectionData("page_1", titleTextView, imageView, descriptionTextView)
    }

    private fun fetchSectionData(sectionId: String, titleView: TextView, imageView: ImageView, descriptionView: TextView) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://raw.githubusercontent.com/FaliedDedd/FGuitar/main/json/accords.json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ERROR", "Ошибка загрузки JSON: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                responseBody?.let {
                    val jsonObject = JSONObject(it)
                    val pagesArray = jsonObject.getJSONArray("pages")

                    for (i in 0 until pagesArray.length()) {
                        val page = pagesArray.getJSONObject(i)
                        if (page.getString("id") == sectionId) {
                            val title = page.getString("title")
                            val imageUrl = page.getString("image_url")
                            val description = page.getString("description")

                            runOnUiThread {
                                titleView.text = title
                                descriptionView.text = description
                                Glide.with(this@Accord_1).load(imageUrl).into(imageView)
                            }
                            break
                        }
                    }
                }
            }
        })
    }

}
