package com.toolbox.app

import android.content.Context
import androidx.room.Room
import com.toolbox.core.media.UriProber
import com.toolbox.core.model.ToolCatalog
import com.toolbox.core.storage.MediaStoreOutputStore
import com.toolbox.core.storage.OutputStore
import com.toolbox.core.task.JobRepository
import com.toolbox.core.task.db.MIGRATION_2_3
import com.toolbox.core.task.db.ToolboxDatabase
import com.toolbox.engine.platform.FtypRemuxEngine
import com.toolbox.engine.platform.PlatformRemuxEngine

/**
 * 唯一知道"用哪个引擎、写到哪里、存到哪张表"的地方。
 * feature 层只拿到 StateFlow 和回调，不 import 任何引擎实现。
 */
object AppGraph {

    lateinit var jobs: JobRepository
        private set

    lateinit var prober: UriProber
        private set

    lateinit var outputStore: OutputStore
        private set

    private var database: ToolboxDatabase? = null

    fun init(context: Context) {
        if (::jobs.isInitialized) return
        val appContext = context.applicationContext

        val db = Room.databaseBuilder(appContext, ToolboxDatabase::class.java, "toolbox.db")
            .addMigrations(MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
        database = db

        prober = UriProber(appContext)
        val store = MediaStoreOutputStore(appContext)
        outputStore = store
        jobs = JobRepository(
            engines = listOf(
                FtypRemuxEngine(appContext),
                PlatformRemuxEngine(appContext)
            ),
            outputStore = store,
            dao = db.jobDao(),
            toolResolver = { id -> ToolCatalog.tools.firstOrNull { it.id.value == id } },
            onWorkEnqueued = {
                // 从后台恢复任务时系统可能拒绝启动 FGS，失败就让用户手动继续
                runCatching { TaskService.start(appContext) }
            }
        )
        jobs.restore()
    }
}