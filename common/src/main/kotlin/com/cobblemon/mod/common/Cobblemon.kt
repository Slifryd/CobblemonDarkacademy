/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.cobblemon.mod.common.CobblemonBuildDetails.smallCommitHash
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.SeasonResolver
import com.cobblemon.mod.common.api.data.DataProvider
import com.cobblemon.mod.common.api.drop.CommandDropEntry
import com.cobblemon.mod.common.api.drop.DropEntry
import com.cobblemon.mod.common.api.drop.EvolutionItemDropEntry
import com.cobblemon.mod.common.api.drop.ItemDropEntry
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.CobblemonEvents.DATA_SYNCHRONIZED
import com.cobblemon.mod.common.api.interaction.RequestManager
import com.cobblemon.mod.common.api.molang.MoLangLoadedFilesCache
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.api.permission.PermissionValidator
import com.cobblemon.mod.common.api.pokeball.catching.calculators.CaptureCalculator
import com.cobblemon.mod.common.api.pokeball.catching.calculators.CaptureCalculators
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.effect.ShoulderEffectRegistry
import com.cobblemon.mod.common.api.pokemon.experience.ExperienceCalculator
import com.cobblemon.mod.common.api.pokemon.experience.ExperienceGroups
import com.cobblemon.mod.common.api.pokemon.experience.StandardExperienceCalculator
import com.cobblemon.mod.common.api.pokemon.feature.*
import com.cobblemon.mod.common.api.pokemon.helditem.HeldItemProvider
import com.cobblemon.mod.common.api.pokemon.stats.EvCalculator
import com.cobblemon.mod.common.api.pokemon.stats.Generation8EvCalculator
import com.cobblemon.mod.common.api.pokemon.stats.StatProvider
import com.cobblemon.mod.common.api.properties.CustomPokemonProperty
import com.cobblemon.mod.common.api.scheduling.ServerRealTimeTaskTracker
import com.cobblemon.mod.common.api.scheduling.ServerTaskTracker
import com.cobblemon.mod.common.api.spawning.BestSpawner
import com.cobblemon.mod.common.api.spawning.CobblemonSpawningZoneGenerator
import com.cobblemon.mod.common.api.spawning.position.AreaSpawnablePositionResolver
import com.cobblemon.mod.common.api.spawning.SpawningZoneGenerator
import com.cobblemon.mod.common.api.starter.StarterHandler
import com.cobblemon.mod.common.api.stats.CobblemonStats
import com.cobblemon.mod.common.api.storage.PokemonStoreManager
import com.cobblemon.mod.common.api.storage.adapter.conversions.ReforgedConversion
import com.cobblemon.mod.common.api.storage.adapter.database.MongoDBStoreAdapter
import com.cobblemon.mod.common.api.storage.adapter.flatfile.FileStoreAdapter
import com.cobblemon.mod.common.api.storage.adapter.flatfile.JSONStoreAdapter
import com.cobblemon.mod.common.api.storage.adapter.flatfile.NBTStoreAdapter
import com.cobblemon.mod.common.api.storage.factory.FileBackedPokemonStoreFactory
import com.cobblemon.mod.common.api.storage.molang.NbtMoLangDataStoreFactory
import com.cobblemon.mod.common.api.storage.pc.PCStore
import com.cobblemon.mod.common.api.storage.pc.link.PCLinkManager
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreManager
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes
import com.cobblemon.mod.common.api.storage.player.adapter.DexDataMongoBackend
import com.cobblemon.mod.common.api.storage.player.adapter.DexDataNbtBackend
import com.cobblemon.mod.common.api.storage.player.adapter.PlayerDataJsonBackend
import com.cobblemon.mod.common.api.storage.player.adapter.PlayerDataMongoBackend
import com.cobblemon.mod.common.api.storage.player.factory.CachedPlayerDataStoreFactory
import com.cobblemon.mod.common.api.tags.CobblemonEntityTypeTags
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.battles.*
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.block.DispenserBehaviorRegistry
import com.cobblemon.mod.common.client.CobblemonPack
import com.cobblemon.mod.common.command.argument.*
import com.cobblemon.mod.common.config.CobblemonConfig
import com.cobblemon.mod.common.config.LastChangedVersion
import com.cobblemon.mod.common.config.constraint.IntConstraint
import com.cobblemon.mod.common.config.starter.StarterConfig
import com.cobblemon.mod.common.data.CobblemonDataProvider
import com.cobblemon.mod.common.events.AdvancementHandler
import com.cobblemon.mod.common.events.EntityCallbackHandler
import com.cobblemon.mod.common.events.CallbackHandler
import com.cobblemon.mod.common.events.PokedexHandler
import com.cobblemon.mod.common.events.ServerTickHandler
import com.cobblemon.mod.common.events.StatHandler
import com.cobblemon.mod.common.net.messages.client.settings.ServerSettingsPacket
import com.cobblemon.mod.common.permission.LaxPermissionValidator
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.aspects.CHARACTERISTIC_RAINBOW_ASPECT
import com.cobblemon.mod.common.pokemon.aspects.COSMETIC_SLOT_ASPECT
import com.cobblemon.mod.common.pokemon.aspects.GENDER_ASPECT
import com.cobblemon.mod.common.pokemon.aspects.SHINY_ASPECT
import com.cobblemon.mod.common.pokemon.evolution.variants.BlockClickEvolution
import com.cobblemon.mod.common.pokemon.feature.SlowpokeTailRegrowthSpeciesFeature
import com.cobblemon.mod.common.pokemon.feature.SlowpokeTailRegrowthSpeciesFeatureProvider
import com.cobblemon.mod.common.pokemon.feature.TagSeasonResolver
import com.cobblemon.mod.common.pokemon.helditem.CobblemonHeldItemManager
import com.cobblemon.mod.common.pokemon.properties.*
import com.cobblemon.mod.common.pokemon.properties.tags.PokemonFlagProperty
import com.cobblemon.mod.common.pokemon.stat.CobblemonStatProvider
import com.cobblemon.mod.common.starter.CobblemonStarterHandler
import com.cobblemon.mod.common.util.*
import com.cobblemon.mod.common.world.feature.CobblemonPlacedFeatures
import com.cobblemon.mod.common.world.feature.ore.CobblemonOrePlacedFeatures
import com.cobblemon.mod.common.world.gamerules.CobblemonGameRules
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import net.minecraft.client.Minecraft
import net.minecraft.commands.synchronization.SingletonArgumentInfo
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.NameTagItem
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.LevelResource
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.PrintWriter
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.properties.Delegates
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents


object Cobblemon {
    const val MODID = CobblemonBuildDetails.MOD_ID
    const val VERSION = CobblemonBuildDetails.VERSION
    const val CONFIG_PATH = "config/$MODID/main.json"
    @JvmField
    val LOGGER: Logger = LogManager.getLogger()

    lateinit var implementation: CobblemonImplementation
    @Deprecated("This field is now a config value", ReplaceWith("Cobblemon.config.captureCalculator"))
    var captureCalculator: CaptureCalculator
        get() = this.config.captureCalculator
        set(value) {
            this.config.captureCalculator = value
        }
    var experienceCalculator: ExperienceCalculator = StandardExperienceCalculator
    var evYieldCalculator: EvCalculator = Generation8EvCalculator
    var starterHandler: StarterHandler = CobblemonStarterHandler()
    var isDedicatedServer = false
    val showdownThread = ShowdownThread()
    lateinit var config: CobblemonConfig
    var spawningZoneGenerator: SpawningZoneGenerator = CobblemonSpawningZoneGenerator
    var areaSpawnablePositionResolver: AreaSpawnablePositionResolver = object : AreaSpawnablePositionResolver {}
    val bestSpawner = BestSpawner
    val battleRegistry = BattleRegistry
    var storage = PokemonStoreManager()
    var molangData = NbtMoLangDataStoreFactory
    lateinit var playerDataManager: PlayerInstancedDataStoreManager
    lateinit var starterConfig: StarterConfig
    val dataProvider: DataProvider = CobblemonDataProvider
    var permissionValidator: PermissionValidator by Delegates.observable(LaxPermissionValidator().also { it.initialize() }) { _, _, newValue -> newValue.initialize() }
    var statProvider: StatProvider = CobblemonStatProvider
    var seasonResolver: SeasonResolver = TagSeasonResolver
    var wallpapers = mutableMapOf<UUID, Set<ResourceLocation>>()

    val serverPlayerStructs = mutableMapOf<UUID, ObjectValue<Player>>()

    val statistics: CobblemonStats = CobblemonStats
    val deltaClientUsers: MutableList<UUID> = mutableListOf()

    @JvmStatic
    val builtinPacks = listOf<CobblemonPack>(
        CobblemonPack(id = "adorncompatibility", name = "Adorn Compatibility", packType = PackType.CLIENT_RESOURCES, activationBehaviour = ResourcePackActivationBehaviour.ALWAYS_ENABLED, neededMods = setOf("adorn")),
        CobblemonPack(id = "gyaradosjump", name = "Gyarados Jump Patterns", packType = PackType.CLIENT_RESOURCES, activationBehaviour = ResourcePackActivationBehaviour.DEFAULT_ENABLED),
        CobblemonPack(id = "regionbiasforms", name = "Region Bias Forms", packType = PackType.CLIENT_RESOURCES, activationBehaviour = ResourcePackActivationBehaviour.DEFAULT_ENABLED),
        CobblemonPack(id = "uniqueshinyforms", name = "Shinies for Magikarp Jump", packType = PackType.CLIENT_RESOURCES, activationBehaviour = ResourcePackActivationBehaviour.NORMAL),

        CobblemonPack(id = "repurposedstructurescobblemon", name = "Repurposed Structures Cobblemon", packType = PackType.SERVER_DATA, activationBehaviour = ResourcePackActivationBehaviour.DEFAULT_ENABLED, neededMods = setOf("repurposed_structures")),
    )

    fun preInitialize(implementation: CobblemonImplementation) {
        this.implementation = implementation

        this.LOGGER.info("Launching Cobblemon ${CobblemonBuildDetails.VERSION}${if(CobblemonBuildDetails.SNAPSHOT) "-SNAPSHOT" else ""} ")
        if (CobblemonBuildDetails.SNAPSHOT) {
            this.LOGGER.info("  - Git Commit: ${smallCommitHash()} (https://gitlab.com/cable-mc/cobblemon/-/commit/${CobblemonBuildDetails.GIT_COMMIT})")
            this.LOGGER.info("  - Branch: ${CobblemonBuildDetails.BRANCH}")
        }

        implementation.registerRecipeSerializers()
        implementation.registerRecipeTypes()
        implementation.registerPermissionValidator()
        implementation.registerSoundEvents()
        implementation.registerDataComponents()
        implementation.registerBlocks()
        implementation.registerItems()
        implementation.registerEntityTypes()
        implementation.registerEntityAttributes()
        implementation.registerBlockEntityTypes()
        implementation.registerPoiTypes()
        implementation.registerVillagers()
        implementation.registerWorldGenFeatures()
        implementation.registerParticles()
        implementation.registerMenu()
        implementation.registerEntityDataSerializers()
        implementation.registerCriteria()
        implementation.registerEntitySubPredicates()
        DispenserBehaviorRegistry.registerDispenserBehaviors()


        DropEntry.register("command", CommandDropEntry::class.java)
        DropEntry.register("item", ItemDropEntry::class.java, isDefault = true)
        DropEntry.register("evolution", EvolutionItemDropEntry::class.java)

        ExperienceGroups.registerDefaults()
        CaptureCalculators.registerDefaults()

        this.loadConfig()
//        CobblemonBlockPredicates.touch()
        CobblemonOrePlacedFeatures.register()
        CobblemonPlacedFeatures.register()
        this.registerArgumentTypes()

        CobblemonGameRules // Init fields and register

        ShoulderEffectRegistry.register()

        DATA_SYNCHRONIZED.subscribe {
            storage.onPlayerDataSync(it)
            playerDataManager.syncAllToPlayer(it)
            starterHandler.handleJoin(it)
            it.requestWallpapers()
            sendServerSettingsPacketToPlayer(it)
        }
        PlatformEvents.SERVER_PLAYER_LOGOUT.subscribe {
            PCLinkManager.removeLink(it.player.uuid)
            BattleRegistry.onPlayerDisconnect(it.player)
            storage.onPlayerDisconnect(it.player)
            playerDataManager.onPlayerDisconnect(it.player)
            println("Saved Cobblemon player data for ${it.player.name.string}")
            RequestManager.onLogoff(it.player)
            serverPlayerStructs.remove(it.player.uuid)
        }
        PlatformEvents.PLAYER_DEATH.subscribe {
            PCLinkManager.removeLink(it.player.uuid)
            battleRegistry.getBattleByParticipatingPlayer(it.player)?.stop()
        }

        PlatformEvents.RIGHT_CLICK_ENTITY.subscribe { event ->
            if (event.player.getItemInHand(event.hand).item is NameTagItem && event.entity.type.`is`(CobblemonEntityTypeTags.CANNOT_HAVE_NAME_TAG)) {
                event.cancel()
            }
        }
        PlatformEvents.RIGHT_CLICK_BLOCK.subscribe { event ->
            val player = event.player
            val block = player.level().getBlockState(event.pos).block
            player.party().forEach { pokemon ->
                pokemon.lockedEvolutions
                    .filterIsInstance<BlockClickEvolution>()
                    .forEach { evolution ->
                        evolution.attemptEvolution(pokemon, BlockClickEvolution.BlockInteractionContext(block, player.level()))
                    }
            }
        }

        PlatformEvents.CHANGE_DIMENSION.subscribe {
            it.player.party().forEach { pokemon -> pokemon.entity?.recallWithAnimation() }
        }

        // Lowest priority because this applies after luxury ball bonus as of gen 4
        CobblemonEvents.FRIENDSHIP_UPDATED.subscribe(Priority.LOWEST) { event ->
            var increment = (event.newFriendship - event.pokemon.friendship).toFloat()
            if (increment <= 0) //these affects are only meant to affect positive gains
                return@subscribe
            // Our Luxury ball spec is diff from official, but we will still assume these stack
            if (event.pokemon.heldItemNoCopy().`is`(CobblemonItemTags.IS_FRIENDSHIP_BOOSTER)) {
                increment += increment * 0.5F
            }
            event.newFriendship = event.pokemon.friendship + increment.roundToInt()
        }

        HeldItemProvider.register(CobblemonHeldItemManager, Priority.LOWEST)
    }

    fun sendServerSettingsPacketToPlayer(player: ServerPlayer) {
        ServerSettingsPacket(
            this.config.preventCompletePartyDeposit,
            this.config.displayEntityLevelLabel,
            this.config.displayEntityNameLabel,
            this.config.maxPokemonLevel,
            this.config.maxPokemonFriendship,
            this.config.maxDynamaxLevel,
        ).sendToPlayer(player)
    }

    fun initialize() {
        showdownThread.launch()

        // Initialize Economy System (Common)
        com.cobblemon.mod.common.economy.EconomyManager.load()

        // Start up the data provider.
        CobblemonDataProvider.registerDefaults()

        SHINY_ASPECT.register()
        GENDER_ASPECT.register()
        COSMETIC_SLOT_ASPECT.register()
        CHARACTERISTIC_RAINBOW_ASPECT.register()

        SpeciesFeatures.types["choice"] = ChoiceSpeciesFeatureProvider::class.java
        SpeciesFeatures.types["flag"] = FlagSpeciesFeatureProvider::class.java
        SpeciesFeatures.types["integer"] = IntSpeciesFeatureProvider::class.java
        SpeciesFeatures.types["battle_flag"] = BattleFlagSpeciesFeatureProvider::class.java

        SpeciesFeatures.register(
            DataKeys.HAS_BEEN_SHEARED,
            FlagSpeciesFeatureProvider(keys = listOf(DataKeys.HAS_BEEN_SHEARED), default = false)
        )

        SpeciesFeatures.register(
            SlowpokeTailRegrowthSpeciesFeature.NAME,
            SlowpokeTailRegrowthSpeciesFeatureProvider
        )

        CustomPokemonProperty.register(UncatchableProperty)
        CustomPokemonProperty.register(BattleCloneProperty)
        CustomPokemonProperty.register(PokemonFlagProperty)
        CustomPokemonProperty.register(HiddenAbilityPropertyType)
        CustomPokemonProperty.register(AspectPropertyType)
        CustomPokemonProperty.register(UnaspectPropertyType)
        CustomPokemonProperty.register(FreezeFrameProperty)
        CustomPokemonProperty.register(NoAIProperty)

        CobblemonEvents.POKEMON_PROPERTY_INITIALISED.emit(Unit)

        CallbackHandler.setup()
        EntityCallbackHandler.setup()

        ifDedicatedServer {
            isDedicatedServer = true
        }

        PlatformEvents.SERVER_TICK_POST.subscribe {
            ServerTaskTracker.update(1/20F)
            ServerRealTimeTaskTracker.update()
        }
        PlatformEvents.SERVER_TICK_PRE.subscribe {
            ServerRealTimeTaskTracker.update()
        }
        PlatformEvents.SERVER_STARTING.subscribe { event ->
            val server = event.server
            MoLangLoadedFilesCache.initialize(server)
            playerDataManager = PlayerInstancedDataStoreManager()

            // Initialize Economy System Backend (Server)
            com.cobblemon.mod.common.economy.EconomyManager.setup(server)
            
            // Setup Data Manager (must be after all registrations)
            playerDataManager.setup(server)

            val mongoClient: MongoClient?

            val pokemonStoreRoot = server.getWorldPath(LevelResource.ROOT).resolve("pokemon").toFile()
            val storeAdapter = when (config.storageFormat) {
                "nbt", "json" -> {
                    val generalJsonFactory = CachedPlayerDataStoreFactory(PlayerDataJsonBackend())
                    generalJsonFactory.setup(server)

                    val pokedexNbtFactory = CachedPlayerDataStoreFactory(DexDataNbtBackend())
                    pokedexNbtFactory.setup(server)

                    playerDataManager.setFactory(generalJsonFactory, PlayerInstancedDataStoreTypes.GENERAL)
                    playerDataManager.setFactory(pokedexNbtFactory, PlayerInstancedDataStoreTypes.POKEDEX)

                    if (config.storageFormat == "nbt") {
                        NBTStoreAdapter(pokemonStoreRoot.absolutePath, useNestedFolders = true, folderPerClass = true)
                    } else {
                        JSONStoreAdapter(
                            pokemonStoreRoot.absolutePath,
                            useNestedFolders = true,
                            folderPerClass = true
                        )
                    }
                }

                "mongodb" -> {
                    try {
                        Class.forName("com.mongodb.client.MongoClient")

                        val mongoClientSettings = MongoClientSettings.builder()
                            .applyConnectionString(ConnectionString(config.mongoDBConnectionString))
                            .build()
                        mongoClient = MongoClients.create(mongoClientSettings)
                        val generalMongoFactory = CachedPlayerDataStoreFactory(PlayerDataMongoBackend(mongoClient, config.mongoDBDatabaseName, "PlayerDataCollection"))
                        generalMongoFactory.setup(server)

                        val pokedexMongoFactory = CachedPlayerDataStoreFactory(DexDataMongoBackend(mongoClient, config.mongoDBDatabaseName, "PokeDexCollection"))
                        pokedexMongoFactory.setup(server)

                        playerDataManager.setFactory(generalMongoFactory, PlayerInstancedDataStoreTypes.GENERAL)
                        playerDataManager.setFactory(pokedexMongoFactory, PlayerInstancedDataStoreTypes.POKEDEX)
                        MongoDBStoreAdapter(mongoClient, config.mongoDBDatabaseName)
                    } catch (e: ClassNotFoundException) {
                        LOGGER.error("MongoDB driver not found.")
                        throw e
                    }

                }


                else -> throw IllegalArgumentException("Unsupported storageFormat: ${config.storageFormat}")
            }
                .with(ReforgedConversion(server.getWorldPath(LevelResource.ROOT))) as FileStoreAdapter<*>

            storage.registerFactory(
                priority = Priority.LOWEST,
                factory = FileBackedPokemonStoreFactory(
                    adapter = storeAdapter,
                    createIfMissing = true,
                    pcConstructor = { uuid -> PCStore(uuid).also { it.resize(config.defaultBoxCount) } }
                )
            )
        }

        PlatformEvents.SERVER_STOPPED.subscribe {
            storage.unregisterAll(it.server.registryAccess())
            playerDataManager.saveAllStores()
            playerDataManager.saveExecutor.shutdown()
            playerDataManager.saveExecutor.awaitTermination(30L, TimeUnit.SECONDS)
        }
        PlatformEvents.SERVER_STARTED.subscribe { event ->
            bestSpawner.onServerStarted(event.server)
            battleRegistry.onServerStarted()
        }
        PlatformEvents.SERVER_TICK_POST.subscribe {
            ServerTickHandler.onTick(it.server)
        }

        BagItems.observable.subscribe {
            LOGGER.info("Starting dummy Showdown battle to force it to pre-load data.")
            battleRegistry.startBattle(
                BattleFormat.GEN_9_SINGLES,
                BattleSide(PokemonBattleActor(UUID.randomUUID(), BattlePokemon(Pokemon().initialize()), -1F)),
                BattleSide(PokemonBattleActor(UUID.randomUUID(), BattlePokemon(Pokemon().initialize()), -1F)),
                false
            ).ifSuccessful {
                it.mute = true
            }.ifErrored {
                val errors = it.errors.joinToString("\n") { it.javaClass.name }
                LOGGER.error("Failed to start dummy Showdown battle: $errors")
            }
        }

        registerEventHandlers()

        CobblemonEvents.COBBLEMON_INITIALISED.emit(Unit)

    }

    fun registerEventHandlers() {

        AdvancementHandler.registerListeners()
        PokedexHandler.registerListeners()
        StatHandler.registerListeners()
        // DISABLED: Mega Evolution integration (conflicts with Mega Showdown mod)
        // MegaEvolutionEventHandler.initialise()
        
        // Battle Factory system event handlers
        com.cobblemon.mod.common.api.battlefactory.BattleFactoryEventHandler.register()
        
        // Load Battle Factory Tower configuration
        val configDir = java.io.File("config/cobblemon")
        configDir.mkdirs()
        com.cobblemon.mod.common.api.battlefactory.BattleFactoryTowerManager.loadConfig(configDir)
        
        // Register Tower Battle Interaction type (Must be done on both client and server)
        com.cobblemon.mod.common.api.npc.configuration.NPCInteractConfiguration.register(
            type = "tower_battle",
            displayName = net.minecraft.network.chat.Component.literal("Tower Battle"),
            clazz = com.cobblemon.mod.common.api.battlefactory.TowerBattleInteraction::class.java
        )
        //safety secure pokemon on restart/crash
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            com.cobblemon.mod.common.api.battlefactory.BattleFactoryTowerManager.resetAllPlayers(server)
        }

    }

    fun getLevel(dimension: ResourceKey<Level>): Level? {
        return if (isDedicatedServer) {
            server()?.getLevel(dimension)
        } else {
            val mc = Minecraft.getInstance()
            return mc.singleplayerServer?.getLevel(dimension) ?: mc.level
        }
    }

    private fun initializeConfig() {
        loadCobblemonConfig()
        saveConfig(this.config)
        PokemonSpecies.observable.subscribe { starterConfig = this.loadStarterConfig() }
    }

    fun loadConfig() {
        initializeConfig()
        bestSpawner.init()
    }

    fun reloadConfig() {
        initializeConfig()
        bestSpawner.reloadConfig()
    }

    private fun loadCobblemonConfig() {
        val configFile = File(CONFIG_PATH)
        configFile.parentFile.mkdirs()

        // Check config existence and load if it exists, otherwise create default.
        if (configFile.exists()) {
            try {
                val fileReader = FileReader(configFile)
                this.config = CobblemonConfig.GSON.fromJson(fileReader, CobblemonConfig::class.java)
                fileReader.close()
            } catch (exception: Exception) {
                LOGGER.error("Failed to load the config! Using default config until the following has been addressed:")
                this.config = CobblemonConfig()
                exception.printStackTrace()
            }

            val defaultConfig = CobblemonConfig()

            CobblemonConfig::class.memberProperties.forEach {
                val field = it.javaField!!
                it.isAccessible = true
                field.annotations.forEach {
                    when (it) {
                        is LastChangedVersion -> {
                            val defaultChangedVersion = it.version
                            val lastSavedVersion = config.lastSavedVersion
                            if (defaultChangedVersion.isLaterVersion(lastSavedVersion)) {
                                field.set(config, field.get(defaultConfig))
                            }
                        }
                        is IntConstraint -> {
                            var value = field.get(config)
                            if (value is Int) {
                                value = value.coerceIn(it.min, it.max)
                                field.set(config, value)
                            }
                        }
                    }
                }
            }
        } else {
            this.config = CobblemonConfig()
        }
    }

    fun loadStarterConfig(): StarterConfig {
        if (config.exportStarterConfig) {
            val file = File("config/cobblemon/starters.json")
            file.parentFile.mkdirs()
            if (!file.exists()) {
                val config = StarterConfig()
                val pw = PrintWriter(file)
                StarterConfig.GSON.toJson(config, pw)
                pw.close()
                return config
            }
            val reader = FileReader(file)
            val config = StarterConfig.GSON.fromJson(reader, StarterConfig::class.java)
            reader.close()
            return config
        } else {
            return StarterConfig()
        }
    }

    fun saveConfig(config: CobblemonConfig) {
        config.lastSavedVersion = VERSION

        try {
            val configFile = File(CONFIG_PATH)
            val fileWriter = FileWriter(configFile)
            // Put the config to json then flush the writer to commence writing.
            CobblemonConfig.GSON.toJson(config, fileWriter)
            fileWriter.flush()
            fileWriter.close()
        } catch (exception: Exception) {
            LOGGER.error("Failed to save the config! Please consult the following stack trace:")
            exception.printStackTrace()
        }
    }

    private fun registerArgumentTypes() {
        this.implementation.registerCommandArgument(cobblemonResource("pokemon"), SpeciesArgumentType::class, SingletonArgumentInfo.contextFree(SpeciesArgumentType::species))
        this.implementation.registerCommandArgument(cobblemonResource("pokemon_properties"), PokemonPropertiesArgumentType::class, SingletonArgumentInfo.contextFree(PokemonPropertiesArgumentType::properties))
        this.implementation.registerCommandArgument(cobblemonResource("spawn_bucket"), SpawnBucketArgumentType::class, SingletonArgumentInfo.contextFree(SpawnBucketArgumentType::spawnBucket))
        this.implementation.registerCommandArgument(cobblemonResource("move"), MoveArgumentType::class, SingletonArgumentInfo.contextFree(MoveArgumentType::move))
        this.implementation.registerCommandArgument(cobblemonResource("party_slot"), PartySlotArgumentType::class, SingletonArgumentInfo.contextFree(PartySlotArgumentType::partySlot))
        this.implementation.registerCommandArgument(cobblemonResource("pokemon_store"), PokemonStoreArgumentType::class, SingletonArgumentInfo.contextFree(PokemonStoreArgumentType::pokemonStore))
        this.implementation.registerCommandArgument(cobblemonResource("dialogue"), DialogueArgumentType::class, SingletonArgumentInfo.contextFree(DialogueArgumentType::dialogue))
        this.implementation.registerCommandArgument(cobblemonResource("form"), FormArgumentType::class, SingletonArgumentInfo.contextFree(FormArgumentType::form))
        this.implementation.registerCommandArgument(cobblemonResource("dex"), DexArgumentType::class, SingletonArgumentInfo.contextFree (DexArgumentType::dex))
        this.implementation.registerCommandArgument(cobblemonResource("npc_class"), NPCClassArgumentType::class, SingletonArgumentInfo.contextFree(NPCClassArgumentType::npcClass))
        this.implementation.registerCommandArgument(cobblemonResource("wallpaper"), UnlockablePCBoxWallpaperArgumentType::class, SingletonArgumentInfo.contextFree(UnlockablePCBoxWallpaperArgumentType::wallpaper))
        this.implementation.registerCommandArgument(cobblemonResource("mark"), MarkArgumentType::class, SingletonArgumentInfo.contextFree(MarkArgumentType::mark))
        this.implementation.registerCommandArgument(cobblemonResource("transform_type"), TransformTypeArgumentType::class, SingletonArgumentInfo.contextFree(TransformTypeArgumentType::transformType))
        this.implementation.registerCommandArgument(cobblemonResource("model_part"), ModelPartArgumentType::class, SingletonArgumentInfo.contextFree(ModelPartArgumentType::modelPart))
    }

}
