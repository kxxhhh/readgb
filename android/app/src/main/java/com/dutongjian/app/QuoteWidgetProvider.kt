package com.dutongjian.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dutongjian.app.data.local.ItemDao
import com.dutongjian.app.data.local.toDomain
import com.dutongjian.app.domain.model.OfflineSeed
import com.dutongjian.app.domain.model.ReadingItem
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface QuoteWidgetEntryPoint {
    fun itemDao(): ItemDao
}

class QuoteWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAsync(context, manager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, QuoteWidgetProvider::class.java)
            refreshAsync(context, manager, manager.getAppWidgetIds(component))
        }
    }

    private fun refreshAsync(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val item = runCatching {
                    EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        QuoteWidgetEntryPoint::class.java,
                    ).itemDao().randomImportedItem()?.toDomain()
                }.getOrNull() ?: fallbackItem()
                withContext(Dispatchers.Main.immediate) {
                    widgetIds.forEach { widgetId -> update(context, manager, widgetId, item) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun fallbackItem(): ReadingItem? {
        val items = OfflineSeed.items
        return items.getOrNull((java.time.LocalDate.now().dayOfYear - 1).mod(items.size.coerceAtLeast(1)))
    }

    private fun update(context: Context, manager: AppWidgetManager, widgetId: Int, item: ReadingItem?) {
        val views = RemoteViews(context.packageName, R.layout.quote_widget).apply {
            setTextViewText(R.id.widget_title, item?.title ?: "读通鉴")
            setTextViewText(R.id.widget_quote, item?.content?.take(110).orEmpty().ifBlank { "打开读通鉴开始阅读" })
            setTextViewText(R.id.widget_meta, item?.dynasty.orEmpty())
            val openIntent = PendingIntent.getActivity(
                context,
                widgetId,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            setOnClickPendingIntent(R.id.widget_root, openIntent)
        }
        manager.updateAppWidget(widgetId, views)
    }

    companion object {
        const val ACTION_REFRESH = "com.dutongjian.app.action.REFRESH_QUOTE_WIDGET"
    }
}
