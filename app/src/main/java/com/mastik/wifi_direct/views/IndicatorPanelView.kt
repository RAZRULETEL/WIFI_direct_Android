package com.mastik.wifi_direct.views

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import com.mastik.wifi_direct.R
import com.mastik.wifi_direct.transfer.FileDescriptorTransferInfo
import timber.log.Timber
import java.util.function.Consumer

class IndicatorPanelView(context: Context, attributes: AttributeSet? = null): LinearLayout(context, attributes)  {

    private val ingoingIndicators: LinearLayout = LinearLayout(context)
    private val outgoingIndicators: LinearLayout = LinearLayout(context)

    private val ingoingLabel: TextView = TextView(context).also { it.text = context.getString(R.string.no_incoming_connections_label) }
    private val outgoingLabel: TextView = TextView(context).also { it.text = context.getString(R.string.no_outgoing_connections_label)}

    init{
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        ingoingIndicators.orientation = VERTICAL
        outgoingIndicators.orientation = VERTICAL

        ingoingIndicators.layoutParams = this.layoutParams
        outgoingIndicators.layoutParams = this.layoutParams

        ingoingIndicators.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if((v as LinearLayout).childCount == 0)
                post { ingoingLabel.text = context.getString(R.string.no_incoming_connections_label) }
            else
                post { ingoingLabel.text = context.getString(R.string.receiving_files_label, v.childCount)}
        }
        outgoingIndicators.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if((v as LinearLayout).childCount == 0)
                post { outgoingLabel.text = context.getString(R.string.no_outgoing_connections_label) }
            else
                post { outgoingLabel.text = context.getString(R.string.sending_files_label, v.childCount)}
        }

        this.addView(ingoingLabel)
        this.addView(ingoingIndicators)
        this.addView(outgoingLabel)
        this.addView(outgoingIndicators)
    }

    fun addIngoingTransferIndicator(fileName: String, fileTransferProgressInfo: FileDescriptorTransferInfo){
        val indicator = TransferProgressIndicatorView(fileName, fileTransferProgressInfo, context)

        post { ingoingIndicators.addView(indicator) }

        fileTransferProgressInfo.onTransferEndListener = Consumer {
            post { ingoingIndicators.removeView(indicator) }
        }
    }

    fun addOutgoingTransferIndicator(fileName: String, fileTransferProgressInfo: FileDescriptorTransferInfo){
        val indicator = TransferProgressIndicatorView(fileName, fileTransferProgressInfo, context)

        post { outgoingIndicators.addView(indicator) }

        fileTransferProgressInfo.onTransferEndListener = Consumer {
            Timber.d("onTransferEnd $it")
            post { outgoingIndicators.removeView(indicator) }
        }
    }
}