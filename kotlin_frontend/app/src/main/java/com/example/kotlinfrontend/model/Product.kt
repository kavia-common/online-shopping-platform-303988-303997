package com.example.kotlinfrontend.model

data class Product(
    val id: String,
    val title: String,
    val description: String,
    val priceCents: Int
) {
    fun priceText(): String = "$" + String.format("%.2f", priceCents / 100.0)
}
