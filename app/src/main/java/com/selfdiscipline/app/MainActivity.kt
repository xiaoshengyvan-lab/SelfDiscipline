package com.selfdiscipline.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.selfdiscipline.app.data.AppSettings
import com.selfdiscipline.app.data.Task
import com.selfdiscipline.app.data.TaskRepository
import com.selfdiscipline.app.databinding.ActivityMainBinding
import com.selfdiscipline.app.service.DailyScheduler
import com.selfdiscipline.app.service.ReminderNotifier
import com.selfdiscipline.app.service.SessionUsage
import com.selfdiscipline.app.service.UsageMonitorService
import com.selfdiscipline.app.service.UsageStatsHelper
import com.selfdiscipline.app.settings.SettingsActivity
import com.selfdiscipline.app.task.TaskEditActivity
import com.selfdiscipline.app.task.TaskListActivity
import com.selfdiscipline.app.util.TimeFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 首页（专注表盘页）：
 *  - 核心圆形表盘：点击开始/暂停/继续专注，长按标记完成
 *  - 表盘内部：待办名称 / 倒计时 / 状态提示
 *  - 圆环进度 = 手机使用时长进度（本次解锁已用 / 阈值），不是倒计时进度
 *  - 权限提示条（仅未授权时显示）、今日统计
 *  - 底部：＋ 新增待办
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val MODE_IDLE = 0
        private const val MODE_COUNTING = 1
        private const val MODE_PAUSED = 2
    }

    private lateinit var binding: ActivityMainBinding
    private val settings by lazy { AppSettings(this) }
    private val repository by lazy { TaskRepository.get(this) }

    private var nextTask: Task? = null
    private var currentTask: Task? = null
    private var remainingMs = 0L
    private var deadlineAt = 0L
    private var mode = MODE_IDLE
    private var tickerJob: Job? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "通知权限未授予，提醒可能无法弹出", Toast.LENGTH_LONG).show()
            }
        }

    /** 接收后台服务广播的实时会话使用时长，驱动圆环进度 */
    private val usageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val usage = intent?.getLongExtra(UsageMonitorService.EXTRA_USAGE_MILLIS, -1L) ?: -1L
            if (usage >= 0) updateRingUsage(usage)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermissionIfNeeded()
        setupActions()
        setupDial()
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
        if (settings.monitoringEnabled) {
            UsageMonitorService.start(this)
        }
        lifecycleScope.launch {
            DailyScheduler.checkDaily(this@MainActivity, repository)
        }
        refreshFocusState()
        updateRingUsage()
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        super.onDestroy()
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
        binding.fabAddTask.setOnClickListener {
            TaskEditActivity.start(this, null)
        }
        binding.btnGrantPermission.setOnClickListener {
            UsageStatsHelper.openUsageAccessSettings(this)
        }
    }

    private fun setupDial() {
        // 点击表盘：开始 / 暂停 / 继续
        binding.dialContainer.setOnClickListener { onDialTap() }
        // 长按表盘：标记完成
        binding.dialContainer.setOnLongClickListener {
            completeTask()
            true
        }
    }

    private fun updatePermissionBanner() {
        val granted = UsageStatsHelper.hasUsageAccess(this)
        binding.cardPermission.visibility = if (granted) View.GONE else View.VISIBLE
    }

    // ---------- 专注表盘 ----------

    private fun refreshFocusState() {
        val now = SystemClock.elapsedRealtime()
        when {
            settings.focusDeadlineElapsed > now -> {
                deadlineAt = settings.focusDeadlineElapsed
                mode = MODE_COUNTING
                loadFocusedTask()
            }
            settings.focusRemainingMs > 0L && settings.focusTaskId >= 0L -> {
                remainingMs = settings.focusRemainingMs
                mode = MODE_PAUSED
                loadFocusedTask()
            }
            else -> {
                mode = MODE_IDLE
                deadlineAt = 0L
                remainingMs = 0L
                render()
            }
        }
    }

    private fun loadFocusedTask() {
        lifecycleScope.launch {
            currentTask = repository.getById(settings.focusTaskId)
            render()
            if (mode == MODE_COUNTING && deadlineAt > SystemClock.elapsedRealtime()) {
                startTicker()
            }
        }
    }

    private fun onDialTap() {
        when (mode) {
            MODE_IDLE -> startFocus()
            MODE_COUNTING -> pauseFocus()
            MODE_PAUSED -> resumeFocus()
        }
    }

    private fun startFocus() {
        val task = currentTask ?: nextTask ?: return
        currentTask = task
        remainingMs = task.durationMinutes * 60_000L
        settings.focusTaskId = task.id
        settings.focusTaskTitle = task.title
        settings.focusTaskDurationMinutes = task.durationMinutes
        settings.focusRemainingMs = remainingMs
        resumeFocus()
    }

    private fun resumeFocus() {
        if (remainingMs <= 0L) return
        deadlineAt = SystemClock.elapsedRealtime() + remainingMs
        settings.focusDeadlineElapsed = deadlineAt
        mode = MODE_COUNTING
        startTicker()
    }

    private fun pauseFocus() {
        tickerJob?.cancel()
        remainingMs = (deadlineAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        settings.focusRemainingMs = remainingMs
        settings.focusDeadlineElapsed = 0L
        mode = MODE_PAUSED
        render()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = lifecycleScope.launch {
            while (true) {
                val remain = deadlineAt - SystemClock.elapsedRealtime()
                if (remain <= 0L) {
                    finishFocus()
                    break
                }
                render()
                delay(250L)
            }
        }
        render()
    }

    /** 倒计时正常走完：重置并弹完成提醒 */
    private fun finishFocus() {
        tickerJob?.cancel()
        val title = currentTask?.title ?: settings.focusTaskTitle.ifEmpty { "专注" }
        settings.clearFocus()
        remainingMs = 0L
        deadlineAt = 0L
        mode = MODE_IDLE
        ReminderNotifier.showFocusFinished(this, title)
        refreshFocusState()
    }

    /** 长按表盘：标记当前任务完成，并切换到下一个待办任务 */
    private fun completeTask() {
        val task = currentTask ?: return
        tickerJob?.cancel()
        settings.clearFocus()
        lifecycleScope.launch {
            if (task.id > 0) repository.markDone(task.id)
            // 完成的同时切换到下一个待办任务
            val next = repository.nextPending()
            currentTask = next
            remainingMs = 0L
            deadlineAt = 0L
            mode = MODE_IDLE
            val msg = if (next != null) {
                "已完成「${task.title}」，下一个：${next.title}"
            } else {
                "已完成「${task.title}」"
            }
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            render()
        }
    }

    // ---------- 渲染 ----------

    private fun render() {
        val task = currentTask ?: nextTask
        binding.tvDialTask.text = task?.title ?: "暂无待办"

        when (mode) {
            MODE_COUNTING -> {
                val remain = (deadlineAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                binding.tvDialCountdown.text = TimeFormat.formatCountdown(remain)
                binding.tvDialHint.text = "点击暂停"
            }
            MODE_PAUSED -> {
                binding.tvDialCountdown.text = TimeFormat.formatCountdown(remainingMs)
                binding.tvDialHint.text = "点击继续"
            }
            else -> {
                // 未开始：不显示倒计时，只显示提示
                binding.tvDialCountdown.text = if (task == null) "暂无待办" else "点击开始专注"
                binding.tvDialCountdown.textSize = if (task == null) 24f else 30f
                binding.tvDialHint.text =
                    if (task == null) "点击下方 + 添加任务" else "点击表盘开始 / 暂停"
            }
        }
        updateRingUsage()
    }

    /** 圆环进度 = 手机使用时长进度（本次解锁已用 / 阈值），与倒计时无关 */
    private fun updateRingUsage(usageMillis: Long = SessionUsage.currentMs(this)) {
        val threshold = settings.dailyThresholdMinutes * 60_000L
        val percent = if (threshold > 0) {
            (usageMillis.toFloat() / threshold).coerceIn(0f, 1f) * 100
        } else 0f
        binding.ringProgress.setProgressCompat(percent.toInt(), true)
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
                binding.tvTodayStats.text =
                    "今日已完成 $completedToday · 待办 ${pending.size}"
                if (mode == MODE_IDLE) {
                    render()
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
}
