package net.handlers;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import net.PacketCreator;

import org.unallied.mmocraft.tools.Authenticator;
import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;

import server.Server;

import client.Client;

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
        if (Authenticator.isValidPass(pass)) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(user.getBytes());
                md.update(pass.getBytes());
                byte[] byteData = md.digest();
                StringBuffer sb = new StringBuffer();
                for (int i=0; i < byteData.length; ++i) {
                    sb.append(Integer.toString((byteData[i] & 0xFF) + 0x100, 16).substring(1));
                }
                pass = sb.toString();
            } catch (NoSuchAlgorithmException e) { //Uh oh!
                System.out.println(e.getMessage());
            }
        }
        boolean accepted = Server.getInstance().getDatabase().createAccount(user, pass, email);
        
        // Tell client that we received their registration request, and that they can now log in
        client.announce(PacketCreator.getRegisterAcknowledgment(accepted));
    }
}