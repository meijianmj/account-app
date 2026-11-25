package com.example.myapplication

import android.content.Context

object CategoryHelper {
    private const val SP_NAME = "category_data"
    private const val KEY_CUSTOM = "custom_list"

    val defaultEmoji = mapOf(
        "正餐" to "🍚", "零食" to "🍪", "交通" to "🚗", "日用" to "🛒",
        "娱乐" to "🎮", "运动" to "🏃", "服饰" to "👗", "家居" to "🏠",
        "通讯" to "📱", "烟酒" to "🍷", "住房" to "🏡", "缴费" to "💡",
        "人情" to "🎁", "教育" to "📚", "医疗" to "💊", "保险" to "🛡️",
        "宠物" to "🐾", "旅行" to "✈️", "转账" to "💸", "投资" to "📈",
        "购物" to "🛍️", "公益" to "❤️", "养娃" to "👶",
        "工资" to "💰", "奖金" to "🎉", "兼职" to "💼", "生意" to "🏪",
        "理财" to "📊", "生活费" to "🏦"
    )

    fun getExpenseCategories(): List<String> {
        val custom = loadCustomNames()
        return defaultEmoji.keys.filterNot { it in listOf("工资","奖金","兼职","生意","理财","转账","生活费") } + custom + "+"
    }

    fun getIncomeCategories(): List<String> {
        val custom = loadCustomNames()
        return defaultEmoji.keys.filter { it in listOf("工资","奖金","兼职","生意","理财","转账","生活费") } + custom + "+"
    }

    fun getEmojiMap(): Map<String, String> {
        val map = defaultEmoji.toMutableMap()
        loadCustomPairs().forEach { map[it.first] = it.second }
        return map
    }

    fun addCategory(name: String, emoji: String) {
        val list = loadCustomPairs().toMutableList()
        if (list.none { it.first == name }) list.add(Pair(name, emoji))
        saveCustom(list)
    }

    fun isCustomCategory(name: String): Boolean {
        return name !in defaultEmoji.keys && loadCustomNames().contains(name)
    }

    fun removeCategory(name: String) {
        val list = loadCustomPairs().toMutableList()
        list.removeAll { it.first == name }
        saveCustom(list)
    }

    private fun loadCustomNames() = loadCustomPairs().map { it.first }

    private fun loadCustomPairs(): List<Pair<String, String>> {
        if (!MyApplication.isReady()) return emptyList()
        val app = MyApplication.instance
        val sp = app.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_CUSTOM, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { part ->
            val arr = part.split(":")
            if (arr.size == 2) Pair(arr[0], arr[1]) else null
        }
    }

    private fun saveCustom(list: List<Pair<String, String>>) {
        if (!MyApplication.isReady()) return
        val app = MyApplication.instance
        val sp = app.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_CUSTOM, list.joinToString(";") { "${it.first}:${it.second}" }).apply()
    }
}