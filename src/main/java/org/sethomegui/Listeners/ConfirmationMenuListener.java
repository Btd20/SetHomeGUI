package org.sethomegui.Listeners;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.sethomegui.SetHomeGUI;
import org.sethomegui.Utils.MenuActions;
import org.sethomegui.Utils.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ConfirmationMenuListener implements Listener {

    private final SetHomeGUI plugin;

    public ConfirmationMenuListener(SetHomeGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        UUID uuid = player.getUniqueId();

        String homeName = plugin.getGuiManager().getPendingDeletionHome(uuid);
        if (homeName == null) return;

        YamlDocument guiConfig = plugin.getGuisConfig();
        Section confirmSection = guiConfig.getSection("gui.confirmation-gui");
        if (confirmSection == null) return;

        String expectedTitle = confirmSection.getString("title", "").replace("%home_name%", homeName);
        expectedTitle = Utils.setPlaceholders(player, expectedTitle, plugin);

        if (!event.getView().getTitle().equals(expectedTitle)) return;

        event.setCancelled(true);

        // Solo procesamos clicks dentro del menú: el inventario del jugador reutiliza la misma numeración de slots
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        // Los comandos personalizados de gui.yml sustituyen a la acción interna del ítem
        if (MenuActions.execute(plugin, player, confirmSection, event.getSlot())) {
            plugin.getGuiManager().playConfiguredClickSound(player, "confirmation-gui");
            return;
        }

        // ⚡ La acción se resuelve por el ítem (PDC) y no por su posición fija
        String clickedKey = Utils.resolveMenuAction(plugin, clickedItem, confirmSection, event.getSlot());
        if (clickedKey == null) return;

        if (!clickedKey.equals("confirm_button") && !clickedKey.equals("cancel_button")) return;

        plugin.getGuiManager().playConfiguredClickSound(player, "confirmation-gui");

        // ACCIÓN A: EL JUGADOR CONFIRMA LA ELIMINACIÓN
        if (clickedKey.equals("confirm_button")) {
            player.closeInventory(); // 1. Se cierra el menú por completo
            plugin.getGuiManager().clearPendingDeletion(uuid);

            YamlDocument playerFile = plugin.getHomeManager().getPlayerFile(uuid);
            List<String> rawHomesList = playerFile != null ? playerFile.getStringList("homes") : new ArrayList<>();
            if (rawHomesList == null) rawHomesList = new ArrayList<>();

            if (rawHomesList.contains(homeName)) {
                rawHomesList.remove(homeName);
                playerFile.set("homes", rawHomesList);
                playerFile.remove(homeName);

                try {
                    playerFile.save();

                    // 🚀 ACTUALIZACIÓN DE LA CACHÉ EN CALIENTE
                    // Si el jugador se ha quedado sin hogares, lo removemos del panel de administración instantáneamente
                    if (plugin.getAdminGUIManager() != null) {
                        plugin.getAdminGUIManager().removePlayerFromCacheIfEmpty(player);
                    }

                    String deleteMsg = plugin.getMainConfig().getString("messages.home-action-messages.home-deleted",
                            "&#ef6603[SetHomeGUI] &#f9a805Home &f%name% &#f9a805has been successfully deleted.");
                    player.sendMessage(Utils.setPlaceholders(player, deleteMsg.replace("%name%", homeName), plugin));
                } catch (IOException e) {
                    String deletionErrorMsg = plugin.getMainConfig().getString(
                            "messages.home-action-messages.file-deletion-error",
                            "&#ef6603[SetHomeGUI] &cAn error occurred while deleting the file."
                    );

                    player.sendMessage(Utils.color(deletionErrorMsg));
                    e.printStackTrace();
                }
            }
        }

        // ACCIÓN B: EL JUGADOR CANCELA EL PROCESO
        else {
            player.closeInventory();
            plugin.getGuiManager().clearPendingDeletion(uuid);

            // Si cancela, le vuelve a abrir su lista de hogares de forma fluida
            plugin.getGuiManager().openHomesGUI(player);
        }
    }
}