package me.horiciastko.bedwars.commands;

import lombok.RequiredArgsConstructor;
import me.horiciastko.bedwars.BedWars;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class PartyCommand implements SubCommand {

    private final BedWars plugin;

    @Override
    public String getName() {
        return "party";
    }

    @Override
    public String getDescription() {
        return "Create and manage a BedWars party";
    }

    @Override
    public String getSyntax() {
        return "/bw party <invite|accept|leave|kick|list|disband>";
    }

    @Override
    public void perform(Player player, String[] args) {
        if (args.length < 2) {
            sendHelp(player);
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "invite":
                invite(player, args);
                return;
            case "accept":
                accept(player);
                return;
            case "leave":
                leave(player);
                return;
            case "kick":
                kick(player, args);
                return;
            case "list":
                list(player);
                return;
            case "disband":
                disband(player);
                return;
            default:
                sendHelp(player);
        }
    }

    private void invite(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.getConfigManager().getMessagesConfig().getString("prefix", "&b&lBEDWARS &8» ") + "&cUsage: /bw party invite <player>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(plugin.getConfigManager().getMessagesConfig().getString("prefix", "&b&lBEDWARS &8» ") + plugin.getConfigManager().getMessagesConfig().getString("party-invite-offline", "&cThat player is not online."));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessagesConfig().getString("prefix", "&b&lBEDWARS &8» ") + plugin.getConfigManager().getMessagesConfig().getString("party-invite-self", "&cYou cannot invite yourself."));
            return;
        }

        boolean ok = plugin.getPartyManager().invite(player, target);
        if (!ok) {
            player.sendMessage(plugin.getConfigManager().getMessagesConfig().getString("prefix", "&b&lBEDWARS &8» ") + "&cYou must be the party leader to invite players.");
            return;
        }

        String inviteSent = plugin.getConfigManager().getMessagesConfig().getString("party-invite-sent", "&aInvited %player% to your party.").replace("%player%", target.getName());
        String inviteReceived = plugin.getConfigManager().getMessagesConfig().getString("party-invite-received", "&e%player% invited you to a party. Type /bw party accept to join.").replace("%player%", player.getName());
        
        player.sendMessage(plugin.getConfigManager().getMessagesConfig().getString("prefix", "&b&lBEDWARS &8» ") + inviteSent);
        target.sendMessage(plugin.getConfigManager().getMessagesConfig().getString("prefix", "&b&lBEDWARS &8» ") + inviteReceived);
    }

    private void accept(Player player) {
        UUID leaderId = plugin.getPartyManager().getInviteLeader(player.getUniqueId());
        if (leaderId == null) {
            player.sendMessage(plugin.getConfigManager().getMessagesConfig().getString("prefix", "&b&lBEDWARS &8» ") + plugin.getConfigManager().getMessagesConfig().getString("party-invite-invalid", "&cYou do not have any pending party invite."));
            return;
        }

        boolean ok = plugin.getPartyManager().acceptInvite(player);
        if (!ok) {
            player.sendMessage(plugin.getConfigManager().getMessagesConfig().getString("prefix", "&b&lBEDWARS &8» ") + plugin.getConfigManager().getMessagesConfig().getString("party-invite-expired", "&cYour party invite expired or is no longer valid."));
            return;
        }

        Player leader = Bukkit.getPlayer(leaderId);
        String leaderName = leader != null ? leader.getName() : "leader";
        
        String joinMsg = plugin.getConfigManager().getMessagesConfig().getString("party-join-success", "&aJoined %leader%'s party.").replace("%leader%", leaderName);
        String notifyMsg = plugin.getConfigManager().getMessagesConfig().getString("party-join-notify", "&a%player% joined your party.").replace("%player%", player.getName());

        player.sendMessage(plugin.getConfigManager().getMessagesConfig().getString("prefix", "&b&lBEDWARS &8» ") + joinMsg);
        if (leader != null) {
            leader.sendMessage(plugin.getConfigManager().getMessagesConfig().getString("prefix", "&b&lBEDWARS &8» ") + notifyMsg);
        }
    }

    private void leave(Player player) {
        me.horiciastko.bedwars.logic.PartyManager.Party party = plugin.getPartyManager().getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage(plugin.getConfigManager().getMessagesConfig().getString("prefix", "&b&lBEDWARS &8» ") + plugin.getConfigManager().getMessagesConfig().getString("party-not-in-party", "&cYou are not in a party."));
            return;
        }

        boolean wasLeader = party.getLeader().equals(player.getUniqueId());
        plugin.getPartyManager().leaveParty(player.getUniqueId());

        String prefix = plugin.getConfigManager().getMessagesConfig().getString("prefix", "&b&lBEDWARS &8» ");
        if (wasLeader) {
            player.sendMessage(prefix + plugin.getConfigManager().getMessagesConfig().getString("party-leave-as-leader", "&aYou left and disbanded your party."));
        } else {
            String leaveMsg = plugin.getConfigManager().getMessagesConfig().getString("party-leave-title", "&eYou left the party.");
            player.sendMessage(prefix + leaveMsg);
            
            Player leader = Bukkit.getPlayer(party.getLeader());
            if (leader != null) {
                String notifyMsg = plugin.getConfigManager().getMessagesConfig().getString("party-leave-notify", "&e%player% left your party.").replace("%player%", player.getName());
                leader.sendMessage(prefix + notifyMsg);
            }
        }
    }

    private void kick(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.getConfigManager().getMessagesConfig().getString("prefix", "&b&lBEDWARS &8» ") + "&cUsage: /bw party kick <player>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            player.sendMessage(plugin.getConfigManager().getMessagesConfig().getString("prefix", "&b&lBEDWARS &8» ") + plugin.getConfigManager().getMessagesConfig().getString("party-invite-offline", "&cThat player is not online."));
            return;
        }

        boolean ok = plugin.getPartyManager().kick(player.getUniqueId(), target.getUniqueId());
        if (!ok) {
            player.sendMessage(plugin.getConfigManager().getMessagesConfig().getString("prefix", "&b&lBEDWARS &8» ") + plugin.getConfigManager().getMessagesConfig().getString("party-kick-no-permission", "&cYou must be the party leader to kick this player."));
            return;
        }

        String prefix = plugin.getConfigManager().getMessagesConfig().getString("prefix", "&b&lBEDWARS &8» ");
        String kickMsg = plugin.getConfigManager().getMessagesConfig().getString("party-kick-success", "&eKicked %player% from your party.").replace("%player%", target.getName());
        String targetMsg = plugin.getConfigManager().getMessagesConfig().getString("party-kick-target", "&cYou were kicked from the party.");
        
        player.sendMessage(prefix + kickMsg);
        target.sendMessage(prefix + targetMsg);
    }

    private void list(Player player) {
        me.horiciastko.bedwars.logic.PartyManager.Party party = plugin.getPartyManager().getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage(plugin.getConfigManager().getMessagesConfig().getString("prefix", "&b&lBEDWARS &8» ") + plugin.getConfigManager().getMessagesConfig().getString("party-not-in-party", "&cYou are not in a party."));
            return;
        }

        String names = party.getMembers().stream()
                .map(id -> {
                    Player online = Bukkit.getPlayer(id);
                    if (online != null) {
                        return online.getName();
                    }
                    String offline = Bukkit.getOfflinePlayer(id).getName();
                    return offline != null ? offline : id.toString();
                })
                .collect(Collectors.joining("§7, §f"));

        String prefix = plugin.getConfigManager().getMessagesConfig().getString("prefix", "&b&lBEDWARS &8» ");
        String leaderMsg = plugin.getConfigManager().getMessagesConfig().getString("party-list-leader", "&eParty Leader: &f%leader%").replace("%leader%", nameOf(party.getLeader()));
        String membersMsg = plugin.getConfigManager().getMessagesConfig().getString("party-list-members", "&eMembers (%count%): &f%members%")
                .replace("%count%", String.valueOf(party.getMembers().size()))
                .replace("%members%", names);
        
        player.sendMessage(prefix + leaderMsg);
        player.sendMessage(prefix + membersMsg);
    }

    private void disband(Player player) {
        if (!plugin.getPartyManager().isLeader(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessagesConfig().getString("prefix", "&b&lBEDWARS &8» ") + plugin.getConfigManager().getMessagesConfig().getString("party-not-leader", "&cOnly party leader can disband the party."));
            return;
        }

        me.horiciastko.bedwars.logic.PartyManager.Party party = plugin.getPartyManager().getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage(plugin.getConfigManager().getMessagesConfig().getString("prefix", "&b&lBEDWARS &8» ") + plugin.getConfigManager().getMessagesConfig().getString("party-not-in-party", "&cYou are not in a party."));
            return;
        }

        String disbandMsg = plugin.getConfigManager().getMessagesConfig().getString("party-disband-notify", "&eParty was disbanded by the leader.");
        String prefix = plugin.getConfigManager().getMessagesConfig().getString("prefix", "&b&lBEDWARS &8» ");
        
        for (UUID member : party.getMembers()) {
            if (!member.equals(player.getUniqueId())) {
                Player online = Bukkit.getPlayer(member);
                if (online != null) {
                    online.sendMessage(prefix + disbandMsg);
                }
            }
        }

        plugin.getPartyManager().disband(player.getUniqueId());
        player.sendMessage(prefix + plugin.getConfigManager().getMessagesConfig().getString("party-disband-success", "&eParty disbanded."));
    }

    private String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        String offline = Bukkit.getOfflinePlayer(uuid).getName();
        return offline != null ? offline : uuid.toString();
    }

    private void sendHelp(Player player) {
        player.sendMessage(" ");
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "party-help-title"));
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "party-help-command")
                .replace("%syntax%", "/bw party invite <player>")
                .replace("%description%", "Invite a player to your party"));
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "party-help-command")
                .replace("%syntax%", "/bw party accept")
                .replace("%description%", "Accept a party invite"));
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "party-help-command")
                .replace("%syntax%", "/bw party leave")
                .replace("%description%", "Leave your current party"));
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "party-help-command")
                .replace("%syntax%", "/bw party kick <player>")
                .replace("%description%", "Kick a player from your party"));
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "party-help-command")
                .replace("%syntax%", "/bw party list")
                .replace("%description%", "View party members"));
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "party-help-command")
                .replace("%syntax%", "/bw party disband")
                .replace("%description%", "Disband your party (leader only)"));
        player.sendMessage(" ");
    }

    @Override
    public List<String> tabComplete(Player player, String[] args) {
        if (args.length == 2) {
            return Arrays.asList("invite", "accept", "leave", "kick", "list", "disband").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && (args[1].equalsIgnoreCase("invite") || args[1].equalsIgnoreCase("kick"))) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(player.getUniqueId())) {
                    names.add(online.getName());
                }
            }
            return names.stream()
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
