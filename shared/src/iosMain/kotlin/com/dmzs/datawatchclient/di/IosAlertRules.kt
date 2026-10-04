package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.dto.AlertActionDto
import com.dmzs.datawatchclient.transport.dto.AlertConditionDto
import com.dmzs.datawatchclient.transport.dto.AlertRuleDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Alert rules (parity B34; PWA Settings → Alert Rules, Android AlertRulesCard). */
public object IosAlertRules {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun list(
        profile: ServerProfile,
        onSuccess: (List<AlertRuleDto>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).listAlertRules().fold(
                onSuccess = { onSuccess(it.rules) },
                onFailure = { onError(it.message ?: "Failed to load alert rules.") },
            )
        }
    }

    /** PWA row detail: "metric op threshold → action". */
    public fun summary(rule: AlertRuleDto): String {
        val c = rule.condition
        val t = if (c.threshold % 1.0 == 0.0) c.threshold.toLong().toString() else c.threshold.toString()
        return "${c.metric} ${c.operator} $t → ${rule.action.kind}"
    }

    public fun setEnabled(
        profile: ServerProfile,
        name: String,
        enabled: Boolean,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val t = IosServiceLocator.transportFor(profile)
            val r = if (enabled) t.enableAlertRule(name) else t.disableAlertRule(name)
            onDone(r.exceptionOrNull()?.let { it.message ?: "Toggle failed." })
        }
    }

    public fun delete(
        profile: ServerProfile,
        name: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val r = IosServiceLocator.transportFor(profile).deleteAlertRule(name)
            onDone(r.exceptionOrNull()?.let { it.message ?: "Delete failed." })
        }
    }

    /** PWA createAlertRule body: name, condition, action.kind, window/cooldown, enabled. */
    public fun create(
        profile: ServerProfile,
        name: String,
        description: String,
        metric: String,
        comparison: String,
        threshold: Double,
        sourceFilter: String,
        windowSeconds: Int,
        actionKind: String,
        cooldownSeconds: Int,
        onDone: (String?) -> Unit,
    ) {
        val rule =
            AlertRuleDto(
                name = name.trim(),
                description = description.trim().ifBlank { null },
                condition = AlertConditionDto(metric = metric, operator = comparison, threshold = threshold),
                sourceFilter = sourceFilter.trim().ifBlank { null },
                windowSeconds = windowSeconds,
                action = AlertActionDto(kind = actionKind),
                enabled = true,
                cooldownSeconds = cooldownSeconds,
            )
        scope.launch {
            val r = IosServiceLocator.transportFor(profile).createAlertRule(rule)
            onDone(r.exceptionOrNull()?.let { it.message ?: "Couldn't create the rule." })
        }
    }
}
