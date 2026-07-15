package com.steel101.musicplayer.network

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class AppDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        
        val dataMethod = request.javaClass.methods.find { 
            it.name == "dataToSend" || it.name == "httpData" || it.name == "getHttpData" || it.name == "data" || it.name == "getData"
        }
        val data = dataMethod?.invoke(request) as? ByteArray
        
        val requestBody = if (data != null) {
            data.toRequestBody()
        } else if (method == "POST") {
            ByteArray(0).toRequestBody()
        } else {
            null
        }

        val httpRequestBuilder = okhttp3.Request.Builder()
            .url(request.url())
            .method(method, requestBody)

        request.headers().forEach { (name, values) ->
            for (value in values) {
                if (!name.equals("Content-Length", ignoreCase = true)) {
                    httpRequestBuilder.addHeader(name, value)
                }
            }
        }
        
        val hasUserAgent = request.headers().any { it.key.equals("User-Agent", ignoreCase = true) }
        if (!hasUserAgent) {
            httpRequestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
        }

        val okResponse = client.newCall(httpRequestBuilder.build()).execute()
        val body = okResponse.body?.string()

        return Response(
            okResponse.code,
            okResponse.message,
            okResponse.headers.toMultimap(),
            body,
            okResponse.request.url.toString()
        )
    }
}
