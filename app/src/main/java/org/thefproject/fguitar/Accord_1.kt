package org.thefproject.fguitar

import android.os.Bundle
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

        fetchJsonData(titleTextView, imageView, descriptionTextView)
    }

    private fun fetchJsonData(titleView: TextView, imageView: ImageView, descriptionView: TextView) {
        val client = OkHttpClient()
        val request = Request.Builder().url(jsonUrl).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let {
                    val jsonObject = JSONObject(it)
                    val title = jsonObject.getString("title")
                    val imageUrl = jsonObject.getString("image_url")
                    val description = jsonObject.getString("description")

                    runOnUiThread {
                        titleView.text = title
                        descriptionView.text = description
                        Glide.with(this@Accord_1).load(imageUrl).into(imageView)
                    }
                }
            }
        })
    }
}
