package ru.nix13.countplaytime

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.util.*
import java.util.concurrent.TimeUnit

class CountPlayTime: JavaPlugin(), Listener {
    private val config = YamlConfiguration()
    private var conn: Connection? = null
    private lateinit var statement: Statement

    private lateinit var host: String
    private var port = 3306
    private lateinit var db: String
    private lateinit var user: String
    private lateinit var pass: String

    private val players = HashMap<String, Long>()

    private fun updatePlaytime(statement: Statement, username: String, time: Long) {
        val rs = statement.executeQuery("SELECT * FROM users_online WHERE name = '$username' AND server = '${server.ip}:${server.port}';")
        if(!rs.next())
            statement.executeUpdate("INSERT INTO users_online (name, playtime, server) VALUES('$username', '$time', '${server.ip}:${server.port}');")
        else
            statement.executeUpdate("UPDATE users_online SET playtime = playtime + $time WHERE name = '${username}' AND server = '${server.ip}:${server.port}';")
    }

    override fun onEnable() {
        val configFile = File(dataFolder, "connection.yml")
        if(!configFile.exists()) {
            configFile.parentFile.mkdirs()
            saveResource("connection.yml", false)
        }

        try {
            config.load(configFile)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        host = config.getString("host")
        port = config.getInt("port")
        db = config.getString("db")
        user = config.getString("user")
        pass = config.getString("password")

        conn = DriverManager.getConnection("jdbc:mysql://$host:$port/$db", user, pass)
        statement = conn!!.createStatement()

        server.pluginManager.registerEvents(this, this)
        logger.info("Count Play Time Plugin loaded!")
    }

    override fun onDisable() {
        logger.info("Count Play Time Plugin unloaded!")
        conn = DriverManager.getConnection("jdbc:mysql://$host:$port/$db", user, pass)
        statement = conn!!.createStatement()
        players.map { player ->
            updatePlaytime(statement, player.key, player.value)
        }
        conn!!.close()
        statement.close()
    }

    @EventHandler
    fun playerJoinEvent(event: PlayerJoinEvent) {
        players[event.player.name] = System.currentTimeMillis() / 1000
    }

    @EventHandler
    fun playerLeaveEvent(event: PlayerQuitEvent) {
        val playerTime = System.currentTimeMillis() / 1000 - players[event.player.name]!!
        players.remove(event.player.name)
        conn = DriverManager.getConnection("jdbc:mysql://$host:$port/$db", user, pass)
        statement = conn!!.createStatement()
        updatePlaytime(statement, event.player.name, playerTime)
        conn!!.close()
        statement.close()
    }

    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<String>): Boolean {
        if(cmd.name.equals("playtime", true)) {
            if(conn!!.isClosed) {
                conn = DriverManager.getConnection("jdbc:mysql://$host:$port/$db", user, pass)
                statement = conn!!.createStatement()
            }
            if(sender !is Player) {
                logger.info("This command can only be run by a player.")
                return false
            }

            val rs = statement.executeQuery("SELECT * FROM users_online WHERE name = '${sender.name}' AND server = '${server.ip}:${server.port}';")
            val playtime = if(rs!!.next()) rs.getLong("playtime") + (System.currentTimeMillis() / 1000 - players[sender.name]!!)
                           else System.currentTimeMillis() / 1000 - players[sender.name]!!
            conn!!.close()

            val formattedTime = String.format("%01d days %02d:%02d:%02d",
                TimeUnit.SECONDS.toDays(playtime),
                TimeUnit.SECONDS.toHours(playtime) - TimeUnit.DAYS.toHours(TimeUnit.SECONDS.toDays(playtime)),
                TimeUnit.SECONDS.toMinutes(playtime) - TimeUnit.HOURS.toMinutes(TimeUnit.SECONDS.toHours(playtime)),
                TimeUnit.SECONDS.toSeconds(playtime) - TimeUnit.MINUTES.toSeconds(TimeUnit.SECONDS.toMinutes(playtime)))

            sender.sendMessage("Playtime on this server: $formattedTime")
            return true
        }
        return false
    }
}