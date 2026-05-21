# RideAllMobs

一个 Minecraft 服务端插件，支持所有服务端核心（Bukkit/Spigot/Paper/Purpur/Leaves/Folia 等），允许玩家在空手时右键点击任意生物实体进行骑乘。

## 功能特性

- 玩家主手没有任何物品时，右键点击任意生物实体，自动骑乘到该生物上
- 骑乘后可以像控制马一样控制该生物移动（WASD 移动，空格跳跃，Shift 下坐骑）
- 按 Shift（潜行键）正常下坐骑，生物继续正常活动
- 骑乘期间，被骑乘的生物保持原有 AI 行为，不会冻结或停止
- 玩家手持任何物品时，右键生物保持原版交互逻辑（如喂食、攻击、交易等），不触发骑乘

## 生效范围

### 可骑乘的生物
所有 LivingEntity（活着的生物），包括但不限于：
- 僵尸、骷髅、爬行者
- 村民、铁傀儡
- 末影龙、凋灵
- 鱿鱼、发光鱿鱼
- 以及其他所有活着的生物实体

### 明确排除的实体
以下非生物实体不会被骑乘：
- 经验球（Experience Orb）
- TNT（Primed TNT）
- 掉落物（Item）
- 盔甲架（Armor Stand）
- 箭（Arrow）
- 矿车（Minecart）
- 船（Boat）
- 画（Painting）
- 展示框（Item Frame）

## 兼容性

- 服务端核心：支持所有 Bukkit 派生服务端（Bukkit/Spigot/Paper/Purpur/Leaves/Folia 等）
- 服务端版本：1.16.5 - 26.1.2
- Java 版本：17

## 安装方法

1. 从 Releases 页面下载最新版本的 `RideAllMobs-1.0.0.jar`
2. 将 JAR 文件放入服务端的 `plugins` 文件夹
3. 重启服务器或使用 `/reload` 命令加载插件
4. 无需任何配置，开箱即用

## 使用方法

1. 将主手切换为空手（手上没有任何物品）
2. 右键点击任意生物实体
3. 即可骑乘到该生物上
4. 按 Shift（潜行键）下坐骑

## 构建方法

### 前置要求
- JDK 17 或更高版本
- Maven 3.6+

### 构建步骤

```bash
# 克隆仓库
git clone https://github.com/chenray/RideAllMobs.git
cd RideAllMobs

# 使用 Maven 构建
mvn clean package

# 构建产物位于 target/RideAllMobs-1.0.0.jar
```

## 技术原理

- 监听 `PlayerInteractEntityEvent` 事件
- 检查玩家主手是否为空（`Material.AIR`）
- 检查目标是否为 LivingEntity 且非 ArmorStand
- 使用 `Entity.addPassenger(Player)` 方法实现骑乘
- 使用 `Player.leaveVehicle()` 在切换坐骑时先下坐骑
- 玩家按 Shift 下坐骑依赖原版 Minecraft 机制

## 依赖与权限

- 无配置文件
- 无权限系统
- 无指令
- 完全开箱即用

## 作者

- 作者：阿清
- 包名：top.chenray

## 协议

本项目仅供学习交流使用。
