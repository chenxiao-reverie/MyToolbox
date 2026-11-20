package com.toolbox.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.toolbox.core.model.JobStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 前台服务只负责两件事：让进程保持前台优先级、把进度显示在通知栏。
 * 真正的执行在 JobRepository 里，所以服务被杀也不会影响任务状态一致性。
 */
class TaskService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "service created")
        createChannel()

        scope.launch {
            AppGraph.jobs.jobs.collectLatest { jobs ->
                val running = jobs.firstOrNull { it.status == JobStatus.RUNNING }
                val queued = jobs.count { it.status == JobStatus.QUEUED }

                if (running == null && queued == 0) {
                    Log.d(TAG, "idle, stopping service")
                    stopSelf()
                    return@collectLatest
                }

                val text = when {
                    running != null -> {
                        val percent = (running.progress.fraction * 100).toInt()
                        "${running.source.displayName} · $percent%"
                    }

                    else -> "$queued 个任务排队中"
                }
                notifyForeground(text, running?.progress?.fraction ?: 0f)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_ALL) {
            AppGraph.jobs.cancelAll()
            return START_NOT_STICKY
        }
        notifyForeground("准备中", 0f)
        AppGraph.jobs.resumePending()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "service destroyed")
        scope.cancel()
        super.onDestroy()
    }

    private fun notifyForeground(text: String, fraction: Float) {
        val type = if (Build.VERSION.SDK_INT >= 29) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(text, fraction),
                type
            )
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }
    }

    private fun buildNotification(text: String, fraction: Float): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, TaskService::class.java).setAction(ACTION_CANCEL_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_toolbox)
            .setContentTitle("正在转换")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setProgress(100, (fraction * 100).toInt().coerceIn(0, 100), false)
            .addAction(0, "取消全部", cancel)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "转换任务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "转换进度与完成提醒"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "TaskService"
        const val CHANNEL_ID = "conversion"
        const val NOTIFICATION_ID = 1001
        const val ACTION_CANCEL_ALL = "com.toolbox.app.action.CANCEL_ALL"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TaskService::class.java)
            )
        }
    }
}