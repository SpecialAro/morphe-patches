package com.specialaro.patches

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val ANDROID_NS =
    "http://schemas.android.com/apk/res/android"

@Suppress("unused")
val minSdk30Patch = resourcePatch(
    name = "Set minimum SDK to 30",
    description = "Changes the application's minimum Android SDK to API 30.",
    default = false,
) {
    execute {
        document("AndroidManifest.xml").use { document ->
            val manifest = document.documentElement

            var usesSdk =
                document.getElementsByTagName("uses-sdk")
                    .item(0) as? Element

            if (usesSdk == null) {
                usesSdk = document.createElement("uses-sdk")

                val application =
                    document.getElementsByTagName("application").item(0)

                if (application != null) {
                    manifest.insertBefore(usesSdk, application)
                } else {
                    manifest.appendChild(usesSdk)
                }
            }

            usesSdk.setAttributeNS(
                ANDROID_NS,
                "android:minSdkVersion",
                "30"
            )
        }
    }
}