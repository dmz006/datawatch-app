package com.dmzs.datawatchclient.profiles

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One form field of the project / cluster profile editor. [key] is the dotted JSON
 * path (`git.url`); [type] is one of the `ProfileForm.TYPE_*` constants; [label] and
 * [placeholder] are the PWA's English copy (Android maps [key] to string resources,
 * iOS localizes via `L(...)`). [section] groups fields under a sub-heading
 * ("agent_settings").
 */
public data class ProfileField(
    val key: String,
    val type: String,
    val label: String,
    val placeholder: String,
    val options: List<String> = emptyList(),
    val section: String = "",
    val defaultValue: String = "",
)

/**
 * The PWA's profile form (app.js `renderProjectEditorForm` / `renderClusterEditorForm`
 * + `collectProjectForm` / `collectClusterForm`) as data, shared by Android and iOS.
 *
 * Form values are flat strings keyed by [ProfileField.key]: booleans are
 * "true"/"false", lists are comma-separated. [apply] overlays them onto the working
 * document so keys the form doesn't know (env, post_task_hooks, shared_volumes,
 * creds_ref, timestamps …) are preserved verbatim.
 */
public object ProfileForm {
    public const val TYPE_TEXT: String = "text"
    public const val TYPE_SELECT: String = "select"
    public const val TYPE_BOOL: String = "bool"
    public const val TYPE_NUMBER: String = "number"
    public const val TYPE_LIST: String = "list"

    public const val SECTION_AGENT_SETTINGS: String = "agent_settings"

    // PWA `_profileKnown` (mirrors the server's knownAgents / knownSidecars).
    public val AGENTS: List<String> = listOf("agent-claude", "agent-opencode", "agent-gemini", "agent-aider")
    public val SIDECARS: List<String> =
        listOf("", "lang-go", "lang-node", "lang-python", "lang-rust", "lang-kotlin", "lang-ruby", "tools-ops")
    public val CLUSTER_KINDS: List<String> = listOf("docker", "k8s", "cf")
    public val MEMORY_MODES: List<String> = listOf("sync-back", "shared", "ephemeral")
    public val GIT_PROVIDERS: List<String> = listOf("github", "gitlab", "local", "")

    private val projectFields: List<ProfileField> =
        listOf(
            ProfileField("name", TYPE_TEXT, "Name", "dns-label like: my-proj"),
            ProfileField("description", TYPE_TEXT, "Description", "optional"),
            ProfileField("git.url", TYPE_TEXT, "Git URL", "https://github.com/user/repo"),
            ProfileField("git.branch", TYPE_TEXT, "Git branch", "defaults to repo default"),
            ProfileField("git.provider", TYPE_SELECT, "Git provider", "", GIT_PROVIDERS),
            ProfileField("image_pair.agent", TYPE_SELECT, "Agent image", "", AGENTS, defaultValue = "agent-claude"),
            ProfileField("image_pair.sidecar", TYPE_SELECT, "Sidecar image", "", SIDECARS),
            ProfileField("memory.mode", TYPE_SELECT, "Memory mode", "", MEMORY_MODES, defaultValue = "sync-back"),
            ProfileField("memory.namespace", TYPE_TEXT, "Memory namespace", "defaults to project-<name>"),
            ProfileField("memory.shared_with", TYPE_LIST, "Memory shared_with", "peer profiles must reciprocate"),
            ProfileField("allow_spawn_children", TYPE_BOOL, "Allow spawn children", "", defaultValue = "false"),
            ProfileField("spawn_budget_total", TYPE_NUMBER, "Spawn budget (total)", "e.g. 10"),
            ProfileField("spawn_budget_per_minute", TYPE_NUMBER, "Spawn budget per minute", "e.g. 2"),
            ProfileField(
                "agent_settings.claude_auth_key_secret", TYPE_TEXT, "Claude auth key secret",
                "secret name → ANTHROPIC_API_KEY", section = SECTION_AGENT_SETTINGS,
            ),
            ProfileField(
                "agent_settings.opencode_ollama_url", TYPE_TEXT, "OpenCode Ollama URL",
                "http://ollama.local:11434 → OPENCODE_PROVIDER_URL", section = SECTION_AGENT_SETTINGS,
            ),
            ProfileField(
                "agent_settings.opencode_model", TYPE_TEXT, "OpenCode model (default)",
                "qwen3:8b → OPENCODE_MODEL", section = SECTION_AGENT_SETTINGS,
            ),
            ProfileField(
                "agent_settings.opencode_models", TYPE_LIST, "OpenCode model pool",
                "qwen3:8b, llama3.1:70b…", section = SECTION_AGENT_SETTINGS,
            ),
            ProfileField("skills", TYPE_LIST, "Skills", "test-first, go-style…", section = SECTION_AGENT_SETTINGS),
        )

    private val clusterFields: List<ProfileField> =
        listOf(
            ProfileField("name", TYPE_TEXT, "Name", "dns-label like: test-k8s"),
            ProfileField("description", TYPE_TEXT, "Description", "optional"),
            ProfileField("kind", TYPE_SELECT, "Kind", "", CLUSTER_KINDS, defaultValue = "k8s"),
            ProfileField("context", TYPE_TEXT, "Context", "kubectl context name"),
            ProfileField("endpoint", TYPE_TEXT, "Endpoint (override)", "https://... (optional)"),
            ProfileField("namespace", TYPE_TEXT, "Namespace", "default"),
            ProfileField("image_registry", TYPE_TEXT, "Image registry", "registry.example.com/datawatch"),
            ProfileField("image_pull_secret", TYPE_TEXT, "Pull secret", "k8s secret name (optional)"),
            ProfileField("parent_callback_url", TYPE_TEXT, "Parent callback URL", "auto-detect if empty"),
        )

    /** Form fields for `project` or `cluster`, in PWA order. */
    public fun fields(kind: String): List<ProfileField> = if (kind == "cluster") clusterFields else projectFields

    /** Select options, plus [current] when it's a value the list doesn't know (so nothing is lost). */
    public fun optionsFor(
        field: ProfileField,
        current: String,
    ): List<String> = if (current in field.options) field.options else field.options + current

    /** A new profile's starting document (PWA defaults: agent-claude, sync-back, k8s). */
    public fun template(kind: String): JsonObject =
        if (kind == "cluster") {
            JsonObject(mapOf("name" to JsonPrimitive(""), "kind" to JsonPrimitive("k8s")))
        } else {
            JsonObject(
                mapOf(
                    "name" to JsonPrimitive(""),
                    "git" to JsonObject(mapOf("url" to JsonPrimitive(""))),
                    "image_pair" to JsonObject(mapOf("agent" to JsonPrimitive("agent-claude"))),
                    "memory" to JsonObject(mapOf("mode" to JsonPrimitive("sync-back"))),
                ),
            )
        }

    /** Flat form values read from [doc]; missing fields take the field default. */
    public fun valuesFrom(
        kind: String,
        doc: JsonObject,
    ): Map<String, String> = fields(kind).associate { f -> f.key to valueOf(doc, f) }

    private fun valueOf(
        doc: JsonObject,
        f: ProfileField,
    ): String {
        val v = get(doc, f.key)
        val s: String =
            when {
                v == null || v is JsonNull -> f.defaultValue
                f.type == TYPE_LIST ->
                    (v as? JsonArray)?.joinToString(", ") { (it as? JsonPrimitive)?.content.orEmpty() }
                        ?: (v as? JsonPrimitive)?.content.orEmpty()
                v is JsonPrimitive -> v.content
                else -> f.defaultValue
            }
        return if (f.type == TYPE_NUMBER && s == "0") "" else s
    }

    /**
     * Overlays form [values] onto [doc] (unknown keys untouched). Blank text clears
     * a key that existed ("") and is skipped when it didn't, so a round trip through
     * the form doesn't add noise to the YAML.
     */
    public fun apply(
        kind: String,
        doc: JsonObject,
        values: Map<String, String>,
    ): JsonObject {
        var out = doc
        for (f in fields(kind)) {
            val raw = values[f.key] ?: continue
            val present = get(out, f.key) != null
            // Untouched fields keep their exact JSON (types, list items with commas …).
            if (present && raw == valueOf(out, f)) continue
            val value = raw.trim()
            val el: JsonElement? =
                when (f.type) {
                    TYPE_BOOL -> {
                        val b = value == "true"
                        if (!b && !present) null else JsonPrimitive(b)
                    }
                    TYPE_NUMBER ->
                        when {
                            value.isEmpty() -> if (present) JsonPrimitive(0) else null
                            else -> value.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(value)
                        }
                    TYPE_LIST -> {
                        val items = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        if (items.isEmpty() && !present) null else JsonArray(items.map { JsonPrimitive(it) })
                    }
                    else -> if (value.isEmpty() && !present) null else JsonPrimitive(value)
                }
            if (el != null) out = set(out, f.key, el)
        }
        return out
    }

    private val nameRe = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")

    /**
     * Client-side copy of the server's `Validate()` (internal/profile) so the form
     * flags problems before the round trip. Empty list = valid.
     */
    public fun validate(
        kind: String,
        doc: JsonObject,
    ): List<String> {
        val errs = ArrayList<String>()
        val name = str(doc, "name")
        when {
            name.isEmpty() -> errs += "name is required"
            name.length > 63 -> errs += "name \"$name\" exceeds 63 chars"
            !nameRe.matches(name) -> errs += "name \"$name\" must be a lowercase dns label (a-z, 0-9, -)"
        }
        if (kind == "cluster") {
            val k = str(doc, "kind")
            if (k !in CLUSTER_KINDS) errs += "kind \"$k\": must be docker|k8s|cf"
            if (k == "k8s" && str(doc, "context").isEmpty() && str(doc, "endpoint").isEmpty()) {
                errs += "k8s cluster profile requires either context or endpoint"
            }
            if (str(doc, "image_registry").any { it == ' ' || it == '\t' || it == '\n' }) {
                errs += "image_registry must not contain whitespace"
            }
        } else {
            if (str(doc, "git.url").isEmpty()) errs += "git.url is required"
            val provider = str(doc, "git.provider")
            if (provider !in GIT_PROVIDERS) errs += "git.provider \"$provider\": must be github|gitlab|local|empty"
            val agent = str(doc, "image_pair.agent")
            if (agent.isEmpty()) {
                errs += "image_pair.agent is required"
            } else if (agent !in AGENTS) {
                errs += "image_pair.agent \"$agent\": not a known agent (${AGENTS.joinToString(", ")})"
            }
            val sidecar = str(doc, "image_pair.sidecar")
            if (sidecar !in SIDECARS) errs += "image_pair.sidecar \"$sidecar\": not a known sidecar"
            val mode = str(doc, "memory.mode")
            if (mode.isNotEmpty() && mode !in MEMORY_MODES) errs += "memory.mode \"$mode\": must be shared|sync-back|ephemeral|empty"
            var budgets = false
            for (k in listOf("spawn_budget_total", "spawn_budget_per_minute")) {
                val v = get(doc, k) as? JsonPrimitive ?: continue
                if (v is JsonNull) continue
                val n = if (v.isString) null else v.content.toLongOrNull()
                if (n == null || n < 0) {
                    errs += "$k must be a non-negative whole number"
                } else if (n > 0) {
                    budgets = true
                }
            }
            val allow = (get(doc, "allow_spawn_children") as? JsonPrimitive)?.content == "true"
            if (!allow && budgets) errs += "spawn budgets set but allow_spawn_children is false — one or the other"
        }
        return errs
    }

    // ---- dotted-path helpers ----

    public fun get(
        doc: JsonObject,
        path: String,
    ): JsonElement? {
        var cur: JsonElement = doc
        for (part in path.split('.')) {
            cur = (cur as? JsonObject)?.get(part) ?: return null
        }
        return cur
    }

    private fun str(
        doc: JsonObject,
        path: String,
    ): String = (get(doc, path) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content.orEmpty()

    private fun set(
        doc: JsonObject,
        path: String,
        value: JsonElement,
    ): JsonObject {
        val head = path.substringBefore('.')
        if (!path.contains('.')) return JsonObject(doc + (head to value))
        val child = doc[head] as? JsonObject ?: JsonObject(emptyMap())
        return JsonObject(doc + (head to set(child, path.substringAfter('.'), value)))
    }
}
