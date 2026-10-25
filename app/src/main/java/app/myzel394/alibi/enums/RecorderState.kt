package app.myzel394.alibi.enums

enum class RecorderState {
    STARTING,
    STOPPED,
    RECORDING,
    PAUSED,
    ERROR,

    // Only used by the model to indicate that the service is not running
    IDLE
}
