package org.sethomegui.Listeners;

import dev.dejvokep.boostedyaml.block.implementation.Section;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.sethomegui.SetHomeGUI;
import org.sethomegui.Utils.MenuActions;
import org.sethomegui.Utils.Utils;
import org.sethomegui.Managers.*;

import java.io.File;
import java.util.List;
import java.util.UUID;

public class AdminMenuClickListener implements Listener {

    private final SetHomeGUI plugin;

    public AdminMenuClickListener(SetHomeGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAdminInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player admin = (Player) event.getWhoClicked();
        UUID uuid = admin.getUniqueId();

        InventoryHolder holder = event.getInventory().getHolder();

        // Comprobación de seguridad nativa por Holders
        if (!(holder instanceof AdminMainHolder) && !(holder instanceof AdminHomesHolder) && !(holder instanceof AdminConfirmHolder)) {
            return;
        }

        event.setCancelled(true); // Cancela el movimiento de los ítems del panel

        // Solo procesamos clicks dentro del panel: el inventario del jugador reutiliza la misma numeración de slots
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        ItemStack clicked = event.getCurrentItem();
        int slot = event.getSlot();
        if (clicked == null || clicked.getType() == Material.AIR || !clicked.hasItemMeta()) return;

        // Los comandos personalizados de gui.yml sustituyen a la acción interna del ítem
        String menuPath = holder instanceof AdminMainHolder ? "gui.admin-gui"
                : holder instanceof AdminHomesHolder ? "gui.admin-gui.admin-player-homes-gui"
                : "gui.admin-gui.admin-confirmation-gui";

        if (MenuActions.execute(plugin, admin, plugin.getGuisConfig().getSection(menuPath), slot)) {
            admin.playSound(admin.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        AdminGUIManager manager = plugin.getAdminGUIManager();
        String action = clicked.getItemMeta().getPersistentDataContainer().get(manager.actionKey, PersistentDataType.STRING);
        if (action == null) action = "";

        int currentPage = manager.getPage(uuid);
        FileConfiguration mainConfig = plugin.getConfig();

        // =========================================================================
        // PANEL DE CONTROL PRINCIPAL: LISTADO DE JUGADORES
        // =========================================================================
        if (holder instanceof AdminMainHolder) {
            switch (action) {
                case "search":
                    admin.playSound(admin.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    // No cerramos inventario aquí, el propio openSign del cliente se encargará de gestionarlo
                    manager.openSearchChat(admin);
                    return;

                case "prev_page":
                    if (currentPage > 1) {
                        admin.playSound(admin.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                        manager.setPage(uuid, currentPage - 1);
                        manager.openAdminMenu(admin);
                    }
                    return;

                case "next_page":
                    admin.playSound(admin.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    manager.setPage(uuid, currentPage + 1);
                    manager.openAdminMenu(admin);
                    return;
            }

            // Identificamos la cabeza por su acción inyectada en el PDC y, como respaldo,
            // por las ranuras declaradas en el YAML (nunca por posiciones fijas en el código)
            Section mainSection = plugin.getGuisConfig().getSection("gui.admin-gui");
            boolean isPlayerHead = action.equals("open_player")
                    || (mainSection != null && mainSection.getIntList("slots").contains(slot));

            if (isPlayerHead) {
                String uuidStr = null;

                if (clicked.getItemMeta().hasLore()) {
                    for (String line : clicked.getItemMeta().getLore()) {
                        String stripped = ChatColor.stripColor(line);
                        if (stripped.contains("h_target:")) {
                            uuidStr = stripped.split("h_target:")[1].trim();
                            break;
                        }
                    }
                }
                if (uuidStr == null) {
                    uuidStr = clicked.getItemMeta().getPersistentDataContainer().get(manager.targetUuidKey, PersistentDataType.STRING);
                }

                if (uuidStr != null) {
                    admin.playSound(admin.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    manager.setPage(uuid, 1); // Reseteamos paginación interna para los hogares
                    manager.openAdminPlayerHomesMenu(admin, UUID.fromString(uuidStr), 1);
                }
            }
            return;
        }

        // =========================================================================
        // SUB PANEL: REPOSITORIO DE HOGARES DEL JUGADOR AUDITADO
        // =========================================================================
        if (holder instanceof AdminHomesHolder) {
            UUID targetUuid = ((AdminHomesHolder) holder).getAuditedPlayerUuid();

            switch (action) {
                case "go_back":
                    admin.playSound(admin.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    manager.setPage(uuid, 1);
                    manager.openAdminMenu(admin);
                    break;

                case "homes_prev":
                    if (currentPage > 1) {
                        admin.playSound(admin.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                        manager.openAdminPlayerHomesMenu(admin, targetUuid, currentPage - 1);
                    }
                    break;

                case "homes_next":
                    admin.playSound(admin.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    manager.openAdminPlayerHomesMenu(admin, targetUuid, currentPage + 1);
                    break;

                case "interact_home":
                    String homeName = clicked.getItemMeta().getPersistentDataContainer().get(manager.targetHomeKey, PersistentDataType.STRING);
                    if (homeName == null) return;

                    // CLIC IZQUIERDO: TELETRANSPORTE DIRECTO (Aquí cerramos inventario voluntariamente)
                    if (event.getClick().isLeftClick()) {
                        admin.playSound(admin.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                        admin.closeInventory();

                        try {
                            // Leemos a traves del backend activo (YAML o MySQL), no del disco
                            dev.dejvokep.boostedyaml.YamlDocument targetData = plugin.getHomeManager().getPlayerFile(targetUuid);
                            if (targetData == null) return;
                            Section hData = targetData.getSection(homeName);
                            if (hData != null) {
                                org.bukkit.World world = Bukkit.getWorld(hData.getString("world", "world"));
                                double x = hData.getDouble("x");
                                double y = hData.getDouble("y");
                                double z = hData.getDouble("z");
                                float yaw = hData.getFloat("yaw", 0.0f);
                                float pitch = hData.getFloat("pitch", 0.0f);

                                if (world != null) {
                                    org.bukkit.Location targetLoc = new org.bukkit.Location(world, x, y, z, yaw, pitch);
                                    admin.getScheduler().run(plugin, (task) -> admin.teleport(targetLoc), null);

                                    String msg = mainConfig.getString("messages.admin.teleport-success", "&a[Admin] Teleported to %home%!");
                                    admin.sendMessage(Utils.color(msg.replace("%home%", homeName)));
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                    // CLIC DERECHO: FLUJO DE CONFIRMACIÓN DE BORRADO
                    else if (event.getClick().isRightClick()) {
                        admin.playSound(admin.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                        manager.openAdminConfirmationMenu(admin, targetUuid, homeName);
                    }
                    break;
            }
            return;
        }

        // =========================================================================
        // SUB PANEL: CONFIRMACIÓN ADMINISTRATIVA DE BORRADO
        // =========================================================================
        if (holder instanceof AdminConfirmHolder) {
            AdminConfirmHolder confirmHolder = (AdminConfirmHolder) holder;
            UUID targetUuid = confirmHolder.getTargetUuid();
            String homeName = confirmHolder.getHomeName();

            if (action.equals("confirm_wipe")) {
                admin.playSound(admin.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                try {
                    // El borrado viaja al backend activo (YAML o MySQL) al llamar a save()
                    dev.dejvokep.boostedyaml.YamlDocument targetData = plugin.getHomeManager().getPlayerFile(targetUuid);
                    if (targetData == null) return;
                    targetData.set(homeName, null);

                    List<String> homeNames = targetData.getStringList("homes");
                    if (homeNames != null) {
                        homeNames.remove(homeName);
                        targetData.set("homes", homeNames);
                    }
                    targetData.save();

                    String msg = mainConfig.getString("messages.admin.delete-success", "&a[Admin] Home %home% deleted.");
                    admin.sendMessage(Utils.color(msg.replace("%home%", homeName)));
                } catch (Exception ignored) {}

                manager.openAdminPlayerHomesMenu(admin, targetUuid, 1);
            }
            else if (action.equals("cancel_wipe")) {
                admin.playSound(admin.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                manager.openAdminPlayerHomesMenu(admin, targetUuid, 1);
            }
        }
    }

    @EventHandler
    public void onAdminInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player admin = (Player) event.getPlayer();

        // Comprobamos si el inventario que se acaba de cerrar es nuestro menú principal
        if (event.getInventory().getHolder() instanceof AdminMainHolder) {

            // Verificamos que el jugador NO esté entrando al modo chat de búsqueda.
            // Si tiene los metadatos del chat, significa que el inventario se cerró a la fuerza
            // para que escriba, por lo que NO debemos resetear el filtro en este caso.
            if (admin.hasMetadata("admin_search_mode")) {
                return;
            }

            // Si el jugador simplemente cerró el menú para volver a jugar, reseteamos su filtro
            AdminGUIManager manager = plugin.getAdminGUIManager();
            manager.setSearchFilter(admin.getUniqueId(), "");
        }
    }
}