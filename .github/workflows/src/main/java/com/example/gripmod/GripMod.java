package com.example.gripmod;

import cam72cam.immersiverailroading.entity.EntityTrainPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.creativetabs.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Mod(modid = GripMod.MODID, name = GripMod.NAME, version = "1.0", dependencies = "required-after:immersiverailroading")
@Mod.EventBusSubscriber
public class GripMod {
    public static final String MODID = "gripmod";
    public static final String NAME = "Grip Mod for IR";

    @GameRegistry.ObjectHolder("gripmod:glove_item")
    public static final Item GLOVE_ITEM = null;
    @GameRegistry.ObjectHolder("gripmod:speedometer_item")
    public static final Item SPEEDOMETER_ITEM = null;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new GripMod.Handler());
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new Item().setCreativeTab(CreativeTabs.MISC).setMaxStackSize(1).setRegistryName("glove_item").setUnlocalizedName("glove_item"));
        event.getRegistry().register(new Item().setCreativeTab(CreativeTabs.MISC).setMaxStackSize(1).setRegistryName("speedometer_item").setUnlocalizedName("speedometer_item"));
    }

    public static class Handler {
        private final HashMap<UUID, Float> playerGripFatigue = new HashMap<>();
        public static final DamageSource TRAIN_FALL = new DamageSource("train_fall").setDamageBypassesArmor();

        @SubscribeEvent
        public void onPlayerUpdate(LivingEvent.LivingUpdateEvent event) {
            if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
            EntityPlayer player = (EntityPlayer) event.getEntityLiving();
            if (player.world.isRemote) return;

            UUID uuid = player.getUniqueID();
            AxisAlignedBB checkArea = player.getEntityBoundingBox().grow(0.5, 0.5, 0.5);
            List<EntityTrainPart> trains = player.world.getEntitiesWithinAABB(EntityTrainPart.class, checkArea);
            
            if (player.isRiding()) { playerGripFatigue.put(uuid, 0f); return; }

            if (!trains.isEmpty()) {
                EntityTrainPart train = trains.get(0);
                double speedKmH = Math.sqrt(train.motionX * train.motionX + train.motionZ * train.motionZ) * 20 * 3.6;

                if (speedKmH > 5.0) {
                    float fatigueDrain = (float) (speedKmH * 0.02);
                    ItemStack offhand = player.getItemStackFromSlot(EntityEquipmentSlot.OFFHAND);
                    if (!offhand.isEmpty() && offhand.getItem() == GripMod.GLOVE_ITEM) { fatigueDrain *= 0.7f; }

                    float currentFatigue = playerGripFatigue.getOrDefault(uuid, 0f) + fatigueDrain;
                    if (currentFatigue >= 100f) {
                        playerGripFatigue.put(uuid, 0f);
                        player.attackEntityFrom(TRAIN_FALL, 1000f);
                        player.sendMessage(new TextComponentString("§cВы не удержались и сорвались с поезда!"));
                    } else {
                        playerGripFatigue.put(uuid, currentFatigue);
                        player.motionX = train.motionX;
                        player.motionZ = train.motionZ;
                        if (player.motionY < 0) player.motionY = 0;
                        player.fallDistance = 0; 
                    }
                } else { decayGrip(uuid); }
            } else { decayGrip(uuid); }
        }

        private void decayGrip(UUID uuid) {
            if (playerGripFatigue.containsKey(uuid)) {
                float current = playerGripFatigue.get(uuid) - 1.0f;
                if (current <= 0) playerGripFatigue.remove(uuid);
                else playerGripFatigue.put(uuid, current);
            }
        }

        @SideOnly(Side.CLIENT)
        @SubscribeEvent
        public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
            if (event.getType() != RenderGameOverlayEvent.ElementType.HOTBAR) return;
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayer player = mc.player;
            if (player == null) return;
            int width = event.getResolution().getScaledWidth();
            int height = event.getResolution().getScaledHeight();

            boolean holdsSpeedometer = player.getHeldItemMainhand().getItem() == GripMod.SPEEDOMETER_ITEM || player.getHeldItemOffhand().getItem() == GripMod.SPEEDOMETER_ITEM;
            if (holdsSpeedometer) {
                List<EntityTrainPart> trains = player.world.getEntitiesWithinAABB(EntityTrainPart.class, player.getEntityBoundingBox().grow(1, 1, 1));
                double speedKmH = 0;
                if (!trains.isEmpty()) {
                    EntityTrainPart train = trains.get(0);
                    speedKmH = Math.sqrt(train.motionX * train.motionX + train.motionZ * train.motionZ) * 20 * 3.6;
                }
                String speedText = String.format("Скорость: %.1f км/ч", speedKmH);
                mc.fontRenderer.drawStringWithShadow(speedText, (width - mc.fontRenderer.getStringWidth(speedText)) / 2, height - 55, 0x00FF00);
            }

            float fatigue = playerGripFatigue.getOrDefault(player.getUniqueID(), 0f);
            if (fatigue > 0.1f) {
                int barWidth = 100, barHeight = 8, x = (width - barWidth) / 2, y = height - 70;
                Gui.drawRect(x, y, x + barWidth, y + barHeight, 0xFF555555);
                int fillWidth = (int) (barWidth * (fatigue / 100f));
                int color = fatigue > 75f ? 0xFFFF0000 : (fatigue > 40f ? 0xFFFFFF00 : 0xFF00FF00);
                Gui.drawRect(x, y, x + fillWidth, y + barHeight, color);
                mc.fontRenderer.drawStringWithShadow("Выносливость хвата", (width - mc.fontRenderer.getStringWidth("Выносливость хвата")) / 2, y - 10, 0xFFFFFF);
            }
        }
    }
}
