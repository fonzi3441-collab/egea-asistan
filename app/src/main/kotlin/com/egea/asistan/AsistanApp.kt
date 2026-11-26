package com.egea.asistan

import android.app.Application
import com.egea.asistan.servis.YuzenButonServisi
import com.egea.asistan.ses.Konusmaci
import com.egea.asistan.ses.OdakYoneticisi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Uygulama: TTS (Konusmaci) ve ses odağı burada tutulur ki dinleme ekranı kapanınca
 * sesli cevap yarıda kesilmesin.
 */
class AsistanApp : Application() {
    lateinit var ayarlar: Ayarlar
        private set
    lateinit var log: KomutLogu
        private set
    lateinit var odak: OdakYoneticisi
        private set
    lateinit var konusmaci: Konusmaci
        private set

    /** Aktiviteden bağımsız işler için (ör. konuşma bitince ses odağını bırakmak). */
    val kapsam = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        ornek = this
        ayarlar = Ayarlar(this)
        log = KomutLogu(this)
        odak = OdakYoneticisi(this)
        konusmaci = Konusmaci(this)
        konusmaci.baslat()
        YuzenButonServisi.uygunsaBaslat(this)
    }

    companion object {
        const val ETIKET = "EgeaAsistan"

        lateinit var ornek: AsistanApp
            private set
    }
}
