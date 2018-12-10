package gg.rsmod.game.model.collision

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Entity
import gg.rsmod.game.model.entity.EntityType
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.region.Chunk
import gg.rsmod.game.model.region.RegionCoordinates
import gg.rsmod.game.model.region.RegionSet

class CollisionManager {

    companion object {
        const val BLOCKED_TILE = 0x1

        const val BRIDGE_TILE = 0x2
    }

    private val blocked: Multimap<RegionCoordinates, Tile> = HashMultimap.create()

    private val bridges = hashSetOf<Tile>()

    val regions = RegionSet()

    fun block(tile: Tile) {
        blocked.put(tile.toRegionCoordinates(), tile)
    }

    fun markBridged(tile: Tile) {
        bridges.add(tile)
    }

    fun submitUpdate(world: World, entity: Entity, updateType: CollisionUpdate.Type) {
        if (entity is GameObject) {
            val builder = CollisionUpdate.Builder()
            builder.setType(updateType)
            builder.putObject(world.definitions, entity)
            apply(world, builder.build())
        }
    }

    fun canTraverse(world: World, tile: Tile, type: EntityType, direction: Direction): Boolean {
        val next = tile.step(1, direction)
        var region = regions.getChunkForTile(world, tile)
        val projectile = type == EntityType.PROJECTILE

        if (!region.canTraverse(next, direction, projectile)) {
            return false
        }

        if (direction.isDiagonal()) {
            direction.getDiagonalComponents().forEach { other ->
                val otherNext = tile.step(1, other)

                if (!region.contains(otherNext)) {
                    region = regions.getChunkForTile(world, otherNext)
                }

                if (!region.canTraverse(otherNext, direction, projectile)) {
                    return false
                }
            }
        }

        return true
    }

    private fun flag(type: CollisionUpdate.Type, matrix: CollisionMatrix, localX: Int, localY: Int, flag: CollisionFlag) {
        if (type === CollisionUpdate.Type.ADDING) {
            matrix.addFlag(localX, localY, flag)
        } else {
            matrix.removeFlag(localX, localY, flag)
        }
    }

    private fun apply(world: World, update: CollisionUpdate) {
        var prev: Chunk? = null

        val type = update.type
        val map = update.flags.asMap()

        for (entry in map.entries) {
            val tile = entry.key

            var height = tile.height
            if (bridges.contains(Tile(tile.x, tile.z, 1))) {
                if (--height < 0) {
                    continue
                }
            }

            if (prev == null || !prev.contains(tile)) {
                prev = regions.getChunkForTile(world, tile)
            }

            val localX = tile.x % Chunk.CHUNK_SIZE
            val localY = tile.z % Chunk.CHUNK_SIZE

            val matrix = prev.getMatrix(height)
            val pawns = CollisionFlag.pawnFlags()
            val projectiles = CollisionFlag.projectileFlags()

            for (flag in entry.value) {
                val direction = flag.direction
                if (direction === Direction.NONE) {
                    continue
                }

                val orientation = direction.value
                if (flag.impenetrable) {
                    flag(type, matrix, localX, localY, projectiles[orientation])
                }

                flag(type, matrix, localX, localY, pawns[orientation])
            }
        }
    }
}