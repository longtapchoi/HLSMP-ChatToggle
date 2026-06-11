package de.elivb.donutChatToggle;

import java.io.File;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class DatabaseManager {
   private final JavaPlugin plugin;
   private Connection connection;
   private final String databasePath;

   public DatabaseManager(JavaPlugin plugin) {
      this.plugin = plugin;
      this.databasePath = plugin.getDataFolder() + File.separator + "data.db";
   }

   public void connect() {
      try {
         // Tạo folder data nếu chưa tồn tại
         File dataFolder = plugin.getDataFolder();
         if (!dataFolder.exists()) dataFolder.mkdirs();

         Class.forName("org.sqlite.JDBC");
         this.connection = DriverManager.getConnection("jdbc:sqlite:" + this.databasePath);
         this.createTables();
      } catch (SQLException | ClassNotFoundException e) {
         plugin.getLogger().severe("Không thể kết nối database: " + e.getMessage());
      }
   }

   private void createTables() throws SQLException {
      try (Statement stmt = this.connection.createStatement()) {
         stmt.execute("CREATE TABLE IF NOT EXISTS global_settings (id INTEGER PRIMARY KEY CHECK (id = 1), global_chat_enabled BOOLEAN NOT NULL)");
         stmt.execute("CREATE TABLE IF NOT EXISTS player_settings (uuid VARCHAR(36) PRIMARY KEY, player_name VARCHAR(16) NOT NULL, chat_disabled BOOLEAN NOT NULL)");
      }
   }

   public void loadGlobalChatStatus() {
      try (PreparedStatement pstmt = this.connection.prepareStatement("SELECT global_chat_enabled FROM global_settings WHERE id = 1")) {
         ResultSet rs = pstmt.executeQuery();
         if (rs.next()) {
            ((Chattoggle) this.plugin).setGlobalChatEnabled(rs.getBoolean("global_chat_enabled"));
         } else {
            try (PreparedStatement insertStmt = this.connection.prepareStatement("INSERT INTO global_settings (id, global_chat_enabled) VALUES (1, ?)")) {
               insertStmt.setBoolean(1, true);
               insertStmt.executeUpdate();
            }
         }
      } catch (SQLException e) {
         plugin.getLogger().severe("Lỗi load trạng thái chat toàn server: " + e.getMessage());
      }
   }

   public void saveGlobalChatStatus(boolean enabled) {
      try (PreparedStatement pstmt = this.connection.prepareStatement("UPDATE global_settings SET global_chat_enabled = ? WHERE id = 1")) {
         pstmt.setBoolean(1, enabled);
         pstmt.executeUpdate();
      } catch (SQLException e) {
         plugin.getLogger().severe("Lỗi lưu trạng thái chat toàn server: " + e.getMessage());
      }
   }

   public Set<UUID> loadPlayerChatSettings() {
      Set<UUID> playersWithChatDisabled = new HashSet<>();
      try (Statement stmt = this.connection.createStatement();
           ResultSet rs = stmt.executeQuery("SELECT uuid FROM player_settings WHERE chat_disabled = 1")) {
         while (rs.next()) {
            playersWithChatDisabled.add(UUID.fromString(rs.getString("uuid")));
         }
      } catch (SQLException e) {
         plugin.getLogger().severe("Lỗi load cài đặt chat người chơi: " + e.getMessage());
      }
      return playersWithChatDisabled;
   }

   public void savePlayerChatSetting(UUID uuid, String playerName, boolean disabled) {
      try (PreparedStatement pstmt = this.connection.prepareStatement(
            "INSERT OR REPLACE INTO player_settings (uuid, player_name, chat_disabled) VALUES (?, ?, ?)")) {
         pstmt.setString(1, uuid.toString());
         pstmt.setString(2, playerName);
         pstmt.setBoolean(3, disabled);
         pstmt.executeUpdate();
      } catch (SQLException e) {
         plugin.getLogger().severe("Lỗi lưu cài đặt chat: " + e.getMessage());
      }
   }

   public void removePlayerChatSetting(UUID uuid) {
      try (PreparedStatement pstmt = this.connection.prepareStatement("DELETE FROM player_settings WHERE uuid = ?")) {
         pstmt.setString(1, uuid.toString());
         pstmt.executeUpdate();
      } catch (SQLException e) {
         plugin.getLogger().severe("Lỗi xóa cài đặt chat: " + e.getMessage());
      }
   }

   public void updateAllPlayerNames() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         this.updatePlayerName(player.getUniqueId(), player.getName());
      }
   }

   public void updatePlayerName(UUID uuid, String playerName) {
      try (PreparedStatement pstmt = this.connection.prepareStatement("UPDATE player_settings SET player_name = ? WHERE uuid = ?")) {
         pstmt.setString(1, playerName);
         pstmt.setString(2, uuid.toString());
         pstmt.executeUpdate();
      } catch (SQLException e) {
         plugin.getLogger().severe("Lỗi cập nhật tên người chơi: " + e.getMessage());
      }
   }

   public void disconnect() {
      try {
         if (this.connection != null && !this.connection.isClosed()) {
            this.connection.close();
         }
      } catch (SQLException e) {
         plugin.getLogger().severe("Lỗi đóng database: " + e.getMessage());
      }
   }
}
