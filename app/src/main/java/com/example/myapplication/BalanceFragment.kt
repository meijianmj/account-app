package com.example.myapplication

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class BalanceFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AccountAdapter
    private lateinit var fabAdd: ImageButton

    private val iconList = listOf(
        R.drawable.`money`,       // 0 默认
        R.drawable.alipay,     // 1 支付宝
        R.drawable.wechat,     // 2 微信
        R.drawable.bank,       // 3 银行卡
        R.drawable.`red`,        // 4 小红书
        R.drawable.dy          // 5 抖音
    )

    private val nameIconIndexMap = mapOf(
        "支付宝" to 1,
        "微信" to 2,
        "银行卡" to 3,
        "银行" to 3,
        "小红书" to 4,
        "抖音" to 5
    )

    private val expandedParentNames = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_balance, container, false)
        recyclerView = view.findViewById(R.id.rv_accounts)
        fabAdd = view.findViewById(R.id.fab_add_account)

        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = AccountAdapter(buildDisplayList())
        recyclerView.adapter = adapter

        fabAdd.setOnClickListener { showAddTopDialog() }
        return view
    }

    private fun buildDisplayList(): List<Any> {
        val displayList = mutableListOf<Any>()
        val topAccounts = AccountRepository.getTopAccounts()
        topAccounts.forEach { parent ->
            displayList.add(parent)
            if (parent.name in expandedParentNames) {
                val children = AccountRepository.getChildAccounts(parent.name)
                children.forEach { child -> displayList.add(ChildItem(child)) }
                displayList.add(AddChildButton(parent.name))
            }
        }
        return displayList
    }

    private fun refreshData() {
        adapter.updateData(buildDisplayList())
    }

    private fun showAddTopDialog() {
        val builder = AlertDialog.Builder(requireContext())
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_account, null)
        val etName = view.findViewById<EditText>(R.id.et_account_name)
        val etBalance = view.findViewById<EditText>(R.id.et_account_balance)

        builder.setTitle("添加账户")
            .setView(view)
            .setPositiveButton("确定", null)
            .setNegativeButton("取消", null)

        val dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val name = etName.text.toString().trim()
            val balanceStr = etBalance.text.toString().trim()
            if (name.isEmpty() || balanceStr.isEmpty()) {
                Toast.makeText(context, "请填写完整", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (AccountRepository.getTopAccounts().any { it.name == name }) {
                Toast.makeText(context, "该账户已存在", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val balance = balanceStr.toDouble()
            val iconIndex = nameIconIndexMap[name] ?: 0
            AccountRepository.addAccount(Account(name = name, balance = balance, iconIndex = iconIndex))
            refreshData()
            dialog.dismiss()
        }
    }

    private fun showAddChildDialog(parentName: String) {
        val builder = AlertDialog.Builder(requireContext())
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_account, null)
        val etName = view.findViewById<EditText>(R.id.et_account_name)
        val etBalance = view.findViewById<EditText>(R.id.et_account_balance)
        etName.hint = "${parentName}子账户名称"

        builder.setTitle("添加${parentName}子账户")
            .setView(view)
            .setPositiveButton("确定", null)
            .setNegativeButton("取消", null)

        val dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val name = etName.text.toString().trim()
            val balanceStr = etBalance.text.toString().trim()
            if (name.isEmpty() || balanceStr.isEmpty()) {
                Toast.makeText(context, "请填写完整", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (AccountRepository.getChildAccounts(parentName).any { it.name == name }) {
                Toast.makeText(context, "该子账户已存在", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val balance = balanceStr.toDouble()
            val account = Account(name = name, balance = balance, iconIndex = 0, parentName = parentName)
            AccountRepository.addAccount(account)
            refreshData()
            dialog.dismiss()
        }
    }

    private fun showEditAccountDialog(account: Account) {
        val builder = AlertDialog.Builder(requireContext())
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_edit_balance, null)
        val etBalance = view.findViewById<EditText>(R.id.et_edit_balance)
        etBalance.setText(String.format("%.2f", account.balance))

        builder.setTitle("修改余额 - ${account.name}")
            .setView(view)
            .setPositiveButton("确定", null)
            .setNegativeButton("取消", null)

        val dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val newBalanceStr = etBalance.text.toString().trim()
            if (newBalanceStr.isEmpty()) {
                Toast.makeText(context, "请输入余额", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val newBalance = newBalanceStr.toDouble()
            AccountRepository.updateAccount(account.copy(balance = newBalance))
            refreshData()
            dialog.dismiss()
        }
    }

    private fun showDeleteDialog(account: Account) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除账户")
            .setMessage("确定要删除「${account.name}」吗？")
            .setPositiveButton("删除") { _, _ ->
                AccountRepository.deleteAccount(account)
                refreshData()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showParentOptions(account: Account) {
        val options = mutableListOf("添加子账户")
        val hasChildren = AccountRepository.getChildAccounts(account.name).isNotEmpty()
        if (!hasChildren) {
            options.add("编辑余额")
        }
        // 🔥 现在所有顶级账户都可以删除，不再限制银行卡
        options.add("删除账户")

        AlertDialog.Builder(requireContext())
            .setTitle(account.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "添加子账户" -> showAddChildDialog(account.name)
                    "编辑余额" -> showEditAccountDialog(account)
                    "删除账户" -> showDeleteDialog(account)
                }
            }
            .show()
    }

    // --------------- 适配器 ---------------
    inner class AccountAdapter(private var items: List<Any>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_PARENT = 0
        private val TYPE_CHILD = 1
        private val TYPE_ADD_BUTTON = 2

        fun updateData(newItems: List<Any>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is Account -> TYPE_PARENT
                is ChildItem -> TYPE_CHILD
                is AddChildButton -> TYPE_ADD_BUTTON
                else -> throw IllegalArgumentException("Unknown type")
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                TYPE_PARENT -> {
                    val v = LayoutInflater.from(parent.context).inflate(R.layout.item_account_parent, parent, false)
                    ParentVH(v)
                }
                TYPE_CHILD -> {
                    val v = LayoutInflater.from(parent.context).inflate(R.layout.item_account_child, parent, false)
                    ChildVH(v)
                }
                TYPE_ADD_BUTTON -> {
                    val v = LayoutInflater.from(parent.context).inflate(R.layout.item_add_child_button, parent, false)
                    AddButtonVH(v)
                }
                else -> throw IllegalArgumentException("Invalid view type")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            when (holder) {
                is ParentVH -> bindParent(holder, item as Account)
                is ChildVH -> bindChild(holder, (item as ChildItem).account)
                is AddButtonVH -> bindAddButton(holder, item as AddChildButton)
            }
        }

        private fun bindParent(holder: ParentVH, account: Account) {
            holder.tvName.text = account.name
            holder.tvBalance.text = "¥${"%.2f".format(account.balance)}"
            val iconRes = if (account.iconIndex in iconList.indices) iconList[account.iconIndex] else iconList[0]
            holder.ivIcon.setImageResource(iconRes)

            val hasChildren = AccountRepository.getChildAccounts(account.name).isNotEmpty()

            // 箭头显示
            holder.ivExpand.visibility = View.VISIBLE
            holder.ivExpand.setImageResource(
                if (account.name in expandedParentNames) android.R.drawable.arrow_up_float
                else android.R.drawable.arrow_down_float
            )

            // 箭头点击：统一切换展开/收起
            holder.ivExpand.setOnClickListener {
                if (account.name in expandedParentNames) {
                    expandedParentNames.remove(account.name)
                } else {
                    expandedParentNames.add(account.name)
                }
                refreshData()
            }

            // 余额数字点击
            holder.tvBalance.setOnClickListener {
                if (!hasChildren) {
                    showEditAccountDialog(account)
                } else {
                    Toast.makeText(context, "这是汇总哦，不可直接编辑捏", Toast.LENGTH_SHORT).show()
                }
            }

            // 整体 item 点击设为空，避免干扰
            holder.itemView.setOnClickListener(null)
            holder.itemView.isClickable = true

            // 长按
            holder.itemView.setOnLongClickListener {
                showParentOptions(account)
                true
            }
        }

        private fun bindChild(holder: ChildVH, account: Account) {
            holder.tvName.text = account.name
            holder.tvBalance.text = "¥${"%.2f".format(account.balance)}"
            holder.ivIcon.setImageResource(R.drawable.bank)

            holder.itemView.setOnClickListener {
                showEditAccountDialog(account)
            }
            holder.itemView.setOnLongClickListener {
                showDeleteDialog(account)
                true
            }
        }

        private fun bindAddButton(holder: AddButtonVH, addButton: AddChildButton) {
            holder.btnAdd.text = "＋ 添加${addButton.parentName}子账户"
            holder.btnAdd.setOnClickListener {
                showAddChildDialog(addButton.parentName)
            }
        }

        override fun getItemCount() = items.size

        inner class ParentVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivIcon: ImageView = itemView.findViewById(R.id.iv_account_icon)
            val tvName: TextView = itemView.findViewById(R.id.tv_account_name)
            val tvBalance: TextView = itemView.findViewById(R.id.tv_account_balance)
            val ivExpand: ImageView = itemView.findViewById(R.id.iv_expand)
        }

        inner class ChildVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivIcon: ImageView = itemView.findViewById(R.id.iv_child_icon)
            val tvName: TextView = itemView.findViewById(R.id.tv_child_name)
            val tvBalance: TextView = itemView.findViewById(R.id.tv_child_balance)
        }

        inner class AddButtonVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val btnAdd: TextView = itemView.findViewById(R.id.btn_add_child)
        }
    }

    private class ChildItem(val account: Account)
    private class AddChildButton(val parentName: String)
}