package com.example.appwifi

import android.os.AsyncTask
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.io.*
import java.lang.ref.WeakReference
import java.net.Socket
import java.net.UnknownHostException


class ThermometerDemand(activity: MainActivity, val demanda:String, val host:String, val portNumber:Int, var timeout: Int) : AsyncTask<Int, Void, String>()  {
    private var connectSuccess: Boolean = true
    private var activityWeakReference : WeakReference<MainActivity> = WeakReference(activity)

    companion object {
        var response : String? = null
        var errorType = 0
    }


    private fun mostraNaTela(str:String) {
        ScreenLog.add(LogType.TO_LOG, str)
    }

    private fun mostraEmHistory(str:String) {
        ScreenLog.add(LogType.TO_HISTORY, str)
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

    override fun doInBackground(vararg params: Int?): String? {
        var demandThread: DemandThread? = null


        val runnableFxPosDemanda = Runnable {

            if ( demandThread != null) {
                Timber.i("XXXXXXX Runnable runnableFxPosDemanda=${demandThread!!.serverResponse}")
            }

        }


        val startTime = System.currentTimeMillis()
        val endTime = startTime + timeout
        Timber.i("doInBackground timeout = ${timeout}  startTime=${startTime}   endTime=${endTime}")

        var responseFromSocket : String? = null
        try {
            demandThread = DemandThread(host, portNumber, demanda, runnableFxPosDemanda)

            Timber.i("doInBackground DemandThread start" )
            demandThread.start()

            while ( System.currentTimeMillis() <  endTime) {
                Thread.sleep(20)
                if ( ! demandThread.isAlive ) {
                    responseFromSocket = demandThread.serverResponse
                    Timber.i("doInBackground responseFromSocket = ${responseFromSocket}")
                    break;
                }
            }
            if ( demandThread.isAlive ) {
                Timber.i("doInBackground interrupt")
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
        var activity= activityWeakReference.get()

        super.onPostExecute(result)

        Timber.i("onPostExecute responseFromThermometer=${result}" )
        response = result

        if (result != null) {
            mostraNaTela(result)
        }

        if ( activity == null || activity.isFinishing()) {
            return
        }

        activity.fxFimConsulta(result)
    }


    class DemandThread( val  host: String, val porta:Int, val demanda:String, val fxPosExecution: Runnable) : Thread() {
        var socket : Socket? = null
        var serverResponse : String? = null
        override fun run() {
            try {
                socket = openSocket(host, porta)
                if  ( socket != null) {
                    serverResponse = dealWithOpenSocket(socket!!, demanda)
                   fxPosExecution.run()
                    socket!!.close()
                }
            } catch (e: Exception) {
                Timber.d("Ocorreu uma Exception ")
                e.printStackTrace()
            }
        }
        private fun openSocket(host: String, porta: Int) : Socket? {
            var socket : Socket? = null
            try {
                Timber.e("WWWWWWWW Tentando socket host=${host}  porta=${porta}")
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

        fun dealWithOpenSocket(socket : Socket, demanda: String) : String? {
            var resposeFromServer : String? = null
            try {
                if (socket.isConnected()) {
                    val out = PrintWriter(
                        BufferedWriter(OutputStreamWriter(socket.getOutputStream()) ), true)
                    val `in` = BufferedReader(InputStreamReader(socket.getInputStream()))
                    out.write(demanda)
                    if ( ! demanda.contains("\n") ) {
                        out.write("\n")
                    }
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


    }



}


