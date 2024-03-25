package com.example.poseexercise.data.plan

import com.example.poseexercise.R

/**
Object containing constants related to exercise data
 **/
object Constants {

    // Function to get a list of predefined exercises with their details
    fun getExerciseList(): List<Exercise> {
        return listOf(
            Exercise(
                id = 1,
                name = "Push up",
                image = R.drawable.push_up,
                calorie = 3.2,
                level = "Intermediate"
            ),
            Exercise(
                id = 2,
                name = "Lunge",
                image = R.drawable.reverse_lunges,
                calorie = 3.0,
                level = "Beginner"
            ),
            Exercise(
                id = 3,
                name = "Squat",
                image = R.drawable.squat,
                calorie = 3.8,
                level = "Beginner"
            ),
            Exercise(
                id = 4,
                name = "Sit up",
                image = R.drawable.sit_ups,
                calorie = 5.0,
                level = "Advance"
            ),
            Exercise(
                id = 5,
                name = "Chest press",
                image = R.drawable.chest_press,
                calorie = 7.0,
                level = "Advance"
            ),
            Exercise(
                id = 6,
                name = "Dead lift",
                image = R.drawable.dead_lift,
                calorie = 10.0,
                level = "Advance"
            ),
            Exercise(
                id = 7,
                name = "Shoulder press",
                image = R.drawable.shoulder_press,
                calorie = 9.0,
                level = "Advance"
            ),
        )
    }
}