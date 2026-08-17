package com.selfdiscipline.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 待办任务（自律计划目录中的一项）
 *
 * @param id             主键
 * @param title          任务名称（我该去做什么）
 * @param durationMinutes 预计专注时长（分钟）
 * @param priority       优先级，数字越小越优先
 * @param createdAt      创建时间
 * @param done           是否已完成
 * @param doneAt         完成时间
 */
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val durationMinutes: Int = 30,
    val priority: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val done: Boolean = false,
    val doneAt: Long? = null
)
