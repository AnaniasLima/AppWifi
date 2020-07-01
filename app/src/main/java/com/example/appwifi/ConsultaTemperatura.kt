package com.example.appwifi

import android.graphics.Color
import android.os.AsyncTask
import android.os.StrictMode
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.io.IOException
import java.lang.ref.WeakReference

class ConsultaTemperatura(activity: MainActivity, segundos : Int, var finalTask : Runnable) : AsyncTask<Int, Int, String>() {
    private var activityWeakReference : WeakReference<MainActivity> = WeakReference(activity)
    private var tempoDecorrido = 0
    private var tempoEspera = segundos * 1000

    companion object {
        var response : String = ""
        var isRunning = false
    }

    init {
        Timber.i("init de ConsultaTemperatura")
    }

    override fun onPreExecute() {
        super.onPreExecute()
        var activity= activityWeakReference.get()

        isRunning = true

        if ( activity == null || activity.isFinishing()) {
            return
        }

        activity.btn_consulta.isEnabled = false
        activity.btn_consulta.text = "Aguarde..."
    }

    override fun doInBackground(vararg params: Int?): String {
        var locateThread: askThermometerThread = askThermometerThread()
        val p1 = params[0] ?: 0
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)


        Timber.i("doInBackground p1=${p1}")
        try {
            tempoDecorrido = 0
            locateThread.start()

//            WWW
            while ( tempoDecorrido < tempoEspera ) {
                if ( !locateThread.isAlive) {
                    break;
                }
                Thread.sleep(100)
                tempoDecorrido += 100
                publishProgress((tempoDecorrido * 100) / tempoEspera, askThermometerThread.ipProcurado)
            }
            locateThread.interrupt()
            Thread.sleep(100)
        } catch (e: IOException)
        {
            e.printStackTrace()
        }

        if ( locateThread.locatedIP == null) {
            response = ""
        } else {
            response = locateThread.locatedIP!!
        }

        Timber.i("doInBackground return :[$response]" )
        return response
    }



    override fun onPostExecute(result: String) {
        super.onPostExecute(result)
        var activity= activityWeakReference.get()

        Timber.i("onPostExecute FindIPUsingMac=${result}" )

        isRunning = false

        if ( activity == null || activity.isFinishing()) {
            return
        }

        activity.btn_consulta.isEnabled = true
        activity.btn_consulta.text = "Consulta"

        activity.tv_lastResult.text = result

        activity.fxFimConsulta("37.0")

        MainActivity.thermometerHandler.post(finalTask)
    }

    class askThermometerThread( ) : Thread() {
        var locatedIP : String? = null
        companion object {
            var ipProcurado : Int = 1
        }

        override fun run() {
            try {
//                locatedIP = findIp(mac)
            } catch (e: Exception) {
                Timber.d("Ocorreu uma Exception ")
                e.printStackTrace()
            }
        }
    }
}
