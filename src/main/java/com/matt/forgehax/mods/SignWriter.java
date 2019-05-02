package com.matt.forgehax.mods;

import static com.matt.forgehax.Helper.getLocalPlayer;
import static com.matt.forgehax.Helper.getNetworkManager;
import static com.matt.forgehax.Helper.getWorld;
import static com.matt.forgehax.Helper.printInform;

import com.matt.forgehax.asm.events.PacketEvent;
import com.matt.forgehax.util.PacketHelper;
import com.matt.forgehax.util.command.Setting;
import com.matt.forgehax.util.entity.LocalPlayerInventory;
import com.matt.forgehax.util.mod.Category;
import com.matt.forgehax.util.mod.ToggleMod;
import com.matt.forgehax.util.mod.loader.RegisterMod;
import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiEditSign;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.network.play.client.CPacketUpdateSign;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;

@RegisterMod
public class SignWriter extends ToggleMod {
    public SignWriter() { super(Category.MISC, "SignWriter", false, "Write signs"); };

    enum TimerAction {
        NOTHING,
        NOTIFY,
        WRITE
    }

    private final Setting<Integer> maxLength =
        getCommandStub()
        .builders()
        .<Integer>newSettingBuilder()
            .name("maxlength")
            .description("Max length of a line")
            .defaultTo(16)
            .min(0)
            .max(384)
            .build();

    private final Setting<Integer> minRange =
        getCommandStub()
            .builders()
            .<Integer>newSettingBuilder()
            .name("minrange")
            .description("start of range to generate")
            .defaultTo(0x400)
            .min(1)
            .build();

    private final Setting<Integer> maxRange =
        getCommandStub()
            .builders()
            .<Integer>newSettingBuilder()
            .name("maxrange")
            .description("end of range")
            .defaultTo(0x4ff)
            .min(1)
            .build();

    // "#hackedbyPutin|☭☭☭☭☭☭|⚒⚒⚒⚒⚒⚒⚒|❤❤❤❤❤❤❤❤|#KGBSquad|#NKVDComrade|Comrades!|IncompetentPeople|LongTimeNoExploit"
    private final Setting<String> randomPrefixes =
        getCommandStub()
            .builders()
            .<String>newSettingBuilder()
            .name("randomPrefixes")
            .description("A pipe \"|\" separated list of line prefixes")
            .defaultTo("                  ")
            .build();

    private final Setting<Boolean> showReceivedLength =
        getCommandStub()
        .builders()
        .<Boolean>newSettingBuilder()
        .name("showReceivedLength")
        .description("show length of updated signs")
        .defaultTo(true)
        .build();

    private final Setting<Boolean> showLengthAll =
        getCommandStub()
            .builders()
            .<Boolean>newSettingBuilder()
            .name("showLengthAll")
            .description("show for any new sign")
            .defaultTo(true)
            .build();

    private final Setting<Integer> timer =
        getCommandStub()
        .builders()
        .<Integer>newSettingBuilder()
        .name("timer")
        .description("countdown in ms")
        .defaultTo(20000)
        .build();

    private final Setting<TimerAction> timerAction =
        getCommandStub()
            .builders()
            .<TimerAction>newSettingEnumBuilder()
            .name("action")
            .description("action on timer end")
            .defaultTo(TimerAction.WRITE)
            .build();

    // Tracking new/old signs for info messages
    private Set<BlockPos> signSet;
    private static BlockPos lastSignPos;
    private long timerStart;

    @Override
    public void onEnabled(){
        signSet = new HashSet<>();
    }

    @Override
    public void onDisabled(){
        signSet = null;
        timerStart = -1; // TODO: Timer methods
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent event) {
        if (timerStart <= 0) return;

        switch (event.phase) {
            case START:
                if (System.currentTimeMillis() - timerStart >= timer.get()) {
                    if (!(MC.currentScreen instanceof GuiEditSign)) {timerStart = -1; return;}
                    switch (timerAction.get()) {
                        case WRITE:
                            ((GuiEditSign)MC.currentScreen).onGuiClosed();
                            MC.currentScreen = null;
                            MC.setIngameFocus();
                        case NOTIFY:
                            Random rand = new Random();
                            rand.setSeed(getLocalPlayer().getUniqueID().getLeastSignificantBits());
                            getWorld().playSound(
                                getLocalPlayer().getPosition().getX(),
                                getLocalPlayer().getPosition().getY(),
                                getLocalPlayer().getPosition().getZ(),
                                SoundEvents.ENTITY_CAT_AMBIENT,
                                SoundCategory.RECORDS, 0.7F, 0.7F + rand.nextFloat()*(1.5F-0.7F),
                                false);
                            timerStart = -1;
                            break;
                        case NOTHING:
                        default:
                            break;
                    }
                }
                break;
            case END:
            default:
                break;
        }
    }

    @SubscribeEvent
    public void interceptEditPacket(PacketEvent.Outgoing.Pre event) {
        if (event.getPacket() instanceof CPacketUpdateSign
            && !PacketHelper.isIgnored(event.getPacket())) {

            BlockPos signPos = ((CPacketUpdateSign) event.getPacket()).getPosition();
            String signLines[] = ((CPacketUpdateSign) event.getPacket()).getLines();

            StringBuilder[] newSignLines = new StringBuilder[4];
            ITextComponent newSignText[] = new ITextComponent[4];

            // RANDOM METHOD
            String prefixList[] = randomPrefixes.get().split("\\|");

            for (byte i=0; i<4; i++) {
                newSignLines[i] = new StringBuilder();

                newSignLines[i].append(
                    prefixList[ThreadLocalRandom.current().nextInt(0,prefixList.length)]
                );

                // add heart to show some true love
                newSignLines[i].appendCodePoint(0x2764);

                while (newSignLines[i].length() < maxLength.get()) {
                    int uchar = ThreadLocalRandom.current().nextInt(minRange.get(),maxRange.get()+1);
                    newSignLines[i].append(Character.toChars(uchar));
                }
                newSignText[i] = new TextComponentString(newSignLines[i].toString());
            }

            if (getNetworkManager() != null) {
                CPacketUpdateSign newPacket = new CPacketUpdateSign(signPos, newSignText);
                PacketHelper.ignore(newPacket);
                getNetworkManager().sendPacket(newPacket);
                event.setCanceled(true);
                lastSignPos = signPos;
                timerStart = -1;
            }
        }
    }

    @SubscribeEvent
    public void onSignPlace(PacketEvent.Outgoing.Pre event) {
        if (event.getPacket() instanceof CPacketPlayerTryUseItemOnBlock
        && !PacketHelper.isIgnored(event.getPacket())) {
            CPacketPlayerTryUseItemOnBlock packet = event.getPacket();

            // Update last pos. Each new placed sign reports as new sign without text
            EnumHand hand = packet.getHand();
            if ((hand == EnumHand.MAIN_HAND
                && Items.SIGN.equals(LocalPlayerInventory.getSelected().getItem()))
             || (hand == EnumHand.OFF_HAND
                && Items.SIGN.equals(LocalPlayerInventory.getOffhand().getItem()))) {

                // packet pos is block looking at
                lastSignPos = packet.getPos().offset(packet.getDirection());
                timerStart = System.currentTimeMillis();
                //printInform("Placed sign is at " + lastSignPos);
            }
      }
    }

    @SubscribeEvent
    public void OnSignUpdate(PacketEvent.Incoming.Pre event) {
        if (event.getPacket() instanceof SPacketUpdateTileEntity
        && !PacketHelper.isIgnored(event.getPacket())) {

            final SPacketUpdateTileEntity packet = event.getPacket();
            // set text on sign
            if (packet.getTileEntityType() == 9 && showReceivedLength.get()) {

                if (!signSet.contains(packet.getPos())) {
                    // new sign placed
                    signSet.add(packet.getPos());
                } else {
                    // old updated

                    if (lastSignPos != null && lastSignPos.equals(packet.getPos())) {
                        printInform(
                        String.format(
                            "My Sign @ %s is %d long",
                            lastSignPos, getNBTSignLength(packet.getNbtCompound())[0]));
                    // only show once per own written sign
                        lastSignPos = null;

                    } else if (showLengthAll.get()) {
                        printInform(
                        String.format(
                            "Some sign @ %s is %d long",
                            packet.getPos(), getNBTSignLength(packet.getNbtCompound())[0]));
                    }

                    signSet.remove(packet.getPos());
                }
            }
        }
    }

    private int[] getNBTSignLength(NBTTagCompound nbt) {
        int signTextLength[] = new int[5];

        for (byte i = 1; i < 5; i++) {
            signTextLength[i] = nbt.getString("Text" + i).length();
            signTextLength[0] += signTextLength[i];
        }

        return signTextLength;
    }

    @Override
    public String getDisplayText() {
        if (timerStart > 0) {
            return String.format("%s[%.2fs]",
                super.getDisplayText(),
                (float)(System.currentTimeMillis() - timerStart)/1000);
        } else {
            return super.getDisplayText();
        }
    }
}
