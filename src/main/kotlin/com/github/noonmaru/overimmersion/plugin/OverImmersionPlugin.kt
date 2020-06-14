package com.github.noonmaru.overimmersion.plugin

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.lang.Math.toRadians
import kotlin.math.cos
import kotlin.math.sin

/**
 * @author Nemo
 */
class OverImmersionPlugin : JavaPlugin() {
    private var process: OverImmersion? = null

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val process = process

        if (process == null) {
            this.process = OverImmersion().apply {
                server.let { server ->
                    server.pluginManager.registerEvents(this, this@OverImmersionPlugin)
                    bukkitTask = server.scheduler.runTaskTimer(this@OverImmersionPlugin, this, 0L, 1L)
                }
            }
        } else {
            process.shutdown()
            this.process = null
            Bukkit.getOnlinePlayers().forEach { player ->
                player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.let { attr ->
                    attr.baseValue = attr.defaultValue
                    player.health = attr.value
                }
            }
        }

        return true
    }
}

class OverImmersion : Runnable, Listener {
    internal lateinit var bukkitTask: BukkitTask

    private var explosionLocation: Location? = null
    private var explosionTicks = 0

    @ExperimentalStdlibApi
    override fun run() {
        Bukkit.getOnlinePlayers().forEach { player ->
            player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.let { attr ->
                //체력설정
                attr.baseValue = 4.0

                if (player.health >= attr.value) {
                    player.health = attr.value
                }
            }
        }

        val players = Bukkit.getOnlinePlayers().asSequence().filter { player ->
            val mode = player.gameMode

            mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE
        }.toMutableList()

        for (player in players) {
            val playerLoc = player.location

            for (other in players) {
                if (other === player) continue

                val otherLoc = other.location
                var distance = 0.0

                if (otherLoc.world === playerLoc.world) {
                    val currentDistance = playerLoc.distance(otherLoc)

                    if (currentDistance > distance) {
                        distance = currentDistance
                    }
                }

                if (distance >= 20.0) {
                    player.damage(1.0)
                }

                player.sendActionBar("${colorBy(distance)}${ChatColor.BOLD}${distance.format(1)}")
            }
        }

        explosionLocation?.let { center ->
            val ticks = ++explosionTicks
            val radiusPerTick = 0.5
            val pointPerCircum = 6.0
            val radius = radiusPerTick * ticks
            val circum = 2.0 * Math.PI * radius
            val pointsCount = (circum / pointPerCircum).toInt()
            val angle = 360.0 / pointsCount

            val world = center.world
            val y = center.y

            for (i in 0 until pointsCount) {
                val currentAngle = toRadians(i * angle)

                val x = -sin(currentAngle)
                val z = cos(currentAngle)

                world.createExplosion(center.x + x * radius, y, center.z + z * radius, 4.0F, false, true)
            }

            if (ticks >= 100) {
                explosionTicks = 0
                explosionLocation = null
            }
        }
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity

        if (explosionLocation == null) {
            explosionLocation = player.location
        }
    }

    fun shutdown() {
        bukkitTask.cancel()
        HandlerList.unregisterAll(this)
    }
}

private fun colorBy(distance: Double): ChatColor {
    return when {
        distance < 10 -> ChatColor.AQUA
        distance < 15 -> ChatColor.GREEN
        distance < 17.5 -> ChatColor.YELLOW
        distance < 20.0 -> ChatColor.LIGHT_PURPLE
        else -> ChatColor.RED
    }
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)