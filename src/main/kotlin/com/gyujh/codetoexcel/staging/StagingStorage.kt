package com.gyujh.codetoexcel.staging

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.name

class StagingStorage {

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    fun rootDir(): Path = Path.of(PathManager.getConfigPath(), ROOT_FOLDER)

    fun resolveExcelFolder(excelPath: String): Path {
        val root = rootDir()
        Files.createDirectories(root)

        val registry = loadRegistry(root)
        val existing = registry.entries.firstOrNull { it.excelPath == excelPath }
        if (existing != null) {
            return root.resolve(existing.folderName)
        }

        val baseName = sanitizeFolderName(excelDisplayName(excelPath))
        val uniqueName = uniqueFolderName(root, registry, baseName)
        val id = "sha256:" + HashUtil.sha256(excelPath)
        registry.entries.add(RegistryEntry(excelPath = excelPath, folderName = uniqueName, id = id))
        saveRegistry(root, registry)

        val folder = root.resolve(uniqueName)
        Files.createDirectories(folder)
        val indexPath = folder.resolve(INDEX_FILE)
        if (!indexPath.exists()) {
            val index = ExcelIndex(
                excel = ExcelMeta(
                    displayName = baseName,
                    path = excelPath,
                    id = id
                ),
                rows = mutableMapOf()
            )
            saveExcelIndex(indexPath, index)
        }
        return folder
    }

    fun loadExcelIndex(excelPath: String): ExcelIndex {
        val folder = resolveExcelFolder(excelPath)
        val indexPath = folder.resolve(INDEX_FILE)
        return readJson(indexPath.toFile(), ExcelIndex::class.java)
            ?: ExcelIndex(
                excel = ExcelMeta(
                    displayName = excelDisplayName(excelPath),
                    path = excelPath,
                    id = "sha256:" + HashUtil.sha256(excelPath)
                ),
                rows = mutableMapOf()
            ).also { saveExcelIndex(indexPath, it) }
    }

    fun saveExcelIndex(excelPath: String, index: ExcelIndex) {
        val folder = resolveExcelFolder(excelPath)
        saveExcelIndex(folder.resolve(INDEX_FILE), index)
    }

    fun listRowDirectories(excelPath: String): List<Path> {
        val folder = resolveExcelFolder(excelPath)
        if (!folder.exists()) return emptyList()
        Files.list(folder).use { stream ->
            return stream.filter { Files.isDirectory(it) }.toList()
        }
    }

    fun ensureRowFolder(excelPath: String, rowNumber: Int): Path {
        val folder = resolveExcelFolder(excelPath)
        val rowFolder = folder.resolve(rowNumber.toString())
        Files.createDirectories(rowFolder)
        return rowFolder
    }

    fun writeImage(rowFolder: Path, fileName: String, bytes: ByteArray) {
        val target = rowFolder.resolve(fileName)
        Files.write(target, bytes)
    }

    fun renameFileSafe(source: Path, target: Path) {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun loadRegistry(root: Path): RegistryIndex {
        val file = root.resolve(REGISTRY_FILE).toFile()
        return readJson(file, RegistryIndex::class.java) ?: RegistryIndex()
    }

    private fun saveRegistry(root: Path, registry: RegistryIndex) {
        val file = root.resolve(REGISTRY_FILE).toFile()
        writeJson(file, registry)
    }

    private fun saveExcelIndex(path: Path, index: ExcelIndex) {
        writeJson(path.toFile(), index)
    }

    private fun <T> readJson(file: File, clazz: Class<T>): T? {
        if (!file.exists()) return null
        return try {
            gson.fromJson(FileUtil.loadFile(file), clazz)
        } catch (_: JsonSyntaxException) {
            null
        }
    }

    private fun writeJson(file: File, value: Any) {
        FileUtil.writeToFile(file, gson.toJson(value))
    }

    private fun uniqueFolderName(root: Path, registry: RegistryIndex, baseName: String): String {
        val used = registry.entries.map { it.folderName }.toSet()
        var candidate = baseName
        var index = 1
        while (used.contains(candidate) || root.resolve(candidate).exists()) {
            candidate = "$baseName ($index)"
            index += 1
        }
        return candidate
    }

    private fun excelDisplayName(excelPath: String): String {
        val name = Path.of(excelPath).name
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }

    private fun sanitizeFolderName(name: String): String =
        FileUtil.sanitizeFileName(name, false)

    companion object {
        private const val ROOT_FOLDER = "code-to-excel"
        private const val INDEX_FILE = "index.json"
        private const val REGISTRY_FILE = "_registry.json"
    }
}

private object HashUtil {
    fun sha256(input: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
