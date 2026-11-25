package com.example.myapplication

data class Account(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val balance: Double,
    val iconIndex: Int = 0,
    val parentName: String? = null   // null 表示顶级账户，非 null 表示子账户对应的父账户名称
)