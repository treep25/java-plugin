package plugin.java;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.logging.Level;

public class AutoTreeChopperPlugin extends JavaPlugin implements Listener {

    //prefer to use a little types of log
    private final Set<Material> LOGS_TYPES = new HashSet<>(List.of(
            Material.ACACIA_LOG,
            Material.BIRCH_LOG,
            Material.CHERRY_LOG,
            Material.DARK_OAK_LOG,
            Material.JUNGLE_LOG,
            Material.MANGROVE_LOG,
            Material.OAK_LOG,
            Material.SPRUCE_LOG,
            Material.STRIPPED_ACACIA_LOG,
            Material.STRIPPED_BIRCH_LOG
    ));

    //three types of material to lunch machine
    private final Set<Material> MACHINE_BLOCKS = new HashSet<>(List.of(
            Material.DIAMOND_BLOCK,
            Material.EMERALD_BLOCK,
            Material.GOLD_BLOCK
    ));

    private Chest activeChest = null;
    private ArmorStand choppingMachine = null;

    @Override
    public void onEnable() {
        getLogger().info("AutoTreeChopper plugin has been enabled and can be used");
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        getLogger().info("AutoTreeChopper plugin has been disabled and cannot be used");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (LOGS_TYPES.contains(block.getType())) {
            //verify block under the chest
            Material bitMaterial = getBitMaterial(block.getLocation().add(0, -1, 0).getBlock());

            chopTree(block, player, bitMaterial);
        }
    }

    @EventHandler
    public void onChestInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null &&
                event.getClickedBlock().getType().equals(Material.CHEST)) {

            Chest chest = (Chest) event.getClickedBlock().getState();

            Material blockUnderType = chest.getLocation().add(0, -1, 0)
                    .getBlock()
                    .getType();

            if (MACHINE_BLOCKS.contains(blockUnderType)) {
                if (!chest.hasMetadata("AutoTreeChopper")) {
                    //activate machine if there is metadata
                    activateMachine(chest);
                    event.getPlayer().sendMessage("Machine activated!");

                } else {
                    event.getPlayer().sendMessage("Machine is already active");
                }
            } else {
                event.getPlayer().sendMessage("Invalid block under the chest. Place a diamond, emerald, or gold block under the chest.");
            }
        }
    }

    private Material getBitMaterial(Block blockUnder) {
        //verify block with machine blocks set
        if (MACHINE_BLOCKS.contains(blockUnder.getType())) {
            return blockUnder.getType();
        } else {
            return Material.DIAMOND_BLOCK;
        }
    }

    private void chopTree(Block block, Player player, Material bitMaterial) {
        if (activeChest == null || block.hasMetadata("AutoTreeChopper")) {
            return;
        }

        //add special logic for some types of block
        if (isSpecialBlock(block.getType())) {
            handleSpecialBlock(block);
            return;
        }

        ItemStack wood = new ItemStack(block.getType(), block.getType().getMaxStackSize());
        Map<Integer, ItemStack> remaining;

        // add synchronized block to enable multi crating of machines
        synchronized (activeChest.getBlockInventory()) {
            remaining = activeChest.getBlockInventory().addItem(wood);
        }

        //set metadata to understand where who
        block.setMetadata("AutoTreeChopper", new FixedMetadataValue(this, true));
        block.breakNaturally(new ItemStack(Material.AIR));

        if (remaining.isEmpty() &&
                activeChest.getBlockInventory().firstEmpty() == -1) {

            getLogger().log(Level.INFO, "Chopping machine stopped due to a full chest.");
            deactivateMachine();
        }

        // runnable task creating
        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                for (Block relative : getAdjacentLogs(block)) {
                    chopTree(relative, player, bitMaterial);
                }
                collectDroppedItems(block.getLocation());
            }
        };
        // chopping delay for different blocks
        int choppingDelay = getChoppingDelay(bitMaterial);

        runnable.runTaskLater(this, choppingDelay);
    }

    private boolean isSpecialBlock(Material material) {
        // Define the set of special blocks
        Set<Material> specialBlocks = new HashSet<>(List.of(
                Material.FERN,
                Material.MUSHROOM_STEM,
                Material.DANDELION,
                Material.POPPY,
                Material.BLUE_ORCHID,
                Material.WATER,
                Material.SPONGE,
                Material.ICE
        ));

        return specialBlocks.contains(material);
    }

    private void handleSpecialBlock(Block block) {
        Material blockType = block.getType();
        // handling specific blocks and processing it
        switch (blockType) {
            case FERN, MUSHROOM_STEM, DANDELION, POPPY, BLUE_ORCHID -> {
                getLogger().log(Level.INFO, "Chopping machine stopped due to contact with a special block: " + blockType);
                deactivateMachine();
            }
            case WATER -> {
                floatMachineInWater();
            }
            case SPONGE, ICE -> {
                handleSpecialBehavior(blockType);
            }
        }
    }

    private void floatMachineInWater() {
        getLogger().log(Level.INFO, "Chopping machine continues chopping in water.");
    }

    private void handleSpecialBehavior(Material specialBlock) {
        switch (specialBlock) {
            case SPONGE -> {
                getLogger().log(Level.INFO, "Encountered Sponge");
                applySpongeEffect();
            }
            case ICE -> {
                getLogger().log(Level.INFO, "Encountered Ice");
                applyIceEffect();
            }
        }
    }

    private void applySpongeEffect() {
        playParticleEffects(choppingMachine.getLocation());
    }

    private void applyIceEffect() {
        Location location = choppingMachine.getLocation();
        World world = location.getWorld();
        int radius = 3; // Adjust the radius as needed

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -radius; y <= radius; y++) {
                    Block block = Objects.requireNonNull(world).getBlockAt(location.getBlockX() + x, location.getBlockY() + y, location.getBlockZ() + z);
                    if (block.getType() == Material.WATER) {
                        block.setType(Material.ICE);
                    }
                }
            }
        }
    }

    private void collectDroppedItems(Location location) {
        if (activeChest == null) {
            return;
        }
        // when coliseum appears
        World world = location.getWorld();
        List<Entity> nearbyEntities = Objects.requireNonNull(world)
                .getEntitiesByClasses(Item.class)
                .stream()
                .toList();

        for (Entity entity : nearbyEntities) {
            if (entity.getLocation().distanceSquared(location) < 2 && !entity.isDead()) {
                ItemStack itemStack = ((Item) entity).getItemStack();
                Map<Integer, ItemStack> remaining;

                synchronized (activeChest.getBlockInventory()) {
                    remaining = activeChest.getBlockInventory().addItem(itemStack);
                }

                if (remaining.isEmpty()) {
                    entity.remove();
                }
            }
        }
    }

    //chopping delay when diamond, gold ...
    private int getChoppingDelay(Material bitMaterial) {
        switch (bitMaterial) {
            case DIAMOND_BLOCK -> {
                return 0;
            }
            case EMERALD_BLOCK -> {
                getLogger().info("Chopping machine with Emerald block encountered. Applying unique bonus feature");
                playParticleEffects(choppingMachine.getLocation());
                return 20;
            }
            case GOLD_BLOCK -> {
                return 40;
            }
            default -> {
                return 20;
            }
        }
    }

    //play particle effect
    private void playParticleEffects(Location location) {
        Objects.requireNonNull(location.getWorld()).spawnParticle(Particle.HEART, location, 50, 0.5, 0.5, 0.5, 0.1);
    }

    private void activateMachine(Chest chest) {
        activeChest = chest;
        //chopping machine creating
        choppingMachine = createChoppingMachine(chest.getLocation());

        int taskId = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (chest.getBlockInventory().firstEmpty() == -1) {
                deactivateMachine();
                return;
            }

            Location targetLocation = findTreeLocation(chest.getLocation(), 40);
            if (targetLocation != null) {
                moveMachine(targetLocation);
                chopTree(targetLocation.getBlock(), null, getBitMaterial(targetLocation.getBlock()));
            } else {
                deactivateMachine();
            }
        }, 0L, 20L).getTaskId();

        chest.setMetadata("AutoTreeChopperTask", new FixedMetadataValue(this, taskId));
    }

    private ArmorStand createChoppingMachine(Location location) {
        World world = location.getWorld();
        ArmorStand armorStand = Objects.requireNonNull(world).spawn(location.clone().add(0.5, 1, 0.5), ArmorStand.class);
        armorStand.setGravity(false);
        armorStand.setInvulnerable(true);
        armorStand.setVisible(false);
        return armorStand;
    }

    private void deactivateMachine() {
        activeChest = null;
        if (choppingMachine != null) {
            choppingMachine.remove();
            choppingMachine = null;
        }
        getLogger().log(Level.INFO, "Chopping machine stopped due to a full chest.");
    }

    //radius prefer to use 40 - can be changed
    private Location findTreeLocation(Location startLocation, int radius) {
        World world = startLocation.getWorld();
        int startX = startLocation.getBlockX();
        int startY = startLocation.getBlockY();
        int startZ = startLocation.getBlockZ();

        for (int x = startX - radius; x <= startX + radius; x++) {
            for (int z = startZ - radius; z <= startZ + radius; z++) {
                for (int y = startY - radius; y <= startY + radius; y++) {
                    Block block = Objects.requireNonNull(world).getBlockAt(x, y, z);
                    if (LOGS_TYPES.contains(block.getType())) {
                        return block.getLocation();
                    }
                }
            }
        }

        return null;
    }

    // move invisible machine to chop
    private void moveMachine(Location targetLocation) {
        if (choppingMachine != null) {
            Location machineLocation = choppingMachine.getLocation();
            machineLocation.setYaw(getYawTowards(targetLocation, machineLocation));
            choppingMachine.teleport(machineLocation.add(machineLocation.getDirection()));
        }
    }

    //angle
    private float getYawTowards(Location target, Location source) {
        double dx = target.getX() - source.getX();
        double dz = target.getZ() - source.getZ();
        double angle = Math.atan2(dz, dx);
        return (float) Math.toDegrees(angle) - 90;
    }

    private List<Block> getAdjacentLogs(Block block) {
        List<Block> logs = new ArrayList<>();
        for (Block relative : getAdjacentBlocks(block)) {
            if (LOGS_TYPES.contains(relative.getType())) {
                logs.add(relative);
            }
        }
        return logs;
    }

    private List<Block> getAdjacentBlocks(Block block) {
        List<Block> adjacentBlocks = new ArrayList<>();
        for (BlockFace face : BlockFace.values()) {
            Block relative = block.getRelative(face);
            adjacentBlocks.add(relative);
        }
        return adjacentBlocks;
    }
}