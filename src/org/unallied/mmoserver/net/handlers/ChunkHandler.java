package org.unallied.mmoserver.net.handlers;

import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;
import org.unallied.mmoserver.client.Client;
import org.unallied.mmoserver.net.PacketCreator;


/**
 * Sent from the client to request a chunk.  The server should respond to this
 * request if it's valid by sending an array of all block IDs that make up the
 * chunk.
 * Format:  [Header(2)][chunkId(8)]
 * @author Faythless
 *
 */
public class ChunkHandler extends AbstractServerPacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, Client client) {
        if (slea.available() != 8) { // Guard
            return;
        }
        /**
         *  Grab the chunk ID x and y coords.  We can do this because of how a
         *  chunkId is formatted
         */
        long chunkId = slea.readLong();
        
        // Make sure the client is able to request this chunk
        // FIXME This needs to be implemented to prevent an exploit!
        
        // Send chunk info to the client
        try {
            client.announce(PacketCreator.getChunk(chunkId));
        } catch (NullPointerException e) {
            // Chunk must not exist
        }
    }
}
