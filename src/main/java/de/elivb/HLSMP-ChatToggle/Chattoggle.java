package de.elivb.donutChatToggle;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class Chattoggle extends JavaPlugin implements Listener {
   private Set<UUID> playersWithChatDisabled;
   private boolean globalChatEnabled = true;
   private FileConfiguration langConfig;
   private File langFile;
   private LicenseManager licenseManager;
   private DatabaseManager databaseManager;

   @Override
   public void onEnable() {
      this.licenseManager = new LicenseManager(this);
      this.databaseManager = new DatabaseManager(this);
      this.databaseManager.connect();
      this.globalChatEnabled = true;
      this.databaseManager.loadGlobalChatStatus();
      this.playersWithChatDisabled = this.databaseManager.loadPlayerChatSettings();
      this.getServer().getPluginManager().registerEvents(this, this);
      this.setupLangFile();
      this.getCommand("chattoggle").setExecutor(this);
      this.getCommand("chatglobaltoggle").setExecutor(this);
      this.databaseManager.updateAllPlayerNames();

      getLogger().info("HLSMP-ChatToggle đã khởi động!");
   }

   public void setGlobalChatEnabled(boolean enabled) {
      this.globalChatEnabled = enabled;
   }

   public LicenseManager getLicenseManager() {
      return this.licenseManager;
   }

   private void setupLangFile() {
      this.langFile = new File(this.getDataFolder(), "lang.yml");
      if (!this.langFile.exists()) {
         this.langFile.getParentFile().mkdirs();
         this.saveResource("lang.yml", false);
      }
      this.langConfig = YamlConfiguration.loadConfiguration(this.langFile);
   }

   public void reloadLangConfig() {
      this.langConfig = YamlConfiguration.loadConfiguration(this.langFile);
   }

   public String getMessage(String path) {
      String message = this.langConfig.getString(path);
      return message == null ? "Không tìm thấy tin nhắn: " + path : Hex.translateAllColorCodes(message);
   }

   @Override
   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      String cmdName = command.getName().toLowerCase();

      // Hỗ trợ cả /chattoggle và /togglechat (alias cho DonutSettings)
      if (cmdName.equals("chatglobaltoggle")) {
         if (!sender.hasPermission("chattoggle.global")) {
            sender.sendMessage(this.getMessage("no-permission"));
            return true;
         }
         this.toggleGlobalChat(sender);
         return true;
      }

      if (cmdName.equals("chattoggle") || cmdName.equals("togglechat")) {
         if (!(sender instanceof Player)) {
            sender.sendMessage(this.getMessage("only-players"));
            return true;
         }

         if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("chattoggle.reload")) {
               sender.sendMessage(this.getMessage("no-permission"));
               return true;
            }
            this.reloadLangConfig();
            this.getLogger().info("Đang tải lại cấu hình...");
            this.getLogger().info("╠ Đã tải lại lang.yml!");
            this.getLogger().info("╚ Tải lại thành công!");
            sender.sendMessage(this.getMessage("plugin-reload"));
            return true;
         }

         this.togglePlayerChat((Player) sender);
         return true;
      }

      return false;
   }

   private void togglePlayerChat(Player player) {
      UUID playerId = player.getUniqueId();
      if (this.playersWithChatDisabled.contains(playerId)) {
         // Đang tắt → bật lại
         this.playersWithChatDisabled.remove(playerId);
         this.databaseManager.savePlayerChatSetting(playerId, player.getName(), false);
         player.sendMessage(this.getMessage("chat-enabled")); // chat vừa được BẬT
      } else {
         // Đang bật → tắt
         this.playersWithChatDisabled.add(playerId);
         this.databaseManager.savePlayerChatSetting(playerId, player.getName(), true);
         player.sendMessage(this.getMessage("chat-disabled")); // chat vừa bị TẮT
      }
   }

   private void toggleGlobalChat(CommandSender sender) {
      this.globalChatEnabled = !this.globalChatEnabled;
      this.databaseManager.saveGlobalChatStatus(this.globalChatEnabled);
      String msgKey = this.globalChatEnabled ? "global-chat-enabled" : "global-chat-disabled";

      sender.sendMessage(this.getMessage(msgKey));
      Bukkit.getGlobalRegionScheduler().run(this, (task) -> {
         for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.equals(sender)) {
               player.sendMessage(this.getMessage(msgKey));
            }
         }
      });
   }

   @EventHandler
   public void onPlayerChat(AsyncPlayerChatEvent event) {
      Player sender = event.getPlayer();
      if (!this.globalChatEnabled && !sender.hasPermission("chattoggle.bypass")) {
         sender.sendMessage(this.getMessage("chat-is-off"));
         event.setCancelled(true);
         return;
      }
      for (Player recipient : new HashSet<>(event.getRecipients())) {
         if (this.playersWithChatDisabled.contains(recipient.getUniqueId())) {
            event.getRecipients().remove(recipient);
         }
      }
   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      this.databaseManager.updatePlayerName(player.getUniqueId(), player.getName());
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      // reserved
   }

   @Override
   public void onDisable() {
      this.databaseManager.saveGlobalChatStatus(this.globalChatEnabled);
      for (Player player : Bukkit.getOnlinePlayers()) {
         this.databaseManager.savePlayerChatSetting(
            player.getUniqueId(),
            player.getName(),
            this.playersWithChatDisabled.contains(player.getUniqueId())
         );
      }
      if (this.databaseManager != null) {
         this.databaseManager.disconnect();
      }
      getLogger().info("HLSMP-ChatToggle đã tắt!");
   }
}
