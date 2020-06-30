@file:Suppress("DEPRECATION")

package com.example.appwifi

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.StrictMode
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
        const val TIMEOUT_TO_FIND_IP = 10
        var thermometerMacAddress : String = ""
        var thermometerIP : String = ""
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

        btn_findArduinoAccessPoint.setOnClickListener {
            findAccessPoint()
        }

        btn_findIP.setOnClickListener {
            findIP(TIMEOUT_TO_FIND_IP)
        }


        btn_connect.setOnClickListener {
            configureThermometer()
        }

        btn_sucesso.setOnClickListener {
            btn_sucesso.visibility = View.INVISIBLE
            btn_sucesso.isEnabled = false
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
            btn_findIP.setText(thermometerIP)
        }
    }



    //-------------------------------------------------
    // Find AccessPoint
    //-------------------------------------------------
    fun findAccessPoint() {
        findPanel.visibility = View.VISIBLE
        btn_findArduinoAccessPoint.visibility = View.VISIBLE
        btn_findArduinoAccessPoint.setText(" Localizando \n Access Point \nAguarde...\n")
        btn_findIP.isEnabled = false

        if (WifiController.isSSIDAvailable(ArduinoSSID)) {
            btn_findArduinoAccessPoint.visibility = View.GONE
            readNetworkPassword()
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

        var ccc: WifiController.ConnectToWifiAccessPointNetwork =
            WifiController.ConnectToWifiAccessPointNetwork(this, nomeDaRedeWifi, senha.toString())

        Timber.i("Ops Antes....")

        ccc.execute(123)
        Timber.i("Ops depois....")
    }


    fun readNetworkPassword() {
        painelSSID.visibility = View.VISIBLE
        et_ssidDaRede.setText(nomeDaRedeWifi)
//        et_senha.setText("")

        btn_connect.visibility = View.VISIBLE
        btn_connect.isEnabled = true
    }

}
