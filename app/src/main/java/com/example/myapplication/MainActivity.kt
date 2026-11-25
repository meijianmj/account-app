package com.example.myapplication

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity() {

    private var lastBackTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnDaily = findViewById<Button>(R.id.btn_daily)
        val btnAdd = findViewById<Button>(R.id.btn_add)
        val btnBalance = findViewById<Button>(R.id.btn_balance)

        replaceFragment(DailyBillFragment())

        btnDaily.setOnClickListener { replaceFragment(DailyBillFragment()) }
        btnAdd.setOnClickListener { replaceFragment(AddRecordFragment()) }
        btnBalance.setOnClickListener { replaceFragment(BalanceFragment()) }

        // 使用 OnBackPressedDispatcher 处理返回
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    val now = System.currentTimeMillis()
                    if (now - lastBackTime < 2000) {
                        finish()
                    } else {
                        lastBackTime = now
                        Toast.makeText(this@MainActivity, "再返回就退出咯\uD83D\uDE18", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}