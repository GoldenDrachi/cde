import re

dungeon_path = r'src\main\kotlin\net\drachi\cde\dungeonsengine\command\DungeonCommand.kt'
with open(dungeon_path, 'r', encoding='utf-8') as f:
    dungeon_content = f.read()

dungeon_old = '''        val root = Commands.literal("cde").requires { it.hasPermission(2) }'''
dungeon_new = '''        val dungeonNode = Commands.literal("dungeon").requires { it.hasPermission(2) }
        val cdeNode = Commands.literal("cde")
        val cdeDungeonNode = Commands.literal("dungeon").requires { it.hasPermission(2) }'''

dungeon_content = dungeon_content.replace(dungeon_old, dungeon_new)

dungeon_old2 = '''        root.then(helpCmd).then(leaveCmd).then(portalCmd).then(configCmd).then(cleanupCmd).then(joinCmd).then(unlockCmd)
        dispatcher.register(root)'''
dungeon_new2 = '''        dungeonNode.then(helpCmd).then(leaveCmd).then(portalCmd).then(configCmd).then(cleanupCmd).then(joinCmd).then(unlockCmd)
        cdeDungeonNode.then(helpCmd).then(leaveCmd).then(portalCmd).then(configCmd).then(cleanupCmd).then(joinCmd).then(unlockCmd)
        cdeNode.then(cdeDungeonNode)
        
        dispatcher.register(dungeonNode)
        dispatcher.register(cdeNode)'''

dungeon_content = dungeon_content.replace(dungeon_old2, dungeon_new2)

with open(dungeon_path, 'w', encoding='utf-8') as f:
    f.write(dungeon_content)


battle_path = r'src\main\kotlin\net\drachi\cde\battleengine\command\CommandManager.kt'
with open(battle_path, 'r', encoding='utf-8') as f:
    battle_content = f.read()

battle_old = '''            dispatcher.register(
                Commands.literal("cdbe")
                    .then(Commands.literal("reload")'''
battle_new = '''            val reloadCmd = Commands.literal("reload")'''
battle_content = battle_content.replace(battle_old, battle_new)

# Find the end of the spawn command
# We can just use string replacement for the structure.