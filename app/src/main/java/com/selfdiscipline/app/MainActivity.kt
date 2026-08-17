package com.selfdiscipline.app

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.selfdiscipline.app.data.AppSettings
import com.selfdiscipline.app.data.Task
import com.selfdiscipline.app.data.TaskRepository
import com.selfdiscipline.app.databinding.ActivityMainBinding
import com.selfdiscipline.app.reminder.ReminderActivity
import com.selfdiscipline.app.service.DailyScheduler
import com.selfdiscipline.app.service.SessionUsage
import com.selfdiscipline.app.service.UsageMonitorService
import com.selfdiscipline.app.service.UsageStatsHelper
import com.selfdiscipline.app.settings.SettingsActivity
import com.selfdiscipline.app.task.TaskListActivity
import com.selfdiscipline.app.util.TimeFormat
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 主页：
 *  - 本次解锁使用时长 + 阈值进度
 *  - 下一步任务卡片（点击进入专注页）
 *  - 底部居中圆形加号：弹窗快速添加待办
 *  - 未授权时才显示权限引导（授权后自动隐藏）
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val settings by lazy { AppSettings(this) }
    private val repository by lazy { TaskRepository.get(this) }
    private var nextTask: Task? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "通知权限未授予，提醒可能无法弹出", Toast.LENGTH_LONG).show()
            }
        }

    /** 接收后台服务广播的实时会话使用时长 */
    private val usageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val usage = intent?.getLongExtra(UsageMonitorService.EXTRA_USAGE_MILLIS, -1L) ?: -1L
            if (usage >= 0) showUsage(usage)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermissionIfNeeded()
        setupActions()
        observeTasks()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            usageReceiver,
            IntentFilter(UsageMonitorService.ACTION_UPDATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(usageReceiver)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionBanner()
        // 开关已开启时确保后台监控服务在运行（服务被杀/升级后自动恢复）
        if (settings.monitoringEnabled) {
            UsageMonitorService.start(this)
        }
        // 每日 0 点刷新任务 + 每日任务提醒（打开 App 时立即生效）
        lifecycleScope.launch {
            DailyScheduler.checkDaily(this@MainActivity, repository)
        }
        showUsage(SessionUsage.currentMs(this))
    }

    // ---------- 初始化 ----------

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun setupActions() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.cardNextTask.setOnClickListener { openFocus() }
        binding.btnStartFocus.setOnClickListener { openFocus() }
        binding.btnGoAddTask.setOnClickListener {
            startActivity(Intent(this, TaskListActivity::class.java))
        }
        binding.fabAddTodo.setOnClickListener { showQuickAddDialog() }
        binding.btnGrantPermission.setOnClickListener {
            UsageStatsHelper.openUsageAccessSettings(this)
        }
    }

    private fun openFocus() {
        val task = nextTask ?: return
        startActivity(
            Intent(this, ReminderActivity::class.java)
                .putExtra(ReminderActivity.EXTRA_USAGE, SessionUsage.currentMs(this))
                .putExtra(ReminderActivity.EXTRA_TASK_ID, task.id)
        )
    }

    /** 底部圆形加号：弹窗快速添加待办（默认 30 分钟），并可查看任务清单 */
    private fun showQuickAddDialog() {
        val input = EditText(this).apply {
            hint = "要做什么…"
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(pad, pad, pad, pad)

        AlertDialog.Builder(this)
            .setTitle("快速待办")
            .setView(input)
            .setPositiveButton("添加") { _, _ ->
                val title = input.text?.toString()?.trim().orEmpty()
                if (title.isEmpty()) {
                    Toast.makeText(this, "先输入要做的事", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    repository.add(title, 30, 0)
                    Toast.makeText(this@MainActivity, "已加入待办", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("查看任务清单") { _, _ ->
                startActivity(Intent(this, TaskListActivity::class.java))
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updatePermissionBanner() {
        val granted = UsageStatsHelper.hasUsageAccess(this)
        // 仅未授权时显示，授权成功后自动隐藏
        binding.cardPermission.visibility = if (granted) View.GONE else View.VISIBLE
    }

    // ---------- 任务 ----------

    private fun observeTasks() {
        lifecycleScope.launch {
            repository.allTasks.collectLatest { tasks ->
                val pending = tasks.filter { !it.done }
                val completedToday = tasks.count {
                    it.done && it.doneAt != null && it.doneAt >= startOfToday()
                }
                nextTask = pending.firstOrNull()
                val next = nextTask

                binding.tvTodayStats.text =
                    "今日已完成 $completedToday · 待办 ${pending.size}"

                if (next != null) {
                    binding.cardNextTask.visibility = View.VISIBLE
                    binding.cardNoTask.visibility = View.GONE
                    binding.tvNextTitle.text = next.title
                    binding.tvNextMeta.text =
                        "预计 ${next.durationMinutes} 分钟 · 还有 ${pending.size} 个待办未完成"
                } else {
                    binding.cardNextTask.visibility = View.GONE
                    binding.cardNoTask.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun startOfToday(): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    // ---------- 使用时长 ----------

    private fun showUsage(usageMillis: Long) {
        // 大数字 + 小号单位（Spannable）
        binding.tvUsage.text = usageText(usageMillis)

        val threshold = settings.dailyThresholdMinutes * 60_000L
        val progress = if (threshold > 0) (usageMillis.toFloat() / threshold).coerceIn(0f, 1f) else 0f
        binding.progressUsage.setProgressCompat((progress * 100).toInt(), true)

        binding.tvUsageHint.text = when {
            !settings.monitoringEnabled -> "自律提醒未开启 · 请到设置页开启"
            usageMillis <= 0L -> "解锁手机后开始计时"
            usageMillis >= threshold -> "已达阈值 ${settings.dailyThresholdMinutes} 分钟"
            else -> "距提醒还差 ${TimeFormat.formatDuration(threshold - usageMillis)}"
        }
    }

    /** 数字大号、单位小号的展示文案 */
    private fun usageText(millis: Long): CharSequence {
        val text = TimeFormat.formatDuration(millis) // 如 45分钟 / 2小时15分钟
        val unitLen = when {
            text.endsWith("分钟") || text.endsWith("小时") -> 2
            else -> 0
        }
        if (unitLen == 0) return text
        return android.text.SpannableString(text).apply {
            setSpan(
                android.text.style.RelativeSizeSpan(0.45f),
                text.length - unitLen, text.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                android.text.style.ForegroundColorSpan(
                    ContextCompat.getColor(this@MainActivity, R.color.text_secondary)
                ),
                text.length - unitLen, text.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
}
