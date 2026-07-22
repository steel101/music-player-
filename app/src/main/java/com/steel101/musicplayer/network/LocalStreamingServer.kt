package com.steel101.musicplayer.network

import com.steel101.musicplayer.data.Song
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket

class LocalStreamingServer() {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentSongProvider: (() -> Song?)? = null
    private var allSongsProvider: (() -> List<Song>)? = null

    fun start(port: Int = 8080, currentSong: () -> Song?, allSongs: () -> List<Song>) {
        if (isRunning) return
        currentSongProvider = currentSong
        allSongsProvider = allSongs
        isRunning = true
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                while (isRunning) {
                    val client = serverSocket?.accept()
                    client?.let { handleClient(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
        serverSocket = null
    }

    fun getIpAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val ip = addr.hostAddress
                        if (ip != null && (iface.name.contains("wlan") || iface.name.contains("eth"))) {
                            return ip
                        }
                    }
                }
            }
            
            val interfaces2 = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces2.hasMoreElements()) {
                val iface = interfaces2.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun handleClient(socket: Socket) {
        scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val line = reader.readLine() ?: return@launch
                
                val out = socket.getOutputStream()

                if (line.startsWith("GET /status")) {
                    val phoneSong = currentSongProvider?.invoke()
                    val statusJson = if (phoneSong != null) {
                        """{"id": "${phoneSong.id}", "title": "${phoneSong.title.replace("\"", "\\\"")}", "artist": "${phoneSong.artist.replace("\"", "\\\"")}"}"""
                    } else "{}"
                    
                    out.write("HTTP/1.1 200 OK\r\n".toByteArray())
                    out.write("Content-Type: application/json\r\n".toByteArray())
                    out.write("\r\n".toByteArray())
                    out.write(statusJson.toByteArray())
                } else if (line.startsWith("GET /stream")) {
                    val songId = line.substringAfter("id=", "").substringBefore(" ").toLongOrNull()
                    val song = if (songId != null) {
                        allSongsProvider?.invoke()?.find { it.id == songId }
                    } else {
                        currentSongProvider?.invoke()
                    }

                    if (song != null && song.path.isNotEmpty()) {
                        val file = File(song.path)
                        if (file.exists()) {
                            out.write("HTTP/1.1 200 OK\r\n".toByteArray())
                            out.write("Content-Type: audio/mpeg\r\n".toByteArray())
                            out.write("Content-Length: ${file.length()}\r\n".toByteArray())
                            out.write("Accept-Ranges: bytes\r\n".toByteArray())
                            out.write("\r\n".toByteArray())
                            
                            file.inputStream().use { input ->
                                input.copyTo(out)
                            }
                        } else {
                            out.write("HTTP/1.1 404 Not Found\r\n\r\n".toByteArray())
                        }
                    } else {
                        out.write("HTTP/1.1 404 Not Found\r\n\r\n".toByteArray())
                    }
                } else {
                    val phoneSong = currentSongProvider?.invoke()
                    val allSongs = allSongsProvider?.invoke() ?: emptyList()
                    
                    val phoneStatusHtml = if (phoneSong != null) {
                        """<div id="phone-status-container" class="phone-status" onclick="playSong('${phoneSong.id}', '${phoneSong.title.replace("'", "\\'")}', '${phoneSong.artist.replace("'", "\\'")}')">
                            <strong>Currently on Phone:</strong> <span id="phone-title">${phoneSong.title}</span> - <span id="phone-artist">${phoneSong.artist}</span>
                        </div>"""
                    } else """<div id="phone-status-container" class="phone-status" style="display:none"></div>"""

                    val songListHtml = allSongs.joinToString("") { s ->
                        """<div class="song-item" onclick="playSong('${s.id}', '${s.title.replace("'", "\\'")}', '${s.artist.replace("'", "\\'")}')">
                            <div class="song-info">
                                <div class="song-title">${s.title}</div>
                                <div class="song-artist">${s.artist}</div>
                            </div>
                        </div>"""
                    }

                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <title>Music Player Web</title>
                            <meta name="viewport" content="width=device-width, initial-scale=1">
                            <style>
                                body { background: #000; color: #FFD700; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; display: flex; flex-direction: column; height: 100vh; }
                                header { background: #1a1a1a; padding: 20px; text-align: center; border-bottom: 2px solid #FFD700; }
                                .phone-status { background: #333; padding: 10px; margin: 10px; border-radius: 10px; border-left: 5px solid #FFD700; cursor: pointer; font-size: 14px; }
                                #search { width: 90%; padding: 12px; margin: 10px auto; display: block; border-radius: 20px; border: 1px solid #FFD700; background: #222; color: #FFD700; }
                                #song-list { flex: 1; overflow-y: auto; padding: 10px; }
                                .song-item { padding: 15px; border-bottom: 1px solid #333; cursor: pointer; transition: background 0.2s; }
                                .song-item:hover { background: #111; }
                                .song-title { font-weight: bold; font-size: 16px; }
                                .song-artist { font-size: 14px; color: #aaa; margin-top: 4px; }
                                footer { background: #1a1a1a; padding: 20px; border-top: 2px solid #FFD700; }
                                #now-playing { margin-bottom: 15px; text-align: center; }
                                audio { width: 100%; filter: invert(1) hue-rotate(180deg) brightness(1.5); }
                            </style>
                        </head>
                        <body>
                            <header>
                                <h1>Music Library</h1>
                            </header>
                            $phoneStatusHtml
                            <input type="text" id="search" placeholder="Search songs..." onkeyup="filterSongs()">
                            <div id="song-list">$songListHtml</div>
                            <footer>
                                <div id="now-playing">
                                    <div id="web-title">Select a song</div>
                                    <div id="web-artist"></div>
                                </div>
                                <audio id="player" controls>
                                    <source id="audio-source" src="" type="audio/mpeg">
                                </audio>
                            </footer>
                            <script>
                                let currentPhoneSongId = null;
                                function playSong(id, title, artist) {
                                    document.getElementById('web-title').innerText = title;
                                    document.getElementById('web-artist').innerText = artist;
                                    const player = document.getElementById('player');
                                    const source = document.getElementById('audio-source');
                                    source.src = '/stream?id=' + id;
                                    player.load();
                                    player.play();
                                }
                                function filterSongs() {
                                    let input = document.getElementById('search').value.toLowerCase();
                                    let items = document.getElementsByClassName('song-item');
                                    for (let i = 0; i < items.length; i++) {
                                        let text = items[i].innerText.toLowerCase();
                                        items[i].style.display = text.includes(input) ? 'block' : 'none';
                                    }
                                }
                                function updatePhoneStatus() {
                                    fetch('/status')
                                        .then(response => response.json())
                                        .then(data => {
                                            const container = document.getElementById('phone-status-container');
                                            if (data.title) {
                                                document.getElementById('phone-title').innerText = data.title;
                                                document.getElementById('phone-artist').innerText = data.artist;
                                                container.style.display = 'block';
                                                container.onclick = () => playSong(data.id, data.title, data.artist);
                                            } else {
                                                container.style.display = 'none';
                                            }
                                        })
                                        .catch(() => {});
                                }
                                setInterval(updatePhoneStatus, 3000);
                            </script>
                        </body>
                        </html>
                    """.trimIndent()
                    
                    out.write("HTTP/1.1 200 OK\r\n".toByteArray())
                    out.write("Content-Type: text/html\r\n".toByteArray())
                    out.write("\r\n".toByteArray())
                    out.write(html.toByteArray())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                socket.close()
            }
        }
    }
}
