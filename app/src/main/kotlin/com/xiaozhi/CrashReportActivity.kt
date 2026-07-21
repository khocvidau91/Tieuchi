package com.xiaozhi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CrashReportActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STACK_TRACE = "extra_stack_trace"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash_report)

        val tvError = findViewById<TextView>(R.id.tv_error)
        val btnCopy = findViewById<Button>(R.id.btn_copy)
        val btnShare = findViewById<Button>(R.id.btn_share)
        val btnClose = findViewById<Button>(R.id.btn_close)

        val stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: "Không có thông tin lỗi."
        tvError.text = stackTrace

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("crash_log", stackTrace)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Đã sao chép", Toast.LENGTH_SHORT).show()
        }

        btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, stackTrace)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(shareIntent, "Chia sẻ lỗi"))
        }

        btnClose.setOnClickListener {
            finishAffinity()   // đóng tất cả activity, thoát app
        }
    }
}