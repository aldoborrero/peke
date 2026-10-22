package dev.aldoborrero.peke.services

import dev.aldoborrero.peke.data.RememberedTabDao
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.RememberedTab
import dev.aldoborrero.peke.util.PekeDispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RememberedTabService
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val rememberedTabDao: RememberedTabDao,
    ) {
        suspend fun getRememberedTab(itemId: String): Int? =
            withContext(PekeDispatchers.IO) {
                serverRepository.currentUser?.rowId?.let { userId ->
                    rememberedTabDao.getRememberedTab(userId, itemId)?.index
                }
            }

        suspend fun saveRememberedTab(
            itemId: String,
            tabIndex: Int,
        ): Unit =
            withContext(PekeDispatchers.IO) {
                serverRepository.currentUser?.rowId?.let { userId ->
                    rememberedTabDao.save(RememberedTab(userId, itemId, tabIndex))
                }
            }
    }
