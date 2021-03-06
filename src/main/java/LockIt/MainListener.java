package LockIt;

import LockIt.utils.LockItUtils;
import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockBurnEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.block.ItemFrameDropItemEvent;
import cn.nukkit.event.entity.EntityExplodeEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerInteractEvent.Action;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.utils.TextFormat;

import java.util.*;

public class MainListener implements Listener {

    private LockIt plugin;

    public final Map<String, ActionData> players = new HashMap<>();

    public MainListener(LockIt plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();

        if (e.isCancelled()) {
            return;
        }

        BlockData data = LockItUtils.getBlockData(b);

        if (data == null) {
            return;
        }

        if (!plugin.canAccess(data, p)) {
            p.sendMessage(TextFormat.RED + "This " + b.getName() + " is locked");
            e.setCancelled();
        } else if (!data.owner.equals(p.getName().toLowerCase()) && !p.hasPermission("lockit.access")) {
            p.sendMessage(TextFormat.RED + "Only owners can remove a protection");
            e.setCancelled();
        } else {
            LockItUtils.removeBlockData(b);
            p.sendMessage(TextFormat.GREEN + "Protection has been successfully removed.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();

        if (!LockIt.getProtectableBlocks().contains(b.getId())) {
            return;
        }

        if (!p.hasPermission("lockit.command.lock") || p.hasPermission("lockit.deny")) {
            return;
        }

        if (plugin.isAutoLock() || p.hasPermission("lockit.autolock")) {
            LockItUtils.createBlockData(b, p.getName(), true);
            p.sendMessage(TextFormat.GREEN + "Created a new protection successfully.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();

        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (p.hasPermission("lockit.info") && e.getItem().getId() == plugin.getInfoItem()) {
            BlockData bData;
            e.setCancelled();

            if (!LockIt.getProtectableBlocks().contains(b.getId()) || (bData = LockItUtils.getBlockData(b)) == null) {
                p.sendMessage(TextFormat.YELLOW + "That block is not protected");
                return;
            }

            p.sendMessage("" + TextFormat.LIGHT_PURPLE + TextFormat.BOLD + b.getName() + ":\n" +
                    TextFormat.GRAY + "Owner: " + TextFormat.YELLOW + bData.owner + "\n" +
                    TextFormat.GRAY + "Users: " + TextFormat.YELLOW + Arrays.toString(bData.users.toArray(new String[bData.users.size()])));
            return;
        }

        ActionData data = players.get(p.getName());
        boolean canAccess = plugin.canAccess(b, p);

        if (data != null) {
            e.setCancelled();
            players.remove(p.getName());

            if (!canAccess && data.action != ActionData.PASSWORD_REQUEST) {
                p.sendMessage(TextFormat.RED + "This " + b.getName() + " is locked by another player");
                return;
            }

            if (!LockIt.getProtectableBlocks().contains(b.getId())) {
                p.sendMessage(TextFormat.RED + "This block is not protectable");
                return;
            }

            BlockData bData = LockItUtils.getBlockData(b);

            switch (data.action) {
                case ActionData.LOCK:
                    if (bData != null) {
                        p.sendMessage(TextFormat.RED + "That block is already protected");
                        return;
                    }

                    LockItUtils.createBlockData(b, p.getName(), false);
                    p.sendMessage(TextFormat.GREEN + "Created a new protection successfully.");
                    return;
                case ActionData.PUBLICLOCK:
                    if (bData != null) {
                        p.sendMessage(TextFormat.RED + "That " + b.getName() + " is already protected");
                        return;
                    }

                    LockItUtils.createBlockData(b, p.getName(), false, LockItUtils.createNBT(p.getName(), new ArrayList<>(), true, null, new ArrayList<>()));
                    p.sendMessage(TextFormat.GREEN + "Created a new protection successfully.");
                    return;
                case ActionData.UNLOCK:
                    if (bData == null) {
                        p.sendMessage(TextFormat.RED + "That " + b.getName() + " is not protected");
                        return;
                    }

                    if (!bData.owner.equals(p.getName().toLowerCase()) && !p.hasPermission("lockit.access")) {
                        p.sendMessage(TextFormat.RED + "Only owners can remove a protection");
                        return;
                    }

                    //canAccess = plugin.canAccess(bData, p);

                    if (!canAccess) {
                        if (bData.password != null) {
                            if (data.data.length < 1) {
                                p.sendMessage(TextFormat.RED + "This " + b.getName() + " has password protection.\n" +
                                        TextFormat.YELLOW + " Use /unlock <password> to remove protection");
                                return;
                            }

                            if (!data.data[0].equals(bData.password)) {
                                p.sendMessage(TextFormat.RED + "Wrong password");
                                return;
                            }
                        } else {
                            p.sendMessage(TextFormat.RED + "This " + b.getName() + " is locked by another player");
                            return;
                        }
                    }

                    LockItUtils.removeBlockData(b);
                    p.sendMessage(TextFormat.GREEN + "Protection has been successfully removed.");
                    break;
                case ActionData.PASSLOCK:
                    if (bData != null) {
                        p.sendMessage(TextFormat.RED + "This block is already protected");
                        return;
                    }

                    String password = data.data[0];
                    LockItUtils.createBlockData(b, p.getName(), false, LockItUtils.createNBT(p.getName(), new ArrayList<>(), false, password, new ArrayList<>()));

                    p.sendMessage(TextFormat.GREEN + "Created a new password protection successfully.");
                    return;
                case ActionData.PASSWORD_REQUEST:
                    if (bData == null) {
                        p.sendMessage(TextFormat.RED + "That block is not protected");
                        return;
                    }

                    if (bData.password == null) {
                        p.sendMessage(TextFormat.RED + "That block is not password protected");
                        return;
                    }

                    if (!bData.password.equals(data.data[0])) {
                        p.sendMessage(TextFormat.RED + "Wrong password");
                        return;
                    }

                    bData.passwordUsers.add(p.getName().toLowerCase());
                    p.sendMessage(TextFormat.GREEN + "Now you have access permission to this " + b.getName());
                    //e.setCancelled(false);
                    return;
            }

            if (data.action >= 5) {
                if (bData == null) {
                    p.sendMessage(TextFormat.RED + "That block is not protected");
                    return;
                }

                if (!canAccess) {
                    p.sendMessage(TextFormat.RED + "This " + b.getName() + " is locked by another player");
                    return;
                }

                if (!bData.owner.equals(p.getName().toLowerCase()) && !p.hasPermission("lockit.access")) {
                    p.sendMessage(TextFormat.RED + "Only owners can modify a protection");
                    return;
                }

                switch (data.action) {
                    case ActionData.ADD:
                        for (String user : data.data) {
                            if (bData.users.contains(user)) {
                                p.sendMessage(TextFormat.YELLOW + "User " + TextFormat.WHITE + user + TextFormat.YELLOW + " is already added.");
                                continue;
                            }
                            bData.users.add(user.toLowerCase());
                        }

                        p.sendMessage(TextFormat.GREEN + "Successfully added users: " + TextFormat.GRAY + Arrays.toString(data.data));
                        break;
                    case ActionData.REMOVE:
                        for (String user : data.data) {
                            if (!bData.users.contains(user.toLowerCase())) {
                                p.sendMessage(TextFormat.YELLOW + "User " + TextFormat.WHITE + user + TextFormat.YELLOW + " doesn't exist.");
                                continue;
                            }
                            bData.users.remove(user.toLowerCase());
                        }

                        p.sendMessage(TextFormat.GREEN + "Successfully removed users: " + TextFormat.GRAY + Arrays.toString(data.data));
                        break;
                    case ActionData.REMOVEALL:
                        bData.users = new ArrayList<>();

                        p.sendMessage(TextFormat.GREEN + "Successfully removed all users");
                        break;
                    case ActionData.PUBLIC:
                        bData.isPublic = true;

                        p.sendMessage(TextFormat.GREEN + "Successfully set protection to public");
                        break;
                    case ActionData.PRIVATE:
                        bData.isPublic = false;

                        p.sendMessage(TextFormat.GREEN + "Successfully set protection to private");
                        break;
                }
            }

            LockItUtils.saveData(b, bData);
            return;
        }

        if (!canAccess) {
            p.sendMessage(TextFormat.RED + "This " + b.getName() + " is locked");
            e.setCancelled();
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        if (e.isCancelled()) {
            return;
        }

        List<Block> newList = new ArrayList<>();

        for (Block b : new ArrayList<>(e.getBlockList())) {
            if (!plugin.isLocked(b)) {
                newList.add(b);
            }
        }

        e.setBlockList(newList);
    }

    @EventHandler
    public void onBurn(BlockBurnEvent e) {
        Block b = e.getBlock();

        if (e.isCancelled()) {
            return;
        }

        if (plugin.isLocked(b)) {
            e.setCancelled();
        }
    }

    /*@EventHandler
    public void onLevelLoad(LevelLoadEvent e) {
        Level level = e.getLevel();

        File world = new File(plugin.getDataFolder() + "/worlds/" + level.getFolderName());
        world.mkdirs();

        plugin.getServer().getScheduler().scheduleAsyncTask(new AsyncTask() {
            private File world;
            private LevelData data;
            private String level;

            public AsyncTask parseTask(File world, String level) {
                this.world = world;
                this.level = level;
                return this;
            }

            @Override
            public void onRun() {
                data = ConfigParser.parseConfig(world);
            }

            @Override
            public void onCompletion(Server server) {
                LockIt plugin = LockIt.getInstance();

                if (plugin.isEnabled() && server.isLevelLoaded(level)) {
                    plugin.setLevelData(server.getLevelByName(level), data);
                }
            }
        }.parseTask(world, level.getFolderName()));
    }*/

    /*@EventHandler
    public void onLevelUnload(LevelUnloadEvent e) {

    }*/

    @EventHandler
    public void onItemFrameDrop(ItemFrameDropItemEvent e) {
        Block b = e.getBlock();
        Player p = e.getPlayer();

        if (e.isCancelled()) {
            return;
        }

        if (!plugin.canAccess(b, p)) {
            p.sendMessage(TextFormat.RED + "This " + b.getName() + " is locked");
            e.setCancelled();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();

        if (players.containsKey(p.getName())) {
            players.remove(p.getName());
        }
    }
}
