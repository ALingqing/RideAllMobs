package top.chenray;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RideAllMobs - 主类
 * <p>
 * 允许玩家在空手时右键点击任意生物实体骑乘上去。
 * 手持物品时右键生物保持原版交互逻辑。
 * <p>
 * 功能列表：
 * - 空手右键骑乘任意生物
 * - 黑名单/白名单配置
 * - 已驯服宠物保护
 * - 速度倍率调节
 * - 下坐骑保留位置 + 安全传送
 */
public class RideAllMobs extends JavaPlugin implements Listener {

    // ==================== 运行时数据 ====================

    /** 记录玩家骑乘前的位置 <玩家UUID, 骑乘前坐标> */
    private final Map<UUID, Location> previousLocations = new HashMap<>();

    /** 记录被骑乘生物的原始移动速度 <生物UUID, 原始速度值> */
    private final Map<UUID, Double> originalSpeeds = new HashMap<>();

    // ==================== 配置缓存 ====================

    /** 骑乘模式: WHITELIST 或 BLACKLIST */
    private String mountMode;

    /** 实体类型名列表 (EntityType.name()) */
    private List<String> entityList;

    /** 是否保护其他玩家驯服的宠物 */
    private boolean protectTamedPets;

    /** 速度倍率 */
    private double speedMultiplier;

    /** 下坐骑是否返回骑乘前位置 */
    private boolean returnToPreviousLocation;

    /** 下坐骑是否安全传送 */
    private boolean safeDismount;

    /** 是否允许骑乘其他玩家 */
    private boolean allowPlayerRiding;

    /** 是否允许叠罗汉（被骑乘的玩家可以同时骑乘其他实体） */
    private boolean allowStacking;

    // ==================== 插件生命周期 ====================

    @Override
    public void onEnable() {
        // 保存默认配置文件 (config.yml)
        saveDefaultConfig();
        // 加载配置到缓存
        reloadConfigSettings();

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("RideAllMobs v" + getDescription().getVersion() + " 已加载 - 阿清制作");
        getLogger().info("空手右键任意生物即可骑乘！");
    }

    @Override
    public void onDisable() {
        getLogger().info("RideAllMobs 已卸载");
    }

    // ==================== 配置管理 ====================

    /**
     * 从 config.yml 重新加载所有配置项到缓存
     */
    private void reloadConfigSettings() {
        reloadConfig();
        FileConfiguration config = getConfig();

        mountMode = config.getString("mount-mode", "BLACKLIST");
        entityList = config.getStringList("entity-list");
        protectTamedPets = config.getBoolean("protect-tamed-pets", true);
        speedMultiplier = config.getDouble("speed-multiplier", 1.0);
        returnToPreviousLocation = config.getBoolean("return-to-previous-location", true);
        safeDismount = config.getBoolean("safe-dismount", true);
        allowPlayerRiding = config.getBoolean("allow-player-riding", false);
        allowStacking = config.getBoolean("allow-stacking", false);
    }

    // ==================== 事件监听: 骑乘 ====================

    /**
     * 监听玩家右键实体事件 - 实现骑乘逻辑
     * 使用 HIGHEST 优先级确保在其他插件处理之后执行，以便检查事件是否已被取消
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // 如果事件已被其他插件取消，则不处理
        if (event.isCancelled()) {
            return;
        }

        // 只处理主手（HAND）交互，忽略副手（OFF_HAND），避免双重触发
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();

        // 检查玩家主手是否为空（Material.AIR）
        // 如果主手有物品，保持原版交互逻辑（喂食、攻击、交易等）
        if (player.getInventory().getItemInMainHand().getType() != Material.AIR) {
            return;
        }

        Entity clicked = event.getRightClicked();

        // 排除盔甲架（ArmorStand 继承自 LivingEntity，需要单独排除）
        if (clicked instanceof ArmorStand) {
            return;
        }

        // 仅对 LivingEntity（活着的生物）生效
        if (!(clicked instanceof LivingEntity)) {
            return;
        }

        LivingEntity target = (LivingEntity) clicked;

        // 检查目标生物是否已死亡
        if (target.isDead()) {
            return;
        }

        // 不能骑乘自己
        if (target.equals(player)) {
            return;
        }

        // ============ 0. 玩家骑乘检查 ============
        if (target instanceof Player) {
            // 未开启玩家骑乘
            if (!allowPlayerRiding) {
                return;
            }
            // 未开启叠罗汉，且目标玩家正在骑乘其他实体
            if (!allowStacking && ((Player) target).isInsideVehicle()) {
                return;
            }
        }

        // ============ 1. 黑名单/白名单检查 ============
        if (!isEntityAllowed(target)) {
            return;
        }

        // ============ 2. 已驯服宠物保护 ============
        if (protectTamedPets && isTamedByOther(target, player)) {
            return;
        }

        // 取消原版交互事件（防止同时触发其他交互逻辑）
        event.setCancelled(true);

        // ============ 3. 保存骑乘前位置 ============
        if (returnToPreviousLocation || safeDismount) {
            previousLocations.put(player.getUniqueId(), player.getLocation().clone());
        }

        // ============ 4. 如果玩家已在骑乘，先下坐骑 ============
        if (player.isInsideVehicle()) {
            Entity oldVehicle = player.getVehicle();
            if (oldVehicle instanceof LivingEntity) {
                // 清除旧坐骑的速度记录（下坐骑事件中会恢复速度）
                originalSpeeds.remove(oldVehicle.getUniqueId());
            }
            player.leaveVehicle();
        }

        // ============ 5. 应用速度倍率 ============
        if (speedMultiplier != 1.0 && target.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
            double originalSpeed = target.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue();
            originalSpeeds.put(target.getUniqueId(), originalSpeed);
            target.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(originalSpeed * speedMultiplier);
        }

        // ============ 6. 骑乘！将玩家添加到目标生物的乘客列表中 ============
        target.addPassenger(player);
    }

    // ==================== 事件监听: 下坐骑 ====================

    /**
     * 监听实体退出坐骑事件 - 处理速度恢复和位置传送
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleExit(VehicleExitEvent event) {
        // 只处理玩家下坐骑
        if (!(event.getExited() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getExited();
        UUID playerUUID = player.getUniqueId();

        // ============ 恢复被骑乘生物的原始移动速度 ============
        Entity vehicle = event.getVehicle();
        if (vehicle instanceof LivingEntity) {
            Double originalSpeed = originalSpeeds.remove(vehicle.getUniqueId());
            if (originalSpeed != null
                    && ((LivingEntity) vehicle).getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                ((LivingEntity) vehicle).getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(originalSpeed);
            }
        }

        // ============ 下坐骑保留位置 + 安全传送 ============
        Location previousLoc = previousLocations.remove(playerUUID);
        if (previousLoc == null) {
            return;
        }

        if (returnToPreviousLocation) {
            if (safeDismount) {
                // 安全传送：如果位置不安全，自动寻找安全位置
                Location safeLoc = findSafeLocation(previousLoc);
                player.teleport(safeLoc);
            } else {
                // 直接传回骑乘前位置
                player.teleport(previousLoc);
            }
        }
    }

    // ==================== 业务逻辑方法 ====================

    /**
     * 检查生物是否允许骑乘（根据黑名单/白名单配置）
     *
     * @param entity 目标生物
     * @return true=允许骑乘, false=禁止骑乘
     */
    private boolean isEntityAllowed(LivingEntity entity) {
        String entityTypeName = entity.getType().name();

        if ("WHITELIST".equalsIgnoreCase(mountMode)) {
            // 白名单模式：只有列表中的生物允许骑乘
            return entityList.contains(entityTypeName);
        } else {
            // 黑名单模式（默认）：列表中的生物禁止骑乘
            return !entityList.contains(entityTypeName);
        }
    }

    /**
     * 检查目标生物是否已被其他玩家驯服
     *
     * @param target 目标生物
     * @param player 当前玩家
     * @return true=已被其他玩家驯服, false=可骑乘
     */
    private boolean isTamedByOther(LivingEntity target, Player player) {
        UUID ownerUUID = null;
        boolean isTamed = false;

        // 检查是否实现 Tameable 接口 (Wolf, Cat, Parrot 等)
        if (target instanceof Tameable) {
            Tameable tameable = (Tameable) target;
            isTamed = tameable.isTamed();
            AnimalTamer owner = tameable.getOwner();
            if (owner != null) {
                ownerUUID = owner.getUniqueId();
            }
        }

        // AbstractHorse (马、驴、骡等) 可能有独立的 getOwnerUniqueId 方法
        if (target instanceof AbstractHorse && ownerUUID == null) {
            AbstractHorse horse = (AbstractHorse) target;
            isTamed = horse.isTamed();
            ownerUUID = horse.getOwnerUniqueId();
        }

        // 已被驯服且主人不是当前玩家
        return isTamed && ownerUUID != null && !player.getUniqueId().equals(ownerUUID);
    }

    /**
     * 查找安全位置
     * 从目标位置开始检查，如果当前位置不安全则向上/下查找最近的空位
     *
     * @param loc 目标位置
     * @return 安全的位置
     */
    private Location findSafeLocation(Location loc) {
        Location safe = loc.clone();

        // 检查当前位置是否安全
        if (isLocationSafe(safe)) {
            return safe;
        }

        // 从当前位置向上查找，最多 10 格
        for (int y = 1; y <= 10; y++) {
            safe = loc.clone().add(0, y, 0);
            if (isLocationSafe(safe)) {
                return safe;
            }
        }

        // 从当前位置向下查找，最多 10 格
        for (int y = 1; y <= 10; y++) {
            safe = loc.clone().subtract(0, y, 0);
            if (isLocationSafe(safe)) {
                return safe;
            }
        }

        // 没找到安全位置，返回世界出生点
        getLogger().warning("玩家 " + loc.getWorld().getName() + " 世界找不到安全位置，传送到出生点");
        return loc.getWorld().getSpawnLocation();
    }

    /**
     * 判断位置是否安全（玩家可以安全站立）
     *
     * @param loc 要检查的位置
     * @return true=安全, false=不安全
     */
    private boolean isLocationSafe(Location loc) {
        // 脚部方块必须是空气或可替换
        Material feet = loc.getBlock().getType();
        // 头部方块必须是空气
        Material head = loc.clone().add(0, 1, 0).getBlock().getType();
        // 下方方块必须是固体（用于站立）
        Material ground = loc.clone().subtract(0, 1, 0).getBlock().getType();

        return feet.isAir() && head.isAir() && ground.isSolid();
    }
}
