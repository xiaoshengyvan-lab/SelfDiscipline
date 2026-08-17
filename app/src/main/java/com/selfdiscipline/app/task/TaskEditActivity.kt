package com.selfdiscipline.app.task

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.selfdiscipline.app.data.TaskRepository
import com.selfdiscipline.app.databinding.ActivityTaskEditBinding
import kotlinx.coroutines.launch

/**
 * 添加 / 编辑任务：任务名称、预计时长（分钟）、优先级
 */
class TaskEditActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TASK_ID = "extra_task_id"

        /** taskId 为 null 表示新建 */
        fun start(context: Context, taskId: Long?) {
            context.startActivity(
                Intent(context, TaskEditActivity::class.java).apply {
                    if (taskId != null) putExtra(EXTRA_TASK_ID, taskId)
                }
            )
        }
    }

    private lateinit var binding: ActivityTaskEditBinding
    private val repository by lazy { TaskRepository.get(this) }
    private var editingTaskId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        // 右上角：任务清单入口
        binding.btnTaskList.setOnClickListener {
            startActivity(Intent(this, TaskListActivity::class.java))
        }

        editingTaskId = intent.getLongExtra(EXTRA_TASK_ID, -1L).takeIf { it > 0 }

        if (editingTaskId != null) {
            binding.toolbar.title = "编辑任务"
            lifecycleScope.launch {
                repository.getById(editingTaskId!!)?.let { task ->
                    binding.etTitle.setText(task.title)
                    binding.etDuration.setText(task.durationMinutes.toString())
                    binding.etPriority.setText(task.priority.toString())
                }
            }
        }

        binding.btnSave.setOnClickListener { save() }
    }

    private fun save() {
        val title = binding.etTitle.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            Toast.makeText(this, "请输入任务名称", Toast.LENGTH_SHORT).show()
            return
        }
        val duration = (binding.etDuration.text?.toString()?.toIntOrNull() ?: 30).coerceIn(1, 1440)
        val priority = binding.etPriority.text?.toString()?.toIntOrNull() ?: 0

        lifecycleScope.launch {
            val existing = editingTaskId?.let { repository.getById(it) }
            if (existing != null) {
                repository.update(
                    existing.copy(
                        title = title,
                        durationMinutes = duration,
                        priority = priority
                    )
                )
            } else {
                repository.add(title, duration, priority)
            }
            finish()
        }
    }
}
