package com.selfdiscipline.app.reminder

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.selfdiscipline.app.data.Task
import com.selfdiscipline.app.data.TaskRepository
import com.selfdiscipline.app.databinding.ActivityReminderBinding
import com.selfdiscipline.app.service.ReminderNotifier
import com.selfdiscipline.app.task.TaskEditActivity
import com.selfdiscipline.app.util.TimeFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 自律提醒页（全屏弹出）：
 *  - 显示本次解锁已使用时长
 *  - 显示“该去做什么”：自动推荐下一个最优先的未完成任务
 *  - 支持「开始专注」倒计时（时长 = 任务设置的预计时长）
 *  - 支持「完成」「换一个任务」「稍后再说」
 */
class ReminderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USAGE = "extra_usage"
        const val EXTRA_TASK_ID = "extra_task_id"
        private const val STATE_DEADLINE = "state_deadline"
    }

    private lateinit var binding: ActivityReminderBinding
    private val repository by lazy { TaskRepository.get(this) }

    private var currentTask: Task? = null
    private var deadlineAt = 0L
    private var tickerJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReminderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cancelReminderNotification()
        setupButtons()
        deadlineAt = savedInstanceState?.getLong(STATE_DEADLINE, 0L) ?: 0L
        showUsageText(intent.getLongExtra(EXTRA_USAGE, 0L))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        cancelReminderNotification()
        showUsageText(intent.getLongExtra(EXTRA_USAGE, 0L))
        reloadTask()
    }

    override fun onResume() {
        super.onResume()
        reloadTask()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_DEADLINE, deadlineAt)
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        super.onDestroy()
    }

    // ---------- 初始化 ----------

    private fun cancelReminderNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ReminderNotifier.NOTIFICATION_ID)
    }

    private fun showUsageText(usageMillis: Long) {
        binding.tvRemindUsage.text = "本次解锁已使用 ${TimeFormat.formatDuration(usageMillis)}"
    }

    private fun setupButtons() {
        binding.btnStart.setOnClickListener { startFocus() }
        binding.btnComplete.setOnClickListener { completeTask() }
        binding.btnSkip.setOnClickListener { loadNextTask() }
        binding.btnLater.setOnClickListener { finish() }
        binding.btnAddTask.setOnClickListener {
            TaskEditActivity.start(this, null)
        }
    }

    // ---------- 任务加载 ----------

    private fun reloadTask() {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        lifecycleScope.launch {
            val task = if (taskId > 0) repository.getById(taskId) else repository.nextPending()
            if (task != null) showTask(task) else showNoTask()
        }
    }

    private fun showNoTask() {
        currentTask = null
        tickerJob?.cancel()
        deadlineAt = 0L
        binding.cardTask.visibility = View.GONE
        binding.tvNoTask.visibility = View.VISIBLE
        binding.btnAddTask.visibility = View.VISIBLE
        binding.btnStart.visibility = View.GONE
        binding.btnComplete.visibility = View.GONE
        binding.btnSkip.visibility = View.GONE
        binding.tvCountdown.visibility = View.GONE
        binding.progressFocus.visibility = View.GONE
        binding.tvFocusHint.visibility = View.GONE
    }

    private fun showTask(task: Task) {
        currentTask = task
        binding.cardTask.visibility = View.VISIBLE
        binding.tvNoTask.visibility = View.GONE
        binding.btnAddTask.visibility = View.GONE
        binding.btnSkip.visibility = View.VISIBLE
        binding.tvTaskTitle.text = task.title
        binding.tvTaskMeta.text = "预计时长 ${task.durationMinutes} 分钟 · 优先级 ${task.priority}"

        val remaining = deadlineAt - SystemClock.elapsedRealtime()
        if (remaining > 0) {
            // 计时进行中（如旋转屏幕后恢复）
            binding.btnStart.visibility = View.GONE
            binding.btnComplete.visibility = View.VISIBLE
            binding.tvFocusHint.text = "专注中，完成后点击「完成」"
            startTicker()
        } else {
            deadlineAt = 0L
            tickerJob?.cancel()
            binding.btnStart.visibility = View.VISIBLE
            binding.btnComplete.visibility = View.GONE
            binding.tvFocusHint.text = "点击「开始专注」进入倒计时"
            binding.tvCountdown.text = TimeFormat.formatCountdown(task.durationMinutes * 60_000L)
            binding.progressFocus.progress = 0
        }
        binding.tvCountdown.visibility = View.VISIBLE
        binding.progressFocus.visibility = View.VISIBLE
        binding.tvFocusHint.visibility = View.VISIBLE
    }

    // ---------- 专注计时 ----------

    private fun startFocus() {
        val task = currentTask ?: return
        deadlineAt = SystemClock.elapsedRealtime() + task.durationMinutes * 60_000L
        binding.btnStart.visibility = View.GONE
        binding.btnComplete.visibility = View.VISIBLE
        binding.tvFocusHint.text = "专注中，完成后点击「完成」"
        startTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = lifecycleScope.launch {
            while (true) {
                val remain = deadlineAt - SystemClock.elapsedRealtime()
                if (remain <= 0) {
                    binding.tvCountdown.text = "00:00"
                    binding.progressFocus.progress = 100
                    binding.tvFocusHint.text = "时间到！给自己一点奖励吧 🎉"
                    break
                }
                binding.tvCountdown.text = TimeFormat.formatCountdown(remain)
                val total = (currentTask?.durationMinutes ?: 30) * 60_000L
                binding.progressFocus.progress = ((total - remain) * 100 / total).toInt()
                delay(1000L)
            }
        }
    }

    private fun completeTask() {
        val task = currentTask ?: return
        tickerJob?.cancel()
        lifecycleScope.launch {
            repository.markDone(task.id)
            Toast.makeText(this@ReminderActivity, "太棒了！已完成「${task.title}」", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadNextTask() {
        val current = currentTask
        lifecycleScope.launch {
            val next = if (current != null) {
                repository.nextPendingExcluding(current.id)
            } else {
                repository.nextPending()
            }
            if (next == null) {
                Toast.makeText(this@ReminderActivity, "没有其他待办任务了", Toast.LENGTH_SHORT).show()
                return@launch
            }
            tickerJob?.cancel()
            deadlineAt = 0L
            showTask(next)
        }
    }
}
