package com.natantec.mobiletube.data

import android.content.Context
import android.content.SharedPreferences

/** Configurações simples do app, guardadas em SharedPreferences. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences("mobiletube", Context.MODE_PRIVATE)
    }

    var blockShorts: Boolean
        get() = sp.getBoolean("blockShorts", true)
        set(v) = sp.edit().putBoolean("blockShorts", v).apply()

    var sponsorBlock: Boolean
        get() = sp.getBoolean("sponsorBlock", true)
        set(v) = sp.edit().putBoolean("sponsorBlock", v).apply()

    /** Altura máxima do vídeo (360, 480, 720 ou 1080). */
    var maxHeight: Int
        get() = sp.getInt("maxHeight", 720)
        set(v) = sp.edit().putInt("maxHeight", v).apply()
}
