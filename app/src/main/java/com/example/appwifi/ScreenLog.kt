package com.example.appwifi

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.list_item.view.*
import timber.log.Timber
import java.util.*


enum class LogType  {
    TO_LOG,
    TO_HISTORY;
}

enum class LogStatus  {
    DISABLED,
    ENABLED;
}


@SuppressLint("StaticFieldLeak")
object  ScreenLog {

    var status = LogStatus.ENABLED
    private var linesToLog: ArrayList<String> = ArrayList()
    private var linesToHistory: ArrayList<String> = ArrayList()
    private var MAX_LOG_LINES=300

    private var logView: RecyclerView? = null
    private var myActivity: AppCompatActivity? = null
    private var myContext: Context? = null
    private var logAdapter : LogAdapter? = null
    private var historyAdapter : HistoryAdapter? = null
    var screenLogHandler = android.os.Handler()


    var logMainList = ArrayList<String>()
    var historyMainList = ArrayList<String>()

    fun start(mainActivity: AppCompatActivity, context: Context, viewLog: RecyclerView, viewHistory: RecyclerView) {
        myActivity = mainActivity
        myContext = context
        logView = viewLog
        logAdapter = LogAdapter(myContext!!, logMainList)
        historyAdapter = HistoryAdapter(myContext!!, historyMainList)

        viewLog.layoutManager = LinearLayoutManager(mainActivity)
        viewLog.adapter = logAdapter

        viewHistory.layoutManager = LinearLayoutManager(mainActivity)
        viewHistory.adapter = historyAdapter

        enable()
    }

    private var updateMainViewRunnable = Runnable {
//        Thread.currentThread().priority = 1
        updateLogMainView()
        updateHistoryMainView()
    }


    fun enable() {
        if ( logView != null ) {
            status = LogStatus.ENABLED
        }
        add(LogType.TO_LOG, "Start")
    }

    fun add(logType : LogType, message : String) {
        val c = Calendar.getInstance()
        val strHora1 =  String.format("%02d:%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND))

        var newString = "$strHora1 - $message"


        if ( message.isEmpty() ) {
            newString = "--------------"
        }

        Timber.i(message)

        if ( status == LogStatus.ENABLED ) {
            var delay = 100L
            if ( logType == LogType.TO_HISTORY) {
                linesToHistory.add(newString)
                // if we have lines to show, do imediate
                if ( linesToHistory.size > 0 ) {
                    delay=10L
                }
            } else {
                linesToLog.add(newString)
                // if we have to much lines to show, do imediate
                if ( linesToLog.size > 10 ) {
                    delay=10L
                }
            }

            screenLogHandler.removeCallbacks(updateMainViewRunnable)
            screenLogHandler.postDelayed(updateMainViewRunnable, delay)
        }
    }


    fun tag(logType : LogType) {
        add(logType, "")
    }

    fun clear(logType : LogType) {
        if ( logType == LogType.TO_HISTORY) {
            val lines = historyAdapter!!.getItemCount()
            linesToHistory.clear()
            historyMainList.clear()
            historyAdapter!!.notifyItemRangeRemoved(0, lines)
        } else {
            val lines = logAdapter!!.getItemCount()
            linesToLog.clear()
            logMainList.clear()
            logAdapter!!.notifyItemRangeRemoved(0, lines)
        }
    }


    fun setLogLines(size:Int) {
        MAX_LOG_LINES = size
    }


    fun updateLogMainView() {
        val linesToMove = linesToLog.size

        if ( linesToMove > 0 ) {
            // Copy lines from localList to mainList
            for (line in 0 until linesToMove ) {
                if (logMainList.size >= MAX_LOG_LINES) {
                    logMainList.removeAt(0)
                    logAdapter!!.notifyItemRangeRemoved(0, 1)
                }
                logMainList.add(linesToLog[0])
                linesToLog.removeAt(0)
            }
            logAdapter!!.notifyDataSetChanged()
            (myActivity as MainActivity).log_recycler_view.smoothScrollToPosition(logAdapter!!.getItemCount() - 1)
        }
    }

    fun updateHistoryMainView() {
        val linesToMove = linesToHistory.size

        if ( linesToMove > 0) {
            // Copy lines from localList to mainList
            for (line in 0 until linesToMove ) {
                if (historyMainList.size >= MAX_LOG_LINES) {
                    historyMainList.removeAt(0)
                    historyAdapter!!.notifyItemRangeRemoved(0, 1)
                }
                historyMainList.add(linesToHistory[0])
                linesToHistory.removeAt(0)
            }
            historyAdapter!!.notifyDataSetChanged()
            (myActivity as MainActivity).history_recycler_view.smoothScrollToPosition(historyAdapter!!.getItemCount() - 1)
        }
    }
}


class LogAdapter(private val context: Context, val list: ArrayList<String>): RecyclerView.Adapter<LogAdapter.ViewHolder>() {
    var contaId=0
    override fun onCreateViewHolder(viewGroup: ViewGroup, position: Int): ViewHolder {
        val view: View = LayoutInflater.from(context).inflate(R.layout.list_item, viewGroup, false)
//        println("onCreateViewHolder position = $position")
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
//        println("getItemCount myList.count = ${list.count()}")
        return list.count()
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val str : String?  = list.get(position)
//        println("onBindViewHolder position = $position - ${list[position]}  id:${viewHolder.id}")
        if ( str != null) {
            viewHolder.bind(str)
        }
    }

    inner class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        var id=contaId++
        fun bind(myItem:String) {
//            println("bind myItem = $myItem")
            itemView.tv_title.text = myItem
        }
    }
}

class HistoryAdapter(private val context: Context, val list: ArrayList<String>): RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
    var contaId=0
    override fun onCreateViewHolder(viewGroup: ViewGroup, position: Int): ViewHolder {
        val view: View = LayoutInflater.from(context).inflate(R.layout.list_item, viewGroup, false)
//        println("onCreateViewHolder position = $position")
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
//        println("getItemCount myList.count = ${list.count()}")
        return list.count()
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val str : String?  = list.get(position)
//        println("onBindViewHolder position = $position - ${list[position]}  id:${viewHolder.id}")
        if ( str != null) {
            viewHolder.bind(str)
        }
    }

    inner class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        var id=contaId++
        fun bind(myItem:String) {
//            println("bind myItem = $myItem")
            itemView.tv_title.text = myItem
        }
    }
}

