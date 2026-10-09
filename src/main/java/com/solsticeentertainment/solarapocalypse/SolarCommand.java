package com.solsticeentertainment.solarapocalypse;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** /solar status | set <days> | add <days> | phase <n> | pause | resume | reload | announce [n] | fire [radius] */
public final class SolarCommand extends CommandBase {

    private static final List<String> SUBCOMMANDS = Arrays.asList("status", "set", "add", "phase", "pause", "resume", "reload", "announce", "fire");

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
        return "/solar status | set <days> | add <days> | phase <n> | pause | resume | reload | announce [n] | fire [radius]";
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
            case "announce": { // replays a phase's message, sound and splash to the sender
                int phase = args.length < 2 ? timeline.phaseAt(ApocalypseClock.progress()) + 1 : parseInt(args[1], 1, timeline.phaseCount());
                if (phase < 1) throw new CommandException("The apocalypse has not started; give a phase number");
                Announcer.announce(getCommandSenderAsPlayer(sender), phase - 1);
                return;
            }
            case "fire": {
                World world = sender.getEntityWorld();
                if (!SolarApocalypse.isCubic(world)) throw new CommandException("Fire is only placed in Cubic Chunks worlds so far");
                int radius = args.length < 2 ? 8 : parseInt(args[1], 0, 64);
                com.solsticeentertainment.solarapocalypse.cc.CubeEngine.FireCensus c =
                        com.solsticeentertainment.solarapocalypse.cc.CubeEngine.fireCensus(world, sender.getPosition(), radius);
                sender.sendMessage(new TextComponentString(String.format(Locale.ROOT,
                        "Fire in the loaded cubes of %d columns within %d: solar %d (by phase/layer mod 16: %s), vanilla %d."
                                + " Lit by the sun since the server started: solar %d, vanilla %d.", c.columns, radius, c.solar, c.epochs,
                        c.vanilla, BlockChanges.solarFireLit, BlockChanges.vanillaFireLit)));
                return;
            }
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
        int line = timeline.track(Math.max(phase, 0));
        long depth = (long) timeline.depthAt(p, line);
        String reach;
        if (!SolarApocalypse.isActive(world)) {
            reach = "this world is not affected";
        } else {
            int reference = line == Timeline.SURFACE && SolarApocalypse.isCubic(world)
                    ? com.solsticeentertainment.solarapocalypse.cc.CubeEngine.groundAt(world, pos.getX(), pos.getZ())
                    : SolarApocalypse.topY(world);
            reach = "depth " + depth + " layers below " + (line == Timeline.TOP ? "TOP_Y" : "SURFACE")
                    + (reference == BlockChanges.NO_Y ? " (not known here yet)" : " (here Y " + reference + ", destroyed down to Y " + (reference - depth + 1) + ")")
                    + (SolarApocalypse.isCubic(world) ? ", cubes queued " + com.solsticeentertainment.solarapocalypse.cc.CubeEngine.queued(world)
                    + behind(timeline, phase) : "")
                    + "; you: " + sky(world, pos.up());
        }
        double tickMs = MathHelper.average(world.getMinecraftServer().tickTimeArray) * 1.0E-6;
        String load = String.format(Locale.ROOT, "Engine: %.2f ms/tick average, %.2f ms slowest tick since the last status, %.1f block"
                        + " changes/tick average, %d since start. Server: %.1f ms/tick (%.1f TPS).", SolarApocalypse.averageEngineMs,
                SolarApocalypse.maxEngineMs, SolarApocalypse.averageChanges, BlockChanges.changed, tickMs, Math.min(20, 1000 / Math.max(tickMs, 1.0E-3)));
        SolarApocalypse.maxEngineMs = 0;
        return new String[]{
                "Solar apocalypse: day " + days(p) + ", " + when + (ApocalypseClock.isPaused() ? " (paused)" : ""),
                reach, load};
    }

    private static String sky(World world, BlockPos pos) {
        int sky = Sky.at(world, pos);
        return (sky == Sky.EXPOSED ? "in the sun" : sky == Sky.SHADED ? "in the shade" : "sky unknown")
                + (Sky.heat(world, pos) ? ", in the heat" : ", out of the heat");
    }

    /** ", engine behind by 0.05 days (10 layers)" while the engine cannot keep up (it then moves every cube step by step). */
    private static String behind(Timeline timeline, int phase) {
        long behind = com.solsticeentertainment.solarapocalypse.cc.CubeEngine.behind;
        if (behind <= 0) return "";
        double perDay = phase < 0 ? 0 : timeline.layersPerDay(phase);
        return ", engine behind by " + days(behind) + (perDay > 0 ? String.format(Locale.ROOT, " (%.0f layers)", behind * perDay / Timeline.DAY) : "");
    }

    private static String days(long units) {
        return String.format(Locale.ROOT, "%.2f days", units / (double) Timeline.DAY);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos target) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, SUBCOMMANDS) : Collections.emptyList();
    }
}
