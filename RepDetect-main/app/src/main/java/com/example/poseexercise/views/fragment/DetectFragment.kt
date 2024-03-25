package com.example.poseexercise.views.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.poseexercise.R
import com.example.poseexercise.adapters.ExerciseGifAdapter
import com.example.poseexercise.adapters.WorkoutAdapter
import com.example.poseexercise.data.plan.ExerciseLog
import com.example.poseexercise.data.plan.ExercisePlan
import com.example.poseexercise.data.plan.Plan
import com.example.poseexercise.data.results.WorkoutResult
import com.example.poseexercise.posedetector.PoseDetectorProcessor
import com.example.poseexercise.posedetector.classification.PoseClassifierProcessor.CHEST_PRESS_CLASS
import com.example.poseexercise.posedetector.classification.PoseClassifierProcessor.DEAD_LIFT_CLASS
import com.example.poseexercise.posedetector.classification.PoseClassifierProcessor.LUNGES_CLASS
import com.example.poseexercise.posedetector.classification.PoseClassifierProcessor.POSE_CLASSES
import com.example.poseexercise.posedetector.classification.PoseClassifierProcessor.PUSHUPS_CLASS
import com.example.poseexercise.posedetector.classification.PoseClassifierProcessor.SHOULDER_PRESS_CLASS
import com.example.poseexercise.posedetector.classification.PoseClassifierProcessor.SITUP_UP_CLASS
import com.example.poseexercise.posedetector.classification.PoseClassifierProcessor.SQUATS_CLASS
import com.example.poseexercise.posedetector.classification.PoseClassifierProcessor.WARRIOR_CLASS
import com.example.poseexercise.posedetector.classification.PoseClassifierProcessor.YOGA_TREE_CLASS
import com.example.poseexercise.util.MemoryManagement
import com.example.poseexercise.util.MyApplication
import com.example.poseexercise.util.MyUtils.Companion.convertTimeStringToMinutes
import com.example.poseexercise.util.MyUtils.Companion.databaseNameToClassification
import com.example.poseexercise.util.MyUtils.Companion.exerciseNameToDisplay
import com.example.poseexercise.util.VisionImageProcessor
import com.example.poseexercise.viewmodels.AddPlanViewModel
import com.example.poseexercise.viewmodels.CameraXViewModel
import com.example.poseexercise.viewmodels.HomeViewModel
import com.example.poseexercise.viewmodels.ResultViewModel
import com.example.poseexercise.views.activity.MainActivity
import com.example.poseexercise.views.fragment.preference.PreferenceUtils
import com.example.poseexercise.views.graphic.GraphicOverlay
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.mlkit.common.MlKitException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

/**
 * Fragment responsible for handling the workout process, camera usage, and exercise tracking.
 */
class DetectFragment : Fragment(), MemoryManagement {
    private var screenOn = false
    private var previewView: PreviewView? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var imageProcessor: VisionImageProcessor? = null
    private var needUpdateGraphicOverlayImageSourceInfo = false
    private var selectedModel = POSE_DETECTION
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var cameraSelector: CameraSelector? = null
    private var today: String = DateFormat.format("EEEE", Date()) as String
    private var runOnce: Boolean = false
    private var isAllWorkoutFinished: Boolean = false
    private var mRecTimer: Timer? = null
    private var mRecSeconds = 0
    private var mRecMinute = 0
    private var mRecHours = 0
    private val onlyExercise: List<String> =
        listOf(
            SQUATS_CLASS,
            PUSHUPS_CLASS,
            LUNGES_CLASS,
            SITUP_UP_CLASS,
            CHEST_PRESS_CLASS,
            DEAD_LIFT_CLASS,
            SHOULDER_PRESS_CLASS
        )
    private val onlyPose: List<String> = listOf(WARRIOR_CLASS, YOGA_TREE_CLASS)
    private var notCompletedExercise: List<Plan>? = null

    // late init properties---
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var timerTextView: TextView
    private lateinit var timerRecordIcon: ImageView
    private lateinit var workoutRecyclerView: RecyclerView
    private lateinit var workoutAdapter: WorkoutAdapter
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var addPlanViewModel: AddPlanViewModel
    private lateinit var startButton: Button
    private lateinit var buttonCompleteExercise: Button
    private lateinit var buttonCancelExercise: Button
    private lateinit var cameraFlipFAB: FloatingActionButton
    private lateinit var confIndicatorView: ImageView
    private lateinit var currentExerciseTextView: TextView
    private lateinit var currentRepetitionTextView: TextView
    private lateinit var confidenceTextView: TextView
    private lateinit var cameraViewModel: CameraXViewModel
    private lateinit var loadingTV: TextView
    private lateinit var loadProgress: ProgressBar
    private lateinit var completeAllExercise: TextView
    private lateinit var skipButton: Button
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var yogaPoseImage: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!allRuntimePermissionsGranted()) {
            getRuntimePermissions()
        }
        initTextToSpeech()
        cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        cameraViewModel = ViewModelProvider(
            this, ViewModelProvider.AndroidViewModelFactory
                .getInstance(requireActivity().application)
        )[CameraXViewModel::class.java]
        resultViewModel = ResultViewModel(MyApplication.getInstance())
        addPlanViewModel = AddPlanViewModel(MyApplication.getInstance())
        homeViewModel = HomeViewModel(MyApplication.getInstance())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_detect, container, false)
        // Linking all button and controls
        cameraFlipFAB = view.findViewById(R.id.facing_switch)
        startButton = view.findViewById(R.id.button_start_exercise)
        buttonCompleteExercise = view.findViewById(R.id.button_complete_exercise)
        buttonCancelExercise = view.findViewById(R.id.button_cancel_exercise)
        timerTextView = view.findViewById(R.id.timerTV)
        timerRecordIcon = view.findViewById(R.id.timerRecIcon)
        confIndicatorView = view.findViewById(R.id.confidenceIndicatorView)
        currentExerciseTextView = view.findViewById(R.id.currentExerciseText)
        currentRepetitionTextView = view.findViewById(R.id.currentRepetitionText)
        confidenceTextView = view.findViewById(R.id.confidenceIndicatorTextView)
        completeAllExercise = view.findViewById(R.id.completedAllExerciseTextView)
        confIndicatorView.visibility = View.INVISIBLE
        confidenceTextView.visibility = View.INVISIBLE
        loadingTV = view.findViewById(R.id.loadingStatus)
        loadProgress = view.findViewById(R.id.loadingProgress)
        skipButton = view.findViewById(R.id.skipButton)
        workoutRecyclerView = view.findViewById(R.id.workoutRecycleViewArea)
        workoutRecyclerView.layoutManager = LinearLayoutManager(activity)
        yogaPoseImage = view.findViewById(R.id.yogaPoseSnapShot)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Initialize views
        super.onViewCreated(view, savedInstanceState)
        previewView = view.findViewById(R.id.preview_view)
        val gifContainer: FrameLayout = view.findViewById(R.id.gifContainer)
        graphicOverlay = view.findViewById(R.id.graphic_overlay)
        cameraFlipFAB.visibility = View.VISIBLE
        startButton.visibility = View.VISIBLE
        gifContainer.visibility = View.GONE
        skipButton.visibility = View.GONE


        // start exercise button
        startButton.setOnClickListener {
            // showing loading AI pose detection Model information to user
            loadingTV.visibility = View.GONE
            loadProgress.visibility = View.GONE
            // Set the screenOn flag to true, preventing the screen from turning off
            screenOn = true
            // Add the FLAG_KEEP_SCREEN_ON flag to the activity's window, keeping the screen on
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            cameraFlipFAB.visibility = View.GONE
            gifContainer.visibility = View.VISIBLE
            buttonCancelExercise.visibility = View.VISIBLE
            buttonCompleteExercise.visibility = View.VISIBLE
            startButton.visibility = View.GONE
            // To disable screen timeout
            //window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            cameraViewModel.triggerClassification.value = true
        }

        // Cancel the exercise
        buttonCancelExercise.setOnClickListener {
            synthesizeSpeech("Workout Cancelled")
            stopMediaTimer()
            Navigation.findNavController(view)
                .navigate(R.id.action_detectFragment_to_cancelFragment)
            // Set the screenOn flag to false, allowing the screen to turn off
            screenOn = false
            // Clear the FLAG_KEEP_SCREEN_ON flag to allow the screen to turn off
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // stop triggering classification process
            cameraViewModel.triggerClassification.value = false
        }

        // 10 reps =  3.2 for push up -> 1 reps = 3.2/10
        // Complete the exercise
        val sitUp = Postures.situp
        val pushUp = Postures.pushup
        val lunge = Postures.lunge
        val squat = Postures.squat
        val chestPress = Postures.chestpress
        val deadLift = Postures.deadlift
        val shoulderPress = Postures.shoulderpress

        buttonCompleteExercise.setOnClickListener {
            synthesizeSpeech("Workout Complete")
            cameraViewModel.postureLiveData.value?.let {
                //val builder = StringBuilder()
                for ((_, value) in it) {
                    if (value.repetition != 0) {
                        lifecycleScope.launch {
                            val calorie = when (value.postureType) {
                                sitUp.type -> sitUp.value / 10
                                pushUp.type -> pushUp.value / 10
                                lunge.type -> lunge.value / 10
                                squat.type -> squat.value / 10
                                chestPress.type -> chestPress.value / 10
                                deadLift.type -> deadLift.value / 10
                                shoulderPress.type -> shoulderPress.value / 10
                                else -> 0.0
                            }
                            val workoutTime =
                                convertTimeStringToMinutes(timerTextView.text.toString())

                            val workOutResult = WorkoutResult(
                                0,
                                value.postureType,
                                value.repetition,
                                value.confidence,
                                System.currentTimeMillis(),
                                calorie * value.repetition,
                                workoutTime
                            )
                            resultViewModel.insert(workOutResult)
                        }
                    }
                }
            }

            // update the workoutTimer in MainActivity
            val currentTimer = timerTextView.text.toString()
            MainActivity.workoutTimer = currentTimer

            stopMediaTimer()

            // Set the screenOn flag to false, allowing the screen to turn off
            screenOn = false

            // Clear the FLAG_KEEP_SCREEN_ON flag to allow the screen to turn off
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // update the workoutResultData in MainActivity
            cameraViewModel.postureLiveData.value?.let {
                val builder = StringBuilder()
                for ((key, value) in it) {
                    if (value.repetition != 0 && key in onlyExercise) {
                        builder.append("${exerciseNameToDisplay(value.postureType)}: ${value.repetition}\n")
                    } else if (key in onlyPose) {
                        builder.append("${exerciseNameToDisplay(value.postureType)}\n")
                    }
                }
                if (builder.toString().isNotEmpty()) MainActivity.workoutResultData =
                    builder.toString()
            }

            // stop triggering classification process
            cameraViewModel.triggerClassification.value = false

            // Navigation to complete fragment
            Navigation.findNavController(view)
                .navigate(R.id.action_detectFragment_to_completedFragment)
        }

        cameraViewModel.processCameraProvider.observe(viewLifecycleOwner) { provider: ProcessCameraProvider? ->
            cameraProvider = provider
            //bindAllCameraUseCases()
            notCompletedExercise?.let { bindAllCameraUseCases(it) } ?: bindAllCameraUseCases(
                emptyList()
            )
        }

        cameraFlipFAB.setOnClickListener {
            toggleCameraLens()
        }
        // initialize the list of plan exercise to be filled from database
        val databaseExercisePlan = mutableListOf<ExercisePlan>()
        // Initialize Exercise Log
        val exerciseLog = ExerciseLog()
        // get the list of plans from database
        lifecycleScope.launch(Dispatchers.IO) {

            // get not completed exercise from database using coroutine
            notCompletedExercise =
                withContext(Dispatchers.IO) { homeViewModel.getNotCompletePlans(today) }

            // Create a set to track unique exercises
            val uniqueExercises = mutableSetOf<String>()

            // Create exerciseGifs list based on notCompletedExercise, avoiding duplicates
            val exerciseGifs = mutableListOf<Pair<String, Int>>()

            // Function to add exercise to the list if it's not already present
            fun addExerciseIfNotPresent(exercise: String) {
                if (uniqueExercises.add(exercise)) {
                    exerciseGifs.add(exercise to mapExerciseToDrawable(exercise))
                }
            }

            // Add entries based on notCompletedExercise
            notCompletedExercise?.let {
                it.map { plan ->
                    addExerciseIfNotPresent(plan.exercise)
                }
            }

            // Add entries for default exercises (squat, lunge, warrior, tree)
            addExerciseIfNotPresent(exerciseNameToDisplay(SQUATS_CLASS))
            addExerciseIfNotPresent(exerciseNameToDisplay(LUNGES_CLASS))
            addExerciseIfNotPresent(exerciseNameToDisplay(WARRIOR_CLASS))
            addExerciseIfNotPresent(exerciseNameToDisplay(YOGA_TREE_CLASS))

            val viewPager: ViewPager2 = view.findViewById(R.id.exerciseViewPager)
            val exerciseGifAdapter = ExerciseGifAdapter(exerciseGifs) {
                // Handle skip button click here
                // Transition to the "Start" button
                startButton.visibility = View.GONE
                cameraFlipFAB.visibility = View.VISIBLE
                viewPager.visibility = View.GONE
                skipButton.visibility = View.GONE
                gifContainer.visibility = View.GONE
                cameraFlipFAB.visibility = View.GONE
            }
            viewPager.adapter = exerciseGifAdapter

            notCompletedExercise?.forEach { item ->
                val exercisePlan =
                    ExercisePlan(
                        item.id,
                        databaseNameToClassification(item.exercise),
                        item.repeatCount
                    )
                val existingExercisePlan =
                    databaseExercisePlan.find {
                        it.planId == item.id
                    }
                if (existingExercisePlan != null) {
                    // Update repetitions if ExercisePlan with the same exerciseName already exists
                    existingExercisePlan.repetitions += item.repeatCount
                } else {
                    // Add a new ExercisePlan if not already in the databaseExercisePlan
                    databaseExercisePlan.add(exercisePlan)
                }
            }
            // Push the planned exercise name in exercise Log
            databaseExercisePlan.forEach {
                exerciseLog.addExercise(
                    it.planId,
                    it.exerciseName,
                    0,
                    0f,
                    false
                )
            }
        }

        // Declare variables to store previous values
        var previousKey: String? = null
        var previousConfidence: Float? = null

        cameraViewModel.postureLiveData.observe(viewLifecycleOwner) { mapResult ->
            for ((key, value) in mapResult) {
                // Visualize the repetition exercise data
                if (key in POSE_CLASSES.toList()) {
                    // get the data from exercise log of specific exercise
                    val data = exerciseLog.getExerciseData(key)
                    if (key in onlyExercise && data == null) {
                        // Adding exercise for the first time
                        exerciseLog.addExercise(
                            null,
                            key,
                            value.repetition,
                            value.confidence,
                            false
                        )
                    } else if (key in onlyExercise && value.repetition == data?.repetitions?.plus(1)) {
                        workoutRecyclerView.visibility = View.VISIBLE
                        if (isAllWorkoutFinished) {
                            completeAllExercise.visibility = View.VISIBLE
                        } else {
                            completeAllExercise.visibility = View.GONE
                        }
                        confIndicatorView.visibility = View.INVISIBLE
                        confidenceTextView.visibility = View.INVISIBLE
                        yogaPoseImage.visibility = View.INVISIBLE
                        // check if the exercise target is complete
                        var repetition: Int? = databaseExercisePlan.find {
                            it.exerciseName.equals(
                                key,
                                ignoreCase = true
                            )
                        }?.repetitions
                        if (repetition == null || repetition == 0) {
                            repetition = HighCount
                        }
                        if (!data.isComplete && (value.repetition >= repetition)) {
                            // Adding data only when the increment happen
                            exerciseLog.addExercise(
                                data.planId,
                                key,
                                value.repetition,
                                value.confidence,
                                true
                            )
                            // inform the user about completion only once
                            synthesizeSpeech(exerciseNameToDisplay(key) + " exercise Complete")
                            // check if all the exercise list complete if yes tell all exercise is complete
                            if (exerciseLog.areAllExercisesCompleted(databaseExercisePlan)) {
                                val handler = Handler(Looper.getMainLooper())
                                handler.postDelayed({
                                    synthesizeSpeech("Congratulation! all the planned exercise completed")
                                    isAllWorkoutFinished = true
                                    completeAllExercise.visibility = View.VISIBLE
                                }, 5000)
                            }
                            // Update complete status for existing plan
                            if (data.planId != null) {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    addPlanViewModel.updateComplete(
                                        true,
                                        System.currentTimeMillis(),
                                        data.planId
                                    )
                                }
                            }
                        } else if (data.isComplete) {
                            // Adding data only when the increment happen
                            exerciseLog.addExercise(
                                data.planId,
                                key,
                                value.repetition,
                                value.confidence,
                                true
                            )
                        } else {
                            // Adding data only when the increment happen
                            exerciseLog.addExercise(
                                data.planId,
                                key,
                                value.repetition,
                                value.confidence,
                                false
                            )
                        }
                        // display Current result when the increment happen
                        displayResult(key, exerciseLog)

                        // update the display list of all exercise progress when the increment happen
                        val exerciseList = exerciseLog.getExerciseDataList()
                        workoutAdapter = WorkoutAdapter(exerciseList, databaseExercisePlan)
                        workoutRecyclerView.adapter = workoutAdapter
                    } else if (key in onlyPose && value.confidence > 0.5) {

                        if (key !== previousKey || value.confidence !== previousConfidence) {
                            // Implementation of pose confidence
                            displayConfidence(key, value.confidence)
                            workoutRecyclerView.visibility = View.GONE
                            completeAllExercise.visibility = View.GONE
                            currentExerciseTextView.visibility = View.VISIBLE
                            currentRepetitionTextView.visibility = View.GONE
                            confidenceTextView.visibility = View.VISIBLE
                            currentExerciseTextView.text = exerciseNameToDisplay(key)
                            confidenceTextView.text = getString(
                                R.string.confidence_percentage,
                                (value.confidence * 100).toInt()
                            )
                            yogaPoseImage.visibility = View.VISIBLE

                            if (key !== previousKey) {
                                yogaPoseImage.setImageResource(getDrawableResourceIdYoga(key))
                            }

                            // Update previous values
                            previousKey = key
                            previousConfidence = value.confidence
                        }
                    } else if (key == previousKey && value.confidence < 0.6) {
                        confIndicatorView.visibility = View.INVISIBLE
                        confidenceTextView.visibility = View.INVISIBLE
                        yogaPoseImage.visibility = View.INVISIBLE
                    }
                }
            }

            // Visualize list of all exercise result for the first time, to show the target exercise
            if (!runOnce) {
                val exerciseList = exerciseLog.getExerciseDataList()
                workoutAdapter = WorkoutAdapter(exerciseList, databaseExercisePlan)
                workoutRecyclerView.adapter = workoutAdapter
                runOnce = true
                loadingTV.visibility = View.GONE
                loadProgress.visibility = View.GONE
                synthesizeSpeech("ready to start")
                startMediaTimer()
                timerTextView.visibility = View.VISIBLE
                timerRecordIcon.visibility = View.VISIBLE
            }
        }
    }


    // Map the notCompletedExercise list to a list of pairs to show gifs
    private fun mapExerciseToDrawable(exercise: String): Int {
        return when (exercise) {
            exerciseNameToDisplay(PUSHUPS_CLASS) -> R.drawable.pushup
            exerciseNameToDisplay(LUNGES_CLASS) -> R.drawable.lunge
            exerciseNameToDisplay(SQUATS_CLASS) -> R.drawable.squats
            exerciseNameToDisplay(SITUP_UP_CLASS) -> R.drawable.situp
            exerciseNameToDisplay(CHEST_PRESS_CLASS) -> R.drawable.chest_press_gif
            exerciseNameToDisplay(DEAD_LIFT_CLASS) -> R.drawable.dead_lift_gif
            exerciseNameToDisplay(SHOULDER_PRESS_CLASS) -> R.drawable.shoulder_press_gif
            exerciseNameToDisplay(WARRIOR_CLASS) -> R.drawable.warrior_yoga_gif
            exerciseNameToDisplay(YOGA_TREE_CLASS) -> R.drawable.tree_yoga_gif
            else -> R.drawable.warrior_yoga_gif
        }
    }

    /**
     * List of yoga images
     */
    private val yogaPoseImages = mapOf(
        WARRIOR_CLASS to R.drawable.warrior_yoga_pose,
        YOGA_TREE_CLASS to R.drawable.tree_yoga_pose
    )

    private fun getDrawableResourceIdYoga(yogaPoseKey: String): Int {
        return yogaPoseImages[yogaPoseKey]
            ?: throw IllegalArgumentException("Invalid yoga pose key: $yogaPoseKey")
    }

    /**
     * Initialize TextToSpeech engine
     */
    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(context) {
            if (it == TextToSpeech.SUCCESS) {
                // Set language to US English and speech rate to 1.0
                textToSpeech.language = Locale.US
                textToSpeech.setSpeechRate(1.0f)
            }
        }
    }

    /**
     * Synthesize speech using TextToSpeech
     */
    private fun synthesizeSpeech(name: String) {
        lifecycleScope.launch(Dispatchers.Default) {
            textToSpeech.speak(name, TextToSpeech.QUEUE_ADD, null, null)
        }
    }

    /**
     * Display exercise result in the UI
     */
    @SuppressLint("SetTextI18n")
    private fun displayResult(key: String, exerciseLog: ExerciseLog) {
        currentExerciseTextView.visibility = View.VISIBLE
        currentRepetitionTextView.visibility = View.VISIBLE
        val data = exerciseLog.getExerciseData(key)
        currentExerciseTextView.text = exerciseNameToDisplay(key)
        currentRepetitionTextView.text = "count: " + data?.repetitions.toString()
    }

    /**
     * Display confidence level with different colors based on thresholds
     */
    private fun displayConfidence(key: String, confidence: Float) {
        confIndicatorView.visibility = View.VISIBLE
        yogaPoseImage.visibility = View.VISIBLE
        if (confidence <= 0.6f) {
            confIndicatorView.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.red)
        } else if (confidence > 0.6f && confidence <= 0.7f) {
            confIndicatorView.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.orange)
        } else if (confidence > 0.7f && confidence <= 0.8f) {
            confIndicatorView.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.yellow)
        } else if (confidence > 0.8f && confidence <= 0.9f) {
            confIndicatorView.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.lightGreen)
        } else {
            confIndicatorView.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.green)
        }
    }

    /**
     * Bind all camera use cases (preview and analysis)
     */
    private fun bindAllCameraUseCases(notCompletedPlan: List<Plan>) {
        // Bind all camera use cases (preview and analysis)
        bindPreviewUseCase()
        cameraViewModel.triggerClassification.observe(viewLifecycleOwner) { pressed ->
            bindAnalysisUseCase(pressed, notCompletedPlan)
        }
    }

    /**
     * bind preview use case
     */
    @Suppress("DEPRECATION")
    private fun bindPreviewUseCase() {
        if (!PreferenceUtils.isCameraLiveViewportEnabled(requireContext())) {
            return
        }
        if (cameraProvider == null) {
            return
        }
        if (previewUseCase != null) {
            cameraProvider!!.unbind(previewUseCase)
        }
        val builder = Preview.Builder()
        val targetResolution =
            PreferenceUtils.getCameraXTargetResolution(requireContext(), lensFacing)
        if (targetResolution != null) {
            builder.setTargetResolution(targetResolution)
        }
        previewUseCase = builder.build()
        previewUseCase!!.setSurfaceProvider(previewView!!.surfaceProvider)
        camera = cameraProvider!!.bindToLifecycle(this, cameraSelector!!, previewUseCase)
    }

    /**
     * bind analysis use case
     */
    private fun bindAnalysisUseCase(runClassification: Boolean, notCompletedPlan: List<Plan>) {
        if (cameraProvider == null) {
            return
        }
        if (analysisUseCase != null) {
            cameraProvider?.unbind(analysisUseCase)
        }
        if (imageProcessor != null) {
            imageProcessor?.stop()
        }
        imageProcessor = try {
            when (selectedModel) {
                POSE_DETECTION -> {
                    // get all the setting preferences for camera x live preview
                    val poseDetectorOptions =
                        PreferenceUtils.getPoseDetectorOptionsForLivePreview(requireContext())
                    val shouldShowInFrameLikelihood =
                        PreferenceUtils.shouldShowPoseDetectionInFrameLikelihoodLivePreview(
                            requireContext()
                        )
                    val visualizeZ = PreferenceUtils.shouldPoseDetectionVisualizeZ(requireContext())
                    val rescaleZ =
                        PreferenceUtils.shouldPoseDetectionRescaleZForVisualization(requireContext())

                    // Build Pose Detector Processor based on the settings/preferences
                    PoseDetectorProcessor(
                        requireContext(),
                        poseDetectorOptions,
                        shouldShowInFrameLikelihood,
                        visualizeZ,
                        rescaleZ,
                        runClassification,
                        true,
                        cameraViewModel,
                        notCompletedPlan
                    )
                }

                else -> throw IllegalStateException("Invalid model name")
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Can not create image processor: $selectedModel",
                e
            )
            Toast.makeText(
                requireContext(),
                "Can not create image processor: " + e.localizedMessage,
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val builder = ImageAnalysis.Builder()
        analysisUseCase = builder.build()
        needUpdateGraphicOverlayImageSourceInfo = true
        analysisUseCase?.setAnalyzer(
            ContextCompat.getMainExecutor(requireContext())
        ) { imageProxy: ImageProxy ->
            if (needUpdateGraphicOverlayImageSourceInfo) {
                val isImageFlipped = lensFacing == CameraSelector.LENS_FACING_FRONT
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                if (rotationDegrees == 0 || rotationDegrees == 180) {
                    graphicOverlay!!.setImageSourceInfo(
                        imageProxy.width,
                        imageProxy.height,
                        isImageFlipped
                    )
                } else {
                    graphicOverlay!!.setImageSourceInfo(
                        imageProxy.height,
                        imageProxy.width,
                        isImageFlipped
                    )
                }
                needUpdateGraphicOverlayImageSourceInfo = false
            }

            try {
                imageProcessor!!.processImageProxy(imageProxy, graphicOverlay)
            } catch (e: MlKitException) {
                Log.e(
                    TAG,
                    "Failed to process image. Error: " + e.localizedMessage
                )
                Toast.makeText(
                    requireContext(),
                    e.localizedMessage,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        cameraProvider?.bindToLifecycle(this, cameraSelector!!, analysisUseCase)
    }

    /**
     * Check if all required runtime permissions are granted
     */
    private fun allRuntimePermissionsGranted(): Boolean {
        // Check if all required runtime permissions are granted
        for (permission in REQUIRED_RUNTIME_PERMISSIONS) {
            permission.let {
                if (!isPermissionGranted(requireContext(), it)) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * Check if a specific permission is granted
     */
    private fun isPermissionGranted(context: Context, permission: String): Boolean {
        // Check if a specific permission is granted
        if (ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Permission granted: $permission")
            return true
        }
        Log.i(TAG, "Permission NOT granted: $permission")
        return false
    }

    /**
     * Request runtime permissions
     */
    private fun getRuntimePermissions() {
        val permissionsToRequest = ArrayList<String>()
        for (permission in REQUIRED_RUNTIME_PERMISSIONS) {
            permission.let {
                if (!isPermissionGranted(requireContext(), it)) {
                    permissionsToRequest.add(permission)
                }
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUESTS
            )
        }
    }

    /**
    Toggle between front and back camera lenses
     *
     */
    private fun toggleCameraLens() {
        if (cameraProvider == null) {
            Log.d(TAG, "Camera provider is null")
            return
        }
        val newLensFacing =
            if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
        val newCameraSelector = CameraSelector.Builder().requireLensFacing(newLensFacing).build()

        try {
            if (cameraProvider!!.hasCamera(newCameraSelector)) {
                Log.d(TAG, "Set facing to $newLensFacing")
                lensFacing = newLensFacing
                cameraSelector = newCameraSelector
                notCompletedExercise?.let { bindAllCameraUseCases(it) } ?: bindAllCameraUseCases(
                    emptyList()
                )
                return
            }
        } catch (e: CameraInfoUnavailableException) {
            Log.e(TAG, "Failed to get camera info", e)
        }
    }

    /**
     * timer handling coroutine
     */
    private val mMainHandler: Handler by lazy {
        Handler(Looper.getMainLooper()) {
            when (it.what) {
                WHAT_START_TIMER -> {
                    if (mRecSeconds % 2 != 0) {
                        timerRecordIcon.visibility = View.VISIBLE
                    } else {
                        timerRecordIcon.visibility = View.INVISIBLE
                    }
                    timerTextView.text = calculateTime(mRecSeconds, mRecMinute)
                }

                WHAT_STOP_TIMER -> {
                    timerTextView.text = calculateTime(0, 0)
                    timerRecordIcon.visibility = View.GONE
                    timerTextView.visibility = View.GONE
                }
            }
            true
        }
    }


    /**
     * Start timer functionality
     */
    private fun startMediaTimer() {
        val pushTask: TimerTask = object : TimerTask() {
            override fun run() {
                mRecSeconds++
                if (mRecSeconds >= 60) {
                    mRecSeconds = 0
                    mRecMinute++
                }
                if (mRecMinute >= 60) {
                    mRecMinute = 0
                    mRecHours++
                    if (mRecHours >= 24) {
                        mRecHours = 0
                        mRecMinute = 0
                        mRecSeconds = 0
                    }
                }
                mMainHandler.sendEmptyMessage(WHAT_START_TIMER)
            }
        }
        if (mRecTimer != null) {
            stopMediaTimer()
        }
        mRecTimer = Timer()
        mRecTimer?.schedule(pushTask, 1000, 1000)
    }


    /**
     * Stop timer functionality
     */
    private fun stopMediaTimer() {
        if (mRecTimer != null) {
            mRecTimer?.cancel()
            mRecTimer = null
        }
        mRecHours = 0
        mRecMinute = 0
        mRecSeconds = 0
        mMainHandler.sendEmptyMessage(WHAT_STOP_TIMER)
    }

    /**
     * Calculate the time and return string
     */
    private fun calculateTime(seconds: Int, minute: Int, hour: Int? = null): String {
        val mBuilder = java.lang.StringBuilder()

        if (hour != null) {
            if (hour < 10) {
                mBuilder.append("0")
                mBuilder.append(hour)
            } else {
                mBuilder.append(hour)
            }
            mBuilder.append(":")
        }

        if (minute < 10) {
            mBuilder.append("0")
            mBuilder.append(minute)
        } else {
            mBuilder.append(minute)
        }

        mBuilder.append(":")
        if (seconds < 10) {
            mBuilder.append("0")
            mBuilder.append(seconds)
        } else {
            mBuilder.append(seconds)
        }
        return mBuilder.toString()
    }


    /**
     * overridden function to clean up memory, clear object reference and un-register onClickListener
     * in WorkOutFragment
     */
    override fun clearMemory() {
        if (!textToSpeech.isSpeaking) {
            textToSpeech.stop()
        }
        textToSpeech.shutdown()
        previewView = null
        graphicOverlay = null
        cameraProvider = null
        camera = null
        previewUseCase = null
        analysisUseCase = null
        imageProcessor = null
        cameraSelector = null
        mRecTimer?.let {
            it.cancel()
            mRecTimer = null
        }
        startButton.setOnClickListener(null)
        buttonCompleteExercise.setOnClickListener(null)
        buttonCancelExercise.setOnClickListener(null)
        cameraFlipFAB.setOnClickListener(null)
        skipButton.setOnClickListener(null)
        workoutRecyclerView.adapter = null
    }

    override fun onDestroy() {
        clearMemory()
        super.onDestroy()
    }

    /**
     *Constants and companion object
     */
    companion object {
        private const val TAG = "RepDetect CameraXLivePreview"
        private const val POSE_DETECTION = "Pose Detection"
        private const val PERMISSION_REQUESTS = 1

        private const val WHAT_START_TIMER = 0x00
        private const val WHAT_STOP_TIMER = 0x01
        private const val HighCount = 9999999

        private val REQUIRED_RUNTIME_PERMISSIONS =
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
    }

    /**
     * Typed constant class for exercise postures
     */
    class TypedConstant(val type: String, val value: Double)
    object Postures {
        val pushup = TypedConstant(PUSHUPS_CLASS, 3.2)
        val lunge = TypedConstant(LUNGES_CLASS, 3.0)
        val squat = TypedConstant(SQUATS_CLASS, 3.8)
        val situp = TypedConstant(SITUP_UP_CLASS, 5.0)
        val chestpress = TypedConstant(CHEST_PRESS_CLASS, 7.0)
        val deadlift = TypedConstant(DEAD_LIFT_CLASS, 10.0)
        val shoulderpress = TypedConstant(SHOULDER_PRESS_CLASS, 9.0)
    }
}