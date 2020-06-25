@file:Suppress("DEPRECATION")

package com.example.appwifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION
import android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION
import android.os.AsyncTask
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.io.*
import java.lang.Thread.sleep
import java.net.Socket
import java.net.UnknownHostException


@SuppressLint("StaticFieldLeak")
object WifiController  {

    private lateinit var connectThread: ConnectThread
    private const val USB_SERIAL_REQUEST_INTERVAL = 30000L
    private const val USB_SERIAL_TIME_TO_CONNECT_INTERVAL = 10000L

    var mainActivity: AppCompatActivity? = null
    var appContext: Context? = null
    lateinit var wifiManager: WifiManager

    var tabletAccessPointSSID : String = ""
    var tabletAccessPointPassword : String = ""

    var newNetwork = ""
    var responseFromThermometer=""

    //    private var connectThread: ConnectThread? = null

    fun start(activity: AppCompatActivity, context: Context) {
        mainActivity = activity
        appContext = context
        wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val filter = IntentFilter()
        filter.addAction(NETWORK_STATE_CHANGED_ACTION)
        filter.addAction(SCAN_RESULTS_AVAILABLE_ACTION)

        context.registerReceiver(wifiEventsReceiver, filter)
    }

    class ConnectThread(var ssid:String, var passwd:String) : Thread(), TcpClient.OnMessageReceived {
        override fun run() {
            var idRedeArduino : Int
            try {
                // Cria profile para se conectar a rede ArduinoSSID
                idRedeArduino = WifiController.createWPAProfile(
                    MainActivity.ArduinoSSID,
                    MainActivity.ArduinoPASSWD
                )
                if ( idRedeArduino < 0 ) {
                    Timber.e("Erro na criação da rede ${idRedeArduino}")
                    return
                }

                // Tenta se conectar  a rede ArduinoSSID
                Timber.i("chamando  connectToWPAWiFi em 888888");
                WifiController.connectToWPAWiFi(MainActivity.ArduinoSSID)

                // Verifica se conectou OK
                if ( comparaSSID(newNetwork, MainActivity.ArduinoSSID) ) {
                    if (WifiController.isConnectedTo(MainActivity.ArduinoSSID)) {
                        Timber.i("Estamos conectado na rede ${MainActivity.ArduinoSSID}")
                        responseFromThermometer = configuraThermometer(ssid, passwd )
                    }
                }

                Timber.i("chamando  disconnectFromWPAWiFi em 00000");
                disconnectFromWPAWiFi()

                // Remove ArduinoSSID da lista de redes cadastradas
                if ( WifiController.wifiManager.removeNetwork(idRedeArduino) ) {
                    Timber.i("Removida rede ${MainActivity.ArduinoSSID}")
                } else {
                    Timber.e("Erro na exclusao da rede ${MainActivity.ArduinoSSID}")
                }


                Timber.i("Vamos reconectar na rede original")
                Timber.i("chamando  connectToWPAWiFi em 99999");
                WifiController.connectToWPAWiFi(ssid)

                if ( newNetwork == ssid ) {
                    Timber.i("Estamos de volta na rede ${ssid}")
                }

            } catch (e: Exception) {
                Timber.d("Ocorreu uma Exception ")
                e.printStackTrace()
            }
        }


        fun configuraThermometer(ssid:String, passwd:String): String {
            val PARAM_TIMEOUT_AS_CLIENT = 20
            val PARAM_TIMEOUT_AS_ACCESS_POINT = 60

            val comment = String.format("[%s\t%s\t%d\t%d]\r\n", ssid, passwd, PARAM_TIMEOUT_AS_CLIENT, PARAM_TIMEOUT_AS_ACCESS_POINT)
            var response: String = ""
            var conectou = false
            var socket : Socket? = null

            Timber.i("Entrando em configuraThermometer")

            // Aguarda para "Estabilizar a
            // tenta conectaralgumas vezes
            for ( i in 1..5) {
                try {
                    Timber.e("WWWWWWWW Tentando socket.... i=${i}")
                    sleep(500)
                    socket = Socket("192.168.4.1", 80)

                    if ( socket == null ) {
                        Timber.e(" ======================== socket == null")
                    }
                    if ( (socket != null) && socket.isConnected() ) {
                        conectou = true
                        Timber.i("configuraThermometer conectou com i=${i}")
                    } else {
                        Timber.e("Socket not connected yet")
                    }
                } catch (e: UnknownHostException) {
                    Timber.e("UnknownHostException")
                } catch (e: IOException) {
                    Timber.e("IOException")
                } catch (e: SecurityException) {
                    Timber.e("SecurityException")
                } catch (e: IllegalArgumentException) {
                    Timber.e("IllegalArgumentException")
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if ( conectou ) {
                    break
                }
            }

            if ( conectou && (socket != null) ) {
                try {
                    if (socket.isConnected()) {
                        val out = PrintWriter(
                            BufferedWriter(
                                OutputStreamWriter(socket.getOutputStream())
                            ), true
                        )
                        val `in` = BufferedReader(
                            InputStreamReader(socket.getInputStream())
                        )
                        out.write(comment)
                        out.flush()
                        Timber.i("Calling Write ${comment}")
                        val resposeFromServer: String = `in`.readLine()
                        out.close()
                        `in`.close()

                        Timber.i("response ======>>>>>  ${resposeFromServer}")
                        if ( resposeFromServer.length > 0 ) {
                            response = resposeFromServer
                        }
                        socket.close()
                    } else {
                        Timber.e( "Socket is not connected")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }
            return response
        }

        override fun messageReceived(pkt: String) {
            Timber.i( "Recebida mensagem : ${pkt}")
        }

    }



    class ConnectToWifiNetwork(val context: Context, val ssid:String, val passwd: String) : AsyncTask<Void, Void, String>() {
        private var connectSuccess: Boolean = true

        init {
            Timber.i("init de ConnectToWifiNetwork")
        }

        override fun onPreExecute() {
            super.onPreExecute()
            Timber.i("Aguardando conexao...")
        }

        override fun onProgressUpdate(vararg values: Void?) {
            super.onProgressUpdate(*values)
            // TODO: ver isso
        }

        override fun doInBackground(vararg params: Void?): String? {
            Timber.i("doInBackground")
            try {

                var esperando = 10000
                connectThread = ConnectThread(ssid, passwd)
                connectThread.start()

                while ( esperando > 0 ) {
                    Thread.sleep(100)
                    if ( ! connectThread.isAlive ) {
                        break;
                    }
                    esperando -= 100
                }
                connectThread.interrupt()
            } catch (e: IOException)
            {
                connectSuccess = false
                e.printStackTrace()
            }
            Timber.i("doInBackground return" )
            return null
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)

            Timber.i("onPostExecute responseFromThermometer=${responseFromThermometer}" )

            if ( ! connectSuccess ) {
                Timber.i("couldn´t connect")
            } else {
                if ( responseFromThermometer != "") {
                    Timber.i("responseFromThermometer = ${responseFromThermometer}" )
                }
            }

            mainActivity?.runOnUiThread {
                (mainActivity as MainActivity).btn_sucesso.text = responseFromThermometer
                (mainActivity as MainActivity).btn_sucesso.isEnabled = true
                (mainActivity as MainActivity).btn_sucesso.visibility = View.VISIBLE
            }


        }

    }



    fun isWifiAccessPointEnabled(): Boolean {
        val apState =
            wifiManager.javaClass.getMethod("getWifiApState").invoke(wifiManager) as Int

        val wifiConfig  = wifiManager.javaClass.getMethod("getWifiApConfiguration").invoke(wifiManager) as WifiConfiguration

        Timber.i("apState = ${apState}")

        val AP_STATE_ENABLED = 13

        if ( apState == AP_STATE_ENABLED ) {
            tabletAccessPointSSID = wifiConfig.SSID
            tabletAccessPointPassword = wifiConfig.preSharedKey
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


        if ( (currentSSID == null) || comparaSSID(currentSSID, "unknown" ) ) {
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
            val networkInfo= intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)

            if ( networkInfo != null ) {
                Timber.i("wifiEventsReceiver recebendo uma notificacao de: action=${action}   isConnectedOrConnecting=${networkInfo.isConnectedOrConnecting}  state=${networkInfo.state}")

                if ( networkInfo.state == NetworkInfo.State.CONNECTED ) {
                    val ssid= intent.getParcelableExtra<WifiInfo>(WifiManager.EXTRA_WIFI_INFO).ssid
                    val log="Connected to SSID:"+ssid
                    Timber.i("Connected to SSID:"+ssid)
                    Toast.makeText(context, log, Toast.LENGTH_SHORT).show()
                    newNetwork = ssid

                }

                if ( networkInfo.state == NetworkInfo.State.DISCONNECTED ) {
                    //                Toast.makeText(context, "Desconectando Wifi", Toast.LENGTH_SHORT).show()
                    Timber.i("NetworkInfo.State.DISCONNECTED ")
                    newNetwork = ""
                }

            }


        }
    }

    fun getSSID( ) : String {
        return wifiManager.connectionInfo.ssid
    }


    fun isConnectedTo(ssid: String):Boolean {
        Timber.i("isConnectedTo Antes [${wifiManager.connectionInfo.ssid}] [${ssid}]")
        return comparaSSID(wifiManager.connectionInfo.ssid, ssid)
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

    fun disconnectFromWPAWiFi() {

        Timber.i("disconnectFromWPAWiFi SSID: ${wifiManager.connectionInfo.ssid}   networkId=${wifiManager.connectionInfo.networkId} ");

        if ( wifiManager.connectionInfo.networkId == -1 ) {
            Timber.i("<<<< Saindo de disconnectFromWPAWiFi MEIO")
            return
        }

        try {
            newNetwork = wifiManager.connectionInfo.ssid
            wifiManager.disconnect()

            // Aguarda sinalização da desconexao
            Timber.i("Aguardando desconexao...");
            var conta=3000
            while (  conta > 0 ) {

                Timber.e("===== Conta =  ${conta}  [${wifiManager.connectionInfo.ssid}]  newNetwork = [${newNetwork}]  wifiManager.connectionInfo.networkId=${wifiManager.connectionInfo.networkId}");
                conta -= 100
                try {
                    sleep(100)
                } catch (e: Exception) {
                    Timber.d("Ocorreu uma Exception em sleep")
                    e.printStackTrace()
                }

                if ( wifiManager.connectionInfo.ssid == "") {
                    Timber.d("wifiManager.connectionInfo.ssid == \"\"")
                    break
                }
                if ( wifiManager.connectionInfo.networkId < 0 ) {
                    Timber.d("wifiManager.connectionInfo.networkId < 0")
                    break
                }

            }
            Timber.e("=====>>>> Conta =  ${conta}  [${wifiManager.connectionInfo.ssid}]  newNetwork = [${newNetwork}]")

        } catch (e: Exception) {
            Timber.d("Ocorreu uma Exception ")
            e.printStackTrace()
        }

        Timber.i("<<<< Saindo de disconnectFromWPAWiFi FIM")
    }


    //connects to the given ssid
    fun connectToWPAWiFi(ssid:String){

        var wifiConfig= getWiFiConfig("\"" + ssid + "\"")

        Timber.i("entrando em connectToWPAWiFi: ${ssid}");


        if ( wifiConfig == null ) {
            Timber.i("Não localizou SSID: ${ssid}");
            return
        }

        Timber.i("chamando  disconnectFromWPAWiFi em 11111");
        disconnectFromWPAWiFi()

        if ( ! wifiManager.enableNetwork(wifiConfig.networkId,true) ) {
            Timber.e("falha em enableNetwork SSID : ${ssid}");
            return
        }

        newNetwork = ""

        Timber.i("chamando reconnect SSID : ${ssid}");
        if ( ! wifiManager.reconnect() ) {
            Timber.e("falha em reconnect SSID : ${ssid}");
            return
        }

        // Aguarda sinalização da nova rede conectada
        Timber.i("Sucesso em reconnect SSID : ${ssid}");
        var conta=5000
        while (  (newNetwork == "")  and (conta > 0)) {
            Timber.e("===== Conta =  ${conta}  [${wifiManager.connectionInfo.ssid}]     networkId=${wifiManager.connectionInfo.networkId}");
            conta -= 100
            sleep(100)
        }


        if ( comparaSSID(wifiManager.connectionInfo.ssid, ssid) ) {
            Timber.i("=====>>>> Sucesso ao conectar em  SSID : ${ssid}");
        } else {
            Timber.e("=====>>>> Conta =  ${conta}  [${wifiManager.connectionInfo.ssid}]  newNetwork = [${newNetwork}]")
        }
    }

    fun createWPAProfile(ssid: String,pass: String) : Int{
        val conf = WifiConfiguration()
        var newId : Int

        conf.SSID = "\"" + ssid + "\""
        conf.preSharedKey = "\"" + pass + "\""
        newId = wifiManager.addNetwork(conf)
        if ( newId < 0 ) {
            Toast.makeText(appContext, "Erro no cadastramento da rede ${ssid} ", Toast.LENGTH_LONG).show()
            Timber.i("Erro no cadatramento da rede ${ssid} ")
        } else {
            Timber.i("saved SSID: ${ssid} to WiFiManger newid = ${newId}")
        }

        return newId
    }

    fun comparaSSID(s1:String, s2:String) : Boolean {
        var str1 = s1;
        var str2 = s2;
        if ( s1.contains("\"")) {
            str1 = s1.drop(1)
            str1 = str1.dropLast(1)
        }
        if ( s2.contains("\"")) {
            str2 = s2.drop(1)
            str2 = str2.dropLast(1)
        }
        return str1==str2
    }


}



