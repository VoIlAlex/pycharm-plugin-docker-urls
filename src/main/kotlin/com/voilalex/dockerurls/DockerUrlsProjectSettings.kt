package com.voilalex.dockerurls

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(
    name = "DockerUrlsProjectSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class DockerUrlsProjectSettings : PersistentStateComponent<DockerUrlsProjectSettings.State> {
    data class State(
        var selectedComposeFile: String? = null
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    fun setSelectedComposeFile(path: String?) {
        state.selectedComposeFile = path
    }

    fun selectedComposeFile(): String? = state.selectedComposeFile
}
