package io.rebble.libpebblecommon.services.blobdb

import io.rebble.libpebblecommon.PacketPriority
import io.rebble.libpebblecommon.ProtocolHandler
import io.rebble.libpebblecommon.packets.blobdb.BlobCommand
import io.rebble.libpebblecommon.packets.blobdb.BlobResponse
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.services.ProtocolService
import kotlinx.coroutines.CompletableDeferred

/**
 * Singleton to handle sending BlobDB commands cleanly, by allowing registered callbacks to be triggered when the sending packet receives a BlobResponse
 * @see BlobResponse
 */
class BlobDBService(private val protocolHandler: ProtocolHandler) : ProtocolService {
    private val pending: MutableMap<UShort, CompletableDeferred<BlobResponse>> = mutableMapOf()

    init {
        protocolHandler.registerReceiveCallback(ProtocolEndpoint.BLOBDB_V1, this::receive)
    }

    /**
     * Send a BlobCommand, with an optional callback to be triggered when a matching BlobResponse is received
     * @see BlobCommand
     * @see BlobResponse
     * @param packet the packet to send
     *
     * @return [BlobResponse] from the watch or *null* if the sending failed
     */
    suspend fun send(
        packet: BlobCommand,
        priority: PacketPriority = PacketPriority.NORMAL
    ): BlobResponse {
        val result = CompletableDeferred<BlobResponse>()
        pending[packet.token.get()] = result

        val sendingResult = protocolHandler.send(packet, priority)
        if (!sendingResult) {
            return BlobResponse(BlobResponse.BlobStatus.WatchDisconnected)
        }

        return result.await()
    }

    /**
     * Intended to be called via a protocol handler, handles BlobResponse packets
     * @return true if the packet was handled, false if it wasn't (e.g. not sent via send())
     * @see send
     * @see BlobResponse
     */
    fun receive(packet: PebblePacket) {
        if (packet !is BlobResponse) {
            throw IllegalStateException("Received invalid packet type: $packet")
        }

        pending.remove(packet.token.get())?.complete(packet)
    }
}