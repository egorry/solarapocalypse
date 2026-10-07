package com.solsticeentertainment.solarapocalypse;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** /solar status | set <days> | add <days> | phase <n> | pause | resume | reload */
public final class SolarCommand extends CommandBase {

    private static final List<String> SUBCOMMANDS = Arrays.asList("status", "set", "add", "phase", "pause", "resume", "reload");

    @Override
    public String getName() {
        return Tags.MOD_ID;
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("solar");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/solar status | set <days> | add <days> | phase <n> | pause | resume | reload";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        Timeline timeline = SolarApocalypse.timeline();
        switch (sub) {
            case "status":
                break;
            case "set":
            case "add":
                if (args.length < 2) throw new WrongUsageException(getUsage(sender));
                long amount = Math.round(parseDouble(args[1]) * Timeline.DAY);
                ApocalypseClock.set(sub.equals("set") ? amount : ApocalypseClock.progress() + amount);
                SolarApocalypse.requeueAll();
                break;
            case "phase":
                if (args.length < 2) throw new WrongUsageException(getUsage(sender));
                int n = parseInt(args[1], 0, timeline.phaseCount());
                ApocalypseClock.set(n == 0 ? 0 : timeline.start(n - 1));
                SolarApocalypse.requeueAll();
                break;
            case "pause":
            case "resume":
                ApocalypseClock.setPaused(sub.equals("pause"));
                break;
            case "reload":
                SolarApocalypse.reload();
                break;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
        for (String line : status(sender.getEntityWorld(), sender.getPosition())) sender.sendMessage(new TextComponentString(line));
    }

    private static String[] status(World world, BlockPos pos) {
        Timeline timeline = SolarApocalypse.timeline();
        long p = ApocalypseClock.progress();
        int phase = timeline.phaseAt(p);
        String when;
        if (phase < 0) {
            when = "safe phase, phase 1 in " + days(timeline.firstStart() - p);
        } else {
            boolean last = phase == timeline.phaseCount() - 1;
            when = "phase " + (phase + 1) + "/" + timeline.phaseCount() + ", " + days(p - timeline.start(phase)) + " in"
                    + (last ? "" : ", next in " + days(timeline.end(phase) - p));
        }
        long depth = (long) timeline.depthAt(p);
        String reach;
        if (!SolarApocalypse.isActive(world)) {
            reach = "this world is not affected";
        } else {
            int reference = SolarApocalypse.isCubic(world)
                    ? com.solsticeentertainment.solarapocalypse.cc.CubeEngine.referenceAt(world, pos.getX(), pos.getZ())
                    : SolarApocalypse.topY(world);
            reach = "depth " + depth + " layers below " + SolarConfig.depthReference
                    + (reference == BlockChanges.NO_Y ? " (not known here yet)" : " (here Y " + reference + ", destroyed down to Y " + (reference - depth + 1) + ")")
                    + (SolarApocalypse.isCubic(world) ? ", cubes queued " + com.solsticeentertainment.solarapocalypse.cc.CubeEngine.queued(world) : "")
                    + "; you: " + sky(world, pos.up());
        }
        return new String[]{
                "Solar apocalypse: day " + days(p) + ", " + when + (ApocalypseClock.isPaused() ? " (paused)" : ""),
                reach};
    }

    private static String sky(World world, BlockPos pos) {
        int sky = Sky.at(world, pos);
        return (sky == Sky.EXPOSED ? "in the sun" : sky == Sky.SHADED ? "in the shade" : "sky unknown")
                + (Sky.heat(world, pos) ? ", in the heat" : ", out of the heat");
    }

    private static String days(long units) {
        return String.format(Locale.ROOT, "%.2f days", units / (double) Timeline.DAY);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos target) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, SUBCOMMANDS) : Collections.emptyList();
    }
}
