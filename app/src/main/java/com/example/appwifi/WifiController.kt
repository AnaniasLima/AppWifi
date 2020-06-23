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
import android.os.Handler
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber


@SuppressLint("StaticFieldLeak")
object WifiController {

    private const val USB_SERIAL_REQUEST_INTERVAL = 30000L
    private const val USB_SERIAL_TIME_TO_CONNECT_INTERVAL = 10000L

    var mainActivity: AppCompatActivity? = null
    var appContext: Context? = null
    lateinit var wifiManager: WifiManager

//    private var connectThread: ConnectThread? = null

    var ssidProcurada : String = ""


    var tabletAccessPointSSID : String = ""
    var tabletAccessPointPassword : String = ""

    fun start(activity: AppCompatActivity, context: Context) {
        mainActivity = activity
        appContext = context
        wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val filter = IntentFilter()
        filter.addAction(NETWORK_STATE_CHANGED_ACTION)
        filter.addAction(SCAN_RESULTS_AVAILABLE_ACTION)

        context.registerReceiver(wifiEventsReceiver, filter)
    }

//    enum class WIFI_AP_STATE {
//        WIFI_AP_STATE_DISABLING, WIFI_AP_STATE_DISABLED, WIFI_AP_STATE_ENABLING, WIFI_AP_STATE_ENABLED, WIFI_AP_STATE_FAILED
//    }
//
//    fun getWifiApState(): WIFI_AP_STATE {
//        return try {
//            val method: Method = wifiManager.getClass().getMethod("getWifiApState")
//            var tmp = method.invoke(wifiManager) as Int
//
//            // Fix for Android 4
//            if (tmp > 10) {
//                tmp = tmp - 10
//            }
//            WIFI_AP_STATE::class.java.getEnumConstants().get(tmp)
//        } catch (e: Exception) {
//            Timber.e(e.message)
//            WIFI_AP_STATE.WIFI_AP_STATE_FAILED
//        }
//    }


    fun isWifiAccessPointEnabled(): Boolean {
        val apState =
            wifiManager.javaClass.getMethod("getWifiApState").invoke(wifiManager) as Int


        val xxx  = wifiManager.javaClass.getMethod("getWifiApConfiguration").invoke(wifiManager) as WifiConfiguration

        Timber.i("apState = ${apState}")

        val AP_STATE_DISABLING = 10
        val AP_STATE_DISABLED = 11
        val AP_STATE_ENABLING = 12
        val AP_STATE_ENABLED = 13
        val AP_STATE_FAILED = 14

        if ( apState == AP_STATE_ENABLED ) {
            tabletAccessPointSSID = xxx.SSID
            tabletAccessPointPassword = xxx.preSharedKey
            Timber.i("ssid = ${tabletAccessPointSSID}   senha[${tabletAccessPointPassword}]")
        } else {
            tabletAccessPointSSID = ""
            tabletAccessPointPassword = ""
        }

        return (apState == AP_STATE_ENABLED)
    }

    fun getAccessPointSSID() : String {
        return(tabletAccessPointSSID)
    }

    fun getAccessPointPassword() : String {
        return(tabletAccessPointPassword)
    }



    fun getCurrentSSID() : String {
        val currentWifi : WifiInfo = wifiManager.getConnectionInfo()
        var currentSSID = currentWifi.getSSID()

        if ( (currentSSID == null) || currentSSID.contains("unknown") ) {
            currentSSID=""
        } else {
            // retira aspas inicial e final
            currentSSID = currentSSID.drop(1)
            currentSSID = currentSSID.dropLast(1)
        }
        Timber.i("Current SSID [${currentSSID}]")
        return currentSSID
    }

    fun isSSIDAvailable(ssid:String) : Boolean {
        val mScanResults: List<ScanResult> = wifiManager.getScanResults()
        var ssidLocalizado = false

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

        return ssidLocalizado
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



    //connects to the given ssid
    fun connectToWPAWiFi(ssidFixed:String, passFixed:String){

        var ssid = ssidFixed
        var pass = passFixed

        if ( ! ssid.contains("\"")) {
            ssid = "\"" + ssid + "\""
        }

        if ( ! pass.contains("\"")) {
            pass = "\"" + pass + "\""
        }


        if( WifiController.isConnectedTo(ssid)){ //see if we are already connected to the given ssid
            wifiManager.disconnect()
        }

        var wifiConfig= getWiFiConfig(ssid)

        if ( wifiConfig == null){ //if the given ssid is not present in the WiFiConfig, create a config for it
            createWPAProfile(ssid,pass)
            wifiConfig=getWiFiConfig(ssid)
        }
        wifiManager.disconnect()
        wifiManager.enableNetwork(wifiConfig!!.networkId,true)
        wifiManager.reconnect()
        Timber.i("Iniciando connection to SSID : ${ssid}");
    }

    fun createWPAProfile(ssid: String,pass: String){
        val conf = WifiConfiguration()

        conf.SSID = ssid
        conf.preSharedKey = pass
        wifiManager.addNetwork(conf)
        Timber.i("saved SSID: ${ssid} to WiFiManger")
    }

}
