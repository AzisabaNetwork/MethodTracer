# MethodTracer
Inserts `Thread.dumpStack();` at the beginning of the method.

## Note
This works only on Bukkit based servers. (no BungeeCord, no Velocity but you should be able to port to another platforms easily)

## Sample config.yml
```yml
org/bukkit/craftbukkit/v1_17_R1/entity/CraftEntity:
  - teleport(Lorg/bukkit/Location;)Z
  - teleport(Lorg/bukkit/Location;Lorg/bukkit/event/player/PlayerTeleportEvent$TeleportCause;)Z
  - teleport(Lorg/bukkit/Location;)Ljava/util/concurrent/CompletableFuture;
  - teleport(Lorg/bukkit/Location;Lorg/bukkit/event/player/PlayerTeleportEvent$TeleportCause;)Ljava/util/concurrent/CompletableFuture;
org/bukkit/craftbukkit/v1_17_R1/entity/CraftPlayer:
  - teleport(Lorg/bukkit/Location;Lorg/bukkit/event/player/PlayerTeleportEvent$TeleportCause;)Z
  - teleport(Lorg/bukkit/Location;Lorg/bukkit/event/player/PlayerTeleportEvent$TeleportCause;)Ljava/util/concurrent/CompletableFuture;
```
