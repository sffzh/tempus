package cn.sffzh.tempus.service

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaBrowser
import com.cappielloantonio.tempo.repository.QueueRepository
import com.cappielloantonio.tempo.service.MediaManager
import com.cappielloantonio.tempo.subsonic.models.Child
import com.cappielloantonio.tempo.util.MappingUtil
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T> ListenableFuture<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addListener({
            try {
                cont.resume(get())
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }, MoreExecutors.directExecutor())

        cont.invokeOnCancellation { cancel(true) }
    }

object QueueEngine {

    //如果后面存在多 MapStratege、多Repository的情况，可以将这两个变量放到sqt
    private val mappingStrategy: MediaItemMappingStrategy = DefaultMediaItemMappingStrategy
    private val queueRepository: QueueRepository = QueueRepository()
    var justStarted: AtomicBoolean = AtomicBoolean(false)

    /**
     * 核心流程：
     * 1. 等待 MediaBrowser 初始化
     * 2. 后台构建 MediaItems
     * 3. 主线程操作播放器
     * 4. 后台写入数据库
     */
    suspend fun queueOperation(
        future: ListenableFuture<MediaBrowser?>,
        media: List<Child?>,
        applyToPlayer: suspend (MediaBrowser, List<MediaItem>) -> Unit,
        writeToDatabase: (suspend () -> Unit)?
    ) {
        // 1. 等待 MediaBrowser 初始化
        val browser = future.await() ?: return

        // 2. 后台构建 MediaItems
        val items = withContext(Dispatchers.Default) {
            mappingStrategy.map(media)
        }

        // 3. 主线程操作播放器
        withContext(Dispatchers.Main) {
            applyToPlayer(browser, items)
        }

        // 4. 后台写入数据库
        writeToDatabase ?: return
        withContext(Dispatchers.IO) {
            writeToDatabase()
        }
    }

    class MediaItemsInfo(val mediaItems:List<MediaItem>, val startIndex:Int, val timestamp:Long)

    suspend fun queueOperation(
        future: ListenableFuture<MediaBrowser?>,
        queryDB: suspend () -> MediaItemsInfo,
        applyToPlayer: suspend (MediaBrowser, MediaItemsInfo) -> Unit,
        writeToDatabase: (suspend () -> Unit)?
    ) {
        // 1. 等待 MediaBrowser 初始化
        val browser = future.await() ?: return

        // 2. 后台构建 MediaItems
        val items = withContext(Dispatchers.IO) {
            queryDB()
        }

        // 3. 主线程操作播放器
        withContext(Dispatchers.Main) {
            applyToPlayer(browser, items)
        }

        // 4. 后台写入数据库
        writeToDatabase ?: return
        withContext(Dispatchers.IO) {
            writeToDatabase()
        }
    }

    suspend fun queueOperation(
        future: ListenableFuture<MediaBrowser?>,
        applyToPlayer: suspend (MediaBrowser) -> Unit,
        writeToDatabase: (suspend () -> Unit)?
    ) {
        // 1. 等待 MediaBrowser 初始化
        val browser = future.await() ?: return

        // 3. 主线程操作播放器
        withContext(Dispatchers.Main) {
            applyToPlayer(browser)
        }

        // 4. 后台写入数据库
        writeToDatabase ?: return
        withContext(Dispatchers.IO) {
            writeToDatabase()
        }
    }

    suspend fun reset(future: ListenableFuture<MediaBrowser?>) = queueOperation(
        future = future,
        applyToPlayer = {mediaBrowser ->
            if (mediaBrowser.isPlaying()) {
                mediaBrowser.pause()
            }
            mediaBrowser.stop()
            mediaBrowser.clearMediaItems()
        },
        writeToDatabase = {
            queueRepository.deleteAll()
        }
    )

    suspend fun hide(future: ListenableFuture<MediaBrowser?>) = queueOperation(
        future = future,
        applyToPlayer = {mediaBrowser ->
            if (mediaBrowser.isPlaying()) {
                mediaBrowser.pause()
            }
        },
        null
    )

    val initOpretion = { browser: MediaBrowser, itemsInfo: MediaItemsInfo ->
        browser.clearMediaItems()
        browser.setMediaItems(itemsInfo.mediaItems)
        browser.seekTo(itemsInfo.startIndex, itemsInfo.timestamp)
        browser.prepare()
    }

    suspend fun init(future: ListenableFuture<MediaBrowser?>, media: List<Child?>) = queueOperation(
        future = future,
        queryDB = {
            val items = mappingStrategy.map(media)
            val startIndex = queueRepository.lastPlayedMediaIndex
            val timestamp = queueRepository.lastPlayedMediaTimestamp
            MediaItemsInfo(items, startIndex, timestamp)
        },
        applyToPlayer = initOpretion,
        writeToDatabase = {
            queueRepository.insertAll(media, true, 0)
        }
    )

    suspend fun check(future: ListenableFuture<MediaBrowser?>) = queueOperation(
        future = future,
        queryDB = {
            val items = mappingStrategy.map(queueRepository.media)
            val startIndex = queueRepository.lastPlayedMediaIndex
            val timestamp = queueRepository.lastPlayedMediaTimestamp
            MediaItemsInfo(items, startIndex, timestamp)
        },
        applyToPlayer = { mediaBrowser, itemInfo ->
            if (mediaBrowser.mediaItemCount < 1  && itemInfo.mediaItems.isNotEmpty()) {
                initOpretion(mediaBrowser, MediaItemsInfo(itemInfo.mediaItems, itemInfo.startIndex, itemInfo.timestamp))
            }
        },
        null
    )

    /**
     * startQueue：从头播放队列
     */
    suspend fun startQueue(
        future: ListenableFuture<MediaBrowser?>,
        media: List<Child?>,
        startIndex: Int
    ) = queueOperation(
        future = future,
        media = media,

        applyToPlayer = { browser, items ->
            justStarted.set(true)

            browser.setMediaItems(items, startIndex, 0)
            browser.prepare()

            val listener = object : Player.Listener {
                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    val count = browser.mediaItemCount
                    if (startIndex in 0 until count) {
                        browser.seekTo(startIndex, 0)
                        browser.play()
                    }
                    browser.removeListener(this)
                }
            }
            browser.addListener(listener)
        },

        writeToDatabase = {
            queueRepository.insertAll(media, true, 0)
        }
    )

    suspend fun startQueue(
        future: ListenableFuture<MediaBrowser?>,
        media: List<Child?>,
    ) = queueOperation(
        future = future,
        media = media,

        applyToPlayer = { browser, items ->
            justStarted.set(true)
            browser.setMediaItems(items)
            browser.prepare()
            browser.play()
        },
        writeToDatabase = {
            queueRepository.insertAll(media,true,0)
        }
    )

    suspend fun playDownloadedMediaItem(
        future: ListenableFuture<MediaBrowser?>,
        mediaItem: MediaItem,
    ) = queueOperation(
        future = future,
        applyToPlayer = { browser ->
            justStarted.set(true);
            browser.setMediaItem(mediaItem);
            browser.prepare();
            browser.play();
        },
        writeToDatabase = {
            queueRepository.deleteAll();
        }
    )

}

fun interface MediaItemMappingStrategy {
    suspend fun map(children: List<Child?>): List<MediaItem>
}

object DefaultMediaItemMappingStrategy : MediaItemMappingStrategy {
    override suspend fun map(children: List<Child?>): List<MediaItem> {
        return MappingUtil.mapMediaItems(children)
    }
}
