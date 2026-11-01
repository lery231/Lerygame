package id.gamebooster

import kotlinx.coroutines.flow.MutableStateFlow

object StateHub {
    val stats = MutableStateFlow<SysStats?>(null)
    val fps = MutableStateFlow<Float?>(null)
    val fg = MutableStateFlow<String?>(null)
    val monitoring = MutableStateFlow(false)
    val root = MutableStateFlow<Boolean?>(null)
    val msg = MutableStateFlow("")
}
