package com.looply.browser.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Pequeno gestor de UserScripts para o WebView Chromium do BrowseIt.
 *
 * Isto não implementa o runtime de extensões do Chrome. Guarda scripts de
 * conteúdo com metadados UserScript e prepara a injeção limitada aos endereços declarados pelo script.
 */
internal object NativeBrowserExtensions {
    private const val PREFS = "looply_browser_extensions"
    private const val KEY_ITEMS = "items"
    private const val MAX_SCRIPT_BYTES = 2 * 1024 * 1024

    data class Script(
        val id: String,
        val name: String,
        val description: String,
        val sourceUrl: String,
        val fileName: String,
        val enabled: Boolean,
        val createdAt: Long,
        val matchPatterns: List<String>,
        val includePatterns: List<String>,
        val excludePatterns: List<String>,
        val runAt: String,
    )

    fun list(context: Context): List<Map<String, Any>> = readScripts(context).map {
        mapOf(
            "id" to it.id,
            "name" to it.name,
            "description" to it.description,
            "sourceUrl" to it.sourceUrl,
            "enabled" to it.enabled,
            "installedAt" to it.createdAt,
            "matchPatterns" to it.matchPatterns,
        )
    }

    fun install(
        context: Context,
        id: String,
        requestedName: String,
        requestedDescription: String,
        sourceUrl: String,
        sourceCode: String,
    ): Map<String, Any> {
        val sourceUri = Uri.parse(sourceUrl)
        require(sourceUri.scheme.equals("https", ignoreCase = true) && !sourceUri.host.isNullOrBlank()) {
            "A origem do script tem de usar HTTPS e conter um anfitrião válido."
        }
        require(sourceCode.toByteArray(Charsets.UTF_8).size in 1..MAX_SCRIPT_BYTES) {
            "O script está vazio ou excede o limite de 2 MB."
        }

        val metadataBlock = Regex(
            "(?s)//\\s*==UserScript==(.+?)//\\s*==/UserScript=="
        ).find(sourceCode)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("O ficheiro não contém cabeçalho UserScript válido.")

        val metadata = linkedMapOf<String, MutableList<String>>()
        metadataBlock.lineSequence().forEach { line ->
            val entry = Regex("^\\s*//\\s*@([A-Za-z0-9:_-]+)\\s+(.+?)\\s*$")
                .find(line) ?: return@forEach
            metadata.getOrPut(entry.groupValues[1].lowercase()) { mutableListOf() }
                .add(entry.groupValues[2].trim())
        }

        val name = requestedName.trim().ifEmpty {
            metadata["name"]?.firstOrNull().orEmpty()
        }
        require(name.isNotBlank()) { "O script não tem nome. Não foi possível instalá-lo." }

        val matches = metadata["match"].orEmpty().filter { it.isNotBlank() }
        val includes = metadata["include"].orEmpty().filter { it.isNotBlank() }
        require(matches.isNotEmpty() || includes.isNotEmpty()) {
            "O script não declara @match ou @include; o BrowseIt não o pode limitar aos sites corretos."
        }

        val safeId = id.trim().ifEmpty { "script-${System.currentTimeMillis()}" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(96)
        val fileName = "$safeId.js"
        val directory = File(context.filesDir, "browser_extensions").apply { mkdirs() }
        File(directory, fileName).writeText(sourceCode, Charsets.UTF_8)

        val script = Script(
            id = safeId,
            name = name,
            description = requestedDescription.trim().ifEmpty {
                metadata["description"]?.firstOrNull().orEmpty()
            },
            sourceUrl = sourceUrl,
            fileName = fileName,
            enabled = true,
            createdAt = System.currentTimeMillis(),
            matchPatterns = matches,
            includePatterns = includes,
            excludePatterns = metadata["exclude"].orEmpty(),
            runAt = metadata["run-at"]?.firstOrNull()?.lowercase() ?: "document-end",
        )

        val all = readScripts(context).filterNot { it.id == safeId }.toMutableList()
        all.add(script)
        writeScripts(context, all)
        return list(context).first { it["id"] == safeId }
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean): Boolean {
        val all = readScripts(context).toMutableList()
        val index = all.indexOfFirst { it.id == id }
        if (index < 0) return false
        all[index] = all[index].copy(enabled = enabled)
        writeScripts(context, all)
        return true
    }

    fun remove(context: Context, id: String): Boolean {
        val all = readScripts(context).toMutableList()
        val removed = all.firstOrNull { it.id == id } ?: return false
        all.removeAll { it.id == id }
        writeScripts(context, all)
        File(File(context.filesDir, "browser_extensions"), removed.fileName).delete()
        return true
    }

    fun enabledInjections(context: Context): List<String> {
        return readScripts(context).filter { it.enabled }.mapNotNull { item ->
            val file = File(File(context.filesDir, "browser_extensions"), item.fileName)
            val code = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
                ?: return@mapNotNull null
            buildInjection(item, code)
        }
    }

    private fun buildInjection(item: Script, fullSource: String): String {
        val body = Regex(
            "(?s)//\\s*==UserScript==.+?//\\s*==/UserScript=="
        ).replaceFirst(fullSource, "")
        val matches = JSONArray(item.matchPatterns).toString()
        val includes = JSONArray(item.includePatterns).toString()
        val excludes = JSONArray(item.excludePatterns).toString()
        val encodedBody = JSONObject.quote(body)
        val encodedId = JSONObject.quote(item.id)
        val encodedRunAt = JSONObject.quote(item.runAt)
        return """
            (function () {
              try {
                if (window.top !== window || !/^https?:$/.test(location.protocol)) return;
                var id = $encodedId;
                var registry = window.__browseItUserScripts || (window.__browseItUserScripts = {});
                if (registry[id]) return;
                registry[id] = true;
                var url = location.href;
                var matchPatterns = $matches;
                var includePatterns = $includes;
                var excludePatterns = $excludes;
                function matches(pattern) {
                  if (pattern === '<all_urls>' || pattern === '*') return true;
                  try {
                    var escaped = String(pattern).replace(/[.+?^{}()|[\]\\]/g, '\\$&').replace(/\*/g, '.*');
                    return new RegExp('^' + escaped + '$', 'i').test(url);
                  } catch (_) { return false; }
                }
                var included = (matchPatterns.length === 0 && includePatterns.length === 0) ||
                  matchPatterns.some(matches) || includePatterns.some(matches);
                if (!included || excludePatterns.some(matches)) return;
                var source = $encodedBody;
                var run = function () {
                  try { (function () { source = source; eval(source); }).call(window); }
                  catch (error) { console.warn('[BrowseIt script ' + id + ']', error); }
                };
                var runAt = $encodedRunAt;
                if (runAt === 'document-start') run();
                else if (runAt === 'document-idle') {
                  if (document.readyState === 'complete') setTimeout(run, 0);
                  else window.addEventListener('load', function () { setTimeout(run, 0); }, { once: true });
                } else if (document.readyState === 'loading') {
                  document.addEventListener('DOMContentLoaded', run, { once: true });
                } else setTimeout(run, 0);
              } catch (error) { console.warn('[BrowseIt script loader]', error); }
            })();
        """.trimIndent()
    }

    private fun readScripts(context: Context): List<Script> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                add(
                    Script(
                        id = id,
                        name = obj.optString("name", "Extensão"),
                        description = obj.optString("description"),
                        sourceUrl = obj.optString("sourceUrl"),
                        fileName = obj.optString("fileName", "$id.js"),
                        enabled = obj.optBoolean("enabled", true),
                        createdAt = obj.optLong("createdAt"),
                        matchPatterns = readStringArray(obj.optJSONArray("matchPatterns")),
                        includePatterns = readStringArray(obj.optJSONArray("includePatterns")),
                        excludePatterns = readStringArray(obj.optJSONArray("excludePatterns")),
                        runAt = obj.optString("runAt", "document-end"),
                    )
                )
            }
        }
    }

    private fun writeScripts(context: Context, scripts: List<Script>) {
        val array = JSONArray()
        scripts.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("description", item.description)
                    put("sourceUrl", item.sourceUrl)
                    put("fileName", item.fileName)
                    put("enabled", item.enabled)
                    put("createdAt", item.createdAt)
                    put("matchPatterns", JSONArray(item.matchPatterns))
                    put("includePatterns", JSONArray(item.includePatterns))
                    put("excludePatterns", JSONArray(item.excludePatterns))
                    put("runAt", item.runAt)
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ITEMS, array.toString())
            .apply()
    }

    private fun readStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}
