package core.domain.knowledge

import android.content.Context
import androidx.room.RoomDatabase
import core.domain.persistence.CoreDatabase

class KnowledgeDatabaseCallback(private val context: Context) : RoomDatabase.Callback() {
    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        super.onOpen(db)
        Thread {
            runCatching {
                KnowledgeSeeder.seed(context, CoreDatabase.getInstance(context, allowCallback = false))
            }
        }.start()
    }
}
