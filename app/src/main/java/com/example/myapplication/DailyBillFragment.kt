package com.example.myapplication

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*
import kotlin.math.*

class DailyBillFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BillAdapter
    private lateinit var tvMonthSelector: TextView
    private lateinit var btnExpenseTab: TextView
    private lateinit var btnIncomeTab: TextView
    private lateinit var tvExpenseTotal: TextView
    private lateinit var tvIncomeTotal: TextView
    private lateinit var tvBalance: TextView
    private lateinit var chartContainer: LinearLayout
    private lateinit var chartDateTv: TextView
    private lateinit var chartAmountTv: TextView
    private lateinit var btnChartPrev: ImageView
    private lateinit var btnChartNext: ImageView
    private lateinit var btnCancelDate: TextView

    private var selectedYear = Calendar.getInstance().get(Calendar.YEAR)
    private var selectedMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
    private var isShowExpense = true
    private var isPieChart = false
    private val selectedDays = mutableSetOf<Int>()
    private var filterCategory: String? = null

    // 账户图标资源列表（与余额页面保持一致）
    private val accountIconList = listOf(
        R.drawable.money,       // 0 默认
        R.drawable.alipay,     // 1 支付宝
        R.drawable.wechat,     // 2 微信
        R.drawable.bank,       // 3 银行卡
        R.drawable.red,        // 4 小红书
        R.drawable.dy          // 5 抖音
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_daily_bill, container, false)

        tvMonthSelector = view.findViewById(R.id.tv_month_selector)
        btnExpenseTab = view.findViewById(R.id.btn_expense_tab)
        btnIncomeTab = view.findViewById(R.id.btn_income_tab)
        tvExpenseTotal = view.findViewById(R.id.tv_expense)
        tvIncomeTotal = view.findViewById(R.id.tv_income)
        tvBalance = view.findViewById(R.id.tv_balance)
        chartContainer = view.findViewById(R.id.chart_container)
        chartDateTv = view.findViewById(R.id.chart_date_tv)
        chartAmountTv = view.findViewById(R.id.chart_amount_tv)
        btnChartPrev = view.findViewById(R.id.btn_chart_prev)
        btnChartNext = view.findViewById(R.id.btn_chart_next)
        recyclerView = view.findViewById(R.id.recycler_view)

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.isNestedScrollingEnabled = false
        adapter = BillAdapter()
        recyclerView.adapter = adapter

        // 动态添加“取消选中”按钮到日期信息所在的布局中
        val dateInfoLayout = chartDateTv.parent as ViewGroup
        btnCancelDate = TextView(requireContext()).apply {
            text = "取消选中"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(10, 4, 10, 4)
            setBackgroundColor(Color.parseColor("#FF6B6B"))
            visibility = View.GONE
            setOnClickListener {
                selectedDays.clear()
                refreshAll()
            }
        }
        dateInfoLayout.addView(btnCancelDate)

        tvMonthSelector.text = String.format(Locale.getDefault(), "%d年%02d月", selectedYear, selectedMonth)
        tvMonthSelector.setOnClickListener { resetFilter(); showMonthPicker() }

        btnExpenseTab.setOnClickListener {
            isShowExpense = true
            selectedDays.clear()
            resetFilter()
            updateTabStyle()
            refreshAll()
        }

        btnIncomeTab.setOnClickListener {
            isShowExpense = false
            selectedDays.clear()
            resetFilter()
            updateTabStyle()
            refreshAll()
        }

        btnChartPrev.setOnClickListener {
            isPieChart = !isPieChart
            selectedDays.clear()
            resetFilter()
            refreshAll()
        }

        btnChartNext.setOnClickListener {
            isPieChart = !isPieChart
            selectedDays.clear()
            resetFilter()
            refreshAll()
        }

        updateTabStyle()
        refreshAll()
        return view
    }

    private fun resetFilter() { filterCategory = null }

    private fun showMonthPicker() {
        DatePickerDialog(requireContext(), { _, year, month, _ ->
            selectedYear = year
            selectedMonth = month + 1
            tvMonthSelector.text = String.format(Locale.getDefault(), "%d年%02d月", selectedYear, selectedMonth)
            selectedDays.clear()
            resetFilter()
            refreshAll()
        }, selectedYear, selectedMonth - 1, 1).show()
    }

    private fun updateTabStyle() {
        if (isShowExpense) {
            btnExpenseTab.setBackgroundColor(Color.parseColor("#FF6B6B"))
            btnExpenseTab.setTextColor(Color.WHITE)
            btnIncomeTab.setBackgroundColor(Color.TRANSPARENT)
            btnIncomeTab.setTextColor(Color.BLACK)
        } else {
            btnIncomeTab.setBackgroundColor(Color.parseColor("#4CAF50"))
            btnIncomeTab.setTextColor(Color.WHITE)
            btnExpenseTab.setBackgroundColor(Color.TRANSPARENT)
            btnExpenseTab.setTextColor(Color.BLACK)
        }
    }

    private fun refreshAll() {
        chartContainer.removeAllViews()
        updateStats()
        updateBillList()
        drawChart()
    }

    private fun updateStats() {
        val list = BillRepository.getAllBills()
        var expense = 0.0; var income = 0.0
        for (bill in list) {
            val parts = parseDate(bill.date) ?: continue
            val y = parts[0]; val m = parts[1]; val d = parts[2]
            if (y == selectedYear && m == selectedMonth) {
                if (selectedDays.isEmpty() || d in selectedDays) {
                    if (bill.amount < 0) expense += -bill.amount else income += bill.amount
                }
            }
        }
        tvExpenseTotal.text = String.format(Locale.getDefault(), "¥%.2f", expense)
        tvIncomeTotal.text = String.format(Locale.getDefault(), "¥%.2f", income)
        tvBalance.text = String.format(Locale.getDefault(), "结余 ¥%.2f", income - expense)
    }

    private fun updateBillList() {
        val sorted = BillRepository.getAllBills()
            .filter {
                val p = parseDate(it.date) ?: return@filter false
                p[0] == selectedYear && p[1] == selectedMonth
            }
            .filter {
                if (selectedDays.isEmpty()) true
                else { val p = parseDate(it.date) ?: return@filter false; p[2] in selectedDays }
            }
            .filter { filterCategory.isNullOrEmpty() || it.category == filterCategory }
            .sortedByDescending {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                sdf.parse(it.date)?.time ?: 0
            }
            .toMutableList()
        adapter.updateData(sorted)
    }

    private fun selectCategoryFilter(cat: String) {
        filterCategory = cat
        updateBillList()
        drawChart()
    }

    private fun cancelFilter() {
        filterCategory = null
        updateBillList()
        drawChart()
    }

    private fun parseDate(dateStr: String): IntArray? {
        val datePart = dateStr.split(" ").firstOrNull() ?: return null
        val parts = datePart.split("-", "/")
        if (parts.size < 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        return intArrayOf(y, m, d)
    }

    private fun drawChart() {
        chartContainer.removeAllViews()
        if (isPieChart) drawPieChart() else drawNativeChart()
    }

    // ---------- 以下为柱状图和环形图绘制，保持不变 ----------
    private fun drawNativeChart() {
        val list = BillRepository.getAllBills()
        val maxDay = getMaxDayOfMonth(selectedYear, selectedMonth)
        val dailyMap = mutableMapOf<Int, Double>()
        for (day in 1..maxDay) dailyMap[day] = 0.0
        for (bill in list) {
            val p = parseDate(bill.date) ?: continue
            if (p[0] != selectedYear || p[1] != selectedMonth) continue
            val value = if (isShowExpense) {
                if (bill.amount < 0) -bill.amount else 0.0
            } else {
                if (bill.amount > 0) bill.amount else 0.0
            }
            dailyMap[p[2]] = dailyMap[p[2]]!! + value
        }
        val maxValue = dailyMap.values.maxOrNull() ?: 0.0
        val normalColor = if (isShowExpense) Color.parseColor("#FF6B6B") else Color.parseColor("#4CAF50")
        val highLightBlue = Color.parseColor("#87CEEB")

        for (day in 1..maxDay) {
            val value = dailyMap[day]!!
            val barHeight = if (maxValue == 0.0) 4 else ((value / maxValue) * 120).toInt().coerceAtLeast(4)
            val item = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                gravity = Gravity.BOTTOM
                tag = day
            }
            val bar = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(-1, barHeight)
                val gd = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadii = floatArrayOf(99f, 99f, 99f, 99f, 0f, 0f, 0f, 0f)
                    setColor(if (day in selectedDays) highLightBlue else if (value == 0.0) Color.LTGRAY else normalColor)
                }
                background = gd
            }
            val label = TextView(requireContext()).apply {
                text = day.toString()
                textSize = 7f
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
            }
            item.addView(bar); item.addView(label)
            chartContainer.addView(item)

            item.setOnClickListener {
                if (day in selectedDays) selectedDays.remove(day) else selectedDays.add(day)
                refreshAll()
            }
        }
        updateTopDateInfo(dailyMap)
    }

    private fun updateTopDateInfo(dailyMap: Map<Int, Double>) {
        if (selectedDays.isEmpty()) {
            chartDateTv.text = "--月--日"
            chartAmountTv.text = "¥0.00"
            btnCancelDate.visibility = View.GONE
        } else if (selectedDays.size == 1) {
            val day = selectedDays.first()
            chartDateTv.text = String.format(Locale.getDefault(), "%d月%d日", selectedMonth, day)
            chartAmountTv.text = String.format(Locale.getDefault(), "¥%.2f", dailyMap[day] ?: 0.0)
            btnCancelDate.visibility = View.VISIBLE
        } else {
            chartDateTv.text = "多日汇总"
            val total = selectedDays.sumOf { dailyMap[it] ?: 0.0 }
            chartAmountTv.text = String.format(Locale.getDefault(), "¥%.2f", total)
            btnCancelDate.visibility = View.VISIBLE
        }
    }

    private fun drawPieChart() {
        val list = BillRepository.getAllBills()
        val categoryMap = linkedMapOf<String, Double>()
        for (bill in list) {
            val p = parseDate(bill.date) ?: continue
            if (p[0] != selectedYear || p[1] != selectedMonth) continue
            if (selectedDays.isNotEmpty() && p[2] !in selectedDays) continue
            val value = if (isShowExpense) {
                if (bill.amount < 0) -bill.amount else 0.0
            } else {
                if (bill.amount > 0) bill.amount else 0.0
            }
            if (value > 0) categoryMap[bill.category] = categoryMap.getOrDefault(bill.category, 0.0) + value
        }
        val total = categoryMap.values.sum()
        if (total == 0.0) {
            chartContainer.addView(TextView(requireContext()).apply {
                text = "本月暂无${if (isShowExpense) "支出" else "收入"}数据"
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(-1, -1)
            })
            chartDateTv.text = if (selectedDays.isEmpty()) String.format(Locale.getDefault(), "%d月", selectedMonth)
            else "所选日期无数据"
            chartAmountTv.text = "¥0.00"
            btnCancelDate.visibility = if (selectedDays.isEmpty()) View.GONE else View.VISIBLE
            return
        }

        val sortedCats = categoryMap.entries.sortedByDescending { it.value }.map { it.key }
        val colors = generateColors(sortedCats.size)

        val frameLayout = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
        }

        val pieView = PieChartView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            setData(categoryMap, total, colors, sortedCats)
            setSelected(filterCategory)
            onCatClick = { selectCategoryFilter(it) }
        }
        frameLayout.addView(pieView)

        if (!filterCategory.isNullOrEmpty()) {
            val cancelBtn = TextView(requireContext()).apply {
                text = "取消筛选"
                textSize = 12f
                setTextColor(Color.WHITE)
                setPadding(10, 4, 10, 4)
                setBackgroundColor(Color.parseColor("#FF6B6B"))
                layoutParams = FrameLayout.LayoutParams(-2, -2).apply {
                    gravity = Gravity.END or Gravity.TOP
                    setMargins(10, 10, 10, 0)
                }
                setOnClickListener { cancelFilter() }
            }
            frameLayout.addView(cancelBtn)
        }

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }
        root.addView(frameLayout)

        val legendLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-2, -1)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 0, 4, 0)
        }
        sortedCats.forEachIndexed { idx, cat ->
            val amount = categoryMap[cat]!!
            val percent = String.format(Locale.getDefault(), "%.1f", amount / total * 100)
            val item = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(0, 4, 0, 4) }
            }
            val dotSize = (16 * resources.displayMetrics.density).toInt()
            val dot = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply { setMargins(0, 0, 8, 0) }
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(colors[idx]) }
            }
            val txt = TextView(requireContext()).apply {
                text = "$cat $percent%"
                textSize = 12f
                setTextColor(Color.DKGRAY)
            }
            item.addView(dot); item.addView(txt)
            item.setOnClickListener { selectCategoryFilter(cat) }
            legendLayout.addView(item)
        }
        root.addView(legendLayout)
        chartContainer.addView(root)

        chartDateTv.text = if (selectedDays.isEmpty()) String.format(Locale.getDefault(), "%d月 总计", selectedMonth)
        else "选中日期汇总"
        chartAmountTv.text = String.format(Locale.getDefault(), "¥%.2f", total)
        btnCancelDate.visibility = if (selectedDays.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun generateColors(count: Int): List<Int> {
        val base = listOf(
            Color.parseColor("#FF6B6B"),
            Color.parseColor("#4CAF50"),
            Color.parseColor("#2196F3"),
            Color.parseColor("#FF9800"),
            Color.parseColor("#9C27B0"),
            Color.parseColor("#00BCD4")
        )
        return List(count) { base[it % base.size] }
    }

    private fun getMaxDayOfMonth(y: Int, m: Int): Int {
        val c = Calendar.getInstance()
        c.set(y, m - 1, 1)
        return c.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    inner class PieChartView(context: Context) : View(context) {
        private var map: Map<String, Double> = emptyMap()
        private var total: Double = 0.0
        private var colorList: List<Int> = emptyList()
        private var cats: List<String> = emptyList()
        var onCatClick: ((String) -> Unit)? = null
        var selectedCategory: String? = null

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rectF = RectF()
        private val density = context.resources.displayMetrics.density

        fun setData(m: Map<String, Double>, t: Double, c: List<Int>, cs: List<String>) {
            map = m; total = t; colorList = c; cats = cs; invalidate()
        }

        fun setSelected(cat: String?) { selectedCategory = cat; invalidate() }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (map.isEmpty()) return
            val centerX = width / 2f; val centerY = height / 2f
            val outRadius = min(width, height) / 2f * 0.8f
            val innerRadius = outRadius * 0.55f
            rectF.set(centerX - outRadius, centerY - outRadius, centerX + outRadius, centerY + outRadius)
            var startAngle = -90f
            var selectedStart = 0f; var selectedSweep = 0f; var selectedColor = 0
            cats.forEachIndexed { idx, cat ->
                val value = map[cat] ?: 0.0
                val sweepAngle = (value / total * 360).toFloat()
                paint.color = colorList[idx]
                canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
                paint.color = Color.WHITE; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f
                canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
                paint.style = Paint.Style.FILL
                if (cat == selectedCategory) {
                    selectedStart = startAngle; selectedSweep = sweepAngle; selectedColor = colorList[idx]
                }
                startAngle += sweepAngle
            }
            paint.color = Color.WHITE
            canvas.drawCircle(centerX, centerY, innerRadius, paint)
            if (selectedCategory != null && map.containsKey(selectedCategory)) {
                val highlightRadius = outRadius + 8 * density
                val highlightRect = RectF(
                    centerX - highlightRadius, centerY - highlightRadius,
                    centerX + highlightRadius, centerY + highlightRadius
                )
                paint.color = selectedColor; paint.style = Paint.Style.STROKE; paint.strokeWidth = 6f
                canvas.drawArc(highlightRect, selectedStart, selectedSweep, false, paint)
                paint.style = Paint.Style.FILL
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN && map.isNotEmpty()) {
                val x = event.x; val y = event.y
                val centerX = width / 2f; val centerY = height / 2f
                val outRadius = min(width, height) / 2f * 0.8f
                val innerRadius = outRadius * 0.55f
                val distance = hypot((x - centerX).toDouble(), (y - centerY).toDouble()).toFloat()
                if (distance < innerRadius || distance > outRadius) return false
                var touchAngle = Math.toDegrees(atan2((y - centerY).toDouble(), (x - centerX).toDouble())).toFloat()
                touchAngle = (touchAngle + 360) % 360
                var currentAngle = -90f
                cats.forEach { cat ->
                    val sweepAngle = (map[cat]!! / total * 360).toFloat()
                    val start = ((currentAngle % 360) + 360) % 360
                    val end = ((currentAngle + sweepAngle) % 360 + 360) % 360
                    val inSector = if (start < end) touchAngle >= start && touchAngle <= end
                    else touchAngle >= start || touchAngle <= end
                    if (inSector) { onCatClick?.invoke(cat); return true }
                    currentAngle += sweepAngle
                }
            }
            return true
        }
    }

    // ========== 账户图标绑定方法 ==========
    private fun bindAccountInfo(accountName: String?, holder: BillAdapter.VH) {
        if (accountName == null) {
            holder.llAccountInfo.visibility = View.GONE
            return
        }
        val account = AccountRepository.getAll().find { it.name == accountName }
        if (account == null) {
            holder.llAccountInfo.visibility = View.GONE
            return
        }

        val displayIconIndex: Int
        val displayText: String

        if (account.parentName != null) {
            // 子账户：显示父账户图标 + 子账户名称
            val parentAccount = AccountRepository.getAll().find { it.name == account.parentName }
            displayIconIndex = parentAccount?.iconIndex ?: 0
            displayText = account.name
        } else {
            // 顶级账户：只显示图标，不显示文字
            displayIconIndex = account.iconIndex
            displayText = ""
        }

        val iconRes = accountIconList.getOrNull(displayIconIndex) ?: accountIconList[0]
        holder.ivAccountIcon.setImageResource(iconRes)

        if (displayText.isNotEmpty()) {
            holder.tvAccountName.text = displayText
            holder.tvAccountName.visibility = View.VISIBLE
        } else {
            holder.tvAccountName.visibility = View.GONE
        }

        holder.llAccountInfo.visibility = View.VISIBLE
    }

    // ========== 适配器 ==========
    inner class BillAdapter : RecyclerView.Adapter<BillAdapter.VH>() {
        private var list = mutableListOf<Bill>()
        private var openPos = -1

        fun updateData(newData: MutableList<Bill>) {
            list = newData
            notifyDataSetChanged()
        }

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvCate: TextView = itemView.findViewById(R.id.tv_category)
            val tvDate: TextView = itemView.findViewById(R.id.tv_date)
            val tvMoney: TextView = itemView.findViewById(R.id.tv_amount)
            val content: View = itemView.findViewById(R.id.content_layout)
            val btnDel: View = itemView.findViewById(R.id.btn_delete)
            val btnEdit: View = itemView.findViewById(R.id.btn_edit)
            // 新增账户信息控件
            val llAccountInfo: LinearLayout = itemView.findViewById(R.id.ll_account_info)
            val ivAccountIcon: ImageView = itemView.findViewById(R.id.iv_account_icon)
            val tvAccountName: TextView = itemView.findViewById(R.id.tv_account_name)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_bill_history, parent, false))
        }

        override fun onBindViewHolder(holder: VH, pos: Int) {
            val b = list[pos]
            holder.tvCate.text = (CategoryHelper.getEmojiMap()[b.category] ?: "") + " " + b.category
            holder.tvDate.text = b.date

            // 使用图标绑定方法替换原来的文字拼接
            bindAccountInfo(b.accountName, holder)

            holder.tvMoney.text = if (b.amount < 0) "-¥${"%.2f".format(-b.amount)}" else "+¥${"%.2f".format(b.amount)}"
            holder.tvMoney.setTextColor(if (b.amount < 0) Color.RED else Color.parseColor("#4CAF50"))

            if (openPos == pos) {
                holder.btnDel.visibility = View.VISIBLE
                holder.btnEdit.visibility = View.VISIBLE
            } else {
                holder.btnDel.visibility = View.GONE
                holder.btnEdit.visibility = View.GONE
            }

            holder.content.setOnClickListener {
                openPos = if (openPos == pos) -1 else pos
                notifyDataSetChanged()
            }

            holder.btnDel.setOnClickListener {
                BillRepository.deleteBill(b)
                list.removeAt(pos)
                notifyItemRemoved(pos)
                resetFilter()
                refreshAll()
                Toast.makeText(context, "删除成功", Toast.LENGTH_SHORT).show()
                openPos = -1
            }

            holder.btnEdit.setOnClickListener {
                val fragment = EditBillFragment.newInstance(b)
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        }

        override fun getItemCount() = list.size
    }
}