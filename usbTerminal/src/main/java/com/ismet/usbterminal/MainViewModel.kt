package com.ismet.usbterminal

import android.app.Application
import android.graphics.PointF
import android.os.Environment
import android.os.SystemClock
import android.preference.PreferenceManager
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ismet.usbterminal.data.*
import com.ismet.usbterminal.mainscreen.powercommands.PowerCommandsFactory
import com.ismet.usbterminal.utils.Utils
import com.ismet.usbterminalnew.R
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.xgouchet.texteditor.common.TextFileUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import javax.inject.Inject

const val CHART_INDEX_UNSELECTED = -1
private const val SEND_TEMPERATURE_OR_CO2_DELAY = 1000L
const val CO2_REQUEST = "(FE-44-00-08-02-9F-25)"
private const val DATE_FORMAT = "yyyyMMdd"
private const val TIME_FORMAT = "HHmmss"
private const val DELIMITER = "_"
private const val MAX_CHARTS = 3

val FORMATTER = SimpleDateFormat("${DATE_FORMAT}${DELIMITER}${TIME_FORMAT}")

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    handle: SavedStateHandle
): ViewModel() {
    private val cacheFilesDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val app = application as EToCApplication
    private val prefs = PreferenceManager.getDefaultSharedPreferences(app)
    val powerCommandsFactory: PowerCommandsFactory

    val events = Channel<MainEvent>(Channel.UNLIMITED)
    val charts = MutableLiveData(List(MAX_CHARTS) { Chart(it, emptyList()) })
    val temperatureShift = handle.getLiveData("temperatureShift", 0)
    val buttonOn1Properties = handle.getLiveData("buttonOn1", ButtonProperties.byButtonIndex(prefs, 0))
    val buttonOn2Properties = handle.getLiveData("buttonOn1", ButtonProperties.byButtonIndex(prefs, 1))
    val buttonOn3Properties = handle.getLiveData("buttonOn1", ButtonProperties.byButtonIndex(prefs, 2))
    val buttonOn4Properties = handle.getLiveData("buttonOn1", ButtonProperties.byButtonIndex(prefs, 3))
    val buttonOn5Properties = handle.getLiveData("buttonOn1", ButtonProperties.byButtonIndex(prefs, 4))
    val buttonOn6Properties = handle.getLiveData("buttonOn1", ButtonProperties.byButtonIndex(prefs, 5))
    //TODO there should be some logic behind one chart
    //val currentChartIndex
    val maxY = handle.getLiveData("maxY",0)
    private var lastTimePressed: Long = 0

    private var shouldSendTemperatureRequest = true
    private var readChartJob: Job? = null
    private var sendTemperatureOrCo2Job: Job? = null
    private var sendWaitForCoolingJob: Job? = null
    private var readCommandsJob: Job? = null
    var chartDate: String = ""
    var chartIdx = 0
    var subDirDate: String = ""

    init {
        val settingsFolder = File(Environment.getExternalStorageDirectory(), AppData.SYSTEM_SETTINGS_FOLDER_NAME)
        powerCommandsFactory = createPowerCommandsFactory(settingsFolder)
        events.offer(MainEvent.ShowToast(powerCommandsFactory.toString()))
        if (settingsFolder.exists()) readSettingsFolder(settingsFolder)
    }

    private fun createPowerCommandsFactory(settingsFolder: File): PowerCommandsFactory {
        val buttonPowerDataFile = File(settingsFolder, AppData.POWER_DATA)
        var powerData: String? = ""
        if (buttonPowerDataFile.exists()) {
            powerData = TextFileUtils.readTextFile(buttonPowerDataFile)
        }
        return app.parseCommands(powerData)
    }

    private fun readSettingsFolder(settingsFolder: File) {
        val button1DataFile = File(settingsFolder, AppData.BUTTON1_DATA)
        if (button1DataFile.exists()) {
            val button1Data = TextFileUtils.readTextFile(button1DataFile)
            if (!button1Data.isEmpty()) {
                val values = button1Data.split(AppData.SPLIT_STRING).toTypedArray()
                if (values.size == 4) {
                    val editor = prefs.edit()
                    editor.putString(PrefConstants.ON_NAME1, values[0])
                    editor.putString(PrefConstants.OFF_NAME1, values[1])
                    editor.putString(PrefConstants.ON1, values[2])
                    editor.putString(PrefConstants.OFF1, values[3])
                    editor.apply()
                }
            }
        }
        val button2DataFile = File(settingsFolder, AppData.BUTTON2_DATA)
        if (button2DataFile.exists()) {
            val button2Data = TextFileUtils.readTextFile(button2DataFile)
            if (!button2Data.isEmpty()) {
                val values = button2Data.split(AppData.SPLIT_STRING).toTypedArray()
                if (values.size == 4) {
                    val editor = prefs.edit()
                    editor.putString(PrefConstants.ON_NAME2, values[0])
                    editor.putString(PrefConstants.OFF_NAME2, values[1])
                    editor.putString(PrefConstants.ON2, values[2])
                    editor.putString(PrefConstants.OFF2, values[3])
                    editor.apply()
                }
            }
        }
        val button3DataFile = File(settingsFolder, AppData.BUTTON3_DATA)
        if (button3DataFile.exists()) {
            val button3Data = TextFileUtils.readTextFile(button3DataFile)
            if (!button3Data.isEmpty()) {
                val values = button3Data.split(AppData.SPLIT_STRING).toTypedArray()
                if (values.size == 4) {
                    val editor = prefs.edit()
                    editor.putString(PrefConstants.ON_NAME3, values[0])
                    editor.putString(PrefConstants.OFF_NAME3, values[1])
                    editor.putString(PrefConstants.ON3, values[2])
                    editor.putString(PrefConstants.OFF3, values[3])
                    editor.apply()
                }
            }
        }
        val temperatureShiftFolder = File(settingsFolder, AppData.TEMPERATURE_SHIFT_FILE)
        if (temperatureShiftFolder.exists()) {
            val temperatureData = TextFileUtils.readTextFile(temperatureShiftFolder)
            if (temperatureData.isNotEmpty()) {
                temperatureShift.value = try {
                    temperatureData.toInt()
                } catch (e: NumberFormatException) {
                    0
                }
            }
        }
        val measureDefaultFilesFile = File(settingsFolder, AppData.MEASURE_DEFAULT_FILES)
        if (measureDefaultFilesFile.exists()) {
            val measureFilesData = TextFileUtils.readTextFile(measureDefaultFilesFile)
            if (measureFilesData.isNotEmpty()) {
                val values = measureFilesData.split(AppData.SPLIT_STRING).toTypedArray()
                if (values.size == 3) {
                    val editor = prefs.edit()
                    editor.putString(
                        PrefConstants.MEASURE_FILE_NAME1,
                        values[0]
                    )
                    editor.putString(
                        PrefConstants.MEASURE_FILE_NAME2,
                        values[1]
                    )
                    editor.putString(
                        PrefConstants.MEASURE_FILE_NAME3,
                        values[2]
                    )
                    editor.apply()
                }
            }
        }
    }

    fun readChart(filePath: String) {
        readChartJob?.cancel()
        val currentCharts = charts.value!!.toMutableList()
        val newChartIndex = currentCharts.indexOfFirst { it.canBeRestoredFromFilePath(filePath) }

        if (newChartIndex == CHART_INDEX_UNSELECTED) {
            // events.offer(MainEvent.ShowToast("Required Log files not available"))
            readChartJob = null
            return
        }

        readChartJob = viewModelScope.launch(Dispatchers.IO) {
            val file = File(filePath)
            val lines = file.readLines()
            var startX = maxOf(1, (lines.size + 1) * currentCharts[newChartIndex].id)
            var newMaxY = maxY.value!!
            val newChartPoints = mutableListOf<PointF>()
            for (line in lines) {
                if (line.isNotEmpty()) {
                    val arr = line.split(",").toTypedArray()
                    val co2 = arr[1].toDouble()
                    startX++
                    if (co2 >= newMaxY) {
                        newMaxY = if (newChartPoints.isEmpty()) {
                            (3 * co2).toInt()
                        } else {
                            (co2 + co2 * 15 / 100f).toInt()
                        }
                    }
                    newChartPoints.add(PointF(startX.toFloat(), co2.toFloat()))
                    delay(50)
                    maxY.postValue(newMaxY)
                    currentCharts[newChartIndex] = currentCharts[newChartIndex].copy(points = newChartPoints)
                    charts.postValue(currentCharts)
                }
            }
            events.send(MainEvent.ShowToast("File reading done"))
        }
    }

    fun startSendingTemperatureOrCo2Requests() {
        sendTemperatureOrCo2Job?.cancel()
        sendTemperatureOrCo2Job = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                shouldSendTemperatureRequest = !shouldSendTemperatureRequest
                if (shouldSendTemperatureRequest) {
                    events.send(MainEvent.WriteToUsb("/5J5R"))
                    delay(350)
                    events.send(MainEvent.WriteToUsb(app.currentTemperatureRequest))
                } else {
                    events.send(MainEvent.WriteToUsb(CO2_REQUEST))
                }
                delay(SEND_TEMPERATURE_OR_CO2_DELAY)
            }
        }
    }

    fun stopSendingTemperatureOrCo2Requests() {
        sendTemperatureOrCo2Job?.cancel()
        sendTemperatureOrCo2Job = null
    }

    fun waitForCooling() {
        val commandsFactory: PowerCommandsFactory = app.powerCommandsFactory

        val command = if (app.isPreLooping) {
            PowerCommand("/5J1R", 1000)
        } else {
            commandsFactory.currentCommand()!!
        }

        sendWaitForCoolingJob?.cancel()
        sendWaitForCoolingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val message = if (app.isPreLooping) {
                    command.command
                } else {
                    "/5H0000R"
                }
                val state = commandsFactory.currentPowerState()
                if (state != PowerState.OFF) {
                    events.send(MainEvent.WriteToUsb(message))
                }
                delay((0.3 * command.delay).toLong())
                delay(command.delay)
            }
        }
    }

    fun stopWaitForCooling() {
        sendWaitForCoolingJob?.cancel()
        sendWaitForCoolingJob = null
    }

    fun readCommandsFromFile(file: File, shouldUseRecentDirectory: Boolean, runningTime: Long, oneLoopTime: Long) {
        readCommandsFromText(TextFileUtils.readTextFile(file), shouldUseRecentDirectory, runningTime, oneLoopTime)
    }

    fun readCommandsFromText(text: String?, shouldUseRecentDirectory: Boolean, runningTime: Long, oneLoopTime: Long) {
        if (text != null && text.isNotEmpty()) {
            stopSendingTemperatureOrCo2Requests()
            val commands: Array<String>
            val delimiter = "\n"
            commands = text.split(delimiter).toTypedArray()
            val simpleCommands: MutableList<String> = ArrayList()
            val loopCommands: MutableList<String> = ArrayList()
            var isLoop = false
            var loopcmd1Idx = -1
            var loopcmd2Idx = -1
            var autoPpm = false
            for (commandIndex in commands.indices) {
                val command = commands[commandIndex]
                if (command != "" && command != "\n") {
                    if (command.contains("loop")) {
                        isLoop = true
                        var lineNos = command.replace("loop", "")
                        lineNos = lineNos.replace("\n", "")
                        lineNos = lineNos.replace("\r", "")
                        lineNos = lineNos.trim { it <= ' ' }
                        val line1 = lineNos.substring(
                            0, lineNos.length
                                    / 2
                        )
                        val line2 = lineNos.substring(
                            lineNos.length / 2,
                            lineNos.length
                        )
                        loopcmd1Idx = line1.toInt() - 1
                        loopcmd2Idx = line2.toInt() - 1
                    } else if (command == "autoppm") {
                        autoPpm = true
                    } else if (isLoop) {
                        if (commandIndex == loopcmd1Idx) {
                            loopCommands.add(command)
                        } else if (commandIndex == loopcmd2Idx) {
                            loopCommands.add(command)
                            isLoop = false
                        }
                    } else {
                        simpleCommands.add(command)
                    }
                }
            }
            readCommandsJob?.cancel()
            readCommandsJob = viewModelScope.launch(Dispatchers.IO) {
                delay(300)
                if (shouldUseRecentDirectory) {
                    val ppm = prefs.getInt(PrefConstants.KPPM, -1)
                    //cal directory
                    if (ppm != -1) {
                        val directory = File(Environment.getExternalStorageDirectory(), AppData.CAL_FOLDER_NAME)
                        val directoriesInside = directory.listFiles { pathname -> pathname.isDirectory }
                        if (directoriesInside != null && directoriesInside.isNotEmpty()) {
                            var recentDir: File? = null
                            for (dir in directoriesInside) {
                                if (recentDir == null || dir.lastModified() > recentDir.lastModified()) {
                                    recentDir = dir
                                }
                            }
                            val name = recentDir!!.name
                            val tokenizer = StringTokenizer(name, DELIMITER)
                            tokenizer.nextToken()
                            // format of directory name is:
                            // MES/CAL + ${DELIMITER} + ${DATE_FORMAT} + ${DELIMITER} + ${TIME_FORMAT} +
                            // ${DELIMITER} + ${USER_COMMENT}
                            val date = tokenizer.nextToken()
                            val time = tokenizer.nextToken()
                            subDirDate = date + DELIMITER + time
                        }
                    }
                }
                val isAuto = prefs.getBoolean(PrefConstants.IS_AUTO, false)
                if (isAuto) {
                    repeat(3) {
                        processChart(runningTime, oneLoopTime, simpleCommands, loopCommands)
                    }
                } else {
                    processChart(runningTime, oneLoopTime, simpleCommands, loopCommands)
                }

                if (isAuto && autoPpm && !prefs.getBoolean(PrefConstants.SAVE_AS_CALIBRATION, false)) {
                    events.send(MainEvent.InvokeAutoCalculations)
                }

                startSendingTemperatureOrCo2Requests()
                events.send(MainEvent.UpdateTimerRunning(false))
                events.send(MainEvent.ShowToast("Timer Stopped"))
            }
        } else {
            events.offer(MainEvent.ShowToast("File not found"))
        }
    }

    private suspend fun processChart(future: Long, delay: Long, simpleCommands: List<String>, loopCommands: List<String>) {
        for (i in simpleCommands.indices) {
            if (simpleCommands[i].contains("delay")) {
                val delayC = simpleCommands[i].replace("delay", "").trim { it <= ' ' }.toLong()
                delay(delayC)
            } else {
                events.send(MainEvent.WriteToUsb(simpleCommands[i]))
            }
        }
        events.send(MainEvent.UpdateTimerRunning(true))

        val len = future / delay
        var count: Long = 0
        if (loopCommands.isNotEmpty()) {
            while (count < len) {
                events.send(MainEvent.IncReadingCount)
                events.send(MainEvent.WriteToUsb(loopCommands[0]))
                val half_delay = delay / 2
                delay(half_delay)
                if (loopCommands.size > 1) {
                    events.send(MainEvent.WriteToUsb(loopCommands[1]))
                    delay(half_delay)
                }

                count++
            }
        }
    }

    fun cacheBytesFromUsb(bytes: ByteArray) = viewModelScope.launch {
        withContext(cacheFilesDispatcher) {
            val strH = String.format(
                "%02X%02X", bytes[3],
                bytes[4]
            )
            val co2 = strH.toInt(16)
            val ppm: Int = prefs.getInt(PrefConstants.KPPM, -1)
            val volumeValue: Int = prefs.getInt(PrefConstants.VOLUME, -1)
            val volume = "_" + if (volumeValue == -1) "" else "" +
                    volumeValue
            val ppmPrefix = if (ppm == -1) {
                "_"
            } else {
                "_$ppm"
            }
            val str_uc: String = prefs.getString(PrefConstants.USER_COMMENT, "")!!
            val fileName: String
            val dirName: String
            val subDirName: String
            if (ppmPrefix == "_") {
                dirName = AppData.MES_FOLDER_NAME
                fileName = "MES_" + chartDate +
                        volume + "_R" + chartIdx + "" +
                        ".csv"
                subDirName = "MES_" + subDirDate + "_" +
                        str_uc
            } else {
                dirName = AppData.CAL_FOLDER_NAME
                fileName = ("CAL_" + chartDate +
                        volume + ppmPrefix + "_R" + chartIdx
                        + ".csv")
                subDirName = "CAL_" + subDirDate + "_" +
                        str_uc
            }
            try {
                var dir = File(Environment.getExternalStorageDirectory(), dirName)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                dir = File(dir, subDirName)
                if (!dir.exists()) {
                    dir.mkdir()
                }
                val formatter = SimpleDateFormat("mm:ss.S", Locale.ENGLISH)
                val file = File(dir, fileName)
                if (!file.exists()) {
                    file.createNewFile()
                }
                val preFormattedTime = formatter.format(Date())
                val arr = preFormattedTime.split("\\.").toTypedArray()
                var formattedTime = ""
                if (arr.size == 1) {
                    formattedTime = arr[0] + ".0"
                } else if (arr.size == 2) {
                    formattedTime = arr[0] + "." + arr[1].substring(0, 1)
                }
                val fos = FileOutputStream(file, true)
                val writer = BufferedWriter(OutputStreamWriter(fos))
                writer.write("$formattedTime,$co2\n")
                writer.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onButton1Click() {
        onClick(0)
    }

    private fun onClick(index: Int) {
        if (powerCommandsFactory.currentPowerState() != PowerState.ON) {
            return
        }
        val command = onPrePullStopped(index)
        val nowTime = SystemClock.uptimeMillis()
        val timeElapsed = Utils.elapsedTimeForSendRequest(nowTime, lastTimePressed)
        if (timeElapsed) {
            lastTimePressed = nowTime
            stopSendingTemperatureOrCo2Requests()
        }
        events.offer(MainEvent.WriteToUsb(command))
        if (timeElapsed) {
            viewModelScope.launch(Dispatchers.IO) {
                delay(1000)
                if (powerCommandsFactory.currentPowerState() == PowerState.ON) {
                    startSendingTemperatureOrCo2Requests()
                }
                onPostPullStarted(index)
            }
        }
    }

    private fun onPrePullStopped(index: Int): String {
        val buttonProperties = when(index) {
            0 -> buttonOn1Properties
            1 -> buttonOn2Properties
            2 -> buttonOn3Properties
            3 -> buttonOn4Properties
            4 -> buttonOn5Properties
            5 -> buttonOn6Properties
            else -> {
                return ""
            }
        }

        val isActivated = buttonProperties.value!!.isActivated
        val alpha = when {
            isActivated && index.isHighlighteable() -> 0.6f
            else -> buttonProperties.value!!.alpha
        }

        val command = when(index) {
            0, 3 -> {
                var command = "" //"/5H1000R";
                if (!isActivated) {
                    command = prefs.getString(PrefConstants.ON1, "")!!
                } else {
                    command = prefs.getString(PrefConstants.OFF1, "")!!
                }
                command
            }
            1, 4 -> {
                var command = "" //"/5H1000R";
                val defaultValue: String
                val prefName: String
                if (!isActivated) {
                    prefName = PrefConstants.OFF2
                    defaultValue = "/5H0000R"
                    command = prefs.getString(PrefConstants.ON2, "")!!
                } else {
                    prefName = PrefConstants.ON2
                    defaultValue = "/5H750R"
                    command = prefs.getString(PrefConstants.OFF2, "")!!
                }
                app.currentTemperatureRequest = prefs.getString(prefName, defaultValue)
                command
            }
            2, 5 -> {
                if (!isActivated) {
                    prefs.getString(PrefConstants.ON3, "")!!
                } else {
                    prefs.getString(PrefConstants.OFF3, "")!!
                }
            }
            else -> ""
        }

        buttonProperties.value = buttonProperties.value!!.copy(isActivated = !isActivated, alpha = alpha)
        return command
    }

    private fun Int.isHighlighteable() = this < 3

    private fun onPostPullStarted(index: Int) {
        if (index.isHighlighteable()) {
            val buttonProperties = when(index) {
                0 -> buttonOn1Properties
                1 -> buttonOn2Properties
                2 -> buttonOn3Properties
                else -> return
            }
            val isActivated = buttonProperties.value!!.isActivated
            val background = if (!isActivated) R.drawable.button_drawable else R.drawable.power_on_drawable
            buttonProperties.value = buttonProperties.value!!.copy(alpha = 1f, background = background)
        }
    }

    // true if success, false otherwise
    fun changeButton1PersistedInfo(persistedInfo: PersistedInfo): Boolean = persistedInfo.isValid().apply {
        if (this) {
            val edit = prefs.edit()
            edit.putString(PrefConstants.ON1, persistedInfo.command)
            edit.putString(PrefConstants.OFF1, persistedInfo.activatedCommand)
            edit.putString(PrefConstants.ON_NAME1, persistedInfo.text)
            edit.putString(PrefConstants.OFF_NAME1, persistedInfo.activatedText)
            edit.apply()
            buttonOn1Properties.value = buttonOn1Properties.value!!.copy(
                text = persistedInfo.text,
                command = persistedInfo.command,
                activatedText = persistedInfo.activatedText,
                activatedCommand = persistedInfo.activatedCommand
            )
            buttonOn4Properties.value = buttonOn4Properties.value!!.copy(
                text = persistedInfo.text,
                command = persistedInfo.command,
                activatedText = persistedInfo.activatedText,
                activatedCommand = persistedInfo.activatedCommand
            )
        }
    }

    fun onButton2Click() {
        onClick(1)
    }

    fun changeButton2PersistedInfo(persistedInfo: PersistedInfo): Boolean = persistedInfo.isValid().apply {
        if (this) {
            val edit = prefs.edit()
            edit.putString(PrefConstants.ON2, persistedInfo.command)
            edit.putString(PrefConstants.OFF2, persistedInfo.activatedCommand)
            edit.putString(PrefConstants.ON_NAME2, persistedInfo.text)
            edit.putString(PrefConstants.OFF_NAME2, persistedInfo.activatedText)
            edit.apply()
            val isActivated = buttonOn2Properties.value!!.isActivated
            val defaultValue: String
            val prefName: String
            if (!isActivated) {
                prefName = PrefConstants.ON2
                defaultValue = "/5H750R"
            } else {
                prefName = PrefConstants.OFF2
                defaultValue = "/5H0000R"
            }
            EToCApplication.getInstance().currentTemperatureRequest = prefs.getString(prefName, defaultValue)

            buttonOn2Properties.value = buttonOn2Properties.value!!.copy(
                text = persistedInfo.text,
                command = persistedInfo.command,
                activatedText = persistedInfo.activatedText,
                activatedCommand = persistedInfo.activatedCommand
            )
            buttonOn5Properties.value = buttonOn5Properties.value!!.copy(
                text = persistedInfo.text,
                command = persistedInfo.command,
                activatedText = persistedInfo.activatedText,
                activatedCommand = persistedInfo.activatedCommand
            )
        }
    }

    fun onButton3Click() {
        onClick(2)
    }

    fun changeButton3PersistedInfo(persistedInfo: PersistedInfo): Boolean = persistedInfo.isValid().apply {
        if (this) {
            val edit = prefs.edit()
            edit.putString(PrefConstants.ON3, persistedInfo.command)
            edit.putString(PrefConstants.OFF3, persistedInfo.activatedCommand)
            edit.putString(PrefConstants.ON_NAME3, persistedInfo.text)
            edit.putString(PrefConstants.OFF_NAME3, persistedInfo.activatedText)
            edit.apply()
            buttonOn3Properties.value = buttonOn3Properties.value!!.copy(
                text = persistedInfo.text,
                command = persistedInfo.command,
                activatedText = persistedInfo.activatedText,
                activatedCommand = persistedInfo.activatedCommand
            )
            buttonOn6Properties.value = buttonOn6Properties.value!!.copy(
                text = persistedInfo.text,
                command = persistedInfo.command,
                activatedText = persistedInfo.activatedText,
                activatedCommand = persistedInfo.activatedCommand
            )
        }
    }

    fun onButton4Click() {
        onClick(3)
    }

    fun changeButton4PersistedInfo(persistedInfo: PersistedInfo): Boolean = persistedInfo.isValid().apply {
        if (this) {
            val edit = prefs.edit()
            edit.putString(PrefConstants.ON1, persistedInfo.command)
            edit.putString(PrefConstants.OFF1, persistedInfo.activatedCommand)
            edit.putString(PrefConstants.ON_NAME1, persistedInfo.text)
            edit.putString(PrefConstants.OFF_NAME1, persistedInfo.activatedText)
            edit.apply()
            buttonOn1Properties.value = buttonOn1Properties.value!!.copy(
                text = persistedInfo.text,
                command = persistedInfo.command,
                activatedText = persistedInfo.activatedText,
                activatedCommand = persistedInfo.activatedCommand
            )
            buttonOn4Properties.value = buttonOn4Properties.value!!.copy(
                text = persistedInfo.text,
                command = persistedInfo.command,
                activatedText = persistedInfo.activatedText,
                activatedCommand = persistedInfo.activatedCommand
            )
        }
    }

    fun onButton5Click() {
        onClick(4)
    }

    fun changeButton5PersistedInfo(persistedInfo: PersistedInfo): Boolean = persistedInfo.isValid().apply {
        if (this) {
            val edit = prefs.edit()
            edit.putString(PrefConstants.ON2, persistedInfo.command)
            edit.putString(PrefConstants.OFF2, persistedInfo.activatedCommand)
            edit.putString(PrefConstants.ON_NAME2, persistedInfo.text)
            edit.putString(PrefConstants.OFF_NAME2, persistedInfo.activatedText)
            edit.apply()
            val isActivated = buttonOn2Properties.value!!.isActivated
            val defaultValue: String
            val prefName: String
            if (!isActivated) {
                prefName = PrefConstants.ON2
                defaultValue = "/5H750R"
            } else {
                prefName = PrefConstants.OFF2
                defaultValue = "/5H0000R"
            }
            EToCApplication.getInstance().currentTemperatureRequest = prefs.getString(prefName, defaultValue)

            buttonOn2Properties.value = buttonOn2Properties.value!!.copy(
                text = persistedInfo.text,
                command = persistedInfo.command,
                activatedText = persistedInfo.activatedText,
                activatedCommand = persistedInfo.activatedCommand
            )
            buttonOn5Properties.value = buttonOn5Properties.value!!.copy(
                text = persistedInfo.text,
                command = persistedInfo.command,
                activatedText = persistedInfo.activatedText,
                activatedCommand = persistedInfo.activatedCommand
            )
        }
    }

    fun onButton6Click() {
        onClick(5)
    }

    fun changeButton6PersistedInfo(persistedInfo: PersistedInfo): Boolean = persistedInfo.isValid().apply {
        if (this) {
            val edit = prefs.edit()
            edit.putString(PrefConstants.ON3, persistedInfo.command)
            edit.putString(PrefConstants.OFF3, persistedInfo.activatedCommand)
            edit.putString(PrefConstants.ON_NAME3, persistedInfo.text)
            edit.putString(PrefConstants.OFF_NAME3, persistedInfo.activatedText)
            edit.apply()
            buttonOn3Properties.value = buttonOn3Properties.value!!.copy(
                text = persistedInfo.text,
                command = persistedInfo.command,
                activatedText = persistedInfo.activatedText,
                activatedCommand = persistedInfo.activatedCommand
            )
            buttonOn6Properties.value = buttonOn6Properties.value!!.copy(
                text = persistedInfo.text,
                command = persistedInfo.command,
                activatedText = persistedInfo.activatedText,
                activatedCommand = persistedInfo.activatedCommand
            )
        }
    }

    fun onSendClick() {
        if (powerCommandsFactory.currentPowerState() != PowerState.ON) {
            return
        }
        val nowTime = SystemClock.uptimeMillis()
        val timeElapsed = Utils.elapsedTimeForSendRequest(nowTime, lastTimePressed)
        if (timeElapsed) {
            lastTimePressed = nowTime
            stopSendingTemperatureOrCo2Requests()
        }
        events.offer(MainEvent.SendMessage)
    }
}