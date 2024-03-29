package org.unallied.mmoserver.net.handlers;

import org.unallied.mmocraft.tools.Authenticator;
import org.unallied.mmocraft.tools.Hasher;
import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;
import org.unallied.mmoserver.client.Client;
import org.unallied.mmoserver.net.PacketCreator;
import org.unallied.mmoserver.server.Server;



public class RegisterHandler extends AbstractServerPacketHandler {

    @Override
    /**
     * A message containing [user][pass][email]
     * This is sent every time the player changes their direction or animation state.
     */
    public void handlePacket(SeekableLittleEndianAccessor slea, Client client) {
        String user = slea.readPrefixedAsciiString();
        String pass = slea.readPrefixedAsciiString();
        String email = slea.readPrefixedAsciiString();
        
        // Prevent bad usernames / passwords / emails
        if (!(Authenticator.isValidUser(user) && Authenticator.isValidPass(pass) && Authenticator.isValidEmail(email))) {
            client.announce(PacketCreator.getRegisterAcknowledgment(false));
            return;
        }
        
        byte[] byteData = Hasher.getSHA256((new String(user).toLowerCase() + pass).getBytes());
        StringBuffer sb = new StringBuffer();
        for (int i=0; i < byteData.length; ++i) {
            sb.append(Integer.toString((byteData[i] & 0xFF) + 0x100, 16).substring(1));
        }
        pass = sb.toString();
        boolean accepted = Server.getInstance().getDatabase().createAccount(user, pass, email);
        
        // Tell client that we received their registration request, and that they can now log in
        client.announce(PacketCreator.getRegisterAcknowledgment(accepted));
    }
}
