package com.project.oscarbt

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.*
import android.support.v4.app.NotificationCompat
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.util.*

class SerialService : Service(), SerialListener {

    private val mainLooper: Handler = Handler(Looper.getMainLooper())
    private val binder: IBinder = SerialBinder()
    private val queue1: Queue<QueueItem> = LinkedList()
    private val queue2: Queue<QueueItem> = LinkedList()

    private var listener : SerialListener? = null
    private var connected : Boolean = false
    private var notificationMsg : String? = null

    inner class SerialBinder : Binder() {
        fun getService() : SerialService { return this@SerialService }
    }

    enum class QueueType {Connect, ConnectError, Read, IoError}

    inner class QueueItem {
        var type : QueueType? = null
        var data : ByteArray? = null
        var e : Exception? = null

        constructor(type: QueueType, data: ByteArray?, e: Exception?) {
            this.type = type
            this.data = data
            this.e = e
        }
    }

    override fun onDestroy() {
        cancelNotification()
        disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    fun connect(listener : SerialListener, notificationMsg : String){
        this.listener = listener
        connected = true
        this.notificationMsg = notificationMsg
    }

    fun disconnect(){
        listener = null
        connected = false
        notificationMsg = null
    }

    fun attach(listener : SerialListener){
        if(Looper.getMainLooper().thread != Thread.currentThread())
            throw IllegalArgumentException("Not in main thread")
        cancelNotification()
        // use synchronized() to prevent new items in queue2
        // new items will not be added to queue1 because mainLooper.post and attach() run in main thread
        if(connected){
            synchronized(this){
                this.listener = listener
            }
        }
        for (item in queue1) {
            when (item.type) {
                SerialService.QueueType.Connect -> listener.onSerialConnect()
                SerialService.QueueType.ConnectError -> listener.onSerialConnectError(item.e!!)
                SerialService.QueueType.Read -> listener.onSerialRead(item.data!!)
                SerialService.QueueType.IoError -> listener.onSerialIoError(item.e!!)
            }
        }
        for (item in queue2) {
            when (item.type) {
                SerialService.QueueType.Connect -> listener.onSerialConnect()
                SerialService.QueueType.ConnectError -> listener.onSerialConnectError(item.e!!)
                SerialService.QueueType.Read -> listener.onSerialRead(item.data!!)
                SerialService.QueueType.IoError -> listener.onSerialIoError(item.e!!)
            }
        }
        queue1.clear()
        queue2.clear()
    }

    fun detach(){
        if(connected)
            createNotification()
        // items already in event queue (posted before detach() to mainLooper) will end up in queue1
        // items occurring later, will be moved directly to queue2
        // detach() and mainLooper.post run in the main thread, so all items are caught
        listener = null
    }

    fun createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nc = NotificationChannel(Constants.NOTIFICATION_CHANNEL, "Background service", NotificationManager.IMPORTANCE_LOW)
            nc.setShowBadge(false)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(nc)
        }
        val disconnectIntent = Intent()
            .setAction(Constants.INTENT_ACTION_DISCONNECT)
        val restartIntent = Intent()
            .setClassName(this, Constants.INTENT_CLASS_MAIN_ACTIVITY)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        val disconnectPendingIntent = PendingIntent.getBroadcast(this, 1, disconnectIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val restartPendingIntent = PendingIntent.getActivity(this, 1, restartIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(resources.getColor(R.color.colorPrimary))
            .setContentTitle(resources.getString(R.string.app_name))
            .setContentText(notificationMsg)
            .setContentIntent(restartPendingIntent)
            .setOngoing(true)
            .addAction(NotificationCompat.Action(R.drawable.ic_clear_white_24dp, "Disconnect", disconnectPendingIntent))
        // @drawable/ic_notification created with Android Studio -> New -> Image Asset using @color/colorPrimaryDark as background color
        // Android < API 21 does not support vectorDrawables in notifications, so both drawables used here, are created as .png instead of .xml
        val notification = builder.build()
        startForeground(Constants.NOTIFY_MANAGER_START_FOREGROUND_SERVICE, notification)


    }

    fun cancelNotification(){
        stopForeground(true)
    }

    override fun onSerialConnect(){
        if(connected){
            synchronized(this) {
                if (listener != null) {
                    mainLooper.post {
                        if (listener != null) {
                            listener!!.onSerialConnect()
                        } else {
                            queue1.add(QueueItem(QueueType.Connect, null, null))
                        }
                    }
                } else {
                    queue2.add(QueueItem(QueueType.Connect, null, null))
                }
            }
        }
    }

    override fun onSerialConnectError(e: Exception) {
        if (connected) {
            synchronized(this) {
                if (listener != null) {
                    mainLooper.post {
                        if (listener != null) {
                            listener!!.onSerialConnectError(e)
                        } else {
                            queue1.add(QueueItem(QueueType.ConnectError, null, e))
                            cancelNotification()
                            disconnect()
                        }
                    }
                } else {
                    queue2.add(QueueItem(QueueType.ConnectError, null, e))
                    cancelNotification()
                    disconnect()
                }
            }
        }
    }

    override fun onSerialRead(data: ByteArray) {
        if (connected) {
            synchronized(this) {
                if (listener != null) {
                    mainLooper.post {
                        if (listener != null) {
                            listener!!.onSerialRead(data)
                        } else {
                            queue1.add(QueueItem(QueueType.Read, data, null))
                        }
                    }
                } else {
                    queue2.add(QueueItem(QueueType.Read, data, null))
                }
            }
        }
    }

    override fun onSerialIoError(e: Exception) {
        if (connected) {
            synchronized(this) {
                if (listener != null) {
                    mainLooper.post {
                        if (listener != null) {
                            listener!!.onSerialIoError(e)
                        } else {
                            queue1.add(QueueItem(QueueType.IoError, null, e))
                            cancelNotification()
                            disconnect()
                        }
                    }
                } else {
                    queue2.add(QueueItem(QueueType.IoError, null, e))
                    cancelNotification()
                    disconnect()
                }
            }
        }
    }


}