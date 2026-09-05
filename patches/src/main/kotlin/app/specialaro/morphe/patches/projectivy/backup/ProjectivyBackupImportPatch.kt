/*
 * Copyright 2026 SpecialAro
 * https://github.com/specialaro/morphe-patches
 */

package app.specialaro.morphe.patches.projectivy.backup

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val PROJECTIVY_PACKAGE_NAME = "com.spocky.projengmenu"

private val PROJECTIVY_COMPATIBILITY = Compatibility(
    name = "Projectivy Launcher",
    packageName = PROJECTIVY_PACKAGE_NAME,
    appIconColor = 0x607D8B,
    targets = listOf(AppTarget("4.68")),
)

/**
 * Projectivy 4.68 backup decrypt helper.
 *
 * Logcat failure before this patch:
 *   javax.crypto.IllegalBlockSizeException: WRONG_FINAL_BLOCK_LENGTH
 *     at javax.crypto.Cipher.doFinal(...)
 *     at R6.h.b(SourceFile:47)
 *
 * The method begins by computing the backup salt length as:
 *   ProjectivyAccessibilityService.z0 + 16
 *
 * Backups written by Projectivy use a 16-byte salt.  If z0 is non-zero in a rebuilt/re-signed
 * build, the importer reads the wrong salt length, shifts the IV/ciphertext offsets, and AES-CBC
 * finalization fails.  Replace the z0 read with zero so the existing add-int keeps the length 16.
 */
private object ProjectivyBackupDecryptFingerprint : Fingerprint(
    definingClass = "LR6/h;",
    name = "b",
    returnType = "[B",
    parameters = listOf("[B"),
    strings = listOf("AES/CBC/PKCS5Padding"),
)

@Suppress("unused")
val projectivyBackupImportPatch = bytecodePatch(
    name = "Fix Projectivy backup import",
    description = "Allows Projectivy Launcher 4.68 settings backups from the original build to import after rebuilding or re-signing.",
    default = true,
) {
    compatibleWith(PROJECTIVY_COMPATIBILITY)

    execute {
        val decryptMethod = ProjectivyBackupDecryptFingerprint.method

        val saltOffsetInstruction = decryptMethod.getInstruction<OneRegisterInstruction>(0)
        if (saltOffsetInstruction.opcode != Opcode.SGET) {
            throw PatchException(
                "Expected Projectivy backup decrypt method to start with SGET, " +
                    "but found ${saltOffsetInstruction.opcode}"
            )
        }

        // Original 4.68 bytecode:
        //   sget v0, Lcom/spocky/projengmenu/services/ProjectivyAccessibilityService;->z0:I
        //   const/16 v1, 0x10
        //   add-int/2addr v0, v1
        //
        // Patched:
        //   const/16 v0, 0x0
        //   const/16 v1, 0x10
        //   add-int/2addr v0, v1
        //
        // const/16 is intentionally used instead of const/4 so the replacement keeps the same
        // 2-code-unit size as the original SGET instruction.
        decryptMethod.replaceInstruction(
            0,
            "const/16 v${saltOffsetInstruction.registerA}, 0x0",
        )
    }
}
