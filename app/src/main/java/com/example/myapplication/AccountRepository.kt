package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences

object AccountRepository {
    private const val SP_NAME = "account_data"
    private const val KEY_SIZE = "account_size"
    private var accountList = mutableListOf<Account>()

    fun init(context: Context) {
        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val size = sp.getInt(KEY_SIZE, 0)
        accountList.clear()
        for (i in 0 until size) {
            val id = sp.getLong("acc_id_$i", 0)
            val name = sp.getString("acc_name_$i", "") ?: ""
            val balance = java.lang.Double.longBitsToDouble(sp.getLong("acc_balance_$i", 0))
            val iconIndex = sp.getInt("acc_icon_$i", 0)
            val parentName = sp.getString("acc_parent_$i", null)
            accountList.add(Account(id, name, balance, iconIndex, parentName))
        }
        initDefaultAccounts()
    }

    private fun initDefaultAccounts() {
        if (accountList.isEmpty()) {
            val baseTime = System.currentTimeMillis()
            val defaults = listOf(
                Account(id = baseTime, name = "支付宝", balance = 0.0, iconIndex = 1),
                Account(id = baseTime + 1, name = "微信", balance = 0.0, iconIndex = 2),
                Account(id = baseTime + 2, name = "银行卡", balance = 0.0, iconIndex = 3),
                Account(id = baseTime + 3, name = "小红书", balance = 0.0, iconIndex = 4),
                Account(id = baseTime + 4, name = "抖音", balance = 0.0, iconIndex = 5)
            )
            for (acc in defaults) {
                accountList.add(acc)
            }
            save()
        }
    }

    fun getAll(): List<Account> = accountList.toList()

    fun getTopAccounts(): List<Account> = accountList.filter { it.parentName == null }

    fun getChildAccounts(parentName: String): List<Account> =
        accountList.filter { it.parentName == parentName }

    fun addAccount(account: Account) {
        accountList.add(0, account)
        if (account.parentName != null) {
            updateParentBalance(account.parentName)
        }
        save()
    }

    fun updateAccount(account: Account) {
        val index = accountList.indexOfFirst { it.id == account.id }
        if (index != -1) {
            accountList[index] = account
            if (account.parentName != null) {
                updateParentBalance(account.parentName)
            }
            save()
        }
    }

    fun deleteAccount(account: Account) {
        // 删除顶级账户时同时删除其子账户
        if (account.parentName == null) {
            val children = getChildAccounts(account.name)
            for (child in children) {
                accountList.removeAll { it.id == child.id }
            }
        }
        accountList.removeAll { it.id == account.id }
        if (account.parentName != null) {
            updateParentBalance(account.parentName)
        }
        save()
    }

    private fun updateParentBalance(parentName: String) {
        val children = getChildAccounts(parentName)
        val total = children.sumOf { it.balance }
        val parent = accountList.find { it.name == parentName && it.parentName == null }
        if (parent != null) {
            val index = accountList.indexOfFirst { it.id == parent.id }
            accountList[index] = parent.copy(balance = total)
        }
    }

    private fun save() {
        val sp = MyApplication.instance.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        sp.edit().apply {
            putInt(KEY_SIZE, accountList.size)
            accountList.forEachIndexed { i, acc ->
                putLong("acc_id_$i", acc.id)
                putString("acc_name_$i", acc.name)
                putLong("acc_balance_$i", java.lang.Double.doubleToRawLongBits(acc.balance))
                putInt("acc_icon_$i", acc.iconIndex)
                putString("acc_parent_$i", acc.parentName)
            }
            apply()
        }
    }
}