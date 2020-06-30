package com.example.appwifi

import android.content.Context
import android.os.AsyncTask
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.io.*
import java.net.Socket
import java.net.UnknownHostException


class ThermometerDemand(val demanda:String, val host:String, val portNumber:Int, var timeout: Long, var finalTask : Runnable) : AsyncTask<Void, Void, String>()  {
    private var connectSuccess: Boolean = true
    private lateinit var demandThread: DemandThread

//    var host = ""
//    var porta : Int = 0
//    var demanda = ""

    companion object {
        var response : String? = null
        var errorType = 0
    }

    override fun onPreExecute() {
        super.onPreExecute()
        Timber.i("Demanda solicitada: ${demanda}...")
        response = null
        errorType = 0
    }

    override fun onProgressUpdate(vararg values: Void?) {
        super.onProgressUpdate(*values)
        // TODO: ver isso
    }

    override fun doInBackground(vararg params: Void?): String? {
        Timber.i("doInBackground")
        var responseFromSocket : String? = null
        try {
            demandThread = DemandThread(host, portNumber, demanda)

            demandThread.start()

            while ( timeout > 0 ) {
                Thread.sleep(20)
                if ( ! demandThread.isAlive ) {
                    responseFromSocket = demandThread.serverResponse
                    break;
                }
                timeout -= 20
            }
            if ( demandThread.isAlive ) {
                demandThread.interrupt()
            }
        } catch (e: IOException)
        {
            connectSuccess = false
            e.printStackTrace()
        }
        Timber.i("doInBackground return" )

        return responseFromSocket
    }

    override fun onPostExecute(result: String?) {
        super.onPostExecute(result)

        response = result

        Timber.i("onPostExecute responseFromThermometer=${response}" )

        MainActivity.thermometerHandler.post(finalTask)

//        activity.runOnUiThread {
//            (activity as MainActivity).btn_sucesso.text = response
//            (activity as MainActivity).btn_sucesso.isEnabled = true
//            (activity as MainActivity).btn_sucesso.visibility = View.VISIBLE
//        }
    }


    class DemandThread( val  host: String, val porta:Int, val demanda:String) : Thread() {
        var socket : Socket? = null
        var serverResponse : String? = null
        override fun run() {
            try {
                socket = openSocket(host, porta)
                if  ( socket != null) {
                    serverResponse = dealWithOpenSocket(socket!!, demanda)
                    socket!!.close()
                }
            } catch (e: Exception) {
                Timber.d("Ocorreu uma Exception ")
                e.printStackTrace()
            }
        }
    }
}

private fun openSocket(host: String, porta: Int) : Socket? {
    var socket : Socket? = null
    try {
        Timber.e("WWWWWWWW Tentando socket")
        socket = Socket(host, porta)

        if ( socket.isConnected() ) {
            Timber.i("Conectou Socket")
        } else {
            socket.close()
            socket=null
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

    return socket
}

private fun dealWithOpenSocket(socket : Socket, demanda: String) : String? {
    var resposeFromServer : String? = null
    try {
        if (socket.isConnected()) {
            val out = PrintWriter(
                BufferedWriter(OutputStreamWriter(socket.getOutputStream()) ), true)
            val `in` = BufferedReader(InputStreamReader(socket.getInputStream()))
            out.write(demanda)
            out.flush()
            Timber.i("Calling Write ${demanda}")
            resposeFromServer = `in`.readLine()
            out.close()
            `in`.close()
            Timber.i("response ======>>>>>  [${resposeFromServer}]  Tam: ${resposeFromServer.length}")
        } else {
            Timber.e( "Socket is not connected")
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } catch (e: IOException) {
        e.printStackTrace()
    }

    return resposeFromServer
}

