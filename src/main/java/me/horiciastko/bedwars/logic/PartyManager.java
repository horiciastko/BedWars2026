package me.horiciastko.bedwars.logic;

import me.horiciastko.bedwars.BedWars;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PartyManager {

    private static final long INVITE_EXPIRE_MS = 60_000L;

    private final BedWars plugin;
    private final Map<UUID, Party> partiesByMember = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> invitesByTarget = new ConcurrentHashMap<>();

    public PartyManager(BedWars plugin) {
        this.plugin = plugin;
    }

    public Party getParty(UUID playerId) {
        return partiesByMember.get(playerId);
    }

    public boolean isLeader(UUID playerId) {
        Party party = partiesByMember.get(playerId);
        return party != null && party.getLeader().equals(playerId);
    }

    public boolean createParty(Player leader) {
        if (getParty(leader.getUniqueId()) != null) {
            return false;
        }
        Party party = new Party(leader.getUniqueId());
        partiesByMember.put(leader.getUniqueId(), party);
        return true;
    }

    public boolean invite(Player leader, Player target) {
        Party party = getOrCreateLeaderParty(leader);
        if (party == null || !party.getLeader().equals(leader.getUniqueId())) {
            return false;
        }
        if (party.getMembers().contains(target.getUniqueId())) {
            return false;
        }

        invitesByTarget.put(target.getUniqueId(), new Invite(leader.getUniqueId(), System.currentTimeMillis()));
        return true;
    }

    public UUID getInviteLeader(UUID targetId) {
        Invite invite = invitesByTarget.get(targetId);
        if (invite == null) {
            return null;
        }
        if (System.currentTimeMillis() - invite.createdAt > INVITE_EXPIRE_MS) {
            invitesByTarget.remove(targetId);
            return null;
        }
        return invite.leaderId;
    }

    public boolean acceptInvite(Player player) {
        UUID leaderId = getInviteLeader(player.getUniqueId());
        if (leaderId == null) {
            return false;
        }

        Party party = partiesByMember.get(leaderId);
        if (party == null) {
            invitesByTarget.remove(player.getUniqueId());
            return false;
        }

        leaveParty(player.getUniqueId());
        party.getMembers().add(player.getUniqueId());
        partiesByMember.put(player.getUniqueId(), party);
        invitesByTarget.remove(player.getUniqueId());
        return true;
    }

    public void declineInvite(UUID playerId) {
        invitesByTarget.remove(playerId);
    }

    public void leaveParty(UUID playerId) {
        Party party = partiesByMember.get(playerId);
        if (party == null) {
            return;
        }

        if (party.getLeader().equals(playerId)) {
            // Disband full party if leader leaves.
            for (UUID member : new ArrayList<>(party.getMembers())) {
                partiesByMember.remove(member);
            }
            return;
        }

        party.getMembers().remove(playerId);
        partiesByMember.remove(playerId);
    }

    public boolean kick(UUID leaderId, UUID memberId) {
        Party party = partiesByMember.get(leaderId);
        if (party == null || !party.getLeader().equals(leaderId)) {
            return false;
        }
        if (leaderId.equals(memberId) || !party.getMembers().contains(memberId)) {
            return false;
        }

        party.getMembers().remove(memberId);
        partiesByMember.remove(memberId);
        return true;
    }

    public void disband(UUID leaderId) {
        Party party = partiesByMember.get(leaderId);
        if (party == null || !party.getLeader().equals(leaderId)) {
            return;
        }
        for (UUID member : new ArrayList<>(party.getMembers())) {
            partiesByMember.remove(member);
        }
    }

    public List<Player> getOnlinePartyMembers(UUID anyMember) {
        Party party = partiesByMember.get(anyMember);
        if (party == null) {
            Player single = Bukkit.getPlayer(anyMember);
            return single != null && single.isOnline() ? Collections.singletonList(single) : Collections.emptyList();
        }

        List<Player> players = new ArrayList<>();
        for (UUID member : party.getMembers()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null && online.isOnline()) {
                players.add(online);
            }
        }
        return players;
    }

    public List<UUID> getPartyMembers(UUID anyMember) {
        Party party = partiesByMember.get(anyMember);
        if (party == null) {
            return Collections.singletonList(anyMember);
        }
        return new ArrayList<>(party.getMembers());
    }

    public List<List<Player>> buildAssignmentGroups(List<Player> playersInArena, int maxGroupSize) {
        Map<UUID, Player> playerById = new ConcurrentHashMap<>();
        for (Player player : playersInArena) {
            playerById.put(player.getUniqueId(), player);
        }

        Set<UUID> consumed = new LinkedHashSet<>();
        List<List<Player>> groups = new ArrayList<>();

        for (Player player : playersInArena) {
            UUID uuid = player.getUniqueId();
            if (consumed.contains(uuid)) {
                continue;
            }

            Party party = partiesByMember.get(uuid);
            if (party == null) {
                groups.add(Collections.singletonList(player));
                consumed.add(uuid);
                continue;
            }

            List<Player> groupMembers = new ArrayList<>();
            for (UUID memberId : party.getMembers()) {
                Player member = playerById.get(memberId);
                if (member != null && !consumed.contains(memberId)) {
                    groupMembers.add(member);
                }
            }

            if (groupMembers.isEmpty()) {
                groups.add(Collections.singletonList(player));
                consumed.add(uuid);
                continue;
            }

            for (Player member : groupMembers) {
                consumed.add(member.getUniqueId());
            }

            if (groupMembers.size() <= maxGroupSize) {
                groups.add(groupMembers);
            } else {
                for (int i = 0; i < groupMembers.size(); i += maxGroupSize) {
                    int end = Math.min(groupMembers.size(), i + maxGroupSize);
                    groups.add(new ArrayList<>(groupMembers.subList(i, end)));
                }
            }
        }

        return groups;
    }

    private Party getOrCreateLeaderParty(Player leader) {
        Party existing = partiesByMember.get(leader.getUniqueId());
        if (existing != null) {
            return existing.getLeader().equals(leader.getUniqueId()) ? existing : null;
        }

        Party created = new Party(leader.getUniqueId());
        partiesByMember.put(leader.getUniqueId(), created);
        return created;
    }

    private static class Invite {
        private final UUID leaderId;
        private final long createdAt;

        private Invite(UUID leaderId, long createdAt) {
            this.leaderId = leaderId;
            this.createdAt = createdAt;
        }
    }

    public static class Party {
        private UUID leader;
        private final LinkedHashSet<UUID> members = new LinkedHashSet<>();

        public Party(UUID leader) {
            this.leader = leader;
            this.members.add(leader);
        }

        public UUID getLeader() {
            return leader;
        }

        public Set<UUID> getMembers() {
            return members;
        }
    }
}
