package com.example.appwifi

import android.os.AsyncTask
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.io.*
import java.lang.ref.WeakReference
import java.net.Socket
import java.net.UnknownHostException


class ThermometerLoop(activity: MainActivity, val demanda:String, val host:String, val portNumber:Int, var timeout: Int, var conta:Int, val delay:Long) : AsyncTask<Int, String, String>()  {
    private var connectSuccess: Boolean = true
    private var activityWeakReference : WeakReference<MainActivity> = WeakReference(activity)

    companion object {
        var response : String? = null
        var errorType = 0
        var mainSocket : Socket? = null
        var out : PrintWriter? = null
        var `in` : BufferedReader? = null
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

    override fun onProgressUpdate(vararg values: String?) {
        super.onProgressUpdate(*values)

        var activity= activityWeakReference.get()

        if ( activity == null || activity.isFinishing()) {
            return
        }

        var str : String? = values[0]

        if ( str != null ) {
            if ( str.contains("ERROR") ) {
                mostraEmHistory(str)
            } else {
                mostraNaTela(str)
            }
        }
    }

    override fun doInBackground(vararg params: Int?): String? {
        var demandThread: DemandThread? = null
        var tentativa = 0
        var error=0

        var responseFromSocket : String? = null
        try {

            while ( tentativa++ < conta ) {

                val startTime = System.currentTimeMillis()
                val endTime = startTime + timeout

                demandThread = DemandThread(host, portNumber, demanda)
                demandThread.start()

                while ( System.currentTimeMillis() <  endTime) {
                    Thread.sleep(20)
                    if ( ! demandThread.isAlive ) {
                        break;
                    }
                }

                if ( demandThread.isAlive ) {
                    Timber.i("doInBackground interrupt")
                    demandThread.interrupt()
                }

                if (demandThread.serverResponse != null) {
                    publishProgress(String.format("%4d - %s", tentativa, demandThread.serverResponse))
                } else {
                    publishProgress(String.format("ERROR %d", ++error))
                    closeSocket()
                }

                Thread.sleep(delay)
            }
        } catch (e: IOException) {
            connectSuccess = false
            e.printStackTrace()
        }

        Timber.i("doInBackground return" )


        return responseFromSocket
    }

    fun closeSocket() {
        if ( mainSocket != null) {
            mainSocket!!.close()
            mainSocket = null
        }

        if ( out != null) {
            out!!.close()
            out = null
        }
        if ( `in` != null) {
            `in`!!.close()
            `in` = null
        }

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


    class DemandThread( val  host: String, val porta:Int, val demanda:String) : Thread() {
        var serverResponse : String? = null
        override fun run() {
            try {
                if ( mainSocket == null ) {
                    mainSocket = openSocket(host, porta)
                }
                if  ( mainSocket != null) {
                    serverResponse = dealWithOpenSocket(mainSocket!!, demanda)
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
                    out = PrintWriter(
                        BufferedWriter(OutputStreamWriter(socket.getOutputStream()) ), true)
                    `in` = BufferedReader(InputStreamReader(socket.getInputStream()))

                    if ( (out == null) || (`in` == null)) {
                        if ( out != null) {
                            out!!.close()
                            out=null
                        }
                        if ( `in` != null) {
                            `in`!!.close()
                            `in`=null
                        }
                        socket.close()
                        socket=null
                    }
                } else {
                    if ( out != null) {
                        out!!.close()
                        out=null
                    }
                    if ( `in` != null) {
                        `in`!!.close()
                        `in`=null
                    }
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
                    out!!.write(demanda)
                    if ( ! demanda.contains("\n") ) {
                        out!!.write("\n")
                    }
                    out!!.flush()
                    Timber.i("Calling Write ${demanda}")
                    resposeFromServer = `in`!!.readLine()
//                    out!!.close()
//                    `in`!!.close()
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


