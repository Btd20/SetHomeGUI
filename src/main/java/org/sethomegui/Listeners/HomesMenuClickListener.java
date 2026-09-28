package org.sethomegui.Listeners;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.sethomegui.SetHomeGUI;
import org.sethomegui.Utils.MenuActions;
import org.sethomegui.Utils.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HomesMenuClickListener implements Listener {

    private final SetHomeGUI plugin;

    public HomesMenuClickListener(SetHomeGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        UUID uuid = player.getUniqueId();
        String title = event.getView().getTitle();

        // Obtener títulos dinámicos resolviendo variables globales de página
        int currentPage = plugin.getGuiManager().getPlayerPage(uuid);
        YamlDocument guiConfig = plugin.getGuisConfig();
        Section homesSection = guiConfig.getSection("gui.homes-gui");
        if (homesSection == null) return;

        YamlDocument playerFile = plugin.getHomeManager().getPlayerFile(uuid);
        List<String> rawHomesList = playerFile != null ? playerFile.getStringList("homes") : new ArrayList<>();
        if (rawHomesList == null) rawHomesList = new ArrayList<>();

        List<Integer> homeSlots = homesSection.getIntList("home-slots");
        int homesPerPage = homeSlots.size();
        int maxPages = (int) Math.ceil((double) rawHomesList.size() / homesPerPage);
        if (maxPages == 0) maxPages = 1;

        String expectedTitle = homesSection.getString("title", "")
                .replace("%page%", String.valueOf(currentPage))
                .replace("%max_page%", String.valueOf(maxPages));
        expectedTitle = Utils.setPlaceholders(player, expectedTitle, plugin);

        // Verificamos si es el inventario correcto
        if (!title.equals(expectedTitle)) return;

        event.setCancelled(true); // Bloquear inventario

        // Solo procesamos clicks dentro del menú: el inventario del jugador reutiliza la misma numeración de slots
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        int clickedSlot = event.getSlot();

        // Los comandos personalizados de gui.yml sustituyen a la acción interna del ítem
        if (MenuActions.execute(plugin, player, homesSection, clickedSlot)) {
            plugin.getGuiManager().playConfiguredClickSound(player, "homes-gui");
            return;
        }

        // --- ACCIÓN 1: BOTONES FIJOS ---
        // La acción se resuelve por el ítem (PDC) y no por su posición fija, de modo que
        // los botones siguen funcionando si se reubican en gui.yml
        String action = Utils.resolveMenuAction(plugin, clickedItem, homesSection, clickedSlot);

        if (action != null) {
            switch (action) {
                case "back":
                    plugin.getGuiManager().playConfiguredClickSound(player, "homes-gui");
                    player.closeInventory();
                    plugin.getGuiManager().openMainGUI(player);
                    return;

                case "previous_page":
                    plugin.getGuiManager().playConfiguredClickSound(player, "homes-gui");
                    // Solo cambia de página si realmente hay una página anterior a la cual ir
                    if (currentPage > 1) {
                        plugin.getGuiManager().setPlayerPage(uuid, currentPage - 1);
                        plugin.getGuiManager().openHomesGUI(player);
                    }
                    return;

                case "next_page":
                    plugin.getGuiManager().playConfiguredClickSound(player, "homes-gui");
                    // Solo cambia de página si realmente hay hogares en la página siguiente
                    if (currentPage < maxPages) {
                        plugin.getGuiManager().setPlayerPage(uuid, currentPage + 1);
                        plugin.getGuiManager().openHomesGUI(player);
                    }
                    return;

                default:
                    // Decoración u otros ítems sin lógica: seguimos evaluando la rejilla de hogares
                    break;
            }
        }

        // --- ACCIÓN 2: CLIC EN UN HOGAR DINÁMICO ---
        if (homeSlots.contains(clickedSlot)) {
            int slotIndexInPage = homeSlots.indexOf(clickedSlot);
            int globalHomeIndex = ((currentPage - 1) * homesPerPage) + slotIndexInPage;

            // Verificamos que el índice realmente apunte a una casa del jugador
            if (globalHomeIndex >= rawHomesList.size()) return;
            String homeName = rawHomesList.get(globalHomeIndex);

            plugin.getGuiManager().playConfiguredClickSound(player, "homes-gui");

            // CASO A: CLIC IZQUIERDO -> ENCOLA TELETRANSPORTE CON COOLDOWN
            if (event.getClick() == ClickType.LEFT) {
                player.closeInventory();

                String worldName = playerFile.getString(homeName + ".world");
                org.bukkit.World world = Bukkit.getWorld(worldName);

                if (world == null) {
                    // Leemos la plantilla desde la configuración usando el fallback nativo por seguridad
                    String worldNotLoadedMsg = plugin.getMainConfig().getString(
                            "messages.home-action-messages.world-not-loaded",
                            "&#ef6603[SetHomeGUI] &cError: Destination world '%world%' is not loaded."
                    );

                    // Reemplazamos la variable %world% por la variable local worldName y aplicamos colores
                    player.sendMessage(Utils.color(worldNotLoadedMsg.replace("%world%", worldName)));
                    return;
                }

                double x = playerFile.getDouble(homeName + ".x");
                double y = playerFile.getDouble(homeName + ".y");
                double z = playerFile.getDouble(homeName + ".z");
                float yaw = playerFile.getDouble(homeName + ".yaw").floatValue();
                float pitch = playerFile.getDouble(homeName + ".pitch").floatValue();

                Location targetLoc = new Location(world, x, y, z, yaw, pitch);

                // Disparamos la lógica con barra de acción, títulos y mitigación de hilos de Folia
                plugin.getTeleportManager().queueTeleport(player, targetLoc);
            }

            // CASO B: CLIC DERECHO -> ABRIR CONFIRMACIÓN
            else if (event.getClick() == ClickType.RIGHT) {
                plugin.getGuiManager().playConfiguredClickSound(player, "homes-gui");
                // Abrimos el menú de confirmación pasándole la casa seleccionada
                plugin.getGuiManager().openConfirmationGUI(player, homeName);
            }
        }
    }
}