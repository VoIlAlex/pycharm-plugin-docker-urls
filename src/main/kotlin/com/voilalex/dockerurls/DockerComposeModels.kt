package com.voilalex.dockerurls

enum class ServiceStatus(val sortOrder: Int) {
    RUNNING(0),
    RESTARTING(1),
    PAUSED(2),
    CREATED(3),
    EXITED(4),
    STOPPED(5),
    UNKNOWN(6);

    companion object {
        fun from(rawState: String?): ServiceStatus {
            val normalized = rawState.orEmpty().trim().lowercase()
            return when {
                normalized.startsWith("running") -> RUNNING
                normalized.startsWith("up") -> RUNNING
                normalized.startsWith("restarting") -> RESTARTING
                normalized.startsWith("paused") -> PAUSED
                normalized.startsWith("created") -> CREATED
                normalized.startsWith("exited") -> EXITED
                normalized.startsWith("stopped") -> STOPPED
                else -> UNKNOWN
            }
        }
    }
}

data class DockerServiceRow(
    val serviceName: String,
    val status: ServiceStatus,
    val statusText: String,
    val urls: List<String>
)
