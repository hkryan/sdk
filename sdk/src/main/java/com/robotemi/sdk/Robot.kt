package com.robotemi.sdk

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import androidx.annotation.CheckResult
import androidx.annotation.RestrictTo
import androidx.annotation.RestrictTo.Scope.LIBRARY
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import com.robotemi.sdk.activitystream.ActivityStreamObject
import com.robotemi.sdk.activitystream.ActivityStreamPublishMessage
import com.robotemi.sdk.activitystream.ActivityStreamUtils
import com.robotemi.sdk.constants.SdkConstants
import com.robotemi.sdk.exception.OnSdkExceptionListener
import com.robotemi.sdk.exception.SdkException
import com.robotemi.sdk.face.ContactModel
import com.robotemi.sdk.face.OnFaceRecognizedListener
import com.robotemi.sdk.listeners.*
import com.robotemi.sdk.map.MapDataModel
import com.robotemi.sdk.mediabar.AidlMediaBarController
import com.robotemi.sdk.mediabar.MediaBarData
import com.robotemi.sdk.model.CallEventModel
import com.robotemi.sdk.model.DetectionData
import com.robotemi.sdk.model.RecentCallModel
import com.robotemi.sdk.navigation.listener.OnCurrentPositionChangedListener
import com.robotemi.sdk.navigation.listener.OnDistanceToLocationChangedListener
import com.robotemi.sdk.navigation.model.Position
import com.robotemi.sdk.navigation.model.SafetyLevel
import com.robotemi.sdk.navigation.model.SpeedLevel
import com.robotemi.sdk.notification.AlertNotification
import com.robotemi.sdk.notification.NormalNotification
import com.robotemi.sdk.notification.NotificationCallback
import com.robotemi.sdk.permission.OnRequestPermissionResultListener
import com.robotemi.sdk.permission.Permission
import com.robotemi.sdk.sequence.OnSequencePlayStatusChangedListener
import com.robotemi.sdk.sequence.SequenceModel
import com.robotemi.sdk.telepresence.CallState
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.collections.HashMap

@SuppressWarnings("unused")
class Robot private constructor(context: Context) {

    private val applicationInfo: ApplicationInfo

    private val uiHandler = Handler(Looper.getMainLooper())

    private var mediaBar = AidlMediaBarController(null)

    private var mediaButtonListener: MediaButtonListener? = null

    private var sdkService: ISdkService? = null

    private val listenersMap = HashMap<String, NotificationListener>()

    private val conversationViewAttachesListeners =
        CopyOnWriteArraySet<ConversationViewAttachesListener>()

    private val ttsListeners = CopyOnWriteArraySet<TtsListener>()

    private val asrListeners = CopyOnWriteArraySet<AsrListener>()

    private val nlpListeners = CopyOnWriteArraySet<NlpListener>()

    private val wakeUpWordListeners = CopyOnWriteArraySet<WakeupWordListener>()

    private val onRobotReadyListeners = HashSet<OnRobotReadyListener>()

    private val onBeWithMeStatusChangeListeners =
        CopyOnWriteArraySet<OnBeWithMeStatusChangedListener>()

    private val onGoToLocationStatusChangeListeners =
        CopyOnWriteArraySet<OnGoToLocationStatusChangedListener>()

    private val onTelepresenceStatusChangedListeners =
        CopyOnWriteArraySet<OnTelepresenceStatusChangedListener>()

    private val onTelepresenceEventChangedListener =
        CopyOnWriteArraySet<OnTelepresenceEventChangedListener>()

    private val onLocationsUpdatedListeners = CopyOnWriteArraySet<OnLocationsUpdatedListener>()

    private val onUsersUpdatedListeners = CopyOnWriteArraySet<OnUsersUpdatedListener>()

    private val onBatteryStatusChangedListeners =
        CopyOnWriteArraySet<OnBatteryStatusChangedListener>()

    private val onPrivacyModeStateChangedListeners =
        CopyOnWriteArraySet<OnPrivacyModeChangedListener>()

    private val onConstraintBeWithStatusChangedListeners =
        CopyOnWriteArraySet<OnConstraintBeWithStatusChangedListener>()

    private val onUserInteractionChangedListeners =
        CopyOnWriteArraySet<OnUserInteractionChangedListener>()

    private val onDetectionStateChangedListeners =
        CopyOnWriteArraySet<OnDetectionStateChangedListener>()

    private val onRequestPermissionResultListeners =
        CopyOnWriteArraySet<OnRequestPermissionResultListener>()

    private val onDistanceToLocationChangedListeners =
        CopyOnWriteArraySet<OnDistanceToLocationChangedListener>()

    private val onCurrentPositionChangedListeners =
        CopyOnWriteArraySet<OnCurrentPositionChangedListener>()

    private val onSequencePlayStatusChangedListeners =
        CopyOnWriteArraySet<OnSequencePlayStatusChangedListener>()

    private val onRobotLiftedListeners = CopyOnWriteArraySet<OnRobotLiftedListener>()

    private val onDetectionDataChangedListeners =
        CopyOnWriteArraySet<OnDetectionDataChangedListener>()

    private val onFaceRecognizedListeners = CopyOnWriteArraySet<OnFaceRecognizedListener>()

    private val onSdkExceptionListeners = CopyOnWriteArraySet<OnSdkExceptionListener>()

    private var activityStreamPublishListener: ActivityStreamPublishListener? = null

    init {
        val appContext = context.applicationContext
        val packageName = appContext.packageName
        val packageManager = appContext.packageManager
        try {
            this.applicationInfo =
                packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        } catch (e: PackageManager.NameNotFoundException) {
            throw RuntimeException(e)
        }
    }

    private val sdkServiceCallback = object : ISdkServiceCallback.Stub() {

        /*****************************************/
        /*                 Voice                 */
        /*****************************************/

        override fun onTtsStatusChanged(ttsRequest: TtsRequest): Boolean {
            if (ttsListeners.isEmpty()) return false
            uiHandler.post {
                for (ttsListener in ttsListeners) {
                    ttsListener.onTtsStatusChanged(ttsRequest)
                }
            }
            return true
        }

        override fun onWakeupWord(wakeupWord: String, direction: Int): Boolean {
            if (wakeUpWordListeners.isEmpty()) return false
            uiHandler.post {
                for (wakeupWordListener in wakeUpWordListeners) {
                    wakeupWordListener.onWakeupWord(wakeupWord, direction)
                }
            }
            return true
        }

        override fun onNlpCompleted(nlpResult: NlpResult): Boolean {
            if (nlpListeners.isEmpty()) return false
            uiHandler.post {
                for (nlpListener in nlpListeners) {
                    nlpListener.onNlpCompleted(nlpResult)
                }
            }
            return true
        }

        override fun onAsrResult(asrText: String): Boolean {
            if (asrListeners.isEmpty()) return false
            uiHandler.post {
                for (asrListener in asrListeners) {
                    asrListener.onAsrResult(asrText)
                }
            }
            return true
        }

        override fun onConversationViewAttaches(isAttached: Boolean): Boolean {
            if (conversationViewAttachesListeners.isEmpty()) return false
            uiHandler.post {
                for (conversationViewAttachesListener in conversationViewAttachesListeners) {
                    conversationViewAttachesListener.onConversationAttaches(isAttached)
                }
            }
            return true
        }

        override fun hasActiveNlpListeners(): Boolean {
            return nlpListeners.isNotEmpty()
        }

        /*****************************************/
        /*               Navigation              */
        /*****************************************/

        override fun onGoToLocationStatusChanged(
            location: String,
            status: String,
            descriptionId: Int,
            description: String
        ): Boolean {
            if (onGoToLocationStatusChangeListeners.isEmpty()) return false
            uiHandler.post {
                for (listener in onGoToLocationStatusChangeListeners) {
                    listener.onGoToLocationStatusChanged(
                        location,
                        status,
                        descriptionId,
                        description
                    )
                }
            }
            return true
        }

        override fun onLocationsUpdated(locations: List<String>): Boolean {
            if (onLocationsUpdatedListeners.isEmpty()) return false
            uiHandler.post {
                for (listener in onLocationsUpdatedListeners) {
                    listener.onLocationsUpdated(locations)
                }
            }
            return true
        }

        override fun onDistanceToLocationChanged(distances: MutableMap<Any?, Any?>): Boolean {
            if (onDistanceToLocationChangedListeners.isEmpty()) return false
            val distancesMap: Map<String, Float> = distances as Map<String, Float>
            uiHandler.post {
                for (listener in onDistanceToLocationChangedListeners) {
                    listener.onDistanceToLocationChanged(distancesMap)
                }
            }
            return true
        }

        override fun onCurrentPositionChanged(position: Position): Boolean {
            if (onCurrentPositionChangedListeners.isEmpty()) return false
            uiHandler.post {
                for (listener in onCurrentPositionChangedListeners) {
                    listener.onCurrentPositionChanged(position)
                }
            }
            return true
        }

        /*****************************************/
        /*            Movement & Follow          */
        /*****************************************/

        override fun onBeWithMeStatusChanged(status: String): Boolean {
            if (onBeWithMeStatusChangeListeners.isEmpty()) return false
            uiHandler.post {
                for (listener in onBeWithMeStatusChangeListeners) {
                    listener.onBeWithMeStatusChanged(status)
                }
            }
            return true
        }

        override fun onConstraintBeWithStatusChanged(isContraint: Boolean): Boolean {
            if (onConstraintBeWithStatusChangedListeners.isEmpty()) return false
            uiHandler.post {
                for (listener in onConstraintBeWithStatusChangedListeners) {
                    listener.onConstraintBeWithStatusChanged(isContraint)
                }
            }
            return true
        }

        override fun onRobotLifted(isRobotLifted: Boolean): Boolean {
            if (onRobotLiftedListeners.isEmpty()) return false
            uiHandler.post {
                for (listener in onRobotLiftedListeners) {
                    listener.onRobotLifted(isRobotLifted)
                }
            }
            return true
        }

        /*****************************************/
        /*           Users & Telepresence        */
        /*****************************************/

        override fun onTelepresenceStatusChanged(callState: CallState): Boolean {
            if (onTelepresenceStatusChangedListeners.isEmpty()) return false
            uiHandler.post {
                for (listener in onTelepresenceStatusChangedListeners) {
                    if (listener != null && callState.sessionId == listener.sessionId) {
                        listener.onTelepresenceStatusChanged(callState)
                    }
                }
            }
            return true
        }

        override fun onUserUpdated(user: UserInfo): Boolean {
            if (onUsersUpdatedListeners.isEmpty()) return false
            uiHandler.post {
                for (listener in onUsersUpdatedListeners) {
                    val isValidListener = listener != null && (listener.userIds == null
                            || listener.userIds!!.isEmpty()
                            || listener.userIds!!.contains(user.userId))
                    if (isValidListener) {
                        listener!!.onUserUpdated(user)
                    }
                }
            }
            return true
        }

        override fun onTelepresenceEventChanged(callEventModel: CallEventModel): Boolean {
            if (onTelepresenceEventChangedListener.isEmpty()) return false
            uiHandler.post {
                for (listener in onTelepresenceEventChangedListener) {
                    listener.onTelepresenceEventChanged(callEventModel)
                }
            }
            return true
        }

        /*****************************************/
        /*                 System                */
        /*****************************************/

        override fun onNotificationBtnClicked(notificationCallback: NotificationCallback) {
            uiHandler.post {
                val notificationListener = listenersMap[notificationCallback.notificationId]
                if (notificationListener != null) {
                    notificationListener.onNotificationBtnClicked(notificationCallback.event)
                    listenersMap.remove(notificationCallback.notificationId)
                }
            }
        }

        override fun onPrivacyModeStateChanged(state: Boolean): Boolean {
            if (onPrivacyModeStateChangedListeners.isEmpty()) return false
            uiHandler.post {
                for (listener in onPrivacyModeStateChangedListeners) {
                    listener.onPrivacyModeChanged(state)
                }
            }
            return true
        }

        override fun onBatteryStatusChanged(batteryData: BatteryData): Boolean {
            if (onBatteryStatusChangedListeners.isEmpty()) return false
            uiHandler.post {
                for (listener in onBatteryStatusChangedListeners) {
                    listener.onBatteryStatusChanged(batteryData)
                }
            }
            return true
        }

        /*****************************************/
        /*               Permission              */
        /*****************************************/

        override fun onRequestPermissionResult(
            permission: String,
            grantResult: Int,
            requestCode: Int
        ): Boolean {
            if (onRequestPermissionResultListeners.isEmpty()) return false
            uiHandler.post {
                for (listener in onRequestPermissionResultListeners) {
                    listener.onRequestPermissionResult(
                        Permission.valueToEnum(permission),
                        grantResult,
                        requestCode
                    )
                }
            }
            return true
        }

        /*****************************************/
        /*            Activity Stream            */
        /*****************************************/

        override fun onActivityStreamPublish(message: ActivityStreamPublishMessage) {
            if (activityStreamPublishListener != null) {
                activityStreamPublishListener!!.onPublish(message)
            }
        }

        /*****************************************/
        /*                 Media                 */
        /*****************************************/

        override fun onPlayButtonClicked(play: Boolean) {
            uiHandler.post {
                if (mediaButtonListener != null) {
                    mediaButtonListener!!.onPlayButtonClicked(play)
                }
            }
        }

        override fun onNextButtonClicked() {
            uiHandler.post {
                if (mediaButtonListener != null) {
                    mediaButtonListener!!.onNextButtonClicked()
                }
            }
        }

        override fun onBackButtonClicked() {
            uiHandler.post {
                if (mediaButtonListener != null) {
                    mediaButtonListener!!.onBackButtonClicked()
                }
            }
        }

        override fun onTrackBarChanged(position: Int) {
            uiHandler.post {
                if (mediaButtonListener != null) {
                    mediaButtonListener!!.onTrackBarChanged(position)
                }
            }
        }

        /*****************************************/
        /*             Detection Mode            */
        /*****************************************/

        override fun onUserInteractionStatusChanged(isInteracting: Boolean): Boolean {
            if (onUserInteractionChangedListeners.isEmpty()) return false
            uiHandler.post {
                for (listener in onUserInteractionChangedListeners) {
                    listener.onUserInteraction(isInteracting)
                }
            }
            return true
        }

        override fun onDetectionStateChanged(state: Int): Boolean {
            if (onDetectionStateChangedListeners.isEmpty()) return false
            uiHandler.post {
                for (listener in onDetectionStateChangedListeners) {
                    listener.onDetectionStateChanged(state)
                }
            }
            return true
        }

        override fun onDetectionDataChanged(detectionData: DetectionData): Boolean {
            if (onDetectionDataChangedListeners.isEmpty()) return false
            uiHandler.post {
                for (listener in onDetectionDataChangedListeners) {
                    listener.onDetectionDataChanged(detectionData)
                }
            }
            return true
        }

        /*****************************************/
        /*                Sequence               */
        /*****************************************/

        override fun onSequencePlayStatusChanged(status: Int): Boolean {
            if (onSequencePlayStatusChangedListeners.isEmpty()) return false
            uiHandler.post {
                for (listener in onSequencePlayStatusChangedListeners) {
                    listener.onSequencePlayStatusChanged(status)
                }
            }
            return true
        }

        /*****************************************/
        /*            Face Recognition           */
        /*****************************************/
        override fun onFaceRecognized(contactModelList: MutableList<ContactModel>): Boolean {
            if (onFaceRecognizedListeners.isEmpty()) return false
            uiHandler.post {
                for (listener in onFaceRecognizedListeners) {
                    listener.onFaceRecognized(contactModelList)
                }
            }
            return false
        }

        override fun onSdkError(sdkException: SdkException): Boolean {
            if (onSdkExceptionListeners.isEmpty()) return false
            uiHandler.post {
                for (listener in onSdkExceptionListeners) {
                    listener.onSdkError(sdkException)
                }
            }
            return true
        }

    }

    /*****************************************/
    /*                  Init                 */
    /*****************************************/

    val isReady
        @CheckResult
        get() = sdkService != null

    @UiThread
    fun onStart(activityInfo: ActivityInfo) {
        try {
            sdkService?.onStart(activityInfo)
        } catch (e: RemoteException) {
            Log.e(TAG, "onStart(ActivityInfo) - Binder invocation exception.")
        }
    }

    @RestrictTo(LIBRARY)
    @UiThread
    internal fun setSdkService(sdkService: ISdkService?) {
        this.sdkService = sdkService
        mediaBar = AidlMediaBarController(sdkService)
        registerCallback()
        onRobotReadyListeners.forEach { it.onRobotReady(sdkService != null) }
    }

    @UiThread
    private fun registerCallback() {
        try {
            sdkService?.register(applicationInfo, sdkServiceCallback)
        } catch (e: RemoteException) {
            Log.e(TAG, "Remote invocation error")
        }
    }

    @UiThread
    fun addOnRobotReadyListener(onRobotReadyListener: OnRobotReadyListener) {
        onRobotReadyListeners.add(onRobotReadyListener)
        onRobotReadyListener.onRobotReady(isReady)
    }

    @UiThread
    fun removeOnRobotReadyListener(onRobotReadyListener: OnRobotReadyListener) {
        onRobotReadyListeners.remove(onRobotReadyListener)
    }

    /*****************************************/
    /*                 Voice                 */
    /*****************************************/

    /**
     * To ask temi to speak something.
     *
     * @param ttsRequest Which contains all the TTS information temi needs to in order to speak.
     */
    fun speak(ttsRequest: TtsRequest) {
        try {
            sdkService?.speak(ttsRequest.apply { packageName = applicationInfo.packageName })
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to invoke remote call speak()")
        }
    }

    /**
     * The wakeup word of the temi's assistant.
     */
    val wakeupWord: String
        @CheckResult
        get() {
            try {
                return sdkService?.wakeupWord ?: ""
            } catch (e: RemoteException) {
                Log.e(TAG, "getWakeupWord() error")
            }
            return ""
        }

    /**
     * Trigger temi's wakeup programmatically.
     */
    fun wakeup() {
        try {
            sdkService?.wakeup()
        } catch (e: RemoteException) {
            Log.e(TAG, "wakeup() error")
        }
    }

    /**
     * Stops currently processed TTS request and empty the queue.
     */
    fun cancelAllTtsRequests() {
        try {
            sdkService?.cancelAll()
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to invoke remote call cancelAllTtsRequest()")
        }
    }

    /**
     * Request to lock contexts even if skill screen is dismissed.
     * Useful for services running in the background without UI.
     *
     * @param contextsToLock - List of contexts names to lock.
     */
    fun lockContexts(contextsToLock: List<String>) {
        try {
            sdkService?.lockContexts(contextsToLock)
        } catch (e: RemoteException) {
            Log.e(TAG, "lockContexts(List<String>) error")
        }
    }

    /**
     * Release previously locked contexts. See [.lockContexts].
     *
     * @param contextsToRelease - List of contexts names to release.
     */
    fun releaseContexts(contextsToRelease: List<String>) {
        try {
            sdkService?.releaseContexts(contextsToRelease)
        } catch (e: RemoteException) {
            Log.e(TAG, "releaseContexts(List<String>) error")
        }
    }

    /**
     * Start a conversation.
     *
     * @param question - First question from robot.
     */
    fun askQuestion(question: String) {
        try {
            sdkService?.askQuestion(question)
        } catch (e: RemoteException) {
            Log.e(TAG, "Ask question call failed")
        }
    }

    /**
     * Finish conversation.
     */
    fun finishConversation() {
        try {
            sdkService?.finishConversation()
        } catch (e: RemoteException) {
            Log.e(TAG, "Finish conversation call failed")
        }
    }

    /**
     * Trigger temi Launcher's default NLU service.
     *
     * @param text The text want to be NlU.
     */
    fun startNlu(text: String) {
        if (isMetaDataOverridingNlu) return
        try {
            sdkService?.startNlu(applicationInfo.packageName, text)
        } catch (e: RemoteException) {
            Log.e(TAG, "startNlu() error")
        }
    }

    private val isMetaDataOverridingNlu: Boolean
        get() = applicationInfo.metaData != null
                && applicationInfo.metaData.getBoolean(SdkConstants.METADATA_OVERRIDE_NLU, false)

    @UiThread
    fun addConversationViewAttachesListenerListener(conversationViewAttachesListener: ConversationViewAttachesListener) {
        conversationViewAttachesListeners.add(conversationViewAttachesListener)
    }

    @UiThread
    fun removeConversationViewAttachesListenerListener(conversationViewAttachesListener: ConversationViewAttachesListener) {
        conversationViewAttachesListeners.remove(conversationViewAttachesListener)
    }

    @UiThread
    fun addNlpListener(nlpListener: NlpListener) {
        nlpListeners.add(nlpListener)
    }

    @UiThread
    fun removeNlpListener(nlpListener: NlpListener) {
        nlpListeners.remove(nlpListener)
    }

    @UiThread
    fun addTtsListener(ttsListener: TtsListener) {
        ttsListeners.add(ttsListener)
    }

    @UiThread
    fun removeTtsListener(ttsListener: TtsListener) {
        ttsListeners.remove(ttsListener)
    }

    @UiThread
    fun addWakeupWordListener(wakeupWordListener: WakeupWordListener) {
        wakeUpWordListeners.add(wakeupWordListener)
    }

    @UiThread
    fun removeWakeupWordListener(wakeupWordListener: WakeupWordListener) {
        wakeUpWordListeners.remove(wakeupWordListener)
    }

    @UiThread
    fun removeAsrListener(asrListener: AsrListener) {
        asrListeners.remove(asrListener)
    }

    @UiThread
    fun addAsrListener(asrListener: AsrListener) {
        asrListeners.add(asrListener)
    }

    /*****************************************/
    /*               Navigation              */
    /*****************************************/

    /**
     * Save location.
     *
     * @param - Location name.
     * @return Result of a successful or failed operation.
     */
    fun saveLocation(name: String): Boolean {
        try {
            return sdkService?.saveLocation(name) ?: false
        } catch (e: RemoteException) {
            Log.e(TAG, "saveLocation(String) error")
        }
        return true
    }

    /**
     * Delete location.
     *
     * @param name - Location name.
     * @return Result of a successful or failed operation.
     */
    fun deleteLocation(name: String): Boolean {
        try {
            return sdkService?.deleteLocation(name) ?: false
        } catch (e: RemoteException) {
            Log.e(TAG, "deleteLocation(String) error")
        }
        return false
    }

    /**
     * Retrieve list of previously saved locations.
     *
     * @return List of saved locations.
     */
    val locations: List<String>
        @CheckResult
        get() {
            try {
                return sdkService?.locations ?: emptyList()
            } catch (e: RemoteException) {
                Log.e(TAG, "getLocations() error")
            }
            return emptyList()
        }

    /**
     * Send robot to previously saved location.
     *
     * @param location Saved location name.
     */
    fun goTo(location: String) {
        require(location.isNotBlank()) { "Location can not be null or empty." }
        try {
            sdkService?.goTo(location)
        } catch (e: RemoteException) {
            Log.e(TAG, "goTo(String) error")
        }
    }

    /**
     * TBD. Go to a specific position with (x,y).
     *
     * @param position Position holds (x,y).
     */
    private fun goToPosition(position: Position) {
        try {
            sdkService?.goToPosition(position)
        } catch (e: RemoteException) {
            Log.e(TAG, "goToPosition() error")
        }
    }

    /**
     *  Set navigation safety level.
     */
    var navigationSafety: SafetyLevel
        @CheckResult
        get() {
            try {
                return SafetyLevel.valueToEnum(
                    sdkService?.navigationSafety ?: SafetyLevel.DEFAULT.value
                )
            } catch (e: RemoteException) {
                Log.e(TAG, "getNavigationSafety() error")
            }
            return SafetyLevel.DEFAULT
        }
        set(safetyLevel) {
            try {
                sdkService?.setNavigationSafety(applicationInfo.packageName, safetyLevel.value)
            } catch (e: RemoteException) {
                Log.e(TAG, "setNavigationSafety() error")
            }
        }

    /**
     * Set navigation speed level.
     */
    var goToSpeed: SpeedLevel
        @CheckResult
        get() {
            try {
                return SpeedLevel.valueToEnum(
                    sdkService?.goToSpeed ?: SpeedLevel.DEFAULT.value
                )
            } catch (e: RemoteException) {
                Log.e(TAG, "getGoToSpeed() error")
            }
            return SpeedLevel.DEFAULT
        }
        set(speedLevel) {
            try {
                sdkService?.setGoToSpeed(applicationInfo.packageName, speedLevel.value)
            } catch (e: RemoteException) {
                Log.e(TAG, "setGoToSpeed() error")
            }
        }

    @UiThread
    fun addOnGoToLocationStatusChangedListener(listener: OnGoToLocationStatusChangedListener) {
        onGoToLocationStatusChangeListeners.add(listener)
    }

    @UiThread
    fun removeOnGoToLocationStatusChangedListener(listener: OnGoToLocationStatusChangedListener) {
        onGoToLocationStatusChangeListeners.remove(listener)
    }

    @UiThread
    fun addOnLocationsUpdatedListener(listener: OnLocationsUpdatedListener) {
        onLocationsUpdatedListeners.add(listener)
    }

    @UiThread
    fun removeOnLocationsUpdateListener(listener: OnLocationsUpdatedListener) {
        onLocationsUpdatedListeners.remove(listener)
    }

    @UiThread
    fun addOnDistanceToLocationChangedListener(listener: OnDistanceToLocationChangedListener) {
        onDistanceToLocationChangedListeners.add(listener)
    }

    @UiThread
    fun removeOnDistanceToLocationChangedListener(listener: OnDistanceToLocationChangedListener) {
        onDistanceToLocationChangedListeners.remove(listener)
    }

    @UiThread
    fun addOnCurrentPositionChangedListener(listener: OnCurrentPositionChangedListener) {
        onCurrentPositionChangedListeners.add(listener)
    }

    @UiThread
    fun removeOnCurrentPositionChangedListener(listener: OnCurrentPositionChangedListener) {
        onCurrentPositionChangedListeners.remove(listener)
    }

    /*****************************************/
    /*            Movement & Follow          */
    /*****************************************/

    /**
     * Request robot to follow user around.
     * Add [OnBeWithMeStatusChangedListener] to listen for status changes.
     */
    fun beWithMe() {
        try {
            sdkService?.beWithMe()
        } catch (e: RemoteException) {
            Log.e(TAG, "beWithMe() error")
        }
    }

    /**
     * Start constraint follow.
     * Add [OnConstraintBeWithStatusChangedListener] to listen for status changed.
     */
    fun constraintBeWith() {
        try {
            sdkService?.constraintBeWith()
        } catch (e: RemoteException) {
            Log.e(TAG, "constraintBeWith() error")
        }
    }

    /**
     * Request robot to stop any movement.
     */
    fun stopMovement() {
        try {
            sdkService?.stopMovement()
        } catch (e: RemoteException) {
            Log.e(TAG, "stopMovement() error")
        }
    }

    /**
     * Joystick commands.
     *
     * @param x Move on the x axis from -1 to 1.
     * @param y Move on the y axis from -1 to 1.
     */
    fun skidJoy(x: Float, y: Float) {
        try {
            sdkService?.skidJoy(x, y)
        } catch (e: RemoteException) {
            Log.e(TAG, "skidJoy(float, float) (x=$x, y=$y) error")
        }
    }

    /**
     * To turn temi by a specific degree.
     *
     * @param degrees the degree amount you want the robot to turn
     * @param speed   deprecated
     */
    @Deprecated("Use turnBy(degrees) instead.", ReplaceWith("turnBy(degrees)"))
    fun turnBy(degrees: Int, speed: Float) {
        turnBy(degrees)
    }

    /**
     * To turn temi by a specific degree.
     *
     * @param degrees the degree amount you want the robot to turn
     */
    fun turnBy(degrees: Int) {
        try {
            sdkService?.turnBy(degrees, 1.0f)
        } catch (e: RemoteException) {
            Log.e(TAG, "turnBy(int) (degrees=$degrees) error")
        }
    }

    /**
     * To tilt temi's head to a specific angle.
     *
     * @param degrees the degree which you want the robot to tilt to, between 55 and -25
     * @param speed   deprecated
     */
    @Deprecated("Use tiltAngle(degrees) instead.", ReplaceWith("tiltAngle(degrees)"))
    fun tiltAngle(degrees: Int, speed: Float) {
        tiltAngle(degrees)
    }

    /**
     * To tilt temi's head to a specific angle.
     *
     * @param degrees the degree which you want the robot to tilt to, between 55 and -25
     */
    fun tiltAngle(degrees: Int) {
        try {
            sdkService?.tiltAngle(degrees, 1.0f)
        } catch (e: RemoteException) {
            Log.e(TAG, "turnBy(int) (degrees=$degrees) error")
        }
    }

    /**
     * To tilt temi's head to by a specific degree.
     *
     * @param degrees The degree amount you want the robot to tilt
     * @param speed Deprecated. The value will always be 1.
     */
    @Deprecated("Use tiltBy(degrees) instead.", ReplaceWith("tiltBy(degrees)"))
    fun tiltBy(degrees: Int, speed: Float) {
        tiltBy(degrees)
    }

    /**
     * To tilt temi's head to by a specific degree.
     *
     * @param degrees The degree amount you want the robot to tilt
     */
    fun tiltBy(degrees: Int) {
        try {
            sdkService?.tiltBy(degrees, 1.0f)
        } catch (e: RemoteException) {
            Log.e(TAG, "tiltBy(int) (degrees=$degrees) error")
        }
    }

    @UiThread
    fun addOnBeWithMeStatusChangedListener(listener: OnBeWithMeStatusChangedListener) {
        onBeWithMeStatusChangeListeners.add(listener)
    }

    @UiThread
    fun removeOnBeWithMeStatusChangedListener(listener: OnBeWithMeStatusChangedListener) {
        onBeWithMeStatusChangeListeners.remove(listener)
    }

    @UiThread
    fun addOnConstraintBeWithStatusChangedListener(listener: OnConstraintBeWithStatusChangedListener) {
        onConstraintBeWithStatusChangedListeners.add(listener)
    }

    @UiThread
    fun removeOnConstraintBeWithStatusChangedListener(listener: OnConstraintBeWithStatusChangedListener) {
        onConstraintBeWithStatusChangedListeners.remove(listener)
    }

    @UiThread
    fun addOnRobotLiftedListener(listener: OnRobotLiftedListener) {
        onRobotLiftedListeners.add(listener)
    }

    @UiThread
    fun removeOnRobotLiftedListener(listener: OnRobotLiftedListener) {
        onRobotLiftedListeners.remove(listener)
    }

    /*****************************************/
    /*           Users & Telepresence        */
    /*****************************************/

    /**
     * Get the information of temi's admin.
     */
    val adminInfo: UserInfo?
        @CheckResult
        get() {
            try {
                return sdkService?.adminInfo
            } catch (e: RemoteException) {
                Log.e(TAG, "getAdminInfo() error")
            }
            return null
        }

    /**
     * Fetch all the temi contacts.
     */
    val allContact: List<UserInfo>
        @CheckResult
        get() {
            val contactList = ArrayList<UserInfo>()
            try {
                contactList.addAll(sdkService?.allContacts ?: emptyList())
            } catch (e: RemoteException) {
                Log.e(TAG, "getAllContacts() error")
            }
            return contactList
        }

    /**
     * Fetch recent calls.
     */
    val recentCalls: List<RecentCallModel>
        @CheckResult
        get() {
            try {
                return sdkService?.recentCalls ?: emptyList()
            } catch (e: RemoteException) {
                Log.e(TAG, "getRecentCalls() error")
            }
            return emptyList()
        }

    /**
     * Start a video call to Admin.
     *
     * @param displayName Name of admin user info.
     * @param peerId ID of admin user info.
     * @return The sessionId of Telepresence call
     */
    fun startTelepresence(displayName: String, peerId: String): String {
        try {
            return sdkService?.startTelepresence(displayName, peerId) ?: ""
        } catch (e: RemoteException) {
            Log.e(TAG, "startTelepresence() error")
        }
        return ""
    }

    /**
     * Start listening for Telepresence Status changes.
     *
     * @param listener The listener you want to add.
     */
    fun addOnTelepresenceStatusChangedListener(listener: OnTelepresenceStatusChangedListener) {
        onTelepresenceStatusChangedListeners.add(listener)
    }

    /**
     * Stop listening for Telepresence Status changes.
     *
     * @param listener The listener you added before.
     */
    fun removeOnTelepresenceStatusChangedListener(listener: OnTelepresenceStatusChangedListener) {
        onTelepresenceStatusChangedListeners.remove(listener)
    }

    /**
     * Start listening for user information updates.
     *
     * @param listener The listener you want to add.
     */
    fun addOnUsersUpdatedListener(listener: OnUsersUpdatedListener) {
        onUsersUpdatedListeners.add(listener)
    }

    /**
     * Stop listening for user information updates.
     *
     * @param listener The listener you added before.
     */
    fun removeOnUsersUpdatedListener(listener: OnUsersUpdatedListener) {
        onUsersUpdatedListeners.remove(listener)
    }

    fun addOnTelepresenceEventChangedListener(listener: OnTelepresenceEventChangedListener) {
        onTelepresenceEventChangedListener.add(listener)
    }

    fun removeOnTelepresenceEventChangedListener(listener: OnTelepresenceEventChangedListener) {
        onTelepresenceEventChangedListener.remove(listener)
    }

    /*****************************************/
    /*                 System                */
    /*****************************************/

    /**
     * Request robot's serial number as a String.
     *
     * @return The serial number of the robot.
     */
    val serialNumber: String?
        @CheckResult
        get() {
            try {
                return sdkService?.serialNumber
            } catch (e: RemoteException) {
                Log.e(TAG, "getSerialNumber() error")
            }
            return null
        }

    /**
     * Request the robot to provide current battery status.
     *
     * @return The battery data the robot.
     */
    val batteryData: BatteryData?
        @CheckResult
        get() {
            try {
                return sdkService?.batteryData
            } catch (e: RemoteException) {
                Log.e(TAG, "getBatteryData() error")
            }
            return null
        }

    /**
     * Go to the App list of Launcher.
     */
    fun showAppList() {
        try {
            sdkService?.showAppList()
        } catch (e: RemoteException) {
            Log.e(TAG, "showAppList() error")
        }
    }

    /**
     * Show the top bar of Launcher.
     */
    fun showTopBar() {
        try {
            sdkService?.showTopBar()
        } catch (e: RemoteException) {
            Log.e(TAG, "showTopBar() error")
        }
    }

    /**
     * Hide the top bar of Launcher.
     */
    fun hideTopBar() {
        try {
            sdkService?.hideTopBar()
        } catch (e: RemoteException) {
            Log.e(TAG, "hideTopBar() error")
        }
    }

    /**
     * Toggle privacy mode on temi.
     */
    var privacyMode: Boolean
        @CheckResult
        get() {
            try {
                return sdkService?.privacyModeState ?: false
            } catch (e: RemoteException) {
                Log.e(TAG, "getPrivacyModeState() error")
            }
            return false
        }
        set(on) {
            try {
                sdkService?.togglePrivacyMode(on, applicationInfo.packageName)
            } catch (e: RemoteException) {
                Log.e(TAG, "togglePrivacyMode() error")
            }
        }

    /**
     * Get(Set) HardButtons enabled(disabled).
     */
    var isHardButtonsDisabled: Boolean
        @CheckResult
        get() {
            try {
                return sdkService?.isHardButtonsDisabled ?: false
            } catch (e: RemoteException) {
                Log.e(TAG, "setHardButtonsDisabled() error")
            }
            return false
        }
        set(disable) {
            try {
                sdkService?.toggleHardButtons(disable, applicationInfo.packageName)
            } catch (e: RemoteException) {
                Log.e(TAG, "isHardButtonsEnabled() error")
            }
        }

    /**
     * Get version of the Launcher.
     */
    val launcherVersion: String
        @CheckResult
        get() {
            try {
                return sdkService?.launcherVersion ?: ""
            } catch (e: RemoteException) {
                Log.e(TAG, "getLauncherVersion() error")
            }
            return ""
        }

    /**
     * Get version of the Robox.
     */
    val roboxVersion: String
        @CheckResult
        get() {
            try {
                return sdkService?.roboxVersion ?: ""
            } catch (e: RemoteException) {
                Log.e(TAG, "getRoboxVersion() error")
            }
            return ""
        }

    /**
     * Show or hide the green badge(Movement indicator such as navigation, follow...) at the top of the screen.
     */
    @get:JvmName("isTopBadgeEnabled")
    var topBadgeEnabled: Boolean
        @CheckResult
        get() {
            try {
                return sdkService?.isTopBadgeEnabled ?: true
            } catch (e: RemoteException) {
                Log.e(TAG, "isTopBadgeEnabled() error")
            }
            return true
        }
        /**
         * @param enabled true to enable, false to disable the green badge.
         */
        set(enabled) {
            if (!isMetaDataKiosk) return
            try {
                sdkService?.setTopBadgeEnabled(applicationInfo.packageName, enabled)
            } catch (e: RemoteException) {
                Log.e(TAG, "setTopBadgeEnabled() error")
            }
        }

    /**
     * Turn on/off detection mode.
     */
    @get: JvmName("isDetectionModeOn")
    var detectionModeOn: Boolean
        @CheckResult
        get() {
            try {
                return sdkService?.isDetectionModeOn ?: false
            } catch (e: RemoteException) {
                Log.e(TAG, "isDetectionModeOn() error")
            }
            return false
        }
        /**
         * @param on true to turn on, false to turn off.
         */
        set(on) {
            setDetectionModeOn(on, SdkConstants.DETECTION_DISTANCE_DEFAULT)
        }

    /**
     * Turn on/off detection mode with distance.
     *
     * @param on true to turn on, false to turn off.
     * @param distance  Maximum detection distance(0.5~2.0), person will be detected when the distance
     *                  to temi less than this value only.
     */
    fun setDetectionModeOn(on: Boolean, distance: Float) {
        try {
            sdkService?.setDetectionModeOn(applicationInfo.packageName, on, distance)
        } catch (e: RemoteException) {
            Log.e(TAG, "setDetectionModeOn() error")
        }
    }

    /**
     * Turn on/off track user(welcome mode).
     */
    @get:JvmName("isTrackUserOn")
    var trackUserOn: Boolean
        @CheckResult
        get() {
            try {
                return sdkService?.isTrackUserOn ?: false
            } catch (e: RemoteException) {
                Log.e(TAG, "isTrackUser() error")
            }
            return false
        }
        /**
         * @param on true to turn on, false to turn off.
         */
        set(on) {
            try {
                sdkService?.setTrackUserOn(applicationInfo.packageName, on)
            } catch (e: RemoteException) {
                Log.e(TAG, "setTrackUserOn() error")
            }
        }

    /**
     * Turn on/off auto return.
     */
    @get:JvmName("isAutoReturnOn")
    var autoReturnOn: Boolean
        @CheckResult
        get() {
            try {
                return sdkService?.isAutoReturnOn ?: false
            } catch (e: RemoteException) {
                Log.e(TAG, "isAutoReturnOn() error")
            }
            return false
        }
        /**
         * @param on true to turn on, false to turn off.
         */
        set(on) {
            try {
                sdkService?.setAutoReturnOn(applicationInfo.packageName, on)
            } catch (e: RemoteException) {
                Log.e(TAG, "setAutoReturnOn() error")
            }
        }

    /**
     * Volume of Launcher OS.
     */
    var volume: Int
        @CheckResult
        get() {
            try {
                return sdkService?.volume ?: 0
            } catch (e: RemoteException) {
                Log.e(TAG, "getVolume() error")
            }
            return 0
        }
        /**
         * @param volume the volume you want to set to Launcher.
         */
        set(volume) {
            try {
                val validVolume = when {
                    volume < 0 -> 0
                    volume > 10 -> 10
                    else -> volume
                }
                sdkService?.setVolume(applicationInfo.packageName, validVolume)
            } catch (e: RemoteException) {
                Log.e(TAG, "setVolume() error")
            }
        }

    @Throws(RemoteException::class)
    fun showNormalNotification(notification: NormalNotification) {
        if (sdkService != null) {
            sdkService!!.showNormalNotification(notification)
        } else {
            throw RemoteException("Sdk service is null.")
        }
    }

    @Throws(RemoteException::class)
    fun showAlertNotification(
        notification: AlertNotification,
        notificationListener: NotificationListener
    ) {
        if (sdkService != null) {
            sdkService!!.showAlertNotification(notification)
            listenersMap[notification.notificationId] = notificationListener
        } else {
            throw RemoteException("Sdk service is null.")
        }
    }

    @Throws(RemoteException::class)
    fun removeAlertNotification(notification: AlertNotification) {
        if (sdkService != null) {
            sdkService!!.removeAlertNotification(notification)
        } else {
            throw RemoteException("Sdk service is null.")
        }
    }

    @UiThread
    fun addOnPrivacyModeStateChangedListener(listener: OnPrivacyModeChangedListener) {
        onPrivacyModeStateChangedListeners.add(listener)
    }

    @UiThread
    fun removeOnPrivacyModeStateChangedListener(listener: OnPrivacyModeChangedListener) {
        onPrivacyModeStateChangedListeners.remove(listener)
    }

    @UiThread
    fun addOnBatteryStatusChangedListener(listener: OnBatteryStatusChangedListener) {
        onBatteryStatusChangedListeners.add(listener)
    }

    @UiThread
    fun removeOnBatteryStatusChangedListener(listener: OnBatteryStatusChangedListener) {
        onBatteryStatusChangedListeners.remove(listener)
    }

    /*****************************************/
    /*               Kiosk Mode              */
    /*****************************************/

    /**
     * Is current skill a Kiosk Mode skill.
     *
     * @return Value of the Metadata Kiosk.
     */
    private val isMetaDataKiosk: Boolean
        get() = applicationInfo.metaData != null
                && applicationInfo.metaData.getBoolean(SdkConstants.METADATA_KIOSK, false)

    /**
     * Toggle the wakeup trigger on and off
     */
    @get: JvmName("isWakeupDisabled")
    @set: JvmName("toggleWakeup")
    var toggleWakeup: Boolean
        @CheckResult
        get() {
            try {
                return sdkService?.isWakeupDisabled ?: false
            } catch (e: RemoteException) {
                Log.e(TAG, "isWakeupDisabled() error")
            }
            return false
        }
        /**
         * @param disabled set true to disable the wakeup or false to enable it.
         */
        set(disabled) {
            if (!isMetaDataKiosk) {
                Log.e(TAG, "Wakeup can only be toggled in Kiosk Mode")
                return
            }
            try {
                sdkService?.toggleWakeup(disabled, applicationInfo.packageName)
            } catch (e: RemoteException) {
                Log.e(TAG, "toggleWakeup() error")
            }
        }

    /**
     * Toggle the visibility of the navigation billboard when you perform goTo commands.
     */
    @get:JvmName("isNavigationBillboardDisabled")
    @set:JvmName("toggleNavigationBillboard")
    var navigationBillboardDisabled: Boolean
        @CheckResult
        get() {
            try {
                return sdkService?.isGoToBillboardDisabled ?: false
            } catch (e: RemoteException) {
                Log.e(TAG, "isNavigationBillboardDisabled() error")
            }
            return false
        }
        /**
         * @param disabled true to disable the billboard or false to enable it.
         */
        set(disabled) {
            if (!isMetaDataKiosk) {
                Log.e(TAG, "Billboard can only be toggled in Kiosk Mode")
                return
            }
            try {
                sdkService?.setGoToBillboardDisabled(applicationInfo.packageName, disabled)
            } catch (e: RemoteException) {
                Log.e(TAG, "toggleNavigationBillboard() error")
            }
        }

    /**
     * Request to be the selected Kiosk Mode App programmatically.
     */
    fun requestToBeKioskApp() {
        if (!isMetaDataKiosk) {
            Log.e(TAG, "No kiosk mode declaration in meta data")
            return
        }
        try {
            sdkService?.requestToBeKioskApp(applicationInfo.packageName)
        } catch (e: RemoteException) {
            Log.e(TAG, "requestToBeKioskApp() error")
        }
    }

    /**
     * Whether this App is selected as the Kiosk Mode App.
     */
    @CheckResult
    fun isSelectedKioskApp(): Boolean {
        if (!isMetaDataKiosk) return false
        try {
            return sdkService?.isSelectedKioskApp(applicationInfo.packageName) ?: false
        } catch (e: RemoteException) {
            Log.e(TAG, "isSelectedKioskApp() error")
        }
        return false
    }

    /*****************************************/
    /*            Activity Stream            */
    /*****************************************/

    fun setActivityStreamPublishListener(activityStreamPublishListener: ActivityStreamPublishListener?) {
        this.activityStreamPublishListener = activityStreamPublishListener
    }

    @Throws(RemoteException::class)
    fun shareActivityObject(activityStreamObject: ActivityStreamObject) {
        sdkService?.let {
            AsyncTask.execute {
                ActivityStreamUtils.handleActivityStreamObject(activityStreamObject)
                try {
                    it.shareActivityStreamObject(activityStreamObject)
                } catch (e: RemoteException) {
                    Log.e(TAG, "Sdk service is null")
                }
            }
        }
    }

    /*****************************************/
    /*                 Media                 */
    /*****************************************/

    fun setMediaButtonListener(mediaButtonListener: MediaButtonListener) {
        this.mediaButtonListener = mediaButtonListener
    }

    fun removeMediaButtonListener() {
        mediaButtonListener = null
    }

    @Throws(RemoteException::class)
    fun updateMediaBar(mediaBarData: MediaBarData) {
        mediaBarData.packageName = applicationInfo.packageName
        mediaBar.updateMediaBar(mediaBarData)
    }

    @Throws(RemoteException::class)
    fun pauseMediaBar() {
        mediaBar.pauseMediaBar()
    }

    @Throws(RemoteException::class)
    fun setMediaPlaying(isPlaying: Boolean) {
        mediaBar.setMediaPlaying(isPlaying, applicationInfo.packageName)
    }

    /*****************************************/
    /*             Detection Mode            */
    /*****************************************/

    @UiThread
    fun addOnUserInteractionChangedListener(listener: OnUserInteractionChangedListener) {
        onUserInteractionChangedListeners.add(listener)
    }

    @UiThread
    fun removeOnUserInteractionChangedListener(listener: OnUserInteractionChangedListener) {
        onUserInteractionChangedListeners.remove(listener)
    }

    @UiThread
    fun addOnDetectionStateChangedListener(listener: OnDetectionStateChangedListener) {
        onDetectionStateChangedListeners.add(listener)
    }

    @Deprecated(
        "Use removeOnDetectionStateChangedListener(listener) instead.",
        ReplaceWith("this.removeOnDetectionStateChangedListener(listener)"),
        DeprecationLevel.WARNING
    )
    @UiThread
    fun removeDetectionStateChangedListener(listener: OnDetectionStateChangedListener) {
        onDetectionStateChangedListeners.remove(listener)
    }

    @UiThread
    fun removeOnDetectionStateChangedListener(listener: OnDetectionStateChangedListener) {
        onDetectionStateChangedListeners.remove(listener)
    }

    @UiThread
    fun addOnDetectionDataChangedListener(listener: OnDetectionDataChangedListener) {
        onDetectionDataChangedListeners.add(listener)
    }

    @UiThread
    fun removeOnDetectionDataChangedListener(listener: OnDetectionDataChangedListener) {
        onDetectionDataChangedListeners.remove(listener)
    }

    /*****************************************/
    /*               Permission              */
    /*****************************************/

    /**
     * Check permission grant status.
     *
     * @param permission The [Permission] you want to check.
     * @return Grant status. See [com.robotemi.sdk.permission.Permission.PermissionResult].
     */
    @Permission.PermissionResult
    @CheckResult
    fun checkSelfPermission(permission: Permission): Int {
        if (permission.isKioskPermission && !isMetaDataKiosk) {
            Log.w(TAG, "Only Kiosk App may have kiosk permissions")
            return Permission.DENIED
        }
        try {
            return sdkService?.checkSelfPermission(applicationInfo.packageName, permission.value)
                ?: Permission.DENIED
        } catch (e: RemoteException) {
            Log.e(TAG, "checkSelfPermission() error")
        }
        return Permission.DENIED
    }

    /**
     * Request permissions.
     *
     * If you already had the permission, Launcher will not handle this request again.
     *
     * Add [OnRequestPermissionResultListener] to listen the request result.
     *
     * @param permissions A list holds the permissions you want to request.
     * @param requestCode Identify which request.
     */
    fun requestPermissions(permissions: List<Permission>, requestCode: Int) {
        val permissionsFromMetadata =
            applicationInfo.metaData?.getString(SdkConstants.METADATA_PERMISSIONS)
        if (permissionsFromMetadata.isNullOrBlank()) {
            Log.w(TAG, "There is no valid permission in metadata")
            return
        }

        val validPermissions = mutableListOf<String>()
        for (permission in permissions) {
            if (!permissionsFromMetadata.contains(permission.value)) {
                Log.w(TAG, "This permission $permission is not declared in AndroidManifest.xml")
                continue
            }
            if (permission.isKioskPermission && !isMetaDataKiosk) {
                Log.w(TAG, "Kiosk permission $permission should request in Kiosk Mode")
                continue
            }
            validPermissions.add(permission.value)
        }

        if (validPermissions.isEmpty()) {
            Log.w(TAG, "There is no valid permission in permissions")
            return
        }

        try {
            sdkService?.requestPermissions(
                applicationInfo.packageName,
                validPermissions,
                requestCode
            )
        } catch (e: RemoteException) {
            Log.e(TAG, "requestPermissions() error")
        }
    }

    @UiThread
    fun addOnRequestPermissionResultListener(listener: OnRequestPermissionResultListener) {
        onRequestPermissionResultListeners.add(listener)
    }

    @UiThread
    fun removeOnRequestPermissionResultListener(listener: OnRequestPermissionResultListener) {
        onRequestPermissionResultListeners.remove(listener)
    }

    /*****************************************/
    /*                Sequence               */
    /*****************************************/

    /**
     * Fetch all sequences user created on the Web platform.
     *
     * @return List holds all sequences.
     */
    @WorkerThread
    @CheckResult
    fun getAllSequences(): List<SequenceModel> {
        try {
            return sdkService?.getAllSequences(applicationInfo.packageName) ?: emptyList()
        } catch (e: RemoteException) {
            Log.e(TAG, "fetchAllSequences() error")
        }
        return emptyList()
    }

    /**
     * Play sequence by sequence ID.
     *
     * @param sequenceId Sequence ID you want to play.
     */
    fun playSequence(sequenceId: String) {
        try {
            sdkService?.playSequence(applicationInfo.packageName, sequenceId)
        } catch (e: RemoteException) {
            Log.e(TAG, "playSequence() error")
        }
    }

    @UiThread
    fun addOnSequencePlayStatusChangedListener(listener: OnSequencePlayStatusChangedListener) {
        onSequencePlayStatusChangedListeners.add(listener)
    }

    @UiThread
    fun removeOnSequencePlayStatusChangedListener(listener: OnSequencePlayStatusChangedListener) {
        onSequencePlayStatusChangedListeners.remove(listener)
    }

    /*****************************************/
    /*                  Map                  */
    /*****************************************/

    /**
     * Get map data.
     *
     * @return Map data.
     */
    @WorkerThread
    @CheckResult
    fun getMapData(): MapDataModel? {
        try {
            return sdkService?.getMapData(applicationInfo.packageName)
        } catch (e: RemoteException) {
            Log.e(TAG, "getMapDataString() error")
        }
        return null
    }

    /*****************************************/
    /*            Face Recognition           */
    /*****************************************/

    /**
     * Start face recognition.
     */
    fun startFaceRecognition() {
        try {
            sdkService?.startFaceRecognition(applicationInfo.packageName)
        } catch (e: RemoteException) {
            Log.e(TAG, "startFaceRecognition() error")
        }
    }

    /**
     * Stop face recognition.
     */
    fun stopFaceRecognition() {
        try {
            sdkService?.stopFaceRecognition(applicationInfo.packageName)
        } catch (e: RemoteException) {
            Log.e(TAG, "stopFaceRecognition() error")
        }
    }

    @UiThread
    fun addOnFaceRecognizedListener(listener: OnFaceRecognizedListener) {
        onFaceRecognizedListeners.add(listener)
    }

    @UiThread
    fun removeOnFaceRecognizedListener(listener: OnFaceRecognizedListener) {
        onFaceRecognizedListeners.remove(listener)
    }

    @UiThread
    fun addOnSdkExceptionListener(listener: OnSdkExceptionListener) {
        onSdkExceptionListeners.add(listener)
    }

    @UiThread
    fun removeOnSdkExceptionListener(listener: OnSdkExceptionListener) {
        onSdkExceptionListeners.remove(listener)
    }

    /*****************************************/
    /*               Interface               */
    /*****************************************/

    interface WakeupWordListener {
        fun onWakeupWord(wakeupWord: String, direction: Int)
    }

    interface TtsListener {
        fun onTtsStatusChanged(ttsRequest: TtsRequest)
    }

    interface AsrListener {
        fun onAsrResult(asrResult: String)
    }

    interface NlpListener {
        fun onNlpCompleted(nlpResult: NlpResult)
    }

    interface ActivityStreamPublishListener {
        fun onPublish(message: ActivityStreamPublishMessage)
    }

    interface MediaButtonListener {

        fun onPlayButtonClicked(play: Boolean)

        fun onNextButtonClicked()

        fun onBackButtonClicked()

        fun onTrackBarChanged(position: Int)
    }

    interface NotificationListener {
        fun onNotificationBtnClicked(btnNumber: Int)
    }

    interface ConversationViewAttachesListener {
        fun onConversationAttaches(isAttached: Boolean)
    }

    companion object {

        private const val TAG = "Robot"

        private var instance: Robot? = null

        @JvmStatic
        fun getInstance(): Robot {
            if (instance == null) {
                synchronized(Robot::class.java) {
                    if (instance == null) {
                        val context = TemiSdkContentProvider.sdkContext
                            ?: throw NullPointerException("context == null")
                        instance = Robot(context)
                    }
                }
            }
            return instance!!
        }
    }
}
