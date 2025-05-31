package org.thefproject.fguitar

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AccordDetailsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accord_details)

        // Получение данных, переданных через Intent
        val title = intent.getStringExtra("title") ?: "Нет названия"
        val imageUrl = intent.getStringExtra("image_url") ?: ""
        val description = intent.getStringExtra("description") ?: ""

        // Привязка данных к элементам макета
        val titleTextView = findViewById<TextView>(R.id.detailTitle)
        val descriptionTextView = findViewById<TextView>(R.id.detailDescription)
        val imageView = findViewById<ImageView>(R.id.detailImage)
        titleTextView.text = title
        descriptionTextView.text = description

        // Если нужно, добавить загрузку изображения (например, с Glide)
        // Glide.with(this).load(imageUrl).into(imageView)
    }
}
