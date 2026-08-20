package com.jksalcedo.librefind.utils

object InstallerHeuristics {
    // Apps installed FROM these are FOSS
    val FOSS_INSTALLERS = setOf(
        "org.fdroid.fdroid",
        "org.fdroid.fdroid.privileged",
        "com.machiav3lli.fdroid",
        "com.looker.droidify",
        "nya.kitsunyan.foxydroid",
        "in.sunilpaulmathew.izzyondroid",
        "dev.zapstore.app",
        "app.accrescent.client",
        "com.samyak.repostore",
        "com.nahnah.florid",
        "ie.defo.ech_apps",
        "app.flicky",
        "dev.imranr.obtainium",
        "dev.imranr.obtainium.fdroid"
    )

    // Apps installed FROM these are proprietary
    val PROPRIETARY_INSTALLERS = setOf(
        "com.android.vending",
        "com.aurora.store",
        "com.apkpure.aegon",
        "com.tomclaw.appsend",
        "com.indus.appstore",
        "com.apkupdater",
        "com.taptap.global",
        "com.taptap",
        "com.taptap.intl",
        "com.sec.android.app.samsungapps",
        "com.huawei.appmarket",
        "com.xiaomi.mipicks",
        "com.oppo.market",
        "com.heytap.market",
        "com.vivo.appstore",
        "com.amazon.venezia"
    )

    fun isFossInstaller(installer: String?): Boolean =
        installer != null && installer in FOSS_INSTALLERS

    fun isProprietaryInstaller(installer: String?): Boolean =
        installer != null && installer in PROPRIETARY_INSTALLERS
}
