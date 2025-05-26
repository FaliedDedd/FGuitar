package org.thefproject.fguitar

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ItemAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        GlobalScope.launch(Dispatchers.IO) {
            val items = api.getData()
            runOnUiThread {
                adapter = ItemAdapter(items) { selectedItem ->
                    val intent = Intent(this@MainActivity, DetailActivity::class.java).apply {
                        putExtra("title", selectedItem.title)
                        putExtra("imageUrl", selectedItem.imageUrl)
                        putExtra("videoUrl", selectedItem.videoUrl)
                    }
                    startActivity(intent)
                }
                recyclerView.adapter = adapter
            }
        }
    }
}
