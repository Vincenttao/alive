package com.alivehealth.alive

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

class GridItemAdapter(context: Context, private val items: Array<String>) :
    ArrayAdapter<String>(context, R.layout.grid_item, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.grid_item, parent, false)

        val textView = view.findViewById<TextView>(R.id.text)
        val imageView = view.findViewById<ImageView>(R.id.image)

        textView.text = items[position]
        imageView.setImageResource(R.drawable.ic_grid) // 替换为您的图片资源

        return view
    }
}