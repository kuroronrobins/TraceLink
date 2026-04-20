package jp.co.terumo.tracelink.rp902app.domain.readresult

/**
 * Values attached to every read result registration from this Android terminal.
 */
data class RegistrationEnvironment(
    val deviceId: String = "android-local-device",
    val readerType: String = "RP902",
)
