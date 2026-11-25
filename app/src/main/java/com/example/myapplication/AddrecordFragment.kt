package com.example.myapplication

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.fragment.app.Fragment
import kotlin.math.abs
import kotlin.math.min

import com.alibaba.dashscope.aigc.generation.Generation
import com.alibaba.dashscope.aigc.generation.GenerationParam
import com.alibaba.dashscope.aigc.generation.GenerationResult
import com.alibaba.dashscope.common.Message
import com.alibaba.dashscope.common.Role
import com.alibaba.dashscope.exception.NoApiKeyException
import kotlinx.coroutines.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
class AddRecordFragment : Fragment() {
    /**
     * 根据大模型返回的账户描述，匹配到 App 中已有的账户并设置图标
     * @param accountDesc 大模型识别的账户名（如“支付宝”、“微信”）
     * @param subDesc 大模型识别的子账户（如“花呗”、“余额宝”）
     */
    private fun autoSetAccount(accountDesc: String?, subDesc: String?) {
        val cleanDesc = (accountDesc ?: "").replace("支付", "").trim()
        if (selectedAccountName != null) {
            // 如果用户已经手动选过账户，则不自动覆盖（也可以询问）
            return
        }
        if (accountDesc.isNullOrBlank()) {
            // 没有识别出支付方式，保持当前选择不变
            return
        }

        // 1. 先尝试匹配父账户（模糊匹配关键词）
        val topAccounts = AccountRepository.getTopAccounts() ?: emptyList()
        var matchedParent: String? = null
        for (parent in topAccounts) {
            if (accountDesc.contains(parent.name, ignoreCase = true) ||
                parent.name.contains(accountDesc, ignoreCase = true)) {
                matchedParent = parent.name
                break
            }
        }
        // 关键词补充匹配（大模型可能返回“支付宝”但你的账户叫“支付宝”）
        if (matchedParent == null) {
            // 简单硬匹配常见别名
            val keyMapping = mapOf(
                "支付宝" to "支付宝",
                "alipay" to "支付宝",
                "微信" to "微信",
                "wechat" to "微信",
                "银行卡" to "银行卡",
                "银行" to "银行卡",
                "小红书" to "小红书",
                "抖音" to "抖音"
            )
            for ((key, value) in keyMapping) {
                if (accountDesc.contains(key, ignoreCase = true)) {
                    matchedParent = value
                    break
                }
            }
        }

        // 如果没匹配到任何父账户，放弃自动设置
        if (matchedParent == null) return

        // 2. 检查该父账户下是否有子账户，并尝试匹配子账户
        val children = AccountRepository.getChildAccounts(matchedParent) ?: emptyList()
        if (children.isNotEmpty() && !subDesc.isNullOrBlank()) {
            // 尝试在子账户列表中模糊匹配 subDesc
            for (child in children) {
                if (subDesc.contains(child.name, ignoreCase = true) ||
                    child.name.contains(subDesc, ignoreCase = true)) {
                    selectedAccountName = child.name
                    updateAccountIndicator(matchedParent) // 显示父账户图标
                    toast("已自动关联：${child.name}")
                    return
                }
            }
        }

        // 3. 没有匹配到子账户，直接使用父账户
        selectedAccountName = matchedParent
        updateAccountIndicator(matchedParent)
        toast("已自动关联：$matchedParent")
    }
    // 放在其他成员变量附近（与 BalanceFragment 保持一致）
    private lateinit var btnAccount: Button          // 关联账户按钮
    private lateinit var ivAccountIndicator: ImageView // 账户图标
    private var selectedAccountName: String? = null     // 当前选中的账户，null=不关联
    private val accountIconList = listOf(
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
    private lateinit var btnOcr: ImageButton
    private lateinit var tvCancel: TextView
    private lateinit var tabExpense: TextView
    private lateinit var tabIncome: TextView
    private lateinit var categoryContainer: LinearLayout
    private lateinit var etAmount: EditText
    private lateinit var etRemark: EditText
    private lateinit var ivAddImg: ImageView
    private lateinit var llImgContainer: LinearLayout
    private lateinit var imgZoomOverlay: FrameLayout
    private lateinit var ivZoom: ImageView
    private lateinit var btnDate: Button
    private lateinit var btnTime: Button
    private lateinit var tvDone: TextView

    private var selectCategory: String? = null
    private var lastSelectedItemLayout: LinearLayout? = null
    private var isExpenseMode = true
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val REQUEST_CODE_IMG = 1001
    private val selectedImageUris = ArrayList<Uri>()
    /**
     * 根据当前选中的账户名更新按钮旁边的图标
     * @param accountName 账户名，null 或空字符串表示隐藏图标
     */
    private fun clearAllInputs() {
        etAmount.setText("")
        etRemark.setText("")
        selectCategory = null
        selectedImageUris.clear()
        selectedAccountName = null
        updateAccountIndicator(null)

        val now = Calendar.getInstance()
        btnDate.text = String.format(
            Locale.getDefault(), "%d-%02d-%02d",
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1,
            now.get(Calendar.DAY_OF_MONTH)
        )
        btnTime.text = timeFormat.format(now.time)

        updateCategoryUI()
        refreshImageList()
        checkDoneEnable()
        toast("已清空")
    }
    // 保存状态
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("et_amount", etAmount.text.toString())
        outState.putString("et_remark", etRemark.text.toString())
        outState.putString("btn_date", btnDate.text.toString())
        outState.putString("btn_time", btnTime.text.toString())
        outState.putBoolean("is_expense_mode", isExpenseMode)
        outState.putString("select_category", selectCategory)
        outState.putString("selected_account_name", selectedAccountName)
        outState.putStringArrayList("selected_image_uris", ArrayList(selectedImageUris.map { it.toString() }))
    }
    private fun updateAccountIndicator(accountName: String?) {
        if (accountName.isNullOrBlank()) {
            ivAccountIndicator.visibility = View.GONE
            return
        }
        val iconIndex = nameIconIndexMap[accountName] ?: 0
        val iconRes = accountIconList.getOrNull(iconIndex) ?: accountIconList[0]
        ivAccountIndicator.setImageResource(iconRes)
        ivAccountIndicator.visibility = View.VISIBLE
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_add_record, container, false)
        initView(view)
        initListener()

        if (savedInstanceState != null) {
            // 恢复之前保存的数据
            etAmount.setText(savedInstanceState.getString("et_amount"))
            etRemark.setText(savedInstanceState.getString("et_remark"))
            btnDate.text = savedInstanceState.getString("btn_date")
            btnTime.text = savedInstanceState.getString("btn_time")
            isExpenseMode = savedInstanceState.getBoolean("is_expense_mode")
            selectCategory = savedInstanceState.getString("select_category")
            selectedAccountName = savedInstanceState.getString("selected_account_name")
            val uris = savedInstanceState.getStringArrayList("selected_image_uris")
            selectedImageUris.clear()
            if (uris != null) {
                for (uriStr in uris) {
                    selectedImageUris.add(Uri.parse(uriStr))
                }
            }
            // 同步 UI
            updateCategoryUI()
            updateAccountIndicator(selectedAccountName)
            switchTab()
            refreshImageList()
        } else {
            // 全新创建，设置默认日期时间
            val now = Calendar.getInstance()
            btnDate.text = String.format(Locale.getDefault(), "%d-%02d-%02d",
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH) + 1,
                now.get(Calendar.DAY_OF_MONTH))
            btnTime.text = timeFormat.format(now.time)
            selectCategory = null
            updateCategoryUI()
        }
        checkDoneEnable()
        return view
    }
    private fun parseDateAndSetBtn(dateStr: String) {
        val formats = listOf("yyyy-MM-dd", "yyyy/MM/dd", "MM-dd", "MM/dd")
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.getDefault())
                val date = sdf.parse(dateStr)
                if (date != null) {
                    val cal = Calendar.getInstance()
                    cal.time = date
                    if (dateStr.length <= 5) cal.set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR))
                    btnDate.text = String.format(Locale.getDefault(), "%d-%02d-%02d",
                        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
                    return
                }
            } catch (_: Exception) {}
        }
    }

    private fun parseTimeAndSetBtn(timeStr: String) {
        val formats = listOf("HH:mm", "h:mm a", "H:mm")
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.getDefault())
                val date = sdf.parse(timeStr)
                if (date != null) {
                    btnTime.text = timeFormat.format(date)
                    return
                }
            } catch (_: Exception) {}
        }
    }
    private fun initView(view: View) {
        tvCancel = view.findViewById(R.id.tv_cancel)
        tabExpense = view.findViewById(R.id.tab_expense)
        tabIncome = view.findViewById(R.id.tab_income)
        categoryContainer = view.findViewById(R.id.category_container)
        etAmount = view.findViewById(R.id.et_amount)
        etRemark = view.findViewById(R.id.et_remark)
        ivAddImg = view.findViewById(R.id.iv_add_img)
        btnOcr = view.findViewById(R.id.btn_ocr)
        llImgContainer = view.findViewById(R.id.ll_img_container)
        imgZoomOverlay = view.findViewById(R.id.img_zoom_overlay)
        ivZoom = view.findViewById(R.id.iv_zoom)
        btnDate = view.findViewById(R.id.btn_date)
        btnTime = view.findViewById(R.id.btn_time)
        tvDone = view.findViewById(R.id.tv_done)
        btnAccount = view.findViewById(R.id.btn_account)
        ivAccountIndicator = view.findViewById(R.id.iv_account_indicator)
        val now = Calendar.getInstance()
//       btnDate.text = String.format(Locale.getDefault(), "%d-%02d-%02d",
//            now.get(Calendar.YEAR),
//            now.get(Calendar.MONTH) + 1,
//            now.get(Calendar.DAY_OF_MONTH))
//        btnTime.text = timeFormat.format(now.time)

        llImgContainer.orientation = LinearLayout.HORIZONTAL
        llImgContainer.clipChildren = false
        llImgContainer.clipToPadding = false
         // 初始无图标
        bindZoomable(ivZoom)
        updateAccountIndicator(null)
    }

    private fun initListener() {
        tvCancel.setOnClickListener {
            clearAllInputs()
        }

        tvDone.setOnClickListener {
            hideKeyboard()
            val amountStr = etAmount.text.toString().trim()
            if (amountStr.isEmpty()) {
                toast("请输入金额")
                return@setOnClickListener
            }
            if (selectCategory.isNullOrBlank()) {
                toast("请选择分类")
                return@setOnClickListener
            }
            // 直接使用已选的账户（可能为null）
            createBillAndSave(selectedAccountName)
        }
        btnAccount.setOnClickListener {
            val topAccounts = AccountRepository.getTopAccounts()
            if (topAccounts.isEmpty()) {
                selectedAccountName = null
                updateAccountIndicator(null)
                toast("暂无可用账户")
                return@setOnClickListener
            }

            val accountNames = mutableListOf("不关联账户")
            accountNames.addAll(topAccounts.map { it.name })

            val adapter = object : ArrayAdapter<String>(
                requireContext(),
                R.layout.item_account_select,
                R.id.tv_account_name,
                accountNames
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    val imageView = view.findViewById<ImageView>(R.id.iv_account_icon)
                    val textView = view.findViewById<TextView>(R.id.tv_account_name)
                    val name = getItem(position)!!
                    if (position == 0) {
                        imageView.setImageResource(accountIconList[0])
                    } else {
                        val account = topAccounts[position - 1]
                        val iconIndex = nameIconIndexMap[account.name] ?: 0
                        imageView.setImageResource(accountIconList.getOrNull(iconIndex) ?: accountIconList[0])
                    }
                    return view
                }
            }

            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("选择关联账户")
                .setAdapter(adapter) { _, which ->
                    if (which == 0) {
                        selectedAccountName = null
                        updateAccountIndicator(null)
                        toast("已选择：不关联账户")
                    } else {
                        val parentAccount = topAccounts[which - 1]
                        val children = AccountRepository.getChildAccounts(parentAccount.name)
                        if (children.isEmpty()) {
                            selectedAccountName = parentAccount.name
                            updateAccountIndicator(selectedAccountName)
                            toast("已选择：${parentAccount.name}")
                        } else {
                            val childNames = children.map { it.name }
                            val childDialog = AlertDialog.Builder(requireContext())
                                .setTitle("选择 ${parentAccount.name} 子账户")
                                .setItems(childNames.toTypedArray()) { _, childIndex ->
                                    selectedAccountName = children[childIndex].name
                                    updateAccountIndicator(parentAccount.name)
                                    toast("已选择：${children[childIndex].name}")
                                }
                                .create()
                            childDialog.window?.setBackgroundDrawableResource(android.R.color.white)
                            childDialog.show()
                        }
                    }
                }
                .create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.white)
            dialog.show()
        }

        tabExpense.setOnClickListener {
            isExpenseMode = true
            switchTab()
            selectCategory = null
            updateCategoryUI()
            checkDoneEnable()
        }

        tabIncome.setOnClickListener {
            isExpenseMode = false
            switchTab()
            selectCategory = null
            updateCategoryUI()
            checkDoneEnable()
        }

        ivAddImg.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            startActivityForResult(intent, REQUEST_CODE_IMG)
        }
        btnOcr.setOnClickListener {
            if (selectedImageUris.isEmpty()) {
                toast("请先添加至少一张图片")
                return@setOnClickListener
            }
            recognizeTextFromImage(selectedImageUris[0])
        }

        imgZoomOverlay.setOnClickListener { imgZoomOverlay.visibility = View.GONE }

        btnDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, y, m, d ->
                btnDate.text = String.format(Locale.getDefault(), "%d-%02d-%02d", y, m + 1, d)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnTime.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(requireContext(), { _, h, mi ->
                btnTime.text = String.format(Locale.getDefault(), "%02d:%02d", h, mi)
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }

        etAmount.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { checkDoneEnable() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_IMG && data != null) {
            if (data.clipData != null) {
                val clipData = data.clipData!!
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    selectedImageUris.add(uri)
                    try {
                        requireContext().contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: SecurityException) {}
                }
            } else {
                data.data?.let { uri ->
                    selectedImageUris.add(uri)
                    try {
                        requireContext().contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: SecurityException) {}
                }
            }
            refreshImageList()
        }
    }

    private fun toast(msg: String) {
        val t = Toast.makeText(context, msg, Toast.LENGTH_SHORT)
        t.setGravity(Gravity.CENTER, 0, 0)
        t.show()
    }

    private fun createBillAndSave(accountName: String?) {
        val amountStr = etAmount.text.toString().trim()
        val amount = amountStr.toDouble()
        val finalAmount = if (isExpenseMode) -amount else amount
        val dateStr = "${btnDate.text} ${btnTime.text}"
        val remark = etRemark.text.toString().trim()

        val bill = Bill(
            id = System.currentTimeMillis(),
            category = selectCategory!!,
            amount = finalAmount,
            date = dateStr,
            type = if (isExpenseMode) BillType.EXPENSE else BillType.INCOME,
            remark = remark,
            imageUris = selectedImageUris.map { it.toString() },
            accountName = accountName
        )
        BillRepository.addBill(bill)

        etAmount.setText("")
        etRemark.setText("")
        selectCategory = null
        selectedImageUris.clear()
        refreshImageList()
        checkDoneEnable()
        toast("记账成功")
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etAmount.windowToken, 0)
    }

    private fun refreshImageList() {
        llImgContainer.removeAllViews()
        val density = resources.displayMetrics.density
        val imgSize = (100 * density).toInt()
        for ((index, uri) in selectedImageUris.withIndex()) {
            val itemContainer = FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(200, 350).apply { setMargins(8, 8, 8, 8) }
            }
            val iv = ImageView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                setBackgroundColor(Color.LTGRAY)
                setImageURI(uri)
            }
            val deleteBtn = TextView(requireContext()).apply {
                text = "✕"
                textSize = 16f
                setTextColor(Color.WHITE)
                background = null
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.END or Gravity.TOP }
                setOnClickListener {
                    selectedImageUris.removeAt(index)
                    refreshImageList()
                }
            }
            itemContainer.addView(iv)
            itemContainer.addView(deleteBtn)
            llImgContainer.addView(itemContainer)
            iv.setOnClickListener {
                ivZoom.setImageURI(uri)
                ivZoom.requestLayout()
                imgZoomOverlay.visibility = View.VISIBLE
            }
        }
    }

    private fun switchTab() {
        if (isExpenseMode) {
            tabExpense.setBackgroundColor(Color.parseColor("#FF6B6B"))
            tabExpense.setTextColor(Color.WHITE)
            tabIncome.setBackgroundColor(Color.TRANSPARENT)
            tabIncome.setTextColor(Color.BLACK)
        } else {
            tabIncome.setBackgroundColor(Color.parseColor("#4CAF50"))
            tabIncome.setTextColor(Color.WHITE)
            tabExpense.setBackgroundColor(Color.TRANSPARENT)
            tabExpense.setTextColor(Color.BLACK)
        }
    }

    private fun checkDoneEnable() {
        val canClick = etAmount.text.isNotBlank() && !selectCategory.isNullOrBlank()
        tvDone.setTextColor(if (canClick) Color.parseColor("#FF6B6B") else Color.GRAY)
        tvDone.isClickable = true
    }
    private fun recognizeTextFromImage(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(requireActivity().contentResolver, uri)
            val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
            val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build()
            )

            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    parseAndFill(visionText.text)
                }
                .addOnFailureListener { e ->
                    toast("识别失败：${e.localizedMessage}")
                }
        } catch (e: Exception) {
            toast("无法加载图片：${e.localizedMessage}")
        }
    }

    /**
     * 调用阿里云百炼大模型，智能解析OCR识别出的文本
     */
    private fun parseAndFill(text: String) {
        // 启动协程，在IO线程请求API，避免卡界面
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val gen = Generation()
                val userMsg = Message.builder()
                    .role(Role.USER.getValue())
                    .content(buildPrompt(text)) // 构建提示词
                    .build()

                val genParam = GenerationParam.builder()
                    .apiKey("sk-9e582ad8928841c6b6c5ce60c7a4b6ca") // TODO: 替换成真实的Key，别硬编码，建议放配置文件
                    .model("qwen-plus")        // 可换 qwen-turbo(更快) 或 qwen-max(更准)
                    .messages(listOf(userMsg))
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .build()

                val result: GenerationResult = gen.call(genParam)
                val responseText = result.output.choices.first().message.content

                // 解析返回的JSON并更新UI
                parseResponseAndUpdateUI(responseText)

            } catch (e: NoApiKeyException) {
                withContext(Dispatchers.Main) {
                    toast("请设置百炼API-Key")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toast("智能识别失败：${e.localizedMessage}")
                    // 若API失败，可回退到简单正则作为 Plan B
                    // fallbackRegexParse(text)
                }
            }
        }
    }

    /**
     * 构造给大模型的提示词
     */
    private fun buildPrompt(ocrText: String): String {
        return """
你是一个专业的记账助手。请分析下面的OCR识别文本，提取关键消费信息。

需要提取的字段：
- amount: 消费金额（只保留数字，如 23.80）
- date: 日期（格式 yyyy-MM-dd 或 MM-dd）
- time: 时间（格式 HH:mm）
- category: 类别（从：正餐、零食、交通、日用、娱乐、购物、旅行、医疗 中选择最贴切的一个）
- account: 支付方式。**你必须扫描整个文本，找到“支付方式”“付款方式”“支付方式：”等关键词，提取紧随其后的账户名称（例如“微信支付 >”提取为“微信”，“支付宝”提取为“支付宝”，“银行卡”提取为“银行卡”）。如果找不到，请留空字符串。不要猜测，不要使用示例值。**
- sub_account: 子账户标识（如花呗、余额宝、中国建设银行等），没有就留空。
- remark: 简短概括这笔消费（比如“正新鸡排外卖”）

直接返回一个纯JSON对象，不要包含任何解释文字。例如：
{"amount":"22.20","date":"2024-04-25","time":"12:30","category":"正餐","account":"支付宝","sub_account":"","remark":"正新鸡排外卖"}

待分析文本：
$ocrText
    """.trimIndent()
    }

    /**
     * 解析大模型返回的JSON，并填入界面控件
     */
    private suspend fun parseResponseAndUpdateUI(responseText: String) {
        // 1. 提前声明，初始化为空字符串，使作用域延伸到整个方法
        var cleanJson = ""
        try {
            cleanJson = responseText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            android.util.Log.d("BAILIAN_JSON", cleanJson)
            val json = JSONObject(cleanJson)

            val amount = json.optString("amount", "")
            val date = json.optString("date", "")
            val time = json.optString("time", "")
            val category = json.optString("category", "")
            val remark = json.optString("remark", "")
            val account = json.optString("account", "")
            val subAccount = json.optString("sub_account", "")

            withContext(Dispatchers.Main) {
                if (amount.isNotEmpty()) etAmount.setText(amount)
                if (date.isNotEmpty()) parseDateAndSetBtn(date)
                if (time.isNotEmpty()) btnTime.text = time
                if (category.isNotEmpty()) {
                    selectCategory = category
                    updateCategoryUI()
                    checkDoneEnable()
                }
                if (remark.isNotEmpty()) etRemark.setText(remark)
                // 自动设置关联账户
                autoSetAccount(account, subAccount)
                toast("智能识别完成")
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                // 现在 cleanJson 可以访问了，即使它可能是空的
                if (cleanJson.isNotEmpty()) {
                    toast("返回JSON: $cleanJson")
                }
                toast("解析结果出错，请重试")
            }
        }
    }
    // ==================== 图片预览缩放（最终稳定版） ====================
    private fun bindZoomable(imageView: ImageView) {
        val matrix = Matrix()
        imageView.scaleType = ImageView.ScaleType.MATRIX
        imageView.imageMatrix = matrix

        var baseScale = 1f
        var isFirstLoad = true

        val resetRunnable = Runnable {
            try {
                val drawable = imageView.drawable ?: return@Runnable
                val vw = imageView.width.toFloat()
                val vh = imageView.height.toFloat()
                if (vw <= 0 || vh <= 0) return@Runnable
                val dw = drawable.intrinsicWidth.toFloat()
                val dh = drawable.intrinsicHeight.toFloat()
                if (dw <= 0 || dh <= 0) return@Runnable

                baseScale = min(vw / dw, vh / dh)
                matrix.reset()
                matrix.postTranslate((vw - dw * baseScale) / 2f, (vh - dh * baseScale) / 2f)
                matrix.postScale(baseScale, baseScale, vw / 2f, vh / 2f)
                imageView.imageMatrix = matrix
                isFirstLoad = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        imageView.setTag(resetRunnable)

        fun fixBounds() {
            val drawable = imageView.drawable ?: return
            val vw = imageView.width.toFloat()
            val vh = imageView.height.toFloat()
            if (vw <= 0 || vh <= 0) return
            val values = FloatArray(9)
            matrix.getValues(values)
            val scale = values[Matrix.MSCALE_X]
            val dw = drawable.intrinsicWidth * scale
            val dh = drawable.intrinsicHeight * scale
            var transX = values[Matrix.MTRANS_X]
            var transY = values[Matrix.MTRANS_Y]

            if (dw <= vw) transX = (vw - dw) / 2f
            else {
                if (transX > 0) transX = 0f
                if (transX < vw - dw) transX = vw - dw
            }
            if (dh <= vh) transY = (vh - dh) / 2f
            else {
                if (transY > 0) transY = 0f
                if (transY < vh - dh) transY = vh - dh
            }
            values[Matrix.MTRANS_X] = transX
            values[Matrix.MTRANS_Y] = transY
            matrix.setValues(values)
            imageView.imageMatrix = matrix
        }

        imageView.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    resetRunnable.run()
                }
            }
        )

        val gestureDetector = GestureDetector(imageView.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    (imageView.parent as? View)?.visibility = View.GONE
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (isFirstLoad) return true
                    val values = FloatArray(9)
                    matrix.getValues(values)
                    val curScale = values[Matrix.MSCALE_X]
                    val targetScale = if (abs(curScale - baseScale) < 0.01f) baseScale * 2f else baseScale

                    val curTransX = values[Matrix.MTRANS_X]
                    val curTransY = values[Matrix.MTRANS_Y]
                    val focusX = e.x
                    val focusY = e.y

                    val targetMatrix = Matrix()
                    targetMatrix.set(matrix)
                    targetMatrix.postTranslate(-focusX, -focusY)
                    targetMatrix.postScale(targetScale / curScale, targetScale / curScale)
                    targetMatrix.postTranslate(focusX, focusY)
                    val targetValues = FloatArray(9)
                    targetMatrix.getValues(targetValues)
                    var targetTransX = targetValues[Matrix.MTRANS_X]
                    var targetTransY = targetValues[Matrix.MTRANS_Y]

                    val drawable = imageView.drawable ?: return true
                    val vw = imageView.width.toFloat()
                    val vh = imageView.height.toFloat()
                    val dw = drawable.intrinsicWidth * targetScale
                    val dh = drawable.intrinsicHeight * targetScale
                    if (dw <= vw) targetTransX = (vw - dw) / 2f
                    else {
                        if (targetTransX > 0) targetTransX = 0f
                        if (targetTransX < vw - dw) targetTransX = vw - dw
                    }
                    if (dh <= vh) targetTransY = (vh - dh) / 2f
                    else {
                        if (targetTransY > 0) targetTransY = 0f
                        if (targetTransY < vh - dh) targetTransY = vh - dh
                    }

                    val startScale = curScale
                    val startTransX = curTransX
                    val startTransY = curTransY
                    val anim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 250
                        interpolator = android.view.animation.DecelerateInterpolator()
                        addUpdateListener { animation ->
                            val fraction = animation.animatedFraction
                            val currentScale = startScale + (targetScale - startScale) * fraction
                            val currentTransX = startTransX + (targetTransX - startTransX) * fraction
                            val currentTransY = startTransY + (targetTransY - startTransY) * fraction
                            matrix.reset()
                            matrix.postScale(currentScale, currentScale)
                            matrix.postTranslate(currentTransX, currentTransY)
                            imageView.imageMatrix = matrix
                        }
                        start()
                    }
                    return true
                }

                override fun onScroll(
                    e1: MotionEvent?, e2: MotionEvent,
                    distanceX: Float, distanceY: Float
                ): Boolean {
                    matrix.postTranslate(-distanceX, -distanceY)
                    fixBounds()
                    return true
                }
            })

        val scaleDetector = ScaleGestureDetector(imageView.context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val scaleFactor = detector.scaleFactor
                    val focusX = detector.focusX
                    val focusY = detector.focusY
                    val values = FloatArray(9)
                    matrix.getValues(values)
                    val curScale = values[Matrix.MSCALE_X]
                    val targetScale = (curScale * scaleFactor).coerceIn(baseScale * 0.5f, baseScale * 4f)

                    matrix.postTranslate(-focusX, -focusY)
                    matrix.postScale(targetScale / curScale, targetScale / curScale)
                    matrix.postTranslate(focusX, focusY)
                    fixBounds()
                    return true
                }
            })

        imageView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (!scaleDetector.isInProgress) {
                gestureDetector.onTouchEvent(event)
            }
            true
        }
    }


    // ========== 分类 UI 相关 ==========
    private fun updateCategoryUI() {
        categoryContainer.removeAllViews()
        lastSelectedItemLayout = null
        val list = if (isExpenseMode) CategoryHelper.getExpenseCategories() else CategoryHelper.getIncomeCategories()
        for (i in list.indices step 4) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 16 }
            }
            for (j in i until i + 4) {
                if (j >= list.size) break
                row.addView(createCategoryItem(list[j]))
            }
            categoryContainer.addView(row)
        }
    }

    private fun createCategoryItem(name: String): LinearLayout {
        val density = resources.displayMetrics.density
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER
            setPadding(4, 8, 4, 8)
        }
        val emojiView = TextView(context).apply {
            text = CategoryHelper.getEmojiMap()[name] ?: "📌"
            textSize = 28f
            gravity = Gravity.CENTER
        }
        val nameView = TextView(context).apply {
            text = name
            textSize = 14f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (4 * density).toInt() }
        }
        layout.addView(emojiView)
        layout.addView(nameView)
        layout.setOnClickListener {
            if (name == "+") {
                showAddCategoryDialog()
                return@setOnClickListener
            }
            lastSelectedItemLayout?.let { old ->
                (old.getChildAt(1) as? TextView)?.setTextColor(Color.BLACK)
            }
            nameView.setTextColor(Color.parseColor("#FF4081"))
            selectCategory = name
            checkDoneEnable()
            toast("已选择：$name")
            lastSelectedItemLayout = layout
        }
        layout.setOnLongClickListener {
            if (CategoryHelper.isCustomCategory(name)) {
                AlertDialog.Builder(requireContext())
                    .setTitle("删除类别")
                    .setMessage("确定要删除类别「$name」吗？")
                    .setPositiveButton("删除") { _, _ ->
                        CategoryHelper.removeCategory(name)
                        if (selectCategory == name) {
                            selectCategory = null
                            checkDoneEnable()
                        }
                        updateCategoryUI()
                        toast("已删除：$name")
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            } else false
        }
        return layout
    }

    private fun showAddCategoryDialog() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_category, null)
        val etName = view.findViewById<EditText>(R.id.et_category_name)
        val etEmoji = view.findViewById<EditText>(R.id.et_category_emoji)
        AlertDialog.Builder(requireContext())
            .setTitle("添加自定义类别")
            .setView(view)
            .setPositiveButton("确定") { _, _ ->
                val name = etName.text.toString().trim()
                val emoji = etEmoji.text.toString().trim()
                if (name.isNotEmpty() && emoji.isNotEmpty()) {
                    CategoryHelper.addCategory(name, emoji)
                    updateCategoryUI()
                } else {
                    toast("名称和 emoji 不能为空")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}