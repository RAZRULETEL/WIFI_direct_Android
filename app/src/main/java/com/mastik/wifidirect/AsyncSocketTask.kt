import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.AsyncTask
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import com.mastik.wifidirect.R
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Reader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset

class AsyncSocketTask(
    private val statusText: TextView,
    private val messageText: TextView,
    private val sendButton: ImageButton,
) : AsyncTask<Void, Void, String?>() {

    override fun doInBackground(vararg params: Void): String? {
        getConnection()
        return "test";
    }

    private fun tryConnect(){
        val client = Socket()
        try {
            client.connect(InetSocketAddress("192.168.137.1", R.integer.port), 3000)
            statusText.post { statusText.setBackgroundColor(Color.GREEN) }
        }
        catch (e: Exception){
            statusText.post { statusText.setBackgroundColor(Color.RED) }
            e.printStackTrace()
        }finally {
            client.close()
        }
    }

    private fun getConnection(){
        val server = ServerSocket(50_001)

        try {
            val client = server.accept()
            statusText.post { statusText.setBackgroundColor(Color.GREEN) }
            statusText.post { statusText.text = "" }

            val outTextStream = OutputStreamWriter(client.getOutputStream(), Charset.forName("UTF-8"))

            sendButton.setOnClickListener {
                Thread{
                    Log.d("SOCKET WRITE", "Sending...")
                    val len = messageText.text.toString().length
                    outTextStream.write(len);outTextStream.write(len / 256)
                    outTextStream.write(len / 256 / 256);outTextStream.write(len / 256 / 256 / 256)

                    outTextStream.write(messageText.text.toString())

                    outTextStream.flush()
                }.start()
            }

            val stream = InputStreamReader(client.getInputStream())
            val buff = CharBuffer.allocate(8192)
            while(client.isConnected){
                val data = stream.read(buff)
                statusText.post { statusText.append("$data: $buff")}
                buff.clear()
            }
            //client.close()
        }catch (e :Exception){
            statusText.post { statusText.post { statusText.setBackgroundColor(Color.RED) } }
            e.printStackTrace()
        }finally {
            server.close()
        }
    }
}