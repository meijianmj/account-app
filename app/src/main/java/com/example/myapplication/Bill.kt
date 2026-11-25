package com.example.myapplication

enum class BillType {
    EXPENSE, INCOME
}

data class Bill(
    val id: Long = System.currentTimeMillis(),
    val category: String,
    val amount: Double,
    val date: String,
    val type: BillType,
    val remark: String = "",
    val imageUris: List<String> = emptyList(),
    val accountName: String? = null   // 新增：关联的账户名称
)