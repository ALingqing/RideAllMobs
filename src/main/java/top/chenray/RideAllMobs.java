package top.chenray;

import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * RideAllMobs - 主类
 *
 * 允许玩家在空手时右键点击任意生物实体骑乘上去。
 * 手持物品时右键生物保持原版交互逻辑。
 */
public class RideAllMobs extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("RideAllMobs v" + getDescription().getVersion() + " 已加载 - 阿清制作");
        getLogger().info("空手右键任意生物即可骑乘！");
    }

    @Override
    public void onDisable() {
        getLogger().info("RideAllMobs 已卸载");
    }

    /**
     * 监听玩家与实体交互事件
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
        // 以下实体自然不继承 LivingEntity，自动被排除：
        //   经验球（ExperienceOrb）、TNT（TNTPrimed）、掉落物（Item）、
        //   箭（Arrow）、矿车（Minecart）、船（Boat）、画（Painting）、
        //   展示框（ItemFrame）等
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

        // 取消原版交互事件（防止同时触发其他交互逻辑）
        event.setCancelled(true);

        // 如果玩家当前已骑乘在其他实体上，先下坐骑
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }

        // 将玩家添加到目标生物的乘客列表中，实现骑乘
        target.addPassenger(player);
    }
}
