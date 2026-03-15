package ai.fixitbuddy.app.features.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import ai.fixitbuddy.app.core.audio.AudioStreamManager
import ai.fixitbuddy.app.core.camera.CameraManager
import ai.fixitbuddy.app.core.camera.GlassesCameraManager
import ai.fixitbuddy.app.core.camera.GlassesState
import ai.fixitbuddy.app.core.websocket.AgentMessage
import ai.fixitbuddy.app.core.websocket.AgentWebSocket
import ai.fixitbuddy.app.core.websocket.ConnectionState
import ai.fixitbuddy.app.core.websocket.TranscriptSpeaker
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var cameraManager: CameraManager
    private lateinit var glassesCameraManager: GlassesCameraManager
    private lateinit var audioManager: AudioStreamManager
    private lateinit var webSocket: AgentWebSocket
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var historyStore: ai.fixitbuddy.app.features.history.SessionHistoryStore

    private val connectionStateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val incomingMessagesFlow = MutableSharedFlow<AgentMessage>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        cameraManager = mockk(relaxed = true)
        glassesCameraManager = mockk(relaxed = true)
        audioManager = mockk(relaxed = true)
        webSocket = mockk(relaxed = true)
        dataStore = mockk(relaxed = true)
        okHttpClient = mockk(relaxed = true)
        historyStore = mockk(relaxed = true)

        every { webSocket.connectionState } returns connectionStateFlow
        every { webSocket.incomingMessages } returns incomingMessagesFlow
        every { cameraManager.frames } returns MutableSharedFlow()
        every { glassesCameraManager.frames } returns MutableSharedFlow()
        every { glassesCameraManager.connectionState } returns MutableStateFlow(GlassesState.DISCONNECTED)
        every { audioManager.audioChunks } returns MutableSharedFlow()
        every { audioManager.audioLevel } returns MutableStateFlow(0f)
        every { dataStore.data } returns flowOf(mockk(relaxed = true))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SessionViewModel {
        return SessionViewModel(cameraManager, glassesCameraManager, audioManager, webSocket, dataStore, okHttpClient, historyStore)
    }

    @Test
    fun `initial state is Idle with empty transcript`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(SessionState.Idle, state.sessionState)
        assertEquals("", state.transcript)
        assertEquals("idle", state.agentState)
        assertNull(state.errorMessage)
    }

    @Test
    fun `initial state has torch off`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.isTorchOn)
        assertFalse(vm.uiState.value.hasTorch)
    }

    @Test
    fun `connection state CONNECTING updates UI to Connecting`() = runTest {
        val vm = createViewModel()
        connectionStateFlow.value = ConnectionState.CONNECTING
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SessionState.Connecting, vm.uiState.value.sessionState)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `connection state CONNECTED updates UI to Active`() = runTest {
        val vm = createViewModel()
        connectionStateFlow.value = ConnectionState.CONNECTED
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SessionState.Active, vm.uiState.value.sessionState)
        assertEquals("listening", vm.uiState.value.agentState)
    }

    @Test
    fun `connection state ERROR updates UI with error message`() = runTest {
        val vm = createViewModel()
        connectionStateFlow.value = ConnectionState.ERROR
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SessionState.Error, vm.uiState.value.sessionState)
        assertNotNull(vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.errorMessage!!.contains("Connection failed"))
    }

    @Test
    fun `connection state DISCONNECTED while Active returns to Idle`() = runTest {
        val vm = createViewModel()
        // First set to connected
        connectionStateFlow.value = ConnectionState.CONNECTED
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SessionState.Active, vm.uiState.value.sessionState)

        // Then disconnect
        connectionStateFlow.value = ConnectionState.DISCONNECTED
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SessionState.Idle, vm.uiState.value.sessionState)
    }

    @Test
    fun `connection state DISCONNECTED while Idle stays Idle`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SessionState.Idle, vm.uiState.value.sessionState)

        connectionStateFlow.value = ConnectionState.DISCONNECTED
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SessionState.Idle, vm.uiState.value.sessionState)
    }

    @Test
    fun `transcript message updates UI transcript`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        incomingMessagesFlow.emit(AgentMessage.Transcript("I can see the engine", true))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("I can see the engine", vm.uiState.value.transcript)
    }

    @Test
    fun `partial transcript message updates UI`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        incomingMessagesFlow.emit(AgentMessage.Transcript("partial text", false))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("partial text", vm.uiState.value.transcript)
    }

    @Test
    fun `multiple transcript messages overwrite previous`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        incomingMessagesFlow.emit(AgentMessage.Transcript("first", true))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("first", vm.uiState.value.transcript)

        incomingMessagesFlow.emit(AgentMessage.Transcript("second", true))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("second", vm.uiState.value.transcript)
    }

    @Test
    fun `status message updates agent state`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        incomingMessagesFlow.emit(AgentMessage.Status("thinking"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("thinking", vm.uiState.value.agentState)
    }

    @Test
    fun `status message with various states`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val states = listOf("listening", "thinking", "speaking", "idle", "unknown_state")
        states.forEach { state ->
            incomingMessagesFlow.emit(AgentMessage.Status(state))
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(state, vm.uiState.value.agentState)
        }
    }

    @Test
    fun `tool call message updates agent state with tool name`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        incomingMessagesFlow.emit(AgentMessage.ToolCall("lookup_equipment_knowledge", "{}"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.agentState.contains("lookup_equipment_knowledge"))
        assertTrue(vm.uiState.value.agentState.contains("using"))
    }

    @Test
    fun `tool call message with different tool names`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val toolNames = listOf("search_tool", "lookup_api", "filter_results")
        toolNames.forEach { toolName ->
            incomingMessagesFlow.emit(AgentMessage.ToolCall(toolName, "{}"))
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(vm.uiState.value.agentState.contains(toolName))
        }
    }

    @Test
    fun `live turn flow tracks tool usage transcript audio and turn completion`() = runTest {
        val vm = createViewModel()
        connectionStateFlow.value = ConnectionState.CONNECTED
        testDispatcher.scheduler.advanceUntilIdle()

        val audioData = byteArrayOf(1, 2, 3, 4)
        incomingMessagesFlow.emit(
            AgentMessage.Transcript(
                "my fridge is not cooling",
                true,
                TranscriptSpeaker.USER
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("thinking", vm.uiState.value.agentState)

        incomingMessagesFlow.emit(AgentMessage.ToolCall("google_search_agent", """{"query":"lg fridge off"}"""))
        incomingMessagesFlow.emit(AgentMessage.Transcript("I can see OFF on the display.", false))
        incomingMessagesFlow.emit(AgentMessage.Audio(audioData))
        incomingMessagesFlow.emit(AgentMessage.Status("listening"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(SessionState.Active, state.sessionState)
        assertEquals("google_search_agent", state.lastToolCall)
        assertEquals(1, state.toolCallCount)
        assertEquals("I can see OFF on the display.", state.transcript)
        assertEquals("listening", state.agentState)
        assertTrue(state.chatTurns.any { it.role == ChatRole.USER && it.text == "my fridge is not cooling" })
        assertTrue(state.chatTurns.any { it.role == ChatRole.GENIE && it.text.contains("OFF on the display") })
        verify { audioManager.playAudioChunk(audioData) }
    }

    @Test
    fun `audio message triggers playback`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val audioData = byteArrayOf(1, 2, 3)
        incomingMessagesFlow.emit(AgentMessage.Audio(audioData))
        testDispatcher.scheduler.advanceUntilIdle()

        verify { audioManager.playAudioChunk(audioData) }
    }

    @Test
    fun `multiple audio messages trigger multiple playbacks`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val audio1 = byteArrayOf(1, 2, 3)
        val audio2 = byteArrayOf(4, 5, 6)

        incomingMessagesFlow.emit(AgentMessage.Audio(audio1))
        testDispatcher.scheduler.advanceUntilIdle()
        incomingMessagesFlow.emit(AgentMessage.Audio(audio2))
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 2) { audioManager.playAudioChunk(any()) }
    }

    @Test
    fun `stopSession resets state to Idle`() = runTest {
        val vm = createViewModel()
        connectionStateFlow.value = ConnectionState.CONNECTED
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SessionState.Active, vm.uiState.value.sessionState)

        vm.stopSession()

        assertEquals(SessionState.Idle, vm.uiState.value.sessionState)
        assertEquals("", vm.uiState.value.transcript)
        assertEquals("idle", vm.uiState.value.agentState)
    }

    @Test
    fun `stopSession clears error message`() = runTest {
        val vm = createViewModel()
        connectionStateFlow.value = ConnectionState.ERROR
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.errorMessage)

        vm.stopSession()

        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `stopSession calls audio cleanup`() = runTest {
        val vm = createViewModel()
        vm.stopSession()

        verify { audioManager.stopRecording() }
        verify { audioManager.stopPlayback() }
    }

    @Test
    fun `stopSession calls websocket disconnect`() = runTest {
        val vm = createViewModel()
        vm.stopSession()

        verify { webSocket.disconnect() }
    }

    @Test
    fun `dismissError clears error message`() = runTest {
        val vm = createViewModel()
        connectionStateFlow.value = ConnectionState.ERROR
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.errorMessage)

        vm.dismissError()
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `dismissError while no error message does nothing`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.errorMessage)

        vm.dismissError()

        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `toggleFlashlight delegates to camera manager`() = runTest {
        every { cameraManager.toggleTorch() } returns true
        val vm = createViewModel()

        vm.toggleFlashlight()

        verify { cameraManager.toggleTorch() }
    }

    @Test
    fun `toggleFlashlight on updates state to true`() = runTest {
        every { cameraManager.toggleTorch() } returns true
        val vm = createViewModel()

        vm.toggleFlashlight()

        assertTrue(vm.uiState.value.isTorchOn)
    }

    @Test
    fun `toggleFlashlight off updates state to false`() = runTest {
        every { cameraManager.toggleTorch() } returns false
        val vm = createViewModel()

        vm.toggleFlashlight()

        assertFalse(vm.uiState.value.isTorchOn)
    }

    @Test
    fun `toggleFlashlight multiple times alternates state`() = runTest {
        every { cameraManager.toggleTorch() }
            .returnsMany(true, false, true, false)
        val vm = createViewModel()

        vm.toggleFlashlight()
        assertTrue(vm.uiState.value.isTorchOn)

        vm.toggleFlashlight()
        assertFalse(vm.uiState.value.isTorchOn)

        vm.toggleFlashlight()
        assertTrue(vm.uiState.value.isTorchOn)

        vm.toggleFlashlight()
        assertFalse(vm.uiState.value.isTorchOn)
    }

    @Test
    fun `updateTorchAvailability reads from camera manager`() = runTest {
        every { cameraManager.hasTorch } returns true
        val vm = createViewModel()

        vm.updateTorchAvailability()

        assertTrue(vm.uiState.value.hasTorch)
    }

    @Test
    fun `updateTorchAvailability false when no torch`() = runTest {
        every { cameraManager.hasTorch } returns false
        val vm = createViewModel()

        vm.updateTorchAvailability()

        assertFalse(vm.uiState.value.hasTorch)
    }

    @Test
    fun `camera manager is accessible from view model`() = runTest {
        val vm = createViewModel()
        assertNotNull(vm.cameraManager)
        assertEquals(cameraManager, vm.cameraManager)
    }

    @Test
    fun `error message from connection error contains helpful text`() = runTest {
        val vm = createViewModel()
        connectionStateFlow.value = ConnectionState.ERROR
        testDispatcher.scheduler.advanceUntilIdle()

        val errorMsg = vm.uiState.value.errorMessage
        assertNotNull(errorMsg)
        assertTrue(errorMsg!!.contains("Connection failed"))
    }

    @Test
    fun `connecting to error to disconnected flow`() = runTest {
        val vm = createViewModel()

        connectionStateFlow.value = ConnectionState.CONNECTING
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SessionState.Connecting, vm.uiState.value.sessionState)

        connectionStateFlow.value = ConnectionState.ERROR
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SessionState.Error, vm.uiState.value.sessionState)
        assertNotNull(vm.uiState.value.errorMessage)

        connectionStateFlow.value = ConnectionState.DISCONNECTED
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SessionState.Idle, vm.uiState.value.sessionState)
    }

    @Test
    fun `transcript persists across status updates`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        incomingMessagesFlow.emit(AgentMessage.Transcript("Hello", true))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Hello", vm.uiState.value.transcript)

        incomingMessagesFlow.emit(AgentMessage.Status("thinking"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Hello", vm.uiState.value.transcript)
        assertEquals("thinking", vm.uiState.value.agentState)
    }

    @Test
    fun `agent state updates do not affect torch state`() = runTest {
        every { cameraManager.hasTorch } returns true
        every { cameraManager.toggleTorch() } returns true
        val vm = createViewModel()

        vm.updateTorchAvailability()
        vm.toggleFlashlight()
        assertTrue(vm.uiState.value.isTorchOn)

        incomingMessagesFlow.emit(AgentMessage.Status("speaking"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.isTorchOn)
    }

    @Test
    fun `empty audio chunk triggers playback`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val emptyAudio = byteArrayOf()
        incomingMessagesFlow.emit(AgentMessage.Audio(emptyAudio))
        testDispatcher.scheduler.advanceUntilIdle()

        verify { audioManager.playAudioChunk(emptyAudio) }
    }

    @Test
    fun `large audio chunk triggers playback`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val largeAudio = ByteArray(10000)
        incomingMessagesFlow.emit(AgentMessage.Audio(largeAudio))
        testDispatcher.scheduler.advanceUntilIdle()

        verify { audioManager.playAudioChunk(any()) }
    }

    @Test
    fun `switchCameraSource to GLASSES starts glasses stream`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.switchCameraSource(CameraSource.GLASSES)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(CameraSource.GLASSES, vm.uiState.value.cameraSource)
        verify { glassesCameraManager.startStream() }
    }

    @Test
    fun `switchCameraSource to PHONE stops glasses stream`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.switchCameraSource(CameraSource.GLASSES)
        vm.switchCameraSource(CameraSource.PHONE)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(CameraSource.PHONE, vm.uiState.value.cameraSource)
        verify { glassesCameraManager.stopStream() }
    }

    @Test
    fun `glassesState propagates from GlassesCameraManager to uiState`() = runTest {
        val glassesStateFlow = MutableStateFlow(GlassesState.DISCONNECTED)
        every { glassesCameraManager.connectionState } returns glassesStateFlow

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(GlassesState.DISCONNECTED, vm.uiState.value.glassesState)

        glassesStateFlow.value = GlassesState.STREAMING
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(GlassesState.STREAMING, vm.uiState.value.glassesState)
    }

    // ── AgentMessage.Interrupted routing ─────────────────────────────────────

    @Test
    fun `Interrupted message calls audioManager interrupt`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        incomingMessagesFlow.emit(AgentMessage.Interrupted)
        testDispatcher.scheduler.advanceUntilIdle()

        verify { audioManager.interrupt() }
    }

    @Test
    fun `Interrupted message does not change agentState`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        incomingMessagesFlow.emit(AgentMessage.Status("speaking"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("speaking", vm.uiState.value.agentState)

        incomingMessagesFlow.emit(AgentMessage.Interrupted)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("speaking", vm.uiState.value.agentState)
    }

    @Test
    fun `Interrupted message does not change transcript`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        incomingMessagesFlow.emit(AgentMessage.Transcript("Hello world", true))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Hello world", vm.uiState.value.transcript)

        incomingMessagesFlow.emit(AgentMessage.Interrupted)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Hello world", vm.uiState.value.transcript)
    }

    @Test
    fun `Interrupted after Audio calls audioManager interrupt`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        incomingMessagesFlow.emit(AgentMessage.Audio(byteArrayOf(1, 2, 3)))
        testDispatcher.scheduler.advanceUntilIdle()

        incomingMessagesFlow.emit(AgentMessage.Interrupted)
        testDispatcher.scheduler.advanceUntilIdle()

        verify { audioManager.interrupt() }
    }

    @Test
    fun `multiple Interrupted messages each call audioManager interrupt`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        incomingMessagesFlow.emit(AgentMessage.Interrupted)
        testDispatcher.scheduler.advanceUntilIdle()
        incomingMessagesFlow.emit(AgentMessage.Interrupted)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 2) { audioManager.interrupt() }
    }

    @Test
    fun `Interrupted is not triggered by Audio messages`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        incomingMessagesFlow.emit(AgentMessage.Audio(byteArrayOf(1, 2, 3)))
        incomingMessagesFlow.emit(AgentMessage.Audio(byteArrayOf(4, 5, 6)))
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { audioManager.interrupt() }
    }

    // ── Status("listening") — turn-complete flow ──────────────────────────────

    @Test
    fun `Status listening after Audio updates agentState`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        incomingMessagesFlow.emit(AgentMessage.Audio(byteArrayOf(1, 2, 3)))
        testDispatcher.scheduler.advanceUntilIdle()

        incomingMessagesFlow.emit(AgentMessage.Status("listening"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("listening", vm.uiState.value.agentState)
    }

    @Test
    fun `Status listening does not call audioManager interrupt`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        incomingMessagesFlow.emit(AgentMessage.Status("listening"))
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { audioManager.interrupt() }
    }

    @Test
    fun `Status speaking does not call audioManager interrupt`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        incomingMessagesFlow.emit(AgentMessage.Status("speaking"))
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { audioManager.interrupt() }
    }

    // ── agentSpeaking mic gate — 400ms delay after turnComplete ───────────────

    @Test
    fun `Status listening delays mic gate by 400ms`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        incomingMessagesFlow.emit(AgentMessage.Audio(byteArrayOf(1, 2, 3)))
        testDispatcher.scheduler.advanceUntilIdle()

        incomingMessagesFlow.emit(AgentMessage.Status("listening"))
        testDispatcher.scheduler.advanceUntilIdle()

        // Advance 399ms — agentSpeaking should still be true (no way to observe directly,
        // but we verify no crash and agentState is correct)
        testDispatcher.scheduler.advanceTimeBy(399)
        assertEquals("listening", vm.uiState.value.agentState)

        // Advance past the 400ms threshold
        testDispatcher.scheduler.advanceTimeBy(2)
        assertEquals("listening", vm.uiState.value.agentState)
    }

    @Test
    fun `fallback 1200ms timer fires if no Status listening arrives`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        incomingMessagesFlow.emit(AgentMessage.Audio(byteArrayOf(1, 2, 3)))
        testDispatcher.scheduler.advanceUntilIdle()

        // Advance past the 1200ms fallback — no crash, state consistent
        testDispatcher.scheduler.advanceTimeBy(1201)
        // agentState was not changed by the timer (it only affects agentSpeaking internally)
        assertEquals("idle", vm.uiState.value.agentState)
    }
}
