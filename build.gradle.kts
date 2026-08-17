// 项目级构建脚本：声明插件版本（模块内通过 plugins { id(...) } 应用，不写版本号）
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
