package app.appsperms.core

/**
 * Validation helpers for values interpolated into privileged shell commands.
 *
 * The interactive terminal intentionally accepts arbitrary commands. Every structured feature,
 * however, must validate package names, AppOps and user IDs before building a command.
 */
object ShellSafety {
    private val packageName = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)*$")
    private val appOp = Regex("^[A-Z][A-Z0-9_]*$")

    fun isValidPackageName(value: String): Boolean =
        value.length in 1..255 && packageName.matches(value)

    fun isValidAppOp(value: String): Boolean =
        value.length in 1..100 && appOp.matches(value)

    fun isValidUserId(value: Int): Boolean = value in 0..99_999

    fun parsePackageList(output: String): Set<String> =
        output.lineSequence()
            .map { it.trim().removePrefix("package:").substringBefore('=') }
            .filter(::isValidPackageName)
            .toSet()
}
