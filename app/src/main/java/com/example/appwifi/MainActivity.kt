@file:Suppress("DEPRECATION")

package com.example.appwifi

import android.content.Context
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    val TAG: String = "MainActivity";
    var nomeDaRedeWifi: String = ""
    var passwordDaRedeWifi: String = ""
    var sharedPreferences : SharedPreferences? = null



    companion object {
        const val ArduinoSSID = "8266_THERMOMETER"
        const val ArduinoPASSWD = "nana12345"
        const val ArduinoAccessPointIP = "192.168.4.1"
        const val ArduinoAccessPointPort = 81
        const val ThermometerPort = 80
        const val TIMEOUT_TO_FIND_IP = 15
        const val TIMEOUT_TO_CONNECT_ACCESS_POINT = 10
        var thermometerMacAddress : String = ""
        var thermometerIP : String = ""
        var temperaturaMedida : Float = 36.0F
        var thermometerHandler = Handler()
        var activity: AppCompatActivity? = null
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        activity = this

        sharedPreferences = this.getPreferences(Context.MODE_PRIVATE)

        thermometerMacAddress = sharedPreferences?.getString("thermometerMacAddress", "").toString()


        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        WifiController.start(this, applicationContext)
        ScreenLog.start(this, applicationContext, log_recycler_view, history_recycler_view)

        btn_findArduinoAccessPoint.setOnClickListener {
            findAccessPoint()
        }

        btn_findIP.setOnClickListener {
            findIP(TIMEOUT_TO_FIND_IP)
        }


        btn_configThermometer.setOnClickListener {
            configureThermometer()
        }

        btn_resetThermometer.setOnClickListener {
            WifiController.ConnectToWifiAccessPointNetwork(this, "", "").execute(TIMEOUT_TO_CONNECT_ACCESS_POINT)
        }

        btn_consulta.setOnClickListener {
            consultaTemperatura(3000)
        }

        btn_startupError.setOnClickListener {
            testCurrentWifiNetwork()
        }

        testCurrentWifiNetwork()
    }


    fun processaNovoMac(novoMac:String?) {
        if ( novoMac != null)  {
            val editor = sharedPreferences!!.edit()
            thermometerMacAddress = novoMac
            editor.putString("thermometerMacAddress", novoMac)
            editor.apply()
        }
    }



    /**
     * A princípio nunca utilizaremos o Arduino como AccessPoint. Sendo assim, vamos garantir que
     * o SSID "8266_THERMOMETER" nõa esteja na lista de redes conhecidos. Caso esteja, vamos tentar
     * excluir a rede. Caso não seja possível (uma aplicação não pode excluir uma rede cadastrada por outros)
     * vamos solicitar ao usuário a exclusao da rede antes de prosseguirmos.
     * Quando o Tablet estiver atuando como AccessPoint, vamos pegar o SSID e a PASSWD para passar
     * para o Arduino caso o mesmo ainda não tenha sido configurado.
     * Estando o Tablet conectado numa rede Wifi, vamos pegar o SSID da rede sendo utilizada para
     * que o Arduino se conecte na mesma rede.
     */
    fun testCurrentWifiNetwork() {
        var errorMessage : String? = null
        val fxName = object{}.javaClass.enclosingMethod?.name
        Timber.e("#### ${fxName}  ####")

        var wifiConfig= WifiController.getWiFiConfig("\"" + ArduinoSSID + "\"")

        startupPanel.visibility = View.VISIBLE
        btn_startupError.setText("  Avaliando Rede... \n  Aguarde... ")
        btn_startupError.isEnabled = false

        if ( wifiConfig != null) {

            // Remove ArduinoSSID da lista de redes cadastradas
            if ( WifiController.wifiManager.removeNetwork(wifiConfig.networkId) ) {
                Timber.e("Removendo ${MainActivity.ArduinoSSID}")
                errorMessage="\n Rede \"${ArduinoSSID}\" precisa \n Ser removida \n (Clique para validar Remoção) \n "
            } else {
                Timber.e("Erro na exclusao da rede ${MainActivity.ArduinoSSID}")
                errorMessage = "\n Rede \"${ArduinoSSID}\" não \n pode estar previamente cadastrada \n Exclua a rede Wifi \"${ArduinoSSID}\" \n"
            }

        } else if (WifiController.isWifiAccessPointEnabled()) {
            nomeDaRedeWifi = WifiController.getAccessPointSSID()
            passwordDaRedeWifi = WifiController.getAccessPointPassword()
            if ( nomeDaRedeWifi.contains("unknown")) {
                errorMessage="\nRede Wifi <unknown>\n Favor tentar novamente \n (Ajuste e clique no botão)\n"
            }
        } else {
            nomeDaRedeWifi = WifiController.getCurrentSSID()

            if (nomeDaRedeWifi.length == 0) {
                errorMessage="\nSem conexão WIFI ativa.\nFavor conectar na mesma\nrede WIFI na qual o \nTermometro deverá ser conectado\n(Ajuste e clique no botão)\n"
            } else if (nomeDaRedeWifi.contains(ArduinoSSID)) {
                errorMessage = "\nConexão WIFI ativa deve \nser a mesma rede WIFI na qual o " +
                        "\nTermometro deverá ser conectado\n" + "(Ajuste e clique no botão)\n"
            }
        }

        if (errorMessage != null ) {
            btn_startupError.setText(errorMessage)
            startupPanel.visibility = View.VISIBLE
            btn_startupError.isEnabled = true
            return;
        }

        // Prepara para próxima fase
        btn_startupError.setText("")
        startupPanel.visibility = View.GONE

        // Se já conhecemos o MAC do Arduino, vamos tentar conectar
        if ( thermometerMacAddress != "") {
            findIP(TIMEOUT_TO_FIND_IP)
        } else {
            // Vamos habilitar painel para localizar e configurar o Arduino Access Point
            findAccessPoint()
        }

    }


    //-------------------------------------------------
    // Consulta
    //-------------------------------------------------
    fun consultaTemperatura(timeout : Int) {
        btn_consulta.setText("Aguarde...")
        btn_consulta.isEnabled = false
        tv_lastResult.text = ""

        if ( ! FindIPUsingMac.isRunning ) {
//            ThermometerDemand(this, "TEMP", thermometerIP, ThermometerPort, timeout).execute(1)

            ThermometerLoop(this, "TEMP", thermometerIP, ThermometerPort, 10000, 100000, 1000L).execute(1)

        }
    }

    fun fxFimConsulta(temp:String?) {
        if ( temp != null) {
            Timber.i("fxFimConsulta=${String}")
            tv_lastResult.text = ThermometerDemand.response
        } else {
            Timber.i("fxFimConsulta")
        }
        btn_consulta.setText("Consulta")
        btn_consulta.isEnabled = true
    }


    //-------------------------------------------------
    // Find IP
    //-------------------------------------------------
    fun findIP(timeout : Int) {
        findPanel.visibility = View.VISIBLE
        btn_findIP.visibility = View.VISIBLE
        btn_findIP.setText(" Localizando MAC \n ${thermometerMacAddress} \nAguarde...")
        btn_findIP.isEnabled = false

        if ( ! FindIPUsingMac.isRunning ) {
            FindIPUsingMac(this, thermometerMacAddress, timeout , runnableIPSearchFinished).execute(456)
        }
    }


    val runnableIPSearchFinished = Runnable {
        Timber.i("Runnable thermometerIP=[${thermometerIP}]")
        if ( thermometerIP == "" ) {
            findPanel.visibility = View.VISIBLE
            btn_findIP.setText("Localizar Mac\n${thermometerMacAddress}")
            btn_findIP.isEnabled = true
        } else {
            findPanel.visibility = View.GONE
            testPanel.visibility = View.VISIBLE
        }
    }



    //-------------------------------------------------
    // Find AccessPoint
    //-------------------------------------------------
    fun findAccessPoint() {
        findPanel.visibility = View.VISIBLE
        btn_findArduinoAccessPoint.visibility = View.VISIBLE
        btn_findArduinoAccessPoint.setText(" Localizando \n Access Point \nAguarde...\n")
        btn_findArduinoAccessPoint.isEnabled = false

        if (WifiController.isSSIDAvailable(ArduinoSSID)) {
            btn_findArduinoAccessPoint.visibility = View.INVISIBLE
            findPanel.visibility = View.GONE
            readNetworkPassword()
        } else {
            btn_findArduinoAccessPoint.setText(" Access Point \n ${ArduinoSSID} \n Não localizado\n Verifique Permissões \n Clique para \n tentar localizar ")
            btn_findArduinoAccessPoint.isEnabled = true
        }

    }


    fun findAccessPointFinished() {
        if ( thermometerIP != "" ) {
            findPanel.visibility = View.VISIBLE
            btn_findIP.setText("Mac não localizado")
        } else {
            findPanel.visibility = View.VISIBLE
            btn_findIP.setText(thermometerIP)
        }
    }


    fun configureThermometer() {
        var senha = et_senha.text
        Timber.i("SSID : ${nomeDaRedeWifi}")
        Timber.i("PASSWD : ${senha}")

        WifiController.ConnectToWifiAccessPointNetwork(this, nomeDaRedeWifi, senha.toString()).execute(TIMEOUT_TO_CONNECT_ACCESS_POINT)
    }


    fun readNetworkPassword() {
        configPanel.visibility = View.VISIBLE
        et_ssidDaRede.setText(nomeDaRedeWifi)
        et_ssidDaRede.isEnabled = false
//        et_senha.setText("")

        btn_configThermometer.visibility = View.VISIBLE
        btn_configThermometer.isEnabled = true

        btn_resetThermometer.visibility = View.VISIBLE
        btn_resetThermometer.isEnabled = true
    }

}
