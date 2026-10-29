package com.natantec.mobiletube

import android.app.Application
import com.natantec.mobiletube.data.OkHttpDownloader
import com.natantec.mobiletube.data.Prefs
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class MobileTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        com.natantec.mobiletube.data.HistoryStore.init(this)
        NewPipe.init(OkHttpDownloader(), Localization("pt", "BR"), ContentCountry("BR"))
    }
}
