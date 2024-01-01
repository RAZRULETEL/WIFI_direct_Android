package com.mastik.wifi_direct.views

import android.content.Context
import android.text.TextUtils
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.mastik.wifi_direct.MainActivity
import com.mastik.wifi_direct.R
import com.mastik.wifi_direct.transfer.FileDescriptorTransferInfo

class TransferProgressIndicatorView(context: Context) : LinearLayout(context) {

    companion object{
        private const val PROGRESS_BAR_WIDTH_DP = 70
        private const val PROGRESS_BAR_HEIGHT_SCALE = 2f
        private const val SPACE_BETWEEN_ELEMENTS_DP = 5
    }

    private val nameText = TextView(context)
    private val progressBar = ProgressBar(
        context, null,
        android.R.attr.progressBarStyleHorizontal
    )
    private val etaText = TextView(context)
    val speedText = TextView(context)

    init {
        orientation = HORIZONTAL

        nameText.text = "File name"
        nameText.maxLines = 1
        nameText.ellipsize = TextUtils.TruncateAt.END
        nameText.layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)



        progressBar.max = 100
        progressBar.scaleY = PROGRESS_BAR_HEIGHT_SCALE
        val progressParams = LayoutParams(
            MainActivity.convertDpToPixel(PROGRESS_BAR_WIDTH_DP, context),
            LayoutParams.MATCH_PARENT
        )
        progressParams.leftMargin = MainActivity.convertDpToPixel(SPACE_BETWEEN_ELEMENTS_DP, context)
        progressParams.rightMargin = MainActivity.convertDpToPixel(SPACE_BETWEEN_ELEMENTS_DP, context)
        progressBar.layoutParams = progressParams



        etaText.text = context.getString(R.string.eta_transfer_info, 0, 0)
        etaText.layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).also { params -> params.rightMargin = MainActivity.convertDpToPixel(
            SPACE_BETWEEN_ELEMENTS_DP, context) }

        speedText.text = context.getString(R.string.speed_transfer_info, 0, "KB")

        this.addView(nameText)
        this.addView(progressBar)
        this.addView(etaText)
        this.addView(speedText)
    }

    constructor(
        fileName: String,
        fileTransferProgressInfo: FileDescriptorTransferInfo,
        context: Context
    ) : this(context) {
        setFileName(fileName)

        setFileTransferProgressInfo(fileTransferProgressInfo)
    }

    fun setFileTransferProgressInfo(fileTransferProgressInfo: FileDescriptorTransferInfo){
        fileTransferProgressInfo.addProgressListener {
            post {
                progressBar.progress =
                    ((it.bytesProgress.toDouble() / it.bytesTotal) * 100).toInt()
                etaText.text = context.getString(R.string.eta_transfer_info, (it.ETA / 60).toInt(), (it.ETA % 60).toInt())
                speedText.text = context.getString(R.string.speed_transfer_info, (it.currentSpeed / 1024).toInt(), "KB")
            }
        }
    }

    fun setFileName(fileName: String){
        post {
            nameText.text = fileName
        }
    }
}