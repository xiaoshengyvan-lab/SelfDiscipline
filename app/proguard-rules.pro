# 项目级 ProGuard 规则（当前 release 未开启混淆，保留以备后用）

# Room 数据库实体与 DAO 相关
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# 保留数据类
-keep class com.selfdiscipline.app.data.** { *; }
