package com.egea.asistan.ui

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.egea.asistan.BuildConfig
import com.egea.asistan.core.TurkceMetin

class ListenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = TurkceMetin.normalize("Egea Asistan ${BuildConfig.VERSION_NAME} ${BuildConfig.GUNCELLEME_REPO}")
            textSize = 24f
        })
    }
}
