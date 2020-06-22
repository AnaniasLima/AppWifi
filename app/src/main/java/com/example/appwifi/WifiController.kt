package com.example.appwifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION
import android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import android.os.Handler
import android.view.View
import android.widget.Toast


@SuppressLint("StaticFieldLeak")
object WifiController {

    private const val USB_SERIAL_REQUEST_INTERVAL = 30000L
    private const val USB_SERIAL_TIME_TO_CONNECT_INTERVAL = 10000L

    var mainActivity: AppCompatActivity? = null
    var appContext: Context? = null
    lateinit var wifiManager: WifiManager

    private var ssidAvailableHandler = Handler()
//    private var connectThread: ConnectThread? = null

    var ssidProcurada : String = ""
    var ssidLocalizado : Boolean = false

    fun start(activity: AppCompatActivity, context: Context) {
        mainActivity = activity
        appContext = context
        wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val filter = IntentFilter()
        filter.addAction(NETWORK_STATE_CHANGED_ACTION)
        filter.addAction(SCAN_RESULTS_AVAILABLE_ACTION)

        context.registerReceiver(wifiEventsReceiver, filter)
    }

    fun getCurrentSSID() : String {
        val currentWifi : WifiInfo = wifiManager.getConnectionInfo()
        var currentSSID = currentWifi.getSSID()

        if ( (currentSSID == null) || currentSSID.contains("unknown") ) {
            currentSSID=""
        }
        Timber.i("Current SSID [${currentSSID}]")
        return currentSSID
    }

    fun isSSIDAvailable(ssid:String) {
        val mScanResults: List<ScanResult> = wifiManager.getScanResults()

        ssidLocalizado = false

        for ( item in mScanResults ) {
            Timber.i(item.SSID)
            if ( ssid == item.SSID)  {
                ssidLocalizado = true
            }
        }

        // Se não localizar, pede para fazer um novo Scan
        if ( ssidLocalizado == false) {
            wifiManager.startScan()
        }

        mainActivity?.runOnUiThread {
            (mainActivity as MainActivity).btn_connect.visibility =  View.VISIBLE
            (mainActivity as MainActivity).btn_connect.isEnabled = ssidLocalizado
        }

//        findNetworkChecking(3000)
    }

    private var checkSSIDRunnable = Runnable {
        mainActivity?.runOnUiThread {
            if ( ssidLocalizado ) {
                (mainActivity as MainActivity).btn_findArduinoAccessPoint.isEnabled = false
                (mainActivity as MainActivity).btn_connect.isEnabled = true
            } else {
                (mainActivity as MainActivity).btn_findArduinoAccessPoint.isEnabled = true
                (mainActivity as MainActivity).btn_connect.isEnabled = false
            }
        }
    }


    fun findNetworkChecking(delayToNext: Long) {
        ssidAvailableHandler.removeCallbacks(checkSSIDRunnable)
        ssidAvailableHandler.postDelayed(checkSSIDRunnable, delayToNext)
    }



    private val wifiEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Timber.i("wifiEventsReceiver recebendo uma notificacao de: action=${action}")
        }
    }

    fun getSSID( ) : String {
        return wifiManager.connectionInfo.ssid
    }


    fun isConnectedTo(ssid: String):Boolean{
        if( wifiManager.connectionInfo.ssid == ssid){
            return true
        }
        return false
    }


    fun getWiFiConfig(ssid: String): WifiConfiguration? {
        val wifiList= wifiManager.configuredNetworks
        for (item in wifiList){
            if(item.SSID != null && item.SSID.equals(ssid)){
                return item
            }
        }
        return null
    }


}
