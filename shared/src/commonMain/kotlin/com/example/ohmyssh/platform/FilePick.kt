package com.example.ohmyssh.platform

import io.github.vinceglb.filekit.core.FileKit
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType

class PickedFile(
    val name: String,
    val bytes: ByteArray,
)

object FilePick {
    suspend fun pickFile(title: String? = null): PickedFile? {
        val file = FileKit.pickFile(
            type = PickerType.File(),
            mode = PickerMode.Single,
            title = title,
        ) ?: return null
        return PickedFile(name = file.name, bytes = file.readBytes())
    }

    suspend fun pickFiles(title: String? = null): List<PickedFile> {
        val files = FileKit.pickFile(
            type = PickerType.File(),
            mode = PickerMode.Multiple(),
            title = title,
        ) ?: return emptyList()
        return files.map { PickedFile(name = it.name, bytes = it.readBytes()) }
    }

    suspend fun saveFile(name: String, extension: String, bytes: ByteArray): String? {
        val file = FileKit.saveFile(
            bytes = bytes,
            baseName = name,
            extension = extension,
        ) ?: return null
        return file.path ?: file.name
    }

    suspend fun pickDirectory(title: String? = null): String? {
        if (!appPlatform.isDesktop) return null
        return FileKit.pickDirectory(title = title)?.path
    }
}
