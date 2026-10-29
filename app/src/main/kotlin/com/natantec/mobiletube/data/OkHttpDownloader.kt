package com.natantec.mobiletube.data

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

object HttpClients {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

/** Ponte entre o NewPipeExtractor e o OkHttp. */
class OkHttpDownloader : Downloader() {

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val url = request.url()
        val data = request.dataToSend()

        val body = when {
            data != null -> data.toRequestBody()
            method == "POST" || method == "PUT" || method == "PATCH" -> ByteArray(0).toRequestBody()
            else -> null
        }

        val builder = okhttp3.Request.Builder()
            .method(method, body)
            .url(url)
            .header("User-Agent", USER_AGENT)

        for ((name, values) in request.headers()) {
            builder.removeHeader(name)
            for (value in values) builder.addHeader(name, value)
        }

        HttpClients.client.newCall(builder.build()).execute().use { resp ->
            if (resp.code == 429) {
                throw ReCaptchaException("O YouTube pediu verificação (reCAPTCHA)", url)
            }
            val text = resp.body?.string()
            return Response(
                resp.code,
                resp.message,
                resp.headers.toMultimap(),
                text,
                resp.request.url.toString()
            )
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}
