package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences

object BillRepository {
    private const val SP_NAME = "bill_data"
    private const val BILL_LIST_KEY = "bill_list"
    private var billList: MutableList<Bill> = mutableListOf()

    fun init(context: Context) {
        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val size = sp.getInt("${BILL_LIST_KEY}_size", 0)
        billList.clear()
        for (i in 0 until size) {
            val id = sp.getLong("${BILL_LIST_KEY}_id_$i", 0)
            val category = sp.getString("${BILL_LIST_KEY}_category_$i", "") ?: ""
            val amount = sp.getFloat("${BILL_LIST_KEY}_amount_$i", 0f).toDouble()
            val date = sp.getString("${BILL_LIST_KEY}_date_$i", "") ?: ""
            val type = if (sp.getBoolean("${BILL_LIST_KEY}_isExpense_$i", true)) {
                BillType.EXPENSE
            } else {
                BillType.INCOME
            }
            val remark = sp.getString("${BILL_LIST_KEY}_remark_$i", "") ?: ""
            val imageStr = sp.getString("${BILL_LIST_KEY}_images_$i", "") ?: ""
            val imageUris = if (imageStr.isEmpty()) emptyList() else imageStr.split("\n")
            val accountName = sp.getString("${BILL_LIST_KEY}_accountName_$i", null)
            billList.add(Bill(id, category, amount, date, type, remark, imageUris, accountName))
        }
    }

    fun getAllBills(): List<Bill> = billList.reversed()

    fun addBill(bill: Bill) {
        billList.add(0, bill)
        // 更新关联账户余额
        bill.accountName?.let { accountName ->
            val accounts = AccountRepository.getAll()
            val account = accounts.find { it.name == accountName }
            if (account != null) {
                // 支出时 amount 为负，收入时 amount 为正，直接加减
                val newBalance = account.balance + bill.amount
                AccountRepository.updateAccount(account.copy(balance = newBalance))
            }
        }
        saveToDisk()
    }

    fun deleteBill(bill: Bill) {
        billList.removeIf { it.id == bill.id }
        // 恢复余额（减去原来的变化量）
        bill.accountName?.let { accountName ->
            val accounts = AccountRepository.getAll()
            val account = accounts.find { it.name == accountName }
            if (account != null) {
                val newBalance = account.balance - bill.amount
                AccountRepository.updateAccount(account.copy(balance = newBalance))
            }
        }
        saveToDisk()
    }

    fun updateBill(updated: Bill) {
        val old = billList.firstOrNull { it.id == updated.id }
        val index = billList.indexOfFirst { it.id == updated.id }
        if (index != -1) {
            // 撤销旧账单的余额影响
            old?.accountName?.let { oldName ->
                val accounts = AccountRepository.getAll()
                val oldAccount = accounts.find { it.name == oldName }
                if (oldAccount != null) {
                    val revertedBalance = oldAccount.balance - old.amount
                    AccountRepository.updateAccount(oldAccount.copy(balance = revertedBalance))
                }
            }
            // 更新账单
            billList[index] = updated
            // 应用新账单的余额影响
            updated.accountName?.let { newName ->
                val accounts = AccountRepository.getAll()
                val newAccount = accounts.find { it.name == newName }
                if (newAccount != null) {
                    val newBalance = newAccount.balance + updated.amount
                    AccountRepository.updateAccount(newAccount.copy(balance = newBalance))
                }
            }
            saveToDisk()
        }
    }

    private fun saveToDisk() {
        if (!MyApplication.isReady()) return
        val context = MyApplication.instance
        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sp.edit()
        editor.putInt("${BILL_LIST_KEY}_size", billList.size)
        for ((index, bill) in billList.withIndex()) {
            editor.putLong("${BILL_LIST_KEY}_id_$index", bill.id)
            editor.putString("${BILL_LIST_KEY}_category_$index", bill.category)
            editor.putFloat("${BILL_LIST_KEY}_amount_$index", bill.amount.toFloat())
            editor.putString("${BILL_LIST_KEY}_date_$index", bill.date)
            editor.putBoolean("${BILL_LIST_KEY}_isExpense_$index", bill.type == BillType.EXPENSE)
            editor.putString("${BILL_LIST_KEY}_remark_$index", bill.remark)
            editor.putString("${BILL_LIST_KEY}_images_$index", bill.imageUris.joinToString("\n"))
            editor.putString("${BILL_LIST_KEY}_accountName_$index", bill.accountName)
        }
        editor.apply()
    }
}