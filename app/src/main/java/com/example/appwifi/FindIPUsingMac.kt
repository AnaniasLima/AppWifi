@file:Suppress("DEPRECATION")

package com.example.appwifi

import android.graphics.Color
import android.os.AsyncTask
import android.os.StrictMode
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.InetAddress


class FindIPUsingMac(activity: MainActivity, val macProcurado:String, segundos : Int, var finalTask : Runnable) : AsyncTask<Int, Int, String>() {
    private var activityWeakReference : WeakReference<MainActivity> = WeakReference(activity)
    private var tempoDecorrido = 0L
    private var tempoEspera = segundos * 1000L

    companion object {
        var response : String = ""
        var isRunning = false
    }

    init {
        Timber.i("init de findIPByMac")
    }

    override fun onPreExecute() {
        super.onPreExecute()
        var activity= activityWeakReference.get()

        isRunning = true

        if ( activity == null || activity.isFinishing()) {
            return
        }
        activity.progressBar.visibility = View.VISIBLE
        activity.label_progressBar.visibility = View.VISIBLE

        activity.progressBar.getProgressDrawable().setColorFilter(
            Color.RED, android.graphics.PorterDuff.Mode.SRC_IN);
    }

    override fun doInBackground(vararg params: Int?): String {
        var locateThread: LocateIpThread = LocateIpThread(macProcurado)
        val p1 = params[0] ?: 0
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        val startTime = System.currentTimeMillis()

        Timber.i("doInBackground p1=${p1}")
        try {
            tempoDecorrido = 0
            locateThread.start()

            tempoDecorrido = System.currentTimeMillis() - startTime
            while ( tempoDecorrido < tempoEspera ) {
                if ( !locateThread.isAlive) {
                    break;
                }
                Thread.sleep(100)
                tempoDecorrido = System.currentTimeMillis() - startTime
                publishProgress(((tempoDecorrido * 100L) / tempoEspera).toInt(), LocateIpThread.ipProcurado)
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

    override fun onProgressUpdate(vararg values: Int?) {
        super.onProgressUpdate(*values)
        var activity= activityWeakReference.get()

        if ( activity == null || activity.isFinishing()) {
            return
        }
        activity.progressBar.progress = values[0] ?: 0


        val subnet = getSubnetAddress(WifiController.wifiManager.dhcpInfo.gateway)
        activity.label_progressBar.text = "$subnet.${values[1]}"
    }


    override fun onPostExecute(result: String) {
        super.onPostExecute(result)
        var activity= activityWeakReference.get()

        Timber.i("onPostExecute FindIPUsingMac=${result}" )

        isRunning = false

        if ( activity == null || activity.isFinishing()) {
            return
        }

        activity.progressBar.progress = 0
        activity.progressBar.visibility = View.INVISIBLE
        activity.label_progressBar.visibility = View.INVISIBLE

        MainActivity.thermometerIP = result

        MainActivity.thermometerHandler.post(finalTask)
    }

    class LocateIpThread(var mac:String ) : Thread() {
        var locatedIP : String? = null
        companion object {
            var ipProcurado : Int = 1
        }

        override fun run() {
            try {
                locatedIP = findIp(mac)
            } catch (e: Exception) {
                Timber.d("Ocorreu uma Exception ")
                e.printStackTrace()
            }
        }
    }
}

private fun getSubnetAddress(address: Int): String {
    return String.format(
        "%d.%d.%d",
        address and 0xff,
        address shr 8 and 0xff,
        address shr 16 and 0xff
    )
}

private fun findIp(strMac:String) : String? {
    val timeout = 50
    try {
        val subnet = getSubnetAddress(WifiController.wifiManager.dhcpInfo.gateway)
        for (i in 1..254) {
            val host = "$subnet.$i"
            FindIPUsingMac.LocateIpThread.ipProcurado = i
            if (InetAddress.getByName(host).isReachable(timeout)) {
                val strLinha = getMacAddressFromIP(strMac)
                if  (strLinha != null) {
                    if ( strLinha.contains(subnet) ) {
                        val strIP = strLinha.substringBefore(' ')
                        Timber.i("IP: [${strIP}]")
                        if (InetAddress.getByName(strIP).isReachable(timeout)) {
                            return(strIP)
                        }
                    }
                }
            } else {
                Timber.e("❌ Not Reachable Host: $host")
            }
        }
    } catch (e: java.lang.Exception) {
        e.printStackTrace()
        Timber.e("===== ERRO ${e.message}")
    }

    return null
}


private fun getMacAddressFromIP(macFinding: String): String? {
    var strRet:String? = null
    var bufferedReader: BufferedReader? = null
    try {
        bufferedReader = BufferedReader(FileReader("/proc/net/arp"))
        var line: String?
        while ( true ) {
            line = bufferedReader.readLine()
            if ( line == null ) break

            if (line.contains(macFinding)) {
                strRet = line
                Timber.i(line)
                break
            } else if ( line.contains("00:00:00:00:00:00")) {
                // Faz nada
            } else {
                Timber.i("Line: [${line}")
            }
        }
    } catch (e: FileNotFoundException) {
        e.printStackTrace()
    } catch (e: IOException) {
        e.printStackTrace()
    } finally {
        try {
            bufferedReader!!.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    return strRet
}

