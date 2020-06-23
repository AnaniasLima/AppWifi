package com.example.appwifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber

class MainActivity : AppCompatActivity()  {

    val TAG:String="MainActivity";
    var nomeDaRedeWifi : String = ""
    var passwordDaRedeWifi : String = ""

    companion object {
        const val ArduinoSSID = "8266_THERMOMETER"
        const val ArduinoPASSWD = "nana12345"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        WifiController.start(this, applicationContext)


        testCurrentWifiNetwork()


        btn_findArduinoAccessPoint.setOnClickListener{
            if ( WifiController.isSSIDAvailable(ArduinoSSID) ) {
                btn_findArduinoAccessPoint.visibility =  View.GONE
                readNetworkPassword()
            }
        }


        btn_connect.setOnClickListener{
            Toast.makeText( this, "Tentando conexao com :" + ArduinoSSID, Toast.LENGTH_LONG).show()
            // connectToWPAWiFi(ArduinoSSID,ArduinoPASSWD)
        }


        btn_erro.setOnClickListener{
            btn_erro.visibility = View.INVISIBLE
            btn_erro.isEnabled = false
            testCurrentWifiNetwork()
        }

        btn_testWifi.setOnClickListener{
            btn_erro.visibility = View.INVISIBLE
            btn_erro.isEnabled = false
            testWifiNetworkParameters()
        }

    }


    fun testCurrentWifiNetwork() {

        if (  WifiController.isWifiAccessPointEnabled() ) {
            nomeDaRedeWifi = WifiController.getAccessPointSSID()
            passwordDaRedeWifi = WifiController.getAccessPointPassword()
        } else {
            nomeDaRedeWifi = WifiController.getCurrentSSID()

            if ( nomeDaRedeWifi.length == 0) {
                btn_erro.setText("\nSem conexão WIFI ativa.\nFavor conectar na mesma\nrede WIFI na qual o \nTermometro deverá ser conectado\n(Ajuste e clique no botão)\n")
                btn_erro.visibility =  View.VISIBLE
                btn_erro.isEnabled = true
            } else if ( nomeDaRedeWifi.contains(ArduinoSSID) ) {
                btn_erro.setText("\nConexão WIFI ativa deve \nser a mesma rede WIFI na qual o \nTermometro deverá ser conectado\n" +
                        "(Ajuste e clique no botão)\n")
                btn_erro.visibility =  View.VISIBLE
                btn_erro.isEnabled = true
            }

        }


        btn_findArduinoAccessPoint.visibility =  View.VISIBLE
        btn_findArduinoAccessPoint.isEnabled = true
    }



    fun testWifiNetworkParameters() {
        var senha = et_senha.text
        Timber.i("SSID : ${nomeDaRedeWifi}")
        Timber.i("PASSWD : ${senha}")

        btn_connect.visibility =  View.VISIBLE
        btn_connect.isEnabled = true

        WifiController.connectToWPAWiFi(nomeDaRedeWifi, senha.toString())
    }


    fun readNetworkPassword()  {
        painelSSID.visibility = View.VISIBLE
        et_ssidDaRede.setText(nomeDaRedeWifi)
        et_senha.setText("")
    }



//    //connects to the given ssid
//    fun connectToWPAWiFi(ssid:String,pass:String){
//        if(WifiController.isConnectedTo(ssid)){ //see if we are already connected to the given ssid
//            Toast.makeText( this, "Connected to"+ssid, Toast.LENGTH_LONG).show()
//            return
//        }
//        val wm:WifiManager= applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
//        var wifiConfig= getWiFiConfig(ssid)
//
//        if ( wifiConfig == null){ //if the given ssid is not present in the WiFiConfig, create a config for it
//            createWPAProfile(ssid,pass)
//            wifiConfig=getWiFiConfig(ssid)
//        }
//        wm.disconnect()
//        wm.enableNetwork(wifiConfig!!.networkId,true)
//        wm.reconnect()
//        Log.d(TAG,"intiated connection to SSID"+ssid);
//    }
//
//
//    fun getWiFiConfig(ssid: String): WifiConfiguration? {
//        val wm:WifiManager= applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
//        val wifiList=wm.configuredNetworks
//        for (item in wifiList){
//            if(item.SSID != null && item.SSID.equals(ssid)){
//                return item
//            }
//        }
//        return null
//    }
//    fun createWPAProfile(ssid: String,pass: String){
//        Log.d(TAG,"Saving SSID :"+ssid)
//        val conf = WifiConfiguration()
//        conf.SSID = ssid
//        conf.preSharedKey = pass
//        val wm:WifiManager= applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
//        wm.addNetwork(conf)
//        Log.d(TAG,"saved SSID to WiFiManger")
//    }
//
//    class WiFiChngBrdRcr : BroadcastReceiver(){ // shows a toast message to the user when device is connected to a AP
//        private val TAG = "WiFiChngBrdRcr"
//        override fun onReceive(context: Context, intent: Intent) {
//            val networkInfo=intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
//            if(networkInfo.state == NetworkInfo.State.CONNECTED){
//                val bssid=intent.getStringExtra(WifiManager.EXTRA_BSSID)
//                Log.d(TAG, "Connected to BSSID:"+bssid)
//                val ssid=intent.getParcelableExtra<WifiInfo>(WifiManager.EXTRA_WIFI_INFO).ssid
//                val log="Connected to SSID:"+ssid
//                Log.d(TAG,"Connected to SSID:"+ssid)
//                Toast.makeText(context, log, Toast.LENGTH_SHORT).show()
//
//
//            }
//        }
//
//
//    }
}