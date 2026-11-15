package com.example.game.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.example.game.model.CharacterAppearance
import com.example.game.model.FlagStatus
import com.example.game.model.GameMode
import com.example.game.model.MapId
import com.example.game.model.MatchConfig
import com.example.game.model.PlayerId
import com.example.game.model.PlayerInputState
import com.example.game.model.PlayerState
import com.example.game.model.WeaponType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

data class DiscoveredLanRoom(
    val roomCode: String,
    val hostName: String,
    val hostIp: String,
    val tcpPort: Int,
    val playerCount: Int,
    val maxPlayers: Int,
    val mapId: MapId,
    val gameMode: GameMode,
    val lastSeenMs: Long
)

data class ConnectedPeer(
    val id: PlayerId,
    val callsign: String,
    val teamIndex: Int,
    val isHost: Boolean,
    val appearance: CharacterAppearance = CharacterAppearance()
)

enum class LanRole {
    NONE,
    HOST,
    CLIENT
}

data class LanLobbyState(
    val role: LanRole = LanRole.NONE,
    val isConnected: Boolean = false,
    val roomCode: String = "",
    val hostIp: String = "",
    val statusMessage: String = "Ready for local Wi-Fi or Android Hotspot",
    val errorMessage: String? = null,
    val peers: List<ConnectedPeer> = emptyList(),
    val discoveredRooms: List<DiscoveredLanRoom> = emptyList(),
    val matchConfig: MatchConfig = MatchConfig(isMultiplayer = true),
    val matchStarted: Boolean = false
)

data class RemoteInterpolationTarget(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var aimAngleRad: Float,
    var facingDir: Float,
    var health: Float,
    var isAlive: Boolean,
    var isCrouching: Boolean,
    var isJetpacking: Boolean,
    var weaponOrdinal: Int,
    var kills: Int,
    var deaths: Int,
    var teamIndex: Int,
    var callsign: String,
    var muzzleFlash: Boolean
)

/**
 * Real offline Wi-Fi / Android Hotspot LAN multiplayer manager.
 * Combines TCP reliable room/lobby/match control + UDP low-latency 25Hz state sync +
 * NsdManager & UDP broadcast discovery for up to 12 real human players with zero internet.
 */
class LocalMultiplayerManager(private val context: Context?) {

    private val _lobbyState = MutableStateFlow(LanLobbyState())
    val lobbyState: StateFlow<LanLobbyState> = _lobbyState.asStateFlow()

    val remoteInputs = ConcurrentHashMap<PlayerId, PlayerInputState>()
    val clientInterpTargets = ConcurrentHashMap<PlayerId, RemoteInterpolationTarget>()

    @Volatile var localPlayerId: PlayerId = "host_0"
    @Volatile var onMatchStartSignal: ((MatchConfig, List<ConnectedPeer>) -> Unit)? = null
    @Volatile var onHostModeUpdate: ((blueScore: Int, redScore: Int, blueFlagX: Float, blueFlagY: Float, blueStatus: Int, redFlagX: Float, redFlagY: Float, redStatus: Int, cpOwner: Int, cpProgress: Float, timeLeft: Float, matchOver: Boolean, winner: String) -> Unit)? = null

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var udpSocket: DatagramSocket? = null
    private var discoverySocket: DatagramSocket? = null
    private var nsdManager: NsdManager? = null
    private var nsdRegListener: NsdManager.RegistrationListener? = null
    private var nsdDiscListener: NsdManager.DiscoveryListener? = null

    private val clientWriters = ConcurrentHashMap<PlayerId, BufferedWriter>()
    private val clientUdpAddresses = ConcurrentHashMap<PlayerId, InetAddress>()
    private val discoveredMap = ConcurrentHashMap<String, DiscoveredLanRoom>()
    private val peerList = CopyOnWriteArrayList<ConnectedPeer>()

    @Volatile private var running = false
    @Volatile private var hostIpAddress: String = ""
    private var clientWriter: BufferedWriter? = null
    private var lastSnapshotSentMs = 0L

    fun getLocalIpAddress(): String {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val candidates = mutableListOf<Pair<Int, String>>()
            for (ni in interfaces) {
                if (!ni.isUp || ni.isLoopback) continue
                for (addr in ni.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        val priority = when {
                            ni.name.contains("ap", ignoreCase = true) -> 0
                            ip.startsWith("192.168.43.") || ip.startsWith("192.168.49.") -> 1
                            ni.name.contains("wlan", ignoreCase = true) -> 2
                            else -> 3
                        }
                        candidates.add(priority to ip)
                    }
                }
            }
            candidates.minByOrNull { it.first }?.second ?: ""
        }.getOrDefault("")
    }

    fun encodeRoomCode(ip: String): String {
        val parts = ip.split(".")
        if (parts.size != 4) return ""
        return parts.joinToString("") { p ->
            (p.toIntOrNull()?.coerceIn(0, 255) ?: 0).toString(16).uppercase().padStart(2, '0')
        }
    }

    fun decodeRoomCodeToIp(input: String): String? {
        val cleaned = input.trim().uppercase()
        if (cleaned.contains(".")) {
            val parts = cleaned.split(".")
            if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) return cleaned
            return null
        }
        if (cleaned.length != 8 || !cleaned.all { it in '0'..'9' || it in 'A'..'F' }) return null
        return (0 until 4).joinToString(".") { i ->
            cleaned.substring(i * 2, i * 2 + 2).toInt(16).toString()
        }
    }

    fun startHosting(hostCallsign: String, appearance: CharacterAppearance, config: MatchConfig) {
        stopAll()
        val ip = getLocalIpAddress()
        if (ip.isBlank()) {
            _lobbyState.value = LanLobbyState(
                errorMessage = "No local Wi-Fi or Hotspot IPv4 interface found. Enable Wi-Fi or Android Hotspot first."
            )
            return
        }

        running = true
        hostIpAddress = ip
        localPlayerId = "host_0"
        val roomCode = encodeRoomCode(ip)
        val hostPeer = ConnectedPeer(localPlayerId, hostCallsign, config.localTeam, isHost = true, appearance = appearance)
        peerList.clear()
        peerList.add(hostPeer)

        _lobbyState.value = LanLobbyState(
            role = LanRole.HOST,
            isConnected = true,
            roomCode = roomCode,
            hostIp = ip,
            statusMessage = "Hosting room $roomCode ($ip) — Waiting for players on Wi-Fi / Hotspot",
            peers = peerList.toList(),
            matchConfig = config.copy(isMultiplayer = true)
        )

        startHostTcpServer()
        startHostUdpRelay()
        startBeaconBroadcaster(roomCode, hostCallsign)
        registerNsdService(hostCallsign, roomCode)
    }

    fun updateHostConfig(config: MatchConfig) {
        if (_lobbyState.value.role != LanRole.HOST) return
        val updated = config.copy(isMultiplayer = true)
        _lobbyState.value = _lobbyState.value.copy(matchConfig = updated)
        broadcastTcpLobbyState()
    }

    fun toggleLocalTeam() {
        val state = _lobbyState.value
        val me = peerList.firstOrNull { it.id == localPlayerId } ?: return
        val nextTeam = if (me.teamIndex == 0) 1 else 0
        if (state.role == LanRole.HOST) {
            val idx = peerList.indexOfFirst { it.id == localPlayerId }
            if (idx >= 0) peerList[idx] = me.copy(teamIndex = nextTeam)
            _lobbyState.value = state.copy(peers = peerList.toList())
            broadcastTcpLobbyState()
        } else if (state.role == LanRole.CLIENT) {
            sendTcpToHost(JSONObject().put("type", "team").put("team", nextTeam))
        }
    }

    fun hostStartMatch() {
        val state = _lobbyState.value
        if (state.role != LanRole.HOST) return
        val cfg = state.matchConfig.copy(isMultiplayer = true, botCount = 0)
        val msg = JSONObject()
            .put("type", "start_match")
            .put("config", encodeConfig(cfg))
            .put("peers", encodePeers(peerList))
        broadcastTcpRaw(msg.toString())
        _lobbyState.value = state.copy(matchStarted = true)
        onMatchStartSignal?.invoke(cfg, peerList.toList())
    }

    fun startDiscovery() {
        stopDiscoveryOnly()
        discoveredMap.clear()
        running = true
        _lobbyState.value = _lobbyState.value.copy(
            statusMessage = "Scanning local Wi-Fi / Hotspot for rooms...",
            errorMessage = null,
            discoveredRooms = emptyList()
        )
        startUdpDiscoveryListener()
        startNsdDiscovery()
    }

    fun joinRoomByCodeOrIp(
        codeOrIp: String,
        callsign: String,
        appearance: CharacterAppearance
    ) {
        val targetIp = decodeRoomCodeToIp(codeOrIp)
        if (targetIp == null) {
            _lobbyState.value = _lobbyState.value.copy(
                errorMessage = "Invalid Room Code or IPv4 address. Enter 8-char code or IP like 192.168.43.1"
            )
            return
        }
        stopAll()
        running = true
        hostIpAddress = targetIp
        _lobbyState.value = LanLobbyState(
            role = LanRole.CLIENT,
            isConnected = false,
            hostIp = targetIp,
            roomCode = encodeRoomCode(targetIp),
            statusMessage = "Connecting to $targetIp:$TCP_PORT..."
        )

        Thread {
            try {
                val sock = Socket()
                sock.connect(InetSocketAddress(targetIp, TCP_PORT), 4500)
                clientSocket = sock
                val writer = BufferedWriter(OutputStreamWriter(sock.getOutputStream()))
                val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                clientWriter = writer

                val joinJson = JSONObject()
                    .put("type", "join")
                    .put("callsign", callsign)
                    .put("shirt", appearance.shirtHex)
                    .put("visor", appearance.visorHex)
                    .put("headgear", appearance.headgearStyle.name)
                writer.write(joinJson.toString() + "\n")
                writer.flush()

                startClientUdpListener()

                while (running && !sock.isClosed) {
                    val line = reader.readLine() ?: break
                    handleHostTcpMessage(JSONObject(line))
                }
                if (running) {
                    _lobbyState.value = _lobbyState.value.copy(
                        isConnected = false,
                        errorMessage = "Disconnected from host ($targetIp)."
                    )
                }
            } catch (e: Exception) {
                _lobbyState.value = _lobbyState.value.copy(
                    role = LanRole.NONE,
                    isConnected = false,
                    errorMessage = "Could not reach $targetIp: ${e.message ?: "Connection failed"}"
                )
            }
        }.apply { isDaemon = true; name = "LanClientTcp"; start() }
    }

    fun sendClientInput(input: PlayerInputState) {
        if (_lobbyState.value.role != LanRole.CLIENT || !_lobbyState.value.isConnected) return
        val sock = udpSocket ?: return
        val addr = runCatching { InetAddress.getByName(hostIpAddress) }.getOrNull() ?: return
        val json = JSONObject()
            .put("type", "input")
            .put("id", localPlayerId)
            .put("mx", input.moveX.toDouble())
            .put("cr", input.crouchPressed)
            .put("jp", input.jumpPressed)
            .put("jt", input.jetpackHeld)
            .put("am", input.aimAngleRad.toDouble())
            .put("ea", input.hasExplicitAim)
            .put("fr", input.isFiring)
            .put("gr", input.grenadeRequested)
            .put("sw", input.selectWeaponIndex)
            .put("rl", input.reloadRequested)
        val bytes = json.toString().toByteArray()
        Thread {
            runCatching { sock.send(DatagramPacket(bytes, bytes.size, addr, UDP_HOST_PORT)) }
        }.start()
    }

    fun hostBroadcastWorldSnapshot(
        players: Collection<PlayerState>,
        blueScore: Int,
        redScore: Int,
        blueFlagX: Float, blueFlagY: Float, blueFlagStatus: FlagStatus,
        redFlagX: Float, redFlagY: Float, redFlagStatus: FlagStatus,
        cpOwner: Int, cpProgress: Float,
        timeLeft: Float,
        matchOver: Boolean,
        winnerLabel: String
    ) {
        if (_lobbyState.value.role != LanRole.HOST) return
        val now = System.currentTimeMillis()
        if (!matchOver && now - lastSnapshotSentMs < 38L) return // ~26 Hz
        lastSnapshotSentMs = now

        val arr = JSONArray()
        for (p in players) {
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("cs", p.callsign)
                    .put("tm", p.teamIndex)
                    .put("x", p.x.toDouble())
                    .put("y", p.y.toDouble())
                    .put("vx", p.vx.toDouble())
                    .put("vy", p.vy.toDouble())
                    .put("am", p.aimAngleRad.toDouble())
                    .put("fd", p.facingDir.toDouble())
                    .put("hp", p.health.toDouble())
                    .put("al", p.isAlive)
                    .put("cr", p.isCrouching)
                    .put("jt", p.isJetpacking)
                    .put("wp", p.currentWeaponIndex)
                    .put("k", p.kills)
                    .put("d", p.deaths)
                    .put("mf", p.muzzleFlashTimer > 0f)
            )
        }
        val packet = JSONObject()
            .put("type", "snap")
            .put("bs", blueScore)
            .put("rs", redScore)
            .put("bfx", blueFlagX.toDouble()).put("bfy", blueFlagY.toDouble()).put("bfs", blueFlagStatus.ordinal)
            .put("rfx", redFlagX.toDouble()).put("rfy", redFlagY.toDouble()).put("rfs", redFlagStatus.ordinal)
            .put("cpo", cpOwner).put("cpp", cpProgress.toDouble())
            .put("tl", timeLeft.toDouble())
            .put("mo", matchOver)
            .put("wn", winnerLabel)
            .put("ps", arr)

        val bytes = packet.toString().toByteArray()
        val sock = udpSocket ?: return
        for (addr in clientUdpAddresses.values) {
            runCatching { sock.send(DatagramPacket(bytes, bytes.size, addr, UDP_CLIENT_PORT)) }
        }
    }

    fun stopAll() {
        running = false
        stopDiscoveryOnly()
        unregisterNsd()
        runCatching { serverSocket?.close() }
        runCatching { clientSocket?.close() }
        runCatching { udpSocket?.close() }
        serverSocket = null
        clientSocket = null
        udpSocket = null
        clientWriter = null
        clientWriters.clear()
        clientUdpAddresses.clear()
        peerList.clear()
        remoteInputs.clear()
        clientInterpTargets.clear()
        _lobbyState.value = LanLobbyState()
    }

    private fun stopDiscoveryOnly() {
        runCatching { discoverySocket?.close() }
        discoverySocket = null
        nsdDiscListener?.let { listener ->
            runCatching { nsdManager?.stopServiceDiscovery(listener) }
        }
        nsdDiscListener = null
    }

    private fun startHostTcpServer() {
        Thread {
            try {
                val srv = ServerSocket(TCP_PORT)
                serverSocket = srv
                var nextClientNum = 1
                while (running && !srv.isClosed) {
                    val client = srv.accept()
                    if (peerList.size >= MAX_PLAYERS) {
                        client.close()
                        continue
                    }
                    val peerId = "peer_${nextClientNum++}"
                    clientUdpAddresses[peerId] = client.inetAddress
                    handleClientConnection(peerId, client)
                }
            } catch (_: Throwable) {
            }
        }.apply { isDaemon = true; name = "LanHostTcpAccept"; start() }
    }

    private fun handleClientConnection(peerId: PlayerId, sock: Socket) {
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                val writer = BufferedWriter(OutputStreamWriter(sock.getOutputStream()))
                clientWriters[peerId] = writer

                while (running && !sock.isClosed) {
                    val line = reader.readLine() ?: break
                    val msg = JSONObject(line)
                    when (msg.optString("type")) {
                        "join" -> {
                            val team = peerList.size % 2
                            val app = CharacterAppearance(
                                shirtHex = msg.optLong("shirt", 0xFF3A523E),
                                visorHex = msg.optLong("visor", 0xFF38BDF8)
                            )
                            val peer = ConnectedPeer(
                                id = peerId,
                                callsign = msg.optString("callsign", "SOLDIER").take(14),
                                teamIndex = team,
                                isHost = false,
                                appearance = app
                            )
                            peerList.add(peer)
                            writer.write(JSONObject().put("type", "welcome").put("yourId", peerId).toString() + "\n")
                            writer.flush()
                            _lobbyState.value = _lobbyState.value.copy(peers = peerList.toList())
                            broadcastTcpLobbyState()
                        }
                        "team" -> {
                            val newTeam = msg.optInt("team", 0).coerceIn(0, 1)
                            val idx = peerList.indexOfFirst { it.id == peerId }
                            if (idx >= 0) {
                                peerList[idx] = peerList[idx].copy(teamIndex = newTeam)
                                _lobbyState.value = _lobbyState.value.copy(peers = peerList.toList())
                                broadcastTcpLobbyState()
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
            } finally {
                clientWriters.remove(peerId)
                clientUdpAddresses.remove(peerId)
                peerList.removeAll { it.id == peerId }
                remoteInputs.remove(peerId)
                _lobbyState.value = _lobbyState.value.copy(peers = peerList.toList())
                broadcastTcpLobbyState()
                runCatching { sock.close() }
            }
        }.apply { isDaemon = true; name = "LanHostClient_$peerId"; start() }
    }

    private fun startHostUdpRelay() {
        Thread {
            try {
                val sock = DatagramSocket(UDP_HOST_PORT)
                udpSocket = sock
                val buf = ByteArray(2048)
                while (running && !sock.isClosed) {
                    val pkt = DatagramPacket(buf, buf.size)
                    sock.receive(pkt)
                    val json = JSONObject(String(pkt.data, 0, pkt.length))
                    if (json.optString("type") == "input") {
                        val id = json.optString("id")
                        clientUdpAddresses[id] = pkt.address
                        remoteInputs[id] = PlayerInputState(
                            moveX = json.optDouble("mx", 0.0).toFloat(),
                            crouchPressed = json.optBoolean("cr", false),
                            jumpPressed = json.optBoolean("jp", false),
                            jetpackHeld = json.optBoolean("jt", false),
                            aimAngleRad = json.optDouble("am", 0.0).toFloat(),
                            hasExplicitAim = json.optBoolean("ea", false),
                            isFiring = json.optBoolean("fr", false),
                            grenadeRequested = json.optBoolean("gr", false),
                            selectWeaponIndex = json.optInt("sw", -1),
                            reloadRequested = json.optBoolean("rl", false)
                        )
                    }
                }
            } catch (_: Throwable) {
            }
        }.apply { isDaemon = true; name = "LanHostUdp"; start() }
    }

    private fun startClientUdpListener() {
        Thread {
            try {
                val sock = DatagramSocket(UDP_CLIENT_PORT)
                udpSocket = sock
                val buf = ByteArray(8192)
                while (running && !sock.isClosed) {
                    val pkt = DatagramPacket(buf, buf.size)
                    sock.receive(pkt)
                    val json = JSONObject(String(pkt.data, 0, pkt.length))
                    if (json.optString("type") == "snap") {
                        val ps = json.getJSONArray("ps")
                        for (i in 0 until ps.length()) {
                            val o = ps.getJSONObject(i)
                            val id = o.getString("id")
                            clientInterpTargets[id] = RemoteInterpolationTarget(
                                x = o.getDouble("x").toFloat(),
                                y = o.getDouble("y").toFloat(),
                                vx = o.getDouble("vx").toFloat(),
                                vy = o.getDouble("vy").toFloat(),
                                aimAngleRad = o.getDouble("am").toFloat(),
                                facingDir = o.getDouble("fd").toFloat(),
                                health = o.getDouble("hp").toFloat(),
                                isAlive = o.getBoolean("al"),
                                isCrouching = o.getBoolean("cr"),
                                isJetpacking = o.getBoolean("jt"),
                                weaponOrdinal = o.getInt("wp"),
                                kills = o.getInt("k"),
                                deaths = o.getInt("d"),
                                teamIndex = o.getInt("tm"),
                                callsign = o.getString("cs"),
                                muzzleFlash = o.getBoolean("mf")
                            )
                        }
                        onHostModeUpdate?.invoke(
                            json.optInt("bs"), json.optInt("rs"),
                            json.optDouble("bfx").toFloat(), json.optDouble("bfy").toFloat(), json.optInt("bfs"),
                            json.optDouble("rfx").toFloat(), json.optDouble("rfy").toFloat(), json.optInt("rfs"),
                            json.optInt("cpo", -1), json.optDouble("cpp", 0.0).toFloat(),
                            json.optDouble("tl", 180.0).toFloat(),
                            json.optBoolean("mo", false),
                            json.optString("wn", "")
                        )
                    }
                }
            } catch (_: Throwable) {
            }
        }.apply { isDaemon = true; name = "LanClientUdp"; start() }
    }

    private fun handleHostTcpMessage(msg: JSONObject) {
        when (msg.optString("type")) {
            "welcome" -> {
                localPlayerId = msg.optString("yourId")
                _lobbyState.value = _lobbyState.value.copy(
                    isConnected = true,
                    statusMessage = "Connected to room ${_lobbyState.value.roomCode}"
                )
            }
            "lobby_state" -> {
                val peers = decodePeers(msg.getJSONArray("peers"))
                val cfg = decodeConfig(msg.getJSONObject("config"))
                _lobbyState.value = _lobbyState.value.copy(
                    isConnected = true,
                    peers = peers,
                    matchConfig = cfg
                )
            }
            "start_match" -> {
                val cfg = decodeConfig(msg.getJSONObject("config"))
                val peers = decodePeers(msg.getJSONArray("peers"))
                _lobbyState.value = _lobbyState.value.copy(matchConfig = cfg, peers = peers, matchStarted = true)
                onMatchStartSignal?.invoke(cfg, peers)
            }
        }
    }

    private fun broadcastTcpLobbyState() {
        val msg = JSONObject()
            .put("type", "lobby_state")
            .put("config", encodeConfig(_lobbyState.value.matchConfig))
            .put("peers", encodePeers(peerList))
        broadcastTcpRaw(msg.toString())
    }

    private fun broadcastTcpRaw(line: String) {
        Thread {
            for (w in clientWriters.values) {
                runCatching {
                    w.write(line + "\n")
                    w.flush()
                }
            }
        }.start()
    }

    private fun sendTcpToHost(json: JSONObject) {
        val w = clientWriter ?: return
        Thread {
            runCatching {
                w.write(json.toString() + "\n")
                w.flush()
            }
        }.start()
    }

    private fun startBeaconBroadcaster(roomCode: String, hostName: String) {
        Thread {
            try {
                val sock = DatagramSocket()
                sock.broadcast = true
                val bcastAddr = InetAddress.getByName("255.255.255.255")
                while (running && _lobbyState.value.role == LanRole.HOST) {
                    val state = _lobbyState.value
                    val json = JSONObject()
                        .put("app", "MINI_BATTLE_ARENA")
                        .put("code", roomCode)
                        .put("host", hostName)
                        .put("ip", hostIpAddress)
                        .put("port", TCP_PORT)
                        .put("cnt", peerList.size)
                        .put("max", MAX_PLAYERS)
                        .put("map", state.matchConfig.mapId.name)
                        .put("mode", state.matchConfig.gameMode.name)
                    val data = json.toString().toByteArray()
                    runCatching { sock.send(DatagramPacket(data, data.size, bcastAddr, DISCOVERY_UDP_PORT)) }
                    Thread.sleep(1200)
                }
                sock.close()
            } catch (_: Throwable) {
            }
        }.apply { isDaemon = true; name = "LanBeacon"; start() }
    }

    private fun startUdpDiscoveryListener() {
        Thread {
            try {
                val sock = DatagramSocket(DISCOVERY_UDP_PORT)
                sock.broadcast = true
                discoverySocket = sock
                val buf = ByteArray(1024)
                while (running && !sock.isClosed) {
                    val pkt = DatagramPacket(buf, buf.size)
                    sock.receive(pkt)
                    val json = runCatching { JSONObject(String(pkt.data, 0, pkt.length)) }.getOrNull() ?: continue
                    if (json.optString("app") != "MINI_BATTLE_ARENA") continue
                    val ip = json.optString("ip").ifBlank { pkt.address.hostAddress ?: continue }
                    if (ip == hostIpAddress && _lobbyState.value.role == LanRole.HOST) continue
                    val room = DiscoveredLanRoom(
                        roomCode = json.optString("code", encodeRoomCode(ip)),
                        hostName = json.optString("host", "HOST"),
                        hostIp = ip,
                        tcpPort = json.optInt("port", TCP_PORT),
                        playerCount = json.optInt("cnt", 1),
                        maxPlayers = json.optInt("max", MAX_PLAYERS),
                        mapId = MapId.entries.firstOrNull { it.name == json.optString("map") } ?: MapId.MILITARY_BASE,
                        gameMode = GameMode.entries.firstOrNull { it.name == json.optString("mode") } ?: GameMode.DEATHMATCH,
                        lastSeenMs = System.currentTimeMillis()
                    )
                    discoveredMap[ip] = room
                    publishDiscoveredRooms()
                }
            } catch (_: Throwable) {
            }
        }.apply { isDaemon = true; name = "LanDiscoveryUdp"; start() }
    }

    private fun publishDiscoveredRooms() {
        val cutoff = System.currentTimeMillis() - 4500L
        discoveredMap.entries.removeIf { it.value.lastSeenMs < cutoff }
        _lobbyState.value = _lobbyState.value.copy(
            discoveredRooms = discoveredMap.values.sortedByDescending { it.lastSeenMs }
        )
    }

    private fun registerNsdService(hostName: String, roomCode: String) {
        val ctx = context ?: return
        runCatching {
            val mgr = ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = mgr
            val info = NsdServiceInfo().apply {
                serviceName = "MBA_${roomCode}_$hostName"
                serviceType = NSD_SERVICE_TYPE
                port = TCP_PORT
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            }
            nsdRegListener = listener
            mgr.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    private fun startNsdDiscovery() {
        val ctx = context ?: return
        runCatching {
            val mgr = ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = mgr
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) = Unit
                override fun onServiceFound(service: NsdServiceInfo) {
                    if (service.serviceType.contains("minibattle")) {
                        mgr.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                val ip = serviceInfo.host?.hostAddress ?: return
                                discoveredMap[ip] = DiscoveredLanRoom(
                                    roomCode = encodeRoomCode(ip),
                                    hostName = serviceInfo.serviceName.removePrefix("MBA_"),
                                    hostIp = ip,
                                    tcpPort = serviceInfo.port,
                                    playerCount = 1,
                                    maxPlayers = MAX_PLAYERS,
                                    mapId = MapId.MILITARY_BASE,
                                    gameMode = GameMode.DEATHMATCH,
                                    lastSeenMs = System.currentTimeMillis()
                                )
                                publishDiscoveredRooms()
                            }
                        })
                    }
                }
                override fun onServiceLost(service: NsdServiceInfo) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            }
            nsdDiscListener = listener
            mgr.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    private fun unregisterNsd() {
        nsdRegListener?.let { listener ->
            runCatching { nsdManager?.unregisterService(listener) }
        }
        nsdRegListener = null
    }

    private fun encodeConfig(cfg: MatchConfig): JSONObject = JSONObject()
        .put("map", cfg.mapId.name)
        .put("mode", cfg.gameMode.name)
        .put("dur", cfg.durationMinutes)
        .put("lim", cfg.scoreLimit)
        .put("wp", cfg.startingWeapon.name)

    private fun decodeConfig(o: JSONObject): MatchConfig = MatchConfig(
        mapId = MapId.entries.firstOrNull { it.name == o.optString("map") } ?: MapId.MILITARY_BASE,
        gameMode = GameMode.entries.firstOrNull { it.name == o.optString("mode") } ?: GameMode.DEATHMATCH,
        durationMinutes = o.optInt("dur", 3),
        scoreLimit = o.optInt("lim", 20),
        botCount = 0,
        startingWeapon = WeaponType.entries.firstOrNull { it.name == o.optString("wp") } ?: WeaponType.ASSAULT_RIFLE,
        isMultiplayer = true
    )

    private fun encodePeers(list: List<ConnectedPeer>): JSONArray {
        val arr = JSONArray()
        for (p in list) {
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("cs", p.callsign)
                    .put("tm", p.teamIndex)
                    .put("host", p.isHost)
                    .put("shirt", p.appearance.shirtHex)
                    .put("visor", p.appearance.visorHex)
            )
        }
        return arr
    }

    private fun decodePeers(arr: JSONArray): List<ConnectedPeer> {
        val out = mutableListOf<ConnectedPeer>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                ConnectedPeer(
                    id = o.getString("id"),
                    callsign = o.getString("cs"),
                    teamIndex = o.getInt("tm"),
                    isHost = o.optBoolean("host", false),
                    appearance = CharacterAppearance(
                        shirtHex = o.optLong("shirt", 0xFF3A523E),
                        visorHex = o.optLong("visor", 0xFF38BDF8)
                    )
                )
            )
        }
        return out
    }

    /**
     * Smoothly interpolates remote players on a LAN client toward the latest host UDP snapshot.
     */
    fun applyClientInterpolation(players: MutableMap<PlayerId, PlayerState>, dt: Float) {
        val alpha = (dt * 18f).coerceIn(0.1f, 1f)
        for ((id, target) in clientInterpTargets) {
            val p = players[id] ?: continue
            if (id == localPlayerId) {
                // Blend local player gently if host correction diverges > 0.6m
                if (abs(p.x - target.x) > 0.65f) p.x += (target.x - p.x) * alpha
                if (abs(p.y - target.y) > 0.65f) p.y += (target.y - p.y) * alpha
                p.health = target.health
                p.isAlive = target.isAlive
                p.kills = target.kills
                p.deaths = target.deaths
            } else {
                p.x += (target.x - p.x) * alpha
                p.y += (target.y - p.y) * alpha
                p.vx = target.vx
                p.vy = target.vy
                p.aimAngleRad = target.aimAngleRad
                p.facingDir = target.facingDir
                p.health = target.health
                p.isAlive = target.isAlive
                p.isCrouching = target.isCrouching
                p.isJetpacking = target.isJetpacking
                p.currentWeaponIndex = target.weaponOrdinal.coerceIn(0, p.weapons.lastIndex)
                p.kills = target.kills
                p.deaths = target.deaths
                p.muzzleFlashTimer = if (target.muzzleFlash) 0.06f else 0f
            }
        }
    }

    companion object {
        const val TCP_PORT = 41235
        const val UDP_HOST_PORT = 41236
        const val UDP_CLIENT_PORT = 41237
        const val DISCOVERY_UDP_PORT = 41234
        const val MAX_PLAYERS = 12
        const val NSD_SERVICE_TYPE = "_minibattle._tcp."
    }
}
