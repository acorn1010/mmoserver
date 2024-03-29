package org.unallied.mmoserver.database;

import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.rowset.serial.SerialBlob;

import org.unallied.mmocraft.BoundLocation;
import org.unallied.mmocraft.tools.Authenticator;
import org.unallied.mmocraft.tools.PrintError;
import org.unallied.mmocraft.tools.input.ByteArrayByteStream;
import org.unallied.mmocraft.tools.input.GenericSeekableLittleEndianAccessor;
import org.unallied.mmoserver.client.Client;
import org.unallied.mmoserver.constants.DatabaseConstants;
import org.unallied.mmoserver.server.ServerPlayer;



public class MySQLDatabase implements DatabaseAccessor {
	
    private static ThreadLocal<Connection> con = new ThreadLocalConnection();

    public static Connection getConnection() {
        return con.get();
    }

    public static void release() throws SQLException {
        con.get().close();
        con.remove();
    }

    private static class ThreadLocalConnection extends ThreadLocal<Connection> {
        static {
            try {
                Class.forName("com.mysql.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                System.out.println("Could not locate the JDBC mysql driver.");
            }
        }

        @Override
        protected Connection initialValue() {
            return getConnection();
        }

        private Connection getConnection() {
            try {
                return DriverManager.getConnection(DatabaseConstants.DB_URL, 
                        DatabaseConstants.DB_USER, DatabaseConstants.DB_PASS);
            } catch (SQLException sql) {
                System.out.println("Could not create a SQL Connection object. Please make sure you've correctly configured the database properties inside of server_conf.properties.");
                sql.printStackTrace();
                return null;
            }
        }

        @Override
        public Connection get() {
            Connection con = super.get();
            try {
                if (!con.isClosed()) {
                    return con;
                }
            } catch (SQLException sql) {
                // Obtain a new connection
            }
            System.out.println("Getting connection");
            con = getConnection();
            super.set(con);
            return con;
        }
    }

    public MySQLDatabase() {
    	
    }
    
    /**
     * Attempts to populate the player's data in the client provided.
     * @param client the client to be associated with the username
     * @param username the username to grab from the database
     * @return true on success; false on failure
     */
    public boolean getPlayer(Client client, String username) {
        boolean result = true;
        try {
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT * " +
                    "FROM account " +
                    "WHERE LOWER(account_user)=LOWER(?)");
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            
            // Only set the password if there's a password to set
            if( rs != null && rs.next() ) {
                int accountId = rs.getInt("account_id");
                String password = rs.getString("account_pass");
                String playerName = rs.getString("player_name");
                Blob playerData = rs.getBlob("player_data");
                rs.close();
                
                if (password == null) {
                    return false;
                }
                
                ServerPlayer player = null;
                if (playerData != null) {
                    ByteArrayByteStream babs = new ByteArrayByteStream(
                            playerData.getBytes(1,  (int)playerData.length()));
                    
                    player = ServerPlayer.fromBytes(
                            new GenericSeekableLittleEndianAccessor(babs));
                } else {
                    // New player.  Set to defaults.
                    player = new ServerPlayer();
                    player.init();
                    player.setHpCurrent(player.getHpMax());
                    player.setLocation(new BoundLocation(0, 0, 0, 0));
                }
                
                // Set client info
                client.loginSession.setPassword(password);
                client.setAccountId(accountId);
                
                // Set the player's information
                player.setId(accountId);
                player.setName(playerName);
//                player.init();
//                player.setLocation(new BoundLocation(playerPosX, playerPosY, 0, 0));
                
                // Kludge:  Create player on land
                player.accelerateDown(100000, 100f, 100f);
                player.update(100000);
                player.setClientLocation(new BoundLocation(player.getLocation()));
                
                // Assign the player to the client
                client.setPlayer(player);
            } else {
                result = false;
            }
            ps.close();
        } catch(SQLException e) {
            result = false;
        }
        return result;
    }

    /**
     * Saves a player's information in the database.  This should be called
     * periodically for all players to prevent exploits.
     * @param player The player to add to the database
     * @return true on success; false if failed
     */
    public boolean savePlayer(ServerPlayer player) {
        Connection conn = getConnection();
        try {
            int index = 1;
            PreparedStatement ps = conn.prepareStatement(
                    "UPDATE account " +
                    "SET player_name=?, player_data=? " +
                    "WHERE account_id=?");
            ps.setString(index++, player.getName());
            ps.setBlob(index++, new SerialBlob(player.getBytes()));
            ps.setInt(index++, player.getId());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            PrintError.print(PrintError.EXCEPTION_CAUGHT, e);
            return false;
        }
        return true;
    }

    /**
     * Creates a new account.
     * @param user
     * @param pass
     * @param email
     * @return 
     */
    public boolean createAccount(String user, String pass, String email) {
        // Make sure user and email are valid.  Don't check pass because it's a hash right now
        if (Authenticator.isValidUser(user)  && Authenticator.isValidEmail(email)) {
            Connection conn = getConnection();
            try {
                int index = 1;
                // Check to see if this user exists and they need a password change.
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM account WHERE LOWER(account_user)=LOWER(?) AND account_pass is NULL LIMIT 1");
                ps.setString(1, user);
                ResultSet rs = ps.executeQuery();
                if (rs != null && rs.next()) {
                    int accountId = rs.getInt("account_id");
                    ps.close();
                    ps = conn.prepareStatement(
                            "UPDATE account SET account_pass=? WHERE account_id=?");
                    ps.setString(1, pass);
                    ps.setInt(2, accountId);
                    ps.executeUpdate();
                } else {
                    ps = conn.prepareStatement(
                            "INSERT INTO account(account_user,player_name,account_pass,account_email) VALUES(LOWER(?),?,?,?)");
                    ps.setString(index++, user);
                    ps.setString(index++, user);
                    ps.setString(index++, pass);
                    ps.setString(index++, email);
                    ps.executeUpdate();
                }
                ps.close();
                return true;
            } catch (SQLException e) {
                PrintError.print(PrintError.EXCEPTION_CAUGHT, e);
            }
        }
        return false;
    }

	@Override
	public void globalLogout() {
        Connection conn = getConnection();
        PreparedStatement ps;
		try {
			ps = conn.prepareStatement("UPDATE account SET account_loggedin = 0");
	        ps.executeUpdate();
	        ps.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
