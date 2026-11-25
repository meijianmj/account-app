package com.example.myapplication

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.min

class EditBillFragment : Fragment() {

    private lateinit var tvCancel: TextView
    private lateinit var tvDone: TextView
    private lateinit var tvOriginalCategory: TextView
    private lateinit var tvOriginalAmount: TextView
    private lateinit var tvOriginalDate: TextView
    private lateinit var tvOriginalType: TextView

    private lateinit var etAmount: EditText
    private lateinit var etRemark: EditText
    private lateinit var btnDate: Button
    private lateinit var btnTime: Button
    private lateinit var tabExpense: TextView
    private lateinit var tabIncome: TextView
    private lateinit var categoryContainer: LinearLayout

    private lateinit var ivAddImg: ImageView
    private lateinit var llImgContainer: LinearLayout
    private lateinit var imgZoomOverlay: FrameLayout
    private lateinit var ivZoom: ImageView

    private var currentBill: Bill? = null
    private var isExpenseMode = true
    private var selectCategory: String? = null
    private var lastSelectedItemLayout: LinearLayout? = null
    private val selectedImageUris = ArrayList<Uri>()

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val REQUEST_CODE_IMG = 1001

    companion object {
        private const val ARG_BILL_ID = "bill_id"
        fun newInstance(bill: Bill): EditBillFragment {
            val fragment = EditBillFragment()
            val args = Bundle()
            args.putLong(ARG_BILL_ID, bill.id)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_edit_bill, container, false)
        initViews(view)
        loadBill()
        initListeners()
        return view
    }

    private fun initViews(view: View) {
        tvCancel = view.findViewById(R.id.tv_cancel)
        tvDone = view.findViewById(R.id.tv_done)
        tvOriginalCategory = view.findViewById(R.id.tv_original_category)
        tvOriginalAmount = view.findViewById(R.id.tv_original_amount)
        tvOriginalDate = view.findViewById(R.id.tv_original_date)
        tvOriginalType = view.findViewById(R.id.tv_original_type)

        etAmount = view.findViewById(R.id.et_amount)
        etRemark = view.findViewById(R.id.et_remark)
        btnDate = view.findViewById(R.id.btn_date)
        btnTime = view.findViewById(R.id.btn_time)
        tabExpense = view.findViewById(R.id.tab_expense)
        tabIncome = view.findViewById(R.id.tab_income)
        categoryContainer = view.findViewById(R.id.category_container)

        ivAddImg = view.findViewById(R.id.iv_add_img)
        llImgContainer = view.findViewById(R.id.ll_img_container)
        imgZoomOverlay = view.findViewById(R.id.img_zoom_overlay)
        ivZoom = view.findViewById(R.id.iv_zoom)

        llImgContainer.orientation = LinearLayout.HORIZONTAL
        llImgContainer.clipChildren = false
        llImgContainer.clipToPadding = false

        bindZoomable(ivZoom)
    }

    private fun loadBill() {
        try {
            val billId = arguments?.getLong(ARG_BILL_ID, -1L) ?: -1L
            if (billId == -1L) {
                safePop("账单ID无效")
                return
            }
            val bill = BillRepository.getAllBills().find { it.id == billId }
            if (bill == null) {
                safePop("账单不存在")
                return
            }
            currentBill = bill

            val safeAmount = if (bill.amount.isNaN() || bill.amount.isInfinite()) 0.0 else bill.amount
            val absAmount = kotlin.math.abs(safeAmount)

            tvOriginalCategory.text = "分类：${bill.category}"
            tvOriginalAmount.text = "金额：¥${"%.2f".format(absAmount)} (${if (safeAmount < 0) "支出" else "收入"})"
            tvOriginalDate.text = "日期：${bill.date}"
            tvOriginalType.text = "类型：${if (bill.type == BillType.EXPENSE) "支出" else "收入"}"

            isExpenseMode = bill.type == BillType.EXPENSE
            selectCategory = bill.category
            etAmount.setText(String.format(Locale.getDefault(), "%.2f", absAmount))
            etRemark.setText(bill.remark.ifBlank { "" })

            val dateParts = bill.date.split(" ")
            btnDate.text = dateParts.getOrElse(0) { bill.date }
            btnTime.text = dateParts.getOrElse(1) { "00:00" }

            selectedImageUris.clear()
            bill.imageUris.forEach { str ->
                try { selectedImageUris.add(Uri.parse(str)) } catch (_: Exception) {}
            }
            refreshImageList()
            switchTab()
            updateCategoryUI()
            checkDoneEnable()

            view?.post {
                if (!selectCategory.isNullOrBlank()) {
                    for (i in 0 until categoryContainer.childCount) {
                        val row = categoryContainer.getChildAt(i) as? LinearLayout ?: continue
                        for (j in 0 until row.childCount) {
                            val item = row.getChildAt(j) as? LinearLayout ?: continue
                            val tv = item.getChildAt(1) as? TextView ?: continue
                            if (tv.text == selectCategory) {
                                tv.setTextColor(Color.parseColor("#FF4081"))
                                lastSelectedItemLayout = item
                                break
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "加载失败：${e.message}", Toast.LENGTH_LONG).show()
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    private fun safePop(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        requireActivity().supportFragmentManager.popBackStack()
    }

    private fun initListeners() {
        tvCancel.setOnClickListener { requireActivity().supportFragmentManager.popBackStack() }
        tvDone.setOnClickListener { saveEditedBill() }

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

    private fun saveEditedBill() {
        try {
            val bill = currentBill
            if (bill == null) {
                Toast.makeText(context, "无法保存", Toast.LENGTH_SHORT).show()
                return
            }
            val amountStr = etAmount.text.toString().trim()
            if (amountStr.isEmpty()) {
                Toast.makeText(context, "请输入金额", Toast.LENGTH_SHORT).show()
                return
            }
            if (selectCategory.isNullOrBlank()) {
                Toast.makeText(context, "请选择分类", Toast.LENGTH_SHORT).show()
                return
            }
            val amount = amountStr.toDouble()
            val finalAmount = if (isExpenseMode) -amount else amount
            val remark = etRemark.text.toString().trim()
            val imageList = selectedImageUris.map { it.toString() }

            val updatedBill = bill.copy(
                category = selectCategory!!,
                amount = finalAmount,
                date = "${btnDate.text} ${btnTime.text}",
                type = if (isExpenseMode) BillType.EXPENSE else BillType.INCOME,
                remark = remark,
                imageUris = imageList
            )
            BillRepository.updateBill(updatedBill)
            Toast.makeText(context, "修改成功", Toast.LENGTH_SHORT).show()
            requireActivity().supportFragmentManager.popBackStack()
        } catch (e: Exception) {
            Log.e("EditBillFragment", "save error", e)
            Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
        }
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

    private fun refreshImageList() {
        llImgContainer.removeAllViews()
        val density = resources.displayMetrics.density
        val imgSize = (70 * density).toInt()
        for ((index, uri) in selectedImageUris.withIndex()) {
            val itemContainer = FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(imgSize, imgSize).apply {
                    setMargins(8, 8, 8, 8)
                }
            }
            val iv = ImageView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                setBackgroundColor(Color.LTGRAY)
                setImageURI(uri)
            }
            val deleteBtn = TextView(requireContext()).apply {
                text = "✕"
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(4, 2, 4, 2)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(180, 0, 0, 0))
                }
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.END or Gravity.TOP
                    setMargins(0, 4, 4, 0)
                }
                setOnClickListener {
                    selectedImageUris.removeAt(index)
                    refreshImageList()
                }
            }
            itemContainer.addView(iv)
            itemContainer.addView(deleteBtn)
            iv.setOnClickListener {
                ivZoom.setImageURI(uri)
                ivZoom.requestLayout()
                imgZoomOverlay.visibility = View.VISIBLE
            }
            llImgContainer.addView(itemContainer)
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
    }

    private fun updateCategoryUI() {
        categoryContainer.removeAllViews()
        lastSelectedItemLayout = null
        val list = if (isExpenseMode) CategoryHelper.getExpenseCategories() else CategoryHelper.getIncomeCategories()
        for (i in list.indices step 4) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
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
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
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
            Toast.makeText(context, "已选择：$name", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, "已删除：$name", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            } else false
        }

        return layout
    }

    private fun showAddCategoryDialog() {
        val builder = AlertDialog.Builder(requireContext())
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_category, null)
        val etName = view.findViewById<EditText>(R.id.et_category_name)
        val etEmoji = view.findViewById<EditText>(R.id.et_category_emoji)

        builder.setTitle("添加自定义类别")
        builder.setView(view)
        builder.setPositiveButton("确定") { _, _ ->
            val name = etName.text.toString().trim()
            val emoji = etEmoji.text.toString().trim()
            if (name.isNotEmpty() && emoji.isNotEmpty()) {
                CategoryHelper.addCategory(name, emoji)
                updateCategoryUI()
            } else {
                Toast.makeText(context, "名称和 emoji 不能为空", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("取消", null)
        builder.show()
    }

    // ==================== 图片预览缩放（与记账页完全一致） ====================
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
}