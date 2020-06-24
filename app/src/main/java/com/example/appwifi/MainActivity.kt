package com.example.appwifi

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    val TAG: String = "MainActivity";
    var nomeDaRedeWifi: String = ""
    var passwordDaRedeWifi: String = ""

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


        btn_findArduinoAccessPoint.setOnClickListener {
            if (WifiController.isSSIDAvailable(ArduinoSSID)) {
                btn_findArduinoAccessPoint.visibility = View.GONE
                readNetworkPassword()
            }
        }


        btn_connect.setOnClickListener {
            configureThermometer()
        }

        btn_sucesso.setOnClickListener {
            btn_sucesso.visibility = View.INVISIBLE
            btn_sucesso.isEnabled = false
        }

        btn_erro.setOnClickListener {
            btn_erro.visibility = View.INVISIBLE
            btn_erro.isEnabled = false
            testCurrentWifiNetwork()
        }

//        btn_testWifi.setOnClickListener{
//            btn_erro.visibility = View.INVISIBLE
//            btn_erro.isEnabled = false
//        }

        testCurrentWifiNetwork()
    }


    fun testCurrentWifiNetwork() {

        var wifiConfig= WifiController.getWiFiConfig("\"" + ArduinoSSID + "\"")

        if ( wifiConfig != null) {

            // Remove ArduinoSSID da lista de redes cadastradas
            if ( WifiController.wifiManager.removeNetwork(wifiConfig.networkId) ) {
                Timber.e("Removendo ${MainActivity.ArduinoSSID}")
                btn_erro.setText("\n Rede \"${ArduinoSSID}\" precisa \n Ser removida \n (Clique para validar Remoção) \n ")
                btn_erro.visibility = View.VISIBLE
                btn_erro.isEnabled = true
            } else {
                Timber.e("Erro na exclusao da rede ${MainActivity.ArduinoSSID}")
                btn_erro.setText("\n Rede \"${ArduinoSSID}\" não \n pode estar previamente cadastrada \n Exclua a rede Wifi \"${ArduinoSSID}\" \n")
                btn_erro.visibility = View.VISIBLE
                btn_erro.isEnabled = true
            }

        } else if (WifiController.isWifiAccessPointEnabled()) {
            nomeDaRedeWifi = WifiController.getAccessPointSSID()
            passwordDaRedeWifi = WifiController.getAccessPointPassword()

            if ( nomeDaRedeWifi.contains("unknown")) {
                btn_erro.setText("\nRede Wifi <unknown>\n Favor tentar novamente \n (Ajuste e clique no botão)\n")
                btn_erro.visibility = View.VISIBLE
                btn_erro.isEnabled = true
            }
        } else {
            nomeDaRedeWifi = WifiController.getCurrentSSID()

            if (nomeDaRedeWifi.length == 0) {
                btn_erro.setText("\nSem conexão WIFI ativa.\nFavor conectar na mesma\nrede WIFI na qual o \nTermometro deverá ser conectado\n(Ajuste e clique no botão)\n")
                btn_erro.visibility = View.VISIBLE
                btn_erro.isEnabled = true
            } else if (nomeDaRedeWifi.contains(ArduinoSSID)) {
                btn_erro.setText(
                    "\nConexão WIFI ativa deve \nser a mesma rede WIFI na qual o \nTermometro deverá ser conectado\n" +
                            "(Ajuste e clique no botão)\n"
                )
                btn_erro.visibility = View.VISIBLE
                btn_erro.isEnabled = true
            }
        }

        if (!btn_erro.isEnabled) {
            btn_findArduinoAccessPoint.visibility = View.VISIBLE
            btn_findArduinoAccessPoint.isEnabled = true
        }
    }


    fun configureThermometer() {
        var senha = et_senha.text
        Timber.i("SSID : ${nomeDaRedeWifi}")
        Timber.i("PASSWD : ${senha}")


        var ccc: WifiController.ConnectToWifiNetwork =
            WifiController.ConnectToWifiNetwork(this, nomeDaRedeWifi, senha.toString())

        Timber.i("Ops Antes....")

        ccc.execute()
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
