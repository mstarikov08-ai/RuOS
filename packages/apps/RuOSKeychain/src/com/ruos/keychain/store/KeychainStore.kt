package com.ruos.keychain.store

import android.content.Context
import com.ruos.keychain.crypto.Vault
import org.json.JSONArray
import org.json.JSONObject

/** A saved login. [domain] is the app package or web host used for autofill matching. */
data class Credential(
    val id: String,
    val title: String,
    val domain: String,
    val username: String,
    val password: String,
    val note: String = ""
)

/** A TOTP (two-factor) account. [secret] is Base32. */
data class TotpAccount(
    val id: String,
    val issuer: String,
    val account: String,
    val secret: String
)

/**
 * Encrypted keychain storage. The entire vault (logins + TOTP secrets) is serialised to
 * JSON and encrypted with [Vault] (AES-256-GCM, AndroidKeyStore) before being written to
 * SharedPreferences. Nothing is stored in plaintext.
 */
class KeychainStore(context: Context) {

    private val prefs = context.getSharedPreferences("ruos_keychain", Context.MODE_PRIVATE)

    private var creds = ArrayList<Credential>()
    private var totps = ArrayList<TotpAccount>()
    private var loaded = false

    private fun ensure() {
        if (loaded) return
        val blob = prefs.getString(KEY_BLOB, null)
        if (blob != null) runCatching { parse(Vault.decrypt(blob)) }
        loaded = true
    }

    fun credentials(): List<Credential> { ensure(); return creds.sortedBy { it.title.lowercase() } }
    fun totpAccounts(): List<TotpAccount> { ensure(); return totps.sortedBy { it.issuer.lowercase() } }

    /** All logins whose domain matches the requesting app/host (autofill). */
    fun matching(domain: String): List<Credential> {
        ensure()
        val d = domain.lowercase()
        return creds.filter { it.domain.isNotEmpty() && (d.contains(it.domain.lowercase()) || it.domain.lowercase().contains(d)) }
    }

    fun upsertCredential(c: Credential) {
        ensure()
        val i = creds.indexOfFirst { it.id == c.id }
        if (i >= 0) creds[i] = c else creds.add(c)
        persist()
    }

    fun deleteCredential(id: String) { ensure(); creds.removeAll { it.id == id }; persist() }

    fun upsertTotp(t: TotpAccount) {
        ensure()
        val i = totps.indexOfFirst { it.id == t.id }
        if (i >= 0) totps[i] = t else totps.add(t)
        persist()
    }

    fun deleteTotp(id: String) { ensure(); totps.removeAll { it.id == id }; persist() }

    private fun persist() {
        val root = JSONObject()
        root.put("creds", JSONArray().apply {
            creds.forEach { c -> put(JSONObject().apply {
                put("id", c.id); put("title", c.title); put("domain", c.domain)
                put("user", c.username); put("pass", c.password); put("note", c.note)
            }) }
        })
        root.put("totp", JSONArray().apply {
            totps.forEach { t -> put(JSONObject().apply {
                put("id", t.id); put("issuer", t.issuer); put("account", t.account); put("secret", t.secret)
            }) }
        })
        prefs.edit().putString(KEY_BLOB, Vault.encrypt(root.toString())).apply()
    }

    private fun parse(json: String) {
        val root = JSONObject(json)
        creds = ArrayList(); totps = ArrayList()
        root.optJSONArray("creds")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                creds.add(Credential(o.getString("id"), o.optString("title"), o.optString("domain"),
                    o.optString("user"), o.optString("pass"), o.optString("note")))
            }
        }
        root.optJSONArray("totp")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                totps.add(TotpAccount(o.getString("id"), o.optString("issuer"), o.optString("account"), o.optString("secret")))
            }
        }
    }

    companion object { private const val KEY_BLOB = "vault" }
}
